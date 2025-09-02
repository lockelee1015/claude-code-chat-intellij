package com.claudecodechat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.Dimension

class ClaudeSettingsConfigurable : Configurable {
    private var settingsComponent: ClaudeSettingsComponent? = null
    
    override fun getDisplayName(): String {
        return "Claude Code Chat"
    }
    
    override fun createComponent(): JComponent {
        settingsComponent = ClaudeSettingsComponent()
        return settingsComponent!!.panel
    }
    
    override fun isModified(): Boolean {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return false
        
        return component.claudePath != settings.claudePath ||
                component.environmentVariables != settings.environmentVariables
    }
    
    override fun apply() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        settings.claudePath = component.claudePath
        settings.environmentVariables = component.environmentVariables
    }
    
    override fun reset() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        component.claudePath = settings.claudePath
        component.environmentVariables = settings.environmentVariables
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
    
    class ClaudeSettingsComponent {
        val panel: JPanel
        private val claudePathField = JBTextField()
        private val environmentVariablesArea = JBTextArea(5, 40)
        
        init {
            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(
                    JBLabel("Claude Executable Path (empty to use PATH):"),
                    claudePathField,
                    1,
                    false
                )
                .addLabeledComponent(
                    JBLabel("Environment Variables (KEY=VALUE, one per line):"),
                    JBScrollPane(environmentVariablesArea).apply {
                        preferredSize = Dimension(400, 100)
                    },
                    1,
                    false
                )
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
        
        var claudePath: String
            get() = claudePathField.text
            set(value) {
                claudePathField.text = value
            }
        
        var environmentVariables: String
            get() = environmentVariablesArea.text
            set(value) {
                environmentVariablesArea.text = value
            }
    }
}