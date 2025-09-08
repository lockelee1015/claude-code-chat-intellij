package com.claudecodechat.completion

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import javax.swing.Icon

/**
 * Custom file type for Claude Code Chat input with PSI support
 */
class ChatFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {

    init {
        println("=== DEBUG: ChatFileType initialized ===")
    }

    companion object {
        val INSTANCE = ChatFileType()
        const val EXTENSION = "ccpinput"
    }

    override fun getName(): String = "Chat"

    override fun getDescription(): String = "Chat input file"

    override fun getDefaultExtension(): String = "chat"

    override fun getIcon(): Icon? = null // 可以使用插件图标
}