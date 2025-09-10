package com.claudecodechat.ui.swing.renderers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
import java.awt.*
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Generic renderer for tools that don't have specific implementations
 */
class GenericRenderer : ToolRenderer() {
    
    override fun canHandle(toolName: String): Boolean {
        // Generic renderer handles everything as fallback
        return true
    }
    
    override fun extractDisplayParameters(toolInput: JsonElement?): String {
        // Try to extract common parameters from JSON
        try {
            val inputStr = toolInput.toString()
            // Look for common parameter patterns
            val patterns = listOf("file_path", "pattern", "command", "target_file")
            for (pattern in patterns) {
                val regex = Regex(""""$pattern"\s*:\s*"([^"]*)"""")
                val match = regex.find(inputStr)
                if (match != null) {
                    val value = match.groupValues[1]
                    return if (value.length > 50) value.take(47) + "..." else value
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
    }
    
    override fun renderContent(input: ToolRenderInput): JPanel {
        return when (input.status) {
            ToolStatus.ERROR -> createErrorPanel(input.toolOutput)
            ToolStatus.IN_PROGRESS -> createInProgressPanel()
            else -> createSuccessPanel(input.toolOutput)
        }
    }
    
    private fun createErrorPanel(errorMessage: String): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            
            val errorContent = JTextArea(errorMessage).apply {
                foreground = Color(220, 38, 38)
                background = JBColor.background()
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            }
            
            add(errorContent, BorderLayout.CENTER)
        }
    }
    
    private fun createSuccessPanel(output: String): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            
            if (output.isNotEmpty()) {
                val displayText = if (output.length > 200) {
                    output.take(197) + "..."
                } else {
                    output
                }
                
                val content = JTextArea(displayText).apply {
                    foreground = JBColor.foreground()
                    background = JBColor.background()
                    font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                }
                
                add(content, BorderLayout.CENTER)
            } else {
                val statusLabel = JBLabel("Tool executed successfully").apply {
                    foreground = Color(34, 197, 94)
                    font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                }
                
                add(statusLabel, BorderLayout.CENTER)
            }
        }
    }

    private fun createInProgressPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                background = JBColor.background()
            }
            val spinner = javax.swing.JLabel(com.intellij.ui.AnimatedIcon.Default()).apply {
                foreground = JBColor.foreground()
            }
            val label = JBLabel("Running...").apply {
                foreground = JBColor.namedColor("Link.activeForeground", JBColor(0x589df6, 0x548af7))
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            }
            row.add(spinner)
            row.add(label)
            add(row, BorderLayout.CENTER)
        }
    }
}
