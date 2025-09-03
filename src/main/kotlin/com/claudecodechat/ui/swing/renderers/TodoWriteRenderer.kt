package com.claudecodechat.ui.swing.renderers

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Renderer for TodoWrite tool
 */
class TodoWriteRenderer : ToolRenderer() {
    
    companion object {
        // Load custom todo icons (already sized at 16x16)
        private val TODO_ICON: Icon = IconLoader.getIcon("/icons/todo.svg", TodoWriteRenderer::class.java)
        private val TODO_DOING_ICON: Icon = IconLoader.getIcon("/icons/todo-doing.svg", TodoWriteRenderer::class.java)
        private val TODO_FINISH_ICON: Icon = IconLoader.getIcon("/icons/todo-finish.svg", TodoWriteRenderer::class.java)
    }
    
    override fun canHandle(toolName: String): Boolean {
        return toolName.lowercase() == "todowrite"
    }
    
    override fun renderContent(input: ToolRenderInput): JPanel {
        return when (input.status) {
            ToolStatus.ERROR -> createErrorPanel(input.toolOutput)
            else -> createTodoListPanel(input.toolInput)
        }
    }
    
    private fun createErrorPanel(errorMessage: String): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
            
            val errorLabel = JBLabel(errorMessage).apply {
                foreground = Color(220, 38, 38)
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            }
            
            add(errorLabel, BorderLayout.CENTER)
        }
    }
    
    private fun createTodoListPanel(input: JsonElement?): JPanel {
        val panel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
            border = JBUI.Borders.empty(8)
        }
        
        // Add header
        val headerLabel = JBLabel("Todos have been modified successfully.").apply {
            foreground = JBColor.foreground()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(headerLabel)
        panel.add(Box.createVerticalStrut(8))
        
        try {
            if (input is JsonObject) {
                val todosArray = input["todos"] as? JsonArray
                if (todosArray != null) {
                    for (todoElement in todosArray) {
                        if (todoElement is JsonObject) {
                            val content = todoElement["content"]?.jsonPrimitive?.content ?: ""
                            val status = todoElement["status"]?.jsonPrimitive?.content ?: "pending"
                            
                            if (content.isNotEmpty()) {
                                val todoItem = createTodoItemComponent(content, status)
                                todoItem.maximumSize = Dimension(Int.MAX_VALUE, todoItem.preferredSize.height)
                                panel.add(todoItem)
                                panel.add(Box.createVerticalStrut(4))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val errorLabel = JBLabel("Failed to parse todo items: ${e.message}").apply {
                foreground = Color(220, 38, 38)
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel.add(errorLabel)
        }
        
        return panel
    }
    
    private fun createTodoItemComponent(content: String, status: String): JPanel {
        val itemPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.background()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        
        // Get appropriate icon based on status using custom todo icons
        val icon = when (status) {
            "completed" -> TODO_FINISH_ICON      // 绿色勾选圆圈
            "in_progress" -> TODO_DOING_ICON     // 蓝色圆圈
            "cancelled" -> AllIcons.Actions.Cancel  // 保留取消图标
            else -> TODO_ICON                    // 灰色圆圈 (更明显的待办状态)
        }
        
        val iconLabel = JBLabel(icon).apply {
            border = JBUI.Borders.empty(0, 0, 0, 8)
            verticalAlignment = SwingConstants.TOP
        }
        
        val contentLabel = JBLabel(content).apply {
            foreground = JBColor.foreground()
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            verticalAlignment = SwingConstants.TOP
        }
        
        itemPanel.add(iconLabel, BorderLayout.WEST)
        itemPanel.add(contentLabel, BorderLayout.CENTER)
        
        return itemPanel
    }
}
