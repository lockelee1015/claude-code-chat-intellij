package com.claudecodechat.ui.swing.renderers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
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
        try {
            if (toolInput is JsonObject) {
                val filePath = toolInput["target_file"]?.jsonPrimitive?.content ?: ""
                // Remove project prefix for cleaner display
                return filePath.substringAfterLast("/")
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
        
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            add(JBScrollPane(editorContent).apply {
                preferredSize = Dimension(-1, 200)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
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
            add(JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 120)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        }
    }
}
