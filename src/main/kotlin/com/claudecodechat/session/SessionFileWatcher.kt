package com.claudecodechat.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.*
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches Claude session files for changes and notifies listeners
 */
class SessionFileWatcher(private val project: Project) : Disposable {
    
    private val logger = Logger.getInstance(SessionFileWatcher::class.java)
    private val listeners = ConcurrentHashMap<String, SessionChangeListener>()
    private var virtualFileListener: VirtualFileListener? = null
    
    interface SessionChangeListener {
        fun onSessionUpdated(sessionId: String, file: VirtualFile)
        fun onSessionDeleted(sessionId: String)
    }
    
    init {
        setupFileListener()
    }
    
    private fun setupFileListener() {
        virtualFileListener = object : VirtualFileListener {
            override fun contentsChanged(event: VirtualFileEvent) {
                handleFileChange(event.file)
            }
            
            override fun fileCreated(event: VirtualFileEvent) {
                handleFileChange(event.file)
            }
            
            override fun fileDeleted(event: VirtualFileEvent) {
                if (isSessionFile(event.file)) {
                    val sessionId = event.file.nameWithoutExtension
                    notifySessionDeleted(sessionId)
                }
            }
            
            override fun fileMoved(event: VirtualFileMoveEvent) {
                handleFileChange(event.file)
            }
        }
        
        VirtualFileManager.getInstance().addVirtualFileListener(virtualFileListener!!, this)
    }
    
    private fun handleFileChange(file: VirtualFile) {
        if (isSessionFile(file) && isProjectSession(file)) {
            val sessionId = file.nameWithoutExtension
            notifySessionUpdated(sessionId, file)
        }
    }
    
    private fun isSessionFile(file: VirtualFile): Boolean {
        return file.extension == "jsonl" && 
               file.path.contains(".claude/projects")
    }
    
    private fun isProjectSession(file: VirtualFile): Boolean {
        val projectPath = project.basePath ?: return false
        SessionHistoryLoader()
        
        // Check if this file belongs to the current project
        val filePath = File(file.path)
        val projectDir = filePath.parentFile
        
        return try {
            val decodedPath = decodeProjectPath(projectDir.name)
            decodedPath == projectPath
        } catch (e: Exception) {
            false
        }
    }
    
    private fun decodeProjectPath(encodedPath: String): String {
        return if (encodedPath.startsWith("-")) {
            "/" + encodedPath.substring(1).replace("-", "/")
        } else {
            encodedPath.replace("-", "/")
        }
    }
    
    /**
     * Watch a specific session for changes
     */
    fun watchSession(sessionId: String, listener: SessionChangeListener) {
        listeners[sessionId] = listener
        logger.info("Started watching session: $sessionId")
    }
    
    /**
     * Stop watching a session
     */
    fun unwatchSession(sessionId: String) {
        listeners.remove(sessionId)
        logger.info("Stopped watching session: $sessionId")
    }
    
    /**
     * Watch current project's sessions directory
     */
    fun watchProjectSessions(listener: SessionChangeListener) {
        project.basePath ?: return
        
        // Find the project directory in Claude's projects folder
        val homeDir = System.getProperty("user.home")
        val projectsDir = File(homeDir, ".claude/projects")
        
        if (projectsDir.exists()) {
            // Use a generic key for project-wide watching
            listeners["PROJECT_WATCH"] = listener
            
            // Request file system refresh for the directory
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(projectsDir)
            virtualFile?.refresh(true, true)
        }
    }
    
    private fun notifySessionUpdated(sessionId: String, file: VirtualFile) {
        // Notify specific session listener
        listeners[sessionId]?.onSessionUpdated(sessionId, file)
        
        // Notify project-wide listener
        listeners["PROJECT_WATCH"]?.onSessionUpdated(sessionId, file)
    }
    
    private fun notifySessionDeleted(sessionId: String) {
        // Notify specific session listener
        listeners[sessionId]?.onSessionDeleted(sessionId)
        
        // Notify project-wide listener
        listeners["PROJECT_WATCH"]?.onSessionDeleted(sessionId)
        
        // Remove the listener for deleted session
        listeners.remove(sessionId)
    }
    
    override fun dispose() {
        listeners.clear()
        virtualFileListener?.let {
            VirtualFileManager.getInstance().removeVirtualFileListener(it)
        }
    }
}
