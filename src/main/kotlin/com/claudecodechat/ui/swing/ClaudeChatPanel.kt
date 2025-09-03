package com.claudecodechat.ui.swing

import com.claudecodechat.services.ChatHistoryService
import com.claudecodechat.cli.ClaudeCliService
import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.MessageType
import com.claudecodechat.settings.ClaudeSettings
import com.claudecodechat.state.SessionViewModel
import com.claudecodechat.completion.CompletionManager
import com.claudecodechat.completion.CompletionState
import com.claudecodechat.completion.CompletionTrigger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.JBPopup

import com.intellij.ui.JBColor
import com.intellij.icons.AllIcons
import com.intellij.ui.components.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBIntSpinner
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.text.*
import javax.swing.UIManager
import com.claudecodechat.ui.markdown.MarkdownRenderer
import com.claudecodechat.ui.markdown.MarkdownRenderConfig

class ClaudeChatPanel(private val project: Project) : JBPanel<ClaudeChatPanel>() {
    private val sessionViewModel = SessionViewModel.getInstance(project)
    private val completionManager = CompletionManager(project)
    private val settings = ClaudeSettings.getInstance()
    
    // UI Components
    private val messagesPanel: JPanel
    private val chatScrollPane: JScrollPane
    private val inputArea: JBTextArea // 多行输入区域
    private val currentFileLabel: JLabel // 显示当前文件和选中行（在发送栏最左侧）
    private val sendButton: JButton
    private val clearButton: JButton
    private val sessionTabs: JBTabbedPane
    private val modelComboBox: JComboBox<String>
    private val chatInputBar: ChatInputBar
    
    // Completion system
    private var completionList: JList<String>
    private var completionPopup: JBPopup? = null
    private var currentCompletionState: CompletionState? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        layout = BorderLayout()
        
        // Initialize UI components
        messagesPanel = createMessagesPanel()
        chatScrollPane = createChatScrollPane()
        inputArea = createInputArea()
        currentFileLabel = createCurrentFileLabel()
        sendButton = createSendButton()
        clearButton = createClearButton()
        sessionTabs = createSessionTabs()
        modelComboBox = createModelComboBox()
        chatInputBar = createChatInputBar()
        // scrollPane is now chatScrollPane
        
        // Initialize completion components
        completionList = createCompletionList()
        
        setupLayout()
        setupEventHandlers()
        observeViewModel()
        loadInitialData()
    }
    
    private fun createMessagesPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
            border = JBUI.Borders.empty(5, 10, 5, 10) // reduced padding
        }
    }
    
    private fun createChatScrollPane(): JScrollPane {
        return JBScrollPane(messagesPanel).apply {
            preferredSize = Dimension(600, 400)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty()
            )
        }
    }
    
    private fun createInputArea(): JBTextArea {
        return JBTextArea(4, 50).apply {
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    handleInputKeyPress(e)
                }
                
                override fun keyReleased(e: KeyEvent) {
                    handleInputKeyRelease(e)
                }
            })
            
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = updateCompletion()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = updateCompletion()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = updateCompletion()
            })
        }
    }
    
    private fun createCurrentFileLabel(): JLabel {
        return JLabel().apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            foreground = Color.decode("#888888")
            text = ""
            border = JBUI.Borders.empty(0, 5, 0, 5)
        }
    }
    
    private fun createSendButton(): JButton {
        return JButton("Send (Ctrl+Enter)").apply {
            addActionListener { sendMessage() }
        }
    }
    
    private fun createClearButton(): JButton {
        return JButton().apply {
            isVisible = false
            isEnabled = false
            preferredSize = Dimension(0, 0)
            minimumSize = Dimension(0, 0)
            maximumSize = Dimension(0, 0)
        }
    }
    
    private fun createSessionTabs(): JBTabbedPane {
        val tabs = JBTabbedPane()
        tabs.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        // No-op: tabs replaced by toolwindow tabs. Keep method for compatibility if needed.
        return tabs
    }
    
    private fun createModelComboBox(): JComboBox<String> {
        val models = arrayOf("auto", "sonnet", "opus", "haiku")
        return JComboBox(models).apply {
            selectedItem = "auto"
            addActionListener {
                // Model selection logic here
            }
        }
    }
    
    private fun createChatInputBar(): ChatInputBar {
        return ChatInputBar(project) { text, model ->
            val modelToUse = if (model == "auto") "" else model
            sessionViewModel.sendPrompt(text, modelToUse)
        }
    }
    
    
    
    private fun createCompletionList(): JList<String> {
        return JList<String>().apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            background = JBColor.background()
            foreground = JBColor.foreground()
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            
            // Set custom cell renderer for icons
            cellRenderer = CompletionListCellRenderer()
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        // Double click to select completion
                        applySelectedCompletion()
                    }
                }
            })
            
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> {
                            applySelectedCompletion()
                            e.consume()
                        }
                        KeyEvent.VK_ESCAPE -> {
                            hideCompletion()
                            inputArea.requestFocus()
                            e.consume()
                        }
                    }
                }
            })
        }
    }
    

    
    private fun setupLayout() {
        // Top toolbar
        val toolbarPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            val leftPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply { /* removed in-tool tabs; kept placeholder for spacing */ }
            val rightPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply { border = JBUI.Borders.empty() }
            add(leftPanel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.EAST)
        }
        
        // Main input area handled by ChatInputBar now
        
        // Input section: reuse shared ChatInputBar (with completion and file info)
        val inputSection = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(5)
            )
            add(chatInputBar, BorderLayout.CENTER)
        }
        
        // Center section (chat + completion)
        val centerSection = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(chatScrollPane, BorderLayout.CENTER)
        }
        
        // Main layout
        add(toolbarPanel, BorderLayout.NORTH)
        add(centerSection, BorderLayout.CENTER)
        add(inputSection, BorderLayout.SOUTH)
    }
    
    private fun setupEventHandlers() {
        // Listen for editor file changes
        updateCurrentFileInfo()
        
        // Track editor selection change (active file changed)
        project.messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    updateCurrentFileInfo()
                }
            }
        )
        
        // Track caret movement to update current line
        EditorFactory.getInstance().eventMulticaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    updateCurrentFileInfo()
                }
            },
            project
        )
    }
    
    private fun updateCurrentFileInfo() {
        scope.launch {
            try {
                val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                val selectedEditor = fileEditorManager.selectedTextEditor
                
                if (selectedEditor != null) {
                    val virtualFile = selectedEditor.virtualFile
                    val caretModel = selectedEditor.caretModel
                    val line = caretModel.logicalPosition.line + 1
                    
                    val fileInfo = when {
                        virtualFile != null -> {
                            val relativePath = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(
                                virtualFile, 
                                project.baseDir ?: virtualFile
                            ) ?: virtualFile.name
                            "current file: $relativePath | line: $line"
                        }
                        else -> ""
                    }
                    
                    ApplicationManager.getApplication().invokeLater {
                        currentFileLabel.text = fileInfo
                        currentFileLabel.isVisible = fileInfo.isNotEmpty()
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        currentFileLabel.text = ""
                        currentFileLabel.isVisible = false
                    }
                }
            } catch (e: Exception) {
                // Ignore errors, just don't show file info
                ApplicationManager.getApplication().invokeLater {
                    currentFileLabel.text = ""
                    currentFileLabel.isVisible = false
                }
            }
        }
    }
    
    private fun observeViewModel() {
        scope.launch {
            sessionViewModel.messages.collect { messages ->
                updateChatDisplay(messages)
            }
        }
        
        scope.launch {
            sessionViewModel.isLoading.collect { isLoading ->
                sendButton.isEnabled = !isLoading
                sendButton.text = if (isLoading) "Sending..." else "Send (Ctrl+Enter)"
            }
        }
        
        // ToolWindow manages tabs now; no need to reflect current session in local tabs
        
        scope.launch {
            completionManager.completionState.collect { state ->
                handleCompletionState(state)
            }
        }
    }
    
    private fun loadInitialData() {
        // Load any initial data if needed
    }
    
    private fun handleInputKeyPress(e: KeyEvent) {
        when {
            e.keyCode == KeyEvent.VK_ENTER && e.isControlDown -> {
                sendMessage()
                e.consume()
            }
            e.keyCode == KeyEvent.VK_ESCAPE -> {
                hideCompletion()
                e.consume()
            }
            e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_DOWN -> {
                if (completionPopup?.isVisible == true) {
                    // Navigate completion list
                    val currentIndex = completionList.selectedIndex
                    val itemCount = completionList.model.size
                    
                    if (itemCount > 0) {
                        val newIndex = when (e.keyCode) {
                            KeyEvent.VK_UP -> if (currentIndex > 0) currentIndex - 1 else itemCount - 1
                            KeyEvent.VK_DOWN -> if (currentIndex < itemCount - 1) currentIndex + 1 else 0
                            else -> currentIndex
                        }
                        completionList.selectedIndex = newIndex
                        completionList.ensureIndexIsVisible(newIndex)
                    }
                    e.consume()
                    return
                }
            }
            e.keyCode == KeyEvent.VK_TAB || e.keyCode == KeyEvent.VK_ENTER -> {
                if (completionPopup?.isVisible == true) {
                    // Accept completion with both TAB and ENTER
                    applySelectedCompletion()
                    e.consume()
                    return
                }
            }
        }
    }
    
    private fun handleInputKeyRelease(e: KeyEvent) {
        // Trigger completion update after key release
        if (!e.isControlDown && !e.isAltDown) {
            updateCompletion()
        }
    }
    
    private fun updateCompletion() {
        val text = inputArea.text
        val cursorPos = inputArea.caretPosition
        completionManager.updateCompletion(text, cursorPos)
    }
    
    private fun handleCompletionState(state: CompletionState) {
        if (state.isShowing && state.items.isNotEmpty()) {
            showCompletionPopup(state)
        } else {
            hideCompletion()
        }
    }
    
    private fun showCompletionPopup(state: CompletionState) {
        // Store current state for apply completion
        currentCompletionState = state
        
        // Create display strings for completion items
        val items = state.items.map { item ->
            when (item) {
                is com.claudecodechat.completion.CompletionItem.SlashCommand -> {
                    "/${item.name} - ${item.description}"
                }
                is com.claudecodechat.completion.CompletionItem.FileReference -> {
                    "${item.fileName} - ${item.relativePath}"
                }
            }
        }
        
        val listModel = DefaultListModel<String>()
        items.forEach { listModel.addElement(it) }
        
        completionList.model = listModel
        completionList.selectedIndex = state.selectedIndex
        
        // Close existing popup
        completionPopup?.cancel()
        
        // Create popup with proper sizing
        completionPopup = JBPopupFactory.getInstance().createPopupChooserBuilder(items)
            .setTitle("Completions")
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(false)
            .setItemChosenCallback {
                applySelectedCompletion()
            }
            .addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
                override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                    completionPopup = null
                    currentCompletionState = null
                }
            })
            .createPopup()
        
        // Calculate popup position and size to align perfectly with input area
        val inputBounds = inputArea.bounds
        val inputLocationOnScreen = inputArea.locationOnScreen
        
        // Set popup size to match input width with fixed height
        val popupWidth = inputBounds.width
        val popupHeight = 220  // Height for ~8 items
        completionPopup?.size = Dimension(popupWidth, popupHeight)
        
        // Position popup directly above the input area
        val popupX = inputLocationOnScreen.x
        val popupY = inputLocationOnScreen.y - popupHeight - 10 // 10px gap for better spacing
        
        completionPopup?.showInScreenCoordinates(inputArea, Point(popupX, popupY))
    }
    
    private fun applySelectedCompletion() {
        val state = currentCompletionState ?: return
        val selectedIndex = completionList.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < state.items.size) {
            applyCompletion(state, selectedIndex)
        }
    }
    
    private fun applyCompletion(state: CompletionState, selectedIndex: Int) {
        if (selectedIndex < 0 || selectedIndex >= state.items.size) return
        
        val selectedItem = state.items[selectedIndex]
        val currentText = inputArea.text
        val triggerPos = state.triggerPosition
        
        val newText = when (selectedItem) {
            is com.claudecodechat.completion.CompletionItem.SlashCommand -> {
                val beforeTrigger = currentText.substring(0, triggerPos)
                val afterCursor = currentText.substring(inputArea.caretPosition)
                val replacement = "/${selectedItem.name} "
                beforeTrigger + replacement + afterCursor
            }
            is com.claudecodechat.completion.CompletionItem.FileReference -> {
                val beforeTrigger = currentText.substring(0, triggerPos)
                val afterCursor = currentText.substring(inputArea.caretPosition)
                val replacement = "@${selectedItem.relativePath} "
                beforeTrigger + replacement + afterCursor
            }
            else -> currentText
        }
        
        inputArea.text = newText
        inputArea.caretPosition = triggerPos + newText.substring(triggerPos).indexOf(' ') + 1
        hideCompletion()
    }
    
    
    private fun hideCompletion() {
        completionPopup?.cancel()
        completionPopup = null
        currentCompletionState = null
        completionManager.hideCompletion()
    }
    
    private fun sendMessage() {
        val message = inputArea.text.trim()
        if (message.isEmpty()) return
        
        val selectedModel = modelComboBox.selectedItem as String
        // If auto is selected, use empty string so CLI doesn't get model parameter
        val modelToUse = if (selectedModel == "auto") "" else selectedModel
        
        // Clear input
        inputArea.text = ""
        hideCompletion()
        
        // Send through SessionViewModel
        sessionViewModel.sendPrompt(message, modelToUse)
    }
    
    private fun updateChatDisplay(messages: List<ClaudeStreamMessage>) {
        ApplicationManager.getApplication().invokeLater {
            // Clear existing messages
            messagesPanel.removeAll()
            
            try {
                // Group messages to handle tool use/result pairs
                val groupedMessages = groupToolMessages(messages)
                
                for (group in groupedMessages) {
                    val messageComponent = when (group.type) {
                        "user" -> createMessageComponent(">", secondaryTextColor(), group.content, secondaryTextColor())
                        "assistant" -> createMessageComponent("●", JBColor.foreground(), group.content, JBColor.foreground())
                        "tool_interaction" -> createToolInteractionComponent(group.toolUse, group.toolResult)
                        "error" -> createMessageComponent("●", Color.decode("#FF6B6B"), group.content, Color.decode("#FF6B6B"))
                        else -> null
                    }
                    
                    messageComponent?.let {
                        messagesPanel.add(it)
                        messagesPanel.add(Box.createVerticalStrut(0)) // No spacing
                    }
                }
                
                // Refresh the display
                messagesPanel.revalidate()
                messagesPanel.repaint()
                
                // Keep view anchored near bottom when new messages arrive
                SwingUtilities.invokeLater {
                    val scrollBar = chatScrollPane.verticalScrollBar
                    if (scrollBar.maximum - scrollBar.value <= 200) {
                        scrollBar.value = scrollBar.maximum
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private data class MessageGroup(
        val type: String, // "user", "assistant", "tool_interaction", "error"
        val content: String = "",
        val toolUse: com.claudecodechat.models.Content? = null,
        val toolResult: com.claudecodechat.models.Content? = null
    )
    
    private fun groupToolMessages(messages: List<ClaudeStreamMessage>): List<MessageGroup> {
        val groups = mutableListOf<MessageGroup>()
        val pendingToolUses = mutableMapOf<String, com.claudecodechat.models.Content>()
        
        for (message in messages) {
            when (message.type) {
                MessageType.USER -> {
                    // Skip meta messages
                    if (message.isMeta) {
                        continue
                    }
                    
                    // Check if this user message contains tool results
                    val toolResults = message.message?.content?.filter { it.type == com.claudecodechat.models.ContentType.TOOL_RESULT }
                    if (toolResults?.isNotEmpty() == true) {
                        // This is a tool result message, try to pair with pending tool uses
                        for (toolResult in toolResults) {
                            val toolUseId = toolResult.toolUseId
                            if (toolUseId != null && pendingToolUses.containsKey(toolUseId)) {
                                val toolUse = pendingToolUses.remove(toolUseId)
                                groups.add(MessageGroup(
                                    type = "tool_interaction",
                                    toolUse = toolUse,
                                    toolResult = toolResult
                                ))
                            } else {
                                // Orphaned tool result
                                groups.add(MessageGroup(
                                    type = "tool_interaction",
                                    toolUse = null,
                                    toolResult = toolResult
                                ))
                            }
                        }
                    } else {
                        // Check if this is a command message by looking at the raw text content
                        val userText = message.message?.content?.firstOrNull()?.text ?: ""
                        if (userText.contains("<command-message>") && userText.contains("<command-name>")) {
                            val commandDisplay = formatCommandMessage(userText)
                            if (commandDisplay.isNotEmpty()) {
                                groups.add(MessageGroup(type = "user", content = commandDisplay))
                            }
                        } else if (userText.isNotEmpty()) {
                            groups.add(MessageGroup(type = "user", content = userText.trimStart()))
                        }
                    }
                }
                MessageType.ASSISTANT -> {
                    message.message?.content?.forEach { content ->
                        when (content.type) {
                            com.claudecodechat.models.ContentType.TEXT -> {
                                if (content.text != null && content.text.isNotEmpty()) {
                                    groups.add(MessageGroup(type = "assistant", content = content.text.trimStart()))
                                }
                            }
                            com.claudecodechat.models.ContentType.TOOL_USE -> {
                                // Store tool use for later pairing with result
                                if (content.id != null) {
                                    pendingToolUses[content.id] = content
                                }
                            }
                            else -> {}
                        }
                    }
                }
                MessageType.ERROR -> {
                    val errorText = message.error?.message ?: "Unknown error"
                    groups.add(MessageGroup(type = "error", content = errorText))
                }
                else -> {}
            }
        }
        
        // Handle any remaining unpaired tool uses
        for (toolUse in pendingToolUses.values) {
            groups.add(MessageGroup(
                type = "tool_interaction",
                toolUse = toolUse,
                toolResult = null
            ))
        }
        
        return groups
    }
    
    /**
     * Create a message component with icon and content in DIV-like layout
     */
    private fun createMessageComponent(iconText: String, iconColor: Color, content: String, textColor: Color): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 0, 0) // No padding
            background = JBColor.background()
            
            // Icon area (left side) - fixed width, top aligned
            val iconPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = JBColor.background()
                preferredSize = Dimension(15, -1) // Narrower
                
                val iconLabel = JBLabel(iconText).apply {
                    foreground = iconColor
                    font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                    horizontalAlignment = SwingConstants.LEFT
                    verticalAlignment = SwingConstants.TOP
                }
                add(iconLabel, BorderLayout.NORTH)
            }
            add(iconPanel, BorderLayout.WEST)
            
            // Content area (right side) - flexible width with text wrapping
            val contentArea = createWrappingContentArea(content, textColor)
            add(contentArea, BorderLayout.CENTER)
        }
    }

    private fun secondaryTextColor(): Color {
        // IntelliJ UIManager 常见键：Label.disabledForeground、Label.infoForeground、Component.infoForeground
        val candidateKeys = listOf(
            "Label.disabledForeground",
            "Label.infoForeground",
            "Component.infoForeground"
        )
        for (key in candidateKeys) {
            val c = UIManager.getColor(key)
            if (c != null) return c
        }
        return JBColor.GRAY
    }
    
    /**
     * Create a tool interaction component
     */
    private fun createToolInteractionComponent(toolUse: com.claudecodechat.models.Content?, toolResult: com.claudecodechat.models.Content?): JPanel {
        val isError = toolResult?.isError == true
        val iconColor = if (isError) Color.decode("#FF6B6B") else Color.decode("#50C878")
        
        val toolName = toolUse?.name ?: "Unknown"
        val inputString = toolUse?.input?.toString() ?: ""
        val formattedDisplay = formatToolDisplay(toolName, inputString, toolResult)
        
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 0, 2, 0) // Small top/bottom padding only
            background = JBColor.background()
            
            // Icon area (left side) - fixed width, top aligned  
            val iconPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = JBColor.background()
                preferredSize = Dimension(20, -1) // Fixed width
                
                val iconLabel = JBLabel("⏺").apply {
                    foreground = iconColor
                    font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                    horizontalAlignment = SwingConstants.LEFT
                    verticalAlignment = SwingConstants.TOP // Top align the icon
                }
                add(iconLabel, BorderLayout.NORTH) // Put icon at top
            }
            add(iconPanel, BorderLayout.WEST)
            
            // Tool content area (right side) with wrapping
            val contentArea = createWrappingToolContentArea(formattedDisplay, JBColor.foreground())
            add(contentArea, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create content area for message text with automatic wrapping
     */
    private fun createWrappingContentArea(content: String, textColor: Color): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty()
            val component = MarkdownRenderer.createComponent(
                content,
                MarkdownRenderConfig(
                    allowHeadings = false,
                    allowImages = false,
                    overrideForeground = textColor,
                )
            )
            add(component, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create content area for message text (legacy - kept for compatibility)
     */
    private fun createContentArea(content: String, textColor: Color): JPanel {
        return createWrappingContentArea(content, textColor)
    }
    
    /**
     * Create content area for tool display with automatic wrapping
     */
    private fun createWrappingToolContentArea(content: String, textColor: Color): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
            border = JBUI.Borders.empty()
            
            val lines = content.split("\\n")
            for ((index, line) in lines.withIndex()) {
                if (line.isNotBlank()) {
                    // First line (tool name) should be bold
                    val isBold = index == 0 && !line.startsWith("⎿")
                    val font = if (isBold) {
                        Font(Font.SANS_SERIF, Font.BOLD, 12)
                    } else {
                        Font(Font.SANS_SERIF, Font.PLAIN, 12)
                    }
                    
                    // Use JTextArea for each line to enable wrapping
                    val textArea = JTextArea(line).apply {
                        foreground = textColor
                        background = JBColor.background()
                        this.font = font
                        isEditable = false
                        isOpaque = false
                        lineWrap = true
                        wrapStyleWord = true
                        border = null
                        alignmentX = Component.LEFT_ALIGNMENT
                    }
                    add(textArea)
                }
            }
        }
    }
    
    /**
     * Create content area for tool display (legacy - kept for compatibility)
     */
    private fun createToolContentArea(content: String, textColor: Color): JPanel {
        return createWrappingToolContentArea(content, textColor)
    }
    
    private fun formatToolDisplay(toolName: String, input: String, toolResult: com.claudecodechat.models.Content?): String {
        val result = mutableListOf<String>()
        
        // Parse input JSON to extract key parameters
        val params = extractToolParameters(toolName, input)
        val toolDisplay = "$toolName($params)"
        result.add(toolDisplay)
        
        // Add result summary with tree symbol
        if (toolResult != null) {
            val resultSummary = formatToolResult(toolName, toolResult)
            if (resultSummary.isNotEmpty()) {
                result.add("⎿  $resultSummary") // Tree continuation symbol
            }
        }
        
        return result.joinToString("\n")
    }
    
    private fun clearChat() {
        messagesPanel.removeAll()
        messagesPanel.revalidate()
        messagesPanel.repaint()
    }
    
    fun appendCodeToInput(code: String) {
        val currentText = inputArea.text
        val newText = if (currentText.isEmpty()) {
            code
        } else {
            "$currentText\n\n$code"
        }
        inputArea.text = newText
        inputArea.caretPosition = newText.length
        inputArea.requestFocus()
    }
    
    private fun showSessionMenu() {
        val projectPath = project.basePath
        val options = mutableListOf<SessionMenuItem>()
        options.add(SessionMenuItem(label = "Start New Session", type = SessionMenuType.NEW))
        
        // Recent sessions (from file system)
        try {
            if (projectPath != null) {
                val recent = com.claudecodechat.session.SessionHistoryLoader()
                    .getRecentSessionsWithDetails(projectPath, 10)
                if (recent.isNotEmpty()) {
                    options.add(SessionMenuItem(label = "— Recent —", type = SessionMenuType.SEPARATOR))
                    recent.forEach { info ->
                        val preview = info.preview?.replace('\n', ' ')
                        val display = if (!preview.isNullOrBlank()) {
                            "${info.id.take(8)}  ·  ${preview.take(60)}"
                        } else {
                            info.id.take(8)
                        }
                        options.add(SessionMenuItem(label = display, type = SessionMenuType.RESUME, sessionId = info.id))
                    }
                }
            }
        } catch (_: Exception) { }
        
        // Fallback to persisted list if needed
        try {
            val persisted = com.claudecodechat.persistence.SessionPersistence.getInstance(project).getRecentSessionIds()
            if (persisted.isNotEmpty()) {
                if (options.none { it.type == SessionMenuType.RESUME }) {
                    options.add(SessionMenuItem(label = "— Recent —", type = SessionMenuType.SEPARATOR))
                }
                persisted.take(10).forEach { id ->
                    if (options.none { it.sessionId == id }) {
                        options.add(SessionMenuItem(label = id.take(8), type = SessionMenuType.RESUME, sessionId = id))
                    }
                }
            }
        } catch (_: Exception) { }
        
        if (options.isEmpty()) {
            options.add(SessionMenuItem(label = "Start New Session", type = SessionMenuType.NEW))
        }
        
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(options)
            .setTitle("Sessions")
            .setRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    val item = value as SessionMenuItem
                    c.text = item.label
                    c.isEnabled = item.type != SessionMenuType.SEPARATOR
                    return c
                }
            })
            .setItemChosenCallback { item ->
                when (item.type) {
                    SessionMenuType.NEW -> sessionViewModel.startNewSession()
                    SessionMenuType.RESUME -> item.sessionId?.let { sessionViewModel.resumeSession(it) }
                    SessionMenuType.SEPARATOR -> {}
                }
            }
            .createPopup()
        
        popup.showUnderneathOf(sessionTabs)
    }

    private data class SessionMenuItem(
        val label: String,
        val type: SessionMenuType,
        val sessionId: String? = null,
    )

    private enum class SessionMenuType { NEW, RESUME, SEPARATOR }

    private fun addPlusTab(tabs: JBTabbedPane) {
        val plus = JPanel()
        plus.isOpaque = false
        tabs.addTab("+", plus)
    }

    private fun addOrUpdateActiveSessionTab(tabs: JBTabbedPane, title: String?, id: String?) {
        // Real tabs are all except the last plus tab
        val count = tabs.tabCount
        val realCount = if (count == 0) 0 else count - 1
        val displayTitle = (title ?: "test").take(40)
        val secondary = secondaryTextColor()
        val idHtml = if (id != null) " <span style='color: rgb(${secondary.red},${secondary.green},${secondary.blue});'>(${id.take(8)})</span>" else ""
        val htmlTitle = "<html>$displayTitle$idHtml</html>"
        if (realCount == 0) {
            val panel = JPanel()
            panel.isOpaque = false
            panel.putClientProperty("sessionId", id)
            tabs.insertTab(htmlTitle, null, panel, id ?: displayTitle, 0)
            if (tabs.tabCount == 1) addPlusTab(tabs)
            tabs.selectedIndex = 0
        } else {
            val comp = tabs.getComponentAt(0) as? JComponent
            comp?.putClientProperty("sessionId", id)
            tabs.setTitleAt(0, htmlTitle)
            tabs.setToolTipTextAt(0, id ?: displayTitle)
            tabs.selectedIndex = 0
        }
    }

    private fun addSessionTab(tabs: JBTabbedPane, title: String?, id: String?) {
        val displayTitle = (title ?: "(new session)").take(40)
        val secondary = secondaryTextColor()
        val idHtml = if (id != null) " <span style='color: rgb(${secondary.red},${secondary.green},${secondary.blue});'>(${id.take(8)})</span>" else ""
        val htmlTitle = "<html>$displayTitle$idHtml</html>"
        val panel = JPanel()
        panel.isOpaque = false
        panel.putClientProperty("sessionId", id)
        val insertIndex = maxOf(0, tabs.tabCount - 1)
        tabs.insertTab(htmlTitle, null, panel, id ?: displayTitle, insertIndex)
        tabs.selectedIndex = insertIndex
        if (id != null) {
            sessionViewModel.resumeSession(id)
        } else {
            sessionViewModel.startNewSession()
        }
    }

    private fun showSessionAddChooser(tabs: JBTabbedPane) {
        val projectPath = project.basePath
        val options = mutableListOf<SessionMenuItem>()
        options.add(SessionMenuItem(label = "Start New Session", type = SessionMenuType.NEW))
        try {
            if (projectPath != null) {
                val recent = com.claudecodechat.session.SessionHistoryLoader()
                    .getRecentSessionsWithDetails(projectPath, 15)
                if (recent.isNotEmpty()) {
                    options.add(SessionMenuItem(label = "— Recent —", type = SessionMenuType.SEPARATOR))
                    recent.forEach { info ->
                        val preview = info.preview?.replace('\n', ' ')
                        val display = if (!preview.isNullOrBlank()) {
                            "${info.id.take(8)}  ·  ${preview.take(60)}"
                        } else {
                            info.id.take(8)
                        }
                        options.add(SessionMenuItem(label = display, type = SessionMenuType.RESUME, sessionId = info.id))
                    }
                }
            }
        } catch (_: Exception) { }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(options)
            .setTitle("Add Session Tab")
            .setRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    val item = value as SessionMenuItem
                    c.text = item.label
                    c.isEnabled = item.type != SessionMenuType.SEPARATOR
                    return c
                }
            })
            .setItemChosenCallback { item ->
                when (item.type) {
                    SessionMenuType.NEW -> addSessionTab(tabs, title = "(new session)", id = null)
                    SessionMenuType.RESUME -> addSessionTab(tabs, title = null, id = item.sessionId)
                    SessionMenuType.SEPARATOR -> {}
                }
            }
            .createPopup()
        popup.showUnderneathOf(sessionTabs)
    }
    
    private fun extractToolParameters(toolName: String, input: String): String {
        if (input.isEmpty()) return ""
        
        try {
            // Simple JSON parsing for common parameters
            return when (toolName.lowercase()) {
                "read" -> {
                    val filePathMatch = Regex(""""file_path"\s*:\s*"([^"]*)"""").find(input)
                    val fullPath = filePathMatch?.groupValues?.get(1) ?: ""
                    removeProjectPrefix(fullPath)
                }
                "write" -> {
                    val filePathMatch = Regex(""""file_path"\s*:\s*"([^"]*)"""").find(input)
                    val fullPath = filePathMatch?.groupValues?.get(1) ?: ""
                    removeProjectPrefix(fullPath)
                }
                "edit" -> {
                    val filePathMatch = Regex(""""file_path"\s*:\s*"([^"]*)"""").find(input)
                    val fullPath = filePathMatch?.groupValues?.get(1) ?: ""
                    removeProjectPrefix(fullPath)
                }
                "multiedit" -> {
                    val filePathMatch = Regex(""""file_path"\s*:\s*"([^"]*)"""").find(input)
                    val fullPath = filePathMatch?.groupValues?.get(1) ?: ""
                    removeProjectPrefix(fullPath)
                }
                "bash" -> {
                    val commandMatch = Regex(""""command"\s*:\s*"([^"]*)"""").find(input)
                    commandMatch?.groupValues?.get(1)?.take(50) ?: ""
                }
                "grep" -> {
                    val patternMatch = Regex(""""pattern"\s*:\s*"([^"]*)"""").find(input)
                    val pattern = patternMatch?.groupValues?.get(1) ?: ""
                    val pathMatch = Regex(""""path"\s*:\s*"([^"]*)"""").find(input)
                    val path = pathMatch?.groupValues?.get(1)
                    if (path != null) "\"$pattern\" in $path" else "\"$pattern\""
                }
                "glob" -> {
                    val patternMatch = Regex(""""pattern"\s*:\s*"([^"]*)"""").find(input)
                    patternMatch?.groupValues?.get(1) ?: ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            return ""
        }
    }
    
    private fun removeProjectPrefix(fullPath: String): String {
        if (fullPath.isEmpty()) return fullPath
        
        try {
            val projectBasePath = project.basePath
            if (projectBasePath != null && fullPath.startsWith(projectBasePath)) {
                val relativePath = fullPath.substring(projectBasePath.length)
                return if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath
            }
        } catch (e: Exception) {
            // Fallback to original path if anything goes wrong
        }
        
        return fullPath
    }
    
    private fun formatCommandMessage(content: String): String {
        try {
            val commandNameMatch = Regex("<command-name>([^<]+)</command-name>").find(content)
            val commandArgsMatch = Regex("<command-args>([^<]*)</command-args>").find(content)
            
            val commandName = commandNameMatch?.groupValues?.get(1)?.trim() ?: ""
            val commandArgs = commandArgsMatch?.groupValues?.get(1)?.trim() ?: ""
            
            return if (commandName.isNotEmpty()) {
                if (commandArgs.isNotEmpty()) {
                    "$commandName $commandArgs"
                } else {
                    commandName
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            return ""
        }
    }
    
    private fun formatToolResult(toolName: String, toolResult: com.claudecodechat.models.Content): String {
        if (toolResult.isError == true) {
            return "Error: ${toolResult.content ?: toolResult.text ?: "Unknown error"}"
        }
        
        val content = toolResult.content ?: toolResult.text ?: ""
        
        return when (toolName.lowercase()) {
            "read" -> {
                val lines = content.split("\n").size
                "Read $lines lines"
            }
            "write" -> {
                if (content.contains("successfully") || content.contains("written")) {
                    "File written successfully"
                } else {
                    "File written"
                }
            }
            "edit", "multiedit" -> {
                // Try to extract diff-like information from the result
                formatEditResult(content)
            }
            "bash" -> {
                val lines = content.trim().split("\n")
                if (lines.size <= 3 && content.length <= 100) {
                    content.trim()
                } else {
                    "Command executed (${lines.size} lines output)"
                }
            }
            "grep" -> {
                if (content.startsWith("Found")) {
                    content.split("\n").first() // Just the "Found X files" line
                } else {
                    val lines = content.split("\n")
                    "Found ${lines.size} matches"
                }
            }
            "glob" -> {
                val lines = content.split("\n")
                "Found ${lines.size} files"
            }
            else -> {
                // Generic result formatting
                if (content.length <= 100) {
                    content.trim()
                } else {
                    content.take(97) + "..."
                }
            }
        }
    }
    
    private fun formatEditResult(content: String): String {
        // Extract diff-like information from edit results
        try {
            // Look for diff-style content showing changes
            val lines = content.split("\n")
            val diffLines = mutableListOf<String>()
            
            var inDiff = false
            var addedLines = 0
            var removedLines = 0
            
            for (line in lines) {
                when {
                    line.contains("→") -> {
                        // This is a line from the diff output
                        inDiff = true
                        diffLines.add(line)
                    }
                    line.startsWith("+") && inDiff -> {
                        addedLines++
                        // Extract the actual added content without the + prefix
                        val addedText = line.substring(1).trim()
                        if (addedText.isNotEmpty()) {
                            diffLines.add("+ $addedText")
                        }
                    }
                    line.startsWith("-") && inDiff -> {
                        removedLines++
                        // Don't show removed lines in summary, just count them
                    }
                }
            }
            
            if (diffLines.isNotEmpty()) {
                val summary = if (addedLines > 0) {
                    "Applied edit with $addedLines additions"
                } else {
                    "Applied edit"
                }
                
                return summary
            }
            
            // Fallback: check for "Applied X edit(s)" pattern
            val editMatch = Regex("""Applied (\d+) edit""").find(content)
            if (editMatch != null) {
                val count = editMatch.groupValues[1]
                return "Applied $count edit${if (count != "1") "s" else ""}"
            }
            
        } catch (e: Exception) {
            // Ignore parsing errors and fall back
        }
        
        return "File edited"
    }
    
    // Completion list cell renderer
    private inner class CompletionListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            // Get the actual completion item from currentCompletionState
            currentCompletionState?.let { state ->
                if (index >= 0 && index < state.items.size) {
                    val item = state.items[index]
                    text = when (item) {
                        is com.claudecodechat.completion.CompletionItem.SlashCommand -> {
                            "/${item.name} - ${item.description}"
                        }
                        is com.claudecodechat.completion.CompletionItem.FileReference -> {
                            "@${item.relativePath}"
                        }
                        else -> value.toString()
                    }
                }
            }
            
            return this
        }
    }
}
