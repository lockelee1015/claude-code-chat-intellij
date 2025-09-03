package com.claudecodechat.toolWindow

import com.claudecodechat.ui.swing.ClaudeChatPanel
import com.claudecodechat.state.SessionViewModel
import com.claudecodechat.session.SessionHistoryLoader
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.Component
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Swing-based tool window factory for Claude Chat
 * Uses stable Swing components for maximum compatibility across IntelliJ versions
 */
class ClaudeChatSimpleToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Show welcome by default; user can pick a session or start new
        addWelcomeContent(project, toolWindow)

        // Add title actions: plus button for new session and history button for session history
        val newSessionAction: AnAction = object : DumbAwareAction("New Session", "Start new session", AllIcons.General.Add) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                // Directly create new session
                addChatContent(project, toolWindow, "New", null)
            }
        }
        
        val historyAction: AnAction = object : DumbAwareAction("Session History", "Browse session history", AllIcons.General.History) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                // Show history popup near the button
                val component = e.inputEvent?.component ?: toolWindow.component
                showHistoryPopup(project, toolWindow, component)
            }
        }
        
        (toolWindow as? ToolWindowEx)?.setTitleActions(listOf(newSessionAction, historyAction))




        // Add content manager listener to customize tab rendering
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                // Update tab icons when selection changes
                updateTabIcons(project, toolWindow)
            }
        })
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
    
    companion object {
        private val CHAT_PANEL_KEY = Key.create<ClaudeChatPanel>("CHAT_PANEL")
    }

    private fun updateTabIcons(project: Project, toolWindow: ToolWindow) {
        // Update tab titles to show short summaries
        val contentManager = toolWindow.contentManager
        for (i in 0 until contentManager.contentCount) {
            val content = contentManager.getContent(i)
            if (content != null) {
                val panel = content.getUserData(CHAT_PANEL_KEY)
                if (panel != null) {
                    // This is a chat panel - keep existing short title or generate one
                    if (content.displayName == "💬" || content.displayName?.length ?: 0 > 5) {
                        content.displayName = "Chat"
                    }
                } else {
                    // This might be welcome content
                    if (content.displayName == "🏠" || content.displayName?.contains("Welcome") == true) {
                        content.displayName = "Home"
                    }
                }
            }
        }
    }

    private fun shortenTitle(raw: String, maxChars: Int = 5): String {
        val t = raw.trim()
        return if (t.length <= maxChars) t else t.substring(0, maxChars) + "…"
    }

    private fun addChatContent(project: Project, toolWindow: ToolWindow, title: String, sessionId: String?): ClaudeChatPanel {
        val chatPanel = ClaudeChatPanel(project)
        val displayTitle = shortenTitle(title, 5) // Use shortened title
        val content = ContentFactory.getInstance().createContent(chatPanel, displayTitle, false)
        content.putUserData(CHAT_PANEL_KEY, chatPanel)
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        // If a session was chosen, resume it
        if (sessionId != null) {
            SessionViewModel.getInstance(project).resumeSession(sessionId)
        } else {
            SessionViewModel.getInstance(project).startNewSession()
        }
        return chatPanel
    }

    private fun addWelcomeContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(java.awt.BorderLayout())
        
        // Welcome message
        val welcomePanel = JPanel()
        welcomePanel.layout = BoxLayout(welcomePanel, BoxLayout.Y_AXIS)
        welcomePanel.border = JBUI.Borders.empty(20, 20, 20, 20)
        
        val title = JLabel("Welcome to Claude Chat")
        title.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 16)
        title.alignmentX = Component.CENTER_ALIGNMENT
        
        val subtitle = JLabel("Start chatting or browse session history using the buttons above")
        subtitle.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
        subtitle.foreground = JBColor.GRAY
        subtitle.alignmentX = Component.CENTER_ALIGNMENT
        
        welcomePanel.add(title)
        welcomePanel.add(Box.createVerticalStrut(10))
        welcomePanel.add(subtitle)
        
        // Chat input at the bottom
        lateinit var welcomeContent: com.intellij.ui.content.Content
        
        val inputBar = com.claudecodechat.ui.swing.ChatInputBar(project) { text, model ->
            // When sending from welcome, transition into a new chat tab
            toolWindow.contentManager.removeContent(welcomeContent, true)
            // Use first few words of the input as title
            val title = if (text.isNotBlank()) {
                text.split(" ").take(2).joinToString(" ")
            } else {
                "New"
            }
            val chat = addChatContent(project, toolWindow, title = title, sessionId = null)
            if (text.isNotBlank()) {
                chat.appendCodeToInput(text)
            }
        }
        val inputPanel = JPanel(java.awt.BorderLayout()).apply {
            border = com.intellij.util.ui.JBUI.Borders.empty(10, 20, 20, 20)
            add(inputBar, java.awt.BorderLayout.CENTER)
        }

        panel.add(welcomePanel, java.awt.BorderLayout.CENTER)
        panel.add(inputPanel, java.awt.BorderLayout.SOUTH)

        welcomeContent = ContentFactory.getInstance().createContent(panel, "Home", false)
        welcomeContent.isCloseable = true
        toolWindow.contentManager.addContent(welcomeContent)
        toolWindow.contentManager.setSelectedContent(welcomeContent)
    }

    private fun showHistoryPopup(project: Project, toolWindow: ToolWindow, anchor: Component) {
        val items = mutableListOf<Pair<String, String>>()
        val basePath = project.basePath
        if (basePath != null) {
            try {
                val recent = SessionHistoryLoader().getRecentSessionsWithDetails(basePath, 15)
                    .sortedByDescending { it.lastModified }
                recent.forEach { s ->
                    val preview = s.preview?.replace('\n', ' ')
                    val label = if (!preview.isNullOrBlank()) "${s.id.take(8)}  ·  ${preview.take(60)}" else s.id.take(8)
                    items.add(label to s.id)
                }
            } catch (_: Exception) {}
        }
        
        if (items.isEmpty()) {
            // Show a simple message if no history available
            val popup = JBPopupFactory.getInstance()
                .createMessage("No session history available")
            popup.showUnderneathOf(anchor)
            return
        }

        val list = JList(items.map { it.first }.toTypedArray())
        val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
            .setTitle("Session History")
            .setItemChoosenCallback {
                val idx = list.selectedIndex
                if (idx >= 0 && idx < items.size) {
                    val (label, sid) = items[idx]
                    // Extract meaningful title from label
                    val parts = label.split("·").map { it.trim() }
                    val title = when {
                        parts.size >= 2 && parts[1].isNotBlank() -> parts[1] // Use preview text
                        else -> sid.take(8) // Use session ID
                    }
                    addChatContent(project, toolWindow, title, sid)
                }
            }
            .createPopup()
        popup.showUnderneathOf(anchor)
    }


}