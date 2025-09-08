package com.claudecodechat.ui.swing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Reusable chat input bar with model selector, send button, completion and current file label.
 * It is independent from ClaudeChatPanel and can be embedded in welcome or other places.
 */
class ChatInputBar(
    private val project: Project,
    private val onSend: (text: String, model: String, planMode: Boolean) -> Unit,
    private val onStop: (() -> Unit)? = null
) : JBPanel<ChatInputBar>(BorderLayout()) {

    init {
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI - Using IntelliJ Editor instead of JTextArea
    private lateinit var inputEditor: Editor
    private lateinit var inputDocument: Document
    private val modelComboBox: JComboBox<String> = JComboBox(arrayOf("auto", "sonnet", "opus", "haiku"))
    private val sendButton: JButton = JButton().apply {
        icon = AllIcons.Actions.Execute
        toolTipText = if (System.getProperty("os.name").lowercase().contains("mac")) {
            "Send (Cmd+Enter)"
        } else {
            "Send (Ctrl+Enter)"
        }
        preferredSize = Dimension(40, 28)
    }

    private val currentFileLabel: JLabel = JLabel("")
    
    // Context status bar
    private val contextStatusBar: JBPanel<JBPanel<*>> = JBPanel(BorderLayout())
    private val contextLabel: JLabel = JLabel("Context: Ready")
    private val contextIcon: JLabel = JLabel(AllIcons.General.Information)
    
    // Loading bar components
    private val loadingPanel: JBPanel<JBPanel<*>> = JBPanel(BorderLayout())
    private val loadingIcon: JLabel = JLabel(AnimatedIcon.Default.INSTANCE)
    private val loadingText: JLabel = JLabel("")
    private val timerLabel: JLabel = JLabel("")
    private val stopButton: JButton = JButton("Stop")
    
    // Plan mode checkbox
    private val planModeCheckBox: JCheckBox = JCheckBox("Plan")
    
    // Timer for tracking execution time
    private var startTime: Long = 0
    private var timerJob: Job? = null

    // Native IntelliJ completion will be handled automatically

    init {
        background = JBColor.background()
        border = JBUI.Borders.empty()

        // Native completion will be handled automatically by IntelliJ

        // Create input editor
        val (editor, document) = createInputEditor()
        inputEditor = editor
        inputDocument = document


        // Setup loading panel
        setupLoadingPanel()

        // Layout - Use Editor component
        val inputScroll = JBScrollPane(inputEditor.component).apply {
            preferredSize = Dimension(600, 100)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = createRoundedBorder(JBColor.border(), 1, 8)
        }
        
        // Add focus listener to change border color
        inputEditor.contentComponent.addFocusListener(object : java.awt.event.FocusListener {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                inputScroll.border = createRoundedBorder(JBColor.BLUE, 2, 8)
            }
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                inputScroll.border = createRoundedBorder(JBColor.border(), 1, 8)
            }
        })

        val controls = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(currentFileLabel, BorderLayout.WEST)
            
            val right = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                // Configure plan mode checkbox - compact and aligned
                planModeCheckBox.apply {
                    font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                    toolTipText = "Enable plan mode (--permission-mode plan) - Use Shift+Tab to toggle"
                    foreground = JBColor.foreground()
                    preferredSize = Dimension(50, 28) // 紧凑宽度，匹配其他控件高度
                    alignmentY = Component.CENTER_ALIGNMENT
                }
                
                // 添加顺序：plan checkbox, 模型选择, 发送按钮
                add(planModeCheckBox)
                add(modelComboBox)
                add(sendButton)
            }
            add(right, BorderLayout.EAST)
        }

        val section = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(loadingPanel, BorderLayout.NORTH)
            add(contextStatusBar, BorderLayout.CENTER)
            
            // Create a container for input and controls
            val inputContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(inputScroll, BorderLayout.CENTER)
                add(controls, BorderLayout.SOUTH)
            }
            add(inputContainer, BorderLayout.SOUTH)
        }

        // Setup context status bar
        setupContextStatusBar()
        
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(section, BorderLayout.CENTER)
        }
        
        add(mainPanel, BorderLayout.CENTER)
        // Don't add completion panel to layout - it will be a popup

        // Actions
        sendButton.addActionListener { sendMessage() }
        setupFileInfoTracking()
    }
    
    /**
     * Create IntelliJ Editor for chat input with PSI support using actual file
     */
    private fun createInputEditor(): Pair<Editor, Document> {
        // Create actual file for better PSI support
        val projectBasePath = project.basePath ?: System.getProperty("user.home")
        val chatInputDir = java.io.File(projectBasePath, ".chat-input")
        if (!chatInputDir.exists()) {
            chatInputDir.mkdirs()
        }

        val chatInputFile = java.io.File(chatInputDir, "chat-input.md")
        if (!chatInputFile.exists()) {
            chatInputFile.writeText("")
        }

        // Get the virtual file from the actual file
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(chatInputFile)
            ?: throw IllegalStateException("Cannot find virtual file for ${chatInputFile.absolutePath}")

        // Create document from the file
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: throw IllegalStateException("Cannot create document for file")

        val editor = EditorFactory.getInstance().createEditor(document, project, virtualFile, false)

        // Configure editor settings
        configureInputEditor(editor)

        // Add document listener for text changes
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // Document change handling if needed
            }
        })
        
        // Auto-trigger completion on / and @ characters
        var lastInsertedChar: Char? = null
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // Only trigger on single character insertion at the end of document
                if (event.newLength == 1 && event.oldLength == 0) {
                    val insertedChar = document.text.getOrNull(event.offset)
                    if (insertedChar == '/' || insertedChar == '@') {
                        lastInsertedChar = insertedChar
                        // Delay the completion trigger to avoid interference
                        javax.swing.SwingUtilities.invokeLater {
                            ApplicationManager.getApplication().invokeLater {
                                // Double-check we're still in the right state
                                if (lastInsertedChar == insertedChar && editor.caretModel.offset == event.offset + 1) {
                                    try {
                                        val handler = com.intellij.codeInsight.completion.CodeCompletionHandlerBase.createHandler(
                                            com.intellij.codeInsight.completion.CompletionType.BASIC
                                        )
                                        handler.invokeCompletion(project, editor)
                                    } catch (e: Exception) {
                                        // Silently ignore completion errors
                                    }
                                    lastInsertedChar = null
                                }
                            }
                        }
                    }
                }
            }
        })


        return Pair(editor, document)
    }
    
    /**
     * Configure editor settings for chat input
     */
    private fun configureInputEditor(editor: Editor) {
        val settings = editor.settings
        
        // Basic settings
        settings.isLineNumbersShown = false
        settings.isFoldingOutlineShown = false
        settings.isAutoCodeFoldingEnabled = false
        settings.isRightMarginShown = false
        settings.isWhitespacesShown = false
        settings.isIndentGuidesShown = false
        settings.additionalLinesCount = 0
        settings.additionalColumnsCount = 0
        settings.isCaretRowShown = false
        
        // Multi-line and wrapping
        settings.isUseSoftWraps = true
        
        // Note: Some completion settings may not be available in EditorSettings
        
        // Set preferred size
        editor.component.preferredSize = Dimension(600, 100)
        editor.component.minimumSize = Dimension(400, 60)
        
        // Add key handlers for special keys (Ctrl+Enter for send)
        setupEditorKeyHandlers(editor)
    }
    
    /**
     * Setup key handlers for the editor
     */
    private fun setupEditorKeyHandlers(editor: Editor) {
        // Add typed handler for send on Ctrl+Enter
        editor.contentComponent.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ENTER && (e.isControlDown || e.isMetaDown) -> {
                        sendMessage()
                        e.consume()
                    }
                    e.keyCode == KeyEvent.VK_TAB && e.isShiftDown -> {
                        togglePlanMode()
                        e.consume()
                    }
                }
            }
        })
    }
    
    
    /**
     * Create a rounded border
     */
    private fun createRoundedBorder(color: Color, thickness: Int, radius: Int): javax.swing.border.Border {
        return object : javax.swing.border.AbstractBorder() {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.stroke = BasicStroke(thickness.toFloat())
                
                // Draw rounded rectangle
                g2.drawRoundRect(
                    x + thickness / 2, 
                    y + thickness / 2, 
                    width - thickness, 
                    height - thickness, 
                    radius, 
                    radius
                )
                g2.dispose()
            }
            
            override fun getBorderInsets(c: Component): Insets {
                return Insets(thickness + 2, thickness + 2, thickness + 2, thickness + 2)
            }
        }
    }


    fun requestInputFocus() { inputEditor.contentComponent.requestFocus() }
    fun setText(text: String) {
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            inputDocument.setText(text)
        }
    }
    fun getText(): String = inputDocument.text
    fun getCaretPosition(): Int = inputEditor.caretModel.offset
    fun setCaretPosition(pos: Int) { inputEditor.caretModel.moveToOffset(pos) }
    fun appendText(text: String) {
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            val current = inputDocument.text
            val newText = if (current.isEmpty()) text else "$current\n\n$text"
            inputDocument.setText(newText)
            inputEditor.caretModel.moveToOffset(newText.length)
        }
    }
    
    /**
     * Update the context information in the status bar
     */
    fun updateContextInfo(context: String) {
        contextLabel.text = "Context: $context"
    }
    
    /**
     * Update token usage information in the status bar
     */
    fun updateTokenUsage(inputTokens: Int, outputTokens: Int, cacheReadTokens: Int = 0, cacheCreationTokens: Int = 0, sessionId: String? = null) {
        val contextLength = inputTokens + cacheReadTokens + cacheCreationTokens
        val totalTokens = contextLength + outputTokens
        
        // Format tokens with 'k' suffix for thousands, keeping one decimal place
        fun formatTokens(tokens: Int): String {
            return if (tokens >= 1000) "${String.format("%.1f", tokens / 1000.0)}k" else tokens.toString()
        }
        
        val displayText = if (contextLength > 0 || outputTokens > 0) {
            val tokenInfo = if (cacheReadTokens > 0 || cacheCreationTokens > 0) {
                "Context: ${formatTokens(contextLength)} (${formatTokens(inputTokens)}↑ + ${formatTokens(cacheReadTokens)} cache read + ${formatTokens(cacheCreationTokens)} cache creation) | Output: ${formatTokens(outputTokens)}↓"
            } else {
                "Context: ${formatTokens(contextLength)} (${formatTokens(inputTokens)}↑) | Output: ${formatTokens(outputTokens)}↓"
            }
            
            // Add session ID in debug mode
            if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode && sessionId != null) {
                "$tokenInfo | Session: ${sessionId.take(8)}"
            } else {
                tokenInfo
            }
        } else {
            val baseText = "Context: Ready"
            // Add session ID in debug mode even when no tokens
            if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode && sessionId != null) {
                "$baseText | Session: ${sessionId.take(8)}"
            } else {
                baseText
            }
        }
        
        contextLabel.text = displayText
    }
    
    // Loading state management
    fun showLoading(userPrompt: String) {
        val summary = createPromptSummary(userPrompt)
        loadingText.text = summary
        
        // Start timer
        startTime = System.currentTimeMillis()
        startTimer()
        
        loadingPanel.isVisible = true
        sendButton.isEnabled = false
        revalidate()
        repaint()
    }
    
    fun hideLoading() {
        // Stop timer
        timerJob?.cancel()
        timerJob = null
        
        loadingPanel.isVisible = false
        sendButton.isEnabled = true
        revalidate()
        repaint()
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val seconds = elapsed / 1000
                val milliseconds = (elapsed % 1000) / 100
                
                ApplicationManager.getApplication().invokeLater {
                    timerLabel.text = "${seconds}.${milliseconds}s"
                }
                
                delay(100) // Update every 100ms
            }
        }
    }
    
    private fun createPromptSummary(prompt: String): String {
        return if (prompt.length <= 50) prompt else prompt.take(47) + "..."
    }
    
    /**
     * Toggle plan mode checkbox
     */
    private fun togglePlanMode() {
        planModeCheckBox.isSelected = !planModeCheckBox.isSelected
    }
    
    /**
     * Check if plan mode is enabled
     */
    fun isPlanModeEnabled(): Boolean = planModeCheckBox.isSelected

    private fun sendMessage() {
        val text = inputDocument.text.trim()
        if (text.isEmpty()) return
        val model = (modelComboBox.selectedItem as? String) ?: "auto"
        
        // Show loading state
        showLoading(text)
        
        // Clear input in write action
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            inputDocument.setText("")
        }
        onSend(text, model, planModeCheckBox.isSelected)
    }



    private fun setupFileInfoTracking() {
        currentFileLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        currentFileLabel.foreground = Color.decode("#888888")
        updateFileInfo()
        project.messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) { updateFileInfo() }
            }
        )
        EditorFactory.getInstance().eventMulticaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) { updateFileInfo() }
            }, project
        )
    }

    private fun updateFileInfo() {
        try {
            val fem = FileEditorManager.getInstance(project)
            val editor = fem.selectedTextEditor
            if (editor != null) {
                val vf = editor.virtualFile
                if (vf != null) {
                    val fileName = vf.name
                    val line = editor.caretModel.logicalPosition.line + 1
                    
                    // Check if there's a selection
                    val selectionModel = editor.selectionModel
                    val hasSelection = selectionModel.hasSelection()
                    
                    val text = if (hasSelection) {
                        val startLine = editor.offsetToLogicalPosition(selectionModel.selectionStart).line + 1
                        val endLine = editor.offsetToLogicalPosition(selectionModel.selectionEnd).line + 1
                        "in $fileName,select lines:$startLine-$endLine"
                    } else {
                        "in $fileName"
                    }
                    
                    ApplicationManager.getApplication().invokeLater {
                        currentFileLabel.text = text
                        currentFileLabel.isVisible = true
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        currentFileLabel.text = ""
                        currentFileLabel.isVisible = false
                    }
                }
            } else {
                ApplicationManager.getApplication().invokeLater {
                    currentFileLabel.text = ""
                    currentFileLabel.isVisible = false
                }
            }
        } catch (_: Exception) {
            ApplicationManager.getApplication().invokeLater {
                currentFileLabel.text = ""
                currentFileLabel.isVisible = false
            }
        }
    }
    
    private fun setupContextStatusBar() {
        // Configure context status bar without background color
        contextStatusBar.apply {
            background = JBColor.background() // 使用原生背景色，无特殊背景
            border = JBUI.Borders.empty(4, 8, 4, 8)
            preferredSize = Dimension(Int.MAX_VALUE, 24)
        }
        
        contextLabel.apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            foreground = JBColor.foreground() // 使用标准前景色以确保可读性
        }
        
        contextIcon.apply {
            icon = AllIcons.General.Information
            border = JBUI.Borders.empty(0, 0, 0, 6)
        }
        
        val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            background = JBColor.background() // 使用原生背景色
            add(contextIcon)
            add(contextLabel)
        }
        
        contextStatusBar.add(leftPanel, BorderLayout.WEST)
    }

    private fun setupLoadingPanel() {
        // Configure loading icon with animation
        loadingIcon.apply {
            icon = AnimatedIcon.Default.INSTANCE // 动态加载图标
            border = JBUI.Borders.empty(0, 0, 0, 8)
        }
        
        // Configure loading components
        loadingText.apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            foreground = JBColor.foreground()
        }
        
        // Configure timer label
        timerLabel.apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(0, 8, 0, 0)
        }
        
        // Configure stop button with native styling
        stopButton.apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            preferredSize = Dimension(70, 28)
            // 使用默认样式，不自定义颜色
            
            addActionListener {
                onStop?.invoke()
                hideLoading()
            }
        }
        
        // Layout loading panel with proper height and border
        loadingPanel.apply {
            background = JBColor.background() // 使用原生背景色
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1), // 添加边框
                JBUI.Borders.empty(8, 12, 8, 12) // 内边距
            )
            preferredSize = Dimension(Int.MAX_VALUE, 48) // 增加高度到 48px
            
            val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 6)).apply {
                background = JBColor.background()
                add(loadingIcon)
                add(loadingText)
                add(timerLabel)
            }
            
            val rightPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 6)).apply {
                background = JBColor.background()
                add(stopButton)
            }
            
            add(leftPanel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.EAST)
            
            // Initially hidden
            isVisible = false
        }
    }
    
    /**
     * Clean up resources when the component is disposed
     */
    fun dispose() {
        scope.cancel()
    }
}



