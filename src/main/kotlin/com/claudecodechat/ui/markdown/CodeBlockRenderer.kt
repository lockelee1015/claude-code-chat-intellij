package com.claudecodechat.ui.markdown

import com.claudecodechat.ui.fonts.FontManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Dimension

/**
 * 自定义代码块渲染器，使用 IntelliJ 原生编辑器组件
 * 提供语法高亮、行号等功能
 */
object CodeBlockRenderer {
    
    /**
     * 创建代码块编辑器组件
     * @param code 代码内容
     * @param language 编程语言（用于语法高亮）
     * @param showLineNumbers 是否显示行号
     * @param isReadOnly 是否只读
     * @param maxHeight 最大高度（像素），null 表示不限制
     */
    fun createCodeBlockEditor(
        code: String,
        language: String? = null,
        showLineNumbers: Boolean = true,
        isReadOnly: Boolean = true,
        maxHeight: Int? = null  // 默认不限制高度，完全展开
    ): JPanel {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        
        return try {
            // 根据语言确定文件类型
            val fileType = getFileTypeFromLanguage(language)
            
            // 创建文档和编辑器
            val document = EditorFactory.getInstance().createDocument(code)
            val editor = EditorFactory.getInstance().createEditor(document, project, fileType, isReadOnly)
            
            // 配置编辑器
            configureCodeBlockEditor(editor, showLineNumbers, maxHeight)
            
            // 创建容器面板
            createEditorPanel(editor, maxHeight)
            
        } catch (e: Exception) {
            // 降级到简单文本显示
            createFallbackCodeBlock(code)
        }
    }
    
    /**
     * 根据语言名称获取对应的文件类型
     */
    private fun getFileTypeFromLanguage(language: String?): FileType {
        if (language.isNullOrBlank()) {
            return FileTypeManager.getInstance().getFileTypeByExtension("txt")
        }
        
        val extension = when (language.lowercase()) {
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "python", "py" -> "py"
            "c", "cpp", "c++" -> "cpp"
            "c#", "csharp", "cs" -> "cs"
            "go", "golang" -> "go"
            "rust", "rs" -> "rs"
            "php" -> "php"
            "ruby", "rb" -> "rb"
            "swift" -> "swift"
            "scala" -> "scala"
            "groovy" -> "groovy"
            "shell", "bash", "sh" -> "sh"
            "sql" -> "sql"
            "xml" -> "xml"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "yaml", "yml" -> "yml"
            "markdown", "md" -> "md"
            "dockerfile" -> "dockerfile"
            "gradle" -> "gradle"
            "properties" -> "properties"
            else -> "txt"
        }
        
        return FileTypeManager.getInstance().getFileTypeByExtension(extension)
    }
    
    /**
     * 配置代码块编辑器设置
     */
    private fun configureCodeBlockEditor(
        editor: Editor, 
        showLineNumbers: Boolean,
        maxHeight: Int?
    ) {
        val settings = editor.settings
        
        // 基本显示设置
        settings.isLineNumbersShown = showLineNumbers
        settings.isFoldingOutlineShown = false
        settings.isAutoCodeFoldingEnabled = false
        settings.isRightMarginShown = false
        settings.isWhitespacesShown = false
        settings.isLeadingWhitespaceShown = false
        settings.isTrailingWhitespaceShown = false
        settings.isIndentGuidesShown = false
        
        // 禁用编辑相关功能
        settings.isVirtualSpace = false
        settings.isBlockCursor = false
        settings.isCaretRowShown = false
        settings.additionalLinesCount = 0
        settings.additionalColumnsCount = 0
        
        // 启用软换行
        settings.isUseSoftWraps = true
        
        // 使用编辑器的行间距设置
        val fontInfo = FontManager.getEditorConsoleFontInfo()
        val scaledFontInfo = FontManager.scaleFont(fontInfo)
        
        // 设置编辑器组件大小和背景
        val component = editor.component
        // 设置代码块的背景色，与 CSS 中的代码块背景一致
        component.background = JBColor(
            java.awt.Color(0, 0, 0, (255 * 0.05).toInt()),  // 浅色模式：5% 黑色透明度
            java.awt.Color(255, 255, 255, (255 * 0.1).toInt())  // 深色模式：10% 白色透明度
        )
        
        // 计算内容的实际高度，让编辑器完全展开显示
        val lineHeight = (scaledFontInfo.fontSize * scaledFontInfo.lineHeight).toInt()
        val lineCount = editor.document.lineCount
        
        if (maxHeight != null) {
            // 如果有最大高度限制，使用较小值
            val preferredHeight = minOf(lineCount * lineHeight + 20, maxHeight)  // 增加一些边距
            component.preferredSize = Dimension(component.preferredSize.width, preferredHeight)
        } else {
            // 完全展开显示，根据内容计算高度
            val preferredHeight = lineCount * lineHeight + 20  // 增加一些边距
            component.preferredSize = Dimension(component.preferredSize.width, preferredHeight)
            component.minimumSize = Dimension(component.minimumSize.width, preferredHeight)
        }
    }
    
    /**
     * 创建编辑器容器面板
     */
    private fun createEditorPanel(editor: Editor, maxHeight: Int?): JPanel {
        return JPanel(BorderLayout()).apply {
            // 让容器透明，只使用编辑器的背景色
            isOpaque = false
            
            // 增加上下间距
            add(editor.component, BorderLayout.CENTER)
            
            // 存储编辑器引用以便清理
            putClientProperty("editor", editor)
            
            // 禁用滚动条，让内容完全展开
            disableScrolling(editor)
            
            // 只设置边距，不设置边框（避免双重边框）
            border = JBUI.Borders.empty(8, 0)  // 上下8px间距
        }
    }
    
    /**
     * 禁用滚动行为，让内容完全展开
     */
    private fun disableScrolling(editor: Editor) {
        // 禁用滚动动画和相关滚动行为
        editor.scrollingModel.disableAnimation()
        
        // 编辑器设置已经在 configureCodeBlockEditor 中处理了滚动相关选项
        // 这里只需要确保滚动模型不会产生动画效果
    }
    
    /**
     * 创建降级的代码块显示
     */
    private fun createFallbackCodeBlock(code: String): JPanel {
        return JPanel(BorderLayout()).apply {
            // 让容器透明
            isOpaque = false
            
            // 使用与编辑器一致的字体设置
            val fontInfo = FontManager.getEditorConsoleFontInfo()
            val scaledFontInfo = FontManager.scaleFont(fontInfo)
            val swingFont = FontManager.getSwingFont(scaledFontInfo)
            
            val textArea = javax.swing.JTextArea(code).apply {
                isEditable = false
                font = swingFont  // 使用与编辑器一致的字体
                // 设置与编辑器相同的代码块背景色
                background = JBColor(
                    java.awt.Color(0, 0, 0, (255 * 0.05).toInt()),  // 浅色模式：5% 黑色透明度
                    java.awt.Color(255, 255, 255, (255 * 0.1).toInt())  // 深色模式：10% 白色透明度
                )
                foreground = JBColor.foreground()
                border = JBUI.Borders.empty(12, 8)  // 增加内边距
                lineWrap = true
                wrapStyleWord = true
                
                // 计算合适的行数，让文本区域完全展开
                val lineCount = code.split('\n').size
                rows = lineCount
            }
            
            // 直接添加文本区域，不使用滚动面板
            add(textArea, BorderLayout.CENTER)
            
            // 只设置边距，不设置边框
            border = JBUI.Borders.empty(8, 0)  // 上下8px间距
        }
    }
    
    /**
     * 释放编辑器资源
     */
    fun disposeEditor(panel: JPanel) {
        val editor = panel.getClientProperty("editor") as? Editor
        editor?.let {
            EditorFactory.getInstance().releaseEditor(it)
        }
    }
}
