package com.claudecodechat.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionMetricsTest {
    
    @Test
    fun `should initialize with default values`() {
        val metrics = SessionMetrics()
        
        assertNull(metrics.firstMessageTime)
        assertEquals(0, metrics.promptsSent)
        assertEquals(0, metrics.toolsExecuted)
        assertEquals(0, metrics.toolsFailed)
        assertEquals(0, metrics.filesCreated)
        assertEquals(0, metrics.filesModified)
        assertEquals(0, metrics.filesDeleted)
        assertEquals(0, metrics.codeBlocksGenerated)
        assertEquals(0, metrics.errorsEncountered)
        assertEquals(0, metrics.checkpointCount)
        assertFalse(metrics.wasResumed)
        assertTrue(metrics.modelChanges.isEmpty())
        assertEquals(0, metrics.totalInputTokens)
        assertEquals(0, metrics.totalOutputTokens)
    }
    
    @Test
    fun `should update metrics correctly`() {
        val metrics = SessionMetrics()
        
        // Update various metrics
        metrics.promptsSent = 5
        metrics.toolsExecuted = 10
        metrics.toolsFailed = 2
        metrics.filesCreated = 3
        metrics.filesModified = 7
        metrics.filesDeleted = 1
        metrics.codeBlocksGenerated = 4
        metrics.errorsEncountered = 1
        metrics.checkpointCount = 2
        metrics.wasResumed = true
        metrics.modelChanges.add("sonnet")
        metrics.modelChanges.add("opus")
        metrics.totalInputTokens = 1000
        metrics.totalOutputTokens = 2000
        
        // Verify updates
        assertEquals(5, metrics.promptsSent)
        assertEquals(10, metrics.toolsExecuted)
        assertEquals(2, metrics.toolsFailed)
        assertEquals(3, metrics.filesCreated)
        assertEquals(7, metrics.filesModified)
        assertEquals(1, metrics.filesDeleted)
        assertEquals(4, metrics.codeBlocksGenerated)
        assertEquals(1, metrics.errorsEncountered)
        assertEquals(2, metrics.checkpointCount)
        assertTrue(metrics.wasResumed)
        assertEquals(listOf("sonnet", "opus"), metrics.modelChanges)
        assertEquals(1000, metrics.totalInputTokens)
        assertEquals(2000, metrics.totalOutputTokens)
    }
    
    @Test
    fun `should track first message time`() {
        val metrics = SessionMetrics()
        val currentTime = System.currentTimeMillis()
        
        assertNull(metrics.firstMessageTime)
        
        metrics.firstMessageTime = currentTime
        
        assertEquals(currentTime, metrics.firstMessageTime)
    }
    
    @Test
    fun `should calculate success rate for tools`() {
        val metrics = SessionMetrics().apply {
            toolsExecuted = 10
            toolsFailed = 2
        }
        
        val successRate = ((metrics.toolsExecuted - metrics.toolsFailed).toDouble() / 
                          metrics.toolsExecuted * 100)
        
        assertEquals(80.0, successRate, 0.01)
    }
    
    @Test
    fun `should track multiple model changes`() {
        val metrics = SessionMetrics()
        
        metrics.modelChanges.add("sonnet")
        metrics.modelChanges.add("opus")
        metrics.modelChanges.add("sonnet")  // Switch back
        
        assertEquals(3, metrics.modelChanges.size)
        assertEquals("sonnet", metrics.modelChanges.first())
        assertEquals("sonnet", metrics.modelChanges.last())
        assertEquals("opus", metrics.modelChanges[1])
    }
    
    @Test
    fun `should calculate total file operations`() {
        val metrics = SessionMetrics().apply {
            filesCreated = 5
            filesModified = 10
            filesDeleted = 2
        }
        
        val totalFileOps = metrics.filesCreated + metrics.filesModified + metrics.filesDeleted
        
        assertEquals(17, totalFileOps)
    }
    
    @Test
    fun `should handle copy operations correctly`() {
        val original = SessionMetrics().apply {
            promptsSent = 3
            toolsExecuted = 5
            modelChanges.add("sonnet")
            totalInputTokens = 500
            totalOutputTokens = 1000
        }
        
        // Kotlin data class copy
        val copied = original.copy(
            promptsSent = 4,
            totalInputTokens = 600
        )
        
        // Original should remain unchanged
        assertEquals(3, original.promptsSent)
        assertEquals(500, original.totalInputTokens)
        
        // Copied should have new values
        assertEquals(4, copied.promptsSent)
        assertEquals(600, copied.totalInputTokens)
        
        // Other values should be copied
        assertEquals(5, copied.toolsExecuted)
        assertEquals(1000, copied.totalOutputTokens)
    }
}