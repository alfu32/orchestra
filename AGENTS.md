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

# response guidelines

- always respond in the sum up in the commitizen format

All commits must follow the Commitizen / Conventional Commits standard using the structural layout below:

## Commitizen / Conventional Commits standard
```text
<type>(<scope>): <subject>

<body>
```

### Field Definitions

* **`<type>`**: Must be one of the following lowercase tokens:
    * `feat`: A new feature or capability.
    * `fix`: A bug fix.
    * `docs`: Documentation changes only.
    * `style`: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc).
    * `refactor`: A code change that neither fixes a bug nor adds a feature.
    * `perf`: A code change that improves performance.
    * `test`: Adding missing tests or correcting existing tests.
    * `chore`: Changes to the build process, auxiliary tools, or libraries/dependencies.
* **`<scope>`**: Optional. A noun naming the specific codebase component or module affected, wrapped in parentheses (e.g., `(parser)`, `(auth)`, `(runtime)`).
* **`<subject>`**: A brief, imperative-mood summary of the change. Do not capitalize the first letter. Do not end with a period.
* **`<body>`**: Optional. Separate from the subject with exactly one blank line. Provides the motivation for the change and contrasts it with previous behavior.

additionally the body should be structured the way you usually do structure the response summary at the end of a work pass ( bulleted points usually) but id also put before in brief what exactly the demand (s) was(were)

### Examples

```text
fix(editor): persist and reveal mapped compiler diagnostics

  - Diagnostics are persisted on each node and restored with the project.
  - New validation/compilation clears previous diagnostics.
  - Gutter markers now reveal the mapped editor, section, and source line automatically.
  - Nodes with diagnostics show a red warning badge in the diagram.
  - Runtime/override errors without source-map entries are retained and shown as unmapped instead of being discarded.
  - The status bar now shows:
    generated-file:line:column -> node section source-line:column

  git diff --check passes. Full Gradle compilation remains blocked by the environment’s existing wildcard-IP Gradle startup failure.

```

```text
fix(compiler): resolve memory leaks on dynamic execution evaluation loops
```
