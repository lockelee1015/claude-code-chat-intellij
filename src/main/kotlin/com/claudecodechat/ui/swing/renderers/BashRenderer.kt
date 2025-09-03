package com.claudecodechat.ui.swing.renderers

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
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
        return if (input.status == ToolStatus.ERROR) {
            createErrorPanel(input.toolOutput, extractDisplayParameters(input.toolInput))
        } else {
            createConsolePanel(input.toolInput, input.toolOutput)
        }
    }
    
    /**
     * Create console panel using IntelliJ's Console component
     */
    private fun createConsolePanel(toolInput: JsonElement?, toolOutput: String): JPanel {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        
        return try {
            val consoleView = createConsoleView(project)
            
            // Add command and output to console with proper formatting
            val command = extractFullCommand(toolInput)
            if (command.isNotEmpty()) {
                // Show command prompt with user input style
                consoleView.print("$ ", ConsoleViewContentType.SYSTEM_OUTPUT)
                consoleView.print("$command\n", ConsoleViewContentType.USER_INPUT)
            }
            
            if (toolOutput.isNotEmpty()) {
                // Add output with normal console output style
                consoleView.print(toolOutput, ConsoleViewContentType.NORMAL_OUTPUT)
                
                // Ensure output ends with newline for better formatting
                if (!toolOutput.endsWith("\n")) {
                    consoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
                }
            }
            
            // Wrap console component
            JPanel(BorderLayout()).apply {
                background = JBColor.background()
                add(consoleView.component, BorderLayout.CENTER)
                preferredSize = Dimension(-1, 150)
                
                // Store console reference for cleanup
                putClientProperty("consoleView", consoleView)
            }
            
        } catch (e: Exception) {
            // Fallback to simple text view
            createFallbackPanel(toolInput, toolOutput)
        }
    }
    
    /**
     * Create IntelliJ Console View with enhanced features
     */
    private fun createConsoleView(project: Project?): ConsoleView {
        val actualProject = project ?: ProjectManager.getInstance().defaultProject
        
        return ConsoleViewImpl(actualProject, true).apply {
            // Enable context menu and other console features
            allowHeavyFilters()
            
            // Set up console with proper capabilities
            try {
                // Enable console features like copy, clear, etc.
                val consoleComponent = component
                consoleComponent.isOpaque = true
                consoleComponent.background = JBColor.background()
            } catch (e: Exception) {
                // Ignore if features not available
            }
        }
    }
    
    /**
     * Extract full command from tool input
     */
    private fun extractFullCommand(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                return toolInput["command"]?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
    }
    
    /**
     * Create error panel for failed commands
     */
    private fun createErrorPanel(errorOutput: String, command: String): JPanel {
        val errorContent = JTextArea().apply {
            foreground = Color(220, 38, 38) // Red for errors
            background = Color(45, 21, 21) // Dark red background
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = false
            border = JBUI.Borders.empty(8)
            
            text = buildString {
                if (command.isNotEmpty()) {
                    append("$ $command\n")
                }
                append(errorOutput)
            }
        }
        
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            add(JBScrollPane(errorContent).apply {
                preferredSize = Dimension(-1, 150)
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create fallback panel when console creation fails
     */
    private fun createFallbackPanel(toolInput: JsonElement?, toolOutput: String): JPanel {
        val command = extractFullCommand(toolInput)
        
        val shellContent = JTextArea().apply {
            foreground = JBColor.foreground()
            background = Color(40, 40, 40) // Terminal-like dark background
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            lineWrap = false
            border = JBUI.Borders.empty(8)
            
            text = buildString {
                if (command.isNotEmpty()) {
                    append("$ $command\n")
                }
                append(toolOutput)
            }
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
