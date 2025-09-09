package com.claudecodechat.ui.swing

import com.claudecodechat.persistence.SessionPersistence
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout

class McpConfigDialog(private val project: Project, private val logicalSessionId: String?) : DialogWrapper(project, true) {
    private var editor: Editor? = null
    private var vFile: VirtualFile? = null
    private val checkboxPanel = JBPanel<JBPanel<*>>(GridLayout(0,1,0,4))
    private val checkboxScroll = JScrollPane(checkboxPanel)
    private val persistence = SessionPersistence.getInstance(project)
    private val projectPath = project.basePath

    init {
        title = "MCP Configuration"
        init()
    }

    override fun createCenterPanel(): JComponent {
        ensureEditor()
        val root = JBPanel<JBPanel<*>>(BorderLayout()).apply { border = JBUI.Borders.empty(8) }
        val split = com.intellij.ui.OnePixelSplitter(true, 0.55f)
        split.firstComponent = JPanel(BorderLayout()).apply {
            add(JLabel("Servers (select for this session)"), BorderLayout.NORTH)
            add(checkboxScroll, BorderLayout.CENTER)
            border = JBUI.Borders.emptyRight(8)
        }
        split.secondComponent = JPanel(BorderLayout()).apply {
            add(JLabel(".mcp.json"), BorderLayout.NORTH)
            add(editor?.component ?: JPanel(), BorderLayout.CENTER)
        }
        root.add(split, BorderLayout.CENTER)
        rebuildCheckboxes()
        return root
    }

    private fun ensureEditor() {
        if (editor != null) return
        val base = projectPath ?: return
        val io = java.io.File(base, ".mcp.json")
        if (!io.exists()) {
            io.writeText("{\n  \"mcpServers\": {}\n}\n")
        }
        vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(io)
        val doc = FileDocumentManager.getInstance().getDocument(vFile!!)
            ?: throw IllegalStateException("Cannot open .mcp.json document")
        val jsonType = FileTypeManager.getInstance().getFileTypeByExtension("json")
        editor = EditorFactory.getInstance().createEditor(doc, project, jsonType, false)
        // basic editor settings
        editor!!.settings.apply {
            isLineNumbersShown = true
            isUseSoftWraps = true
        }
        // Rebuild checkboxes on content change (debounced by Swing)
        doc.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                SwingUtilities.invokeLater { rebuildCheckboxes() }
            }
        })
    }

    private fun readText(): String {
        val doc = editor?.document ?: return ""
        return doc.text
    }

    private fun writeText(text: String) {
        val doc = editor?.document ?: return
        ApplicationManager.getApplication().runWriteAction {
            doc.setText(text)
            FileDocumentManager.getInstance().saveDocument(doc)
        }
    }

    private fun parseServers(text: String): List<String> {
        return try {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(text)
            val obj = root as? kotlinx.serialization.json.JsonObject ?: return emptyList()
            val key = when {
                obj.containsKey("mcpServers") -> "mcpServers"
                obj.containsKey("servers") -> "servers"
                else -> return emptyList()
            }
            val servers = obj[key] as? kotlinx.serialization.json.JsonObject ?: return emptyList()
            servers.keys.toList().sorted()
        } catch (e: Exception) { emptyList() }
    }

    private fun rebuildCheckboxes() {
        checkboxPanel.removeAll()
        val names = parseServers(readText())
        val selected = if (logicalSessionId != null) persistence.getSelectedMcpServers(logicalSessionId) else emptyList()
        names.forEach { name ->
            val cb = JBCheckBox(name, selected.contains(name))
            checkboxPanel.add(cb)
        }
        checkboxPanel.revalidate()
        checkboxPanel.repaint()
    }

    override fun doOKAction() {
        // Save selections
        val selected = (0 until checkboxPanel.componentCount)
            .mapNotNull { checkboxPanel.getComponent(it) as? JBCheckBox }
            .filter { it.isSelected }
            .map { it.text }
        if (logicalSessionId != null) {
            persistence.setSelectedMcpServers(logicalSessionId, selected)
        }
        // Save file
        try {
            val doc = editor?.document
            if (doc != null) FileDocumentManager.getInstance().saveDocument(doc)
        } catch (_: Exception) {}

        super.doOKAction()
    }

    override fun dispose() {
        editor?.let { EditorFactory.getInstance().releaseEditor(it) }
        super.dispose()
    }
}
