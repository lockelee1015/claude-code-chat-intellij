package com.claudecodechat.completion

import com.intellij.openapi.project.Project
import java.io.File

/**
 * Registry for slash commands
 */
class SlashCommandRegistry(private val project: Project) {
    
    /**
     * Get all built-in slash commands
     */
    fun getBuiltInCommands(): List<CompletionItem.SlashCommand> {
        return listOf(
            CompletionItem.SlashCommand(
                name = "help",
                description = "Get usage help",
                icon = "❓"
            ),
            CompletionItem.SlashCommand(
                name = "clear",
                description = "Clear conversation history",
                icon = "🧹"
            ),
            CompletionItem.SlashCommand(
                name = "model",
                description = "Select or change the AI model",
                argumentHint = "[model-name]",
                icon = "🤖"
            ),
            CompletionItem.SlashCommand(
                name = "cost",
                description = "Show token usage statistics",
                icon = "💰"
            ),
            CompletionItem.SlashCommand(
                name = "config",
                description = "View/modify configuration",
                icon = "⚙️"
            ),
            CompletionItem.SlashCommand(
                name = "review",
                description = "Request code review",
                argumentHint = "[file-or-directory]",
                icon = "👁️"
            ),
            CompletionItem.SlashCommand(
                name = "init",
                description = "Initialize project with CLAUDE.md guide",
                icon = "🚀"
            ),
            CompletionItem.SlashCommand(
                name = "memory",
                description = "Edit CLAUDE.md memory files",
                icon = "🧠"
            ),
            CompletionItem.SlashCommand(
                name = "status",
                description = "View account and system statuses",
                icon = "📊"
            ),
            CompletionItem.SlashCommand(
                name = "vim",
                description = "Enter vim mode for alternating insert and command modes",
                icon = "📝"
            ),
            CompletionItem.SlashCommand(
                name = "compact",
                description = "Compact conversation with optional focus instructions",
                argumentHint = "[instructions]",
                icon = "📦"
            ),
            CompletionItem.SlashCommand(
                name = "add-dir",
                description = "Add additional working directories",
                argumentHint = "[directory-path]",
                icon = "📁"
            ),
            CompletionItem.SlashCommand(
                name = "agents",
                description = "Manage custom AI subagents for specialized tasks",
                icon = "🤝"
            ),
            CompletionItem.SlashCommand(
                name = "bug",
                description = "Report bugs (sends conversation to Anthropic)",
                icon = "🐛"
            ),
            CompletionItem.SlashCommand(
                name = "doctor",
                description = "Checks the health of your Claude Code installation",
                icon = "🏥"
            ),
            CompletionItem.SlashCommand(
                name = "login",
                description = "Switch Anthropic accounts",
                icon = "🔑"
            ),
            CompletionItem.SlashCommand(
                name = "logout",
                description = "Sign out from your Anthropic account",
                icon = "🚪"
            ),
            CompletionItem.SlashCommand(
                name = "mcp",
                description = "Manage MCP server connections and OAuth authentication",
                icon = "🔌"
            ),
            CompletionItem.SlashCommand(
                name = "permissions",
                description = "View or update permissions",
                icon = "🔒"
            ),
            CompletionItem.SlashCommand(
                name = "pr_comments",
                description = "View pull request comments",
                icon = "💬"
            ),
            CompletionItem.SlashCommand(
                name = "terminal-setup",
                description = "Install Shift+Enter key binding for newlines",
                icon = "⌨️"
            )
        )
    }
    
    /**
     * Get project-specific commands from .claude/commands/
     */
    fun getProjectCommands(): List<CompletionItem.SlashCommand> {
        val projectPath = project.basePath ?: return emptyList()
        val commandsDir = File(projectPath, ".claude/commands")
        
        if (!commandsDir.exists() || !commandsDir.isDirectory) {
            return emptyList()
        }
        
        return scanCommandDirectory(commandsDir, CompletionSource.PROJECT)
    }
    
    /**
     * Get user-level commands from ~/.claude/commands/
     */
    fun getUserCommands(): List<CompletionItem.SlashCommand> {
        val userHome = System.getProperty("user.home")
        val commandsDir = File(userHome, ".claude/commands")
        
        if (!commandsDir.exists() || !commandsDir.isDirectory) {
            return emptyList()
        }
        
        return scanCommandDirectory(commandsDir, CompletionSource.USER)
    }
    
    /**
     * Get all available commands
     */
    fun getAllCommands(): List<CompletionItem.SlashCommand> {
        val commands = mutableListOf<CompletionItem.SlashCommand>()
        
        // Add built-in commands
        commands.addAll(getBuiltInCommands())
        
        // Add project commands
        commands.addAll(getProjectCommands())
        
        // Add user commands
        commands.addAll(getUserCommands())
        
        // TODO: Add MCP commands when MCP integration is available
        
        return commands
    }
    
    /**
     * Filter commands by query
     */
    fun filterCommands(query: String): List<CompletionItem.SlashCommand> {
        if (query.isEmpty()) {
            return getAllCommands()
        }
        
        val lowerQuery = query.lowercase()
        return getAllCommands().filter { command ->
            command.name.lowercase().contains(lowerQuery) ||
            command.description.lowercase().contains(lowerQuery)
        }.sortedBy { command ->
            // Prioritize commands that start with the query
            if (command.name.lowercase().startsWith(lowerQuery)) 0 else 1
        }
    }
    
    /**
     * Scan a directory for command files
     */
    private fun scanCommandDirectory(
        dir: File, 
        source: CompletionSource,
        parentPath: String = ""
    ): List<CompletionItem.SlashCommand> {
        val commands = mutableListOf<CompletionItem.SlashCommand>()
        
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> {
                    // Recursively scan subdirectories
                    val subPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
                    commands.addAll(scanCommandDirectory(file, source, subPath))
                }
                file.extension == "md" -> {
                    // Parse command from markdown file
                    val commandName = file.nameWithoutExtension
                    val description = parseCommandDescription(file) ?: "Custom command"
                    val argumentHint = parseArgumentHint(file)
                    
                    commands.add(CompletionItem.SlashCommand(
                        name = commandName,
                        description = if (parentPath.isNotEmpty()) {
                            "$description (${source.name.lowercase()}:$parentPath)"
                        } else {
                            "$description (${source.name.lowercase()})"
                        },
                        argumentHint = argumentHint,
                        source = source,
                        icon = "📜"
                    ))
                }
            }
        }
        
        return commands
    }
    
    /**
     * Parse command description from frontmatter or first line
     */
    private fun parseCommandDescription(file: File): String? {
        try {
            val lines = file.readLines()
            
            // Check for frontmatter
            if (lines.firstOrNull() == "---") {
                val frontmatterEnd = lines.drop(1).indexOfFirst { it == "---" }
                if (frontmatterEnd > 0) {
                    val frontmatter = lines.subList(1, frontmatterEnd + 1)
                    val descLine = frontmatter.find { it.startsWith("description:") }
                    if (descLine != null) {
                        return descLine.substringAfter("description:").trim().trim('"', '\'')
                    }
                }
            }
            
            // Use first non-empty line as description
            return lines.find { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }
                ?.take(100) // Limit description length
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Parse argument hint from frontmatter
     */
    private fun parseArgumentHint(file: File): String? {
        try {
            val lines = file.readLines()
            
            // Check for frontmatter
            if (lines.firstOrNull() == "---") {
                val frontmatterEnd = lines.drop(1).indexOfFirst { it == "---" }
                if (frontmatterEnd > 0) {
                    val frontmatter = lines.subList(1, frontmatterEnd + 1)
                    val hintLine = frontmatter.find { it.startsWith("argument-hint:") }
                    if (hintLine != null) {
                        return hintLine.substringAfter("argument-hint:").trim().trim('"', '\'')
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return null
    }
}