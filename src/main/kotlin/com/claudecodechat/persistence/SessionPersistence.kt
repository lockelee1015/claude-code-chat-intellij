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
        var lastSessionId: String? = null,
        var recentSessionIds: MutableList<String> = mutableListOf(),
        var autoResumeLastSession: Boolean = true,
        var openTabs: MutableList<TabInfo> = mutableListOf()
    )
    
    data class TabInfo(
        var sessionId: String? = null,
        var title: String = "",
        var isActive: Boolean = false
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
}