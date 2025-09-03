package com.claudecodechat.ui.swing.renderers

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Renderer for Write tool - shows created file content
 */
class WriteRenderer : ToolRenderer() {
    
    override fun canHandle(toolName: String): Boolean {
        return toolName.lowercase() == "write"
    }
    
    override fun extractDisplayParameters(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                val fullPath = toolInput["file_path"]?.jsonPrimitive?.content ?: ""
                // Remove current working directory prefix but keep relative path structure
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
        
        return createContentPreview(input)
    }
    
    /**
     * Create content preview showing the written file content
     */
    private fun createContentPreview(input: ToolRenderInput): JPanel {
        val filePath = extractDisplayParameters(input.toolInput)
        val fileType = getFileType(filePath)
        val fileContent = extractFileContent(input.toolInput)
        
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            
            // Create content preview
            val contentPanel = if (fileContent.isNotEmpty()) {
                createEditorPreview(fileContent, fileType)
            } else {
                createSummaryView()
            }
            
            add(contentPanel, BorderLayout.CENTER)
            
            // Store file info for "Show Content" button access
            putClientProperty("fileContent", fileContent)
            putClientProperty("filePath", getFullFilePath(input.toolInput))
            putClientProperty("fileType", fileType)
            
            // Set preferred size
            preferredSize = Dimension(-1, 120)
        }
    }
    
    /**
     * Create editor preview showing file content
     */
    private fun createEditorPreview(content: String, fileType: FileType): JPanel {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        
        return try {
            // Create document with file content
            val document = EditorFactory.getInstance().createDocument(content)
            
            // Create editor
            val editor = EditorFactory.getInstance().createEditor(document, project, fileType, true)
            
            // Configure editor settings
            configureEditor(editor)
            
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
            createSimpleTextView(content)
        }
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
     * Create summary view for files without content
     */
    private fun createSummaryView(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            
            val summaryLabel = JBLabel("New file created").apply {
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
                preferredSize = Dimension(-1, 100)
                border = JBUI.Borders.empty()
            }
            
            add(scrollPane, BorderLayout.CENTER)
            
            // Disable scrolling by default, enable on click
            ScrollControlUtil.disableScrollingByDefault(scrollPane)
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
     * Extract file content from tool input
     */
    private fun extractFileContent(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                return toolInput["content"]?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
    }
    
    /**
     * Get full file path from tool input
     */
    private fun getFullFilePath(toolInput: JsonElement?): String {
        try {
            if (toolInput is JsonObject) {
                return toolInput["file_path"]?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return ""
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
     * Show file content in virtual file editor
     */
    fun showContentInEditor(filePath: String, content: String, fileType: FileType) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        
        try {
            // Create virtual file with content
            val fileName = if (filePath.isNotEmpty()) filePath.substringAfterLast("/") else "NewFile.txt"
            val virtualFile = LightVirtualFile(fileName, fileType, content)
            
            // Open in editor
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        } catch (e: Exception) {
            // Ignore errors in editor opening
        }
    }
}
