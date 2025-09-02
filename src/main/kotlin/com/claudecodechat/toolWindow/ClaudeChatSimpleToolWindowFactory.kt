package com.claudecodechat.toolWindow

import com.claudecodechat.ui.swing.ClaudeChatPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Swing-based tool window factory for Claude Chat
 * Uses stable Swing components for maximum compatibility across IntelliJ versions
 */
class ClaudeChatSimpleToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = ClaudeChatPanel(project)
        val content = ContentFactory.getInstance().createContent(
            chatPanel,
            "Claude Chat",
            false
        )
        toolWindow.contentManager.addContent(content)
        
        // Store reference for disposal
        content.putUserData(CHAT_PANEL_KEY, chatPanel)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
    
    companion object {
        private val CHAT_PANEL_KEY = com.intellij.openapi.util.Key.create<ClaudeChatPanel>("CHAT_PANEL")
    }
}