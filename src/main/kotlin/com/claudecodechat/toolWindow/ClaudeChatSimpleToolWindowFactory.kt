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
        val raw = message.message?.content?.firstOrNull()?.text ?: return ""
        return stripIdeContext(raw).trim()
    }

    private fun stripIdeContext(text: String): String {
        // Remove a leading or trailing <ide_context ...><file .../></ide_context> block
        val regex = Regex(
            """(?s)^\n*<ide_context[^>]*>\s*<file[^>]*/>\s*</ide_context>\n*|\n*<ide_context[^>]*>\s*<file[^>]*/>\s*</ide_context>\n*$""",
            RegexOption.IGNORE_CASE
        )
        return text.replace(regex, "").trim()
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

        // If a session was chosen, resume logical session
        if (sessionId != null) {
            SessionViewModel.getInstance(project).resumeLogicalSession(sessionId)
        } else {
            SessionViewModel.getInstance(project).startNewSession()
        }
        return chatPanel
    }

    private fun addWelcomeContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout()).apply {
            background = JBColor.background() // 设置主面板背景色以适应主题
        }
        
        // Welcome message
        val welcomePanel = JPanel().apply {
            background = JBColor.background() // 设置欢迎面板背景色
        }
        welcomePanel.layout = BoxLayout(welcomePanel, BoxLayout.Y_AXIS)
        welcomePanel.border = JBUI.Borders.empty(20, 20, 20, 20)
        
        val title = JLabel("Welcome to Claude Chat")
        title.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
        title.alignmentX = Component.CENTER_ALIGNMENT
        
        val subtitle = JLabel("Start chatting or browse session history using the buttons above")
        subtitle.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
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
        tip1.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        tip1.foreground = JBColor.GRAY
        tip1.alignmentX = Component.CENTER_ALIGNMENT

        val tip2 = JLabel("💡 Reference files: @filename to include file content in your prompt")
        tip2.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
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
                // When sending from Home, replace Home with a new chat tab and immediately send
                val cm = toolWindow.contentManager
                val index = cm.getIndexOfContent(welcomeContent).takeIf { it >= 0 } ?: cm.contentCount
                val safeText = stripIdeContext(text).trim()

                // Create the chat content first at the same index to avoid toolwindow collapsing
                val tempPanel = ClaudeChatPanel(project)
                val displayTitle = shortenTitle(if (safeText.isNotBlank()) safeText else "New", 5)
                val chatContent = com.intellij.ui.content.ContentFactory.getInstance().createContent(tempPanel, displayTitle, false)
                chatContent.putUserData(CHAT_PANEL_KEY, tempPanel)
                chatContent.isCloseable = true
                cm.addContent(chatContent, index)
                cm.setSelectedContent(chatContent)

                // Remove the Home content after new chat is present
                cm.removeContent(welcomeContent, true)

                if (safeText.isNotBlank()) {
                    // Explicitly begin a fresh session (do not continue previous)
                    SessionViewModel.getInstance(project).startNewSession()
                    tempPanel.sendInitialMessage(safeText, model, planMode)
                }
            },
            onStop = {
                SessionViewModel.getInstance(project).stopCurrentRequest()
            }
        )
        val inputPanel = JPanel(BorderLayout()).apply {
            background = JBColor.background() // 设置输入面板背景色
            border = JBUI.Borders.empty(0, 10, 0, 10)
            add(inputBar, BorderLayout.CENTER)
        }

        panel.add(welcomePanel, BorderLayout.CENTER)
        panel.add(inputPanel, BorderLayout.SOUTH)

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
        val sessionList = SessionViewModel.getInstance(project).getRecentSessionsWithDetails()
        
        if (sessionList.isEmpty()) {
            println("DEBUG: No sessions found, showing empty message")
            val popup = JBPopupFactory.getInstance()
                .createMessage("No session history available")
            popup.showUnderneathOf(anchor)
            return
        }

        // Create custom list with detailed layout
        data class LogicalSessionItem(val id: String, val preview: String, val lastModified: Long, val messageCount: Int)
        val listModel = DefaultListModel<LogicalSessionItem>()
        sessionList.forEach { d -> listModel.addElement(LogicalSessionItem(d.id, d.preview, System.currentTimeMillis(), d.messageCount)) }

        val list = JList(listModel).apply {
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                    val item = value as? LogicalSessionItem
                    if (item == null) return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    val panel = JPanel(BorderLayout()).apply {
                        border = JBUI.Borders.empty(8, 12, 8, 12)
                        background = if (isSelected) list?.selectionBackground ?: JBColor.BLUE else list?.background ?: JBColor.WHITE
                    }
                    val summary = JLabel(if (item.preview.length > 35) item.preview.take(35) + "..." else item.preview).apply {
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        foreground = if (isSelected) list?.selectionForeground ?: JBColor.WHITE else list?.foreground ?: JBColor.BLACK
                    }
                    val right = JLabel(item.messageCount.toString()).apply {
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                        foreground = if (isSelected) list?.selectionForeground ?: JBColor.WHITE else JBColor.GRAY
                        horizontalAlignment = RIGHT
                        preferredSize = Dimension(30, preferredSize.height)
                    }
                    panel.add(summary, BorderLayout.CENTER)
                    panel.add(right, BorderLayout.EAST)
                    return panel
                }
            }
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = Math.min(sessionList.size, 8) // Max 8 visible rows
        }
        
        val scrollPane = JScrollPane(list).apply {
            preferredSize = Dimension(500, Math.min(sessionList.size * 60 + 10, 480))
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
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedSession = list.selectedValue as? LogicalSessionItem
                    if (selectedSession != null) {
                        popup.closeOk(null)
                        val title = selectedSession.preview.ifBlank { selectedSession.id.take(8) }
                        addChatContent(project, toolWindow, title, selectedSession.id)
                    }
                }
            }
        })
        
        list.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) {
                    val selectedSession = list.selectedValue as? LogicalSessionItem
                    if (selectedSession != null) {
                        popup.closeOk(null)
                        val title = selectedSession.preview.ifBlank { selectedSession.id.take(8) }
                        addChatContent(project, toolWindow, title, selectedSession.id)
                    }
                }
            }
        })
        
        // Position popup to not exceed window bounds
        val toolWindowComponent = toolWindow.component
        toolWindowComponent.bounds
        val screenBounds = toolWindowComponent.graphicsConfiguration.bounds
        
        // Calculate popup position
        val popupWidth = 500
        val popupHeight = Math.min(sessionList.size * 60 + 50, 500)
        
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
