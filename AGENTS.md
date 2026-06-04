# Repository Guidelines

## Project Structure & Module Organization

This repository contains the Kotlin implementation of the Orchestra/InFlow rewrite plus the source specifications.

- `README.md`: project overview and local commands.
- `spec/`: Markdown specifications that define system behavior and architecture.
- `core/`: serializable node document model, diagnostics, and validation.
- `storage-json/`: in-memory repository and JSON persistence.
- `completion-core/`: model-derived completion service.
- `compiler-api/`: compiler interfaces and generated-project contracts.
- `compilers-impl/`: deterministic Kotlin/JVM project generator.
- `app-desktop/`: Swing desktop shell, CLI entry point, editor adapter, and CodeMirror bridge asset.

## Build, Test, and Development Commands

Use Gradle with a workspace-local cache:

- `GRADLE_USER_HOME=.gradle-user gradle test`: compile all modules and run tests.
- `GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='new build/sample.inflow.json'`: create a sample document.
- `GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='validate build/sample.inflow.json'`: validate document references.
- `GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='compile build/sample.inflow.json build/generated-sample'`: export a generated Kotlin project.
- `GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='desktop'`: open the graphical editor.

## Coding Style & Naming Conventions

Kotlin code uses 4-space indentation, trailing commas in multiline declarations, and package names under `com.orchestra`. Keep model types in `core`; do not add UI, filesystem, or compiler implementation dependencies there. Markdown specs should keep concise headings and fenced code blocks.

## Testing Guidelines

Tests use `kotlin.test` under each module's `src/test/kotlin`. Add tests beside the module whose behavior changes. Prefer behavior names such as `create link synchronizes endpoints` or `generates kotlin project files`. Run `gradle test` before handing off changes.

## Commit & Pull Request Guidelines

This repository has no commit history yet, so use a simple imperative style for commits:

- `Add object model addendum`
- `Clarify node link semantics`
- `Document initial test strategy`

Pull requests should include a short summary, affected modules/spec files, and test results. Link related issues when available. For future UI changes, include screenshots or before/after examples.

## Agent-Specific Instructions

Keep edits scoped and preserve user-authored content. Do not rename spec files or reorganize modules unless the task explicitly requires it.
