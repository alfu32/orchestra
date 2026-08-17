# Repository Guidelines

## Project Structure & Module Organization

Threadwork is a Kotlin/JVM topology-first IDE and compiler framework. Keep changes within the existing module boundaries:

- `core/`: serializable `ThreadworkDocument`/node model, classification, diagnostics, and validation.
- `storage-json/`: repository mutations and `.orch` JSON persistence.
- `completion-core/`: model- and technology-aware editor completions.
- `compiler-api/`: compiler, virtual-file, and layout-strategy contracts.
- `compilers-impl/`: generic/template, Kotlin, Node.js, PHP, and compiler-compiler implementations.
- `assets/`: licensed fonts, SVG icon sources, and generated resources.
- `app-desktop/`: CLI, Swing UI, canvas, sheet export, plugins, and code editor.
- `spec/`: functional and architectural specifications.

## Build, Test, and Development Commands

Use the Gradle wrapper with the workspace-local cache:

- `GRADLE_USER_HOME=.gradle-user ./gradlew test`: compile all modules and run tests.
- `GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='desktop'`: launch the editor.
- `GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='validate build/sample.orch'`: validate a project.
- `GRADLE_USER_HOME=.gradle-user ./gradlew clean fatJar -Prelease_number=1.7.0`: create `dist/threadwork-1.7.0.jar`.

## Coding Style & Naming Conventions

Use 4-space Kotlin indentation, trailing commas in multiline declarations, and packages under `com.threadwork`. Keep UI, filesystem, and concrete compiler dependencies out of `core`. Prefer established repository abstractions over parallel helpers. Preserve `.orch` compatibility unless a task explicitly changes the persisted contract.

## Testing Guidelines

Tests use `kotlin.test` under each module's `src/test/kotlin`. Add focused tests beside the behavior changed; broaden coverage for model, persistence, compiler, and cross-module contracts. Use descriptive behavior names such as `create link synchronizes endpoints`.

## Commits & Pull Requests

Use imperative, scoped commits such as `Rename application to Threadwork`. Pull requests should summarize behavior, list affected modules/specs, report test results, link issues, and include screenshots for visible UI changes.

## Agent-Specific Instructions

Preserve user-authored project content and unrelated worktree changes. Do not reorganize modules or rename specification files unless explicitly required.
