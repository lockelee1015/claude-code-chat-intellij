package com.claudecodechat.ui.fonts

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.util.ui.JBUI
import java.awt.Font

/**
 * 字体管理器，统一管理编辑器和 Markdown 渲染的字体设置
 * 确保整个插件的字体风格一致
 */
object FontManager {
    
    /**
     * 获取编辑器字体信息
     */
    data class EditorFontInfo(
        val fontFamily: String,
        val fontSize: Int,
        val lineHeight: Float,
        val letterSpacing: Float = 0f
    )
    
    /**
     * 获取当前编辑器的主字体信息
     */
    fun getEditorFontInfo(): EditorFontInfo {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val font = scheme.getFont(EditorFontType.PLAIN)
        
        return EditorFontInfo(
            fontFamily = font.family,
            fontSize = font.size,
            lineHeight = scheme.lineSpacing,
            letterSpacing = 0f // IntelliJ 默认没有字符间距设置
        )
    }
    
    /**
     * 获取编辑器的等宽字体信息（用于代码块）
     */
    fun getEditorConsoleFontInfo(): EditorFontInfo {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val consoleFont = scheme.consoleFontName?.let { fontName ->
            Font(fontName, Font.PLAIN, scheme.consoleFontSize)
        } ?: scheme.getFont(EditorFontType.PLAIN)
        
        return EditorFontInfo(
            fontFamily = consoleFont.family,
            fontSize = consoleFont.size,
            lineHeight = scheme.lineSpacing,
            letterSpacing = 0f
        )
    }
    
    /**
     * 获取适合 Markdown 正文的字体信息
     * 基于编辑器字体，但可能会调整大小
     */
    fun getMarkdownTextFontInfo(fontSizeAdjustment: Int = 0, lineSpacing: Float? = null): EditorFontInfo {
        val editorFont = getEditorFontInfo()
        return editorFont.copy(
            fontSize = (editorFont.fontSize + fontSizeAdjustment).coerceAtLeast(8),
            lineHeight = lineSpacing ?: editorFont.lineHeight
        )
    }
    
    /**
     * 获取适合 Markdown 代码块的字体信息
     * 使用编辑器的等宽字体设置
     */
    fun getMarkdownCodeFontInfo(fontSizeAdjustment: Int = -1): EditorFontInfo {
        val consoleFont = getEditorConsoleFontInfo()
        return consoleFont.copy(
            fontSize = (consoleFont.fontSize + fontSizeAdjustment).coerceAtLeast(8)
        )
    }
    
    /**
     * 获取适合内联代码的字体信息
     * 使用等宽字体家族，但大小与正文文本保持一致
     */
    fun getMarkdownInlineCodeFontInfo(textFontInfo: EditorFontInfo): EditorFontInfo {
        val consoleFont = getEditorConsoleFontInfo()
        return textFontInfo.copy(
            fontFamily = consoleFont.fontFamily,  // 使用等宽字体
            fontSize = textFontInfo.fontSize      // 但保持与正文相同的大小
        )
    }
    
    /**
     * 创建 CSS 字体样式字符串
     */
    fun createCssFontStyle(fontInfo: EditorFontInfo): String {
        val lineHeightValue = fontInfo.lineHeight
        
        // 简化字体名称，避免特殊字符导致解析问题
        val safeFontFamily = fontInfo.fontFamily.replace("'", "").replace("\"", "")
        
        return """
            font-family: ${safeFontFamily}, monospace;
            font-size: ${fontInfo.fontSize}px;
        """.trimIndent()
    }
    
    /**
     * 创建用于正文的 CSS 字体样式
     */
    fun createTextCssFontStyle(fontInfo: EditorFontInfo): String {
        val lineHeightValue = fontInfo.lineHeight
        
        // 简化字体名称，避免特殊字符导致解析问题
        val safeFontFamily = fontInfo.fontFamily.replace("'", "").replace("\"", "")
        
        return """
            font-family: ${safeFontFamily}, sans-serif;
            font-size: ${fontInfo.fontSize}px;
        """.trimIndent()
    }
    
    /**
     * 创建用于代码的 CSS 字体样式
     */
    fun createCodeCssFontStyle(fontInfo: EditorFontInfo): String {
        val lineHeightValue = fontInfo.lineHeight
        
        // 简化字体名称，避免特殊字符导致解析问题
        val safeFontFamily = fontInfo.fontFamily.replace("'", "").replace("\"", "")
        
        return """
            font-family: ${safeFontFamily}, monospace;
            font-size: ${fontInfo.fontSize}px;
        """.trimIndent()
    }
    
    /**
     * 创建用于内联代码的 CSS 字体样式
     */
    fun createInlineCodeCssFontStyle(fontInfo: EditorFontInfo): String {
        val lineHeightValue = fontInfo.lineHeight
        
        // 简化字体名称，避免特殊字符导致解析问题
        val safeFontFamily = fontInfo.fontFamily.replace("'", "").replace("\"", "")
        
        return """
            font-family: ${safeFontFamily}, monospace;
            font-size: ${fontInfo.fontSize}px;
        """.trimIndent()
    }
    
    /**
     * 获取与编辑器一致的 Swing 字体
     */
    fun getSwingFont(fontInfo: EditorFontInfo): Font {
        return Font(fontInfo.fontFamily, Font.PLAIN, fontInfo.fontSize)
    }
    
    /**
     * 获取 JetBrains UI 缩放比例
     */
    fun getUIScale(): Float {
        return JBUI.pixScale()
    }
    
    /**
     * 根据 UI 缩放调整字体大小
     * 注意：编辑器字体已经包含了缩放，所以通常不需要再次缩放
     */
    fun scaleFont(fontInfo: EditorFontInfo): EditorFontInfo {
        // 编辑器字体已经考虑了系统缩放，通常不需要再次缩放
        // 除非是自定义字体大小的情况
        return fontInfo
    }
    
    /**
     * 检查字体是否为等宽字体
     */
    fun isMonospaceFont(fontFamily: String): Boolean {
        val monospaceKeywords = listOf(
            "mono", "consola", "courier", "menlo", "monaco", 
            "jetbrains", "fira", "source code", "roboto mono",
            "ubuntu mono", "liberation mono", "dejavu sans mono"
        )
        
        val lowerFamily = fontFamily.lowercase()
        return monospaceKeywords.any { keyword ->
            lowerFamily.contains(keyword)
        }
    }
    
    /**
     * 获取默认的等宽字体后备列表
     */
    fun getMonospaceFontFallbacks(): List<String> {
        return listOf(
            "JetBrains Mono",
            "Fira Code",
            "Source Code Pro",
            "Menlo",
            "Monaco", 
            "Consolas",
            "Courier New",
            "monospace"
        )
    }
    
    /**
     * 获取默认的文本字体后备列表
     */
    fun getTextFontFallbacks(): List<String> {
        return listOf(
            "-apple-system",
            "BlinkMacSystemFont",
            "Segoe UI",
            "Roboto",
            "Helvetica Neue",
            "Arial",
            "Noto Sans",
            "sans-serif"
        )
    }
}
