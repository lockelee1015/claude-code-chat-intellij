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
import com.intellij.openapi.fileTypes.FileTypeManager
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
        if (text.isEmpty() || cursorPosition <= 0 || cursorPosition > text.length) return null
        
        // Look backward from cursor to find trigger, but with more flexible rules
        var pos = minOf(cursorPosition - 1, text.length - 1)
        var searchLimit = maxOf(0, cursorPosition - 50) // Only search back 50 characters max
        
        while (pos >= searchLimit && pos < text.length) {
            val char = text[pos]
            
            when (char) {
                '/' -> {
                    // For slash commands, check if this is valid trigger position
                    if (isValidSlashPosition(text, pos)) {
                        val endPos = minOf(cursorPosition, text.length)
                        val query = text.substring(pos + 1, endPos)
                        // Allow query to contain spaces for slash commands
                        return Triple(CompletionTrigger.SLASH, pos, query)
                    }
                }
                '@' -> {
                    // Check if this is at word boundary (start or after whitespace)
                    if (pos == 0 || (pos > 0 && text[pos - 1].isWhitespace())) {
                        val endPos = minOf(cursorPosition, text.length)
                        val query = text.substring(pos + 1, endPos)
                        // For file references, stop at first space in query
                        val spaceIndex = query.indexOf(' ')
                        val cleanQuery = if (spaceIndex >= 0) query.substring(0, spaceIndex) else query
                        return Triple(CompletionTrigger.AT, pos, cleanQuery)
                    }
                }
                '\n' -> {
                    // Stop at line breaks for performance
                    break
                }
            }
            
            pos--
        }
        
        return null
    }
    
    /**
     * Check if the '/' at the given position is a valid slash command trigger
     */
    private fun isValidSlashPosition(text: String, slashPos: Int): Boolean {
        // Must be at start or after whitespace
        if (slashPos != 0 && !text[slashPos - 1].isWhitespace()) {
            return false
        }
        
        // If at start of text, it's valid
        if (slashPos == 0) {
            return true
        }
        
        // Look backward from the slash to see if this is the start of a new line/command
        // Find the beginning of this line
        var lineStart = slashPos - 1
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        
        // Check if there's already a slash command on this line before this position
        val lineContent = text.substring(lineStart, slashPos).trim()
        
        // If the line content before this slash contains another slash command, reject
        return !lineContent.contains('/')
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
            // Use ReadAction for thread safety
            val files = ReadAction.compute<Set<VirtualFile>, Exception> {
                val fileSet = mutableSetOf<VirtualFile>()
                val scope = GlobalSearchScope.projectScope(project)
                
                // Search by filename and path
                FilenameIndex.processAllFileNames({ filename ->
                    // Support glob-style matching
                    if (matchesGlobPattern(filename, query) || 
                        filename.contains(query, ignoreCase = true)) {
                        FilenameIndex.getVirtualFilesByName(filename, scope).forEach { file ->
                            // Also check if the relative path matches
                            val relativePath = getRelativePath(file)
                            if (relativePath != null && 
                                (relativePath.contains(query, ignoreCase = true) ||
                                 matchesGlobPattern(relativePath, query))) {
                                fileSet.add(file)
                            }
                        }
                    }
                    true
                }, scope, null)
                
                fileSet
            }
            
            if (!isActive) return@withContext emptyList()
            
            files.take(20).mapNotNull { file ->
                ReadAction.compute<CompletionItem.FileReference?, Exception> {
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
                }
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
     * Get recent files from editor and project
     */
    private suspend fun getRecentFiles(): List<CompletionItem.FileReference> {
        return withContext(Dispatchers.Default) {
            try {
                ReadAction.compute<List<CompletionItem.FileReference>, Exception> {
                    val files = mutableSetOf<VirtualFile>()
                    
                    // Get currently open files
                    val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    files.addAll(fileEditorManager.openFiles)
                    
                    // Get some project files if we don't have many open files
                    if (files.size < 10) {
                        val scope = GlobalSearchScope.projectScope(project)
                        var count = 0
                        FilenameIndex.processAllFileNames({ filename ->
                            if (count >= 20) return@processAllFileNames false
                            
                            FilenameIndex.getVirtualFilesByName(filename, scope).forEach { file ->
                                if (count < 20 && (file.extension in setOf("kt", "java", "js", "ts", "py", "md", "txt", "json", "xml", "html", "css"))) {
                                    files.add(file)
                                    count++
                                }
                            }
                            true
                        }, scope, null)
                    }
                    
                    files.mapNotNull { file ->
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
                    }.take(15)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
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
     * Get icon identifier for file type
     */
    private fun getFileIcon(extension: String?): String? {
        if (extension == null) return null
        
        return when (extension.lowercase()) {
            "kt" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "md" -> "markdown"
            "json" -> "json"
            "xml" -> "xml"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss", "sass" -> "sass"
            "yml", "yaml" -> "yaml"
            "txt" -> "text"
            "sql" -> "sql"
            "sh" -> "shell"
            "gradle" -> "gradle"
            "properties" -> "properties"
            else -> "file"
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