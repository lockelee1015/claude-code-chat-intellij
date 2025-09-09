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
    // Track if user has manually selected a session (to prevent auto-resume conflicts)
    private var userHasSelectedSession = false
    // Current logical session id (groups multiple CLI sessions)
    private var currentLogicalId: String? = null
    
    init {
        // Set up file watcher for real-time updates
        setupFileWatcher()
        
        // Register disposable
        Disposer.register(project, this)
        
        // Auto-resume most recent session only if user hasn't manually selected one
        Thread {
            try {
                Thread.sleep(2000) // Longer delay to give user time to select a session
                
                // Only auto-resume if user hasn't manually selected a session
                if (!userHasSelectedSession) {
                    val lid = sessionPersistence.getLastLogicalSessionId()
                    if (lid != null) {
                        logger.info("Auto-resuming most recent logical session: $lid")
                        resumeLogicalSession(lid)
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
        val model: String,
        val planMode: Boolean = false
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
    fun sendPrompt(prompt: String, model: String = "sonnet", planMode: Boolean = false) {
        logger.info("Sending prompt: $prompt with model: $model")
        
        // If already loading, queue the prompt
        if (_isLoading.value) {
            logger.info("Already loading, queueing prompt")
            queuePrompt(prompt, model, planMode)
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
                val currentSessionId = _currentSession.value?.sessionId
                logger.info("Sending prompt with session management: sessionId=$currentSessionId, resume=${currentSessionId != null}")
                
                val options = ClaudeCliService.ExecuteOptions(
                    prompt = prompt,
                    model = model,
                    sessionId = currentSessionId,
                    resume = false, // 不再使用这个参数，CLI逻辑会自动处理
                    continueSession = false, // 不使用 -c，让用户明确控制session创建
                    verbose = true,
                    skipPermissions = true,
                    permissionMode = if (planMode) "plan" else null
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
        logger.info("Handling message type: ${message.type}, subtype: ${message.subtype}")
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
            // Record mapping to logical session
            if (currentLogicalId == null) {
                currentLogicalId = sessionPersistence.createLogicalSession()
            }
            currentLogicalId?.let { lid ->
                sessionPersistence.appendCliSession(lid, message.sessionId)
                sessionPersistence.setLastLogicalSessionId(lid)
            }
            return // Don't add system init messages to UI
        }
        
        // Handle completion signal
        if (message.type == MessageType.SYSTEM && message.subtype == "complete") {
            logger.info("Received completion signal, setting loading to false")
            _isLoading.value = false
            
            // Check if this completion message has usage data
            message.message?.usage?.let { usage ->
                logger.info("Completion message usage: input=${usage.inputTokens}, output=${usage.outputTokens}")
                updateMetrics { metrics ->
                    val newInput = metrics.totalInputTokens + usage.inputTokens
                    val newOutput = metrics.totalOutputTokens + usage.outputTokens
                    val newCacheRead = metrics.cacheReadInputTokens + (usage.cacheReadInputTokens ?: 0)
                    val newCacheCreation = metrics.cacheCreationInputTokens + (usage.cacheCreationInputTokens ?: 0)
                    logger.info("Updating metrics from completion: totalInput=$newInput, totalOutput=$newOutput")
                    metrics.copy(
                        totalInputTokens = newInput,
                        totalOutputTokens = newOutput,
                        cacheReadInputTokens = newCacheRead,
                        cacheCreationInputTokens = newCacheCreation
                    )
                }
            } ?: run {
                logger.info("No usage data found in completion message. Message content: ${message.message}")
            }
            return
        }
        
        // If an error occurs, stop loading and surface the error message
        if (message.type == MessageType.ERROR) {
            logger.warn("Received ERROR message: ${message.error?.message ?: message}")
            _isLoading.value = false
            addMessage(message) // still show the error in the transcript
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

        // Update logical session summary info for history list
        if (currentLogicalId == null) {
            currentLogicalId = sessionPersistence.createLogicalSession()
        }
        currentLogicalId?.let { lid ->
            if (message.type == MessageType.USER) {
                val firstText = message.message?.content?.firstOrNull { it.type == com.claudecodechat.models.ContentType.TEXT }?.text
                sessionPersistence.updatePreviewAndCount(lid, firstText, incrementCountBy = 1)
            } else if (message.type == MessageType.ASSISTANT) {
                sessionPersistence.updatePreviewAndCount(lid, null, incrementCountBy = 1)
            }
        }
        
        // Update metrics based on message content
        message.message?.let { msg ->
            // Update token usage
            msg.usage?.let { usage ->
                logger.info("Token usage detected: input=${usage.inputTokens}, output=${usage.outputTokens}, cacheRead=${usage.cacheReadInputTokens}, cacheCreation=${usage.cacheCreationInputTokens}")
                updateMetrics { metrics ->
                    val newInput = metrics.totalInputTokens + usage.inputTokens
                    val newOutput = metrics.totalOutputTokens + usage.outputTokens
                    val newCacheRead = metrics.cacheReadInputTokens + (usage.cacheReadInputTokens ?: 0)
                    val newCacheCreation = metrics.cacheCreationInputTokens + (usage.cacheCreationInputTokens ?: 0)
                    logger.info("Updating metrics: totalInput=$newInput, totalOutput=$newOutput, cacheRead=$newCacheRead, cacheCreation=$newCacheCreation")
                    metrics.copy(
                        totalInputTokens = newInput,
                        totalOutputTokens = newOutput,
                        cacheReadInputTokens = newCacheRead,
                        cacheCreationInputTokens = newCacheCreation
                    )
                }
            } ?: run {
                // Debug: log when no usage data is found
                logger.info("No usage data found in message type: ${message.type}, subtype: ${message.subtype}")
                
                // Fallback: estimate tokens for text messages if no usage data available
                if (message.type == MessageType.ASSISTANT || message.type == MessageType.USER) {
                    msg.content.forEach { content ->
                        if (content.type == com.claudecodechat.models.ContentType.TEXT && content.text != null) {
                            val estimatedTokens = estimateTokens(content.text)
                            if (estimatedTokens > 0) {
                                logger.info("Estimated ${estimatedTokens} tokens for ${message.type} message")
                                updateMetrics { metrics ->
                                    if (message.type == MessageType.USER) {
                                        metrics.copy(totalInputTokens = metrics.totalInputTokens + estimatedTokens)
                                    } else {
                                        metrics.copy(totalOutputTokens = metrics.totalOutputTokens + estimatedTokens)
                                    }
                                }
                            }
                        }
                    }
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
        userHasSelectedSession = true // Mark that user has made a choice
        currentThread?.interrupt()
        _messages.value = emptyList()
        _currentSession.value = null
        _sessionMetrics.value = SessionMetrics()
        _queuedPrompts.value = emptyList()
        _isLoading.value = false
        // Create a new logical session and point to it
        currentLogicalId = sessionPersistence.createLogicalSession()
        
        // Session started - no need to show system message in UI
    }

    /**
     * Resume an existing session
     */
    fun resumeSession(sessionId: String) {
        userHasSelectedSession = true // Mark that user has made a choice
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
                
                // Clear current messages first to force UI refresh when switching
                _messages.value = emptyList()
                _messages.value = historicalMessages
                
                // Update session info
                _currentSession.value = SessionInfo(sessionId = sessionId, projectId = null)
                // Ensure we have a logical session and append this CLI id
                if (currentLogicalId == null) {
                    currentLogicalId = sessionPersistence.createLogicalSession()
                }
                currentLogicalId?.let { lid -> sessionPersistence.appendCliSession(lid, sessionId) }
                
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
     * Resume a logical session by concatenating all CLI sessions in its chain
     */
    fun resumeLogicalSession(logicalId: String) {
        userHasSelectedSession = true
        Thread {
            _isLoading.value = true
            try {
                logger.info("Resuming logical session: $logicalId")
                val projectPath = project.basePath ?: return@Thread
                val ls = sessionPersistence.getLogicalSession(logicalId)
                if (ls == null) {
                    logger.warn("Logical session not found: $logicalId")
                    currentLogicalId = logicalId
                    _messages.value = emptyList()
                    _currentSession.value = null
                    return@Thread
                }
                val all = mutableListOf<ClaudeStreamMessage>()
                ls.cliSessionIds.forEach { sid ->
                    try {
                        all += sessionHistoryLoader.loadSession(projectPath, sid)
                    } catch (e: Exception) {
                        logger.warn("Failed to load chain session $sid", e)
                    }
                }
                _messages.value = emptyList()
                _messages.value = all
                if (ls.cliSessionIds.isNotEmpty()) {
                    _currentSession.value = SessionInfo(sessionId = ls.cliSessionIds.last(), projectId = null)
                }
                currentLogicalId = logicalId
                if (all.isNotEmpty()) updateMetricsFromMessages(all)
                updateMetrics { it.copy(wasResumed = true) }
            } catch (e: Exception) {
                logger.error("Failed to resume logical session: $logicalId", e)
                errorList.add("Failed to resume session: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }.apply { name = "Resume-Logical-Session-Thread"; start() }
    }
    
    /**
     * Update metrics from loaded historical messages
     */
    private fun updateMetricsFromMessages(messages: List<ClaudeStreamMessage>) {
        var inputTokens = 0
        var outputTokens = 0
        var cacheReadTokens = 0
        var cacheCreationTokens = 0
        var toolsExecuted = 0
        var filesCreated = 0
        var filesModified = 0
        
        messages.forEach { message ->
            message.message?.let { msg ->
                // Count tokens
                msg.usage?.let { usage ->
                    inputTokens += usage.inputTokens
                    outputTokens += usage.outputTokens
                    cacheReadTokens += usage.cacheReadInputTokens ?: 0
                    cacheCreationTokens += usage.cacheCreationInputTokens ?: 0
                } ?: run {
                    // Fallback: estimate tokens for text messages if no usage data available
                    if (message.type == MessageType.USER || message.type == MessageType.ASSISTANT) {
                        msg.content.forEach { content ->
                            if (content.type == com.claudecodechat.models.ContentType.TEXT && content.text != null) {
                                val estimatedTokens = estimateTokens(content.text)
                                if (estimatedTokens > 0) {
                                    if (message.type == MessageType.USER) {
                                        inputTokens += estimatedTokens
                                    } else {
                                        outputTokens += estimatedTokens
                                    }
                                }
                            }
                        }
                    }
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
            cacheReadInputTokens = cacheReadTokens,
            cacheCreationInputTokens = cacheCreationTokens,
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
        return sessionPersistence.getLogicalSessions().map { it.id }
    }
    
    /**
     * Get recent sessions with details for UI display
     */
    fun getRecentSessionsWithDetails(): List<SessionDetails> {
        return sessionPersistence.getLogicalSessions().map { ls ->
            SessionDetails(
                id = ls.id,
                preview = if (ls.preview.isNotBlank()) ls.preview else "No messages",
                timestamp = formatTimestamp(ls.lastModified),
                messageCount = ls.messageCount
            )
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
     * Clear the chat and start a new session
     */
    fun clearChat() {
        logger.info("Clearing chat and starting new session")
        startNewSession()
    }
    
    /**
     * Queue a prompt for later execution
     */
    private fun queuePrompt(prompt: String, model: String, planMode: Boolean = false) {
        Thread {
            val queuedPrompt = QueuedPrompt(
                id = System.currentTimeMillis().toString(),
                prompt = prompt,
                model = model,
                planMode = planMode
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
                    sendPrompt(prompt.prompt, prompt.model, prompt.planMode)
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
    
    fun stopCurrentRequest() {
        logger.info("Stopping current request")
        currentThread?.interrupt()
        currentThread = null
        
        // Also stop any running Claude processes
        cliService.stopAllProcesses()
        
        _isLoading.value = false
        logger.info("Request stopped, loading state set to false")
    }

    override fun dispose() {
        currentThread?.interrupt()
        // File watcher will be disposed automatically as it's registered with Disposer
    }
    
    /**
     * Simple token estimation based on word count and characters
     * Rough approximation: ~0.75 tokens per word or ~4 characters per token
     */
    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        
        // Method 1: Word-based estimation (more accurate for English)
        val wordCount = text.split("\\s+").count { it.isNotBlank() }
        val wordBasedEstimate = (wordCount * 0.75).toInt()
        
        // Method 2: Character-based estimation (more universal)
        val charBasedEstimate = (text.length / 4.0).toInt()
        
        // Use the higher estimate to be conservative
        return maxOf(wordBasedEstimate, charBasedEstimate, 1)
    }
}
