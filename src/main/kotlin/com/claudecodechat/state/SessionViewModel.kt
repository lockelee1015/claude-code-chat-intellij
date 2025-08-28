package com.claudecodechat.state

import com.claudecodechat.cli.ClaudeCliService
import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.MessageType
import com.claudecodechat.models.SessionMetrics
import com.claudecodechat.persistence.SessionPersistence
import com.claudecodechat.session.SessionHistoryLoader
import com.claudecodechat.session.SessionFileWatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * ViewModel for managing Claude chat session state
 */
@Service(Service.Level.PROJECT)
class SessionViewModel(private val project: Project) : Disposable {
    
    private val logger = Logger.getInstance(SessionViewModel::class.java)
    private val cliService = ClaudeCliService.getInstance(project)
    private val sessionPersistence = SessionPersistence.getInstance(project)
    private val sessionHistoryLoader = SessionHistoryLoader()
    private val sessionFileWatcher = SessionFileWatcher(project)
    // Track current job
    private var currentThread: Thread? = null
    // Track if we're in follow mode (auto-scroll to new messages)
    private var followMode = true
    
    init {
        // Set up file watcher for real-time updates
        setupFileWatcher()
        
        // Register disposable
        Disposer.register(project, this)
        
        // Auto-resume most recent session
        Thread {
            try {
                Thread.sleep(500) // Small delay to ensure UI is ready
                
                // First try to get the most recent session from file system
                val projectPath = project.basePath
                if (projectPath != null) {
                    val recentSessions = sessionHistoryLoader.getRecentSessionsWithDetails(projectPath, 1)
                    if (recentSessions.isNotEmpty()) {
                        val mostRecent = recentSessions.first()
                        logger.info("Auto-resuming most recent session: ${mostRecent.id}")
                        resumeSession(mostRecent.id)
                    } else {
                        // Fall back to persisted session ID
                        sessionPersistence.getLastSessionId()?.let { sessionId ->
                            logger.info("Auto-resuming persisted session: $sessionId")
                            resumeSession(sessionId)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error auto-resuming session", e)
            }
        }.apply {
            name = "Auto-Resume-Thread"
            start()
        }
    }
    
    private fun setupFileWatcher() {
        sessionFileWatcher.watchProjectSessions(object : SessionFileWatcher.SessionChangeListener {
            override fun onSessionUpdated(sessionId: String, file: VirtualFile) {
                // Only update if this is the current session
                if (_currentSession.value?.sessionId == sessionId) {
                    logger.info("Current session file updated: $sessionId")
                    
                    // Load new messages (incrementally if possible)
                    Thread {
                        try {
                            val projectPath = project.basePath ?: return@Thread
                            val allMessages = sessionHistoryLoader.loadSession(projectPath, sessionId)
                            
                            // Only add new messages (messages not already in our list)
                            val currentMessageCount = _messages.value.size
                            if (allMessages.size > currentMessageCount) {
                                val newMessages = allMessages.drop(currentMessageCount)
                                logger.info("Adding ${newMessages.size} new messages to session")
                                
                                // Append new messages
                                _messages.value = _messages.value + newMessages
                                
                                // Update metrics for new messages
                                newMessages.forEach { message ->
                                    handleStreamMessage(message)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to update session from file", e)
                        }
                    }.apply {
                        name = "Session-Update-Thread"
                        start()
                    }
                }
            }
            
            override fun onSessionDeleted(sessionId: String) {
                if (_currentSession.value?.sessionId == sessionId) {
                    logger.info("Current session deleted: $sessionId")
                    // Clear the session if it was deleted
                    startNewSession()
                }
            }
        })
    }
    
    companion object {
        fun getInstance(project: Project): SessionViewModel = project.service()
    }
    
    data class SessionInfo(
        val sessionId: String,
        val projectId: String?,
        val startTime: Long = System.currentTimeMillis(),
        val model: String = "sonnet"
    )
    
    data class QueuedPrompt(
        val id: String,
        val prompt: String,
        val model: String
    )
    
    // State flows
    private val _messages = MutableStateFlow<List<ClaudeStreamMessage>>(emptyList())
    val messages: StateFlow<List<ClaudeStreamMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentSession = MutableStateFlow<SessionInfo?>(null)
    val currentSession: StateFlow<SessionInfo?> = _currentSession.asStateFlow()
    
    private val _sessionMetrics = MutableStateFlow(SessionMetrics())
    val sessionMetrics: StateFlow<SessionMetrics> = _sessionMetrics.asStateFlow()
    
    private val _queuedPrompts = MutableStateFlow<List<QueuedPrompt>>(emptyList())
    val queuedPrompts: StateFlow<List<QueuedPrompt>> = _queuedPrompts.asStateFlow()
    
    // Use a simple list for errors that can be accessed without suspend
    private val errorList = mutableListOf<String>()
    
    /**
     * Send a prompt to Claude
     */
    fun sendPrompt(prompt: String, model: String = "sonnet") {
        logger.info("Sending prompt: $prompt with model: $model")
        
        // If already loading, queue the prompt
        if (_isLoading.value) {
            logger.info("Already loading, queueing prompt")
            queuePrompt(prompt, model)
            return
        }
        
        currentThread?.interrupt()
        
        // Set loading state immediately in the main thread
        _isLoading.value = true
        logger.info("Loading state set to true")
        
        currentThread = Thread {
            try {
                // Add user message to the list
                addMessage(ClaudeStreamMessage(
                    type = MessageType.USER,
                    message = com.claudecodechat.models.Message(
                        role = "user",
                        content = listOf(
                            com.claudecodechat.models.Content(
                                type = com.claudecodechat.models.ContentType.TEXT,
                                text = prompt
                            )
                        )
                    )
                ))
                
                // Update metrics
                updateMetrics { it.copy(promptsSent = it.promptsSent + 1) }
                
                // Prepare execution options
                val options = ClaudeCliService.ExecuteOptions(
                    prompt = prompt,
                    model = model,
                    sessionId = _currentSession.value?.sessionId,
                    resume = false,
                    continueSession = _currentSession.value != null,
                    verbose = true,
                    skipPermissions = true
                )
                
                // Execute Claude Code CLI with callback
                cliService.executeClaudeCode(
                    projectPath = project.basePath ?: "",
                    options = options,
                    onMessage = { message ->
                        handleStreamMessage(message)
                    }
                )
                
            } catch (e: Exception) {
                logger.error("Error sending prompt", e)
                errorList.add("Failed to send prompt: ${e.message}")
                updateMetrics { it.copy(errorsEncountered = it.errorsEncountered + 1) }
                // Only reset loading state on error
                _isLoading.value = false
                logger.info("Loading state set to false due to error")
            } finally {
                // Don't reset loading state here - let the completion signal handle it
                // The loading state will be reset when we receive the "complete" signal
                // or when the user calls stopCurrentRequest()
                processNextQueuedPrompt()
            }
        }.apply {
            name = "Claude-Send-Thread"
            start()
        }
    }
    
    /**
     * Handle incoming stream message
     */
    private fun handleStreamMessage(message: ClaudeStreamMessage) {
        // Extract session info first if available
        if (message.type == MessageType.SYSTEM && 
            message.subtype == "init" && 
            message.sessionId != null) {
            val sessionInfo = SessionInfo(
                sessionId = message.sessionId,
                projectId = message.projectId,
                model = _currentSession.value?.model ?: "sonnet"
            )
            _currentSession.value = sessionInfo
            // Persist the session ID
            sessionPersistence.setLastSessionId(message.sessionId)
            return // Don't add system init messages to UI
        }
        
        // Handle completion signal
        if (message.type == MessageType.SYSTEM && message.subtype == "complete") {
            logger.info("Received completion signal, setting loading to false")
            _isLoading.value = false
            return
        }
        
        // Filter out system messages and pure result messages
        if (message.type == MessageType.SYSTEM) {
            return
        }
        
        // Filter out messages with type name "RESULT"
        if (message.type.name == "RESULT") {
            return
        }
        
        // Filter out messages with subtype "result"
        if (message.subtype == "result") {
            return
        }
        
        // Add all other messages (USER, ASSISTANT, tool calls/results within ASSISTANT messages)
        addMessage(message)
        
        // Update metrics based on message content
        message.message?.let { msg ->
            // Update token usage
            msg.usage?.let { usage ->
                updateMetrics { metrics ->
                    metrics.copy(
                        totalInputTokens = metrics.totalInputTokens + usage.inputTokens,
                        totalOutputTokens = metrics.totalOutputTokens + usage.outputTokens
                    )
                }
            }
            
            // Track tool usage
            msg.content.forEach { content ->
                when (content.type) {
                    com.claudecodechat.models.ContentType.TOOL_USE -> {
                        updateMetrics { it.copy(toolsExecuted = it.toolsExecuted + 1) }
                        
                        // Track file operations
                        content.name?.let { toolName ->
                            when {
                                toolName.contains("create", ignoreCase = true) ||
                                toolName.contains("write", ignoreCase = true) -> {
                                    updateMetrics { it.copy(filesCreated = it.filesCreated + 1) }
                                }
                                toolName.contains("edit", ignoreCase = true) ||
                                toolName.contains("modify", ignoreCase = true) -> {
                                    updateMetrics { it.copy(filesModified = it.filesModified + 1) }
                                }
                                toolName.contains("delete", ignoreCase = true) ||
                                toolName.contains("remove", ignoreCase = true) -> {
                                    updateMetrics { it.copy(filesDeleted = it.filesDeleted + 1) }
                                }
                            }
                        }
                    }
                    com.claudecodechat.models.ContentType.TOOL_RESULT -> {
                        if (content.isError == true) {
                            updateMetrics { it.copy(toolsFailed = it.toolsFailed + 1) }
                        }
                    }
                    com.claudecodechat.models.ContentType.TEXT -> {
                        // Count code blocks (simple heuristic)
                        content.text?.let { text ->
                            val codeBlockCount = text.count { it == '`' } / 6  // Rough estimate
                            if (codeBlockCount > 0) {
                                updateMetrics { 
                                    it.copy(codeBlocksGenerated = it.codeBlocksGenerated + codeBlockCount)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Handle errors
        message.error?.let {
            errorList.add("Error: ${it.message}")
            updateMetrics { metrics ->
                metrics.copy(errorsEncountered = metrics.errorsEncountered + 1)
            }
        }
    }
    
    /**
     * Start a new session
     */
    fun startNewSession() {
        logger.info("Starting new session")
        currentThread?.interrupt()
        _messages.value = emptyList()
        _currentSession.value = null
        _sessionMetrics.value = SessionMetrics()
        _queuedPrompts.value = emptyList()
        _isLoading.value = false
        sessionPersistence.setLastSessionId(null)
        
        // Session started - no need to show system message in UI
    }
    
    /**
     * Resume an existing session
     */
    fun resumeSession(sessionId: String) {
        Thread {
            _isLoading.value = true
            try {
                logger.info("Resuming session: $sessionId")
                val projectPath = project.basePath
                
                if (projectPath == null) {
                    logger.error("Project base path is null")
                    errorList.add("Cannot resume session: project path not found")
                    return@Thread
                }
                
                logger.info("Project path: $projectPath")
                
                // Load session history from JSONL file
                val historicalMessages = sessionHistoryLoader.loadSession(projectPath, sessionId)
                
                logger.info("Loaded ${historicalMessages.size} historical messages for session $sessionId")
                
                if (historicalMessages.isEmpty()) {
                    logger.warn("No messages loaded for session $sessionId - file may not exist or be empty")
                    // Still update session info even if no messages loaded
                }
                
                // Clear current messages and load historical ones
                _messages.value = historicalMessages
                
                // Update session info
                _currentSession.value = SessionInfo(
                    sessionId = sessionId,
                    projectId = null
                )
                sessionPersistence.setLastSessionId(sessionId)
                
                // Update metrics based on loaded messages
                if (historicalMessages.isNotEmpty()) {
                    updateMetricsFromMessages(historicalMessages)
                }
                updateMetrics { it.copy(wasResumed = true) }
                
                logger.info("Session $sessionId resumed successfully with ${historicalMessages.size} messages")
                
            } catch (e: Exception) {
                logger.error("Failed to resume session: $sessionId", e)
                errorList.add("Failed to resume session: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }.apply {
            name = "Resume-Session-Thread"
            start()
        }
    }
    
    /**
     * Update metrics from loaded historical messages
     */
    private fun updateMetricsFromMessages(messages: List<ClaudeStreamMessage>) {
        var inputTokens = 0
        var outputTokens = 0
        var toolsExecuted = 0
        var filesCreated = 0
        var filesModified = 0
        
        messages.forEach { message ->
            message.message?.let { msg ->
                // Count tokens
                msg.usage?.let { usage ->
                    inputTokens += usage.inputTokens
                    outputTokens += usage.outputTokens
                }
                
                // Count tools
                msg.content.forEach { content ->
                    when (content.type) {
                        com.claudecodechat.models.ContentType.TOOL_USE -> {
                            toolsExecuted++
                            content.name?.let { toolName ->
                                when {
                                    toolName.contains("create", ignoreCase = true) ||
                                    toolName.contains("write", ignoreCase = true) -> filesCreated++
                                    toolName.contains("edit", ignoreCase = true) ||
                                    toolName.contains("modify", ignoreCase = true) -> filesModified++
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        
        _sessionMetrics.value = SessionMetrics(
            totalInputTokens = inputTokens,
            totalOutputTokens = outputTokens,
            toolsExecuted = toolsExecuted,
            filesCreated = filesCreated,
            filesModified = filesModified,
            wasResumed = true
        )
    }
    
    /**
     * Get recent session IDs for quick resume
     */
    fun getRecentSessionIds(): List<String> {
        // First try to get from persistence
        val persistedIds = sessionPersistence.getRecentSessionIds()
        if (persistedIds.isNotEmpty()) return persistedIds
        
        // Otherwise scan the file system
        return try {
            val projectPath = project.basePath ?: return emptyList()
            val sessions = sessionHistoryLoader.getRecentSessionsWithDetails(projectPath)
            sessions.map { it.id }
        } catch (e: Exception) {
            logger.warn("Failed to load recent sessions from filesystem", e)
            emptyList()
        }
    }
    
    /**
     * Get recent sessions with details for UI display
     */
    fun getRecentSessionsWithDetails(): List<SessionDetails> {
        val projectPath = project.basePath ?: return emptyList()
        
        return try {
            val sessions = sessionHistoryLoader.getRecentSessionsWithDetails(projectPath)
            sessions.map { session ->
                SessionDetails(
                    id = session.id,
                    preview = session.preview ?: "No messages",
                    timestamp = formatTimestamp(session.lastModified),
                    messageCount = session.messageCount
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to load session details", e)
            emptyList()
        }
    }
    
    private fun formatTimestamp(millis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - millis
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} min ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            else -> {
                val date = java.util.Date(millis)
                val format = java.text.SimpleDateFormat("MMM dd, HH:mm")
                format.format(date)
            }
        }
    }
    
    data class SessionDetails(
        val id: String,
        val preview: String,
        val timestamp: String,
        val messageCount: Int
    )
    
    /**
     * Clear the chat
     */
    fun clearChat() {
        currentThread?.interrupt()
        _messages.value = emptyList()
        _sessionMetrics.value = SessionMetrics()
        _queuedPrompts.value = emptyList()
        errorList.clear()
    }
    
    /**
     * Queue a prompt for later execution
     */
    private fun queuePrompt(prompt: String, model: String) {
        Thread {
            val queuedPrompt = QueuedPrompt(
                id = System.currentTimeMillis().toString(),
                prompt = prompt,
                model = model
            )
            _queuedPrompts.value = _queuedPrompts.value + queuedPrompt
        }.apply {
            name = "Queue-Prompt-Thread"
            start()
        }
    }
    
    /**
     * Process next queued prompt
     */
    private fun processNextQueuedPrompt() {
        if (_queuedPrompts.value.isNotEmpty()) {
            Thread {
                val prompt = _queuedPrompts.value.firstOrNull()
                if (prompt != null) {
                    _queuedPrompts.value = _queuedPrompts.value.drop(1)
                    sendPrompt(prompt.prompt, prompt.model)
                }
            }.apply {
                name = "Process-Queue-Thread"
                start()
            }
        }
    }
    
    private fun addMessage(message: ClaudeStreamMessage) {
        _messages.value = _messages.value + message
    }
    
    private fun updateMetrics(update: (SessionMetrics) -> SessionMetrics) {
        _sessionMetrics.value = update(_sessionMetrics.value)
    }
    
    fun getErrors(): List<String> = errorList.toList()
    
    fun clearErrors() {
        errorList.clear()
    }
    
    fun stopCurrentRequest() {
        logger.info("Stopping current request")
        currentThread?.interrupt()
        currentThread = null
        _isLoading.value = false
        logger.info("Request stopped, loading state set to false")
    }
    
    /**
     * Set follow mode (auto-scroll to new messages)
     */
    fun setFollowMode(enabled: Boolean) {
        followMode = enabled
    }
    
    fun isFollowMode(): Boolean = followMode
    
    override fun dispose() {
        currentThread?.interrupt()
        // File watcher will be disposed automatically as it's registered with Disposer
    }
}