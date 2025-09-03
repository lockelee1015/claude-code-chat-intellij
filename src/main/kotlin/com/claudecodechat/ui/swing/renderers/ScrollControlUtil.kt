package com.claudecodechat.ui.swing.renderers

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelListener
import javax.swing.JComponent
import javax.swing.JScrollPane

/**
 * Utility for controlling scroll behavior in tool renderers
 */
object ScrollControlUtil {
    
    /**
     * Disable scrolling by default, enable on click and disable on mouse exit
     */
    fun disableScrollingByDefault(component: JComponent) {
        var scrollingEnabled = false
        
        // Create overlay to capture initial clicks
        val clickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!scrollingEnabled) {
                    scrollingEnabled = true
                    enableScrolling(component)
                    // Add visual feedback
                    component.border = JBUI.Borders.customLine(
                        JBColor.namedColor("Component.focusedBorderColor", JBColor.BLUE), 1
                    )
                }
            }
            
            override fun mouseExited(e: MouseEvent) {
                if (scrollingEnabled) {
                    scrollingEnabled = false
                    disableScrolling(component)
                    // Remove visual feedback
                    component.border = JBUI.Borders.empty()
                }
            }
        }
        
        // Apply to the component and its children
        addClickListenerRecursively(component, clickListener)
        
        // Initially disable scrolling
        disableScrolling(component)
    }
    
    /**
     * Add click listener to component and its children recursively
     */
    private fun addClickListenerRecursively(component: JComponent, listener: MouseAdapter) {
        component.addMouseListener(listener)
        
        for (child in component.components) {
            if (child is JComponent) {
                addClickListenerRecursively(child, listener)
            }
        }
    }
    
    /**
     * Disable scrolling on component
     */
    private fun disableScrolling(component: JComponent) {
        when (component) {
            is JScrollPane -> {
                component.verticalScrollBar.isEnabled = false
                component.horizontalScrollBar.isEnabled = false
                // Disable mouse wheel scrolling by removing wheel listeners
                disableWheelScrolling(component)
            }
            else -> {
                // For console components or other scrollable components
                disableScrollingRecursively(component)
            }
        }
    }
    
    /**
     * Enable scrolling on component
     */
    private fun enableScrolling(component: JComponent) {
        when (component) {
            is JScrollPane -> {
                component.verticalScrollBar.isEnabled = true
                component.horizontalScrollBar.isEnabled = true
                // Re-enable mouse wheel scrolling
                enableWheelScrolling(component)
            }
            else -> {
                // For console components or other scrollable components
                enableScrollingRecursively(component)
            }
        }
    }
    
    /**
     * Recursively disable scrolling in child components
     */
    private fun disableScrollingRecursively(component: JComponent) {
        if (component is JScrollPane) {
            component.verticalScrollBar.isEnabled = false
            component.horizontalScrollBar.isEnabled = false
            disableWheelScrolling(component)
        }
        
        for (child in component.components) {
            if (child is JComponent) {
                disableScrollingRecursively(child)
            }
        }
    }
    
    /**
     * Recursively enable scrolling in child components
     */
    private fun enableScrollingRecursively(component: JComponent) {
        if (component is JScrollPane) {
            component.verticalScrollBar.isEnabled = true
            component.horizontalScrollBar.isEnabled = true
            enableWheelScrolling(component)
        }
        
        for (child in component.components) {
            if (child is JComponent) {
                enableScrollingRecursively(child)
            }
        }
    }
    
    /**
     * Disable mouse wheel scrolling on component
     */
    private fun disableWheelScrolling(component: JScrollPane) {
        // Store original listeners for restoration
        val originalListeners = component.mouseWheelListeners
        component.putClientProperty("originalWheelListeners", originalListeners)
        
        // Remove all wheel listeners
        for (listener in originalListeners) {
            component.removeMouseWheelListener(listener)
        }
    }
    
    /**
     * Enable mouse wheel scrolling on component
     */
    private fun enableWheelScrolling(component: JScrollPane) {
        // Restore original listeners
        @Suppress("UNCHECKED_CAST")
        val originalListeners = component.getClientProperty("originalWheelListeners") as? Array<MouseWheelListener>
        originalListeners?.forEach { listener ->
            component.addMouseWheelListener(listener)
        }
    }
}
