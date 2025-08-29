# GitHub Actions Workflows

This repository includes several GitHub Actions workflows for building, testing, and releasing the Claude Code Chat plugin.

## Workflows

### 1. Build and Test (`build.yml`)
**Trigger**: Push to master/main/develop branches, Pull Requests
**Purpose**: Continuous integration - builds and tests the plugin

- Sets up JDK 17 and Gradle environment
- Runs full build and test suite
- Uploads build artifacts and test reports
- Caches Gradle dependencies for faster builds

### 2. Release (`release.yml`)
**Trigger**: 
- Push tags matching `v*` pattern (e.g., `v1.0.0`)
- Manual dispatch via GitHub UI

**Purpose**: Automated releases with plugin distribution

Features:
- Extracts version from git tag or manual input
- Updates version in `build.gradle.kts` automatically
- Builds and verifies plugin compatibility
- Creates GitHub release with auto-generated notes
- Uploads plugin ZIP to release assets

### 3. Plugin Verification (`verify.yml`)
**Trigger**: 
- Push to master/main branches
- Pull Requests
- Daily schedule (2 AM UTC)

**Purpose**: Ensures plugin compatibility across IntelliJ versions

- Tests against multiple IntelliJ IDEA versions (2024.1, 2024.2)
- Runs plugin verifier to check API compatibility
- Uploads verification reports

## Usage

### Creating a Release

#### Method 1: Git Tag (Recommended)
```bash
git tag v1.0.0
git push origin v1.0.0
```

#### Method 2: Manual Dispatch
1. Go to GitHub Actions tab
2. Select "Release" workflow
3. Click "Run workflow"
4. Enter version number (e.g., `1.0.0`)
5. Click "Run workflow"

### Monitoring Builds

- **Build Status**: Check the Actions tab for build/test results
- **Artifacts**: Download built plugins from successful workflow runs
- **Verification Reports**: Review plugin compatibility reports

### Required Permissions

The workflows require the following GitHub token permissions:
- `contents: write` - For creating releases
- `actions: read` - For workflow execution

## Files Structure

```
.github/
├── workflows/
│   ├── build.yml      # CI/CD build and test
│   ├── release.yml    # Release automation
│   └── verify.yml     # Plugin verification
└── README.md         # This file
```

## Configuration

### Build Configuration
- **JDK Version**: 17 (Temurin distribution)
- **Gradle**: Uses wrapper with daemon disabled for CI
- **IntelliJ Platform**: 2024.1 (configurable)

### Cache Strategy
- Caches Gradle packages and wrapper for faster builds
- 30-day retention for build artifacts
- 7-day retention for test results and verification reports

### Release Notes
Release notes are auto-generated with:
- Feature highlights
- Technical improvements
- Version-specific changes

## Troubleshooting

### Build Failures
1. Check JDK compatibility
2. Verify Gradle wrapper permissions
3. Review dependency conflicts

### Release Issues
1. Ensure proper version format (`v1.0.0`)
2. Check GitHub token permissions
3. Verify build.gradle.kts syntax

### Plugin Verification Failures
1. Review API compatibility issues
2. Check IntelliJ platform version support
3. Update plugin dependencies if needed