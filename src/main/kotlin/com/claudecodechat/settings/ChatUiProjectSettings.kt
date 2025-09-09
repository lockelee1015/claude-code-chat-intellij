package com.claudecodechat.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service

@Service(Service.Level.PROJECT)
@State(
    name = "com.claudecodechat.settings.ChatUiProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ChatUiProjectSettings(private val project: Project) : PersistentStateComponent<ChatUiProjectSettings.State> {

    data class State(
        var chatInputSplitterProportion: Float = -1f // -1 = not set yet (use default calculation)
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var chatInputSplitterProportion: Float
        get() = state.chatInputSplitterProportion
        set(value) { state.chatInputSplitterProportion = value }

    companion object {
        fun getInstance(project: Project): ChatUiProjectSettings = project.service()
    }
}
