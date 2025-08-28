package com.claudecodechat.debug

import com.claudecodechat.session.SessionHistoryLoader
import java.io.File

/**
 * Debug tool to test session loading
 */
object DebugSessionLoader {
    @JvmStatic
    fun main(args: Array<String>) {
        val loader = SessionHistoryLoader()
        val homeDir = System.getProperty("user.home")
        val projectsDir = File(homeDir, ".claude/projects")
        
        println("Claude projects directory: ${projectsDir.absolutePath}")
        println("Exists: ${projectsDir.exists()}")
        
        if (projectsDir.exists()) {
            println("\nProjects found:")
            projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                println("  Directory: ${dir.name}")
                
                // Try to decode the path
                val decoded = decodeProjectPath(dir.name)
                println("    Decoded path: $decoded")
                
                // List sessions in this project
                val sessions = dir.listFiles { file -> file.name.endsWith(".jsonl") }
                if (sessions?.isNotEmpty() == true) {
                    println("    Sessions:")
                    sessions.forEach { session ->
                        println("      - ${session.nameWithoutExtension} (${session.length()} bytes)")
                    }
                }
            }
        }
        
        // Test loading a specific project
        val testProjectPath = "/Users/lichao/workspace/github.com/lockelee1015/claude-intellij/claude-code-chat"
        println("\nTesting project path: $testProjectPath")
        println("Encoded: ${encodeProjectPath(testProjectPath)}")
        
        // Try to find this project
        val projects = loader.listProjects()
        println("\nProjects via loader:")
        projects.forEach { project ->
            println("  ${project.path}")
            project.sessions.forEach { session ->
                println("    - ${session.id}")
            }
        }
    }
    
    private fun decodeProjectPath(encodedPath: String): String {
        return if (encodedPath.startsWith("-")) {
            encodedPath.replace("-", "/")
        } else {
            "/" + encodedPath.replace("-", "/")
        }
    }
    
    private fun encodeProjectPath(projectPath: String): String {
        return projectPath.replace("/", "-")
    }
}