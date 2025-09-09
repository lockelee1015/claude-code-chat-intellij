package com.claudecodechat.ui.swing

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Global manager that installs a single Enter handler and dispatches to
 * all registered chat editors. Each ChatInputBar registers its editor and
 * a send callback. This makes Enter-to-send work across multiple tabs.
 */
object ChatEnterToSendManager {
    private val editors: MutableMap<Editor, () -> Unit> = Collections.synchronizedMap(IdentityHashMap<Editor, () -> Unit>())
    private var installed = false
    private var originalEnterHandler: EditorActionHandler? = null

    fun registerEditor(editor: Editor, onSend: () -> Unit) {
        ensureInstalled()
        editors[editor] = onSend
    }

    fun unregisterEditor(editor: Editor) {
        editors.remove(editor)
    }

    fun executeOriginalEnter(editor: Editor, caret: Caret?, dataContext: DataContext) {
        originalEnterHandler?.execute(editor, caret, dataContext)
    }

    private fun ensureInstalled() {
        if (installed) return
        val mgr = EditorActionManager.getInstance()
        val actionId = IdeActions.ACTION_EDITOR_ENTER
        originalEnterHandler = mgr.getActionHandler(actionId)

        val custom = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                val callback = editors[editor]
                if (callback != null) {
                    // If completion popup is open, keep default behavior
                    if (LookupManager.getActiveLookup(editor) != null) {
                        originalEnterHandler?.execute(editor, caret, dataContext)
                        return
                    }
                    // Enter -> send for registered chat editors
                    callback.invoke()
                    return
                }
                // Not a chat editor -> default behavior
                originalEnterHandler?.execute(editor, caret, dataContext)
            }
        }
        mgr.setActionHandler(actionId, custom)
        installed = true
    }
}
