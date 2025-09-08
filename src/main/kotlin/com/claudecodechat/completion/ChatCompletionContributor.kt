package com.claudecodechat.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.icons.AllIcons

/**
 * Completion contributor for Claude chat input
 * Provides slash commands (/) and file references (@) completion
 */
class ChatCompletionContributor : CompletionContributor() {

    private var project: Project? = null
    private var slashCommandRegistry: SlashCommandRegistry? = null

    init {
        // Register for markdown files which have excellent PSI support
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            ChatCompletionProvider()
        )
    }
    
    fun initialize(project: Project) {
        this.project = project
        this.slashCommandRegistry = SlashCommandRegistry(project)
    }
    
    private inner class ChatCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            // 动态初始化项目相关组件
            if (project == null) {
                project = parameters.originalFile.project
                slashCommandRegistry = SlashCommandRegistry(project!!)
            }

            parameters.originalFile.virtualFile
            val editor = parameters.editor
            val document = editor.document
            val caretOffset = editor.caretModel.offset

            // 获取当前输入的触发字符
            val triggerResult = findCompletionTrigger(document.text, caretOffset)
            val trigger = if (triggerResult != null) {
                when (triggerResult.first) {
                    CompletionTrigger.SLASH -> "/${triggerResult.third}"
                    CompletionTrigger.AT -> "@${triggerResult.third}"
                    CompletionTrigger.MANUAL -> ""
                }
            } else ""

            // 检查是否有无效的触发字符（用户输入了 / 或 @ 但不满足条件）
            val hasInvalidTrigger = hasInvalidTriggerChar(document.text, caretOffset)

            when {
                trigger.startsWith("/") -> {
                    addSlashCompletions(result, trigger.substring(1))
                }
                trigger.startsWith("@") -> {
                    addFileCompletions(result, trigger.substring(1))
                }
                !hasInvalidTrigger -> {
                    // 只有在没有无效触发字符的情况下才显示一般提示
                    result.addElement(
                        LookupElementBuilder.create("/")
                            .withPresentableText("/")
                            .withTailText(" 输入斜杠命令")
                            .withIcon(AllIcons.Nodes.Method)
                    )
                    result.addElement(
                        LookupElementBuilder.create("@")
                            .withPresentableText("@")
                            .withTailText(" 引用文件")
                            .withIcon(AllIcons.FileTypes.Text)
                    )
                }
                else -> {
                    // 如果用户输入了无效的触发字符，不显示任何补全
                }
            }
        }
        
        private fun addSlashCompletions(result: CompletionResultSet, query: String) {
            val registry = slashCommandRegistry
            if (registry == null) {
                return
            }
            val commands = registry.filterCommands(query)
            
            commands.forEach { command ->
                val lookup = LookupElementBuilder.create(command.name)
                    .withPresentableText("/${command.name}")
                    .withTailText(" ${command.description}")
                    .withIcon(AllIcons.Nodes.Method)
                    .withInsertHandler { context, item ->
                        val editor = context.editor
                        val document = editor.document
                        val startOffset = context.startOffset
                        val caretOffset = context.tailOffset
                        
                        // Find the actual start of the trigger (should be the '/')
                        var triggerStart = startOffset
                        val text = document.text
                        while (triggerStart > 0 && text[triggerStart - 1] != '/') {
                            triggerStart--
                        }
                        if (triggerStart > 0 && text[triggerStart - 1] == '/') {
                            triggerStart--
                        }
                        
                        // Replace from trigger start to caret with the full command
                        document.replaceString(triggerStart, caretOffset, "/${command.name} ")
                        editor.caretModel.moveToOffset(triggerStart + command.name.length + 2)
                    }
                
                result.addElement(lookup)
            }
        }
        
        private fun addFileCompletions(result: CompletionResultSet, query: String) {
            val currentProject = project ?: return
            
            // Get recent files for empty query
            if (query.isEmpty()) {
                val recentFiles = getRecentProjectFiles(currentProject)
                recentFiles.take(10).forEach { virtualFile -> // Limit to 10 recent files
                    addFileCompletion(result, virtualFile, currentProject)
                }
            } else {
                // Search files using IntelliJ's file search
                val searchResults = searchProjectFiles(currentProject, query)
                searchResults.take(20).forEach { virtualFile -> // Limit to 20 search results
                    addFileCompletion(result, virtualFile, currentProject)
                }
            }
        }
        
        private fun addFileCompletion(result: CompletionResultSet, virtualFile: com.intellij.openapi.vfs.VirtualFile, currentProject: Project) {
            val projectBasePath = currentProject.basePath ?: return
            val relativePath = try {
                val projectPath = java.io.File(projectBasePath).toPath()
                val filePath = java.io.File(virtualFile.path).toPath()
                projectPath.relativize(filePath).toString()
            } catch (e: Exception) {
                virtualFile.name
            }
            
            val lookup = LookupElementBuilder.create(relativePath)
                .withPresentableText("@${virtualFile.name}")
                .withTailText(" $relativePath")
                .withIcon(virtualFile.fileType.icon)
                .withInsertHandler { context, item ->
                    val editor = context.editor
                    val document = editor.document
                    val startOffset = context.startOffset
                    val caretOffset = context.tailOffset
                    
                    // Find the actual start of the trigger (should be the '@')
                    var triggerStart = startOffset
                    val text = document.text
                    while (triggerStart > 0 && text[triggerStart - 1] != '@') {
                        triggerStart--
                    }
                    if (triggerStart > 0 && text[triggerStart - 1] == '@') {
                        triggerStart--
                    }
                    
                    // Replace from trigger start to caret with the full file reference
                    document.replaceString(triggerStart, caretOffset, "@$relativePath ")
                    editor.caretModel.moveToOffset(triggerStart + relativePath.length + 2)
                }
            
            result.addElement(lookup)
        }
        
        private fun findCompletionTrigger(text: String, offset: Int): Triple<CompletionTrigger, Int, String>? {
            if (text.isEmpty() || offset <= 0) return null
            
            // Look backward from cursor to find the most recent trigger
            var pos = minOf(offset - 1, text.length - 1)
            var foundTrigger: Triple<CompletionTrigger, Int, String>? = null
            
            while (pos >= 0 && pos < text.length) {
                val char = text[pos]
                
                when (char) {
                    '/' -> {
                        if (isValidSlashPosition(text, pos)) {
                            val query = text.substring(pos + 1, offset)
                            foundTrigger = Triple(CompletionTrigger.SLASH, pos, query)
                            break
                        }
                    }
                    '@' -> {
                        if (pos == 0 || text[pos - 1].isWhitespace()) {
                            val query = text.substring(pos + 1, offset)
                            val spaceIndex = query.indexOf(' ')
                            val cleanQuery = if (spaceIndex >= 0) query.substring(0, spaceIndex) else query
                            foundTrigger = Triple(CompletionTrigger.AT, pos, cleanQuery)
                            break
                        }
                    }
                    '\n' -> {
                        // Stop at line breaks
                        break
                    }
                }
                pos--
            }
            
            return foundTrigger
        }
        
        private fun isValidSlashPosition(text: String, slashPos: Int): Boolean {
            // 只有在行首才允许斜杠命令
            if (slashPos == 0) {
                return true
            }
            
            // 找到当前行的开始位置
            var lineStart = slashPos - 1
            while (lineStart > 0 && text[lineStart - 1] != '\n') {
                lineStart--
            }
            
            // 检查从行首到斜杠位置之间是否只有空白字符
            val linePrefix = text.substring(lineStart, slashPos)
            return linePrefix.isBlank() // 只有空白字符才允许（相当于行首）
        }
        
        private fun hasInvalidTriggerChar(text: String, offset: Int): Boolean {
            if (text.isEmpty() || offset <= 0) return false
            
            // 向后查找，看看是否有 / 或 @ 但不满足触发条件
            var pos = minOf(offset - 1, text.length - 1)
            
            while (pos >= 0 && pos < text.length) {
                val char = text[pos]
                
                when (char) {
                    '/' -> {
                        // 如果找到 / 但不是有效位置，则认为是无效触发
                        if (!isValidSlashPosition(text, pos)) {
                            return true
                        } else {
                            // 找到了有效的 /，停止搜索
                            return false
                        }
                    }
                    '@' -> {
                        // 如果找到 @ 但不是有效位置（不在开头或空白后），则认为是无效触发
                        if (pos != 0 && !text[pos - 1].isWhitespace()) {
                            return true
                        } else {
                            // 找到了有效的 @，停止搜索
                            return false
                        }
                    }
                    '\n' -> {
                        // 遇到换行符，停止搜索
                        break
                    }
                }
                pos--
            }
            
            return false
        }
        
        private fun getRecentProjectFiles(project: Project): List<com.intellij.openapi.vfs.VirtualFile> {
            // Get recent files from IntelliJ's file manager
            val recentFiles = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
            
            // Get recently opened files
            val recentFilesManager = com.intellij.openapi.fileEditor.impl.EditorHistoryManager.getInstance(project)
            recentFilesManager.fileList.forEach { virtualFile ->
                if (virtualFile.isValid && !virtualFile.isDirectory) {
                    recentFiles.add(virtualFile)
                }
            }
            
            // If we don't have enough recent files, add some project files
            if (recentFiles.size < 10) {
                com.intellij.psi.search.GlobalSearchScope.projectScope(project)
                val fileIndex = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
                
                val allProjectFiles = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
                val projectDir = project.projectFile?.parent ?: project.workspaceFile?.parent ?: return recentFiles
                com.intellij.openapi.vfs.VfsUtil.iterateChildrenRecursively(
                    projectDir,
                    { virtualFile -> 
                        !virtualFile.name.startsWith(".") && 
                        (virtualFile.isDirectory || fileIndex.isInContent(virtualFile))
                    }
                ) { virtualFile ->
                    if (!virtualFile.isDirectory && virtualFile.isValid) {
                        allProjectFiles.add(virtualFile)
                    }
                    true
                }
                
                // Add some common project files
                allProjectFiles.take(10).forEach { file ->
                    if (!recentFiles.contains(file)) {
                        recentFiles.add(file)
                    }
                }
            }
            
            return recentFiles
        }
        
        private fun searchProjectFiles(project: Project, query: String): List<com.intellij.openapi.vfs.VirtualFile> {
            val results = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
            
            // Use IntelliJ's FilenameIndex for fast file searching
            val fileNames = com.intellij.psi.search.FilenameIndex.getAllFilenames(project)
            val projectScope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            
            // Search for files by name
            fileNames.filter { fileName ->
                fileName.contains(query, ignoreCase = true)
            }.forEach { fileName ->
                val files = com.intellij.psi.search.FilenameIndex.getVirtualFilesByName(fileName, projectScope)
                files.forEach { virtualFile ->
                    if (virtualFile.isValid && !virtualFile.isDirectory) {
                        results.add(virtualFile)
                    }
                }
            }
            
            // Also search in file paths for more comprehensive results
            if (results.size < 10) {
                val fileIndex = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
                val projectDir = project.projectFile?.parent ?: project.workspaceFile?.parent ?: return results
                com.intellij.openapi.vfs.VfsUtil.iterateChildrenRecursively(
                    projectDir,
                    { virtualFile -> 
                        !virtualFile.name.startsWith(".") && 
                        (virtualFile.isDirectory || fileIndex.isInContent(virtualFile))
                    }
                ) { virtualFile ->
                    if (!virtualFile.isDirectory && virtualFile.isValid) {
                        val path = virtualFile.path
                        if ((virtualFile.name.contains(query, ignoreCase = true) || 
                             path.contains(query, ignoreCase = true)) &&
                            !results.contains(virtualFile)) {
                            results.add(virtualFile)
                        }
                    }
                    results.size < 20 // Stop when we have enough results
                }
            }
            
            return results.sortedBy { it.name }
        }
        
        private fun getFileIcon(fileType: String?): javax.swing.Icon? {
            if (fileType == null) return null
            
            return when (fileType.lowercase()) {
                "kt" -> AllIcons.FileTypes.Java
                "java" -> AllIcons.FileTypes.Java
                "js", "jsx" -> AllIcons.FileTypes.JavaScript
                "ts", "tsx" -> AllIcons.FileTypes.JavaScript
                "py" -> AllIcons.FileTypes.Java
                "md" -> AllIcons.FileTypes.Java
                "json" -> AllIcons.FileTypes.Json
                "xml" -> AllIcons.FileTypes.Xml
                "html", "htm" -> AllIcons.FileTypes.Html
                "css" -> AllIcons.FileTypes.Css
                else -> AllIcons.FileTypes.Unknown
            }
        }
    }
}
