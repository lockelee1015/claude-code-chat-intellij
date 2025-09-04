package com.claudecodechat.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.claudecodechat.settings.ClaudeSettings",
    storages = [Storage("ClaudeCodeChatSettings.xml")]
)
class ClaudeSettings : PersistentStateComponent<ClaudeSettings> {
    var claudePath: String = ""
    var environmentVariables: String = ""
    var markdownFontSize: Int = 11  // Default markdown font size
    var debugMode: Boolean = false  // Debug mode for tool development
    var maxMessagesPerSession: Int = 100  // Maximum messages to display per session
    
    companion object {
        fun getInstance(): ClaudeSettings {
            return ApplicationManager.getApplication()
                .getService(ClaudeSettings::class.java) ?: ClaudeSettings()
        }
    }
    
    override fun getState(): ClaudeSettings {
        return this
    }
    
    override fun loadState(state: ClaudeSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}