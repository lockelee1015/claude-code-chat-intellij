package com.claudecodechat.models

import androidx.compose.ui.graphics.ImageBitmap
import java.util.UUID

/**
 * Represents an image attachment in a chat message
 */
data class ImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val displayId: Int, // For showing as [Image 1], [Image 2], etc.
    val base64Data: String,
    val mediaType: String, // e.g., "image/png", "image/jpeg"
    val thumbnailBitmap: ImageBitmap? = null,
    val sizeBytes: Long,
    val fileName: String? = null
) {
    /**
     * Get the placeholder text for this image
     */
    fun getPlaceholder(): String = "[Image $displayId]"
    
    /**
     * Check if the image size is within limits (5MB)
     */
    fun isWithinSizeLimit(): Boolean = sizeBytes <= 5 * 1024 * 1024
    
    /**
     * Convert to Claude API format
     */
    fun toClaudeContent(): Map<String, Any> = mapOf(
        "type" to "image",
        "source" to mapOf(
            "type" to "base64",
            "media_type" to mediaType,
            "data" to base64Data
        )
    )
}