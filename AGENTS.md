# Repository Guidelines

## Project Structure & Modules
- `src/main/kotlin/com/claudecodechat/` – Kotlin sources grouped by feature: `actions/`, `cli/`, `completion/`, `models/`, `services/`, `session/`, `settings/`, `state/`, `ui/`, `utils/`.
- `src/main/resources/` – Plugin resources: `META-INF/plugin.xml`, `icons/*.svg`.
- `src/test/kotlin/` – Unit tests (JUnit 5 + MockK). Example: `JsonUtilsTest.kt`.
- Build config: `build.gradle.kts`, `settings.gradle.kts`, `detekt.yml`.

## Build, Test, Run
- `./gradlew buildPlugin` – Builds plugin ZIP to `build/distributions/`.
- `./gradlew runIde` – Launches sandbox IDE for plugin dev.
- `./gradlew test` – Runs unit tests (JUnit Platform).
- `./gradlew verifyPlugin` – Verifies plugin against target IDEs.
- `./gradlew detekt` or `./gradlew codeStandards` – Lint/auto-correct per `detekt.yml`.

Requirements: JDK 17, Gradle Wrapper, Kotlin 2.1.0, IntelliJ Platform Gradle Plugin.

## Coding Style & Naming
- Kotlin official style (4‑space indent, 120‑char line limit enforced by Detekt).
- Naming (Detekt): Classes `PascalCase`, functions `lowerCamelCase`, packages `lowercase.withDots`, variables `lowerCamelCase`.
- Imports: avoid wildcard imports (except `java.util.*` per config).
- Comments: avoid `TODO:/FIXME:` in committed code (flagged by Detekt).

## Testing Guidelines
- Frameworks: JUnit 5, MockK, Coroutines Test.
- Place tests under `src/test/kotlin/...` mirroring source packages.
- Naming: `XxxTest.kt`, test methods explain intent.
- Run: `./gradlew test`. Add tests for non‑UI logic (parsers, services, utils).

## Commit & PR Guidelines
- Commits: Conventional Commits (e.g., `feat: ...`, `fix: ...`, `refactor: ...`, `docs: ...`, `test: ...`, `chore: ...`).
- PRs should include: clear description, linked issues, steps to test, and screenshots/GIFs for UI changes.
- Before opening PR: run `./gradlew test detekt buildPlugin` and ensure no warnings/errors.

## Security & Configuration Tips
- Do not commit secrets. Configure Claude CLI via Settings UI: `claudePath` and `environmentVariables` (one `KEY=VALUE` per line).
- Avoid hardcoding paths; use `ClaudeSettings` for runtime configuration.
- Publishing/signing uses env vars: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`.
