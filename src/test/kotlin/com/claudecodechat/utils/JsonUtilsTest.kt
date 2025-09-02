package com.claudecodechat.utils

import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.Content
import com.claudecodechat.models.ContentType
import com.claudecodechat.models.Message
import com.claudecodechat.models.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonUtilsTest {
    
    @Test
    fun `should parse simple text message`() {
        val json = """
            {
                "type": "assistant",
                "message": {
                    "role": "assistant",
                    "content": [
                        {
                            "type": "text",
                            "text": "Hello, how can I help you?"
                        }
                    ]
                }
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        assertEquals(MessageType.ASSISTANT, message.type)
        assertNotNull(message.message)
        assertEquals("assistant", message.message?.role)
        assertEquals(1, message.message?.content?.size)
        assertEquals(ContentType.TEXT, message.message?.content?.first()?.type)
        assertEquals("Hello, how can I help you?", message.message?.content?.first()?.text)
    }
    
    @Test
    fun `should parse tool use message`() {
        val json = """
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {
                            "type": "tool_use",
                            "id": "tool_123",
                            "name": "Bash",
                            "input": {
                                "command": "ls -la",
                                "description": "List files"
                            }
                        }
                    ]
                }
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        assertEquals(MessageType.ASSISTANT, message.type)
        val toolUse = message.message?.content?.first()
        assertNotNull(toolUse)
        assertEquals(ContentType.TOOL_USE, toolUse?.type)
        assertEquals("tool_123", toolUse?.id)
        assertEquals("Bash", toolUse?.name)
        assertNotNull(toolUse?.input)
    }
    
    @Test
    fun `should parse system init message with session info`() {
        val json = """
            {
                "type": "system",
                "subtype": "init",
                "session_id": "session_abc123",
                "project_id": "project_xyz",
                "timestamp": "2024-01-01T12:00:00Z"
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        assertEquals(MessageType.SYSTEM, message.type)
        assertEquals("init", message.subtype)
        assertEquals("session_abc123", message.sessionId)
        assertEquals("project_xyz", message.projectId)
        assertEquals("2024-01-01T12:00:00Z", message.timestamp)
    }
    
    @Test
    fun `should parse error message`() {
        val json = """
            {
                "type": "error",
                "error": {
                    "type": "api_error",
                    "message": "API rate limit exceeded",
                    "code": "RATE_LIMIT"
                }
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        assertEquals(MessageType.ERROR, message.type)
        assertNotNull(message.error)
        assertEquals("api_error", message.error?.type)
        assertEquals("API rate limit exceeded", message.error?.message)
        assertEquals("RATE_LIMIT", message.error?.code)
    }
    
    @Test
    fun `should parse message with usage info`() {
        val json = """
            {
                "type": "assistant",
                "message": {
                    "usage": {
                        "input_tokens": 100,
                        "output_tokens": 250,
                        "cache_creation_input_tokens": 50,
                        "cache_read_input_tokens": 25
                    }
                }
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        val usage = message.message?.usage
        assertNotNull(usage)
        assertEquals(100, usage?.inputTokens)
        assertEquals(250, usage?.outputTokens)
        assertEquals(50, usage?.cacheCreationInputTokens)
        assertEquals(25, usage?.cacheReadInputTokens)
    }
    
    @Test
    fun `should parse multiple JSONL lines`() {
        val jsonLines = """
            {"type": "system", "subtype": "init", "session_id": "session_1"}
            {"type": "user", "message": {"content": [{"type": "text", "text": "Hello"}]}}
            {"type": "assistant", "message": {"content": [{"type": "text", "text": "Hi!"}]}}
        """.trimIndent()
        
        val messages = JsonUtils.parseClaudeMessages(jsonLines)
        
        assertEquals(3, messages.size)
        assertEquals(MessageType.SYSTEM, messages[0].type)
        assertEquals(MessageType.USER, messages[1].type)
        assertEquals(MessageType.ASSISTANT, messages[2].type)
    }
    
    @Test
    fun `should skip invalid lines when parsing multiple messages`() {
        val jsonLines = """
            {"type": "system", "subtype": "init"}
            invalid json line
            {"type": "user", "message": {"content": []}}
            
            {"type": "assistant", "message": {"content": []}}
        """.trimIndent()
        
        val messages = JsonUtils.parseClaudeMessages(jsonLines)
        
        assertEquals(3, messages.size) // Should skip the invalid line and empty line
    }
    
    @Test
    fun `should handle unknown fields gracefully`() {
        val json = """
            {
                "type": "assistant",
                "unknown_field": "some_value",
                "message": {
                    "content": []
                }
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        assertEquals(MessageType.ASSISTANT, message.type)
        assertNotNull(message.message)
    }
    
    @Test
    fun `should serialize and deserialize objects correctly`() {
        val original = ClaudeStreamMessage(
            type = MessageType.USER,
            message = Message(
                role = "user",
                content = listOf(
                    Content(
                        type = ContentType.TEXT,
                        text = "Test message"
                    )
                )
            ),
            sessionId = "test_session",
            timestamp = "2024-01-01T00:00:00Z"
        )
        
        val json = JsonUtils.toJson(original)
        val deserialized = JsonUtils.fromJson<ClaudeStreamMessage>(json)
        
        assertEquals(original.type, deserialized.type)
        assertEquals(original.sessionId, deserialized.sessionId)
        assertEquals(original.timestamp, deserialized.timestamp)
        assertEquals(original.message?.role, deserialized.message?.role)
        assertEquals(original.message?.content?.size, deserialized.message?.content?.size)
    }
    
    @Test
    fun `should parse tool result with error`() {
        val json = """
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {
                            "type": "tool_result",
                            "tool_use_id": "tool_123",
                            "content": "Error: Command failed",
                            "is_error": true
                        }
                    ]
                }
            }
        """.trimIndent()
        
        val message = JsonUtils.parseClaudeMessage(json)
        
        val toolResult = message.message?.content?.first()
        assertNotNull(toolResult)
        assertEquals(ContentType.TOOL_RESULT, toolResult?.type)
        assertEquals("tool_123", toolResult?.toolUseId)
        assertEquals("Error: Command failed", toolResult?.content)
        assertTrue(toolResult?.isError ?: false)
    }
}
