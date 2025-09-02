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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.text.*

class ClaudeChatPanel(private val project: Project) : JBPanel<ClaudeChatPanel>() {
    private val sessionViewModel = SessionViewModel.getInstance(project)
    private val completionManager = CompletionManager(project)
    private val settings = ClaudeSettings.getInstance()
    
    // UI Components
    private val chatArea: JTextPane
    private val inputArea: JBTextArea // 多行输入区域
    private val currentFileLabel: JLabel // 显示当前文件和选中行（在发送栏最左侧）
    private val sendButton: JButton
    private val clearButton: JButton
    private val sessionButton: JButton
    private val modelComboBox: JComboBox<String>
    private val scrollPane: JBScrollPane
    
    // Completion system
    private var completionPanel: JBPanel<*>
    private var completionList: JList<String>
    private var completionScrollPane: JScrollPane
    private var currentCompletionState: CompletionState? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        layout = BorderLayout()
        
        // Initialize UI components
        chatArea = createChatArea()
        inputArea = createInputArea()
        currentFileLabel = createCurrentFileLabel()
        sendButton = createSendButton()
        clearButton = createClearButton()
        sessionButton = createSessionButton()
        modelComboBox = createModelComboBox()
        scrollPane = createScrollPane()
        
        // Initialize completion components
        completionList = createCompletionList()
        completionScrollPane = createCompletionScrollPane()
        completionPanel = createCompletionPanel()
        
        setupLayout()
        setupEventHandlers()
        observeViewModel()
        loadInitialData()
    }
    
    private fun createChatArea(): JTextPane {
        return JTextPane().apply {
            isEditable = false
            background = JBColor.background()
            contentType = "text/html"
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
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
    
    
    private fun createScrollPane(): JBScrollPane {
        return JBScrollPane(chatArea).apply {
            preferredSize = Dimension(600, 400)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
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
            add(scrollPane, BorderLayout.CENTER)
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
            val doc = chatArea.styledDocument
            val style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
            
            // Clear existing content
            chatArea.text = ""
            
            try {
                for (message in messages) {
                    when (message.type) {
                        MessageType.USER -> {
                            val userText = message.message?.content?.firstOrNull()?.text ?: ""
                            addStyledText(doc, style, "[You]: ", Color.decode("#4A90E2"), true)
                            addStyledText(doc, style, "$userText\n\n", JBColor.foreground(), false)
                        }
                        MessageType.ASSISTANT -> {
                            addStyledText(doc, style, "[Claude]: ", Color.decode("#50C878"), true)
                            message.message?.content?.forEach { content ->
                                when (content.type) {
                                    com.claudecodechat.models.ContentType.TEXT -> {
                                        if (content.text != null) {
                                            addStyledText(doc, style, "${content.text}\n", JBColor.foreground(), false)
                                        }
                                    }
                                    com.claudecodechat.models.ContentType.TOOL_USE -> {
                                        // Display tool calls
                                        addStyledText(doc, style, "🛠️ Tool: ${content.name ?: "unknown"}\n", Color.decode("#FFA500"), true)
                                        if (content.input != null) {
                                            addStyledText(doc, style, "  Input: ${content.input}\n", Color.decode("#808080"), false)
                                        }
                                    }
                                    com.claudecodechat.models.ContentType.TOOL_RESULT -> {
                                        // Display tool results
                                        val resultColor = if (content.isError == true) Color.decode("#FF6B6B") else Color.decode("#90EE90")
                                        val resultPrefix = if (content.isError == true) "❌ Tool Error: " else "✅ Tool Result: "
                                        addStyledText(doc, style, resultPrefix, resultColor, true)
                                        if (content.content != null) {
                                            addStyledText(doc, style, "${content.content}\n", resultColor, false)
                                        } else if (content.text != null) {
                                            addStyledText(doc, style, "${content.text}\n", resultColor, false)
                                        }
                                    }
                                }
                            }
                            addStyledText(doc, style, "\n", JBColor.foreground(), false)
                        }
                        MessageType.ERROR -> {
                            val errorText = message.error?.message ?: "Unknown error"
                            addStyledText(doc, style, "[Error]: ", Color.decode("#FF6B6B"), true)
                            addStyledText(doc, style, "$errorText\n\n", Color.decode("#FF6B6B"), false)
                        }
                        else -> {
                            // Handle other message types if needed
                        }
                    }
                }
                
                // Scroll to bottom
                chatArea.caretPosition = doc.length
                
            } catch (e: BadLocationException) {
                e.printStackTrace()
            }
        }
    }
    
    private fun addStyledText(doc: StyledDocument, style: MutableAttributeSet, text: String, color: Color, bold: Boolean) {
        StyleConstants.setForeground(style, color)
        StyleConstants.setBold(style, bold)
        doc.insertString(doc.length, text, style)
    }
    
    private fun showSessionMenu() {
        val sessions = sessionViewModel.getRecentSessionsWithDetails()
        val menuItems = mutableListOf<String>()
        
        menuItems.add("🆕 New Session")
        menuItems.add("---") // Separator
        
        sessions.forEach { session ->
            menuItems.add("📝 ${session.id.take(8)}... - ${session.preview.take(50)}")
        }
        
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(menuItems)
            .setTitle("Sessions")
            .setItemChosenCallback { selectedItem ->
                when {
                    selectedItem.startsWith("🆕") -> {
                        sessionViewModel.startNewSession()
                    }
                    selectedItem.startsWith("📝") -> {
                        val sessionId = sessions.find { 
                            selectedItem.contains(it.id.take(8)) 
                        }?.id
                        if (sessionId != null) {
                            sessionViewModel.resumeSession(sessionId)
                        }
                    }
                }
            }
            .createPopup()
        
        popup.showUnderneathOf(sessionButton)
    }
    
    
    private fun clearChat() {
        val choice = Messages.showYesNoDialog(
            project,
            "Are you sure you want to clear the chat history?",
            "Clear Chat History",
            Messages.getQuestionIcon()
        )
        
        if (choice == Messages.YES) {
            sessionViewModel.clearChat()
        }
    }
    
    fun appendCodeToInput(code: String) {
        ApplicationManager.getApplication().invokeLater {
            val currentText = inputArea.text
            val separator = if (currentText.isNotEmpty() && !currentText.endsWith("\n")) "\n\n" else ""
            inputArea.text = currentText + separator + "```\n" + code + "\n```"
            inputArea.caretPosition = inputArea.text.length
            inputArea.requestFocusInWindow()
        }
    }
    
    fun dispose() {
        scope.cancel()
        hideCompletion()
    }
    
    /**
     * Custom list cell renderer for completion items with file icons
     */
    private inner class CompletionListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            // Get the completion item for this index
            val state = currentCompletionState
            if (state != null && index >= 0 && index < state.items.size) {
                val item = state.items[index]
                when (item) {
                    is com.claudecodechat.completion.CompletionItem.FileReference -> {
                        // Use IntelliJ's file icon
                        try {
                            val file = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(
                                java.io.File(item.path), false
                            )
                            if (file != null) {
                                val fileType = com.intellij.openapi.fileTypes.FileTypeManager
                                    .getInstance().getFileTypeByFile(file)
                                icon = fileType.icon
                            }
                        } catch (e: Exception) {
                            // Fallback: no icon
                            icon = null
                        }
                        text = "${item.relativePath} ${if (item.fileType != null) "(${item.fileType})" else ""}"
                    }
                    is com.claudecodechat.completion.CompletionItem.SlashCommand -> {
                        // No icon for slash commands
                        icon = null
                        text = "/${item.name} - ${item.description}"
                    }
                }
            }
            
            return this
        }
    }
}