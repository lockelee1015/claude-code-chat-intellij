package com.claudecodechat.cli

import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.MessageType
import com.claudecodechat.models.ErrorInfo
import com.claudecodechat.utils.JsonUtils
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
    private val messageChannel = Channel<ClaudeStreamMessage>(Channel.UNLIMITED)
    
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
        val claudeBinary = findClaudeBinary()
        
        logger.info("Executing Claude Code CLI:")
        logger.info("  Binary: $claudeBinary")
        logger.info("  Args: $args")
        logger.info("  Working directory: $projectPath")
        
        try {
            val command = mutableListOf(claudeBinary)
            command.addAll(args)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(projectPath))
            
            // Set up environment with PATH
            val env = processBuilder.environment()
            val currentPath = env["PATH"] ?: ""
            val nodePath = "${System.getProperty("user.home")}/.nvm/versions/node/v22.17.0/bin"
            if (!currentPath.contains(nodePath)) {
                env["PATH"] = "$nodePath:$currentPath"
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
                    
                    // Use a more aggressive reading approach
                    while (true) {
                        val line = reader.readLine()
                        if (line == null) {
                            logger.info("Reached end of stdout stream")
                            break
                        }
                        lineCount++
                        logger.info("Stdout line #$lineCount: $line")
                        if (line.isNotBlank()) {
                            try {
                                val message = JsonUtils.parseClaudeMessage(line)
                                logger.info("Parsed message type: ${message.type}")
                                onMessage(message)
                            } catch (e: Exception) {
                                logger.warn("Failed to parse Claude message: $line", e)
                            }
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
            options.resume && options.sessionId != null -> {
                args.add("--resume")
                args.add(options.sessionId)
            }
            options.continueSession -> {
                args.add("-c")
            }
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
        // Try to find Claude binary in PATH
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        
        logger.info("Searching for Claude binary in PATH: $pathDirs")
        
        for (dir in pathDirs) {
            val claudeFile = File(dir, CLAUDE_BINARY)
            if (claudeFile.exists() && claudeFile.canExecute()) {
                logger.info("Found Claude binary at: ${claudeFile.absolutePath}")
                return claudeFile.absolutePath
            }
        }
        
        // Try common installation locations including nvm
        val commonPaths = listOf(
            "/usr/local/bin/claude",
            "/usr/bin/claude",
            "${System.getProperty("user.home")}/.local/bin/claude",
            "${System.getProperty("user.home")}/bin/claude",
            "${System.getProperty("user.home")}/.nvm/versions/node/v22.17.0/bin/claude"
        )
        
        for (path in commonPaths) {
            val claudeFile = File(path)
            if (claudeFile.exists() && claudeFile.canExecute()) {
                logger.info("Found Claude binary at: ${claudeFile.absolutePath}")
                return claudeFile.absolutePath
            }
        }
        
        logger.warn("Claude binary not found in common locations, using fallback")
        // Fallback to just the binary name and hope it's in PATH
        return CLAUDE_BINARY
    }
    
    private fun generateProcessId(): String {
        return "claude-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"
    }
    
    fun dispose() {
        stopAllProcesses()
    }
}