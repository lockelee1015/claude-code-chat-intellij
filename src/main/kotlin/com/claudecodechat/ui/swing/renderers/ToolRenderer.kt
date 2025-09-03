package com.claudecodechat.ui.swing.renderers

import kotlinx.serialization.json.JsonElement
import javax.swing.JPanel

/**
 * Tool execution status
 */
enum class ToolStatus {
    SUCCESS,    // 成功完成
    ERROR,      // 执行失败  
    IN_PROGRESS // 进行中（保留，暂未使用）
}

/**
 * Tool rendering input data
 */
data class ToolRenderInput(
    val toolName: String,
    val toolInput: JsonElement?,
    val toolOutput: String,
    val status: ToolStatus
)

/**
 * Abstract base class for tool renderers
 */
abstract class ToolRenderer {
    
    /**
     * Render the tool content panel (without title/header)
     * @param input Tool rendering input data
     * @return JPanel containing the tool-specific content
     */
    abstract fun renderContent(input: ToolRenderInput): JPanel
    
    /**
     * Extract display parameters from tool input for title
     * @param toolInput Raw tool input JSON
     * @return Formatted parameters string for display in title
     */
    open fun extractDisplayParameters(toolInput: JsonElement?): String = ""
    
    /**
     * Check if this renderer can handle the given tool name
     */
    abstract fun canHandle(toolName: String): Boolean
}
