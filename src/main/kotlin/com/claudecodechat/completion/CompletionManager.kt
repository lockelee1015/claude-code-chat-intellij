package com.claudecodechat.completion

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.util.concurrency.NonUrgentExecutor

/**
 * Manages completion functionality including parsing input and providing suggestions
 */
class CompletionManager(private val project: Project) {
    
    private val slashCommandRegistry = SlashCommandRegistry(project)
    private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentSearchJob: Job? = null
    
    private val _completionState = MutableStateFlow(CompletionState())
    val completionState: StateFlow<CompletionState> = _completionState.asStateFlow()
    
    /**
     * Update completion based on input text and cursor position
     */
    fun updateCompletion(text: String, cursorPosition: Int) {
        // Cancel any ongoing search
        currentSearchJob?.cancel()
        
        val trigger = findTrigger(text, cursorPosition)
        
        if (trigger == null) {
            hideCompletion()
            return
        }
        
        val (triggerType, triggerPos, query) = trigger
        
        when (triggerType) {
            CompletionTrigger.SLASH -> {
                // Slash commands are fast, do synchronously
                val items = getSlashCompletions(query)
                _completionState.value = CompletionState(
                    isShowing = items.isNotEmpty(),
                    items = items,
                    selectedIndex = 0,
                    trigger = triggerType,
                    query = query,
                    triggerPosition = triggerPos
                )
            }
            CompletionTrigger.AT -> {
                // File completions are slow, do asynchronously
                currentSearchJob = searchScope.launch {
                    val items = getFileCompletionsAsync(query)
                    if (!isActive) return@launch // Check if cancelled
                    
                    _completionState.value = CompletionState(
                        isShowing = items.isNotEmpty(),
                        items = items,
                        selectedIndex = 0,
                        trigger = triggerType,
                        query = query,
                        triggerPosition = triggerPos
                    )
                }
            }
            CompletionTrigger.MANUAL -> {
                // Mixed completions, handle async
                currentSearchJob = searchScope.launch {
                    val slashItems = getSlashCompletions(query)
                    val fileItems = getFileCompletionsAsync(query)
                    if (!isActive) return@launch
                    
                    val allItems = (slashItems + fileItems).take(15)
                    _completionState.value = CompletionState(
                        isShowing = allItems.isNotEmpty(),
                        items = allItems,
                        selectedIndex = 0,
                        trigger = triggerType,
                        query = query,
                        triggerPosition = triggerPos
                    )
                }
            }
        }
    }
    
    /**
     * Hide completion popup
     */
    fun hideCompletion() {
        currentSearchJob?.cancel()
        _completionState.value = CompletionState()
    }
    
    /**
     * Select next item in completion list
     */
    fun selectNext() {
        _completionState.value = _completionState.value.selectNext()
    }
    
    /**
     * Select previous item in completion list
     */
    fun selectPrevious() {
        _completionState.value = _completionState.value.selectPrevious()
    }
    
    /**
     * Accept the currently selected completion
     */
    fun acceptCompletion(originalText: String, cursorPosition: Int): CompletionResult? {
        val state = _completionState.value
        val selectedItem = state.selectedItem ?: return null
        
        val beforeTrigger = originalText.substring(0, state.triggerPosition)
        val afterCursor = originalText.substring(cursorPosition)
        
        val replacement = when (selectedItem) {
            is CompletionItem.SlashCommand -> {
                val command = selectedItem.fullCommand
                if (selectedItem.argumentHint != null) {
                    "$command "
                } else {
                    command
                }
            }
            is CompletionItem.FileReference -> selectedItem.fullReference
        }
        
        val newText = beforeTrigger + replacement + afterCursor
        val newCursorPosition = beforeTrigger.length + replacement.length
        
        hideCompletion()
        
        return CompletionResult(
            originalText = originalText,
            newText = newText,
            newCursorPosition = newCursorPosition
        )
    }
    
    /**
     * Find completion trigger in text at cursor position
     */
    private fun findTrigger(text: String, cursorPosition: Int): Triple<CompletionTrigger, Int, String>? {
        if (cursorPosition <= 0) return null
        
        // Look backward from cursor to find trigger
        var pos = cursorPosition - 1
        while (pos >= 0) {
            val char = text[pos]
            
            when (char) {
                '/' -> {
                    // Check if this is at word boundary (start or after whitespace)
                    if (pos == 0 || text[pos - 1].isWhitespace()) {
                        val query = text.substring(pos + 1, cursorPosition)
                        return Triple(CompletionTrigger.SLASH, pos, query)
                    }
                }
                '@' -> {
                    // Check if this is at word boundary (start or after whitespace)
                    if (pos == 0 || text[pos - 1].isWhitespace()) {
                        val query = text.substring(pos + 1, cursorPosition)
                        return Triple(CompletionTrigger.AT, pos, query)
                    }
                }
            }
            
            // Stop at whitespace
            if (char.isWhitespace()) {
                break
            }
            
            pos--
        }
        
        return null
    }
    
    /**
     * Get slash command completions
     */
    private fun getSlashCompletions(query: String): List<CompletionItem> {
        return slashCommandRegistry.filterCommands(query).take(10)
    }
    
    /**
     * Get file completions for @ mentions (async version)
     */
    private suspend fun getFileCompletionsAsync(query: String): List<CompletionItem> {
        if (query.isEmpty()) {
            return getRecentFiles().take(10)
        }
        
        return withContext(Dispatchers.Default) {
            val files = mutableSetOf<VirtualFile>()
            
            // Use ReadAction for thread safety
            ReadAction.compute<Unit, Exception> {
                val scope = GlobalSearchScope.projectScope(project)
                
                // Search by filename and path
                FilenameIndex.processAllFileNames({ filename ->
                    // Cancel if coroutine is cancelled
                    if (!isActive) return@processAllFileNames false
                    
                    // Support glob-style matching
                    if (matchesGlobPattern(filename, query) || 
                        filename.contains(query, ignoreCase = true)) {
                        FilenameIndex.getVirtualFilesByName(filename, scope).forEach { file ->
                            // Also check if the relative path matches
                            val relativePath = getRelativePath(file)
                            if (relativePath != null && 
                                (relativePath.contains(query, ignoreCase = true) ||
                                 matchesGlobPattern(relativePath, query))) {
                                files.add(file)
                            }
                        }
                    }
                    true
                }, scope, null)
            }
            
            if (!isActive) return@withContext emptyList()
            
            files.take(20).mapNotNull { file ->
                val relativePath = getRelativePath(file)
                if (relativePath != null) {
                    CompletionItem.FileReference(
                        path = file.path,
                        fileName = file.name,
                        relativePath = relativePath,
                        fileType = file.extension,
                        icon = getFileIcon(file.extension)
                    )
                } else null
            }.sortedWith(compareBy<CompletionItem.FileReference> { item ->
                // Prioritize path matches over filename matches
                when {
                    item.relativePath.equals(query, ignoreCase = true) -> 0
                    item.relativePath.startsWith(query, ignoreCase = true) -> 1
                    item.fileName.equals(query, ignoreCase = true) -> 2
                    item.fileName.startsWith(query, ignoreCase = true) -> 3
                    item.relativePath.contains("/$query", ignoreCase = true) -> 4
                    else -> 5
                }
            }.thenBy { it.relativePath.length })
        }
    }
    
    /**
     * Check if a string matches a glob-like pattern
     */
    private fun matchesGlobPattern(text: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return true
        
        // Simple glob matching - support * and ? wildcards
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        
        return try {
            text.matches(Regex(regexPattern, RegexOption.IGNORE_CASE))
        } catch (e: Exception) {
            // If regex fails, fall back to simple contains
            text.contains(pattern, ignoreCase = true)
        }
    }
    
    /**
     * Get recent files (placeholder implementation)
     */
    private fun getRecentFiles(): List<CompletionItem.FileReference> {
        // TODO: Implement recent files tracking
        return emptyList()
    }
    
    /**
     * Get relative path for a file
     */
    private fun getRelativePath(file: VirtualFile): String? {
        val basePath = project.basePath ?: return null
        val projectDir = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(java.io.File(basePath), false) ?: return null
        return com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(file, projectDir)
    }
    
    /**
     * Get icon for file type
     */
    private fun getFileIcon(extension: String?): String {
        return when (extension?.lowercase()) {
            "kt" -> "🟪"
            "java" -> "☕"
            "js", "jsx" -> "🟨"
            "ts", "tsx" -> "🔷"
            "py" -> "🐍"
            "md" -> "📝"
            "json" -> "📄"
            "xml" -> "📋"
            "yml", "yaml" -> "📊"
            "txt" -> "📄"
            "html" -> "🌐"
            "css" -> "🎨"
            "sql" -> "🗃️"
            else -> "📄"
        }
    }
    
    /**
     * Clean up resources
     */
    fun dispose() {
        currentSearchJob?.cancel()
        searchScope.cancel()
    }
}