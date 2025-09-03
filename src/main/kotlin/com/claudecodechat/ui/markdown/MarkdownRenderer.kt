package com.claudecodechat.ui.markdown

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
        val baseFont = JBUI.Fonts.label()
        val fontFamily = baseFont.family

        val css = """
            <style>
              body { margin: 0; color: $colorForeground; background: $colorBackground; font-family: ${fontFamily}, sans-serif; font-size: 1em; }
              pre, code { font-family: Menlo, Monaco, Consolas, monospace; font-size: 1em; }
              /* 尽量使用 Swing 支持的 CSS 子集，避免解析异常 */
              pre { background-color: rgba(0,0,0,0.05); padding: 6px 8px; white-space: pre-wrap; margin: 0.25em 0; }
              code { background-color: rgba(0,0,0,0.06); padding: 1px 3px; }
              p { margin: 0.25em 0; }
              ul, ol { margin-top: 2px; margin-bottom: 2px; }
              table { width: 100%; }
              th, td { border: 1px solid rgba(127,127,127,0.35); padding: 4px 6px; }
              a { color: #6aa9ff; text-decoration: none; }
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
        val html = toHtml(markdown, config)
        val editor = object : JEditorPane("text/html", html) {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }.apply {
            isEditable = false
            isOpaque = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(editor, BorderLayout.CENTER)
        }
    }

    private fun colorToCss(color: java.awt.Color): String {
        val r = color.red
        val g = color.green
        val b = color.blue
        return "rgb($r,$g,$b)"
    }
}


