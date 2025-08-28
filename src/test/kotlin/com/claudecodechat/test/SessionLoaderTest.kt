package com.claudecodechat.test

import com.claudecodechat.session.SessionHistoryLoader

fun main() {
    println("Testing Session History Loader")
    println("=" * 50)
    
    val loader = SessionHistoryLoader()
    val projectPath = "/Users/lichao/workspace/github.com/lockelee1015/claude-intellij/claude-code-chat"
    val sessionId = "4dcdd156-5684-4aa0-9bd3-7f7637088316"
    
    println("Project Path: $projectPath")
    println("Session ID: $sessionId")
    println()
    
    // Test loading session
    println("Loading session...")
    val messages = loader.loadSession(projectPath, sessionId)
    
    println("Messages loaded: ${messages.size}")
    
    messages.forEach { msg ->
        println("  Type: ${msg.type}, Subtype: ${msg.subtype}")
        msg.message?.content?.forEach { content ->
            println("    Content type: ${content.type}")
            if (content.text != null) {
                println("    Text: ${content.text.take(50)}...")
            }
        }
    }
    
    // Test listing projects
    println("\nListing all projects:")
    val projects = loader.listProjects()
    projects.forEach { project ->
        println("Project: ${project.path}")
        project.sessions.forEach { session ->
            println("  - Session: ${session.id}")
        }
    }
}

operator fun String.times(n: Int): String = this.repeat(n)