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
import kotlinx.coroutines.*
import com.claudecodechat.persistence.SessionPersistence
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
     * Create IntelliJ Editor for chat input with PSI support using a unique file per tab
     */
    private fun createInputEditor(): Pair<Editor, Document> {
        // Create a unique real file for better PSI and completion support (per tab)
        val projectBasePath = project.basePath ?: System.getProperty("user.home")
        val chatInputDir = java.io.File(projectBasePath, ".chat-input")
        if (!chatInputDir.exists()) chatInputDir.mkdirs()
        val uniqueName = "chat-input-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".md"
        val chatInputFile = java.io.File(chatInputDir, uniqueName)
        if (!chatInputFile.exists()) chatInputFile.writeText("")
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
                val hovering = linkRanges.any { it.containsOffset(offset) }
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
                val link = linkRanges.firstOrNull { it.containsOffset(offset) } ?: return
                val token = inputDocument.getText(link).removePrefix("@")
                openFileToken(token)
                me.consume()
            }
        }
        EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(clickListener, project)
    }

    private fun TextRange.containsOffset(offset: Int): Boolean = offset in startOffset until endOffset

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
                val io = java.io.File(base, token)
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
        val text = inputDocument.text.trim()
        if (text.isEmpty()) return
        val model = (modelComboBox.selectedItem as? String) ?: "auto"
        
        // Build IDE context XML and append after main content if available
        val ideContext = buildIdeContextXml()
        val finalText = if (ideContext != null) "$text\n\n$ideContext" else text
        
        // Show loading state
        showLoading(text)
        
        // Clear input in write action
        ApplicationManager.getApplication().runWriteAction {
            inputDocument.setText("")
        }
        onSend(finalText, model, planModeCheckBox.isSelected)
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
