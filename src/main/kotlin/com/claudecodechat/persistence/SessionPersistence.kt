package com.claudecodechat.persistence

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent storage for session data
 */
@State(
    name = "ClaudeCodeChatSessions",
    storages = [Storage("claude-code-chat.xml")]
)
@Service(Service.Level.PROJECT)
class SessionPersistence : PersistentStateComponent<SessionPersistence.State> {
    
    companion object {
        fun getInstance(project: Project): SessionPersistence = project.service()
    }
    
    data class State(
        // Deprecated: last CLI session id (kept for backward compatibility)
        var lastSessionId: String? = null,
        var recentSessionIds: MutableList<String> = mutableListOf(),
        var autoResumeLastSession: Boolean = true,
        var openTabs: MutableList<TabInfo> = mutableListOf(),
        // New: logical sessions that group multiple CLI session IDs
        var logicalSessions: MutableList<LogicalSession> = mutableListOf(),
        var lastLogicalSessionId: String? = null,
        // key: logicalSessionId -> selected MCP server names
        var mcpSelections: MutableMap<String, MutableList<String>> = mutableMapOf()
    )
    
    data class TabInfo(
        var sessionId: String? = null,
        var title: String = "",
        var isActive: Boolean = false
    )

    data class LogicalSession(
        var id: String = java.util.UUID.randomUUID().toString(),
        var cliSessionIds: MutableList<String> = mutableListOf(),
        var title: String = "",
        var preview: String = "",
        var lastModified: Long = System.currentTimeMillis(),
        var messageCount: Int = 0
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }
    
    fun getLastSessionId(): String? = myState.lastSessionId
    
    fun setLastSessionId(sessionId: String?) {
        myState.lastSessionId = sessionId
        sessionId?.let {
            if (!myState.recentSessionIds.contains(it)) {
                myState.recentSessionIds.add(0, it)
                // Keep only last 10 sessions
                if (myState.recentSessionIds.size > 10) {
                    myState.recentSessionIds = myState.recentSessionIds.take(10).toMutableList()
                }
            }
        }
    }
    
    fun getRecentSessionIds(): List<String> = myState.recentSessionIds.toList()
    
    fun shouldAutoResume(): Boolean = myState.autoResumeLastSession
    
    fun setAutoResume(value: Boolean) {
        myState.autoResumeLastSession = value
    }
    
    // Tab management methods
    fun saveOpenTabs(tabs: List<TabInfo>) {
        myState.openTabs.clear()
        myState.openTabs.addAll(tabs)
    }
    
    fun getOpenTabs(): List<TabInfo> = myState.openTabs.toList()
    
    fun addTab(sessionId: String?, title: String, isActive: Boolean = false) {
        // Mark all other tabs as inactive if this one is active
        if (isActive) {
            myState.openTabs.forEach { it.isActive = false }
        }
        myState.openTabs.add(TabInfo(sessionId, title, isActive))
    }
    
    fun removeTab(sessionId: String?) {
        myState.openTabs.removeAll { it.sessionId == sessionId }
    }
    
    fun setActiveTab(sessionId: String?) {
        myState.openTabs.forEach { it.isActive = (it.sessionId == sessionId) }
    }

    // --- Logical sessions API ---
    fun createLogicalSession(initialTitle: String = ""): String {
        val ls = LogicalSession()
        ls.title = initialTitle
        myState.logicalSessions.add(0, ls)
        myState.lastLogicalSessionId = ls.id
        return ls.id
    }

    fun getLastLogicalSessionId(): String? = myState.lastLogicalSessionId

    fun setLastLogicalSessionId(id: String?) {
        myState.lastLogicalSessionId = id
    }

    fun appendCliSession(logicalId: String, cliId: String) {
        val ls = myState.logicalSessions.find { it.id == logicalId } ?: return
        if (!ls.cliSessionIds.contains(cliId)) {
            ls.cliSessionIds.add(cliId)
        }
        ls.lastModified = System.currentTimeMillis()
        // maintain MRU ordering: move to front
        myState.logicalSessions.removeIf { it.id == logicalId }
        myState.logicalSessions.add(0, ls)
        myState.lastLogicalSessionId = logicalId
    }

    fun updatePreviewAndCount(logicalId: String, newPreviewIfEmpty: String?, incrementCountBy: Int = 0) {
        val ls = myState.logicalSessions.find { it.id == logicalId } ?: return
        if (ls.preview.isBlank() && !newPreviewIfEmpty.isNullOrBlank()) {
            ls.preview = newPreviewIfEmpty
        }
        if (incrementCountBy != 0) ls.messageCount = (ls.messageCount + incrementCountBy).coerceAtLeast(0)
        ls.lastModified = System.currentTimeMillis()
    }

    fun getLogicalSessions(limit: Int? = null): List<LogicalSession> {
        val list = myState.logicalSessions.toList()
        return if (limit != null) list.take(limit) else list
    }

    fun getLogicalSession(logicalId: String): LogicalSession? = myState.logicalSessions.find { it.id == logicalId }

    // --- MCP selections ---
    fun getSelectedMcpServers(logicalId: String): List<String> {
        return myState.mcpSelections[logicalId]?.toList() ?: emptyList()
    }

    fun setSelectedMcpServers(logicalId: String, servers: List<String>) {
        myState.mcpSelections[logicalId] = servers.distinct().toMutableList()
    }
}
