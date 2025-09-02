import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij.platform") version "2.2.0"
    id("org.jetbrains.changelog") version "2.2.0"
    // Temporary: Use external Compose while testing Jewel bridge integration
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    kotlin("plugin.serialization") version "1.9.23"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

group = "com.claudecodechat"
version = "1.0.3"

repositories {
    mavenCentral()
    google()
    // Temporary: Keep external Compose repos while testing Jewel bridge
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kmp/public/")
    
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Use IntelliJ Platform instead of specific IDE to support all JetBrains IDEs
        // Upgrade to 2025.1 for Jewel bundled modules support (251.2+)
        intellijIdeaCommunity("2025.1")
        // instrumentationTools() is deprecated and no longer necessary
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        
        // IntelliJ Platform bundled Jewel modules for bridge theme
        // Note: These modules are available in IntelliJ Platform 2025.1+ (251.2+)
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
    }
    
    // Temporary: External Compose dependencies while testing bridge integration
    implementation(compose.desktop.currentOs)
    
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

// Temporary: Compose Desktop configuration while testing bridge integration
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
            sinceBuild = "251"  // IntelliJ Platform 2025.1+ (required for Jewel bundled modules)
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
                sinceBuild = "251"  // IntelliJ Platform 2025.1+ (required for Jewel bundled modules)
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
        sinceBuild = "251"  // IntelliJ Platform 2025.1+ (required for Jewel bundled modules)
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