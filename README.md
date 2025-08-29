# Claude Code Chat - IntelliJ 插件

一个将 Claude Code CLI 集成到 IntelliJ IDEA 的插件，提供原生的 AI 编程助手体验。

## ✨ 主要功能

### 💬 智能对话界面
- **实时流式响应**：支持 Claude 的流式输出，响应即时显示
- **Markdown 渲染**：完整支持 GitHub Flavored Markdown，包括代码高亮、表格、任务列表等
- **会话管理**：支持会话保存、恢复和切换
- **停止生成**：随时停止 AI 响应生成

### 🔍 智能自动补全
- **斜杠命令**：输入 `/` 触发命令提示
  - `/help` - 显示帮助信息
  - `/clear` - 清空当前会话
  - `/model` - 切换 AI 模型
  - `/session` - 管理会话
  - `/status` - 查看系统状态
  - 还有 20+ 内置命令
  
- **文件引用**：输入 `@` 快速引用项目文件
  - 支持模糊搜索
  - 支持 glob 模式匹配（如 `@src/*.kt`）
  - 异步搜索，不阻塞 UI
  - 智能排序，优先显示相关文件

### 🎨 专业的 UI 设计
- **IntelliJ 原生风格**：完美融入 IDE 界面
- **暗色/亮色主题**：自动跟随 IDE 主题
- **Compose UI**：使用最新的 Jetpack Compose 构建
- **响应式布局**：自适应不同窗口大小

### 🚀 高级功能
- **模型选择**：支持 Auto、Opus、Sonnet 模型切换
- **工具调用展示**：清晰显示 Claude 使用的工具和参数
- **错误处理**：友好的错误提示和恢复机制
- **键盘快捷键**：
  - `Ctrl/Cmd + Enter` - 发送消息
  - `↑/↓` - 导航补全列表
  - `Tab/Enter` - 选择补全项
  - `Esc` - 关闭补全

## 📦 安装

### 前置要求
1. **IntelliJ IDEA** 2024.1 或更高版本
2. **Claude Code CLI** 已安装（[安装说明](https://docs.anthropic.com/claude/docs/claude-code)）

### 安装步骤

#### 方法一：从 Release 下载
1. 从 [Releases](https://github.com/lockelee1015/claude-code-chat-intellij/releases) 下载最新的 `.zip` 文件
2. 打开 IntelliJ IDEA
3. 进入 `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
4. 选择下载的 ZIP 文件
5. 重启 IDE

#### 方法二：从源码构建
```bash
git clone https://github.com/lockelee1015/claude-code-chat-intellij.git
cd claude-code-chat-intellij/claude-code-chat
./gradlew buildPlugin
# 插件将生成在 build/distributions/ 目录
```

## 🎯 使用方法

### 打开插件
1. 在 IntelliJ IDEA 中，点击右侧工具栏的 "Claude Chat" 图标
2. 或通过 `View` → `Tool Windows` → `Claude Chat`

### 基本对话
1. 在输入框中输入你的问题
2. 点击 "Send" 或按 `Ctrl/Cmd + Enter` 发送
3. Claude 会实时流式返回响应

### 使用自动补全
- **斜杠命令**：输入 `/` 后会自动弹出命令列表
- **文件引用**：输入 `@` 后会显示项目文件列表
- 使用方向键选择，Tab 或 Enter 确认

### 引用文件示例
```
@MainActivity.kt 这个文件有什么问题？
@src/main/ 帮我重构这个目录下的代码
@*.xml 检查所有 XML 文件的格式
```

## 🛠️ 开发

### 技术栈
- **Kotlin** - 主要开发语言
- **Jetpack Compose Desktop** - UI 框架
- **IntelliJ Platform SDK** - 插件开发框架
- **flexmark-java** - Markdown 解析
- **Kotlin Coroutines** - 异步编程

### 项目结构
```
claude-code-chat/
├── src/main/kotlin/com/claudecodechat/
│   ├── cli/                  # Claude CLI 集成
│   ├── completion/            # 自动补全系统
│   ├── models/               # 数据模型
│   ├── services/             # 服务层
│   ├── state/                # 状态管理
│   └── ui/compose/           # Compose UI 组件
├── build.gradle.kts          # 构建配置
└── plugin.xml                # 插件配置
```

### 构建命令
```bash
# 构建插件
./gradlew buildPlugin

# 运行 IDE 进行调试
./gradlew runIde

# 运行测试
./gradlew test

# 验证插件兼容性
./gradlew verifyPlugin
```

## 🐛 问题排查

### Claude CLI 找不到
1. 确保 Claude Code CLI 已正确安装
2. 检查 PATH 环境变量是否包含 claude 命令
3. 尝试在终端运行 `claude --version` 验证安装

### 插件无响应
1. 检查 IDE 日志：`Help` → `Show Log in Explorer/Finder`
2. 确保网络连接正常
3. 重启 IDE

### 自动补全不工作
1. 确保项目已正确索引
2. 等待项目索引完成
3. 尝试重建项目索引：`File` → `Invalidate Caches...`

## 📝 更新日志

### v1.0.0 (2024-12)
- ✨ 初始版本发布
- 🎯 完整的 Claude Code CLI 集成
- 🔍 智能自动补全系统（斜杠命令 + 文件引用）
- 📝 专业的 Markdown 渲染（使用 flexmark-java）
- 🎨 现代化 Compose UI
- ⚡ 异步文件搜索优化
- 🛠️ 改进的工具调用展示

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 🔗 相关链接

- [Claude Code 官方文档](https://docs.anthropic.com/claude/docs/claude-code)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Jetpack Compose Desktop](https://www.jetbrains.com/lp/compose-desktop/)