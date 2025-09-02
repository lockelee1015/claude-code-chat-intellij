package com.claudecodechat.utils

import com.claudecodechat.models.ClaudeStreamMessage
import kotlinx.serialization.json.Json

/**
 * Utility object for JSON parsing operations
 */
object JsonUtils {
    
    /**
     * JSON configuration with lenient parsing
     */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
    
    /**
     * Parse a JSONL line to ClaudeStreamMessage
     */
    fun parseClaudeMessage(jsonLine: String): ClaudeStreamMessage {
        return json.decodeFromString<ClaudeStreamMessage>(jsonLine)
    }
    
    /**
     * Parse multiple JSONL lines
     */
    fun parseClaudeMessages(jsonLines: String): List<ClaudeStreamMessage> {
        return jsonLines.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    parseClaudeMessage(line)
                } catch (e: Exception) {
                    null // Skip invalid lines
                }
            }
    }
    
    /**
     * Serialize an object to JSON string
     */
    inline fun <reified T> toJson(value: T): String {
        return json.encodeToString(kotlinx.serialization.serializer(), value)
    }
    
    /**
     * Deserialize JSON string to object
     */
    inline fun <reified T> fromJson(jsonString: String): T {
        return json.decodeFromString(jsonString)
    }
}