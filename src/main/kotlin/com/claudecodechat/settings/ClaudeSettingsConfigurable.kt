package com.claudecodechat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.JBIntSpinner
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
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
                component.debugMode != settings.debugMode ||
                component.maxMessagesPerSession != settings.maxMessagesPerSession ||
                component.useEnhancedCodeBlocks != settings.useEnhancedCodeBlocks ||
                component.showCodeBlockLineNumbers != settings.showCodeBlockLineNumbers ||
                component.maxCodeBlockHeight != settings.maxCodeBlockHeight ||
                component.syncWithEditorFont != settings.syncWithEditorFont ||
                component.markdownLineSpacing != settings.markdownLineSpacing
    }
    
    override fun apply() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        settings.claudePath = component.claudePath
        settings.environmentVariables = component.environmentVariables
        settings.markdownFontSize = component.markdownFontSize
        settings.debugMode = component.debugMode
        settings.maxMessagesPerSession = component.maxMessagesPerSession
        settings.useEnhancedCodeBlocks = component.useEnhancedCodeBlocks
        settings.showCodeBlockLineNumbers = component.showCodeBlockLineNumbers
        settings.maxCodeBlockHeight = component.maxCodeBlockHeight
        settings.syncWithEditorFont = component.syncWithEditorFont
        settings.markdownLineSpacing = component.markdownLineSpacing
    }
    
    override fun reset() {
        val settings = ClaudeSettings.getInstance()
        val component = settingsComponent ?: return
        
        component.claudePath = settings.claudePath
        component.environmentVariables = settings.environmentVariables
        component.markdownFontSize = settings.markdownFontSize
        component.debugMode = settings.debugMode
        component.maxMessagesPerSession = settings.maxMessagesPerSession
        component.useEnhancedCodeBlocks = settings.useEnhancedCodeBlocks
        component.showCodeBlockLineNumbers = settings.showCodeBlockLineNumbers
        component.maxCodeBlockHeight = settings.maxCodeBlockHeight
        component.syncWithEditorFont = settings.syncWithEditorFont
        component.markdownLineSpacing = settings.markdownLineSpacing
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
    
    class ClaudeSettingsComponent {
        val panel: JPanel
        private val claudePathField = JBTextField()
        private val environmentVariablesArea = JBTextArea(5, 40)
        private val markdownFontSizeSpinner = JBIntSpinner(10, 8, 24)
        private val debugModeCheckBox = JBCheckBox("Enable debug mode (show tool IDs and debug info)")
        private val maxMessagesSpinner = JBIntSpinner(100, 10, 1000)
        private val enhancedCodeBlocksCheckBox = JBCheckBox("Use IntelliJ editor for code blocks (with syntax highlighting)")
        private val showLineNumbersCheckBox = JBCheckBox("Show line numbers in code blocks")
        private val maxCodeBlockHeightSpinner = JBIntSpinner(300, 100, 800)
        private val syncWithEditorFontCheckBox = JBCheckBox("Sync markdown fonts with editor fonts")
        private val markdownLineSpacingSpinner = JSpinner(SpinnerNumberModel(1.4, 1.0, 2.5, 0.1))
        
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
                .addLabeledComponent(
                    JBLabel("Max Messages Per Session (10-1000):"),
                    maxMessagesSpinner,
                    1,
                    false
                )
                .addComponent(debugModeCheckBox, 1)
                .addSeparator(2)
                .addComponent(JBLabel("Code Block Display Options:"), 1)
                .addComponent(enhancedCodeBlocksCheckBox, 1)
                .addComponent(showLineNumbersCheckBox, 1)
                .addLabeledComponent(
                    JBLabel("Max Code Block Height (100-800px):"),
                    maxCodeBlockHeightSpinner,
                    1,
                    false
                )
                .addComponent(syncWithEditorFontCheckBox, 1)
                .addLabeledComponent(
                    JBLabel("Markdown Line Spacing (1.0-2.5):"),
                    markdownLineSpacingSpinner,
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
        
        var maxMessagesPerSession: Int
            get() = maxMessagesSpinner.number
            set(value) {
                maxMessagesSpinner.number = value
            }
        
        var useEnhancedCodeBlocks: Boolean
            get() = enhancedCodeBlocksCheckBox.isSelected
            set(value) {
                enhancedCodeBlocksCheckBox.isSelected = value
            }
        
        var showCodeBlockLineNumbers: Boolean
            get() = showLineNumbersCheckBox.isSelected
            set(value) {
                showLineNumbersCheckBox.isSelected = value
            }
        
        var maxCodeBlockHeight: Int
            get() = maxCodeBlockHeightSpinner.number
            set(value) {
                maxCodeBlockHeightSpinner.number = value
            }
        
        var syncWithEditorFont: Boolean
            get() = syncWithEditorFontCheckBox.isSelected
            set(value) {
                syncWithEditorFontCheckBox.isSelected = value
            }
        
        var markdownLineSpacing: Float
            get() = (markdownLineSpacingSpinner.value as Number).toFloat()
            set(value) {
                markdownLineSpacingSpinner.value = value
            }
    }
}
