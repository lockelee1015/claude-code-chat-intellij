package com.claudecodechat.completion

/**
 * Data models for the completion system
 */

/**
 * Represents a completion item that can be suggested to the user
 */
sealed class CompletionItem {
    /**
     * A slash command completion
     */
    data class SlashCommand(
        val name: String,
        val description: String,
        val argumentHint: String? = null,
        val source: CompletionSource = CompletionSource.BUILT_IN,
        val icon: String? = null
    ) : CompletionItem() {
        // Full command with slash prefix
        val fullCommand: String get() = "/$name"
    }
    
    /**
     * A file reference completion (for @ mentions)
     */
    data class FileReference(
        val path: String,
        val fileName: String,
        val relativePath: String,
        val fileType: String? = null,
        val icon: String? = null
    ) : CompletionItem() {
        // Full reference with @ prefix
        val fullReference: String get() = "@$relativePath"
    }
}

/**
 * Source of a slash command
 */
enum class CompletionSource {
    BUILT_IN,   // Built-in Claude commands
    PROJECT,    // Project-specific commands (.claude/commands/)
    USER,       // User-level commands (~/.claude/commands/)
    MCP         // MCP server commands
}

/**
 * State of the completion system
 */
data class CompletionState(
    val isShowing: Boolean = false,
    val items: List<CompletionItem> = emptyList(),
    val selectedIndex: Int = 0,
    val trigger: CompletionTrigger? = null,
    val query: String = "",
    val triggerPosition: Int = 0
) {
    // Get currently selected item
    val selectedItem: CompletionItem?
        get() = items.getOrNull(selectedIndex)
    
    // Check if there are items to show
    val hasItems: Boolean
        get() = items.isNotEmpty()
    
    // Navigate up in the list
    fun selectPrevious(): CompletionState {
        if (!hasItems) return this
        val newIndex = if (selectedIndex > 0) selectedIndex - 1 else items.size - 1
        return copy(selectedIndex = newIndex)
    }
    
    // Navigate down in the list
    fun selectNext(): CompletionState {
        if (!hasItems) return this
        val newIndex = if (selectedIndex < items.size - 1) selectedIndex + 1 else 0
        return copy(selectedIndex = newIndex)
    }
}

/**
 * Trigger type for completion
 */
enum class CompletionTrigger {
    SLASH,      // Triggered by "/" for commands
    AT,         // Triggered by "@" for file references
    MANUAL      // Manually triggered (e.g., Ctrl+Space)
}

/**
 * Result of completion acceptance
 */
data class CompletionResult(
    val originalText: String,
    val newText: String,
    val newCursorPosition: Int
)