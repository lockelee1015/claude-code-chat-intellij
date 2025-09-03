package com.claudecodechat.ui.swing

import com.claudecodechat.completion.CompletionManager
import com.claudecodechat.completion.CompletionState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.awt.RelativePoint
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.util.ui.JBUI
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
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
    private val onSend: (text: String, model: String) -> Unit,
    private val onStop: (() -> Unit)? = null
) : JBPanel<ChatInputBar>(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val completionManager = CompletionManager(project)

    // UI
    private val inputArea: JTextArea = JTextArea(4, 50)
    private val modelComboBox: JComboBox<String> = JComboBox(arrayOf("auto", "sonnet", "opus", "haiku"))
    private val sendButton: JButton = JButton("Send (Ctrl+Enter)")
    private val currentFileLabel: JLabel = JLabel("")
    
    // Loading bar components
    private val loadingPanel: JBPanel<JBPanel<*>> = JBPanel(BorderLayout())
    private val loadingIcon: JLabel = JLabel(AnimatedIcon.Default.INSTANCE)
    private val loadingText: JLabel = JLabel("")
    private val stopButton: JButton = JButton("Stop")

    // Completion UI
    private val completionList: JList<String> = JList()
    private var completionPopup: JBPopup? = null
    private var currentCompletionState: CompletionState? = null
    private var currentSelectedIndex: Int = 0

    init {
        background = JBColor.background()
        border = JBUI.Borders.empty()

        // Input config
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
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
                            val state = currentCompletionState
                            if (state != null && state.items.isNotEmpty()) {
                                val currentIndex = completionList.selectedIndex
                                val newIndex = when (e.keyCode) {
                                    KeyEvent.VK_UP -> if (currentIndex > 0) currentIndex - 1 else state.items.size - 1
                                    KeyEvent.VK_DOWN -> if (currentIndex < state.items.size - 1) currentIndex + 1 else 0
                                    else -> currentIndex
                                }
                                completionList.selectedIndex = newIndex
                                completionList.ensureIndexIsVisible(newIndex)
                                currentSelectedIndex = newIndex
                                e.consume() // Only consume when popup is visible
                            }
                        }
                    }
                    e.keyCode == KeyEvent.VK_TAB || e.keyCode == KeyEvent.VK_ENTER -> {
                        if (completionPopup?.isVisible == true) {
                            applySelectedCompletion()
                            e.consume()
                        }
                    }
                }
            }
            override fun keyReleased(e: KeyEvent) {
                if (!e.isControlDown && !e.isAltDown) updateCompletion()
            }
        })
        inputArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = updateCompletion()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = updateCompletion()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = updateCompletion()
        })

        // Completion list (minimal setup for popup)
        completionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        completionList.background = JBColor.background()
        completionList.foreground = JBColor.foreground()
        completionList.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        
        // Add keyboard support to completion list
        completionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
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

        // Setup loading panel
        setupLoadingPanel()

        // Layout
        val inputScroll = JBScrollPane(inputArea).apply {
            preferredSize = Dimension(600, 100)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = createRoundedBorder(JBColor.border(), 1, 8)
        }
        
        // Add focus listener to change border color
        inputArea.addFocusListener(object : java.awt.event.FocusListener {
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
            val right = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
                add(modelComboBox)
                add(sendButton)
            }
            add(right, BorderLayout.EAST)
        }

        val section = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(loadingPanel, BorderLayout.NORTH)
            add(inputScroll, BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }

        add(section, BorderLayout.CENTER)
        // Don't add completion panel to layout - it will be a popup

        // Actions
        sendButton.addActionListener { sendMessage() }
        observeCompletion()
        setupFileInfoTracking()
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

    fun requestInputFocus() { inputArea.requestFocus() }
    fun setText(text: String) { inputArea.text = text }
    fun getText(): String = inputArea.text
    fun getCaretPosition(): Int = inputArea.caretPosition
    fun setCaretPosition(pos: Int) { inputArea.caretPosition = pos }
    fun appendText(text: String) {
        val current = inputArea.text
        inputArea.text = if (current.isEmpty()) text else "$current\n\n$text"
        inputArea.caretPosition = inputArea.text.length
    }
    
    // Loading state management
    fun showLoading(userPrompt: String) {
        val summary = createPromptSummary(userPrompt)
        loadingText.text = summary
        loadingPanel.isVisible = true
        sendButton.isEnabled = false
        revalidate()
        repaint()
    }
    
    fun hideLoading() {
        loadingPanel.isVisible = false
        sendButton.isEnabled = true
        revalidate()
        repaint()
    }
    
    private fun createPromptSummary(prompt: String): String {
        return if (prompt.length <= 50) prompt else prompt.take(47) + "..."
    }

    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        val model = (modelComboBox.selectedItem as? String) ?: "auto"
        
        // Show loading state
        showLoading(text)
        
        inputArea.text = ""
        hideCompletion()
        onSend(text, model)
    }

    private fun updateCompletion() {
        val text = inputArea.text
        val cursorPos = inputArea.caretPosition
        completionManager.updateCompletion(text, cursorPos)
    }

    private fun observeCompletion() {
        scope.launch {
            completionManager.completionState.collect { state ->
                handleCompletionState(state)
            }
        }
    }

    private fun handleCompletionState(state: CompletionState) {
        if (state.isShowing && state.items.isNotEmpty()) {
            showCompletionPopup(state)
        } else {
            hideCompletion()
        }
    }

    private fun showCompletionPopup(state: CompletionState) {
        currentCompletionState = state
        currentSelectedIndex = state.selectedIndex
        
        // Close existing popup
        completionPopup?.cancel()
        

        
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
        
        // Update the JList model and selection
        val listModel = DefaultListModel<String>()
        items.forEach { listModel.addElement(it) }
        completionList.model = listModel
        completionList.selectedIndex = currentSelectedIndex
        
        // Create scrollable popup using ComponentPopupBuilder
        val scrollPane = JBScrollPane(completionList).apply {
            // Height for ~8 items (25px per item + padding)
            preferredSize = Dimension(inputArea.width, 220)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.empty()
        }
        
        completionPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, completionList)
            .setTitle("Completions")
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(false)
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
        
        // Position popup directly above the input area with more space
        val popupX = inputLocationOnScreen.x
        val popupY = inputLocationOnScreen.y - 220 - 40 // 10px gap above 220px height
        
        completionPopup?.showInScreenCoordinates(inputArea, Point(popupX, popupY))
    }

    private fun applySelectedCompletion() {
        val state = currentCompletionState ?: return
        val selectedIndex = completionList.selectedIndex
        if (selectedIndex in 0 until state.items.size) {
            applyCompletion(state, selectedIndex)
        }
    }

    private fun applyCompletion(state: CompletionState, selectedIndex: Int) {
        val selectedItem = state.items[selectedIndex]
        val currentText = inputArea.text
        val triggerPos = state.triggerPosition
        val newText = when (selectedItem) {
            is com.claudecodechat.completion.CompletionItem.SlashCommand -> {
                val before = currentText.substring(0, triggerPos)
                val after = currentText.substring(inputArea.caretPosition)
                before + "/${selectedItem.name} " + after
            }
            is com.claudecodechat.completion.CompletionItem.FileReference -> {
                val before = currentText.substring(0, triggerPos)
                val after = currentText.substring(inputArea.caretPosition)
                before + "@${selectedItem.relativePath} " + after
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
                val line = editor.caretModel.logicalPosition.line + 1
                val relativePath = if (vf != null) {
                    com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(vf, project.baseDir ?: vf) ?: vf.name
                } else ""
                val text = if (relativePath.isNotEmpty()) "current file: $relativePath | line: $line" else ""
                ApplicationManager.getApplication().invokeLater {
                    currentFileLabel.text = text
                    currentFileLabel.isVisible = text.isNotEmpty()
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
    
    private fun setupLoadingPanel() {
        // Configure loading components
        loadingIcon.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        loadingText.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        loadingText.foreground = JBColor.foreground()
        
        stopButton.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        stopButton.preferredSize = Dimension(60, 24)
        stopButton.addActionListener {
            onStop?.invoke()
            hideLoading()
        }
        
        // Layout loading panel
        loadingPanel.apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(4, 8, 4, 8)
            
            val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                background = JBColor.background()
                add(loadingIcon)
                add(loadingText)
            }
            
            val rightPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                background = JBColor.background()
                add(stopButton)
            }
            
            add(leftPanel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.EAST)
            
            // Initially hidden
            isVisible = false
        }
    }
}



