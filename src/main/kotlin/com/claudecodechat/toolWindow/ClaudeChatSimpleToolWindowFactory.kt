package com.claudecodechat.toolWindow

import com.claudecodechat.settings.ClaudeSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.Box

/**
 * Simplified Swing-based tool window factory as fallback
 * This avoids Compose Desktop compatibility issues in IntelliJ plugin environment
 */
class ClaudeChatSimpleToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = createSimpleChatPanel(project)
        val content = ContentFactory.getInstance().createContent(
            chatPanel,
            "Claude Chat",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createSimpleChatPanel(project: Project): JBPanel<JBPanel<*>> {
        val panel = JBPanel<JBPanel<*>>(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // Title
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.insets = Insets(10, 10, 20, 10)
        gbc.anchor = GridBagConstraints.CENTER
        panel.add(JBLabel("<html><h2>Claude Code Chat</h2></html>"), gbc)
        
        // Description
        gbc.gridy = 1
        gbc.insets = Insets(0, 10, 20, 10)
        panel.add(JBLabel("<html><div style='text-align: center'>Simple UI mode for maximum compatibility.<br/>Configure environment variables and Claude path settings below.</div></html>"), gbc)
        
        // Settings button
        gbc.gridy = 2
        gbc.insets = Insets(0, 10, 10, 10)
        val settingsButton = JButton("Open Settings")
        settingsButton.addActionListener {
            showSettingsInfo(project)
        }
        panel.add(settingsButton, gbc)
        
        // Info about CLI usage
        gbc.gridy = 3
        gbc.insets = Insets(20, 10, 10, 10)
        panel.add(JBLabel("<html><div style='text-align: center'><b>Usage:</b><br/>Configure environment variables in Settings → Tools → Claude Code Chat<br/>Then use 'claude' command in your terminal</div></html>"), gbc)
        
        // Spacer
        gbc.gridy = 4
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.VERTICAL
        panel.add(Box.createVerticalGlue(), gbc)
        
        return panel
    }
    
    private fun showSettingsInfo(project: Project) {
        val settings = ClaudeSettings.getInstance()
        val info = buildString {
            appendLine("Current Settings:")
            appendLine()
            appendLine("Claude Path: ${if (settings.claudePath.isEmpty()) "(using system PATH)" else settings.claudePath}")
            appendLine()
            if (settings.environmentVariables.isNotEmpty()) {
                appendLine("Environment Variables:")
                settings.environmentVariables.lines().forEach { line ->
                    if (line.trim().isNotEmpty() && !line.trim().startsWith("#")) {
                        appendLine("  $line")
                    }
                }
            } else {
                appendLine("Environment Variables: (none configured)")
            }
            appendLine()
            appendLine("To modify settings, go to:")
            appendLine("Settings → Tools → Claude Code Chat")
        }
        
        Messages.showInfoMessage(
            project,
            info,
            "Claude Code Chat Settings"
        )
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}