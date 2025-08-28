# Compose Multiplatform IntelliJ Plugin Development Reference

## Key Configuration Examples

### 1. Basic IntelliJ Plugin with Compose Configuration

```kotlin
import org.jetbrains.compose.compose

plugins {
    id("org.jetbrains.intellij") version "1.3.0"
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    id("org.jetbrains.compose") version "1.0.1"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(compose.desktop.currentOs)
}

intellij {
    pluginName.set("Example plugin name")
    version.set("2021.3")
    plugins.set(listOf("org.jetbrains.compose.intellij.platform:0.1.0"))
}
```

### 2. Plugin XML Manifest

```xml
<idea-plugin>
    <id>com.jetbrains.ComposeDemoPlugin</id>
    <name>Jetpack Compose for Desktop Demo</name>
    <vendor>Demo Vendor</vendor>
    <description>...</description>
    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.compose.intellij.platform</depends>
</idea-plugin>
```

### 3. Settings Gradle Configuration

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

### 4. Build Configuration for Compose HTML

```gradle
plugins {
    kotlin("multiplatform") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.2"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
            }
        }
    }
}
```

## Important Notes

1. **Gradle Properties**: Add `kotlin.stdlib.default.dependency=false` to gradle.properties
2. **Dependencies**: Use `compileOnly` for Compose dependencies in IntelliJ plugins
3. **Platform Plugin**: Must include `org.jetbrains.compose.intellij.platform` dependency
4. **Running**: Use `./gradlew runIde` to test the plugin

## Key Commands

- `./gradlew runIde` - Run IntelliJ with the plugin
- `./gradlew buildPlugin` - Build the plugin distribution
- `./gradlew publishToMavenLocal` - Publish to local Maven repository

## Integration with Third-Party Libraries

When integrating non-Composable UI components, use:
- `DomSideEffect` for updating DOM managed by external libraries
- `DisposableRefEffect` for mounting/unmounting external UI components

## Webpack Configuration

For web targets, configure module name:
```javascript
// webpack.config.d/configModuleName.js
config.output = config.output || {};
config.output.library = "MyComposables";
```

## Testing

Add test dependencies:
```kotlin
val jsTest by getting {
    implementation(kotlin("test-js"))
    implementation(compose.html.testUtils)
}
```