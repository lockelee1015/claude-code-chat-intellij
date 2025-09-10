package com.claudecodechat.ui.swing

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Global manager that wraps the Paste action for registered chat editors.
 * If clipboard contains an image, calls the provided handler and suppresses
 * the default paste; otherwise falls back to the original handler.
 */
object ChatPasteImageManager {
    private val log = Logger.getInstance(ChatPasteImageManager::class.java)
    private val editors: MutableMap<Editor, () -> Boolean> =
        Collections.synchronizedMap(IdentityHashMap<Editor, () -> Boolean>())

    private var installed = false
    private var originalPasteHandler: EditorActionHandler? = null

    fun registerEditor(editor: Editor, onPaste: () -> Boolean) {
        ensureInstalled()
        editors[editor] = onPaste
    }

    fun unregisterEditor(editor: Editor) {
        editors.remove(editor)
    }

    private fun ensureInstalled() {
        if (installed) return
        val mgr = EditorActionManager.getInstance()
        // Use the editor-specific paste action id
        val actionId = IdeActions.ACTION_EDITOR_PASTE
        originalPasteHandler = mgr.getActionHandler(actionId)

        val custom = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                val handler = editors[editor]
                if (handler != null) {
                    // If handler returns true, it handled image paste
                    val handled = runCatching { handler.invoke() }.getOrDefault(false)
                    if (handled) {
                        if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) {
                            log.info("Editor paste intercepted by ChatPasteImageManager: handled=true")
                        }
                        return
                    }
                    if (com.claudecodechat.settings.ClaudeSettings.getInstance().debugMode) {
                        log.info("Editor paste intercepted by ChatPasteImageManager: handled=false -> falling back")
                    }
                }
                // Fallback to original paste behavior
                originalPasteHandler?.execute(editor, caret, dataContext)
            }
        }
        mgr.setActionHandler(actionId, custom)
        installed = true
    }
}
