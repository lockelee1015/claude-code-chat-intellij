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
        var autoResumeLastSession: Boolean = true
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
}