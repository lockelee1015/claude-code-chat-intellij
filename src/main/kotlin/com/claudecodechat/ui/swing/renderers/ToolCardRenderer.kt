package com.claudecodechat.ui.swing.renderers

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JPanel
import javax.swing.Box

/**
 * Parent renderer that wraps tool content with title bar and card styling
 */
class ToolCardRenderer {
    
    /**
     * Create a complete tool card with title bar and content
     */
    fun createCard(
        toolName: String,
        toolInput: JsonElement?,
        toolOutput: String,
        status: ToolStatus,
        renderer: ToolRenderer
    ): JPanel {
        val input = ToolRenderInput(toolName, toolInput, toolOutput, status)
        
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = createCardBorder()
            background = JBColor.background()
            maximumSize = Dimension(Int.MAX_VALUE, 300) // Limit height
            
            // Create title bar (with special handling for Read tool)
            val titleBar = if (toolName.lowercase() == "read") {
                createClickableTitleBar(toolName, renderer.extractDisplayParameters(toolInput), status, toolInput)
            } else {
                createTitleBar(toolName, renderer.extractDisplayParameters(toolInput), status)
            }
            add(titleBar, BorderLayout.NORTH)
            
            // Create content panel using specific renderer
            val contentPanel = renderer.renderContent(input)
            add(contentPanel, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create title bar with status colors
     */
    private fun createTitleBar(toolName: String, parameters: String, status: ToolStatus): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = getStatusBackgroundColor(status)
            border = JBUI.Borders.empty(8, 12)
            
            val title = if (parameters.isNotEmpty()) "$toolName($parameters)" else toolName
            val titleLabel = JBLabel(title).apply {
                foreground = JBColor.foreground()
                font = Font(Font.MONOSPACED, Font.BOLD, 12)
            }
            
            add(titleLabel, BorderLayout.WEST)
        }
    }
    
    /**
     * Create clickable title bar for Read tool
     */
    private fun createClickableTitleBar(toolName: String, filePath: String, status: ToolStatus, toolInput: JsonElement?): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = getStatusBackgroundColor(status)
            border = JBUI.Borders.empty(8, 12)
            
            val fileName = if (filePath.isNotEmpty()) filePath.substringAfterLast("/") else "unknown"
            
            // Create horizontal panel to hold tool name and clickable file name
            val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                background = getStatusBackgroundColor(status)
                
                // Tool name (non-clickable)
                val toolLabel = JBLabel("$toolName(").apply {
                    foreground = JBColor.foreground()
                    font = Font(Font.MONOSPACED, Font.BOLD, 12)
                }
                add(toolLabel)
                
                // File name (clickable)
                val fileLabel = JBLabel(fileName).apply {
                    foreground = Color(0x6aa9ff) // 蓝色表示可点击
                    font = Font(Font.MONOSPACED, Font.BOLD, 12)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            openFileInEditor(filePath)
                        }
                    })
                }
                add(fileLabel)
                
                // Closing parenthesis (non-clickable)
                val closeLabel = JBLabel(")").apply {
                    foreground = JBColor.foreground()
                    font = Font(Font.MONOSPACED, Font.BOLD, 12)
                }
                add(closeLabel)
            }
            
            add(titlePanel, BorderLayout.WEST)
        }
    }
    
    /**
     * Open file in IntelliJ editor
     */
    private fun openFileInEditor(filePath: String) {
        try {
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            if (project != null && filePath.isNotEmpty()) {
                val file = File(filePath)
                if (file.exists()) {
                    val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
                    if (virtualFile != null) {
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors when opening file
        }
    }
    
    /**
     * Get background color based on tool status
     */
    private fun getStatusBackgroundColor(status: ToolStatus): Color {
        return when (status) {
            ToolStatus.ERROR -> Color(255, 235, 235, 180)     // 浅红色透明
            ToolStatus.IN_PROGRESS -> Color(235, 245, 255, 180) // 浅蓝色透明
            ToolStatus.SUCCESS -> Color(235, 255, 235, 180)     // 浅绿色透明
        }
    }
    
    /**
     * Create rounded border for tool cards
     */
    private fun createCardBorder(): javax.swing.border.Border {
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
}
