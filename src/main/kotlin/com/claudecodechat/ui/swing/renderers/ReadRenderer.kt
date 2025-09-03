package com.claudecodechat.ui.swing.renderers

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Renderer for Read tool
 */
class ReadRenderer : ToolRenderer() {
    
    override fun canHandle(toolName: String): Boolean {
        return toolName.lowercase() == "read"
    }
    
    override fun extractDisplayParameters(toolInput: JsonElement?): String {
        return getFilePath(toolInput)
    }
    
    private fun getFilePath(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                return toolInput["file_path"]?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
    }
    
    override fun renderContent(input: ToolRenderInput): JPanel {
        if (input.status == ToolStatus.ERROR) {
            return createErrorPanel(input.toolOutput)
        }
        
        val lines = input.toolOutput.split("\n")
        val totalLines = lines.size
        
        // Simple line count display
        val summaryLabel = JTextArea("Read $totalLines lines").apply {
            foreground = JBColor.foreground()
            background = JBColor.background()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = false
            border = JBUI.Borders.empty(8)
        }
        
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            add(summaryLabel, BorderLayout.CENTER)
            
            // Set smaller height
            preferredSize = Dimension(-1, 35)
            
            // Disable scrolling by default, enable on click
            ScrollControlUtil.disableScrollingByDefault(this)
        }
    }
    
    private fun createErrorPanel(errorMessage: String): JPanel {
        val errorContent = JTextArea(errorMessage).apply {
            foreground = Color(220, 38, 38)
            background = JBColor.background()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }
        
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            val scrollPane = JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 120)
                border = JBUI.Borders.empty()
            }
            add(scrollPane, BorderLayout.CENTER)
            
            // Disable scrolling by default, enable on click
            ScrollControlUtil.disableScrollingByDefault(scrollPane)
        }
    }
}
