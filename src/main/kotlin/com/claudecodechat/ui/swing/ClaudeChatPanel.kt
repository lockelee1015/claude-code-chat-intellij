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
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBIntSpinner
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
    private val sessionButton: JButton
    private val modelComboBox: JComboBox<String>
    
    // Completion system
    private var completionPanel: JBPanel<*>
    private var completionList: JList<String>
    private var completionScrollPane: JScrollPane
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
        sessionButton = createSessionButton()
        modelComboBox = createModelComboBox()
        // scrollPane is now chatScrollPane
        
        // Initialize completion components
        completionList = createCompletionList()
        completionScrollPane = createCompletionScrollPane()
        completionPanel = createCompletionPanel()
        
        setupLayout()
        setupEventHandlers()
        observeViewModel()
        loadInitialData()
    }
    
    private fun createMessagesPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
            border = JBUI.Borders.empty(10)
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
        return JButton("Clear").apply {
            addActionListener { clearChat() }
        }
    }
    
    private fun createSessionButton(): JButton {
        return JButton("Sessions").apply {
            addActionListener { showSessionMenu() }
        }
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
    
    private fun createCompletionScrollPane(): JScrollPane {
        return JScrollPane(completionList).apply {
            preferredSize = Dimension(600, 150)
            maximumSize = Dimension(Int.MAX_VALUE, 150)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty()
            )
        }
    }
    
    private fun createCompletionPanel(): JBPanel<*> {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(JLabel("Completions").apply {
                font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                foreground = JBColor.gray
                border = JBUI.Borders.empty(2, 5)
            }, BorderLayout.NORTH)
            add(completionScrollPane, BorderLayout.CENTER)
            
            // Initially hidden
            isVisible = false
            preferredSize = Dimension(0, 0)
        }
    }
    
    private fun setupLayout() {
        // Top toolbar
        val toolbarPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
            border = JBUI.Borders.empty(5)
            add(sessionButton)
            add(Box.createHorizontalGlue())
            add(clearButton)
        }
        
        // Main input area (multi-line)
        val inputScrollPane = JBScrollPane(inputArea).apply {
            preferredSize = Dimension(600, 100)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        // Bottom controls panel with current file on left, controls on right
        val controlsPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            
            // Left: current file/select line info
            add(currentFileLabel, BorderLayout.WEST)
            
            // Right: model select + send button
            val rightPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
                add(JLabel("model select"))
                add(modelComboBox)
                add(sendButton)
            }
            add(rightPanel, BorderLayout.EAST)
        }
        
        // Input section (input + controls)
        val inputSection = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(5)
            )
            add(inputScrollPane, BorderLayout.CENTER)
            add(controlsPanel, BorderLayout.SOUTH)
        }
        
        // Center section (chat + completion)
        val centerSection = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(chatScrollPane, BorderLayout.CENTER)
            add(completionPanel, BorderLayout.SOUTH)
        }
        
        // Main layout
        add(toolbarPanel, BorderLayout.NORTH)
        add(centerSection, BorderLayout.CENTER)
        add(inputSection, BorderLayout.SOUTH)
    }
    
    private fun setupEventHandlers() {
        // Listen for editor file changes
        updateCurrentFileInfo()
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
        
        scope.launch {
            sessionViewModel.currentSession.collect { session ->
                sessionButton.text = session?.sessionId?.take(8) ?: "New Session"
            }
        }
        
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
                if (completionPanel.isVisible) {
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
                if (completionPanel.isVisible) {
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
        
        // Create simple string list for the model (actual display is handled by cell renderer)
        val items = state.items.mapIndexed { index, _ -> "Item $index" }
        
        val listModel = DefaultListModel<String>()
        items.forEach { listModel.addElement(it) }
        
        completionList.model = listModel
        completionList.selectedIndex = state.selectedIndex
        
        // Show the completion panel
        completionPanel.isVisible = true
        completionPanel.preferredSize = Dimension(completionPanel.width, 150)
        
        // Ensure the selected item is visible
        if (state.selectedIndex >= 0) {
            completionList.ensureIndexIsVisible(state.selectedIndex)
        }
        
        // Revalidate the layout
        completionPanel.revalidate()
        completionPanel.repaint()
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
        completionPanel.isVisible = false
        completionPanel.preferredSize = Dimension(0, 0)
        completionPanel.revalidate()
        completionPanel.repaint()
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
                        messagesPanel.add(Box.createVerticalStrut(4)) // Reduced spacing between messages
                    }
                }
                
                // Add flexible space at the bottom
                messagesPanel.add(Box.createVerticalGlue())
                
                // Refresh the display
                messagesPanel.revalidate()
                messagesPanel.repaint()
                
                // Scroll to bottom
                SwingUtilities.invokeLater {
                    val scrollBar = chatScrollPane.verticalScrollBar
                    scrollBar.value = scrollBar.maximum
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
            border = JBUI.Borders.empty(2, 0, 2, 0) // Small top/bottom padding only
            background = JBColor.background()
            
            // Icon area (left side) - fixed width, top aligned
            val iconPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = JBColor.background()
                preferredSize = Dimension(20, -1) // Fixed width
                
                val iconLabel = JBLabel(iconText).apply {
                    foreground = iconColor
                    font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                    horizontalAlignment = SwingConstants.LEFT
                    verticalAlignment = SwingConstants.TOP // Top align the icon
                }
                add(iconLabel, BorderLayout.NORTH) // Put icon at top
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
        // Show session selection menu
        // This can be expanded later with actual session management
        Messages.showInfoMessage(project, "Session management coming soon!", "Sessions")
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
