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
import com.intellij.util.ui.JBUI
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
    private val onSend: (text: String, model: String) -> Unit
) : JBPanel<ChatInputBar>(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val completionManager = CompletionManager(project)

    // UI
    private val inputArea: JTextArea = JTextArea(4, 50)
    private val modelComboBox: JComboBox<String> = JComboBox(arrayOf("auto", "sonnet", "opus", "haiku"))
    private val sendButton: JButton = JButton("Send (Ctrl+Enter)")
    private val currentFileLabel: JLabel = JLabel("")

    // Completion UI
    private val completionList: JList<String> = JList()
    private val completionScrollPane: JScrollPane = JScrollPane(completionList)
    private val completionPanel: JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout())
    private var currentCompletionState: CompletionState? = null

    init {
        background = JBColor.background()
        border = JBUI.Borders.empty()

        // Input config
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                    sendMessage()
                    e.consume()
                }
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    hideCompletion()
                    e.consume()
                }
                if (e.keyCode == KeyEvent.VK_TAB || e.keyCode == KeyEvent.VK_ENTER) {
                    if (completionPanel.isVisible) {
                        applySelectedCompletion()
                        e.consume()
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

        // Completion list
        completionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        completionList.background = JBColor.background()
        completionList.foreground = JBColor.foreground()
        completionList.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        completionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> { applySelectedCompletion(); e.consume() }
                    KeyEvent.VK_ESCAPE -> { hideCompletion(); inputArea.requestFocus(); e.consume() }
                }
            }
        })
        completionScrollPane.preferredSize = Dimension(600, 150)
        completionPanel.border = JBUI.Borders.empty()
        completionPanel.add(JLabel("Completions").apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 11)
            foreground = JBColor.gray
            border = JBUI.Borders.empty(2, 5)
        }, BorderLayout.NORTH)
        completionPanel.add(completionScrollPane, BorderLayout.CENTER)
        completionPanel.isVisible = false
        completionPanel.preferredSize = Dimension(0, 0)

        // Layout
        val inputScroll = JBScrollPane(inputArea).apply {
            preferredSize = Dimension(600, 100)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

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
            add(inputScroll, BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }

        add(section, BorderLayout.CENTER)
        add(completionPanel, BorderLayout.SOUTH)

        // Actions
        sendButton.addActionListener { sendMessage() }
        observeCompletion()
        setupFileInfoTracking()
    }

    fun requestInputFocus() { inputArea.requestFocus() }
    fun setText(text: String) { inputArea.text = text }
    fun appendText(text: String) {
        val current = inputArea.text
        inputArea.text = if (current.isEmpty()) text else "$current\n\n$text"
        inputArea.caretPosition = inputArea.text.length
    }

    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        val model = (modelComboBox.selectedItem as? String) ?: "auto"
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
        val items = state.items.mapIndexed { index, _ -> "Item $index" }
        val model = DefaultListModel<String>()
        items.forEach { model.addElement(it) }
        completionList.model = model
        completionList.selectedIndex = state.selectedIndex
        completionPanel.isVisible = true
        completionPanel.preferredSize = Dimension(completionPanel.width, 150)
        if (state.selectedIndex >= 0) completionList.ensureIndexIsVisible(state.selectedIndex)
        completionPanel.revalidate(); completionPanel.repaint()
    }

    private fun applySelectedCompletion() {
        val state = currentCompletionState ?: return
        val idx = completionList.selectedIndex
        if (idx in 0 until state.items.size) applyCompletion(state, idx)
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
        completionPanel.isVisible = false
        completionPanel.preferredSize = Dimension(0, 0)
        completionPanel.revalidate(); completionPanel.repaint()
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
}


