package com.claudecodechat.ui.swing.renderers

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
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
                    val titleBar = if (toolName.lowercase() == "read" || 
                              toolName.lowercase() in listOf("edit", "multiedit", "write")) {
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
     * Create clickable title bar for Read and Edit tools
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
                    foreground = JBColor.namedColor("Link.activeForeground", JBColor(0x589df6, 0x548af7)) // IntelliJ 默认链接颜色
                    font = Font(Font.MONOSPACED, Font.BOLD, 12)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            val fullPath = getFullFilePath(toolInput)
                            openFileInEditor(fullPath)
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
            
            // Add Show Diff/Content link for Edit and Write tools
            when (toolName.lowercase()) {
                in listOf("edit", "multiedit") -> {
                    val diffLink = JBLabel("Show Diff").apply {
                        foreground = JBColor.namedColor("Link.activeForeground", JBColor(0x589df6, 0x548af7))
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        border = JBUI.Borders.empty(0, 8)
                        
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                showFullDiffForEdit(toolInput)
                            }
                            
                            override fun mouseEntered(e: MouseEvent) {
                                foreground = JBColor.namedColor("Link.hoverForeground", JBColor(0x4a90e2, 0x6ba7f5))
                            }
                            
                            override fun mouseExited(e: MouseEvent) {
                                foreground = JBColor.namedColor("Link.activeForeground", JBColor(0x589df6, 0x548af7))
                            }
                        })
                    }
                    add(diffLink, BorderLayout.EAST)
                }
                "write" -> {
                    val contentLink = JBLabel("Show Content").apply {
                        foreground = JBColor.namedColor("Link.activeForeground", JBColor(0x589df6, 0x548af7))
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        border = JBUI.Borders.empty(0, 8)
                        
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                showContentForWrite(toolInput)
                            }
                            
                            override fun mouseEntered(e: MouseEvent) {
                                foreground = JBColor.namedColor("Link.hoverForeground", JBColor(0x4a90e2, 0x6ba7f5))
                            }
                            
                            override fun mouseExited(e: MouseEvent) {
                                foreground = JBColor.namedColor("Link.activeForeground", JBColor(0x589df6, 0x548af7))
                            }
                        })
                    }
                    add(contentLink, BorderLayout.EAST)
                }
            }
        }
    }
    
    /**
     * Get full file path from tool input
     */
    private fun getFullFilePath(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                // For Read tool
                val targetFile = toolInput["target_file"]?.jsonPrimitive?.content
                if (!targetFile.isNullOrEmpty()) return targetFile
                
                // For Edit tools
                val filePath = toolInput["file_path"]?.jsonPrimitive?.content
                if (!filePath.isNullOrEmpty()) return filePath
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
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
     * Get background color based on tool status with dark mode support
     */
    private fun getStatusBackgroundColor(status: ToolStatus): Color {
        return when (status) {
            ToolStatus.ERROR -> if (JBColor.isBright()) {
                Color(255, 235, 235, 180)     // 明亮模式：浅红色透明
            } else {
                Color(80, 30, 30, 180)        // 深色模式：深红色透明
            }
            ToolStatus.IN_PROGRESS -> if (JBColor.isBright()) {
                Color(235, 245, 255, 180)     // 明亮模式：浅蓝色透明
            } else {
                Color(30, 40, 80, 180)        // 深色模式：深蓝色透明
            }
            ToolStatus.SUCCESS -> if (JBColor.isBright()) {
                Color(235, 255, 235, 180)     // 明亮模式：浅绿色透明
            } else {
                Color(40, 60, 40, 160)        // 深色模式：更低调的深绿色透明
            }
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
    
    /**
     * Show full diff for Edit tools
     */
    private fun showFullDiffForEdit(toolInput: JsonElement?) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        
        try {
            if (toolInput is JsonObject) {
                val oldString = toolInput["old_string"]?.jsonPrimitive?.content ?: ""
                val newString = toolInput["new_string"]?.jsonPrimitive?.content ?: ""
                val filePath = toolInput["file_path"]?.jsonPrimitive?.content ?: ""
                
                if (oldString.isNotEmpty() && newString.isNotEmpty()) {
                    val fileName = if (filePath.isNotEmpty()) filePath.substringAfterLast("/") else "File"
                    
                    val diffRequest = SimpleDiffRequest(
                        "Edit Changes: $fileName",
                        DiffContentFactory.getInstance().create(oldString),
                        DiffContentFactory.getInstance().create(newString),
                        "Before",
                        "After"
                    )
                    
                    DiffManager.getInstance().showDiff(project, diffRequest)
                }
            }
        } catch (e: Exception) {
            // Ignore errors in diff display
        }
    }
    
    /**
     * Show content for Write tools using virtual file
     */
    private fun showContentForWrite(toolInput: JsonElement?) {
        try {
            if (toolInput is JsonObject) {
                val filePath = toolInput["file_path"]?.jsonPrimitive?.content ?: ""
                val content = toolInput["contents"]?.jsonPrimitive?.content ?: ""
                
                if (content.isNotEmpty()) {
                    // Try to get file type for syntax highlighting
                    val fileType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance()
                        .getFileTypeByFileName(filePath.substringAfterLast("/"))
                    
                    // Use WriteRenderer to show content in editor
                    val writeRenderer = WriteRenderer()
                    writeRenderer.showContentInEditor(filePath, content, fileType)
                }
            }
        } catch (e: Exception) {
            // Ignore errors in content display
        }
    }
}
