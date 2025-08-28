package com.claudecodechat.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.time.LocalDateTime

@Service(Service.Level.PROJECT)
class ChatHistoryService {
    private val chatHistory = mutableListOf<ChatMessage>()
    
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    companion object {
        fun getInstance(project: Project): ChatHistoryService = project.service()
    }
    
    fun addMessage(role: String, content: String) {
        chatHistory.add(ChatMessage(role, content))
    }
    
    fun getHistory(): List<ChatMessage> {
        return chatHistory.toList()
    }
    
    fun clearHistory() {
        chatHistory.clear()
    }
    
    fun getRecentMessages(count: Int): List<ChatMessage> {
        return chatHistory.takeLast(count)
    }
}