import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.0"
    id("org.jetbrains.changelog") version "2.2.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

group = "com.claudecodechat"
version = "1.0.3"

repositories {
    mavenCentral()
    
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Use IntelliJ Platform with broad version support
        intellijIdeaCommunity("2025.1")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    
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


intellijPlatform {
    pluginConfiguration {
        name = "Claude Code Chat"
        description = """
            An IntelliJ IDEA plugin for integrating Claude Code CLI with environment variable configuration.
            Features include:
            - Direct Claude Code CLI integration with custom environment variables
            - Claude executable path configuration
            - Settings management for CLI execution
            - Cross-platform support (macOS, Linux, Windows)
            - Stable Swing UI for maximum compatibility
        """.trimIndent()
        
        changeNotes = """
            <h2>1.2.0</h2>
            <ul>
                <li>Stable Swing UI for maximum compatibility across all IntelliJ versions</li>
                <li>Environment variables configuration for Claude Code CLI</li>
                <li>Custom Claude executable path setting</li>
                <li>Settings management interface</li>
                <li>Cross-platform support (macOS, Linux, Windows)</li>
            </ul>
        """.trimIndent()
        
        ideaVersion {
            sinceBuild = "251"  // IntelliJ Platform 2025.1+
            untilBuild = "999.*"  // Support all future versions including 2025.2+
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
                sinceBuild = "251"  // IntelliJ Platform 2025.1+
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
        sinceBuild = "251"  // IntelliJ Platform 2025.1+
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
}

// Add a custom task to run the session test
tasks.register<JavaExec>("runSessionTest") {
    mainClass.set("com.claudecodechat.test.SessionLoaderTestKt")
    classpath = sourceSets["test"].runtimeClasspath
    
    // Add runtime dependencies
    dependsOn("compileTestKotlin")
}

// Add a code standards verification task
tasks.register("codeStandards") {
    group = "verification"
    description = "Run detekt for code standards verification"
    dependsOn("detekt")
}

// Configuration cleanup - using IntelliJ Platform bundled modules now

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/detekt.yml")
    autoCorrect = true
    parallel = true
}

// Add detekt tasks
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// Make build depend on detekt
tasks.named("build") {
    dependsOn("detekt")
}