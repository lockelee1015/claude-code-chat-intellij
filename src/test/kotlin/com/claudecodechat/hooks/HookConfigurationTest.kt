package com.claudecodechat.hooks

import com.claudecodechat.utils.JsonUtils
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HookConfigurationTest {
    
    @Test
    fun `should create empty hook configuration`() {
        val config = HooksConfiguration()
        
        assertNull(config.PreToolUse)
        assertNull(config.PostToolUse)
        assertNull(config.Notification)
        assertNull(config.Stop)
        assertNull(config.SubagentStop)
    }
    
    @Test
    fun `should create hook configuration with matchers`() {
        val config = HooksConfiguration(
            PreToolUse = listOf(
                HookMatcher(
                    matcher = "Bash",
                    hooks = listOf(
                        HookCommand(
                            type = "command",
                            command = "echo 'Running bash command'",
                            timeout = 10
                        )
                    )
                )
            ),
            PostToolUse = listOf(
                HookMatcher(
                    matcher = "Write|Edit",
                    hooks = listOf(
                        HookCommand(
                            type = "command",
                            command = "prettier --write \$FILE_PATH"
                        )
                    )
                )
            )
        )
        
        assertNotNull(config.PreToolUse)
        assertEquals(1, config.PreToolUse?.size)
        assertEquals("Bash", config.PreToolUse?.first()?.matcher)
        
        assertNotNull(config.PostToolUse)
        assertEquals(1, config.PostToolUse?.size)
        assertEquals("Write|Edit", config.PostToolUse?.first()?.matcher)
    }
    
    @Test
    fun `should serialize and deserialize hook configuration`() {
        val original = HooksConfiguration(
            PreToolUse = listOf(
                HookMatcher(
                    matcher = ".*git.*",
                    hooks = listOf(
                        HookCommand(
                            type = "command",
                            command = "git status",
                            timeout = 5
                        )
                    )
                )
            ),
            Notification = listOf(
                HookCommand(
                    type = "command",
                    command = "notify-send 'Task completed'"
                )
            )
        )
        
        val json = JsonUtils.toJson(original)
        val deserialized = JsonUtils.fromJson<HooksConfiguration>(json)
        
        assertEquals(original.PreToolUse?.size, deserialized.PreToolUse?.size)
        assertEquals(
            original.PreToolUse?.first()?.matcher,
            deserialized.PreToolUse?.first()?.matcher
        )
        assertEquals(
            original.Notification?.size,
            deserialized.Notification?.size
        )
    }
    
    @Test
    fun `should handle hook context correctly`() {
        val toolInput = buildJsonObject {
            put("command", JsonPrimitive("ls -la"))
            put("description", JsonPrimitive("List files"))
        }
        
        val context = HookContext(
            toolName = "Bash",
            toolInput = toolInput,
            projectPath = "/path/to/project",
            sessionId = "session_123",
            timestamp = 1234567890
        )
        
        assertEquals("Bash", context.toolName)
        assertNotNull(context.toolInput)
        assertEquals("/path/to/project", context.projectPath)
        assertEquals("session_123", context.sessionId)
        assertEquals(1234567890, context.timestamp)
    }
    
    @Test
    fun `should create hook result with success`() {
        val result = HookResult(
            success = true,
            output = "Command executed successfully",
            exitCode = 0,
            shouldBlock = false
        )
        
        assertTrue(result.success)
        assertEquals("Command executed successfully", result.output)
        assertNull(result.error)
        assertEquals(0, result.exitCode)
        assertFalse(result.shouldBlock)
    }
    
    @Test
    fun `should create hook result with error`() {
        val result = HookResult(
            success = false,
            error = "Command failed",
            exitCode = 1,
            shouldBlock = true
        )
        
        assertFalse(result.success)
        assertEquals("Command failed", result.error)
        assertNull(result.output)
        assertEquals(1, result.exitCode)
        assertTrue(result.shouldBlock)
    }
    
    @Test
    fun `should handle blocking hook result`() {
        val result = HookResult(
            success = false,
            error = "Operation blocked by security policy",
            exitCode = 2,  // Exit code 2 means block
            shouldBlock = true
        )
        
        assertFalse(result.success)
        assertTrue(result.shouldBlock)
        assertEquals(2, result.exitCode)
    }
    
    @Test
    fun `should match hook levels correctly`() {
        val levels = HookLevel.values()
        
        assertEquals(3, levels.size)
        assertTrue(HookLevel.LOCAL in levels)
        assertTrue(HookLevel.PROJECT in levels)
        assertTrue(HookLevel.USER in levels)
        
        // Verify priority order (implicit by enum ordinal)
        assertTrue(HookLevel.LOCAL.ordinal < HookLevel.PROJECT.ordinal)
        assertTrue(HookLevel.PROJECT.ordinal < HookLevel.USER.ordinal)
    }
    
    @Test
    fun `should create complex hook configuration`() {
        val config = HooksConfiguration(
            PreToolUse = listOf(
                HookMatcher(
                    matcher = "Bash",
                    hooks = listOf(
                        HookCommand(
                            type = "command",
                            command = """
                                if [[ "${'$'}(jq -r .tool_input.command)" =~ "rm -rf" ]]; then
                                    echo "Dangerous command blocked"
                                    exit 2
                                fi
                            """.trimIndent(),
                            timeout = 5
                        )
                    )
                ),
                HookMatcher(
                    matcher = "Write|Edit|MultiEdit",
                    hooks = listOf(
                        HookCommand(
                            type = "command",
                            command = "echo 'File operation: \${TOOL_NAME}'",
                            timeout = 3
                        )
                    )
                )
            ),
            PostToolUse = listOf(
                HookMatcher(
                    hooks = listOf(
                        HookCommand(
                            type = "command",
                            command = "git add -A && git commit -m 'Auto-commit by Claude'"
                        )
                    )
                )
            ),
            Stop = listOf(
                HookCommand(
                    type = "command",
                    command = "cleanup_temp_files.sh"
                )
            )
        )
        
        assertEquals(2, config.PreToolUse?.size)
        assertEquals(1, config.PostToolUse?.size)
        assertNull(config.PostToolUse?.first()?.matcher) // No matcher means match all
        assertEquals(1, config.Stop?.size)
    }
}
