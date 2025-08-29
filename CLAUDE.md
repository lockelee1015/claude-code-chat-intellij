# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Essential Commands
```bash
# Build the plugin
./gradlew buildPlugin

# Run IDE with plugin for testing
./gradlew runIde

# Compile Kotlin code
./gradlew compileKotlin

# Run tests
./gradlew test

# Clean and rebuild
./gradlew clean buildPlugin

# Verify plugin compatibility
./gradlew verifyPlugin

# Run a specific test
./gradlew test --tests "com.claudecodechat.utils.JsonUtilsTest"
```

### Release Process
1. Update version in `build.gradle.kts` (line 19)
2. Update `CHANGELOG.md` with new version details
3. Build: `./gradlew clean buildPlugin`
4. Create git tag: `git tag -a v1.0.x -m "Release version 1.0.x"`
5. Push with tags: `git push origin master --tags`
6. Plugin artifact will be in `build/distributions/claude-code-chat-{version}.zip`

## Architecture Overview

### Core Integration Pattern
This plugin integrates with Claude Code CLI through a process-based architecture:

1. **ClaudeCliService** (`cli/ClaudeCliService.kt`): Manages Claude CLI processes
   - Spawns `claude` binary with appropriate arguments
   - Handles JSONL streaming output from CLI
   - Manages process lifecycle (start, stop, kill)

2. **SessionViewModel** (`state/SessionViewModel.kt`): Central state management
   - Coordinates between UI and CLI service
   - Manages session state and message flow
   - Handles auto-resume of recent sessions
   - Integrates with file watchers for real-time updates

3. **ClaudeChatPanelFinal** (`ui/compose/ClaudeChatPanelFinal.kt`): Main UI component
   - 2600+ line Compose UI implementation
   - Handles message rendering, markdown parsing, tool display
   - Manages IDE context awareness (current file, selected lines)
   - Implements auto-completion system

### Key Architectural Decisions

#### Message Flow
```
User Input → SessionViewModel → ClaudeCliService → Claude CLI Process
                                                         ↓
UI Update ← SessionViewModel ← JSONL Parser ← Streaming Output
```

#### Session Management
- Sessions are stored in `.claude_chat_sessions/` directory
- Each session is a JSONL file with timestamped messages
- `SessionFileWatcher` monitors changes for real-time updates
- `SessionHistoryLoader` provides session discovery and preview

#### Completion System
- **SlashCommandRegistry**: Defines 20+ slash commands (`/help`, `/clear`, etc.)
- **CompletionManager**: Handles completion state and UI coordination
- File references (`@filename`) use fuzzy search with glob pattern support
- Async search to prevent UI blocking

#### IDE Context Awareness
- Tracks current open file via `FileEditorManager`
- Monitors text selection in editor (polls every 500ms)
- Automatically appends context to messages: `[IDE Context: User is in IDE and has file.kt open, User has selected lines X-Y]`
- Selected code is included in message when available

### UI Component Structure

The UI uses Jetpack Compose Desktop with a custom implementation:
- **Message rendering**: Supports user, assistant, error, and system messages
- **Markdown parsing**: Uses flexmark-java for GitHub Flavored Markdown
- **Tool display**: Special rendering for tool_use and tool_result
- **Code blocks**: Syntax highlighting with copy functionality
- **Loading states**: Animated indicators with progress bars

### Plugin Configuration

**plugin.xml** key registrations:
- Tool window: `Claude Chat` (right anchor)
- Settings: Under Tools → Claude Code Chat
- Actions: Send to Claude (Ctrl+Alt+C in editor)
- Services: ClaudeApiService (app), ChatHistoryService (project)

**build.gradle.kts** dependencies:
- IntelliJ Platform: 2024.1+ (minimum 223 build)
- Compose Desktop: 1.6.11
- Kotlin: 1.9.23 with JVM 17
- Key libs: flexmark (markdown), okhttp3 (network), kotlinx-serialization (JSON)

### Testing Approach

Tests use JUnit 5 with MockK for mocking:
- Unit tests in `src/test/kotlin/`
- Focus on JSON parsing, session metrics, hook configuration
- Run with `./gradlew test`

### Critical Files to Understand

1. **ClaudeCliService.kt**: How CLI integration works
2. **SessionViewModel.kt**: State management and message flow
3. **ClaudeChatPanelFinal.kt**: Complete UI implementation
4. **CompletionManager.kt**: Auto-completion logic
5. **SessionFileWatcher.kt**: Real-time session updates

### Common Issues and Solutions

**Claude CLI not found**: 
- Check `findClaudeBinary()` in ClaudeCliService
- Ensures `claude` is in PATH or common locations

**Compilation errors with SimpleText**:
- Private composable function defined at end of ClaudeChatPanelFinal
- Takes: text, color, fontSize, optional fontWeight/fontFamily/maxLines
- No fontStyle parameter

**Bracket balance issues**:
- ClaudeChatPanelFinal is complex with nested composables
- Use `awk` to check bracket balance when editing

**IDE context not working**:
- Requires FileEditorManager and text editor access
- Polling timer updates selection every 500ms