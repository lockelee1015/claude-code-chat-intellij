# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🏗️ Architecture Overview

This is an IntelliJ IDEA plugin that integrates Claude Code CLI with the IDE. The architecture follows IntelliJ Platform conventions with:

- **Swing-based UI** (`src/main/kotlin/com/claudecodechat/ui/swing/`) - Stable Swing UI components for maximum compatibility
- **CLI Integration** (`src/main/kotlin/com/claudecodechat/cli/`) - Handles Claude CLI process execution and communication
- **Completion System** (`src/main/kotlin/com/claudecodechat/completion/`) - Slash commands (`/`) and file references (`@`)
- **State Management** (`src/main/kotlin/com/claudecodechat/state/`) - Session and conversation state handling
- **Settings** (`src/main/kotlin/com/claudecodechat/settings/`) - Plugin configuration and environment variables
- **Persistence** (`src/main/kotlin/com/claudecodechat/persistence/`) - Session storage and management
- **Hook System** (`src/main/kotlin/com/claudecodechat/hooks/`) - Event handling and integration points

## 🛠️ Development Commands

### Build & Test
```bash
# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.claudecodechat.test.SessionLoaderTest"

# Run IDE for debugging
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin

# Code quality checks
./gradlew detekt
```

### Code Standards
- **Kotlin 2.1.0** with Java 17 target
- **Detekt** configured in `detekt.yml` for code quality
- **IntelliJ Platform SDK** 2025.1+ compatibility
- **Swing UI** preferred over Compose for stability

## 📁 Key Files & Locations

- **Plugin Configuration**: `src/main/resources/META-INF/plugin.xml`
- **Build Configuration**: `build.gradle.kts`
- **Settings UI**: `src/main/kotlin/com/claudecodechat/settings/ClaudeSettingsConfigurable.kt`
- **Main Tool Window**: `src/main/kotlin/com/claudecodechat/toolWindow/ClaudeChatSimpleToolWindowFactory.kt`
- **CLI Service**: `src/main/kotlin/com/claudecodechat/cli/ClaudeCliService.kt`
- **Session Management**: `src/main/kotlin/com/claudecodechat/state/SessionViewModel.kt`

## 🔧 Common Development Tasks

### Adding New Slash Commands
1. Add command to `src/main/kotlin/com/claudecodechat/completion/SlashCommandRegistry.kt`
2. Implement handler in appropriate service
3. Update completion in `src/main/kotlin/com/claudecodechat/completion/CompletionManager.kt`

### Modifying UI Components
- Use Swing components in `src/main/kotlin/com/claudecodechat/ui/swing/`
- Follow IntelliJ Platform UI guidelines
- Ensure dark/light theme compatibility

### Environment Configuration
- Settings are managed in `src/main/kotlin/com/claudecodechat/settings/ClaudeSettings.kt`
- CLI path and environment variables configurable through IDE settings

## 🚀 Deployment

- Plugin version is set in `build.gradle.kts` (currently 1.0.3)
- GitHub Actions handle CI/CD in `.github/workflows/`
- Release process uses `release.yml` workflow
- Plugin signing configured via environment variables

## 🧪 Testing Strategy

- Unit tests in `src/test/kotlin/`
- Focus on CLI integration and session management
- Mock external dependencies where appropriate
- Use `@Test` annotations with JUnit 5

## 🔍 Key Dependencies

- **IntelliJ Platform SDK** - Plugin framework
- **flexmark-java** - Markdown parsing
- **OkHttp** - HTTP client (for future enhancements)
- **Kotlin Serialization** - JSON handling
- **Kotlin Coroutines** - Async operations

## ⚠️ Important Notes

- **Swing UI**: This plugin uses Swing instead of Compose for maximum compatibility
- **CLI Dependency**: Requires Claude Code CLI installed and in PATH
- **Platform Support**: Targets IntelliJ Platform 2025.1+ (sinceBuild: 251)
- **Chinese Support**: Chinese documentation available in `README_CN.md`

Use `./gradlew runIde` to test changes in a development IDE instance.