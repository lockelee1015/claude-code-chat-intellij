package com.claudecodechat.hooks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Hook configuration for controlling Claude Code CLI behavior
 */
@Serializable
data class HooksConfiguration(
    val PreToolUse: List<HookMatcher>? = null,
    val PostToolUse: List<HookMatcher>? = null,
    val Notification: List<HookCommand>? = null,
    val Stop: List<HookCommand>? = null,
    val SubagentStop: List<HookCommand>? = null
)

@Serializable
data class HookMatcher(
    val matcher: String? = null,  // Tool name pattern (supports regex)
    val hooks: List<HookCommand> = emptyList()
)

@Serializable
data class HookCommand(
    val type: String = "command",  // Currently only "command" is supported
    val command: String,           // Shell command to execute
    val timeout: Int? = null      // Timeout in seconds
)

/**
 * Hook configuration levels
 */
enum class HookLevel {
    LOCAL,    // Project-specific (.claude/.hooks.json)
    PROJECT,  // Project configuration (CLAUDE.project.json)
    USER      // User-global (~/.claude/settings.json)
}

/**
 * Hook execution context
 */
data class HookContext(
    val toolName: String,
    val toolInput: JsonElement?,
    val projectPath: String,
    val sessionId: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Hook execution result
 */
data class HookResult(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val exitCode: Int? = null,
    val shouldBlock: Boolean = false  // If true, the tool execution should be blocked
)