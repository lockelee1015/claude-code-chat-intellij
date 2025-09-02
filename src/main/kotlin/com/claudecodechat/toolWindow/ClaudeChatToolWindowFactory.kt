package com.claudecodechat.toolWindow

import com.claudecodechat.services.ChatHistoryService
import com.claudecodechat.services.ClaudeApiService
import com.claudecodechat.settings.ClaudeSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class ClaudeChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            ClaudeChatPanel(project),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    class ClaudeChatPanel(private val project: Project) : JPanel(BorderLayout()) {
        private val chatArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        
        private val inputArea = JTextArea(5, 0).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        
        private val sendButton = JButton("Send")
        private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        init {
            setupUI()
            setupListeners()
        }
        
        private fun setupUI() {
            val chatScrollPane = JBScrollPane(chatArea).apply {
                preferredSize = Dimension(400, 400)
            }
            add(chatScrollPane, BorderLayout.CENTER)
            
            val bottomPanel = JPanel(BorderLayout())
            val inputScrollPane = JBScrollPane(inputArea).apply {
                preferredSize = Dimension(400, 100)
            }
            bottomPanel.add(inputScrollPane, BorderLayout.CENTER)
            
            val buttonPanel = JPanel()
            buttonPanel.add(sendButton)
            bottomPanel.add(buttonPanel, BorderLayout.SOUTH)
            
            add(bottomPanel, BorderLayout.SOUTH)
        }
        
        private fun setupListeners() {
            sendButton.addActionListener {
                sendMessage()
            }
            
            inputArea.addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER && e.isControlDown) {
                        sendMessage()
                        e.consume()
                    }
                }
            })
        }
        
        private fun sendMessage() {
            val message = inputArea.text.trim()
            if (message.isEmpty()) return
            
            val settings = ClaudeSettings.getInstance()
            // TODO: Remove API dependency - using Claude CLI instead
            val apiKey = "" // settings.apiKey
            
            if (apiKey.isBlank()) {
                Messages.showErrorDialog(
                    project,
                    "Claude Code integration is now using CLI. Please use the CLI features.",
                    "Using Claude CLI"
                )
                return
            }
            
            appendToChatArea("You: $message\n\n")
            ChatHistoryService.getInstance(project).addMessage("user", message)
            inputArea.text = ""
            sendButton.isEnabled = false
            
            coroutineScope.launch {
                try {
                    val response = ClaudeApiService.getInstance().sendMessage(
                        message = message,
                        apiKey = apiKey,
                        model = "claude-3-sonnet-20240229", // settings.model
                        maxTokens = 4096 // settings.maxTokens
                    )
                    
                    response.fold(
                        onSuccess = { content ->
                            appendToChatArea("Claude: $content\n\n")
                            ChatHistoryService.getInstance(project).addMessage("assistant", content)
                        },
                        onFailure = { error ->
                            appendToChatArea("Error: ${error.message}\n\n")
                            Messages.showErrorDialog(
                                project,
                                "Failed to get response from Claude: ${error.message}",
                                "API Error"
                            )
                        }
                    )
                } finally {
                    sendButton.isEnabled = true
                }
            }
        }
        
        private fun appendToChatArea(text: String) {
            SwingUtilities.invokeLater {
                chatArea.append(text)
                chatArea.caretPosition = chatArea.document.length
            }
        }
        
        fun appendCodeToInput(code: String) {
            SwingUtilities.invokeLater {
                val formattedCode = "```\n$code\n```\n"
                inputArea.text = inputArea.text + formattedCode
                inputArea.requestFocusInWindow()
            }
        }
        
        fun dispose() {
            coroutineScope.cancel()
        }
    }
}