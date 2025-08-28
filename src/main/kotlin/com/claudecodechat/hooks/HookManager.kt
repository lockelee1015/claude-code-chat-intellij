package com.claudecodechat.hooks

import com.claudecodechat.utils.JsonUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manager for Hook execution and configuration loading
 */
class HookManager(private val project: Project) {
    
    private val logger = Logger.getInstance(HookManager::class.java)
    
    companion object {
        const val LOCAL_HOOKS_FILE = ".claude/.hooks.json"
        const val PROJECT_HOOKS_FILE = "CLAUDE.project.json"
        const val USER_HOOKS_FILE = ".claude/settings.json"
    }
    
    /**
     * Load and merge hook configurations from all levels
     */
    fun loadHookConfiguration(): HooksConfiguration {
        val projectPath = project.basePath ?: return HooksConfiguration()
        
        val userHooks = loadUserHooks()
        val projectHooks = loadProjectHooks(projectPath)
        val localHooks = loadLocalHooks(projectPath)
        
        return mergeHookConfigurations(userHooks, projectHooks, localHooks)
    }
    
    /**
     * Execute PreToolUse hooks
     */
    suspend fun executePreToolUseHooks(
        context: HookContext,
        configuration: HooksConfiguration = loadHookConfiguration()
    ): HookResult {
        val hooks = configuration.PreToolUse ?: return HookResult(success = true)
        
        for (matcher in hooks) {
            if (shouldExecuteHook(matcher.matcher, context.toolName)) {
                for (hookCommand in matcher.hooks) {
                    val result = executeHook(hookCommand, context)
                    if (!result.success || result.shouldBlock) {
                        return result
                    }
                }
            }
        }
        
        return HookResult(success = true)
    }
    
    /**
     * Execute PostToolUse hooks
     */
    suspend fun executePostToolUseHooks(
        context: HookContext,
        configuration: HooksConfiguration = loadHookConfiguration()
    ): HookResult {
        val hooks = configuration.PostToolUse ?: return HookResult(success = true)
        
        for (matcher in hooks) {
            if (shouldExecuteHook(matcher.matcher, context.toolName)) {
                for (hookCommand in matcher.hooks) {
                    val result = executeHook(hookCommand, context)
                    // Post hooks don't block, but we still log errors
                    if (!result.success) {
                        logger.warn("PostToolUse hook failed: ${result.error}")
                    }
                }
            }
        }
        
        return HookResult(success = true)
    }
    
    /**
     * Execute a single hook command
     */
    private suspend fun executeHook(
        hook: HookCommand,
        context: HookContext
    ): HookResult {
        return try {
            val command = expandHookCommand(hook.command, context)
            val timeout = hook.timeout ?: 30 // Default 30 seconds
            
            logger.debug("Executing hook: $command")
            
            val processBuilder = ProcessBuilder("/bin/sh", "-c", command)
            processBuilder.directory(File(context.projectPath))
            processBuilder.environment().apply {
                put("TOOL_NAME", context.toolName)
                put("SESSION_ID", context.sessionId ?: "")
                put("PROJECT_PATH", context.projectPath)
                context.toolInput?.let {
                    put("TOOL_INPUT", JsonUtils.toJson(it))
                }
            }
            
            val process = processBuilder.start()
            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            
            if (!completed) {
                process.destroyForcibly()
                return HookResult(
                    success = false,
                    error = "Hook timed out after $timeout seconds",
                    shouldBlock = true
                )
            }
            
            val exitCode = process.exitValue()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            // Exit code 2 means block the operation
            HookResult(
                success = exitCode == 0,
                output = output,
                error = if (exitCode != 0) error else null,
                exitCode = exitCode,
                shouldBlock = exitCode == 2
            )
        } catch (e: Exception) {
            logger.error("Error executing hook", e)
            HookResult(
                success = false,
                error = e.message,
                shouldBlock = false
            )
        }
    }
    
    /**
     * Check if a hook should be executed based on the matcher
     */
    private fun shouldExecuteHook(matcher: String?, toolName: String): Boolean {
        if (matcher == null) return true // No matcher means always execute
        
        return try {
            // Support regex matching
            toolName.matches(Regex(matcher))
        } catch (e: Exception) {
            // Fall back to simple string matching
            toolName.contains(matcher, ignoreCase = true)
        }
    }
    
    /**
     * Expand variables in hook command
     */
    private fun expandHookCommand(command: String, context: HookContext): String {
        var expanded = command
        expanded = expanded.replace("\${TOOL_NAME}", context.toolName)
        expanded = expanded.replace("\${SESSION_ID}", context.sessionId ?: "")
        expanded = expanded.replace("\${PROJECT_PATH}", context.projectPath)
        expanded = expanded.replace("\${TIMESTAMP}", context.timestamp.toString())
        return expanded
    }
    
    /**
     * Load user-level hooks
     */
    private fun loadUserHooks(): HooksConfiguration? {
        val userHome = System.getProperty("user.home")
        val hooksFile = File(userHome, USER_HOOKS_FILE)
        return loadHooksFromFile(hooksFile)
    }
    
    /**
     * Load project-level hooks
     */
    private fun loadProjectHooks(projectPath: String): HooksConfiguration? {
        val hooksFile = File(projectPath, PROJECT_HOOKS_FILE)
        return loadHooksFromFile(hooksFile)
    }
    
    /**
     * Load local-level hooks
     */
    private fun loadLocalHooks(projectPath: String): HooksConfiguration? {
        val hooksFile = File(projectPath, LOCAL_HOOKS_FILE)
        return loadHooksFromFile(hooksFile)
    }
    
    /**
     * Load hooks configuration from a file
     */
    private fun loadHooksFromFile(file: File): HooksConfiguration? {
        if (!file.exists()) return null
        
        return try {
            val content = file.readText()
            JsonUtils.fromJson<HooksConfiguration>(content)
        } catch (e: Exception) {
            logger.warn("Failed to load hooks from ${file.path}", e)
            null
        }
    }
    
    /**
     * Merge hook configurations with priority: Local > Project > User
     */
    private fun mergeHookConfigurations(
        user: HooksConfiguration?,
        project: HooksConfiguration?,
        local: HooksConfiguration?
    ): HooksConfiguration {
        // Start with empty configuration
        val merged = HooksConfiguration()
        
        // Merge in order of priority (lowest to highest)
        val configs = listOfNotNull(user, project, local)
        
        return HooksConfiguration(
            PreToolUse = mergeHookMatchers(configs.mapNotNull { it.PreToolUse }),
            PostToolUse = mergeHookMatchers(configs.mapNotNull { it.PostToolUse }),
            Notification = mergeHookCommands(configs.mapNotNull { it.Notification }),
            Stop = mergeHookCommands(configs.mapNotNull { it.Stop }),
            SubagentStop = mergeHookCommands(configs.mapNotNull { it.SubagentStop })
        )
    }
    
    private fun mergeHookMatchers(lists: List<List<HookMatcher>>): List<HookMatcher> {
        return lists.flatten()
    }
    
    private fun mergeHookCommands(lists: List<List<HookCommand>>): List<HookCommand> {
        return lists.flatten()
    }
}