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
    var markdownFontSize: Int = 10  // Default markdown font size
    var debugMode: Boolean = false  // Debug mode for tool development
    var maxMessagesPerSession: Int = 100  // Maximum messages to display per session
    var useEnhancedCodeBlocks: Boolean = false  // Use IntelliJ editor for code block rendering
    var showCodeBlockLineNumbers: Boolean = true  // Show line numbers in code blocks
    var maxCodeBlockHeight: Int = 300  // Maximum height for code blocks in pixels
    var syncWithEditorFont: Boolean = false  // Sync markdown fonts with editor fonts
    var markdownLineSpacing: Float = 1.4f  // Line spacing multiplier for markdown content
    var chatInputSplitterProportion: Float = 0.80f  // Splitter proportion for messages vs input
    // Image input mode: "path" (attachments + @ref) or "base64" (stream-json stdin)
    var imageInputMode: String = "path"
    
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
