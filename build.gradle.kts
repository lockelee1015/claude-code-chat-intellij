import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij.platform") version "2.2.0"
    id("org.jetbrains.changelog") version "2.2.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    kotlin("plugin.serialization") version "1.9.23"
}

group = "com.claudecodechat"
version = "1.0.5"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Use IntelliJ Platform instead of specific IDE to support all JetBrains IDEs
        intellijIdeaCommunity("2024.1")
        instrumentationTools()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    
    // Compose Desktop dependencies with all platforms
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.components.resources)
    
    // Explicitly add Skiko for all platforms
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.9")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.8.9")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.9")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.8.9")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.8.9")
    
    // Kotlin Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Network and HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Markdown parsing
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    
    // Process management - using standard Java ProcessBuilder instead
    
    // File watching
    implementation("io.methvin:directory-watcher:0.18.0")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Claude Code Chat"
        description = """
            An IntelliJ IDEA plugin for integrating Claude Code CLI with a modern Compose UI.
            Features include:
            - Direct Claude Code CLI integration
            - Real-time streaming responses
            - Session management and recovery
            - Hook mechanism for security
            - Modern Jetpack Compose + Jewel UI
        """.trimIndent()
        
        changeNotes = """
            <h2>1.0.0</h2>
            <ul>
                <li>Initial release with Compose + Jewel UI</li>
                <li>Claude Code CLI integration</li>
                <li>JSONL streaming support</li>
                <li>Session management</li>
                <li>Hook mechanism for security</li>
            </ul>
        """.trimIndent()
        
        ideaVersion {
            sinceBuild = "223"
            untilBuild = "999.*"  // Support all future versions
        }
    }
    
    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }
    
    publishing {
        token = System.getenv("PUBLISH_TOKEN")
        channels = listOf("default")
    }
    
    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(
                    IntelliJPlatformType.IntellijIdeaCommunity,
                    IntelliJPlatformType.IntellijIdeaUltimate,
                    IntelliJPlatformType.GoLand,
                    IntelliJPlatformType.PyCharmCommunity,
                    IntelliJPlatformType.WebStorm
                )
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "223"
                untilBuild = "999.*"  // Support all future versions
            }
        }
    }
}

changelog {
    version = "1.0.0"
    path = file("CHANGELOG.md").absolutePath
    keepUnreleasedSection = true
    unreleasedTerm = "[Unreleased]"
    groups = listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security")
}

tasks {
    wrapper {
        gradleVersion = "8.9"
    }
    
    test {
        useJUnitPlatform()
    }
    
    patchPluginXml {
        sinceBuild = "223"
        untilBuild = "999.*"  // Support all future versions
        
        changeNotes = provider {
            val changelog = project.changelog
            val changelogItem = changelog.getOrNull(project.version.toString()) ?: changelog.getLatest()
            markdownToHTML(changelogItem.toText())
        }
    }
    
    buildSearchableOptions {
        enabled = false
    }
    
    // Ensure Skiko native libraries are included in the plugin
    prepareSandbox {
        doLast {
            // Copy Skiko native libraries to the plugin lib folder
            val skikoLibs = configurations.runtimeClasspath.get().filter { 
                it.name.contains("skiko-awt-runtime")
            }
            skikoLibs.forEach { lib ->
                copy {
                    from(zipTree(lib))
                    into("${destinationDir}/claude-code-chat/lib")
                    include("*.dylib", "*.dll", "*.so", "*.sha256")
                }
            }
        }
    }
    
    buildPlugin {
        // Ensure the plugin includes all necessary runtime dependencies
        archiveClassifier.set("")
    }
}

// Add a custom task to run the session test
tasks.register<JavaExec>("runSessionTest") {
    mainClass.set("com.claudecodechat.test.SessionLoaderTestKt")
    classpath = sourceSets["test"].runtimeClasspath
    
    // Add runtime dependencies
    dependsOn("compileTestKotlin")
}

// Ensure Compose dependencies are available
configurations.all {
    exclude(group = "org.jetbrains.compose.material", module = "material")
}