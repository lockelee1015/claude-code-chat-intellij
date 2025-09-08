package com.claudecodechat.ui.markdown

import com.claudecodechat.settings.ClaudeSettings
import com.claudecodechat.ui.fonts.FontManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.html.HtmlRenderer
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Color
import java.awt.Component

/**
 * 增强的 Markdown 渲染器，支持使用 IntelliJ 编辑器渲染代码块
 */
data class EnhancedMarkdownRenderConfig(
    val allowHeadings: Boolean = false,
    val allowImages: Boolean = false,
    val overrideForeground: Color? = null,
    val fontScale: Float? = null,
    val useIntelliJCodeBlocks: Boolean = true,  // 是否使用 IntelliJ 编辑器渲染代码块
    val showLineNumbers: Boolean = true,        // 代码块是否显示行号
    val maxCodeBlockHeight: Int = 300           // 代码块最大高度
)

object EnhancedMarkdownRenderer {
    private val baseOptions: MutableDataSet = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
        ))
    }

    private fun buildParser(): Parser = Parser.builder(baseOptions).build()
    private fun buildRenderer(): HtmlRenderer = HtmlRenderer.builder(baseOptions).build()

    /**
     * 创建混合渲染的组件：HTML + IntelliJ 编辑器代码块
     */
    fun createComponent(
        markdown: String, 
        config: EnhancedMarkdownRenderConfig = EnhancedMarkdownRenderConfig()
    ): JPanel {
        if (!config.useIntelliJCodeBlocks) {
            // 使用原始的 HTML 渲染方式
            return createHtmlOnlyComponent(markdown, config)
        }
        
        // 使用混合渲染方式
        return createMixedComponent(markdown, config)
    }
    
    /**
     * 创建混合渲染组件（HTML + IntelliJ 编辑器）
     */
    private fun createMixedComponent(
        markdown: String,
        config: EnhancedMarkdownRenderConfig
    ): JPanel {
        val parser = buildParser()
        val document = parser.parse(markdown)
        applyRestrictions(document, config)
        
        // 提取代码块和其他内容
        val segments = extractContentSegments(document)
        
        if (segments.isEmpty()) {
            return JPanel()
        }
        
        if (segments.size == 1 && segments[0] !is CodeBlockSegment) {
            // 只有一个非代码块段，使用普通 HTML 渲染
            return createHtmlComponent(segments[0].content, config) ?: JPanel()
        }
        
        // 创建垂直布局的面板来组合各个部分
        return createVerticalPanel(segments, config)
    }
    
    /**
     * 提取内容段落（代码块和其他内容）
     */
    private fun extractContentSegments(document: Node): List<ContentSegment> {
        val segments = mutableListOf<ContentSegment>()
        val currentNonCodeContent = StringBuilder()
        
        fun flushNonCodeContent() {
            if (currentNonCodeContent.isNotEmpty()) {
                segments.add(NonCodeSegment(currentNonCodeContent.toString().trim()))
                currentNonCodeContent.clear()
            }
        }
        
        var node: Node? = document.firstChild
        while (node != null) {
            when (node) {
                is FencedCodeBlock -> {
                    flushNonCodeContent()
                    val info = node.info.toString()
                    val language = info.split(" ").firstOrNull()?.trim()
                    val code = node.contentChars.toString()
                    segments.add(CodeBlockSegment(code, language))
                }
                is IndentedCodeBlock -> {
                    flushNonCodeContent()
                    val code = node.contentChars.toString()
                    segments.add(CodeBlockSegment(code, null))
                }
                else -> {
                    // 收集非代码块内容
                    currentNonCodeContent.append(node.chars.toString()).append("\n")
                }
            }
            node = node.next
        }
        
        flushNonCodeContent()
        return segments
    }
    
    /**
     * 创建垂直排列的混合内容面板
     */
    private fun createVerticalPanel(
        segments: List<ContentSegment>,
        config: EnhancedMarkdownRenderConfig
    ): JPanel {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        
        segments.forEach { segment ->
            val component = when (segment) {
                is CodeBlockSegment -> createCodeBlockComponent(segment, config)
                is NonCodeSegment -> createHtmlComponent(segment.content, config)
            }
            
            component?.let {
                // 确保组件有合适的对齐方式
                it.alignmentX = Component.LEFT_ALIGNMENT
                mainPanel.add(it)
                
                // 在组件之间添加小间距
                mainPanel.add(Box.createVerticalStrut(4))
            }
        }
        
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(mainPanel, BorderLayout.NORTH)
        }
    }
    
    /**
     * 创建代码块组件
     */
    private fun createCodeBlockComponent(
        segment: CodeBlockSegment,
        config: EnhancedMarkdownRenderConfig
    ): JPanel {
        return try {
            CodeBlockRenderer.createCodeBlockEditor(
                code = segment.code,
                language = segment.language,
                showLineNumbers = config.showLineNumbers,
                isReadOnly = true,
                maxHeight = null  // 不限制高度，完全展开
            )
        } catch (e: Exception) {
            // 降级到原始HTML渲染
            createHtmlComponent("```${segment.language ?: ""}\n${segment.code}\n```", config)
                ?: JPanel() // 提供默认的空面板
        }
    }
    
    /**
     * 创建 HTML 组件
     */
    private fun createHtmlComponent(
        content: String,
        config: EnhancedMarkdownRenderConfig
    ): JPanel? {
        if (content.isBlank()) return null
        
        val html = toHtml(content, config)
        val settings = ClaudeSettings.getInstance()
        val fontSizeAdjustment = if (settings.syncWithEditorFont) {
            0  // 使用编辑器字体大小，不做调整
        } else {
            // 使用用户设置的绝对字体大小，而不是相对调整
            val editorFontSize = FontManager.getEditorFontInfo().fontSize
            val targetSize = settings.markdownFontSize
            // 如果目标大小合理，使用相对调整；否则使用默认调整
            if (targetSize in 8..24) {
                targetSize - editorFontSize
            } else {
                -1  // 默认稍微小一点
            }
        }
        val textFontInfo = FontManager.getMarkdownTextFontInfo(fontSizeAdjustment, settings.markdownLineSpacing)
        val scaledTextFont = FontManager.scaleFont(textFontInfo)
        val swingFont = FontManager.getSwingFont(scaledTextFont)
        
        val editor = object : JEditorPane("text/html", html) {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }.apply {
            isEditable = false
            isOpaque = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = swingFont  // 使用与编辑器一致的字体
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(editor, BorderLayout.CENTER)
        }
    }
    
    /**
     * 使用原始 HTML 渲染方式
     */
    private fun createHtmlOnlyComponent(
        markdown: String,
        config: EnhancedMarkdownRenderConfig
    ): JPanel {
        val html = toHtml(markdown, config)
        val settings = ClaudeSettings.getInstance()
        val fontSizeAdjustment = if (settings.syncWithEditorFont) {
            0  // 使用编辑器字体大小，不做调整
        } else {
            // 使用用户设置的绝对字体大小，而不是相对调整
            val editorFontSize = FontManager.getEditorFontInfo().fontSize
            val targetSize = settings.markdownFontSize
            // 如果目标大小合理，使用相对调整；否则使用默认调整
            if (targetSize in 8..24) {
                targetSize - editorFontSize
            } else {
                -1  // 默认稍微小一点
            }
        }
        val textFontInfo = FontManager.getMarkdownTextFontInfo(fontSizeAdjustment, settings.markdownLineSpacing)
        val scaledTextFont = FontManager.scaleFont(textFontInfo)
        val swingFont = FontManager.getSwingFont(scaledTextFont)
        
        val editor = object : JEditorPane("text/html", html) {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }.apply {
            isEditable = false
            isOpaque = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = swingFont  // 使用与编辑器一致的字体
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(editor, BorderLayout.CENTER)
        }
    }

    /**
     * 转换为 HTML（与原始实现保持一致）
     */
    fun toHtml(markdown: String, config: EnhancedMarkdownRenderConfig): String {
        val parser = buildParser()
        val renderer = buildRenderer()

        val document: Node = parser.parse(markdown)
        applyRestrictions(document, config)

        val htmlBody = renderer.render(document)
        val colorForeground = colorToCss(config.overrideForeground ?: JBColor.foreground())
        val colorBackground = "transparent"
        
        // 使用字体管理器获取与编辑器一致的字体设置
        val settings = ClaudeSettings.getInstance()
        val fontSizeAdjustment = if (settings.syncWithEditorFont) {
            0  // 使用编辑器字体大小，不做调整
        } else {
            // 使用用户设置的绝对字体大小，而不是相对调整
            val editorFontSize = FontManager.getEditorFontInfo().fontSize
            val targetSize = settings.markdownFontSize
            // 如果目标大小合理，使用相对调整；否则使用默认调整
            if (targetSize in 8..24) {
                targetSize - editorFontSize
            } else {
                -1  // 默认稍微小一点
            }
        }
        val textFontInfo = FontManager.getMarkdownTextFontInfo(fontSizeAdjustment, settings.markdownLineSpacing)
        val codeFontInfo = FontManager.getMarkdownCodeFontInfo()
        val inlineCodeFontInfo = FontManager.getMarkdownInlineCodeFontInfo(textFontInfo)
        
        // 应用 UI 缩放
        val scaledTextFont = FontManager.scaleFont(textFontInfo)
        val scaledCodeFont = FontManager.scaleFont(codeFontInfo)
        val scaledInlineCodeFont = FontManager.scaleFont(inlineCodeFontInfo)

        val css = """
            <style>
              body { 
                margin: 0; 
                color: $colorForeground; 
                background: $colorBackground; 
                ${FontManager.createTextCssFontStyle(scaledTextFont)}
              }
              /* 代码块样式 */
              pre { 
                ${FontManager.createCodeCssFontStyle(scaledCodeFont)}
                background-color: rgba(0,0,0,0.05); 
                padding: 6px 8px; 
                white-space: pre-wrap; 
                margin: 0.25em 0; 
                border-radius: 4px;
              }
              /* 内联代码样式 - 与正文大小协调 */
              code { 
                ${FontManager.createInlineCodeCssFontStyle(scaledInlineCodeFont)}
                background-color: rgba(0,0,0,0.06); 
                padding: 1px 3px; 
                border-radius: 2px;
              }
              /* 代码块内的 code 元素不显示背景色，避免双重背景 */
              pre code { 
                background-color: transparent !important; 
                padding: 0 !important; 
                border-radius: 0 !important;
              }
              p { 
                margin: 0.25em 0; 
                line-height: ${scaledTextFont.lineHeight};
              }
              ul, ol { 
                margin-top: 2px; 
                margin-bottom: 2px; 
                line-height: ${scaledTextFont.lineHeight};
              }
              table { 
                width: 100%; 
                border-collapse: collapse;
              }
              th, td { 
                border: 1px solid rgba(127,127,127,0.35); 
                padding: 4px 6px; 
                line-height: ${scaledTextFont.lineHeight};
              }
              a { 
                color: #6aa9ff; 
                text-decoration: none; 
              }
              h1, h2, h3, h4, h5, h6 {
                line-height: ${scaledTextFont.lineHeight * 1.2f};
                margin: 0.5em 0 0.25em 0;
              }
            </style>
        """.trimIndent()

        return """
            <html>
              <head>$css</head>
              <body>$htmlBody</body>
            </html>
        """.trimIndent()
    }

    private fun applyRestrictions(root: Node, config: EnhancedMarkdownRenderConfig) {
        if (!config.allowHeadings) {
            var node: Node? = root.firstChild
            while (node != null) {
                val next = node.next
                if (node is Heading) {
                    val paragraph = Paragraph()
                    var child = node.firstChild
                    while (child != null) {
                        val childNext = child.next
                        paragraph.appendChild(child)
                        child = childNext
                    }
                    node.insertBefore(paragraph)
                    node.unlink()
                }
                node = next
            }
        }

        if (!config.allowImages) {
            var node: Node? = root.firstChild
            while (node != null) {
                val next = node.next
                if (node is Image) {
                    val altText = node.firstChild?.chars?.toString() ?: ""
                    val replacement = Text(altText)
                    node.insertBefore(replacement)
                    node.unlink()
                }
                node = next
            }
        }
    }

    private fun colorToCss(color: java.awt.Color): String {
        val r = color.red
        val g = color.green
        val b = color.blue
        return "rgb($r,$g,$b)"
    }
}

/**
 * 内容段落基类
 */
sealed class ContentSegment {
    abstract val content: String
}

/**
 * 代码块段落
 */
data class CodeBlockSegment(
    val code: String,
    val language: String?
) : ContentSegment() {
    override val content: String get() = code
}

/**
 * 非代码块段落
 */
data class NonCodeSegment(
    override val content: String
) : ContentSegment()
