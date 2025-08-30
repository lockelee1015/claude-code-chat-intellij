# Claude Code Chat Changelog

## [Unreleased]

## [1.0.5] - 2025-08-31
### Fixed
- Fixed Skiko library loading issue on macOS ARM64 (M1/M2)
- Added all platform-specific Skiko native libraries to plugin package
- Ensured proper packaging of .dylib, .dll, .so files and their checksums

## [1.0.4] - 2025-08-29
### Added
- Image paste support - users can now paste screenshots directly into the input field
- Image preview cards with thumbnails and remove buttons
- Support for sending images to Claude CLI using JSON format
- Detailed logging for image transmission to help with debugging
- GitHub Actions disk space cleanup to fix verification issues

### Improved
- Reduced image preview card size for better UI space utilization
- Enhanced error logging for CLI commands with image data

### Fixed
- Image data not being sent to Claude CLI correctly
- GitHub Actions verification failing due to insufficient disk space

## [1.0.3] - 2025-08-29
### Added
- IDE context awareness - automatically includes current file and selected lines in messages
- Selected lines indicator in input area showing which lines are selected
- Visual indicators for current file and selected lines in the UI
- IDE context display in user messages with secondary color styling

### Improved
- Loading indicator now shows only first line of user input for cleaner display
- Better spacing between model selector and send button (12dp)
- Edit tool renderer with diff-style display for file changes
- Replaced emoji icons with letter-based icons for consistent color scheme

### Fixed
- Compilation errors and bracket balance issues
- Model selector positioning moved to right side next to send button

## [1.0.2] - 2025-08-29
### Added
- Improved Edit tool rendering with diff-style display
- Better visual hierarchy in UI components

### Fixed
- Version compatibility issues
- Added session.txt to gitignore

## [1.0.1] - 2025-08-28
### Fixed
- Compatibility improvements for broader IntelliJ version support

## [1.0.0] - 2025-08-28
### Added
- Initial release of Claude Code Chat plugin
- Interactive chat interface with Claude AI
- Tool window integration in IntelliJ IDEA
- Send selected code to Claude for analysis
- Configurable API settings (API key, model, max tokens, temperature)
- Chat history management
- Keyboard shortcuts for common actions
- Settings page under Tools → Claude Code Chat