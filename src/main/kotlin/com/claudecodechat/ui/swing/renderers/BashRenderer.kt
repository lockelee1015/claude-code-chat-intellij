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
 * Renderer for Bash tool
 */
class BashRenderer : ToolRenderer() {
    
    override fun canHandle(toolName: String): Boolean {
        return toolName.lowercase() == "bash"
    }
    
    override fun extractDisplayParameters(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                val command = toolInput["command"]?.jsonPrimitive?.content ?: ""
                return if (command.length > 50) command.take(47) + "..." else command
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
    }
    
    override fun renderContent(input: ToolRenderInput): JPanel {
        val command = extractDisplayParameters(input.toolInput)
        val textColor = if (input.status == ToolStatus.ERROR) Color(220, 38, 38) else JBColor.foreground()
        
        val shellContent = JTextArea("$ $command\n${input.toolOutput}").apply {
            foreground = textColor
            background = JBColor.background()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = false
            border = JBUI.Borders.empty(8)
        }
        
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            add(JBScrollPane(shellContent).apply {
                preferredSize = Dimension(-1, 150)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        }
    }
}
