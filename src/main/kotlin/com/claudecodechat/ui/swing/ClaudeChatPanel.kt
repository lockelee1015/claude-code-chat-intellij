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
                // Group messages to handle tool use/result pairs
                val groupedMessages = groupToolMessages(messages)
                
                for (group in groupedMessages) {
                    when (group.type) {
                        "user" -> {
                            val userText = group.content
                            addMessageWithIcon(doc, style, userText, MessageType.USER)
                        }
                        "assistant" -> {
                            addMessageWithIcon(doc, style, group.content, MessageType.ASSISTANT)
                        }
                        "tool_interaction" -> {
                            // Display tool use and result together
                            addToolInteraction(doc, style, group.toolUse, group.toolResult)
                        }
                        "error" -> {
                            addMessageWithIcon(doc, style, group.content, MessageType.ERROR)
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
                            groups.add(MessageGroup(type = "user", content = userText))
                        }
                    }
                }
                MessageType.ASSISTANT -> {
                    message.message?.content?.forEach { content ->
                        when (content.type) {
                            com.claudecodechat.models.ContentType.TEXT -> {
                                if (content.text != null && content.text.isNotEmpty()) {
                                    groups.add(MessageGroup(type = "assistant", content = content.text))
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
    
    private fun addToolInteraction(doc: StyledDocument, style: MutableAttributeSet, toolUse: com.claudecodechat.models.Content?, toolResult: com.claudecodechat.models.Content?) {
        // Use same layout as messages: left margin + icon area
        val leftMargin = "  " // 2 spaces for left margin
        val iconWidth = 4      // 4 chars total for icon area
        
        // Add left margin
        doc.insertString(doc.length, leftMargin, null)
        
        // Add tool icon with fixed width
        val iconStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        val isError = toolResult?.isError == true
        val iconColor = if (isError) Color.decode("#FF6B6B") else Color.decode("#50C878") // Red for error, green for success
        StyleConstants.setForeground(iconStyle, iconColor)
        StyleConstants.setBold(iconStyle, true)
        
        // Icon area: icon + padding to fixed width
        val iconArea = "⏺".padEnd(iconWidth) // Record button symbol with padding
        doc.insertString(doc.length, iconArea, iconStyle)
        
        // Format tool display
        val toolName = toolUse?.name ?: "Unknown"
        val inputString = toolUse?.input?.toString() ?: ""
        val formattedDisplay = formatToolDisplay(toolName, inputString, toolResult)
        
        val textColor = JBColor.foreground() // Use normal text color, not secondary
        val textStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        StyleConstants.setForeground(textStyle, textColor)
        
        val lines = formattedDisplay.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            
            if (i == 0) {
                // First line: tool name should be bold
                val parts = line.split("(", limit = 2)
                if (parts.size == 2) {
                    // Tool name part (bold)
                    StyleConstants.setBold(textStyle, true)
                    doc.insertString(doc.length, parts[0], textStyle)
                    
                    // Parameters part (normal)
                    StyleConstants.setBold(textStyle, false)
                    doc.insertString(doc.length, "(${parts[1]}", textStyle)
                } else {
                    StyleConstants.setBold(textStyle, true)
                    doc.insertString(doc.length, line, textStyle)
                }
            } else {
                // Continuation lines with proper indentation matching icon area
                StyleConstants.setBold(textStyle, false)
                val continuationIndent = leftMargin + " ".repeat(iconWidth)
                doc.insertString(doc.length, "\n$continuationIndent$line", textStyle)
            }
        }
        
        doc.insertString(doc.length, "\n\n", null)
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
                    "Updated CLAUDE.md with $addedLines additions"
                } else {
                    "Applied edit"
                }
                
                // Return summary + key diff lines
                val result = mutableListOf(summary)
                
                // Add a few key diff lines to show what was changed
                diffLines.take(8).forEach { diffLine ->
                    when {
                        diffLine.contains("→") -> {
                            // Line number - extract the actual content
                            val match = Regex("""\s*(\d+)→(.*)$""").find(diffLine)
                            if (match != null) {
                                val lineContent = match.groupValues[2].trim()
                                if (lineContent.isNotEmpty() && !lineContent.startsWith("#") && !lineContent.startsWith("`")) {
                                    result.add("    ${match.groupValues[1]}")
                                }
                            }
                        }
                        diffLine.startsWith("+") -> {
                            val addedContent = diffLine.substring(1).trim()
                            if (addedContent.isNotEmpty()) {
                                result.add("    + $addedContent")
                            }
                        }
                    }
                }
                
                return result.joinToString("\n")
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
    
    private fun addStyledText(doc: StyledDocument, style: MutableAttributeSet, text: String, color: Color, bold: Boolean) {
        StyleConstants.setForeground(style, color)
        StyleConstants.setBold(style, bold)
        doc.insertString(doc.length, text, style)
    }
    
    private fun addMessageWithIcon(doc: StyledDocument, style: MutableAttributeSet, text: String, messageType: MessageType) {
        // Create fixed-width layout with proper left margin: margin + icon area + text area
        val leftMargin = "  " // 2 spaces for left margin
        val iconWidth = 4      // 4 chars total for icon area
        
        // Add left margin
        doc.insertString(doc.length, leftMargin, null)
        
        // Add icon with fixed width
        val iconStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        val (iconText, iconColor) = when (messageType) {
            MessageType.USER -> ">" to JBColor.gray // Secondary color for user icon
            MessageType.ASSISTANT -> "●" to Color.decode("#FFFFFF") // White circle
            MessageType.ERROR -> "●" to Color.decode("#FF6B6B") // Red circle
            else -> "●" to JBColor.foreground()
        }
        
        StyleConstants.setForeground(iconStyle, iconColor)
        StyleConstants.setBold(iconStyle, true)
        
        // Icon area: icon + padding to fixed width
        val iconArea = iconText.padEnd(iconWidth)
        doc.insertString(doc.length, iconArea, iconStyle)
        
        // Set text color
        val textColor = when (messageType) {
            MessageType.USER -> JBColor.gray // Secondary color for user messages
            MessageType.ERROR -> Color.decode("#FF6B6B")
            else -> JBColor.foreground()
        }
        
        // Right side: plain text without markdown rendering
        // Continuation lines use left margin + icon width for proper alignment
        val continuationIndent = leftMargin + " ".repeat(iconWidth)
        addPlainText(doc, text, textColor, continuationIndent)
        
        doc.insertString(doc.length, "\n\n", null)
    }
    
    private fun addPlainText(doc: StyledDocument, text: String, color: Color, indent: String) {
        val style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        StyleConstants.setForeground(style, color)
        StyleConstants.setBold(style, false)
        
        val lines = text.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            if (i == 0) {
                doc.insertString(doc.length, line, style)
            } else {
                doc.insertString(doc.length, "\n$indent$line", style)
            }
        }
    }
    
    
    private fun addToolMessage(doc: StyledDocument, style: MutableAttributeSet, text: String, isError: Boolean = false) {
        // Use same layout as messages: left margin + icon area
        val leftMargin = "  " // 2 spaces for left margin
        val iconWidth = 4      // 4 chars total for icon area
        
        // Add left margin
        doc.insertString(doc.length, leftMargin, null)
        
        // Add tool icon with fixed width
        val iconStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        val iconColor = if (isError) Color.decode("#FF6B6B") else Color.decode("#50C878") // Green or red
        StyleConstants.setForeground(iconStyle, iconColor)
        StyleConstants.setBold(iconStyle, true)
        
        // Icon area: icon + padding to fixed width
        val iconArea = "●".padEnd(iconWidth)
        doc.insertString(doc.length, iconArea, iconStyle)
        
        // Add tool text with proper indentation
        val textColor = if (isError) Color.decode("#FF6B6B") else Color.decode("#808080")
        val textStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        StyleConstants.setForeground(textStyle, textColor)
        
        val lines = text.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            
            // First line (title) should be bold
            StyleConstants.setBold(textStyle, i == 0)
            
            if (i == 0) {
                doc.insertString(doc.length, line, textStyle)
            } else {
                // Continuation lines with proper indentation matching icon area
                val continuationIndent = leftMargin + " ".repeat(iconWidth)
                doc.insertString(doc.length, "\n$continuationIndent$line", textStyle)
            }
        }
        
        doc.insertString(doc.length, "\n\n", null)
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