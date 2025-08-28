package com.claudecodechat.actions

import com.claudecodechat.services.ChatHistoryService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

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