package com.claudecodechat.ui.markdown

import com.claudecodechat.settings.ClaudeSettings
import com.claudecodechat.ui.fonts.FontManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.vladsch.flexmark.ast.Heading
import com.vladsch.flexmark.ast.Image
import com.vladsch.flexmark.ast.Paragraph
import com.vladsch.flexmark.ast.Text
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.html.HtmlRenderer
import javax.swing.JEditorPane
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Color

/**
 * Markdown rendering utility backed by flexmark.
 * Supports configurable feature toggles (e.g., disable headings).
 */
data class MarkdownRenderConfig(
    val allowHeadings: Boolean = false,
    val allowImages: Boolean = false,
    val overrideForeground: Color? = null,
    val fontScale: Float? = null,
    val useEnhancedCodeBlocks: Boolean = false,  // 是否使用增强的代码块渲染
)

object MarkdownRenderer {
    private val baseOptions: MutableDataSet = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
        ))
    }

    private fun buildParser(): Parser = Parser.builder(baseOptions).build()
    private fun buildRenderer(): HtmlRenderer = HtmlRenderer.builder(baseOptions).build()

    fun toHtml(markdown: String, config: MarkdownRenderConfig = MarkdownRenderConfig()): String {
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
                background-color: rgb(240,240,240); 
                padding: 6px 8px; 
                margin: 4px 0; 
              }
              /* 内联代码样式 - 与正文大小协调 */
              code { 
                ${FontManager.createInlineCodeCssFontStyle(scaledInlineCodeFont)}
                background-color: rgb(230,230,230); 
                padding: 1px 3px; 
              }
              /* 代码块内的 code 元素不显示背景色，避免双重背景 */
              pre code { 
                background-color: transparent; 
                padding: 0;
              }
              p { margin: 4px 0; }
              ul, ol { margin-top: 2px; margin-bottom: 2px; }
              table { 
                width: 100%; 
                border-collapse: collapse;
              }
              th, td { border: 1px solid rgb(200,200,200); padding: 4px 6px; }
              a { 
                color: #6aa9ff; 
                text-decoration: none; 
              }
              h1, h2, h3, h4, h5, h6 { margin: 8px 0 4px 0; }
            </style>
        """.trimIndent()

        return """
            <html>
              <head>$css</head>
              <body>$htmlBody</body>
            </html>
        """.trimIndent()
    }

    private fun applyRestrictions(root: Node, config: MarkdownRenderConfig) {
        if (!config.allowHeadings) {
            var node: Node? = root.firstChild
            while (node != null) {
                val next = node.next
                if (node is Heading) {
                    val paragraph = Paragraph()
                    // Move children of heading into paragraph
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

    fun createComponent(markdown: String, config: MarkdownRenderConfig = MarkdownRenderConfig()): JPanel {
        val settings = ClaudeSettings.getInstance()
        
        // 如果启用了增强代码块渲染（从配置或设置中获取）
        val useEnhanced = config.useEnhancedCodeBlocks || settings.useEnhancedCodeBlocks
        if (useEnhanced) {
            val enhancedConfig = EnhancedMarkdownRenderConfig(
                allowHeadings = config.allowHeadings,
                allowImages = config.allowImages,
                overrideForeground = config.overrideForeground,
                fontScale = config.fontScale,
                useIntelliJCodeBlocks = true,
                showLineNumbers = settings.showCodeBlockLineNumbers,
                maxCodeBlockHeight = settings.maxCodeBlockHeight
            )
            return EnhancedMarkdownRenderer.createComponent(markdown, enhancedConfig)
        }
        
        // 使用原始的 HTML 渲染方式
        val html = toHtml(markdown, config)
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

    private fun colorToCss(color: Color): String {
        val r = color.red
        val g = color.green
        val b = color.blue
        return "rgb($r,$g,$b)"
    }
}

