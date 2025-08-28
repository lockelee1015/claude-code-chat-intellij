package com.claudecodechat.toolWindow

import com.claudecodechat.ui.compose.ClaudeChatPanelFinal
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Tool window factory for the Compose-based Claude Chat panel
 */
class ClaudeChatComposeToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = ClaudeChatPanelFinal(project)
        val content = ContentFactory.getInstance().createContent(
            chatPanel.createComponent(),
            "Claude Chat",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}