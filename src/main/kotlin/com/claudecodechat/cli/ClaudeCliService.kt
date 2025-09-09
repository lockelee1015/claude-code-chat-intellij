package com.claudecodechat.cli

import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.MessageType
import com.claudecodechat.models.ErrorInfo
import com.claudecodechat.utils.JsonUtils
import com.claudecodechat.settings.ClaudeSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Service to manage Claude Code CLI processes
 */
@Service(Service.Level.PROJECT)
class ClaudeCliService(private val project: Project) {
    
    private val logger = Logger.getInstance(ClaudeCliService::class.java)
    private val processes = ConcurrentHashMap<String, ClaudeProcess>()

    companion object {
        fun getInstance(project: Project): ClaudeCliService = project.service()

        const val DEFAULT_MODEL = "sonnet"

        const val DEFAULT_OUTPUT_FORMAT = "stream-json"
        
        // Claude CLI executable name
        const val CLAUDE_BINARY = "claude"
    }
    
    data class ClaudeProcess(
        val processId: String,
        val process: Process,
        val sessionId: String?,
        val projectPath: String
    )
    
    data class ExecuteOptions(
        val prompt: String,
        val model: String = DEFAULT_MODEL,
        val sessionId: String? = null,
        val resume: Boolean = false,
        val continueSession: Boolean = false,
        val verbose: Boolean = true,
        val skipPermissions: Boolean = true,
        val permissionMode: String? = null,
        val customArgs: List<String> = emptyList()
    )
    
    
    /**
     * Execute Claude Code CLI with streaming output
     */
    fun executeClaudeCode(
        projectPath: String,
        options: ExecuteOptions,
        onMessage: (ClaudeStreamMessage) -> Unit
    ) {
        val processId = generateProcessId()
        val args = buildCommandArgs(options)
        val settings = ClaudeSettings.getInstance()
        val claudeBinary = if (settings.claudePath.isNotEmpty()) {
            settings.claudePath
        } else {
            findClaudeBinary()
        }
        
        logger.info("Executing Claude Code CLI:")
        logger.info("  Binary: $claudeBinary")
        logger.info("  Args: $args")
        logger.info("  Working directory: $projectPath")
        
        try {
            val command = mutableListOf(claudeBinary)
            command.addAll(args)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(projectPath))
            
            // Set up environment
            val env = processBuilder.environment()
            
            // Add custom environment variables from settings
            parseEnvironmentVariables(settings.environmentVariables).forEach { (key, value) ->
                env[key] = value
                logger.info("Set environment variable: $key=$value")
            }
            
            // Start the process
            val process = processBuilder.start()
            
            processes[processId] = ClaudeProcess(
                processId = processId,
                process = process,
                sessionId = options.sessionId,
                projectPath = projectPath
            )
            
            logger.info("Process started successfully with PID: ${process.pid()}")
            logger.info("Process is alive: ${process.isAlive}")
            
            // Close stdin immediately since we don't need it
            try {
                process.outputStream.close()
                logger.info("Closed process stdin")
            } catch (e: Exception) {
                logger.warn("Error closing stdin: ${e.message}")
            }
            
            // Read stdout in a separate thread with buffering disabled
            Thread {
                try {
                    logger.info("Starting stdout reader thread")
                    val inputStream = process.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream), 1) // Small buffer size
                    var lineCount = 0
                    
                    // Use a more aggressive reading approach with interrupt checks
                    while (true) {
                        // Check if thread was interrupted
                        if (Thread.currentThread().isInterrupted) {
                            logger.info("Stdout reader thread interrupted, stopping")
                            break
                        }
                        
                        val line = reader.readLine()
                        if (line == null) {
                            logger.info("Reached end of stdout stream")
                            break
                        }
                        lineCount++
                        logger.info("Stdout line #$lineCount: $line")
                        if (line.isNotBlank()) {
                            try {
                                logger.info("Raw message line: $line")
                                val message = JsonUtils.parseClaudeMessage(line)
                                logger.info("Parsed message type: ${message.type}, subtype: ${message.subtype}")
                                
                                // Debug: Check if message contains usage data
                                message.message?.usage?.let { usage ->
                                    logger.info("USAGE DATA - input: ${usage.inputTokens}, output: ${usage.outputTokens}, cacheRead: ${usage.cacheReadInputTokens}, cacheCreation: ${usage.cacheCreationInputTokens}")
                                }
                                
                                if (message.message?.usage == null) {
                                    logger.info("No usage data found in message")
                                }
                                
                                onMessage(message)
                            } catch (e: Exception) {
                                logger.warn("Failed to parse Claude message: $line", e)
                            }
                        }
                        
                        // Additional interrupt check after processing
                        if (Thread.currentThread().isInterrupted) {
                            logger.info("Stdout reader thread interrupted after processing line, stopping")
                            break
                        }
                    }
                    logger.info("Stdout reader finished, read $lineCount lines")
                } catch (e: Exception) {
                    logger.error("Error reading stdout: ${e.message}", e)
                }
            }.apply {
                name = "Claude-Stdout-Reader"
                isDaemon = true
                start()
            }
            
            // Read stderr in a separate thread
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.errorStream))
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) {
                                logger.warn("Claude CLI stderr: $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error reading stderr", e)
                }
            }.apply {
                name = "Claude-Stderr-Reader"
                start()
            }
            
            // Wait for process in a separate thread (no timeout)
            Thread {
                try {
                    logger.info("Waiting for process to complete...")
                    
                    // Wait for process without timeout
                    val exitCode = process.waitFor()
                    logger.info("Claude process exited with code: $exitCode (pid: $processId)")
                    
                    if (exitCode != 0) {
                        onMessage(ClaudeStreamMessage(
                            type = MessageType.ERROR,
                            error = ErrorInfo(
                                type = "PROCESS_EXIT_ERROR",
                                message = "Claude process exited with code: $exitCode",
                                code = exitCode.toString()
                            )
                        ))
                    } else {
                        // Send completion signal for successful exit
                        onMessage(ClaudeStreamMessage(
                            type = MessageType.SYSTEM,
                            subtype = "complete"
                        ))
                    }
                    processes.remove(processId)
                } catch (e: Exception) {
                    logger.error("Error waiting for process", e)
                }
            }.apply {
                name = "Claude-Process-Waiter"
                start()
            }
            
        } catch (e: Exception) {
            val errorMsg = "Failed to start Claude Code CLI process: ${e.message}"
            logger.error(errorMsg, e)
            onMessage(ClaudeStreamMessage(
                type = MessageType.ERROR,
                error = ErrorInfo(
                    type = "PROCESS_START_ERROR",
                    message = errorMsg
                )
            ))
        }
    }
    
    /**
     * Stop a running Claude process
     */
    fun stopProcess(processId: String) {
        processes.remove(processId)?.let { claudeProcess ->
            try {
                claudeProcess.process.destroyForcibly()
                logger.info("Stopped Claude process: $processId")
            } catch (e: Exception) {
                logger.error("Error stopping Claude process", e)
            }
        }
    }
    
    /**
     * Stop all running processes
     */
    fun stopAllProcesses() {
        processes.keys.toList().forEach { stopProcess(it) }
    }
    
    /**
     * Check if a session is active
     */
    fun isSessionActive(sessionId: String): Boolean {
        return processes.values.any { it.sessionId == sessionId }
    }
    
    /**
     * Get active sessions for a project path
     */
    fun getActiveSessions(projectPath: String): List<String> {
        return processes.values
            .filter { it.projectPath == projectPath }
            .mapNotNull { it.sessionId }
    }
    
    private fun buildCommandArgs(options: ExecuteOptions): List<String> {
        val args = mutableListOf<String>()
        
        // Add model only if specified (not empty)
        if (options.model.isNotEmpty()) {
            args.add("--model")
            args.add(options.model)
        }
        
        // For non-interactive streaming output
        args.add("--print")
        args.add("--output-format")
        args.add(DEFAULT_OUTPUT_FORMAT)
        
        // Verbose is required for stream-json format
        args.add("--verbose")
        
        // Session management
        when {
            options.sessionId != null -> {
                // 仅使用 --resume <id>（当前 CLI 不允许与 --session-id 同时使用）
                val sid = options.sessionId
                logger.info("Using --resume with sessionId: $sid")
                args.add("--resume")
                // 显式传入会话 ID，避免交互选择
                args.add(sid)
            }
            options.continueSession -> {
                // 如果没有 sessionId 但要继续会话，使用 -c 继续最近的 session
                logger.info("Using -c to continue most recent session")
                args.add("-c")
            }
            else -> {
                logger.info("No session management flags - will create new session")
            }
        }
        
        // Permission mode (plan mode)
        if (options.permissionMode != null) {
            args.add("--permission-mode")
            args.add(options.permissionMode)
            logger.info("Using permission mode: ${options.permissionMode}")
        }
        
        // Skip permissions is important for non-interactive mode
        if (options.skipPermissions) {
            args.add("--dangerously-skip-permissions")
        }
        
        // Add prompt last (as a positional argument)
        args.add(options.prompt)
        
        // Custom args
        args.addAll(options.customArgs)
        
        return args
    }
    
    private fun findClaudeBinary(): String {
        // First try to use 'which' command to find claude
        try {
            val process = ProcessBuilder("which", CLAUDE_BINARY).start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val path = process.inputStream.bufferedReader().readText().trim()
                if (path.isNotEmpty()) {
                    logger.info("Found Claude binary using 'which': $path")
                    return path
                }
            }
        } catch (e: Exception) {
            logger.debug("'which' command failed, trying alternative methods: ${e.message}")
        }
        
        // Fallback: try to find in PATH manually
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        logger.info("Searching for Claude binary in PATH: $pathDirs")
        
        for (dir in pathDirs) {
            val claudeFile = File(dir, CLAUDE_BINARY)
            if (claudeFile.exists() && claudeFile.canExecute()) {
                logger.info("Found Claude binary at: ${claudeFile.absolutePath}")
                return claudeFile.absolutePath
            }
        }
        
        logger.warn("Claude binary not found, using fallback")
        // Fallback to just the binary name and hope it's in PATH
        return CLAUDE_BINARY
    }
    
    private fun generateProcessId(): String {
        return "claude-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"
    }
    
    /**
     * Parse environment variables from a string in the format "KEY=VALUE" (one per line)
     */
    private fun parseEnvironmentVariables(envVarsText: String): Map<String, String> {
        if (envVarsText.isBlank()) return emptyMap()
        
        return envVarsText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0].trim() to parts[1].trim()
                } else {
                    logger.warn("Invalid environment variable format: $line")
                    null
                }
            }
            .toMap()
    }
    
    fun dispose() {
        stopAllProcesses()
    }
}
