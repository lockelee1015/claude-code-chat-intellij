package com.claudecodechat.ui.swing.renderers

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Renderer for Edit tool using IntelliJ's native editor
 */
class EditRenderer : ToolRenderer() {
    
    override fun canHandle(toolName: String): Boolean {
        return toolName.lowercase() in listOf("edit", "search_replace", "multiedit")
    }
    
    override fun extractDisplayParameters(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                val fullPath = toolInput["file_path"]?.jsonPrimitive?.content ?: ""
                // Remove current working directory prefix
                return removeWorkingDirectoryPrefix(fullPath)
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
    }
    
    /**
     * Remove working directory prefix from file path
     */
    private fun removeWorkingDirectoryPrefix(filePath: String): String {
        if (filePath.isEmpty()) return filePath
        
        // Get current working directory
        val cwd = System.getProperty("user.dir")
        return if (filePath.startsWith(cwd)) {
            val relativePath = filePath.removePrefix(cwd).removePrefix("/")
            if (relativePath.isEmpty()) filePath else relativePath
        } else {
            filePath
        }
    }
    
    override fun renderContent(input: ToolRenderInput): JPanel {
        if (input.status == ToolStatus.ERROR) {
            return createErrorPanel(input.toolOutput)
        }
        
        return createDiffPanel(input)
    }
    
    /**
     * Create diff panel showing before/after changes
     */
    private fun createDiffPanel(input: ToolRenderInput): JPanel {
        val filePath = extractDisplayParameters(input.toolInput)
        val fileType = getFileType(filePath)
        
        // Parse edit information from tool input
        val editInfo = parseEditInfo(input.toolInput)
        
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            
            // Create diff view directly (no header needed since title bar handles it)
            val diffPanel = if (editInfo.oldString.isNotEmpty() && editInfo.newString.isNotEmpty()) {
                createInlineDiffView(editInfo.oldString, editInfo.newString, fileType)
            } else {
                createSummaryView(editInfo)
            }
            
            add(diffPanel, BorderLayout.CENTER)
            
            // Store edit info for title bar button access
            putClientProperty("editInfo", editInfo)
            
            // Set preferred size
            preferredSize = Dimension(-1, 180)
        }
    }
    

    
    /**
     * Create inline diff view using IntelliJ editor
     */
    private fun createInlineDiffView(oldText: String, newText: String, fileType: FileType): JPanel {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        
        return try {
            // Create document with new content
            val document = EditorFactory.getInstance().createDocument(newText)
            
            // Create editor
            val editor = EditorFactory.getInstance().createEditor(document, project, fileType, true)
            
            // Configure editor settings
            configureEditor(editor)
            
            // Add diff highlighting and get modified line offsets
            val modifiedLineOffsets = addDiffHighlighting(editor, oldText, newText)

            // Auto-scroll to first modified line
            if (modifiedLineOffsets.isNotEmpty()) {
                val firstModifiedOffset = modifiedLineOffsets.first()
                val lineNumber = editor.document.getLineNumber(firstModifiedOffset)
                editor.caretModel.moveToOffset(firstModifiedOffset)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
            
            // Wrap editor component
            JPanel(BorderLayout()).apply {
                background = JBColor.background()
                add(editor.component, BorderLayout.CENTER)
                
                // Store editor reference for cleanup
                putClientProperty("editor", editor)
                
                // Disable scrolling by default, enable on click
                ScrollControlUtil.disableScrollingByDefault(editor.component)
            }
            
        } catch (e: Exception) {
            // Fallback to simple text view
            createSimpleTextView(newText)
        }
    }
    
    /**
     * Add diff highlighting to editor and return offsets of modified lines
     */
    private fun addDiffHighlighting(editor: Editor, oldText: String, newText: String): List<Int> {
        val modifiedOffsets = mutableListOf<Int>()
        try {
            val oldLines = oldText.split("\n")
            val newLines = newText.split("\n")
            val markupModel = editor.markupModel
            
            // Simple line-by-line comparison
            val maxLines = maxOf(oldLines.size, newLines.size)
            var currentOffset = 0
            
            for (i in 0 until maxLines) {
                val oldLine = oldLines.getOrNull(i) ?: ""
                val newLine = newLines.getOrNull(i) ?: ""
                
                when {
                    // Line was added (exists in new but not old)
                    i >= oldLines.size -> {
                        val lineEndOffset = currentOffset + newLine.length
                        highlightLine(markupModel, currentOffset, lineEndOffset, true) // Green for added
                        modifiedOffsets.add(currentOffset)
                    }
                    // Line was deleted (exists in old but not new) - skip highlighting since it's not in new text
                    i >= newLines.size -> {
                        // Nothing to highlight in new text
                    }
                    // Line was modified
                    oldLine != newLine -> {
                        val lineEndOffset = currentOffset + newLine.length
                        highlightLine(markupModel, currentOffset, lineEndOffset, true) // Green for modified
                        modifiedOffsets.add(currentOffset)
                    }
                    // Line unchanged - no highlighting needed
                }
                
                if (i < newLines.size) {
                    currentOffset += newLine.length + 1 // +1 for newline character
                }
            }
        } catch (e: Exception) {
            // Ignore highlighting errors
        }
        return mutableListOf<Int>()
    }
    
    /**
     * Highlight a line in the editor
     */
    private fun highlightLine(markupModel: MarkupModel, startOffset: Int, endOffset: Int, isAdded: Boolean) {
        val color = if (isAdded) {
            Color(200, 255, 200) // Light green for added/modified lines
        } else {
            Color(255, 200, 200) // Light red for deleted lines (not used in this context)
        }
        
        val textAttributes = TextAttributes().apply {
            backgroundColor = color
        }
        
        markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.SELECTION - 1, // Lower than selection
            textAttributes,
            com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
        )
    }
    
    /**
     * Configure editor settings for read-only mode
     */
    private fun configureEditor(editor: Editor) {
        val settings = editor.settings
        
        // Set read-only
        editor.document.setReadOnly(true)
        
        // Configure appearance
        settings.isLineNumbersShown = true
        settings.isFoldingOutlineShown = false
        settings.isAutoCodeFoldingEnabled = false
        settings.isRightMarginShown = false
        settings.isWhitespacesShown = false
        settings.isLeadingWhitespaceShown = false
        settings.isTrailingWhitespaceShown = false
        
        // Disable editing features
        settings.isVirtualSpace = false
        settings.isBlockCursor = false
        settings.isCaretRowShown = false
    }
    
    /**
     * Create summary view for operations without specific old/new strings
     */
    private fun createSummaryView(editInfo: EditInfo): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            
            val summaryText = when (editInfo.operationType) {
                "multiedit" -> "Applied ${editInfo.editCount} edits"
                else -> "File modified"
            }
            
            val summaryLabel = JBLabel(summaryText).apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                foreground = JBColor.foreground()
                horizontalAlignment = SwingConstants.CENTER
            }
            
            add(summaryLabel, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create simple text view as fallback
     */
    private fun createSimpleTextView(text: String): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            
            val textArea = com.intellij.ui.components.JBTextArea(text).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                background = JBColor.background()
                foreground = JBColor.foreground()
                lineWrap = false
            }
            
            val scrollPane = com.intellij.ui.components.JBScrollPane(textArea).apply {
                preferredSize = Dimension(-1, 150)
                border = JBUI.Borders.empty()
            }
            
            add(scrollPane, BorderLayout.CENTER)
            
            // Disable scrolling by default, enable on click
            ScrollControlUtil.disableScrollingByDefault(scrollPane)
        }
    }
    
    /**
     * Show full diff in separate window using virtual file
     */
    private fun showFullDiff(oldText: String, newText: String, filePath: String) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        
        try {
            // Create virtual file with the new content for immediate viewing
            if (newText.isNotEmpty()) {
                val fileName = if (filePath.isNotEmpty()) filePath.substringAfterLast("/") else "ModifiedFile.txt"
                val fileType = getFileType(filePath)
                val virtualFile = com.intellij.testFramework.LightVirtualFile(fileName, fileType, newText)
                
                // Open the modified file in editor
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
            
            // Also show diff window for comparison
            val diffRequest = SimpleDiffRequest(
                "File Changes: ${filePath.substringAfterLast("/")}",
                DiffContentFactory.getInstance().create(oldText),
                DiffContentFactory.getInstance().create(newText),
                "Before",
                "After"
            )
            
            DiffManager.getInstance().showDiff(project, diffRequest)
        } catch (e: Exception) {
            // Ignore errors in diff display
        }
    }
    
    /**
     * Get file type for syntax highlighting
     */
    private fun getFileType(filePath: String): FileType {
        return if (filePath.isNotEmpty()) {
            FileTypeManager.getInstance().getFileTypeByFileName(filePath)
        } else {
            FileTypeManager.getInstance().getFileTypeByExtension("txt")
        }
    }
    
    /**
     * Parse edit information from tool input
     */
    private fun parseEditInfo(toolInput: JsonElement?): EditInfo {
        if (toolInput !is JsonObject) {
            return EditInfo()
        }
        
        return try {
            EditInfo(
                oldString = toolInput["old_string"]?.jsonPrimitive?.content ?: "",
                newString = toolInput["new_string"]?.jsonPrimitive?.content ?: "",
                operationType = determineOperationType(toolInput),
                editCount = countEdits(toolInput)
            )
        } catch (e: Exception) {
            EditInfo()
        }
    }
    
    /**
     * Determine operation type from tool input
     */
    private fun determineOperationType(toolInput: JsonObject): String {
        return when {
            toolInput.containsKey("edits") -> "multiedit"
            toolInput["old_string"]?.jsonPrimitive?.content?.isEmpty() == true -> "write"
            else -> "edit"
        }
    }
    
    /**
     * Count number of edits for MultiEdit
     */
    private fun countEdits(toolInput: JsonObject): Int {
        return try {
            toolInput["edits"]?.let { editsElement ->
                if (editsElement is kotlinx.serialization.json.JsonArray) {
                    editsElement.size
                } else 1
            } ?: 1
        } catch (e: Exception) {
            1
        }
    }
    
    /**
     * Create error panel
     */
    private fun createErrorPanel(errorMessage: String): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            
            val errorLabel = JBLabel("Error: $errorMessage").apply {
                foreground = Color(220, 38, 38)
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            }
            
            add(errorLabel, BorderLayout.CENTER)
        }
    }
    
    /**
     * Data class to hold edit information
     */
    private data class EditInfo(
        val oldString: String = "",
        val newString: String = "",
        val operationType: String = "edit",
        val editCount: Int = 1
    )
}
