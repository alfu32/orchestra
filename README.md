# Threadwork

**Software, designed in two dimensions.**

Threadwork is a topology-first IDE and compilation framework for engineered software systems. Instead of treating a project primarily as a linear collection of source files, Threadwork treats its two-dimensional node-and-link topology as the system's central design artifact. Source code remains important, but it lives inside components whose responsibilities, interfaces, tests, ownership, and relationships are explicit.

The project is under active development. The repository contains the working Kotlin desktop application, persistent object model, compiler framework, built-in compilers, plugin interfaces, technical-sheet exporter, and source specifications.

## Why Threadwork

Conventional IDEs present software mainly as files and text. Threadwork adds a spatial systems-engineering layer:

- **Topology carries design intent.** The arrangement and connectivity of components expose architecture that is difficult to recover from source files alone.
- **Systems decompose telescopically.** A composite can contain processors, links, libraries, and more composites, allowing design from the whole system down to individual implementation units.
- **Interfaces are explicit.** Links identify the variables carried between nodes and hold the technology-specific type definition for that data.
- **Every box is a contract.** A node can carry a declaration, instantiation, specification, tests, and usage instructions, each with its own language identifier.
- **Accountability is granular.** Revision, modification, and responsible-person metadata support delegated work and engineering review at component level.
- **Technical drawings are first-class output.** The canvas is designed for readable routing and export on ISO sheets with a border, title block, folding marks, and parts list.
- **The model is AI-readable.** `.orch` projects are structured JSON. Their bounded components, typed I/O, specifications, and test data give humans and automated tools a clearer problem decomposition.

Component boundaries can support least-knowledge delegation and reduce unnecessary exposure of the whole design. They are an engineering aid, not a security boundary or an automatic guarantee of intellectual-property protection.

## Design Model

Threadwork deliberately uses one serializable `Node` structure for every entity. `NodeKind` distinguishes `Node`, `Processor`, `Link`, `Group`, and `Note`; structural composition is determined by the child hierarchy. The document's root node is also the project node and supplies the project name.

### Nodes and composites

A node stores identity, parent and child references, ports, canvas geometry, technology metadata, content, revisions, ownership, arbitrary metadata, and plugin data. A composite expands to contain its descendants and can be collapsed to a terminal-sized representation. Geometry and expansion state are persisted in the project.

Language, technology, responsible person, revision, and file-layout settings can be inherited through the parent chain. Declaration and instantiation language identifiers may also inherit; specification and usage default to Markdown, while tests default to JSON.

### Links and packet contracts

Links are nodes, not detached edge records. A link stores its source and target node/port references, transport category, variable name, type name, and textual payload definition. The payload may be a language-native type declaration, JSON Schema, CSV header, XML/YAML structure, or another compiler-defined contract.

The core classifier gives link identity precedence over name-based node classification. It then derives semantic stereotypes such as generator, transformer, sink, service library, error handler, test, transport, error pipe, and dependency injection from hierarchy, connectivity, and naming conventions.

Transport categories cover in-process, inter-process, machine-to-machine, and compiler-specific/other mechanisms. The compiler decides the concrete queue, argument, pipe, file, RPC, HTTP, TCP, or runtime implementation.

## Desktop Application

The Swing desktop shell has two primary workspaces:

- **Flow Designer** combines the entity hierarchy with a zoomable, pannable technical-drawing canvas.
- **Entities Edit (IDE)** combines selected-entity and hierarchy trees, the metadata inspector, and tabbed declaration, specification, tests, usage, and instantiation editors.

Current editing support includes graphical node/link creation, sticky select/node/link modes, window selection, multi-selection, movement, hierarchy-aware insertion and reparenting, tree rename/reparent, copy/cut/paste, undo/redo, autosave, align/distribute commands, collapsible composites, typed ports, and command-palette access. Double-clicking a node or link opens its detailed editor.

The canvas uses an in-memory 512 px LRU tile cache, zoom buckets, a separate screen-space overlay, and cached link routes. Routing uses orthogonal and 45-degree segments, fixed endpoint stubs, obstacle/crossing costs, ordered ports, directional markers, and endpoint labels. The cache is intentionally memory-only so a project is not silently materialized as images in a shared filesystem location.

The built-in grid code editor provides multiple cursors and selections, configurable space indentation, syntax highlighting, model-aware completion, keyboard navigation, and integrated undo/redo. A CodeMirror bridge remains as an adapter boundary, but the grid editor is the active dependency-free implementation.

### Sheets and export

Sheet preview supports automatic or explicit A4 through A0 portrait/landscape formats plus A3/A2/A1/A0 roll formats. Scale choices range from `1:1` upward; scaling enlarges the represented sheet rather than the entities. Selection controls the export scope, with selected composites including their descendants.

Exports are available as vector SVG, PNG, or PDF. The export omits the editor grid and includes the project name, format, scale, revision, standard border/folding marks, title block, and a parts list with name, kind, modification date, revision, responsible person, and signature space.

## Compilation Architecture

`compiler-api` separates generated text from packaging:

- `CompilerPlugin` registers language/technology pairs, validates a document, and compiles all or part of it.
- `VirtualFile` and `FsStorage` describe generated or restored file collections without coupling compilers to physical I/O.
- `StructuredCompiler` performs recursive compilation, gathers child/link artifacts, delegates across technology boundaries, and applies the effective layout strategy.
- `LayoutCompositeCompiler` allows one compiler facade to provide a focused implementation per filesystem layout.
- `NodeCompilerContext` exposes the current node, document, child/link artifacts, effective layout, extension, and compile options.

Available layout strategies are:

| ID | Behavior |
| --- | --- |
| `single-file` | Combines generated artifacts into one source file named from the project or selected node. |
| `direct-file-system-homomorphism` | Mirrors the composite hierarchy as directories and source files. |
| `classified-filesystem` | Groups generated entities into directories such as `nodes`, `links`, `libraries`, and `composites`. |
| `source-set` | Places generated content under a conventional source/resource tree. |

Built-in compiler implementations target Kotlin/JVM, Node.js/CommonJS, and PHP. They are backed by Pebble template sets. The generic compiler consumes graph-defined `@Compiler`, compiler-template, project/static-file overrides, and the compiler-compiler can generate a Kotlin `CompilerPlugin` implementation from one `@Compiler` subtree.

Compilation may target the current selection or the full project. When child technology differs from its parent, the structured compiler can delegate that subtree to a registered compiler advertising the matching technology.

## Plugins

At startup Threadwork loads JARs from a plugin directory. By default this is `plugins/` beside the running JAR; `--plugins <dir>` or `--plugins-dir <dir>` overrides it. The directory is created on first use and its resolved path is shown in About.

Compiler plugins are discovered with Java `ServiceLoader`. Desktop plugins can read the current document, post repository updates, add toolbar commands, add command-palette entries, and contribute center workspace tabs. Plugins compile against the public `com.threadwork` APIs; the package rename is intentionally a breaking change from pre-Threadwork builds.

## Repository Layout

| Module | Responsibility |
| --- | --- |
| `core/` | `ThreadworkDocument`, nodes, classification, inheritance helpers, diagnostics, and validation. |
| `storage-json/` | Mutable repository operations and JSON `.orch` persistence/repair. |
| `completion-core/` | Model-aware and technology-aware editor completions. |
| `compiler-api/` | Compiler contracts, virtual files, recursive compilation kernel, and layout strategies. |
| `compilers-impl/` | Generic/template compiler plus Kotlin, Node.js, PHP, and compiler-compiler implementations. |
| `assets/` | Licensed fonts, SVG sources, and generated multi-resolution UI icons. |
| `app-desktop/` | CLI, Swing application, canvas, sheet exporter, plugin loading, and editor adapters. |
| `spec/` | Functional, object-model, graphics, plugin, and compiler specifications. |

The code uses Kotlin 2.2, JVM toolchain 21, kotlinx.serialization, Pebble templates, and Apache Batik for SVG asset rasterization. Bundled fonts are selected from assets with their accompanying licenses; D-DIN is the default canvas font and Monaspace Neon is the default editor font.

## Build and Run

Requirements are a JDK 21 installation and a network connection for the first Gradle dependency resolution. Use the wrapper and a workspace-local cache:

```bash
GRADLE_USER_HOME=.gradle-user ./gradlew test
GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='desktop'
```

Create, validate, and compile a project from the CLI:

```bash
GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='new build/sample.orch'
GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='validate build/sample.orch'
GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='compile build/sample.orch build/generated-sample'
```

Pass a custom plugin directory after any applicable command:

```bash
GRADLE_USER_HOME=.gradle-user ./gradlew :app-desktop:run --args='desktop --plugins /absolute/path/to/plugins'
```

Build the distributable fat JAR with an explicit semantic version:

```bash
GRADLE_USER_HOME=.gradle-user ./gradlew clean fatJar -Prelease_number=1.7.0
java -jar dist/threadwork-1.7.0.jar desktop
```

Without `-Prelease_number`, the build uses the latest Git tag, falling back to `0.1.0`. The generated, ignored `com.threadwork.Version` records the semantic version, Git commit, tag, and UTC build date. The fat JAR and manifest carry the same build identity.

## Project Files and Compatibility

`.orch` is the native Threadwork project extension. Its payload is JSON with nodes serialized as a list and indexed by UUID in memory. The loader also repairs endpoint/parent indexes and accepts the earlier map-shaped JSON representation. The extension is retained across the product rename so existing `.orch` designs remain usable.

## Development

Tests use `kotlin.test` and live beside each module under `src/test/kotlin`. Run the complete suite before submitting changes:

```bash
GRADLE_USER_HOME=.gradle-user ./gradlew test
```

Keep the serializable model and validation rules in `core`, persistence mutations in `storage-json`, compiler contracts in `compiler-api`, and UI concerns in `app-desktop`. Update the relevant specification when changing a persisted contract or cross-module behavior.

See [`AGENTS.md`](AGENTS.md) for contributor conventions and [`spec/`](spec/) for the detailed design record.
