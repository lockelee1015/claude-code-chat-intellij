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
    var apiKey: String = ""
    var model: String = "claude-3-sonnet-20240229"
    var maxTokens: Int = 4096
    var temperature: Double = 0.7
    
    companion object {
        fun getInstance(): ClaudeSettings {
            return ApplicationManager.getApplication()
                .getService(ClaudeSettings::class.java)
        }
    }
    
    override fun getState(): ClaudeSettings {
        return this
    }
    
    override fun loadState(state: ClaudeSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}