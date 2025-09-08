package com.claudecodechat.toolWindow

import com.claudecodechat.ui.swing.ClaudeChatPanel
import com.claudecodechat.state.SessionViewModel
import com.claudecodechat.session.SessionHistoryLoader
import com.claudecodechat.persistence.SessionPersistence
import com.claudecodechat.models.ClaudeStreamMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
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
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Swing-based tool window factory for Claude Chat
 * Uses stable Swing components for maximum compatibility across IntelliJ versions
 */
class ClaudeChatSimpleToolWindowFactory : ToolWindowFactory, DumbAware {

    init {
        println("=== DEBUG: ClaudeChatSimpleToolWindowFactory initialized ===")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        println("=== DEBUG: Creating tool window content ===")

        // Manually register completion contributor
        println("DEBUG: Manually registering ChatCompletionContributor")
        val completionContributor = com.claudecodechat.completion.ChatCompletionContributor()
        completionContributor.initialize(project)
        println("DEBUG: ChatCompletionContributor registered manually")

        val sessionPersistence = SessionPersistence.getInstance(project)
        
        // Restore saved tabs or show welcome by default
        val savedTabs = sessionPersistence.getOpenTabs()
        if (savedTabs.isNotEmpty()) {
            // Restore saved tabs
            for (tabInfo in savedTabs) {
                if (tabInfo.sessionId != null) {
                    addChatContent(project, toolWindow, tabInfo.title, tabInfo.sessionId)
                } else {
                    addWelcomeContent(project, toolWindow)
                }
            }
            
            // Set the active tab
            val activeTab = savedTabs.find { it.isActive }
            activeTab?.let { tab ->
                val contentManager = toolWindow.contentManager
                for (i in 0 until contentManager.contentCount) {
                    val content = contentManager.getContent(i)
                    val panel = content?.getUserData(CHAT_PANEL_KEY)
                    // For welcome content, check if it's the active one
                    if ((tab.sessionId == null && panel == null) || 
                        (tab.sessionId != null && panel != null)) {
                        content?.let { contentManager.setSelectedContent(it) }
                        break
                    }
                }
            }
        } else {
            // Show welcome by default; user can pick a session or start new
            addWelcomeContent(project, toolWindow)
        }

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




        // Add content manager listener to customize tab rendering and save state
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                // Update tab icons when selection changes
                updateTabIcons(project, toolWindow)
                // Save current tab state
                saveTabState(project, toolWindow)
            }
            
            override fun contentAdded(event: ContentManagerEvent) {
                saveTabState(project, toolWindow)
            }
            
            override fun contentRemoved(event: ContentManagerEvent) {
                // Clean up message observer
                val messageObserver = event.content?.getUserData(MESSAGE_OBSERVER_KEY)
                messageObserver?.cancel()
                
                saveTabState(project, toolWindow)
                
                // If last tab was removed, show welcome page
                if (toolWindow.contentManager.contentCount == 0) {
                    addWelcomeContent(project, toolWindow)
                }
            }
        })
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
    
    companion object {
        private val CHAT_PANEL_KEY = Key.create<ClaudeChatPanel>("CHAT_PANEL")
        private val SESSION_ID_KEY = Key.create<String>("SESSION_ID")
        private val MESSAGE_OBSERVER_KEY = Key.create<Job>("MESSAGE_OBSERVER")
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

    private fun extractFirstUserMessageText(message: ClaudeStreamMessage): String {
        return message.message?.content?.firstOrNull()?.text ?: ""
    }

    private fun createTitleSummary(text: String, maxWords: Int = 3): String {
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return if (words.size <= maxWords) {
            words.joinToString(" ")
        } else {
            words.take(maxWords).joinToString(" ") + "…"
        }
    }

    private fun addChatContent(project: Project, toolWindow: ToolWindow, title: String, sessionId: String?): ClaudeChatPanel {
        val chatPanel = ClaudeChatPanel(project)
        val displayTitle = shortenTitle(title, 5) // Use shortened title
        val content = ContentFactory.getInstance().createContent(chatPanel, displayTitle, false)
        content.putUserData(CHAT_PANEL_KEY, chatPanel)
        sessionId?.let { content.putUserData(SESSION_ID_KEY, it) }
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        // Observe messages to update tab title with first user message
        val messageObserver = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            SessionViewModel.getInstance(project).messages.collect { messages ->
                val firstUserMessage = messages.firstOrNull { 
                    it.type.name == "USER" && it.message?.content?.isNotEmpty() == true
                }
                firstUserMessage?.let { message ->
                    val userText = extractFirstUserMessageText(message)
                    if (userText.isNotBlank()) {
                        val summary = createTitleSummary(userText)
                        content.displayName = summary
                        // Save updated tab state
                        saveTabState(project, toolWindow)
                    }
                }
            }
        }
        content.putUserData(MESSAGE_OBSERVER_KEY, messageObserver)

        // If a session was chosen, resume it
        if (sessionId != null) {
            SessionViewModel.getInstance(project).resumeSession(sessionId)
        } else {
            SessionViewModel.getInstance(project).startNewSession()
        }
        return chatPanel
    }

    private fun addWelcomeContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(java.awt.BorderLayout()).apply {
            background = JBColor.background() // 设置主面板背景色以适应主题
        }
        
        // Welcome message
        val welcomePanel = JPanel().apply {
            background = JBColor.background() // 设置欢迎面板背景色
        }
        welcomePanel.layout = BoxLayout(welcomePanel, BoxLayout.Y_AXIS)
        welcomePanel.border = JBUI.Borders.empty(20, 20, 20, 20)
        
        val title = JLabel("Welcome to Claude Chat")
        title.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 14)
        title.alignmentX = Component.CENTER_ALIGNMENT
        
        val subtitle = JLabel("Start chatting or browse session history using the buttons above")
        subtitle.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
        subtitle.foreground = JBColor.GRAY
        subtitle.alignmentX = Component.CENTER_ALIGNMENT
        
        welcomePanel.add(title)
        welcomePanel.add(Box.createVerticalStrut(10))
        welcomePanel.add(subtitle)
        welcomePanel.add(Box.createVerticalStrut(20))
        
        // Feature tips
        val tipsPanel = JPanel().apply {
            background = JBColor.background() // 设置提示面板背景色
        }
        tipsPanel.layout = BoxLayout(tipsPanel, BoxLayout.Y_AXIS)
        tipsPanel.alignmentX = Component.CENTER_ALIGNMENT
        
        val tip1 = JLabel("💡 Use slash commands: /help, /clear, /cost, /review, /init")
        tip1.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11)
        tip1.foreground = JBColor.GRAY
        tip1.alignmentX = Component.CENTER_ALIGNMENT

        val tip2 = JLabel("💡 Reference files: @filename to include file content in your prompt")
        tip2.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11)
        tip2.foreground = JBColor.GRAY
        tip2.alignmentX = Component.CENTER_ALIGNMENT

        tipsPanel.add(tip1)
        tipsPanel.add(Box.createVerticalStrut(4))
        tipsPanel.add(tip2)
        welcomePanel.add(tipsPanel)
        
        // Chat input at the bottom
        lateinit var welcomeContent: com.intellij.ui.content.Content
        
        val inputBar = com.claudecodechat.ui.swing.ChatInputBar(
            project = project,
            onSend = { text, model, planMode ->
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
            },
            onStop = {
                com.claudecodechat.state.SessionViewModel.getInstance(project).stopCurrentRequest()
            }
        )
        val inputPanel = JPanel(java.awt.BorderLayout()).apply {
            background = JBColor.background() // 设置输入面板背景色
            border = com.intellij.util.ui.JBUI.Borders.empty(0, 10, 0, 10)
            add(inputBar, java.awt.BorderLayout.CENTER)
        }

        panel.add(welcomePanel, java.awt.BorderLayout.CENTER)
        panel.add(inputPanel, java.awt.BorderLayout.SOUTH)

        welcomeContent = ContentFactory.getInstance().createContent(panel, "Home", false)
        welcomeContent.isCloseable = true
        toolWindow.contentManager.addContent(welcomeContent)
        toolWindow.contentManager.setSelectedContent(welcomeContent)
        
        // Observe loading state for the welcome input bar
        val sessionViewModel = SessionViewModel.getInstance(project)
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            sessionViewModel.isLoading.collect { isLoading ->
                if (!isLoading) {
                    inputBar.hideLoading()
                }
            }
        }
    }

    private fun showHistoryPopup(project: Project, toolWindow: ToolWindow, anchor: Component) {
        println("DEBUG: showHistoryPopup called")
        val basePath = project.basePath
        println("DEBUG: basePath = $basePath")
        val sessions = if (basePath != null) {
            try {
                val sessionList = SessionHistoryLoader().getRecentSessionsWithDetails(basePath, 15)
                    .sortedByDescending { it.lastModified }
                println("DEBUG: Found ${sessionList.size} sessions")
                sessionList
            } catch (e: Exception) {
                println("DEBUG: Exception loading sessions: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
        
        if (sessions.isEmpty()) {
            println("DEBUG: No sessions found, showing empty message")
            val popup = JBPopupFactory.getInstance()
                .createMessage("No session history available")
            popup.showUnderneathOf(anchor)
            return
        }

        // Create custom list with detailed layout
        val listModel = DefaultListModel<SessionHistoryLoader.SessionInfo>()
        sessions.forEach { listModel.addElement(it) }
        
        val list = JList(listModel).apply {
            cellRenderer = SessionHistoryRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = Math.min(sessions.size, 8) // Max 8 visible rows
        }
        
        val scrollPane = JScrollPane(list).apply {
            preferredSize = Dimension(500, Math.min(sessions.size * 60 + 10, 480))
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, list)
            .setTitle("Session History")
            .setRequestFocus(true)
            .setFocusable(true)
            .setResizable(true)
            .setMovable(false)
            .createPopup()
            
        // Handle selection
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedSession = list.selectedValue
                    if (selectedSession != null) {
                        popup.closeOk(null)
                        val title = extractTitleFromSession(selectedSession)
                        addChatContent(project, toolWindow, title, selectedSession.id)
                    }
                }
            }
        })
        
        list.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) {
                    val selectedSession = list.selectedValue
                    if (selectedSession != null) {
                        popup.closeOk(null)
                        val title = extractTitleFromSession(selectedSession)
                        addChatContent(project, toolWindow, title, selectedSession.id)
                    }
                }
            }
        })
        
        // Position popup to not exceed window bounds
        val toolWindowComponent = toolWindow.component
        val toolWindowBounds = toolWindowComponent.bounds
        val screenBounds = toolWindowComponent.graphicsConfiguration.bounds
        
        // Calculate popup position
        val popupWidth = 500
        val popupHeight = Math.min(sessions.size * 60 + 50, 500)
        
        val anchorLocation = anchor.locationOnScreen
        var x = anchorLocation.x
        var y = anchorLocation.y + anchor.height
        
        // Ensure popup doesn't exceed right edge
        if (x + popupWidth > screenBounds.x + screenBounds.width) {
            x = screenBounds.x + screenBounds.width - popupWidth - 10
        }
        
        // Ensure popup doesn't exceed bottom edge  
        if (y + popupHeight > screenBounds.y + screenBounds.height) {
            y = anchorLocation.y - popupHeight
        }
        
        println("DEBUG: Showing popup underneath anchor")
        popup.showUnderneathOf(anchor)
    }
    
    private fun extractTitleFromSession(session: SessionHistoryLoader.SessionInfo): String {
        val preview = session.preview?.replace('\n', ' ')?.trim()
        return when {
            !preview.isNullOrBlank() -> {
                // Take first few words as title
                preview.split(" ").take(3).joinToString(" ")
            }
            else -> session.id.take(8)
        }
    }
    
    // Custom cell renderer for session history
    private class SessionHistoryRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val session = value as? SessionHistoryLoader.SessionInfo
            if (session == null) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            }
            
            val panel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 12, 8, 12)
                background = if (isSelected) list?.selectionBackground ?: JBColor.BLUE else list?.background ?: JBColor.WHITE
            }
            
            // Left: Time
            val timeStr = formatTime(session.lastModified)
            val timeLabel = JLabel(timeStr).apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                foreground = if (isSelected) list?.selectionForeground ?: JBColor.WHITE else JBColor.GRAY
                preferredSize = Dimension(80, preferredSize.height)
            }
            
            // Center: Summary
            val preview = session.preview?.replace('\n', ' ')?.trim() ?: "Empty session"
            val summaryLabel = JLabel(if (preview.length > 35) preview.take(35) + "..." else preview).apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                foreground = if (isSelected) list?.selectionForeground ?: JBColor.WHITE else list?.foreground ?: JBColor.BLACK
            }
            
            // Right: Message count
            val countLabel = JLabel("${session.messageCount}").apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                foreground = if (isSelected) list?.selectionForeground ?: JBColor.WHITE else JBColor.GRAY
                horizontalAlignment = SwingConstants.RIGHT
                preferredSize = Dimension(30, preferredSize.height)
            }
            
            panel.add(timeLabel, BorderLayout.WEST)
            panel.add(summaryLabel, BorderLayout.CENTER)
            panel.add(countLabel, BorderLayout.EAST)
            
            return panel
        }
        
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60 * 1000 -> "刚刚"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
                else -> {
                    val date = java.text.SimpleDateFormat("MM-dd").format(java.util.Date(timestamp))
                    date
                }
            }
        }
    }
    
    private fun saveTabState(project: Project, toolWindow: ToolWindow) {
        val sessionPersistence = SessionPersistence.getInstance(project)
        val contentManager = toolWindow.contentManager
        val tabs = mutableListOf<SessionPersistence.TabInfo>()
        
        for (i in 0 until contentManager.contentCount) {
            val content = contentManager.getContent(i)
            if (content != null) {
                val sessionId = content.getUserData(SESSION_ID_KEY)
                val title = content.displayName ?: "Untitled"
                val isActive = contentManager.selectedContent == content
                tabs.add(SessionPersistence.TabInfo(sessionId, title, isActive))
            }
        }
        
        sessionPersistence.saveOpenTabs(tabs)
    }


}