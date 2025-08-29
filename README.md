# Claude Code Chat - IntelliJ Plugin

[![Build and Test](https://github.com/lockelee1015/claude-intellij/actions/workflows/build.yml/badge.svg)](https://github.com/lockelee1015/claude-intellij/actions/workflows/build.yml)
[![Plugin Verification](https://github.com/lockelee1015/claude-intellij/actions/workflows/verify.yml/badge.svg)](https://github.com/lockelee1015/claude-intellij/actions/workflows/verify.yml)

An IntelliJ IDEA plugin that integrates Claude Code CLI with a modern Compose UI, providing seamless AI-powered development assistance directly within your IDE.

## 🌟 Features

### 🤖 Claude Code Integration
- **Direct CLI Integration**: Seamlessly execute Claude Code commands
- **Real-time Streaming**: Live response streaming with JSONL support
- **Session Management**: Persistent sessions with recovery capabilities
- **Process Control**: Proper process management with stop functionality

### ✨ Auto-Completion
- **Slash Commands**: Type `/` for built-in commands (`/help`, `/clear`, `/model`, etc.)
- **File References**: Type `@` for project file search with glob patterns
- **Keyboard Navigation**: Arrow keys, Tab/Enter to select, Esc to close
- **Smart Search**: Async file search with pattern matching and caching

### 📝 Enhanced Markdown Rendering
- **GitHub Flavored Markdown**: Tables, strikethrough, task lists, autolinks
- **Syntax Highlighting**: Code blocks with language detection
- **Professional Styling**: Headers, bold, italic, inline code
- **Powered by flexmark-java**: Industry-standard markdown parsing

### 🎨 Modern UI
- **Jetpack Compose**: Modern reactive UI framework
- **IntelliJ Theme Integration**: Follows IDE color schemes
- **Responsive Design**: Adapts to different window sizes
- **Professional Styling**: Clean, IntelliJ-style interface

## 🚀 Installation

### From Releases
1. Download the latest `.zip` file from [Releases](https://github.com/lockelee1015/claude-intellij/releases)
2. In IntelliJ IDEA: `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
3. Select the downloaded ZIP file
4. Restart IntelliJ IDEA

### From Source
```bash
git clone https://github.com/lockelee1015/claude-intellij.git
cd claude-intellij/claude-code-chat
./gradlew buildPlugin
```

## 🛠️ Requirements

- **IntelliJ IDEA**: 2024.1 or later
- **JDK**: 17 or later
- **Claude Code CLI**: Installed and accessible in PATH

### Installing Claude Code CLI
```bash
npm install -g @anthropic/claude-code
# or
curl -fsSL https://get.claude.ai/install.sh | sh
```

## 📖 Usage

### Basic Commands
- **Send Message**: Type your message and click Send or press Ctrl/Cmd+Enter
- **Auto-completion**: 
  - Type `/` for slash commands
  - Type `@` for file references
- **Stop Generation**: Click the Stop button during response generation
- **Model Selection**: Use dropdown to choose between Auto, Opus, or Sonnet

### Slash Commands
- `/help` - Show available commands
- `/clear` - Clear current session
- `/model <name>` - Switch AI model
- `/session` - Session management
- `/status` - Check system status

### File References
- `@filename` - Reference specific files
- `@src/` - Reference directory contents
- `@*.kt` - Glob pattern matching

## 🏗️ Development

### Building
```bash
./gradlew build
```

### Running
```bash
./gradlew runIde
```

### Testing
```bash
./gradlew test
```

### Plugin Verification
```bash
./gradlew verifyPlugin
```

## 🔧 Configuration

The plugin automatically detects Claude Code CLI installation. If needed, configure:

1. **PATH Setup**: Ensure Claude CLI is in your system PATH
2. **Node.js**: Some installations require Node.js (v14+)
3. **Permissions**: Grant necessary file system permissions

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Commit: `git commit -m 'Add amazing feature'`
5. Push: `git push origin feature/amazing-feature`
6. Open a Pull Request

## 📋 Architecture

### Key Components
- **ClaudeChatPanelFinal**: Main UI component with Compose
- **ClaudeCliService**: Claude Code CLI process management
- **SessionViewModel**: State management and business logic
- **CompletionManager**: Auto-completion system
- **Markdown Rendering**: flexmark-java integration

### Technology Stack
- **Kotlin**: Primary development language
- **Jetpack Compose**: Modern UI framework
- **IntelliJ Platform SDK**: Plugin development
- **flexmark-java**: Markdown parsing
- **Coroutines**: Async operations

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Anthropic**: For Claude Code CLI and API
- **JetBrains**: For IntelliJ Platform SDK
- **flexmark-java**: For markdown parsing
- **Jetpack Compose**: For modern UI framework

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/lockelee1015/claude-intellij/issues)
- **Discussions**: [GitHub Discussions](https://github.com/lockelee1015/claude-intellij/discussions)
- **Claude Code**: [Official Documentation](https://docs.anthropic.com/claude/docs/claude-code)

---

**Built with ❤️ using Claude Code**