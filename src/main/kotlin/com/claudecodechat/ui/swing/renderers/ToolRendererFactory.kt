package com.claudecodechat.ui.swing.renderers

/**
 * Factory for creating appropriate tool renderers
 */
object ToolRendererFactory {
    
    private val renderers = listOf(
        TodoWriteRenderer(),
        BashRenderer(),
        ReadRenderer(),
        EditRenderer(),
        WriteRenderer(),
        // Add more specific renderers here
        GenericRenderer() // Always keep generic as last fallback
    )
    
    /**
     * Get appropriate renderer for the given tool name
     */
    fun getRenderer(toolName: String): ToolRenderer {
        return renderers.find { it.canHandle(toolName) } ?: GenericRenderer()
    }
    
    /**
     * Create tool card using appropriate renderer
     */
    fun createToolCard(
        toolName: String,
        toolInput: kotlinx.serialization.json.JsonElement?,
        toolOutput: String,
        hasError: Boolean,
        toolId: String? = null
    ): javax.swing.JPanel {
        val status = if (hasError) ToolStatus.ERROR else ToolStatus.SUCCESS
        val renderer = getRenderer(toolName)
        val cardRenderer = ToolCardRenderer()
        
        // Use friendly display name for tool title
        val displayName = when (toolName.lowercase()) {
            "todowrite" -> "Todo"
            "search_replace" -> "Edit"
            "multiedit" -> "MultiEdit"
            "write" -> "Write"
            else -> toolName
        }
        
        return cardRenderer.createCard(displayName, toolInput, toolOutput, status, renderer, toolId)
    }
}
