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
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
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
import java.awt.FlowLayout

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
            
            // Create diff view based on operation type
            val diffPanel = when {
                // Normal edit with both old and new content
                editInfo.oldString.isNotEmpty() && editInfo.newString.isNotEmpty() -> {
                    createInlineDiffView(editInfo.oldString, editInfo.newString, fileType)
                }
                // Deletion operation (old content exists, new content is empty)
                editInfo.oldString.isNotEmpty() && editInfo.newString.isEmpty() -> {
                    createDeletionView(editInfo.oldString, fileType)
                }
                // Addition operation (no old content, new content exists) 
                editInfo.oldString.isEmpty() && editInfo.newString.isNotEmpty() -> {
                    createAdditionView(editInfo.newString, fileType)
                }
                // Other cases (multiedit, etc.)
                else -> {
                    createSummaryView(editInfo)
                }
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
                editor.document.getLineNumber(firstModifiedOffset)
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
     * Highlight text as deleted (red background)
     */
    private fun highlightAsDeleted(editor: Editor, text: String) {
        try {
            val markupModel = editor.markupModel
            val textLength = text.length
            
            // Add red background highlighting for entire content
            markupModel.addRangeHighlighter(
                0, textLength,
                HighlighterLayer.SELECTION - 1,
                createDeletedTextAttributes(),
                HighlighterTargetArea.EXACT_RANGE
            )
        } catch (e: Exception) {
            // Ignore highlighting errors
        }
    }
    
    /**
     * Highlight text as added (green background)
     */
    private fun highlightAsAdded(editor: Editor, text: String) {
        try {
            val markupModel = editor.markupModel
            val textLength = text.length
            
            // Add green background highlighting for entire content
            markupModel.addRangeHighlighter(
                0, textLength,
                HighlighterLayer.SELECTION - 1,
                createAddedTextAttributes(),
                HighlighterTargetArea.EXACT_RANGE
            )
        } catch (e: Exception) {
            // Ignore highlighting errors
        }
    }
    
    /**
     * Create text attributes for deleted content
     */
    private fun createDeletedTextAttributes(): TextAttributes {
        return TextAttributes().apply {
            backgroundColor = JBColor(
                Color(255, 220, 220),  // 浅色主题：浅红色
                Color(50, 30, 30)      // 深色主题：深红色
            )
            effectType = EffectType.STRIKEOUT
            effectColor = JBColor(
                Color(160, 20, 20),    // 浅色主题：深红色删除线
                Color(200, 100, 100)   // 深色主题：浅红色删除线
            )
        }
    }
    
    /**
     * Create text attributes for added content
     */
    private fun createAddedTextAttributes(): TextAttributes {
        return TextAttributes().apply {
            backgroundColor = JBColor(
                Color(220, 255, 220),  // 浅色主题：浅绿色
                Color(30, 50, 30)      // 深色主题：深绿色
            )
        }
    }
    
    /**
     * Highlight a line in the editor
     */
    private fun highlightLine(markupModel: MarkupModel, startOffset: Int, endOffset: Int, isAdded: Boolean) {
        val color = if (isAdded) {
            // 绿色高亮，适配深色和浅色主题
            JBColor(
                Color(200, 255, 200),  // 浅色主题：浅绿色
                Color(40, 60, 40)      // 深色主题：深绿色
            )
        } else {
            // 红色高亮，适配深色和浅色主题
            JBColor(
                Color(255, 200, 200),  // 浅色主题：浅红色
                Color(60, 40, 40)      // 深色主题：深红色
            )
        }
        
        val textAttributes = TextAttributes().apply {
            backgroundColor = color
        }
        
        markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.SELECTION - 1, // Lower than selection
            textAttributes,
            HighlighterTargetArea.EXACT_RANGE
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
     * Configure viewer settings for deletion/addition views
     */
    private fun configureViewerSettings(editor: Editor) {
        val settings = editor.settings
        
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
     * Create deletion view showing removed content with strike-through
     */
    private fun createDeletionView(deletedText: String, fileType: FileType): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            
            // Create header to indicate deletion
            val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                background = JBColor(
                    Color(255, 235, 235, 180),  // 浅色主题：浅红色半透明
                    Color(60, 40, 40, 180)      // 深色主题：深红色半透明
                )
                
                val deleteIcon = JBLabel("🗑").apply {
                    font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
                }
                
                val deleteLabel = JBLabel("Deleted content:").apply {
                    font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                    foreground = JBColor(
                        Color(160, 20, 20),     // 浅色主题：深红色
                        Color(220, 120, 120)    // 深色主题：浅红色
                    )
                }
                
                add(deleteIcon)
                add(deleteLabel)
            }
            
            // Create editor showing deleted content with strike-through effect
            val editorPanel = try {
                val project = ProjectManager.getInstance().defaultProject
                val editorFactory = EditorFactory.getInstance()
                val document = editorFactory.createDocument(deletedText)
                val editor = editorFactory.createViewer(document, project)
                
                // Configure editor appearance
                configureViewerSettings(editor)
                
                // Add red background highlighting to show deletion
                highlightAsDeleted(editor, deletedText)
                
                editor.component
            } catch (e: Exception) {
                // Fallback to simple text area
                createSimpleTextView(deletedText)
            }
            
            add(headerPanel, BorderLayout.NORTH)
            add(editorPanel, BorderLayout.CENTER)
        }
    }
    
    /**
     * Create addition view showing added content
     */
    private fun createAdditionView(addedText: String, fileType: FileType): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            
            // Create header to indicate addition
            val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                background = JBColor(
                    Color(235, 255, 235, 180),  // 浅色主题：浅绿色半透明
                    Color(40, 60, 40, 180)      // 深色主题：深绿色半透明
                )
                
                val addIcon = JBLabel("➕").apply {
                    font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
                }
                
                val addLabel = JBLabel("Added content:").apply {
                    font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                    foreground = JBColor(
                        Color(20, 120, 20),     // 浅色主题：深绿色
                        Color(120, 200, 120)    // 深色主题：浅绿色
                    )
                }
                
                add(addIcon)
                add(addLabel)
            }
            
            // Create editor showing added content
            val editorPanel = try {
                val project = ProjectManager.getInstance().defaultProject
                val editorFactory = EditorFactory.getInstance()
                val document = editorFactory.createDocument(addedText)
                val editor = editorFactory.createViewer(document, project)
                
                // Configure editor appearance
                configureViewerSettings(editor)
                
                // Add green background highlighting to show addition
                highlightAsAdded(editor, addedText)
                
                editor.component
            } catch (e: Exception) {
                // Fallback to simple text area
                createSimpleTextView(addedText)
            }
            
            add(headerPanel, BorderLayout.NORTH)
            add(editorPanel, BorderLayout.CENTER)
        }
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
            
            val textArea = JBTextArea(text).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                background = JBColor.background()
                foreground = JBColor.foreground()
                lineWrap = false
            }
            
            val scrollPane = JBScrollPane(textArea).apply {
                preferredSize = Dimension(-1, 150)
                border = JBUI.Borders.empty()
            }
            
            add(scrollPane, BorderLayout.CENTER)
            
            // Disable scrolling by default, enable on click
            ScrollControlUtil.disableScrollingByDefault(scrollPane)
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
                foreground = JBColor(
                    Color(220, 38, 38),     // 浅色主题：红色
                    Color(255, 120, 120)    // 深色主题：浅红色
                )
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
