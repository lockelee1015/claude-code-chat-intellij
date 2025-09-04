package com.claudecodechat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.JBIntSpinner
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
                component.environmentVariables != settings.environmentVariables ||
                component.markdownFontSize != settings.markdownFontSize ||
                component.debugMode != settings.debugMode
    }
    
    override fun apply() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        settings.claudePath = component.claudePath
        settings.environmentVariables = component.environmentVariables
        settings.markdownFontSize = component.markdownFontSize
        settings.debugMode = component.debugMode
    }
    
    override fun reset() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        component.claudePath = settings.claudePath
        component.environmentVariables = settings.environmentVariables
        component.markdownFontSize = settings.markdownFontSize
        component.debugMode = settings.debugMode
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
    
    class ClaudeSettingsComponent {
        val panel: JPanel
        private val claudePathField = JBTextField()
        private val environmentVariablesArea = JBTextArea(5, 40)
        private val markdownFontSizeSpinner = JBIntSpinner(11, 8, 24)
        private val debugModeCheckBox = JBCheckBox("Enable debug mode (show tool IDs and debug info)")
        
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
                .addLabeledComponent(
                    JBLabel("Markdown Font Size (8-24px):"),
                    markdownFontSizeSpinner,
                    1,
                    false
                )
                .addComponent(debugModeCheckBox, 1)
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
        
        var markdownFontSize: Int
            get() = markdownFontSizeSpinner.number
            set(value) {
                markdownFontSizeSpinner.number = value
            }
        
        var debugMode: Boolean
            get() = debugModeCheckBox.isSelected
            set(value) {
                debugModeCheckBox.isSelected = value
            }
    }
}