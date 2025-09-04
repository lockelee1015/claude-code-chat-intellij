package com.claudecodechat.ui.swing

import com.claudecodechat.services.ChatHistoryService
import com.claudecodechat.cli.ClaudeCliService
import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.MessageType
import com.claudecodechat.settings.ClaudeSettings
import com.claudecodechat.state.SessionViewModel
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
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*
import com.claudecodechat.ui.swing.renderers.ToolRendererFactory
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.text.*
import javax.swing.UIManager
import com.claudecodechat.ui.markdown.MarkdownRenderer
import com.claudecodechat.ui.markdown.MarkdownRenderConfig

class ClaudeChatPanel(private val project: Project) : JBPanel<ClaudeChatPanel>() {
    private val sessionViewModel = SessionViewModel.getInstance(project)
    
    // UI Components
    private val messagesPanel: JPanel
    private val chatScrollPane: JScrollPane
    private val sessionTabs: JBTabbedPane
    private val chatInputBar: ChatInputBar
    
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        layout = BorderLayout()
        background = JBColor.background() // 设置主面板背景色以适应主题
        
        // Initialize UI components
        messagesPanel = createMessagesPanel()
        chatScrollPane = createChatScrollPane()
        sessionTabs = createSessionTabs()
        chatInputBar = createChatInputBar()
        
        
        setupLayout()
        setupEventHandlers()
        observeViewModel()
        loadInitialData()
    }
    
    private fun createMessagesPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = getMessageAreaBackgroundColor() // 使用主题自适应背景色
            border = JBUI.Borders.empty(5, 10, 5, 10) // reduced padding
        }
    }
    
    /**
     * 获取消息区域的主题自适应背景色
     */
    private fun getMessageAreaBackgroundColor(): Color {
        return if (JBColor.isBright()) {
            Color.WHITE // 明亮主题使用白色
        } else {
            Color(30, 30, 30) // 深色主题使用深灰色
        }
    }
    
    private fun createChatScrollPane(): JScrollPane {
        return JBScrollPane(messagesPanel).apply {
            preferredSize = Dimension(600, 400)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.empty()
            background = getMessageAreaBackgroundColor() // 使用主题自适应背景色
            viewport.background = getMessageAreaBackgroundColor() // 确保视口背景也是主题色
        }
    }
    
    private fun createSessionTabs(): JBTabbedPane {
        val tabs = JBTabbedPane()
        tabs.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        // No-op: tabs replaced by toolwindow tabs. Keep method for compatibility if needed.
        return tabs
    }
    
    private fun createChatInputBar(): ChatInputBar {
        return ChatInputBar(
            project = project,
            onSend = { text, model ->
            val modelToUse = if (model == "auto") "" else model
            sessionViewModel.sendPrompt(text, modelToUse)
            },
            onStop = {
                sessionViewModel.stopCurrentRequest()
            }
        )
    }
    
    private fun setupLayout() {
        // Remove the unused toolbar since it's empty and causing top spacing
        // val toolbarPanel = ... (removed to eliminate top spacing)
        
        // Main input area handled by ChatInputBar now
        
        // Input section: reuse shared ChatInputBar (with completion and file info)
        val inputSection = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background() // 输入区域背景色
            border = JBUI.Borders.empty(0, 10, 0, 10)
            add(chatInputBar, BorderLayout.CENTER)
        }
        
        // Center section (chat + completion)
        val centerSection = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background() // 中心区域背景色
            add(chatScrollPane, BorderLayout.CENTER)
        }
        
        // Main layout - no toolbar needed
        add(centerSection, BorderLayout.CENTER)
        add(inputSection, BorderLayout.SOUTH)
    }
    
    private fun setupEventHandlers() {
        // File info tracking is now handled by ChatInputBar
    }
    
    private fun observeViewModel() {
        scope.launch {
            sessionViewModel.messages.collect { messages ->
                updateChatDisplay(messages)
            }
        }
        
        scope.launch {
            sessionViewModel.isLoading.collect { isLoading ->
                if (!isLoading) {
                    chatInputBar.hideLoading()
                }
            }
        }
        
        scope.launch {
            sessionViewModel.sessionMetrics.collect { metrics ->
                println("DEBUG: sessionMetrics collected - input=${metrics.totalInputTokens}, output=${metrics.totalOutputTokens}, cacheRead=${metrics.cacheReadInputTokens}, cacheCreation=${metrics.cacheCreationInputTokens}")
                println("DEBUG: Metrics summary - total=${metrics.totalInputTokens + metrics.totalOutputTokens}, wasResumed=${metrics.wasResumed}")
                chatInputBar.updateTokenUsage(
                    metrics.totalInputTokens, 
                    metrics.totalOutputTokens,
                    metrics.cacheReadInputTokens,
                    metrics.cacheCreationInputTokens
                )
            }
        }
        
        // ToolWindow manages tabs now; no need to reflect current session in local tabs
        
        // Completion state is now handled by ChatInputBar
    }
    
    private fun loadInitialData() {
        // Load any initial data if needed
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
                        "tool_interaction" -> ToolRendererFactory.createToolCard(
                            group.toolUse?.name ?: "Unknown",
                            group.toolUse?.input,
                            group.toolResult?.content ?: group.toolResult?.text ?: "",
                            group.toolResult?.isError == true
                        )
                        "error" -> createMessageComponent("●", Color.decode("#FF6B6B"), group.content, Color.decode("#FF6B6B"))
                        else -> null
                    }
                    
                    messageComponent?.let {
                        messagesPanel.add(it)
                        messagesPanel.add(Box.createVerticalStrut(8)) // 适当的消息间距
                    }
                }
                
                // 添加垂直胶水组件，将消息推到顶部，防止消息过少时过度拉伸
                messagesPanel.add(Box.createVerticalGlue())
                
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
            border = JBUI.Borders.empty(4, 0, 4, 0) // 适量的内边距
            background = getMessageAreaBackgroundColor() // 使用主题自适应背景色
            
            // 设置最大高度以防止过度拉伸
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            
            // Icon area (left side) - fixed width, top aligned
            val iconPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = getMessageAreaBackgroundColor() // 使用主题自适应背景色
                preferredSize = Dimension(20, -1) // 统一宽度与工具图标一致
                maximumSize = Dimension(20, Int.MAX_VALUE)
                
                val iconLabel = JBLabel(iconText).apply {
                    foreground = iconColor
                    font = Font(Font.MONOSPACED, Font.BOLD, 12)  // 调整为12号字体
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
     * Create a modern tool card based on tool type
     */
    private fun createToolCard(toolUse: com.claudecodechat.models.Content?, toolResult: com.claudecodechat.models.Content?): JPanel {
        if (toolUse == null) return JPanel()
        
        val toolName = toolUse.name ?: ""
        val toolInput = toolUse.input?.toString() ?: ""
        val toolOutput = toolResult?.content ?: toolResult?.text ?: ""
        val hasError = toolResult?.isError == true
        
        return when (toolName.lowercase()) {
            "edit", "multiedit" -> createDiffCard(toolName, toolInput, toolOutput, hasError)
            "read" -> createEditorCard(toolName, toolInput, toolOutput, hasError)
            "bash" -> createShellCard(toolName, toolInput, toolOutput, hasError)
            "write" -> createWriteCard(toolName, toolInput, toolOutput, hasError)
            "todowrite" -> createTodoCard(toolName, toolUse, toolOutput, hasError)
            "grep", "glob" -> createSearchCard(toolName, toolInput, toolOutput, hasError)
            else -> createGenericCard(toolName, toolInput, toolOutput, hasError)
        }
    }
    
    /**
     * Get background color for tool cards with dark mode support
     */
    private fun getToolCardBackgroundColor(hasError: Boolean, isInProgress: Boolean): Color {
        return when {
            hasError -> if (JBColor.isBright()) {
                Color(255, 235, 235, 180)     // 明亮模式：浅红色透明
            } else {
                Color(80, 30, 30, 180)        // 深色模式：深红色透明
            }
            isInProgress -> if (JBColor.isBright()) {
                Color(235, 245, 255, 180)     // 明亮模式：浅蓝色透明
            } else {
                Color(30, 40, 80, 180)        // 深色模式：深蓝色透明
            }
            else -> if (JBColor.isBright()) {
                Color(235, 255, 235, 180)     // 明亮模式：浅绿色透明
            } else {
                Color(40, 60, 40, 160)        // 深色模式：更低调的深绿色透明
            }
        }
    }

    /**
     * Create base tool card with consistent styling and status colors
     */
    private fun createBaseCard(title: String, hasError: Boolean = false, isInProgress: Boolean = false): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = createToolCardBorder()
            background = JBColor.background()
            maximumSize = Dimension(Int.MAX_VALUE, 300) // Limit height
            
            // Header with status color background with dark mode support
            val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = getToolCardBackgroundColor(hasError, isInProgress)
                border = JBUI.Borders.empty(8, 12)
                
                val titleLabel = JBLabel(title).apply {
                    foreground = JBColor.foreground()
                    font = Font(Font.MONOSPACED, Font.BOLD, 12)
                }
                
                add(titleLabel, BorderLayout.WEST)
            }
            
            add(header, BorderLayout.NORTH)
        }
    }
    
    /**
     * Create rounded border for tool cards
     */
    private fun createToolCardBorder(): javax.swing.border.Border {
        return object : javax.swing.border.AbstractBorder() {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBColor.namedColor("Component.borderColor", JBColor.border())
                g2.stroke = BasicStroke(1.0f)
                
                g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8)
                g2.dispose()
            }
            
            override fun getBorderInsets(c: Component): Insets {
                return Insets(1, 1, 1, 1)
            }
        }
    }

    /**
     * Create diff card for file edits
     */
    private fun createDiffCard(toolName: String, toolInput: String, toolOutput: String, hasError: Boolean): JPanel {
        val filePath = extractToolParameters(toolName, toolInput)
        val title = if (filePath.isNotEmpty()) "$toolName($filePath)" else toolName
        
        val card = createBaseCard(title, hasError, false)
        
        if (hasError) {
            val errorContent = JTextArea(toolOutput).apply {
                foreground = Color(220, 38, 38)
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(8)
            }
            card.add(JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 120)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        } else {
            val diffContent = JTextArea("File modified successfully").apply {
                foreground = Color(34, 197, 94)
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                border = JBUI.Borders.empty(8)
            }
            card.add(diffContent, BorderLayout.CENTER)
        }
        
        return card
    }
    
    /**
     * Create editor card for file reading
     */
    private fun createEditorCard(toolName: String, toolInput: String, toolOutput: String, hasError: Boolean): JPanel {
        val filePath = extractToolParameters(toolName, toolInput)
        val title = if (filePath.isNotEmpty()) "$toolName($filePath)" else toolName
        
        val card = createBaseCard(title, hasError, false)
        
        if (hasError) {
            val errorContent = JTextArea(toolOutput).apply {
                foreground = Color(220, 38, 38)
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(8)
            }
            card.add(JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 120)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        } else {
            val lines = toolOutput.split("\n")
            val previewLines = lines.take(8).joinToString("\n")
            val displayText = if (lines.size > 8) {
                "$previewLines\n... +${lines.size - 8} more lines"
            } else {
                previewLines
            }
            
            val editorContent = JTextArea(displayText).apply {
                foreground = JBColor.foreground()
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = false
                border = JBUI.Borders.empty(8)
            }
            
            card.add(JBScrollPane(editorContent).apply {
                preferredSize = Dimension(-1, 200)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        }
        
        return card
    }
    
    /**
     * Create shell card for bash execution
     */
    private fun createShellCard(toolName: String, toolInput: String, toolOutput: String, hasError: Boolean): JPanel {
        val command = extractToolParameters(toolName, toolInput)
        val title = if (command.isNotEmpty()) "$toolName($command)" else toolName
        
        val card = createBaseCard(title, hasError, false)
        
        val textColor = if (hasError) Color(220, 38, 38) else JBColor.foreground()
        val bgColor = JBColor.background()
        
        val shellContent = JTextArea("$ $command\n$toolOutput").apply {
            foreground = textColor
            background = bgColor
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = false
            border = JBUI.Borders.empty(8)
        }
        
        card.add(JBScrollPane(shellContent).apply {
            preferredSize = Dimension(-1, 150)
            border = JBUI.Borders.empty()
        }, BorderLayout.CENTER)
        
        return card
    }
    
    /**
     * Create write card for file writing
     */
    private fun createWriteCard(toolName: String, toolInput: String, toolOutput: String, hasError: Boolean): JPanel {
        val filePath = extractToolParameters(toolName, toolInput)
        val title = if (filePath.isNotEmpty()) "$toolName($filePath)" else toolName
        
        val card = createBaseCard(title, hasError, false)
        
        val statusText = if (hasError) {
            toolOutput
        } else {
            "File written successfully"
        }
        
        val statusContent = JBLabel(statusText).apply {
            foreground = if (hasError) Color(220, 38, 38) else Color(34, 197, 94)
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            border = JBUI.Borders.empty(8)
        }
        
        card.add(statusContent, BorderLayout.CENTER)
        return card
    }
    
    /**
     * Create todo card for todo_write tool
     */
    private fun createTodoCard(toolName: String, toolUse: com.claudecodechat.models.Content?, toolOutput: String, hasError: Boolean): JPanel {
        val card = createBaseCard("TodoWrite", hasError, false)
        
        if (hasError) {
            val errorContent = JTextArea(toolOutput).apply {
                foreground = Color(220, 38, 38)
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(8)
            }
            card.add(JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 120)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        } else {
            // Create todo list using Swing components - no scrolling
            val todoPanel = createTodoListPanel(toolUse?.input)
            card.add(todoPanel, BorderLayout.CENTER)
        }
        
        return card
    }
    
    /**
     * Create todo list panel using Swing components with JetBrains icons
     */
    private fun createTodoListPanel(input: JsonElement?): JPanel {
        val panel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
        }
        
        // Add header
        val headerLabel = JBLabel("Todos have been modified successfully.").apply {
            foreground = JBColor.foreground()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(headerLabel)
        panel.add(Box.createVerticalStrut(8))
        
        try {
            if (input is JsonObject) {
                val todosArray = input["todos"] as? JsonArray
                if (todosArray != null) {
                    for (todoElement in todosArray) {
                        if (todoElement is JsonObject) {
                            val content = todoElement["content"]?.jsonPrimitive?.content ?: ""
                            val status = todoElement["status"]?.jsonPrimitive?.content ?: "pending"
                            
                            if (content.isNotEmpty()) {
                                println("DEBUG: Adding todo item: $content (status: $status)")
                                val todoItem = createTodoItemComponent(content, status)
                                todoItem.maximumSize = Dimension(Int.MAX_VALUE, todoItem.preferredSize.height)
                                panel.add(todoItem)
                                panel.add(Box.createVerticalStrut(4))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: show simple message
            val errorLabel = JBLabel("Failed to parse todo items: ${e.message}").apply {
                foreground = Color(220, 38, 38)
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel.add(errorLabel)
            e.printStackTrace()
        }
        
        // Add a test item to make sure panel is working
        val testItem = JBLabel("Total todos processed: ${panel.componentCount}").apply {
            foreground = Color(100, 100, 100)
            font = Font(Font.MONOSPACED, Font.ITALIC, 10)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(testItem)
        
        return panel
    }
    
    /**
     * Create individual todo item component with icon and text
     */
    private fun createTodoItemComponent(content: String, status: String): JPanel {
        val itemPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        
        // Get appropriate icon based on status
        val icon = when (status) {
            "completed" -> AllIcons.Actions.Checked
            "in_progress" -> AllIcons.Process.Step_passive  
            "cancelled" -> AllIcons.Actions.Cancel
            else -> AllIcons.General.TodoDefault
        }
        
        val iconLabel = JBLabel(icon).apply {
            border = JBUI.Borders.empty(0, 0, 0, 8)
            verticalAlignment = SwingConstants.TOP
        }
        
        val contentLabel = JBLabel(content).apply {
            foreground = JBColor.foreground()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            verticalAlignment = SwingConstants.TOP
        }
        
        itemPanel.add(iconLabel, BorderLayout.WEST)
        itemPanel.add(contentLabel, BorderLayout.CENTER)
        
        return itemPanel
    }

    /**
     * Parse todo content from JsonElement input
     */
    private fun parseTodoContentFromInput(input: JsonElement?): String {
        try {
            if (input is JsonObject) {
                val todosArray = input["todos"] as? JsonArray
                if (todosArray != null) {
                    val todoItems = mutableListOf<String>()
                    
                    for (todoElement in todosArray) {
                        if (todoElement is JsonObject) {
                            val content = todoElement["content"]?.jsonPrimitive?.content ?: ""
                            val status = todoElement["status"]?.jsonPrimitive?.content ?: "pending"
                            
                            val statusIcon = when (status) {
                                "completed" -> "☑ "
                                "in_progress" -> "⟳ "
                                "cancelled" -> "✗ "
                                else -> "○ "
                            }
                            
                            if (content.isNotEmpty()) {
                                todoItems.add("$statusIcon$content")
                            }
                        }
                    }
                    
                    return if (todoItems.isNotEmpty()) {
                        "Todos have been modified successfully.\n\n" + todoItems.joinToString("\n")
                    } else {
                        "Todos have been modified successfully."
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to simple output
        }
        
        return "Todos have been modified successfully."
    }

    /**
     * Parse todo content from JSON input (legacy method)
     */
    private fun parseTodoContent(toolInput: String): String {
        try {
            // Extract todos array from JSON
            val todosMatch = Regex(""""todos"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(toolInput)
            if (todosMatch != null) {
                val todosJson = todosMatch.groupValues[1]
                val todoItems = mutableListOf<String>()
                
                // Extract individual todo items
                val todoPattern = Regex("""\{[^}]*"content"\s*:\s*"([^"]*)"[^}]*"status"\s*:\s*"([^"]*)"[^}]*\}""")
                todoPattern.findAll(todosJson).forEach { match ->
                    val content = match.groupValues[1]
                    val status = match.groupValues[2]
                    val statusIcon = when (status) {
                        "completed" -> "☑ "
                        "in_progress" -> "⟳ "
                        "cancelled" -> "✗ "
                        else -> "○ "
                    }
                    todoItems.add("$statusIcon $content")
                }
                
                return if (todoItems.isNotEmpty()) {
                    "Todos have been modified successfully.\n\n" + todoItems.joinToString("\n")
                } else {
                    "Todos have been modified successfully."
                }
            }
        } catch (e: Exception) {
            // Fallback to simple output
        }
        
        return "Todos have been modified successfully."
    }

    /**
     * Create search card for grep/glob results
     */
    private fun createSearchCard(toolName: String, toolInput: String, toolOutput: String, hasError: Boolean): JPanel {
        val pattern = extractToolParameters(toolName, toolInput)
        val title = if (pattern.isNotEmpty()) "$toolName($pattern)" else toolName
        
        val card = createBaseCard(title, hasError, false)
        
        if (hasError) {
            val errorContent = JTextArea(toolOutput).apply {
                foreground = Color(220, 38, 38)
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(8)
            }
            card.add(JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 120)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        } else {
            val lines = toolOutput.split("\n")
            val resultLines = lines.take(10)
            val displayText = if (lines.size > 10) {
                "${resultLines.joinToString("\n")}\n... +${lines.size - 10} more results"
            } else {
                resultLines.joinToString("\n")
            }
            
            val searchContent = JTextArea(displayText).apply {
                foreground = JBColor.foreground()
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = false
                border = JBUI.Borders.empty(8)
            }
            
            card.add(JBScrollPane(searchContent).apply {
                preferredSize = Dimension(-1, 180)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        }
        
        return card
    }
    
    /**
     * Create generic card for other tools
     */
    private fun createGenericCard(toolName: String, toolInput: String, toolOutput: String, hasError: Boolean): JPanel {
        val params = extractToolParameters(toolName, toolInput)
        val title = if (params.isNotEmpty()) "$toolName($params)" else toolName
        
        val card = createBaseCard(title, hasError, false)
        
        val content = if (toolOutput.isNotEmpty()) toolOutput else "Tool executed"
        val displayText = if (content.length > 200) {
            content.take(197) + "..."
        } else {
            content
        }
        
        val genericContent = JTextArea(displayText).apply {
            foreground = if (hasError) Color(220, 38, 38) else JBColor.foreground()
            background = JBColor.background()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }
        
        card.add(genericContent, BorderLayout.CENTER)
        return card
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
            border = JBUI.Borders.empty(4, 0, 4, 0) // 适量的内边距
            background = JBColor.background()
            
            // 设置最大高度以防止过度拉伸
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            
            // Icon area (left side) - fixed width, top aligned  
            val iconPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = JBColor.background()
                preferredSize = Dimension(20, -1) // Fixed width
                maximumSize = Dimension(20, Int.MAX_VALUE)
                
                val iconLabel = JBLabel("⏺").apply {
                    foreground = iconColor
                    font = Font(Font.SANS_SERIF, Font.BOLD, 12)  // 与消息图标保持一致
                    horizontalAlignment = SwingConstants.LEFT
                    verticalAlignment = SwingConstants.TOP // Top align the icon
                }
                add(iconLabel, BorderLayout.NORTH) // Put icon at top
            }
            add(iconPanel, BorderLayout.WEST)
            
            // Tool content area (right side) with wrapping
            val originalContent = toolResult?.content ?: toolResult?.text ?: ""
            val contentArea = createWrappingToolContentAreaWithExpansion(formattedDisplay, JBColor.foreground(), toolName, originalContent)
            add(contentArea, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create content area for message text with automatic wrapping
     */
    private fun createWrappingContentArea(content: String, textColor: Color): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = getMessageAreaBackgroundColor() // 使用主题自适应背景色
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
     * Create content area for tool display with automatic wrapping and expansion support
     */
    private fun createWrappingToolContentAreaWithExpansion(content: String, textColor: Color, toolName: String, originalContent: String): JPanel {
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
                        Font(Font.SANS_SERIF, Font.BOLD, 12)  // 调整为12号字体
                    } else {
                        Font(Font.SANS_SERIF, Font.PLAIN, 12)  // 调整为12号字体
                    }
                    
                    // Check if this line contains "show more" for bash output
                    if (line.contains("(show more)")) {
                        val textComponent = createExpandableTextComponentForTool(line, textColor, font, toolName, originalContent)
                        add(textComponent)
                    } else {
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
                            alignmentX = LEFT_ALIGNMENT
                        }
                        add(textArea)
                    }
                }
            }
        }
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
                        Font(Font.SANS_SERIF, Font.BOLD, 12)  // 调整为12号字体
                    } else {
                        Font(Font.SANS_SERIF, Font.PLAIN, 12)  // 调整为12号字体
                    }
                    
                    // Check if this line contains "show more" for bash output
                    if (line.contains("(show more)")) {
                        val textComponent = createExpandableTextComponent(line, textColor, font, content)
                        add(textComponent)
                    } else {
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
                            alignmentX = LEFT_ALIGNMENT
                    }
                    add(textArea)
                    }
                }
            }
        }
    }
    
    /**
     * Create an expandable text component for "show more" functionality in tool output
     */
    private fun createExpandableTextComponentForTool(line: String, textColor: Color, font: Font, toolName: String, originalContent: String): JPanel {
        val containerPanel = JBPanel<JBPanel<*>>()
        containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
        containerPanel.background = JBColor.background()
        containerPanel.border = JBUI.Borders.empty()
        
        val mainText = line.substringBefore("(show more)")
        val showMoreText = "(show more)"
        
        // Create main text area
        val textArea = JTextArea(mainText).apply {
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
        
        // Create clickable "show more" link
        val showMoreLabel = JBLabel(showMoreText).apply {
            foreground = Color.decode("#3498db") // 蓝色链接颜色
            this.font = font
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    expandToolContent(containerPanel, toolName, originalContent, textColor, font)
                }
                
                override fun mouseEntered(e: MouseEvent) {
                    foreground = Color.decode("#2980b9") // 深蓝色悬停效果
                }
                
                override fun mouseExited(e: MouseEvent) {
                    foreground = Color.decode("#3498db")
                }
            })
        }
        
        // Add text and link to container
        containerPanel.add(textArea)
        containerPanel.add(showMoreLabel)
        
        return containerPanel
    }
    
    /**
     * Create an expandable text component for "show more" functionality
     */
    private fun createExpandableTextComponent(line: String, textColor: Color, font: Font, fullContent: String): JPanel {
        val containerPanel = JBPanel<JBPanel<*>>()
        containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
        containerPanel.background = JBColor.background()
        containerPanel.border = JBUI.Borders.empty()
        
        val mainText = line.substringBefore("(show more)")
        val showMoreText = "(show more)"
        
        // Create main text area
        val textArea = JTextArea(mainText).apply {
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
        
        // Create clickable "show more" link
        val showMoreLabel = JBLabel(showMoreText).apply {
            foreground = Color.decode("#3498db") // 蓝色链接颜色
            this.font = font
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    expandContent(containerPanel, fullContent, textColor, font)
                }
                
                override fun mouseEntered(e: MouseEvent) {
                    foreground = Color.decode("#2980b9") // 深蓝色悬停效果
                }
                
                override fun mouseExited(e: MouseEvent) {
                    foreground = Color.decode("#3498db")
                }
            })
        }
        
        // Add text and link to container
        containerPanel.add(textArea)
        containerPanel.add(showMoreLabel)
        
        return containerPanel
    }
    
    /**
     * Expand the tool content to show full output
     */
    private fun expandToolContent(container: JPanel, toolName: String, originalContent: String, textColor: Color, font: Font) {
        val lines = originalContent.trim().split("\n").filter { it.isNotBlank() }
        
        container.removeAll()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        
        // Add all lines with proper indentation (5 spaces to align with ⎿ content)
        for ((index, line) in lines.withIndex()) {
            val indentedLine = if (index == 0) line else "     $line"  // 5 spaces to align with ⎿ symbol
            
            val textArea = JTextArea(indentedLine).apply {
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
            container.add(textArea)
        }
        
        // Add collapse link
        val collapseLabel = JBLabel("(show less)").apply {
            foreground = Color.decode("#3498db") // 蓝色链接颜色
            this.font = font
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    collapseToolContent(container, toolName, originalContent, textColor, font)
                }
                
                override fun mouseEntered(e: MouseEvent) {
                    foreground = Color.decode("#2980b9") // 深蓝色悬停效果
                }
                
                override fun mouseExited(e: MouseEvent) {
                    foreground = Color.decode("#3498db")
                }
            })
        }
        container.add(collapseLabel)
        
        container.revalidate()
        container.repaint()
    }
    
    /**
     * Collapse the tool content back to summary view
     */
    private fun collapseToolContent(container: JPanel, toolName: String, originalContent: String, textColor: Color, font: Font) {
        // Recreate the original collapsed format
        val formattedSummary = formatBashResult(originalContent)
        
        container.removeAll()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        
        val lines = formattedSummary.split("\\n")
        for ((index, line) in lines.withIndex()) {
            if (line.isNotBlank()) {
                // Check if this line contains "show more" for bash output
                if (line.contains("(show more)")) {
                    val textComponent = createExpandableTextComponentForTool(line, textColor, font, toolName, originalContent)
                    container.add(textComponent)
                        } else {
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
                    container.add(textArea)
                }
            }
        }
        
        container.revalidate()
        container.repaint()
    }
    
    /**
     * Expand the content to show full output
     */
    private fun expandContent(container: JPanel, fullContent: String, textColor: Color, font: Font) {
        // Get the original tool result content from the formatted display
        val toolResultContent = extractOriginalBashContent(fullContent)
        val lines = toolResultContent.trim().split("\n").filter { it.isNotBlank() }
        
        container.removeAll()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        
        // Add all lines
        for (line in lines) {
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
            container.add(textArea)
        }
        
        container.revalidate()
        container.repaint()
    }
    
    /**
     * Extract original bash content from the full content string
     */
    private fun extractOriginalBashContent(fullContent: String): String {
        // This would need to be passed the original tool result content
        // For now, we'll reconstruct it from the formatted display
        return fullContent
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
        chatInputBar.appendText(code)
        chatInputBar.requestInputFocus()
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
                formatBashResult(content)
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
    
    private fun formatBashResult(content: String): String {
        val lines = content.trim().split("\n").filter { it.isNotBlank() }
        
        return when {
            lines.isEmpty() -> "Command executed"
            lines.size <= 3 -> {
                // For 3 or fewer lines, indent all lines properly (5 spaces to align with content after ⎿  )
                lines.mapIndexed { index, line ->
                    if (index == 0) line else "     $line"
                }.joinToString("\n")
            }
            else -> {
                val visibleLines = lines.take(3)
                val remainingCount = lines.size - 3
                val formattedLines = visibleLines.mapIndexed { index, line ->
                    if (index == 0) line else "     $line"
                }.joinToString("\n")
                "$formattedLines\n… +$remainingCount lines (show more)"
            }
        }
    }
    
}
