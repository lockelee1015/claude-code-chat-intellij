package com.claudecodechat.session

import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.Content
import com.claudecodechat.models.ContentType
import com.claudecodechat.models.Message
import com.claudecodechat.models.MessageType
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Loads and parses Claude Code session history from JSONL files
 */
class SessionHistoryLoader {
    
    private val logger = Logger.getInstance(SessionHistoryLoader::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Get the Claude projects directory
     */
    fun getClaudeProjectsDir(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".claude/projects")
    }
    
    /**
     * List all available projects
     */
    fun listProjects(): List<ProjectInfo> {
        val projectsDir = getClaudeProjectsDir()
        if (!projectsDir.exists() || !projectsDir.isDirectory) {
            return emptyList()
        }
        
        return projectsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            try {
                // Try to reconstruct the original path
                // This is a best-effort approach since we can't know exactly where dots vs slashes were
                val path = reconstructPath(dir.name)
                ProjectInfo(
                    id = dir.name,
                    path = path,
                    sessions = listProjectSessions(dir)
                )
            } catch (e: Exception) {
                logger.warn("Failed to parse project: ${dir.name}", e)
                null
            }
        } ?: emptyList()
    }
    
    /**
     * Best effort to reconstruct the original path from encoded name
     * This won't be perfect but helps for display purposes
     */
    private fun reconstructPath(encodedName: String): String {
        // Start with basic decoding
        var path = encodedName
        
        // Handle absolute paths
        if (path.startsWith("-")) {
            path = "/" + path.substring(1)
        }
        
        // Common patterns to preserve
        path = path
            .replace("-com-", ".com/")
            .replace("-org-", ".org/")
            .replace("-io-", ".io/")
            .replace("-net-", ".net/")
            .replace("-", "/")  // Replace remaining dashes with slashes
        
        return path
    }
    
    /**
     * List all sessions for a project with preview
     */
    fun listProjectSessions(projectDir: File): List<SessionInfo> {
        return projectDir.listFiles { file -> 
            file.name.endsWith(".jsonl") 
        }?.map { file ->
            val sessionId = file.nameWithoutExtension
            val (preview, messageCount) = getSessionPreview(file)
            SessionInfo(
                id = sessionId,
                file = file,
                lastModified = file.lastModified(),
                preview = preview,
                messageCount = messageCount
            )
        } ?: emptyList()
    }
    
    /**
     * Get preview text and message count for a session
     */
    private fun getSessionPreview(file: File): Pair<String?, Int> {
        if (!file.exists()) return null to 0
        
        var firstUserMessage: String? = null
        var messageCount = 0
        
        try {
            file.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) {
                        try {
                            val jsonElement = json.parseToJsonElement(line)
                            val jsonObject = jsonElement.jsonObject
                            val type = jsonObject["type"]?.jsonPrimitive?.content
                            
                            if (type == "user" && firstUserMessage == null) {
                                val messageObj = jsonObject["message"]?.jsonObject
                                val content = messageObj?.get("content")
                                
                                firstUserMessage = when (content) {
                                    is JsonPrimitive -> content.content.take(100)
                                    is JsonArray -> {
                                        content.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content?.take(100)
                                    }
                                    else -> null
                                }
                            }
                            
                            if (type in listOf("user", "assistant")) {
                                messageCount++
                            }
                        } catch (e: Exception) {
                            // Skip malformed lines
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to get preview for session: ${file.name}", e)
        }
        
        return firstUserMessage to messageCount
    }
    
    /**
     * Load a specific session's messages from JSONL file
     */
    fun loadSessionMessages(sessionFile: File): List<ClaudeStreamMessage> {
        if (!sessionFile.exists()) {
            logger.warn("Session file does not exist: ${sessionFile.path}")
            return emptyList()
        }
        
        logger.info("Loading messages from file: ${sessionFile.path}, size: ${sessionFile.length()} bytes")
        val messages = mutableListOf<ClaudeStreamMessage>()
        var lineCount = 0
        var parsedCount = 0
        
        try {
            sessionFile.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    lineCount++
                    if (line.isNotBlank()) {
                        try {
                            val message = parseJsonLine(line)
                            if (message != null) {
                                if (shouldIncludeMessage(message)) {
                                    messages.add(message)
                                    parsedCount++
                                } else {
                                    logger.debug("Skipped message type: ${message.type}, subtype: ${message.subtype}")
                                }
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to parse line $lineCount: ${e.message}")
                        }
                    }
                }
            }
            logger.info("Loaded $parsedCount messages from $lineCount lines")
        } catch (e: Exception) {
            logger.error("Failed to load session file: ${sessionFile.path}", e)
        }
        
        return messages
    }
    
    /**
     * Load session by ID from a specific project
     */
    fun loadSession(projectPath: String, sessionId: String): List<ClaudeStreamMessage> {
        logger.info("Loading session: $sessionId for project: $projectPath")
        val projectDir = getProjectDirectory(projectPath)
        logger.info("Project directory: ${projectDir.absolutePath}")
        val sessionFile = File(projectDir, "$sessionId.jsonl")
        logger.info("Session file path: ${sessionFile.absolutePath}, exists: ${sessionFile.exists()}")
        return loadSessionMessages(sessionFile)
    }
    
    /**
     * Get recent sessions for a project with details
     */
    fun getRecentSessionsWithDetails(projectPath: String, limit: Int = 10): List<SessionInfo> {
        val projectDir = getProjectDirectory(projectPath)
        if (!projectDir.exists()) {
            logger.warn("Project directory does not exist: ${projectDir.absolutePath}")
            return emptyList()
        }
        
        return listProjectSessions(projectDir)
            .sortedByDescending { it.lastModified }
            .take(limit)
    }
    
    /**
     * Parse a single JSONL line into a ClaudeStreamMessage
     */
    private fun parseJsonLine(line: String): ClaudeStreamMessage? {
        val jsonElement = json.parseToJsonElement(line)
        val jsonObject = jsonElement.jsonObject
        
        val type = jsonObject["type"]?.jsonPrimitive?.content ?: return null
        
        return when (type) {
            "user" -> parseUserMessage(jsonObject)
            "assistant" -> parseAssistantMessage(jsonObject)
            "tool_use" -> parseToolUseMessage(jsonObject)
            "tool_result" -> parseToolResultMessage(jsonObject)
            "system", "init" -> parseSystemMessage(jsonObject)
            else -> null
        }
    }
    
    private fun parseUserMessage(obj: JsonObject): ClaudeStreamMessage {
        val messageObj = obj["message"]?.jsonObject
        val content = messageObj?.get("content")?.let { contentElement ->
            when (contentElement) {
                is JsonPrimitive -> listOf(Content(
                    type = ContentType.TEXT,
                    text = contentElement.content
                ))
                is JsonArray -> contentElement.mapNotNull { parseContent(it) }
                else -> emptyList()
            }
        } ?: emptyList()
        
        return ClaudeStreamMessage(
            type = MessageType.USER,
            message = Message(
                role = "user",
                content = content
            ),
            timestamp = obj["timestamp"]?.jsonPrimitive?.content,
            isMeta = obj["isMeta"]?.jsonPrimitive?.content?.toBoolean() ?: false
        )
    }
    
    private fun parseAssistantMessage(obj: JsonObject): ClaudeStreamMessage {
        val messageObj = obj["message"]?.jsonObject
        val content = messageObj?.get("content")?.let { contentElement ->
            when (contentElement) {
                is JsonPrimitive -> listOf(Content(
                    type = ContentType.TEXT,
                    text = contentElement.content
                ))
                is JsonArray -> contentElement.mapNotNull { parseContent(it) }
                else -> emptyList()
            }
        } ?: emptyList()
        
        return ClaudeStreamMessage(
            type = MessageType.ASSISTANT,
            message = Message(
                role = "assistant",
                content = content
            ),
            timestamp = obj["timestamp"]?.jsonPrimitive?.content,
            isMeta = obj["isMeta"]?.jsonPrimitive?.content?.toBoolean() ?: false
        )
    }
    
    private fun parseToolUseMessage(obj: JsonObject): ClaudeStreamMessage? {
        val messageObj = obj["message"]?.jsonObject ?: return null
        val contentArray = messageObj["content"]?.jsonArray ?: return null
        
        val content = contentArray.mapNotNull { parseContent(it) }
        
        return ClaudeStreamMessage(
            type = MessageType.ASSISTANT,
            message = Message(
                role = "assistant",
                content = content
            ),
            timestamp = obj["timestamp"]?.jsonPrimitive?.content,
            isMeta = obj["isMeta"]?.jsonPrimitive?.content?.toBoolean() ?: false
        )
    }
    
    private fun parseToolResultMessage(obj: JsonObject): ClaudeStreamMessage? {
        // Tool results are typically embedded in assistant messages
        // We'll handle them as part of the assistant message content
        return null
    }
    
    private fun parseSystemMessage(obj: JsonObject): ClaudeStreamMessage? {
        // Extract session metadata if needed
        val sessionId = obj["session_id"]?.jsonPrimitive?.content
        val projectId = obj["project_id"]?.jsonPrimitive?.content
        
        if (sessionId != null) {
            return ClaudeStreamMessage(
                type = MessageType.SYSTEM,
                subtype = "init",
                sessionId = sessionId,
                projectId = projectId,
                isMeta = obj["isMeta"]?.jsonPrimitive?.content?.toBoolean() ?: false
            )
        }
        return null
    }
    
    private fun parseContent(element: JsonElement): Content? {
        if (element !is JsonObject) return null
        
        val type = element["type"]?.jsonPrimitive?.content ?: return null
        
        return when (type) {
            "text" -> Content(
                type = ContentType.TEXT,
                text = element["text"]?.jsonPrimitive?.content
            )
            "tool_use" -> Content(
                type = ContentType.TOOL_USE,
                id = element["id"]?.jsonPrimitive?.content,
                name = element["name"]?.jsonPrimitive?.content,
                input = element["input"]
            )
            "tool_result" -> Content(
                type = ContentType.TOOL_RESULT,
                toolUseId = element["tool_use_id"]?.jsonPrimitive?.content,
                content = element["content"]?.jsonPrimitive?.content,
                isError = element["is_error"]?.jsonPrimitive?.content == "true"
            )
            else -> null
        }
    }
    
    /**
     * Filter out messages that shouldn't be displayed
     */
    private fun shouldIncludeMessage(message: ClaudeStreamMessage): Boolean {
        // Skip meta messages
        if (message.isMeta) {
            return false
        }
        
        // Skip system messages except for init
        if (message.type == MessageType.SYSTEM && message.subtype != "init") {
            return false
        }
        
        // Skip result-type messages
        if (message.type.name == "RESULT" || message.subtype == "result") {
            return false
        }
        
        return true
    }
    
    /**
     * Decode project path from directory name
     * This is complex because Claude doesn't simply replace / with -
     * It preserves the actual project folder structure
     * 
     * Examples:
     * "-Users-lichao-workspace-github-com-lockelee1015-claude-intellij-claude-code-chat" 
     *   matches the actual path: "/Users/lichao/workspace/github.com/lockelee1015/claude-intellij/claude-code-chat"
     *   NOT "/Users/lichao/workspace/github/com/lockelee1015/claude/intellij/claude/code/chat"
     */
    private fun decodeProjectPath(encodedPath: String): String {
        // For now, we'll just match directly without decoding
        // Since we need to match the exact project path
        logger.debug("Attempting to decode path: $encodedPath")
        return encodedPath  // Return as-is for matching
    }
    
    /**
     * Get project directory from path
     */
    private fun getProjectDirectory(projectPath: String): File {
        val projectsDir = getClaudeProjectsDir()
        logger.info("Looking for project in: ${projectsDir.absolutePath}")
        logger.info("Project path to match: $projectPath")
        
        // The actual encoded directory name that Claude uses
        // Simply replace all / and . with -
        val encodedProjectPath = projectPath
            .replace("/", "-")
            .replace(".", "-")
        
        logger.info("Encoded project path: $encodedProjectPath")
        
        val projectDir = File(projectsDir, encodedProjectPath)
        if (projectDir.exists()) {
            logger.info("Found project directory: ${projectDir.absolutePath}")
        } else {
            logger.warn("Project directory not found: ${projectDir.absolutePath}")
        }
        
        return projectDir
    }
    
    /**
     * Encode project path to directory name
     */
    private fun encodeProjectPath(projectPath: String): String {
        // Replace / with - (Claude's encoding)
        // Keep the leading - for absolute paths
        val encoded = projectPath.replace("/", "-")
        logger.debug("Encoded path: $projectPath -> $encoded")
        return encoded
    }
    
    data class ProjectInfo(
        val id: String,
        val path: String,
        val sessions: List<SessionInfo>
    )
    
    data class SessionInfo(
        val id: String,
        val file: File,
        val lastModified: Long,
        val preview: String? = null,
        val messageCount: Int = 0
    )
}