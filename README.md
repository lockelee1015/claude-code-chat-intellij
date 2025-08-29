# Claude Code Chat - IntelliJ Plugin

[![Build and Test](https://github.com/lockelee1015/claude-code-chat-intellij/actions/workflows/build.yml/badge.svg)](https://github.com/lockelee1015/claude-code-chat-intellij/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

[中文文档](README_CN.md)

An IntelliJ IDEA plugin that integrates Claude Code CLI, providing a native AI programming assistant experience.

## ✨ Key Features

### 💬 Smart Chat Interface
- **Real-time Streaming**: Supports Claude's streaming output with instant responses
- **Markdown Rendering**: Full support for GitHub Flavored Markdown including code highlighting, tables, task lists
- **Session Management**: Save, restore, and switch between sessions
- **Stop Generation**: Stop AI response generation at any time

### 🔍 Intelligent Auto-completion
- **Slash Commands**: Type `/` to trigger command suggestions
  - `/help` - Display help information
  - `/clear` - Clear current session
  - `/model` - Switch AI model
  - `/session` - Manage sessions
  - `/status` - View system status
  - 20+ built-in commands
  
- **File References**: Type `@` to quickly reference project files
  - Fuzzy search support
  - Glob pattern matching (e.g., `@src/*.kt`)
  - Async search without blocking UI
  - Smart sorting with relevant files prioritized

### 🎨 Professional UI Design
- **Native IntelliJ Style**: Seamlessly integrates with IDE interface
- **Dark/Light Theme**: Automatically follows IDE theme
- **Compose UI**: Built with latest Jetpack Compose
- **Responsive Layout**: Adapts to different window sizes

### 🚀 Advanced Features
- **Model Selection**: Switch between Auto, Opus, Sonnet models
- **Tool Usage Display**: Clear visualization of Claude's tool usage and parameters
- **Error Handling**: Friendly error messages and recovery mechanisms
- **Keyboard Shortcuts**:
  - `Ctrl/Cmd + Enter` - Send message
  - `↑/↓` - Navigate completion list
  - `Tab/Enter` - Select completion item
  - `Esc` - Close completion

## 📦 Installation

### Prerequisites
1. **IntelliJ IDEA** 2024.1 or higher
2. **Claude Code CLI** installed ([Installation Guide](https://docs.anthropic.com/claude/docs/claude-code))

### Installation Steps

#### Method 1: Download from Release
1. Download the latest `.zip` file from [Releases](https://github.com/lockelee1015/claude-code-chat-intellij/releases)
2. Open IntelliJ IDEA
3. Go to `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
4. Select the downloaded ZIP file
5. Restart IDE

#### Method 2: Build from Source
```bash
git clone https://github.com/lockelee1015/claude-code-chat-intellij.git
cd claude-code-chat-intellij/claude-code-chat
./gradlew buildPlugin
# Plugin will be generated in build/distributions/
```

## 🎯 Usage

### Opening the Plugin
1. In IntelliJ IDEA, click "Claude Chat" icon in the right toolbar
2. Or via `View` → `Tool Windows` → `Claude Chat`

### Basic Conversation
1. Type your question in the input field
2. Click "Send" or press `Ctrl/Cmd + Enter`
3. Claude will stream the response in real-time

### Using Auto-completion
- **Slash Commands**: Type `/` to automatically show command list
- **File References**: Type `@` to display project file list
- Use arrow keys to navigate, Tab or Enter to confirm

### File Reference Examples
```
@MainActivity.kt What's wrong with this file?
@src/main/ Help me refactor code in this directory
@*.xml Check formatting of all XML files
```

## 🛠️ Development

### Tech Stack
- **Kotlin** - Primary development language
- **Jetpack Compose Desktop** - UI framework
- **IntelliJ Platform SDK** - Plugin development framework
- **flexmark-java** - Markdown parsing
- **Kotlin Coroutines** - Asynchronous programming

### Project Structure
```
claude-code-chat/
├── src/main/kotlin/com/claudecodechat/
│   ├── cli/                  # Claude CLI integration
│   ├── completion/            # Auto-completion system
│   ├── models/               # Data models
│   ├── services/             # Service layer
│   ├── state/                # State management
│   └── ui/compose/           # Compose UI components
├── build.gradle.kts          # Build configuration
└── plugin.xml                # Plugin configuration
```

### Build Commands
```bash
# Build plugin
./gradlew buildPlugin

# Run IDE for debugging
./gradlew runIde

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin
```

## 🐛 Troubleshooting

### Claude CLI Not Found
1. Ensure Claude Code CLI is properly installed
2. Check if `claude` command is in PATH
3. Try running `claude --version` in terminal to verify

### Plugin Not Responding
1. Check IDE logs: `Help` → `Show Log in Explorer/Finder`
2. Ensure network connection is stable
3. Restart IDE

### Auto-completion Not Working
1. Ensure project is properly indexed
2. Wait for project indexing to complete
3. Try rebuilding index: `File` → `Invalidate Caches...`

## 📝 Changelog

### v1.0.0 (2024-12)
- ✨ Initial release
- 🎯 Full Claude Code CLI integration
- 🔍 Smart auto-completion system (slash commands + file references)
- 📝 Professional Markdown rendering (powered by flexmark-java)
- 🎨 Modern Compose UI
- ⚡ Async file search optimization
- 🛠️ Enhanced tool usage display

## 📄 License

MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Issues and Pull Requests are welcome!

### How to Contribute
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 🔗 Links

- [Claude Code Documentation](https://docs.anthropic.com/claude/docs/claude-code)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Jetpack Compose Desktop](https://www.jetbrains.com/lp/compose-desktop/)

## 👥 Author

- **Lichao Lee** - [GitHub](https://github.com/lockelee1015)

## 🙏 Acknowledgments

- [Anthropic](https://www.anthropic.com/) - For Claude Code CLI and API
- [JetBrains](https://www.jetbrains.com/) - For IntelliJ Platform SDK
- [flexmark-java](https://github.com/vsch/flexmark-java) - For Markdown parsing

---

**Built with ❤️ using Claude Code**