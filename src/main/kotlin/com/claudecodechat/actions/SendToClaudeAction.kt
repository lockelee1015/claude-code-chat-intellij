package com.claudecodechat.actions

import com.claudecodechat.ui.swing.ClaudeChatPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class SendToClaudeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            return
        }
        
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Chat")
        
        toolWindow?.show {
            val contentManager = toolWindow.contentManager
            val content = contentManager.getContent(0)
            val panel = content?.component as? ClaudeChatPanel
            panel?.appendCodeToInput(selectedText)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        e.presentation.isEnabled = hasSelection
    }
}