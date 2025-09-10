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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import com.claudecodechat.persistence.SessionPersistence
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.imageio.ImageIO
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
    private val log = Logger.getInstance(ChatInputBar::class.java)
    companion object {
        private const val TEMP_DIR_NAME = "claude-chat-input"
        private const val RETENTION_MS: Long = 48L * 60 * 60 * 1000 // 48 hours
        private const val IMAGES_DIR_NAME = "claude-chat-images"
        private val cleanupRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        private fun scheduleAsyncTempCleanup() {
            if (cleanupRunning.compareAndSet(false, true)) {
                try {
                    com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            // Clean up both tmp directory (for backward compatibility) and project .idea directory
                            cleanupTempFiles(java.io.File(System.getProperty("java.io.tmpdir"), TEMP_DIR_NAME))
                            // Project-specific cleanup will be handled per project in createInputEditor
                        } finally {
                            cleanupRunning.set(false)
                        }
                    }
                } catch (_: Exception) {
                    cleanupRunning.set(false)
                }
            }
        }
        
        private fun cleanupTempFiles(tempDir: java.io.File) {
            if (tempDir.exists()) {
                val now = System.currentTimeMillis()
                // Clean up chat input temp files
                tempDir.listFiles { f -> f.isFile && f.name.startsWith("chat-input-") && f.name.endsWith(".md") }?.forEach { f ->
                    if (now - f.lastModified() > RETENTION_MS) {
                        runCatching { f.delete() }
                    }
                }
                // Clean up old image files in the images subdirectory
                val imagesDir = java.io.File(tempDir, IMAGES_DIR_NAME)
                if (imagesDir.exists()) {
                    imagesDir.listFiles { f -> f.isFile && f.name.endsWith(".png") }?.forEach { f ->
                        if (now - f.lastModified() > RETENTION_MS) {
                            runCatching { f.delete() }
                        }
                    }
                    // Remove empty images directory
                    if (imagesDir.listFiles()?.isEmpty() == true) {
                        runCatching { imagesDir.delete() }
                    }
                }
            }
        }
    }

    init {
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI - Using IntelliJ Editor instead of JTextArea
    private lateinit var inputEditor: Editor
    private lateinit var inputDocument: Document
    private var inputIoFile: java.io.File? = null
    private var inputVFile: com.intellij.openapi.vfs.VirtualFile? = null
    private val modelComboBox: JComboBox<String> = JComboBox(arrayOf("auto", "sonnet", "opus", "haiku"))
    // 智能按钮：发送时显示为停止按钮，停止时显示为发送按钮
    private val smartButton: JButton = JButton().apply {
        icon = AllIcons.Actions.Execute
        toolTipText = if (System.getProperty("os.name").lowercase().contains("mac")) {
            "Send (Cmd+Enter)"
        } else {
            "Send (Ctrl+Enter)"
        }
        preferredSize = Dimension(40, 28)
    }

    private val currentFileLabel: JLabel = JLabel("")
    private val mcpButton: JButton = JButton("MCP").apply {
        toolTipText = "Configure MCP servers for this session"
        preferredSize = Dimension(52, 28)
    }
    
    // 合并的状态栏：显示 context 和 loading 信息
    private val statusBar: JBPanel<JBPanel<*>> = JBPanel(BorderLayout())
    private val statusLabel: JLabel = JLabel("Context: Ready")
    private val statusIcon: JLabel = JLabel(AllIcons.General.Information)
    private val sessionIdLabel: JLabel = JLabel("")
    
    // Loading 状态管理
    private var isLoading = false
    
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

        // Register Enter-to-send with global manager (Shift+Enter inserts newline)
        ChatEnterToSendManager.registerEditor(inputEditor) { sendMessage() }
        // Also bind Shift+Enter to insert newline
        installEnterToSend(inputEditor)

        // Install @file hyperlink highlighting + Cmd/Ctrl+Click navigation
        installFileReferenceHyperlinks(inputEditor)


        // Setup status bar
        setupStatusBar()
        
        // Install image paste handler
        installImagePasteHandler()

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
                    toolTipText = "Plan mode (Claude Code --permission-mode plan). Toggle with Shift+Tab."
                    foreground = JBColor.foreground()
                    preferredSize = Dimension(50, 28) // 紧凑宽度，匹配其他控件高度
                    alignmentY = CENTER_ALIGNMENT
                }
                
                // 添加顺序：plan checkbox, MCP, 模型选择, 智能按钮
                add(planModeCheckBox)
                add(mcpButton)
                add(modelComboBox)
                add(smartButton)
            }
            add(right, BorderLayout.EAST)
        }

        val section = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(statusBar, BorderLayout.NORTH)
            
            // Create a container for input and controls
            val inputContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(inputScroll, BorderLayout.CENTER)
                add(controls, BorderLayout.SOUTH)
            }
            add(inputContainer, BorderLayout.CENTER)
        }
        
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(section, BorderLayout.CENTER)
        }
        
        add(mainPanel, BorderLayout.CENTER)
        // Don't add completion panel to layout - it will be a popup

        // Actions
        smartButton.addActionListener { 
            if (isLoading) {
                stopMessage()
            } else {
                sendMessage()
            }
        }
        setupFileInfoTracking()

        // MCP config button: open dialog with selection and editor
        mcpButton.addActionListener {
            val logicalId = com.claudecodechat.state.SessionViewModel.getInstance(project).currentLogicalSessionId()
            val dlg = McpConfigDialog(project, logicalId)
            dlg.show()
            // refresh button text with selection count
            if (logicalId != null) {
                val count = SessionPersistence.getInstance(project).getSelectedMcpServers(logicalId).size
                mcpButton.text = if (count > 0) "MCP($count)" else "MCP"
            }
        }

        // Initialize MCP button label with current selection count
        try {
            val logicalId = com.claudecodechat.state.SessionViewModel.getInstance(project).currentLogicalSessionId()
            if (logicalId != null) {
                val count = SessionPersistence.getInstance(project).getSelectedMcpServers(logicalId).size
                mcpButton.text = if (count > 0) "MCP($count)" else "MCP"
            }
        } catch (_: Exception) { }
    }
    
    /**
     * Create IntelliJ Editor for chat input with PSI support using a unique temp file per tab
     */
    private fun createInputEditor(): Pair<Editor, Document> {
        // Create temp file under project .idea directory to avoid polluting system tmp
        val ideaDir = java.io.File(project.basePath, ".idea")
        if (!ideaDir.exists()) ideaDir.mkdirs()
        
        val tempDir = java.io.File(ideaDir, TEMP_DIR_NAME)
        if (!tempDir.exists()) tempDir.mkdirs()
        
        // Clean up old files in this project's temp directory
        cleanupTempFiles(tempDir)
        
        val chatInputFile = java.io.File.createTempFile("chat-input-", ".md", tempDir).apply {
            // Don't use deleteOnExit() as we manage cleanup ourselves
        }
        inputIoFile = chatInputFile

        // Get the virtual file from the actual file
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(chatInputFile)
            ?: throw IllegalStateException("Cannot find virtual file for ${chatInputFile.absolutePath}")
        inputVFile = virtualFile

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
                        SwingUtilities.invokeLater {
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

    // --- Hyperlink support for @file references ---
    private val linkHighlighters = mutableListOf<RangeHighlighter>()
    private val linkRanges = mutableListOf<TextRange>()

    private fun installFileReferenceHyperlinks(editor: Editor) {
        // Re-scan and highlight on text change
        inputDocument.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                refreshFileReferenceLinks(editor)
            }
        })
        refreshFileReferenceLinks(editor)

        // Mouse motion: show hand cursor when hovering a link (with or without modifier)
        val motionListener = object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                if (e.editor !== editor) return
                val offset = offsetAt(e)
                val hovering = linkRanges.any { it.containsOffsetInText(offset) }
                editor.contentComponent.cursor = if (hovering) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        }
        EditorFactory.getInstance().eventMulticaster.addEditorMouseMotionListener(motionListener, project)

        // Mouse click: Cmd/Ctrl + click to open the file
        val clickListener = object : EditorMouseListener {
            override fun mouseClicked(e: EditorMouseEvent) {
                if (e.editor !== editor) return
                val me = e.mouseEvent
                val modifierDown = me.isMetaDown || me.isControlDown
                if (!modifierDown) return
                val offset = offsetAt(e)
                val link = linkRanges.firstOrNull { it.containsOffsetInText(offset) } ?: return
                val token = inputDocument.getText(link).removePrefix("@")
                openFileToken(token)
                me.consume()
            }
        }
        EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(clickListener, project)
    }

    private fun TextRange.containsOffsetInText(offset: Int): Boolean = offset in startOffset until endOffset

    private fun offsetAt(e: EditorMouseEvent): Int {
        val p = e.mouseEvent.point
        val visual = e.editor.xyToVisualPosition(p)
        val logical = e.editor.visualToLogicalPosition(visual)
        return e.editor.logicalPositionToOffset(logical)
    }

    private fun refreshFileReferenceLinks(editor: Editor) {
        try {
            // clear
            linkHighlighters.forEach { it.dispose() }
            linkHighlighters.clear()
            linkRanges.clear()

            val text = inputDocument.text ?: return
            val regex = Regex("@([\\w./-]+)")
            val scheme = EditorColorsManager.getInstance().globalScheme
            val attrs = scheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)
                ?: TextAttributes().apply {
                    foregroundColor = JBColor.BLUE
                    effectType = EffectType.LINE_UNDERSCORE
                    effectColor = JBColor.BLUE
                }
            for (m in regex.findAll(text)) {
                val range = TextRange(m.range.first, m.range.last + 1)
                linkRanges.add(range)
                val h = editor.markupModel.addRangeHighlighter(
                    range.startOffset,
                    range.endOffset,
                    HighlighterLayer.ADDITIONAL_SYNTAX,
                    attrs,
                    com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
                )
                linkHighlighters.add(h)
            }
        } catch (_: Exception) {
            // don't break UI if any issue occurs
        }
    }

    private fun openFileToken(token: String) {
        // If the token contains '/', treat as relative path under project
        val vfs: List<VirtualFile> = if (token.contains('/')) {
            val base = project.basePath
            if (base != null) {
                val io = if (token.startsWith("$IMAGES_DIR_NAME/")) {
                    java.io.File(base, ".idea/${IMAGES_DIR_NAME}/" + token.removePrefix("$IMAGES_DIR_NAME/"))
                } else {
                    java.io.File(base, token)
                }
                LocalFileSystem.getInstance().findFileByIoFile(io)?.let { listOf(it) } ?: emptyList()
            } else emptyList()
        } else {
            // Search by file name
            val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            com.intellij.psi.search.FilenameIndex.getVirtualFilesByName(token, scope).toList()
        }
        when {
            vfs.isEmpty() -> {}
            vfs.size == 1 -> FileEditorManager.getInstance(project).openFile(vfs.first(), true)
            else -> {
                val base = project.basePath
                val items = vfs.map { vf ->
                    if (base != null) java.nio.file.Paths.get(base).relativize(java.nio.file.Paths.get(vf.path)).toString() else vf.path
                }
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(items)
                    .setTitle("Open file")
                    .setItemChosenCallback { selected ->
                        val idx = items.indexOf(selected)
                        if (idx >= 0) FileEditorManager.getInstance(project).openFile(vfs[idx], true)
                    }
                    .createPopup()
                    .showInCenterOf(this)
            }
        }
    }

    // --- Enter to Send wiring ---
    private var shiftEnterAction: AnAction? = null

    private fun installEnterToSend(chatEditor: Editor) {
        // Only bind Shift+Enter to insert newline using original handler through manager
        val shiftAct = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                ChatEnterToSendManager.executeOriginalEnter(chatEditor, chatEditor.caretModel.currentCaret, e.dataContext)
            }
        }
        shiftAct.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.SHIFT_DOWN_MASK)),
            chatEditor.contentComponent
        )
        shiftEnterAction = shiftAct
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
        ApplicationManager.getApplication().runWriteAction {
            inputDocument.setText(text)
        }
    }
    fun getText(): String = inputDocument.text
    fun getCaretPosition(): Int = inputEditor.caretModel.offset
    fun setCaretPosition(pos: Int) { inputEditor.caretModel.moveToOffset(pos) }
    fun appendText(text: String) {
        ApplicationManager.getApplication().runWriteAction {
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
        if (!isLoading) {
            statusLabel.text = "Context: $context"
        }
    }
    
    /**
     * Update token usage information in the status bar
     */
    fun updateTokenUsage(
        inputTokens: Int,
        outputTokens: Int,
        cacheReadTokens: Int = 0,
        cacheCreationTokens: Int = 0,
        sessionId: String? = null,
        toolsExecuted: Int = 0,
        mcpCalls: Int = 0
    ) {
        val contextLength = inputTokens + cacheReadTokens + cacheCreationTokens
        contextLength + outputTokens
        
        // Format tokens with 'k' suffix for thousands, keeping one decimal place
        fun formatTokens(tokens: Int): String {
            return if (tokens >= 1000) "${String.format("%.1f", tokens / 1000.0)}k" else tokens.toString()
        }
        
        var displayText = if (contextLength > 0 || outputTokens > 0) {
            if (cacheReadTokens > 0 || cacheCreationTokens > 0) {
                "Context: ${formatTokens(contextLength)} (${formatTokens(inputTokens)}↑ + ${formatTokens(cacheReadTokens)} cache read + ${formatTokens(cacheCreationTokens)} cache creation) | Output: ${formatTokens(outputTokens)}↓"
            } else {
                "Context: ${formatTokens(contextLength)} (${formatTokens(inputTokens)}↑) | Output: ${formatTokens(outputTokens)}↓"
            }
        } else {
            "Context: Ready"
        }

        // In debug mode, append tools/mcp summary
        if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) {
            displayText += " | Tools: $toolsExecuted (MCP $mcpCalls)"
        }
        
        if (!isLoading) {
            statusLabel.text = displayText
        }
        // Keep session id label updated regardless of loading state
        updateSessionId(sessionId)
    }
    
    // Loading state management
    fun showLoading(userPrompt: String) {
        isLoading = true
        val summary = createPromptSummary(userPrompt)
        
        // Start timer
        startTime = System.currentTimeMillis()
        startTimer()
        
        // 更新状态栏显示 loading 信息
        updateStatusDisplay(summary, true)
        
        // 更新按钮为停止状态
        smartButton.icon = AllIcons.Actions.Suspend
        smartButton.toolTipText = "Stop"
        
        revalidate()
        repaint()
    }
    
    fun hideLoading() {
        isLoading = false
        
        // Stop timer
        timerJob?.cancel()
        timerJob = null
        
        // 恢复状态栏显示
        updateStatusDisplay("Context: Ready", false)
        
        // 恢复按钮为发送状态
        smartButton.icon = AllIcons.Actions.Execute
        smartButton.toolTipText = if (System.getProperty("os.name").lowercase().contains("mac")) {
            "Send (Cmd+Enter)"
        } else {
            "Send (Ctrl+Enter)"
        }
        
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
                    if (isLoading) {
                        val currentText = statusLabel.text
                        val baseText = currentText.substringBefore(" | ")
                        statusLabel.text = "$baseText | ${seconds}.${milliseconds}s"
                    }
                }
                
                delay(100) // Update every 100ms
            }
        }
    }
    
    private fun createPromptSummary(prompt: String): String {
        return if (prompt.length <= 50) prompt else prompt.take(47) + "..."
    }
    
    /**
     * 更新状态栏显示
     */
    private fun updateStatusDisplay(text: String, isActive: Boolean) {
        statusLabel.text = text
        statusIcon.icon = if (isActive) AnimatedIcon.Default.INSTANCE else AllIcons.General.Information
    }
    
    /**
     * 停止消息处理
     */
    private fun stopMessage() {
        onStop?.invoke()
        hideLoading()
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
        val rawText = inputDocument.text.trim()
        if (rawText.isEmpty()) return
        val model = (modelComboBox.selectedItem as? String) ?: "auto"
        
        // Do not add IDE context for slash commands
        val isSlash = rawText.trimStart().startsWith("/")
        val ideContext = if (!isSlash) buildIdeContextXml() else null

        // Transform pasted image references into [#imageN] + attachments xml
        val (textWithImageIds, attachmentsXml) = if (!isSlash) transformImageReferences(rawText) else rawText to null

        // Assemble final text: user text [+ ide_context] [+ attachments]
        val finalText = buildString {
            append(textWithImageIds)
            if (!attachmentsXml.isNullOrBlank()) {
                append("\n\n")
                append(attachmentsXml)
            }
            if (ideContext != null) {
                append("\n\n")
                append(ideContext)
            }
        }
        
        // Show loading state
        showLoading(rawText)
        
        // Clear input in write action
        ApplicationManager.getApplication().runWriteAction {
            inputDocument.setText("")
        }
        onSend(finalText, model, planModeCheckBox.isSelected)
    }

    /**
     * Replace @claude-chat-images/... in text with [#imageN] and build an attachments XML block.
     */
    private fun transformImageReferences(text: String): Pair<String, String?> {
        return try {
            // Match @claude-chat-images/<filename>
            val pattern = Regex("@" + Regex.escape(IMAGES_DIR_NAME) + "/[\\w./-]+")
            val matches = pattern.findAll(text).toList()
            if (matches.isEmpty()) return text to null

            // Preserve first-appearance order and de-duplicate
            val ordered = LinkedHashSet<String>()
            matches.forEach { ordered.add(it.value) }

            // Build id mapping: [#image1], [#image2], ...
            val idByRef = mutableMapOf<String, String>()
            var idx = 1
            ordered.forEach { ref ->
                idByRef[ref] = "#image$idx"
                idx++
            }

            if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) {
                log.info("Image refs found in prompt: ${ordered.joinToString()}")
            }

            // Replace in text
            var newText = text
            ordered.forEach { ref ->
                val idToken = "[${idByRef[ref]}]"
                newText = newText.replace(ref, idToken)
            }

            // Build XML attachments
            val abs = ordered.mapNotNull { ref -> imageRefToAbsolutePath(ref) }
            val ids = ordered.map { idByRef[it] ?: "" }
            val attachments = buildImageAttachmentsXml(abs, ids)
            if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) {
                log.info("Built attachments XML: ${attachments ?: "<none>"}")
            }
            if (attachments == null) text to null else newText to attachments
        } catch (_: Exception) {
            text to null
        }
    }

    private fun imageRefToAbsolutePath(ref: String): String? {
        // ref like: @claude-chat-images/filename.png
        val base = project.basePath ?: return null
        val fileName = ref.substringAfter("$IMAGES_DIR_NAME/").takeIf { it.isNotBlank() } ?: return null
        return try {
            java.nio.file.Paths.get(base, ".idea", IMAGES_DIR_NAME, fileName).toFile().absolutePath
        } catch (_: Exception) { null }
    }

    private fun buildImageAttachmentsXml(absPaths: List<String>, ids: List<String>): String? {
        if (absPaths.isEmpty() || absPaths.size != ids.size) return null
        val items = absPaths.zip(ids)
        val body = items.joinToString("") { (path, id) ->
            val p = xmlEscape(path)
            val i = xmlEscape(id)
            "<image id=\"$i\" path=\"$p\"/>"
        }
        return "<attachments>$body</attachments>"
    }

    private fun buildIdeContextXml(): String? {
        return try {
            val fem = FileEditorManager.getInstance(project)
            val editor = fem.selectedTextEditor ?: return null
            val vf = editor.virtualFile ?: return null
            val base = project.basePath
            val path = try {
                if (base != null) {
                    val rel = java.nio.file.Paths.get(base).relativize(java.nio.file.Paths.get(vf.path)).toString()
                    xmlEscape(rel)
                } else xmlEscape(vf.path)
            } catch (_: Exception) {
                xmlEscape(vf.path)
            }
            val caretLine = editor.caretModel.logicalPosition.line + 1
            val sel = editor.selectionModel
            val selAttr = if (sel.hasSelection()) {
                val startLine = editor.offsetToLogicalPosition(sel.selectionStart).line + 1
                val endLine = editor.offsetToLogicalPosition(sel.selectionEnd).line + 1
                " selection_start=\"$startLine\" selection_end=\"$endLine\""
            } else ""
            // Add a human-readable note attribute to ide_context so the model knows this is IDE context
            val notePlain = if (sel.hasSelection()) {
                "IDE context: user is in IntelliJ; focus file '$path'; selection lines ${selAttr.substringAfter("selection_start=\"").substringBefore("\"")} - ${selAttr.substringAfter("selection_end=\"").substringBefore("\"")}"
            } else {
                "IDE context: user is in IntelliJ; focus file '$path'; caret at line $caretLine"
            }
            val note = xmlEscape(notePlain)
            "<ide_context note=\"$note\"><file path=\"$path\" caret_line=\"$caretLine\"$selAttr/></ide_context>"
        } catch (_: Exception) {
            null
        }
    }
    
    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")



    /**
     * Install image paste handler
     */
    private fun installImagePasteHandler() {
        // For IntelliJ Editor, we need to handle paste at the editor level
        // Set transfer handler on the editor's content component
        inputEditor.contentComponent.transferHandler = object : javax.swing.TransferHandler() {
            override fun importData(comp: JComponent, t: Transferable): Boolean {
                try {
                    debugLogFlavors("TransferHandler.importData", t)
                    extractImageFromTransferable(t)?.let { bi ->
                        handlePastedImage(bi)
                        return true
                    }
                } catch (e: Exception) {
                    // Log error but don't show to user
                    log.warn("Failed to handle image paste (TransferHandler)", e)
                }
                return false
            }
            
            override fun canImport(comp: JComponent, transferFlavors: Array<out DataFlavor>): Boolean {
                return transferFlavors.any { it == DataFlavor.imageFlavor || it == DataFlavor.javaFileListFlavor || it.primaryType == "image" }
            }
        }
        
        // Register global paste wrapper for this editor
        ChatPasteImageManager.registerEditor(inputEditor) {
            val contents = CopyPasteManager.getInstance().contents ?: return@registerEditor false
            debugLogFlavors("PasteWrapper", contents)
            extractImageFromTransferable(contents)?.let { img ->
                handlePastedImage(img)
                return@registerEditor true
            }
            false
        }
    }
    
    /**
     * Handle pasted image - save to file and insert @ reference
     */
    private fun handlePastedImage(image: BufferedImage) {
        try {
            // Create images directory in .idea folder to keep it hidden from users
            val ideaDir = File(project.basePath, ".idea")
            if (!ideaDir.exists()) ideaDir.mkdirs()
            
            val imagesDir = File(ideaDir, IMAGES_DIR_NAME)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Generate unique filename
            val timestamp = LocalDateTime.now().format(dateTimeFormatter)
            val uuid = UUID.randomUUID().toString().substring(0, 8)
            val fileName = "screenshot-$timestamp-$uuid.png"
            val imageFile = File(imagesDir, fileName)
            
            // Save image to file
            ImageIO.write(image, "png", imageFile)
            
            // Insert @ reference at current caret position
            val reference = "@$IMAGES_DIR_NAME/$fileName"
            ApplicationManager.getApplication().runWriteAction {
                val currentText = inputDocument.text
                val caretOffset = inputEditor.caretModel.offset
                
                // Insert space before if not at beginning and not already preceded by space
                val prefix = if (caretOffset > 0 && currentText.getOrNull(caretOffset - 1) != ' ' && currentText.getOrNull(caretOffset - 1) != '\n') {
                    " "
                } else {
                    ""
                }
                
                // Insert space after if not at end and not already followed by space
                val suffix = if (caretOffset < currentText.length && currentText.getOrNull(caretOffset) != ' ' && currentText.getOrNull(caretOffset) != '\n') {
                    " "
                } else {
                    ""
                }
                
                val newText = currentText.substring(0, caretOffset) + prefix + reference + suffix + currentText.substring(caretOffset)
                inputDocument.setText(newText)
                
                // Move caret to after the inserted reference
                inputEditor.caretModel.moveToOffset(caretOffset + prefix.length + reference.length)
            }
            
            // Update status to show image was saved
            updateContextInfo("Image saved: $fileName")
            if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) {
                log.info("Pasted image saved to: ${imageFile.absolutePath}")
            }
            
        } catch (e: IOException) {
            // Handle file I/O errors silently
        } catch (e: Exception) {
            // Handle other errors silently
        }
    }

    private fun isImageFile(f: File): Boolean {
        val name = f.name.lowercase()
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")
    }

    private fun toBufferedImage(img: Image): BufferedImage {
        if (img is BufferedImage) return img
        val w = img.getWidth(null)
        val h = img.getHeight(null)
        val b = BufferedImage(if (w > 0) w else 1, if (h > 0) h else 1, BufferedImage.TYPE_INT_ARGB)
        val g2 = b.createGraphics()
        g2.drawImage(img, 0, 0, null)
        g2.dispose()
        return b
    }

    private fun extractImageFromTransferable(t: Transferable): BufferedImage? {
        // 1) Direct java image
        runCatching {
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                (t.getTransferData(DataFlavor.imageFlavor) as? Image)?.let { return toBufferedImage(it) }
            }
        }
        // 2) File list
        runCatching {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @Suppress("UNCHECKED_CAST")
                val files = t.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                val f = files?.firstOrNull { isImageFile(it) }
                if (f != null) return ImageIO.read(f)
            }
        }
        // 3) Any image/* flavor to InputStream/byte[]/Image
        for (flavor in t.transferDataFlavors) {
            runCatching {
                val mt = flavor.mimeType.lowercase()
                if (flavor.primaryType == "image" || mt.startsWith("image/")) {
                    val data = t.getTransferData(flavor)
                    when (data) {
                        is Image -> return toBufferedImage(data)
                        is java.io.InputStream -> return ImageIO.read(data)
                        is ByteArray -> return ImageIO.read(java.io.ByteArrayInputStream(data))
                    }
                }
            }
        }
        // 4) URI list with file://
        runCatching {
            val uriFlavor = DataFlavor("text/uri-list;class=java.lang.String")
            if (t.isDataFlavorSupported(uriFlavor)) {
                val s = t.getTransferData(uriFlavor) as? String
                val uri = s?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
                if (uri != null && uri.startsWith("file:")) {
                    val file = java.nio.file.Paths.get(java.net.URI(uri)).toFile()
                    if (isImageFile(file)) return ImageIO.read(file)
                }
            }
        }
        return null
    }

    private fun debugLogFlavors(source: String, t: Transferable) {
        if (!com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) return
        val flavors = t.transferDataFlavors.joinToString { f ->
            val cl = f.representationClass?.name ?: "?"
            "${f.primaryType}/${f.subType} as $cl"
        }
        log.info("[$source] clipboard flavors: $flavors")
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
                    editor.caretModel.logicalPosition.line + 1
                    
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
    
    private fun setupStatusBar() {
        // Configure status bar
        statusBar.apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(4, 8, 4, 8)
            preferredSize = Dimension(Int.MAX_VALUE, 24)
        }
        
        // Configure status label
        statusLabel.apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            foreground = JBColor.foreground()
        }
        
        // Configure status icon
        statusIcon.apply {
            icon = AllIcons.General.Information
            border = JBUI.Borders.empty(0, 0, 0, 6)
        }
        
        val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            background = JBColor.background()
            add(statusIcon)
            add(statusLabel)
        }
        // Right panel for fixed session id display
        val rightPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            background = JBColor.background()
            sessionIdLabel.apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                foreground = JBColor.foreground()
                toolTipText = null
            }
            add(sessionIdLabel)
        }

        statusBar.add(leftPanel, BorderLayout.WEST)
        statusBar.add(rightPanel, BorderLayout.EAST)
    }

    /**
     * Update the fixed session id label on the right.
     * Always shown when available, including during loading.
     */
    fun updateSessionId(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            sessionIdLabel.text = ""
            sessionIdLabel.toolTipText = null
        } else {
            val shortId = sessionId.take(8)
            sessionIdLabel.text = "Session: $shortId"
            sessionIdLabel.toolTipText = sessionId
        }
    }
    
    /**
     * Clean up resources when the component is disposed
     */
    fun dispose() {
        scope.cancel()
        // Unregister from global manager
        try { ChatEnterToSendManager.unregisterEditor(inputEditor) } catch (_: Exception) { }
        try { ChatPasteImageManager.unregisterEditor(inputEditor) } catch (_: Exception) { }
        // Unregister Shift+Enter shortcut
        try {
            shiftEnterAction?.unregisterCustomShortcutSet(inputEditor.contentComponent)
        } catch (_: Exception) { }
        // Release editor and clean up unique file
        try {
            EditorFactory.getInstance().releaseEditor(inputEditor)
        } catch (_: Exception) { }
        try {
            val io = inputIoFile
            if (io != null && io.exists()) {
                io.delete()
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshIoFiles(listOf(io))
            }
        } catch (_: Exception) { }
    }
}
