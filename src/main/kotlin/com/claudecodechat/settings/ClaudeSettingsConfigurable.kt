package com.claudecodechat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

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
        
        return component.apiKey != settings.apiKey ||
                component.model != settings.model ||
                component.maxTokens != settings.maxTokens ||
                component.temperature != settings.temperature
    }
    
    override fun apply() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        settings.apiKey = component.apiKey
        settings.model = component.model
        settings.maxTokens = component.maxTokens
        settings.temperature = component.temperature
    }
    
    override fun reset() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        component.apiKey = settings.apiKey
        component.model = settings.model
        component.maxTokens = settings.maxTokens
        component.temperature = settings.temperature
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
    
    class ClaudeSettingsComponent {
        val panel: JPanel
        private val apiKeyField = JBPasswordField()
        private val modelComboBox = ComboBox(arrayOf(
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307",
            "claude-2.1",
            "claude-2.0"
        ))
        private val maxTokensField = JBTextField()
        private val temperatureField = JBTextField()
        
        init {
            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(
                    JBLabel("API Key:"),
                    apiKeyField,
                    1,
                    false
                )
                .addLabeledComponent(
                    JBLabel("Model:"),
                    modelComboBox,
                    1,
                    false
                )
                .addLabeledComponent(
                    JBLabel("Max Tokens:"),
                    maxTokensField,
                    1,
                    false
                )
                .addLabeledComponent(
                    JBLabel("Temperature (0.0 - 1.0):"),
                    temperatureField,
                    1,
                    false
                )
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
        
        var apiKey: String
            get() = String(apiKeyField.password)
            set(value) {
                apiKeyField.text = value
            }
        
        var model: String
            get() = modelComboBox.selectedItem as? String ?: "claude-3-sonnet-20240229"
            set(value) {
                modelComboBox.selectedItem = value
            }
        
        var maxTokens: Int
            get() = maxTokensField.text.toIntOrNull() ?: 4096
            set(value) {
                maxTokensField.text = value.toString()
            }
        
        var temperature: Double
            get() = temperatureField.text.toDoubleOrNull() ?: 0.7
            set(value) {
                temperatureField.text = value.toString()
            }
    }
}