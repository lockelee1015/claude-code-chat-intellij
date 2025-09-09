package com.claudecodechat.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents a message in the Claude Code CLI JSONL stream
 */
@Serializable
data class ClaudeStreamMessage(
    val type: MessageType,
    val subtype: String? = null,
    val message: Message? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("project_id")
    val projectId: String? = null,
    val timestamp: String? = null,
    val error: ErrorInfo? = null,
    @SerialName("checkpoint_data")
    val checkpointData: JsonElement? = null,
    @SerialName("isMeta")
    val isMeta: Boolean = false,
    @SerialName("leaf_uuid")
    val leafUuid: String? = null,
    val summary: String? = null
)

@Serializable
enum class MessageType {
    @SerialName("system")
    SYSTEM,
    @SerialName("assistant")
    ASSISTANT,
    @SerialName("user")
    USER,
    @SerialName("result")
    RESULT,
    @SerialName("error")
    ERROR,
    @SerialName("meta")
    META
}

@Serializable
data class Message(
    val role: String? = null,
    val content: List<Content> = emptyList(),
    val usage: Usage? = null,
    val id: String? = null,
    val model: String? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    @SerialName("stop_sequence")
    val stopSequence: String? = null
)

@Serializable
data class Content(
    val type: ContentType,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
    @SerialName("tool_use_id")
    val toolUseId: String? = null,
    val content: String? = null,
    @SerialName("is_error")
    val isError: Boolean? = null
)

@Serializable
enum class ContentType {
    @SerialName("text")
    TEXT,
    @SerialName("tool_use")
    TOOL_USE,
    @SerialName("tool_result")
    TOOL_RESULT
}

@Serializable
data class Usage(
    @SerialName("input_tokens")
    val inputTokens: Int = 0,
    @SerialName("output_tokens")
    val outputTokens: Int = 0,
    @SerialName("cache_creation_input_tokens")
    val cacheCreationInputTokens: Int? = null,
    @SerialName("cache_read_input_tokens")
    val cacheReadInputTokens: Int? = null
)

@Serializable
data class ErrorInfo(
    val type: String,
    val message: String,
    val code: String? = null
)

/**
 * Session metrics tracking
 */
data class SessionMetrics(
    var firstMessageTime: Long? = null,
    var promptsSent: Int = 0,
    var toolsExecuted: Int = 0,
    var toolsFailed: Int = 0,
    var filesCreated: Int = 0,
    var filesModified: Int = 0,
    var filesDeleted: Int = 0,
    var mcpCalls: Int = 0,
    var codeBlocksGenerated: Int = 0,
    var errorsEncountered: Int = 0,
    var checkpointCount: Int = 0,
    var wasResumed: Boolean = false,
    val modelChanges: MutableList<String> = mutableListOf(),
    var totalInputTokens: Int = 0,
    var totalOutputTokens: Int = 0,
    var cacheReadInputTokens: Int = 0,
    var cacheCreationInputTokens: Int = 0
)
