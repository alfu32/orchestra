# Technical Specification Addendum — Plugin Architecture

## 1. Purpose

This addendum extends the main technical specification with a plugin architecture for the Kotlin desktop application.

The application shall support external plugin JARs that extend the system without requiring changes to the desktop host application.

Plugins shall be able to provide:

```text
- compiler backends
- technology and language support
- metadata-aware code-completion providers
- test runners
- analytics tools
- project-management tools
- documentation exporters
- workflow exporters
- AI-agent task generators
- validation tools
```

The plugin system shall be designed around the current object model.

The central architectural rule is:

```text
The core node document is the stable shared model.
Plugins are independent extensions that inspect, transform, analyze, compile, test, or annotate that model.
```

---

## 2. Architectural Position

The desktop application acts as the host platform.

```text
+------------------------------------------------------+
| Kotlin Desktop Host Application                      |
|                                                      |
|  +------------------+                                |
|  | Compose UI       |                                |
|  +------------------+                                |
|                                                      |
|  +------------------+                                |
|  | CodeMirror       |                                |
|  | WebView Adapter  |                                |
|  +------------------+                                |
|                                                      |
|  +------------------+                                |
|  | Plugin Manager   |                                |
|  +------------------+                                |
|                                                      |
+------------------------+-----------------------------+
                         |
                         v
+------------------------------------------------------+
| Core Model                                           |
| - ThreadworkDocument                                     |
| - Node                                               |
| - NodeText                                           |
| - NodeLayout                                         |
| - NodePort                                           |
| - LinkData                                           |
| - TechnologyMetadata                                 |
| - Diagnostics                                        |
+------------------------+-----------------------------+
                         |
                         v
+------------------------------------------------------+
| Plugin JARs                                          |
| - Compiler plugins                                   |
| - Technology plugins                                 |
| - Completion plugins                                 |
| - Test runner plugins                                |
| - Analytics plugins                                  |
| - Project-management plugins                         |
| - Export plugins                                     |
+------------------------------------------------------+
```

The desktop host application shall not contain hardcoded knowledge of specific compilers, technologies, test runners, analytics tools, or project-management tools.

It shall discover and invoke them through plugin interfaces.

---

## 3. Plugin Deployment Model

Plugins shall be distributed as separate JAR files.

Default plugin directory:

```text
plugins/
```

Example layout:

```text
plugins/
├── threadwork-compiler-kotlin-jvm.jar
├── threadwork-compiler-python.jar
├── threadwork-technology-kotlin-jvm.jar
├── threadwork-testrunner-kotlin-jvm.jar
├── threadwork-analytics-structure.jar
├── threadwork-pm-kanban.jar
└── threadwork-export-human-workflow.jar
```

At startup, the application shall:

```text
1. Scan the plugin directory.
2. Load plugin JARs.
3. Discover plugin implementations.
4. Validate plugin descriptors.
5. Initialize valid plugins.
6. Register their capabilities.
7. Expose plugin actions in the UI.
```

For the MVP, plugin loading may use Java `ServiceLoader`.

Later versions may introduce isolated classloaders, plugin enable/disable state, dependency resolution, version constraints, and sandboxing.

---

## 4. Plugin Trust Model

MVP plugins are local trusted code.

The plugin system does not initially provide hard sandboxing.

A plugin JAR can execute JVM code and therefore must be treated as trusted.

The MVP shall prioritize:

```text
- simple plugin authoring
- simple loading
- clear interfaces
- stable access to the document model
```

Over:

```text
- sandboxing
- permission isolation
- network restrictions
- bytecode verification
- plugin marketplace security
```

Security isolation may be added later if untrusted third-party plugins become a requirement.

---

## 5. Base Plugin Interface

All plugins shall implement a base interface.

```kotlin
interface ThreadworkPlugin {
    val id: String
    val displayName: String
    val version: String
    val description: String

    fun initialize(context: PluginContext) {}
    fun shutdown() {}
}
```

### 5.1 Plugin ID

The plugin id must be stable.

Recommended naming convention:

```text
threadwork.compiler.kotlin.jvm
threadwork.compiler.python
threadwork.technology.kotlin.jvm
threadwork.testrunner.kotlin.jvm
threadwork.analytics.structure
threadwork.pm.kanban
threadwork.export.human.workflow
```

The plugin id is used for:

```text
- plugin registry lookup
- diagnostics source tracking
- plugin-owned node metadata
- plugin configuration
- UI action grouping
```

Plugin ids must not be derived from display names.

---

## 6. Plugin Context

Plugins shall receive controlled access to host services through `PluginContext`.

```kotlin
interface PluginContext {
    val documentAccess: DocumentAccess
    val diagnostics: DiagnosticSink
    val events: EventBus
    val workspace: WorkspaceContext
    val services: PluginServiceRegistry
}
```

The context is the only supported entry point from a plugin into the host application.

Plugins shall not directly access UI internals, Compose state, or storage internals.

---

## 7. Document Access

Plugins need access to the current object model.

Access shall be mediated by interfaces.

```kotlin
interface ReadOnlyDocumentAccess {
    fun getDocumentSnapshot(): ThreadworkDocument
    fun getNode(id: NodeId): Node?
    fun requireNode(id: NodeId): Node
}
```

Mutable access shall be separated.

```kotlin
interface MutableDocumentAccess : ReadOnlyDocumentAccess {
    fun updateNode(node: Node)
    fun updateNodePluginData(
        nodeId: NodeId,
        pluginId: String,
        data: JsonObject
    )
}
```

The default rule shall be:

```text
Analytics, compiler, validation, export, and documentation plugins receive read-only access.

Project-management plugins and controlled transformation plugins may receive mutable access.
```

The host application shall decide what kind of access a plugin receives.

---

## 8. Plugin-Owned Node Data

The core `Node` model shall include plugin-owned structured data.

```kotlin
@Serializable
data class Node(
    val id: NodeId,
    var name: String,
    var kind: NodeKind,
    var parentId: NodeId? = null,

    val children: MutableList<NodeId> = mutableListOf(),
    val incomingLinks: MutableList<NodeId> = mutableListOf(),
    val outgoingLinks: MutableList<NodeId> = mutableListOf(),

    var layout: NodeLayout = NodeLayout(),
    var text: NodeText = NodeText(),
    var technology: TechnologyMetadata = TechnologyMetadata(),
    val ports: MutableList<NodePort> = mutableListOf(),
    var link: LinkData? = null,

    var metadata: MutableMap<String, String> = mutableMapOf(),

    var pluginData: MutableMap<String, JsonObject> = mutableMapOf()
)
```

### 8.1 Purpose

`pluginData` prevents the core node schema from being changed for every plugin.

Examples:


Project-management plugin:
    `pluginData["threadwork.pm.kanban"]`

Test runner plugin:
    `pluginData["threadwork.testrunner.kotlin.jvm"]`

Analytics plugin:
    `pluginData["threadwork.analytics.structure"]`

AI-agent plugin:
    `pluginData["threadwork.ai.agent.generator"]`


### 8.2 Example Project-Management Plugin Data

```json
{
  "status": "in-progress",
  "priority": "high",
  "assignedTo": "John",
  "blocked": false,
  "dueDate": "2026-02-15"
}
```

### 8.3 Example Test Runner Plugin Data

```json
{
  "lastRunAt": "2026-01-21T10:30:00Z",
  "status": "failed",
  "passed": 12,
  "failed": 2,
  "durationMs": 1534
}
```

---

## 9. Plugin Service Registry

The host shall expose shared services through a registry.

```kotlin
interface PluginServiceRegistry {
    fun <T : Any> getService(serviceClass: KClass<T>): T?
    fun <T : Any> requireService(serviceClass: KClass<T>): T
}
```

Possible services:


- NodeCompletionService
- DocumentValidationService
- GeneratedProjectWriter
- BuildRunner
- JsonSerializer
- TechnologyRegistry
- CommandBus
- DiagnosticRepository


The service registry allows plugins to cooperate with host services without depending on implementation classes.

---

## 10. Event Bus

Plugins may subscribe to document and application events.

```kotlin
interface EventBus {
    fun publish(event: ThreadworkEvent)
    fun subscribe(listener: (ThreadworkEvent) -> Unit): Subscription
}
```

Events:

```kotlin
sealed interface ThreadworkEvent

data class DocumentLoadedEvent(
    val documentId: String
) : ThreadworkEvent

data class DocumentSavedEvent(
    val documentId: String
) : ThreadworkEvent

data class NodeCreatedEvent(
    val nodeId: NodeId
) : ThreadworkEvent

data class NodeUpdatedEvent(
    val nodeId: NodeId
) : ThreadworkEvent

data class NodeDeletedEvent(
    val nodeId: NodeId
) : ThreadworkEvent

data class SelectionChangedEvent(
    val selectedNodeIds: List<NodeId>
) : ThreadworkEvent

data class CompilationCompletedEvent(
    val result: CompilationResult
) : ThreadworkEvent

data class TestRunCompletedEvent(
    val result: TestRunResult
) : ThreadworkEvent

data class DiagnosticsChangedEvent(
    val sourcePluginId: String?
) : ThreadworkEvent
```

Use cases:


- analytics plugin refreshes after document changes
- project-management plugin updates status after tests
- test runner stores latest result on node
- UI updates diagnostics panel
- completion providers refresh cached node context


---

## 11. Diagnostics

All plugins shall report findings through the common diagnostic model.

```kotlin
data class Diagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val nodeId: NodeId? = null,
    val textSection: NodeTextSection? = null,
    val line: Int? = null,
    val column: Int? = null,
    val sourcePluginId: String? = null
)
```

```kotlin
enum class DiagnosticSeverity {
    Info,
    Warning,
    Error
}
```

Diagnostics may represent:


- compiler errors
- invalid links
- missing ports
- missing tests
- failed test assertions
- project-management warnings
- incomplete specifications
- unsupported technology choices


The UI shall display diagnostics in a unified diagnostics panel.

If location is known, diagnostics may also be shown in the CodeMirror editor.

---

## 12. Plugin Types

The system shall support multiple plugin categories.

A single JAR may contain more than one plugin implementation.

---

## 13. Compiler Plugins

Compiler plugins transform the document model into generated artifacts.

Examples:


- Kotlin/JVM source project compiler
- Python source project compiler
- TypeScript source project compiler
- human workflow compiler
- documentation compiler
- AI-agent task compiler


Interface:

```kotlin
interface CompilerPlugin : ThreadworkPlugin {
    fun supports(document: ThreadworkDocument): Boolean

    fun validate(
        context: PluginContext,
        document: ThreadworkDocument
    ): List<Diagnostic>

    fun compile(
        context: PluginContext,
        document: ThreadworkDocument,
        options: CompilerOptions
    ): CompilationResult
}
```

Compiler plugins should usually receive read-only document access.

Compiler output shall include traceability to source nodes.

```kotlin
data class CompilationResult(
    val generatedProject: GeneratedProject?,
    val diagnostics: List<Diagnostic>,
    val success: Boolean
)

data class GeneratedProject(
    val name: String,
    val files: List<GeneratedFile>
)

data class GeneratedFile(
    val path: String,
    val content: String,
    val originNodeId: NodeId?,
    val reason: String
)
```

---

## 14. Technology Plugins

Technology plugins describe language, framework, runtime, and editor behavior.

They may provide:


- language descriptors
- technology descriptors
- file extensions
- content types
- CodeMirror language mode ids
- default source templates
- completion providers
- compiler compatibility metadata
- test-runner compatibility metadata


Interface:

```kotlin
interface TechnologyPlugin : ThreadworkPlugin {
    fun getTechnologies(): List<TechnologyDescriptor>

    fun getDefaultTemplate(
        technologyId: String,
        nodeKind: NodeKind
    ): String

    fun getCompletionSuggestions(
        context: PluginContext,
        request: CompletionRequest
    ): List<CompletionSuggestion>
}
```

Descriptor:

```kotlin
data class TechnologyDescriptor(
    val id: String,
    val displayName: String,
    val languageId: String,
    val defaultFileExtension: String,
    val contentType: String,
    val codeMirrorLanguageId: String?
)
```

Example:



TechnologyDescriptor(
    id = "kotlin-jvm",
    displayName = "Kotlin/JVM",
    languageId = "kotlin",
    defaultFileExtension = "kt",
    contentType = "text/x-kotlin",
    codeMirrorLanguageId = "kotlin"
)


---

## 15. Completion Plugins

Completion may be provided by technology plugins or standalone completion plugins.

The completion service shall aggregate suggestions from:


- core model-derived suggestions
- technology plugins
- compiler plugins
- user/project metadata


Interface:

```kotlin
interface CompletionPlugin : ThreadworkPlugin {
    fun getSuggestions(
        context: PluginContext,
        request: CompletionRequest
    ): List<CompletionSuggestion>
}
```

Request:

```kotlin
data class CompletionRequest(
    val nodeId: NodeId,
    val textSection: NodeTextSection,
    val languageId: String,
    val technologyId: String,
    val cursorOffset: Int,
    val fullText: String,
    val currentLine: String,
    val prefix: String
)
```

Suggestion:

```kotlin
data class CompletionSuggestion(
    val label: String,
    val insertText: String,
    val kind: CompletionSuggestionKind,
    val detail: String = "",
    val documentation: String = "",
    val sourcePluginId: String? = null
)
```

Suggestion kinds:

```kotlin
enum class CompletionSuggestionKind {
    InputPort,
    OutputPort,
    Import,
    SiblingNode,
    ParentNode,
    ChildNode,
    LinkedSourceNode,
    LinkedTargetNode,
    Library,
    Keyword,
    Snippet,
    CompilerSymbol,
    UserSymbol
}
```

Required model-derived suggestions:


- input port names
- output port names
- incoming link source nodes
- outgoing link target nodes
- parent node name
- child node names
- sibling node names
- technology metadata
- import/library names


---

## 16. Test Runner Plugins

Test runner plugins execute tests attached to nodes.

They may run tests for:


- selected node
- selected subtree
- composite node


Interface:

```kotlin
interface TestRunnerPlugin : ThreadworkPlugin {
    fun supports(
        node: Node,
        document: ThreadworkDocument
    ): Boolean

    fun runTests(
        context: PluginContext,
        document: ThreadworkDocument,
        nodeId: NodeId,
        options: TestRunOptions
    ): TestRunResult
}
```

Result:

```kotlin
data class TestRunResult(
    val pluginId: String,
    val rootNodeId: NodeId,
    val startedAt: Instant,
    val finishedAt: Instant,
    val diagnostics: List<Diagnostic>,
    val nodeResults: List<NodeTestResult>,
    val success: Boolean
)

data class NodeTestResult(
    val nodeId: NodeId,
    val status: TestStatus,
    val passed: Int,
    val failed: Int,
    val durationMs: Long,
    val message: String = ""
)

enum class TestStatus {
    NotRun,
    Passed,
    Failed,
    Error,
    Skipped
}
```

Test runner plugins may update their own `pluginData` on nodes with latest result summaries.

---

## 17. Analytics Plugins

Analytics plugins inspect the document and produce reports.

Examples:


- broken link report
- orphan node report
- missing tests report
- missing specification report
- missing AI instructions report
- graph complexity report
- unused output report
- unresolved input report
- dependency depth report


Interface:

```kotlin
interface AnalyticsPlugin : ThreadworkPlugin {
    fun analyze(
        context: PluginContext,
        document: ThreadworkDocument,
        selection: NodeSelection?
    ): AnalyticsReport
}
```

Report:

```kotlin
data class AnalyticsReport(
    val pluginId: String,
    val title: String,
    val summary: String,
    val diagnostics: List<Diagnostic>,
    val items: List<AnalyticsReportItem>
)

data class AnalyticsReportItem(
    val nodeId: NodeId?,
    val title: String,
    val description: String,
    val severity: DiagnosticSeverity
)
```

---

## 18. Project-Management Plugins

Project-management plugins attach workflow state to nodes.

Examples:


- todo / in-progress / done
- priority
- assigned person
- due date
- blocked flag
- review status
- implementation status
- test status


Interface:

```kotlin
interface ProjectManagementPlugin : ThreadworkPlugin {
    fun getStatusDescriptors(): List<ProjectStatusDescriptor>

    fun getNodeStatus(
        context: PluginContext,
        nodeId: NodeId
    ): ProjectStatus?

    fun setNodeStatus(
        context: PluginContext,
        nodeId: NodeId,
        status: ProjectStatus
    )
}
```

Status:

```kotlin
data class ProjectStatus(
    val statusId: String,
    val priority: String = "",
    val assignedTo: String = "",
    val dueDate: String = "",
    val blocked: Boolean = false,
    val notes: String = ""
)
```

Project-management data shall be stored in `node.pluginData[pluginId]`.

---

## 19. Export Plugins

Export plugins generate non-source-code artifacts.

Examples:


- Markdown documentation
- HTML documentation
- human operating procedure
- business workflow report
- AI-agent implementation tasks
- checklist
- test plan


Interface:

```kotlin
interface ExportPlugin : ThreadworkPlugin {
    fun supports(document: ThreadworkDocument): Boolean

    fun export(
        context: PluginContext,
        document: ThreadworkDocument,
        options: ExportOptions
    ): ExportResult
}
```

---

## 20. Plugin Manager

The host shall provide a plugin manager.

```kotlin
interface PluginManager {
    fun loadPlugins(pluginDirectory: Path)
    fun shutdownPlugins()

    fun getAllPlugins(): List<ThreadworkPlugin>

    fun getCompilerPlugins(): List<CompilerPlugin>
    fun getTechnologyPlugins(): List<TechnologyPlugin>
    fun getCompletionPlugins(): List<CompletionPlugin>
    fun getTestRunnerPlugins(): List<TestRunnerPlugin>
    fun getAnalyticsPlugins(): List<AnalyticsPlugin>
    fun getProjectManagementPlugins(): List<ProjectManagementPlugin>
    fun getExportPlugins(): List<ExportPlugin>
}
```

The plugin manager is responsible for:


- scanning plugin directory
- loading plugin JARs
- discovering plugin implementations
- initializing plugins
- handling plugin startup errors
- registering plugin capabilities
- shutting down plugins


---

## 21. Plugin Discovery

The MVP shall use Java `ServiceLoader`.

Each plugin JAR shall include provider declarations under:


`META-INF/services/`


Example:

`META-INF/services/com.threadwork.plugin.api.ThreadworkPlugin`

Content:

```java
com.example.threadwork.kotlin.KotlinJvmCompilerPlugin
com.example.threadwork.kotlin.KotlinJvmTechnologyPlugin
com.example.threadwork.kotlin.KotlinJvmTestRunnerPlugin
```

A plugin JAR may expose multiple plugin classes.

---

## 22. UI Integration

The desktop UI shall expose plugin actions through menus and panels.

Recommended menu groups:


Compile
    Kotlin/JVM
    Python
    Human Workflow

Run Tests
    Selected Node
    Selected Subtree
    Whole Document

Analyze
    Validate Structure
    Missing Tests
    Missing Specifications
    Graph Complexity

Project
    Kanban Status
    Todo/In Progress/Done Report

Export
    Markdown Documentation
    Human Workflow
    AI Agent Tasks


The UI must not instantiate plugin classes directly.

The UI shall query `PluginManager`.

---

## 23. Plugin Failure Handling

A failing plugin must not crash the whole application if avoidable.

The host shall catch plugin errors during:


- loading
- initialization
- action execution
- shutdown


Failures shall be reported as diagnostics or plugin manager errors.

Example diagnostic:

```
Plugin threadwork.compiler.kotlin.jvm failed during compile:
NullPointerException while generating node read_assoc_smt.
```

The plugin may be disabled for the current session after repeated failures.

---

## 24. Versioning

Each plugin shall declare a version.

The host shall also expose the plugin API version.

```kotlin
data class PluginApiVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
)
```

For MVP, version compatibility may be simple:

```
Plugin major API version must match host major API version.
```

Later, more sophisticated compatibility checks may be added.

---

## 25. Recommended Module Layout

```text
threadwork/
├── core/
│   ├── model
│   ├── diagnostics
│   ├── traversal
│   └── serialization
│
├── storage-json/
│   ├── in-memory repository
│   └── JSON save/load
│
├── plugin-api/
│   ├── ThreadworkPlugin
│   ├── PluginContext
│   ├── DocumentAccess
│   ├── Diagnostics
│   ├── Events
│   ├── CompilerPlugin
│   ├── TechnologyPlugin
│   ├── CompletionPlugin
│   ├── TestRunnerPlugin
│   ├── AnalyticsPlugin
│   ├── ProjectManagementPlugin
│   └── ExportPlugin
│
├── plugin-host/
│   ├── PluginManager
│   ├── ServiceLoaderPluginLoader
│   ├── PluginRegistry
│   └── PluginLifecycle
│
├── completion-core/
│   ├── NodeCompletionService
│   ├── model-derived completions
│   └── plugin completion aggregation
│
├── editor-codemirror/
│   ├── WebView editor adapter
│   └── JSON bridge protocol
│
├── app-desktop/
│   ├── Compose UI
│   ├── canvas
│   ├── tree
│   ├── inspector
│   ├── editor panel
│   ├── diagnostics panel
│   └── plugin menus
│
├── plugins/
│   ├── compiler-kotlin-jvm-plugin/
│   ├── technology-kotlin-jvm-plugin/
│   ├── testrunner-kotlin-jvm-plugin/
│   ├── analytics-structure-plugin/
│   ├── pm-kanban-plugin/
│   └── export-human-workflow-plugin/
│
└── test-fixtures/
```

---

## 26. MVP Plugin Set

The MVP should include the following built-in or bundled plugins:

```
1. Kotlin/JVM technology plugin
2. Kotlin/JVM naive compiler plugin
3. Kotlin/JVM test runner plugin
4. Structure analytics plugin
5. Simple Kanban/project-status plugin
6. Markdown documentation export plugin
```

These may be developed in the same repository but packaged as plugins to prove the architecture.

---

## 27. Main Design Rules

### Rule 1

The core model remains stable and shared.

Plugins work against the core model.

---

### Rule 2

Plugins are not subclasses of nodes.

Plugins annotate, inspect, compile, test, or export nodes.

---

### Rule 3

Plugin-specific data belongs in `node.pluginData[pluginId]`.

Do not modify the core node schema for each plugin.

---

### Rule 4

The desktop host does not know specific plugin implementations.

It only knows plugin interfaces.

---

### Rule 5

Compiler plugins are only one plugin category.

The system is a general model-extension and artifact-generation platform.

---

### Rule 6

The MVP plugin system uses trusted local JARs.

Sandboxing is not part of the first implementation.

---

## 28. Summary

The application shall be implemented as a Kotlin desktop host built around a stable recursive node document model. The host shall load external plugin JARs that provide compilers, technologies, completions, test runners, analytics, project-management features, exporters, validators, and AI-agent generators. Plugins shall receive controlled access to the current document through `PluginContext`, report issues through a common diagnostic model, subscribe to document events through an event bus, and store plugin-owned node state under `node.pluginData[pluginId]`.

This architecture keeps the object model at the center and allows the system to grow by adding plugins instead of rewriting the host application.
