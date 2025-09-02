package com.claudecodechat.actions

import com.claudecodechat.services.ChatHistoryService
import com.claudecodechat.ui.swing.ClaudeChatPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

class ClearChatAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to clear the chat history?",
            "Clear Chat History",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            ChatHistoryService.getInstance(project).clearHistory()
            
            // Also clear UI if it's open
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Claude Chat")
            
            if (toolWindow != null) {
                val contentManager = toolWindow.contentManager
                val content = contentManager.getContent(0)
                val panel = content?.component as? ClaudeChatPanel
                panel?.let { 
                    // Trigger UI refresh by clearing the display
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        // Clear the chat display
                    }
                }
            }
            
            Messages.showInfoMessage(
                project,
                "Chat history has been cleared.",
                "Chat History Cleared"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}