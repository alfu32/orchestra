# InFlow Rewrite — Technical Specification

## 1. Project Objective

The application is a desktop visual orchestration editor for designing software systems, workflows, and generated projects through a hierarchy of connected nodes.

The rewrite shall use Kotlin as the primary implementation language.

The system shall consist of:

```text
- Kotlin desktop application
- shared Kotlin core model module
- in-memory document repository
- JSON save/load persistence
- canvas-based visual editor
- CodeMirror code editor embedded in WebView
- metadata-aware completion service
- compiler plugin interface
- first naive source-code compiler
```

The rewrite should preserve the successful conceptual model from the current implementation while reducing architectural complexity.

---

## 2. High-Level Architecture

```text
+------------------------------------------------------+
| Desktop Application                                  |
| Kotlin / Compose Multiplatform                       |
|                                                      |
|  +-------------------+   +-------------------------+ |
|  | Tree View         |   | Canvas Graph View       | |
|  +-------------------+   +-------------------------+ |
|                                                      |
|  +------------------------------------------------+  |
|  | Node Editor Panel                              |  |
|  | - Source editor                                |  |
|  | - Specification editor                         |  |
|  | - Tests editor                                 |  |
|  | - AI instructions editor                       |  |
|  | - Metadata editor                              |  |
|  +------------------------------------------------+  |
|                                                      |
|  +------------------------------------------------+  |
|  | CodeMirror WebView Adapter                     |  |
|  +------------------------------------------------+  |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
| Application Services                                 |
| - Document service                                   |
| - Node service                                       |
| - Link service                                       |
| - Selection service                                  |
| - Completion service                                 |
| - Compiler service                                   |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
| Core Module                                          |
| - Node                                               |
| - Node metadata                                      |
| - Node text sections                                |
| - Layout                                            |
| - Ports                                             |
| - Links                                             |
| - Compiler interfaces                               |
| - Diagnostics                                       |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
| Storage Module                                       |
| - In-memory repository                               |
| - JSON import/export                                 |
| - Dirty-state tracking                               |
| - File save/load                                    |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
| Compiler Plugins                                     |
| - Naive source-code project compiler                 |
| - Future workflow/documentation/AI compilers         |
+------------------------------------------------------+
```

---

## 3. Technology Stack

### 3.1 Primary Language

```text
Kotlin
```

Kotlin shall be used for:

```text
- core model
- application services
- desktop UI
- storage
- compiler backend
- completion engine
- build/run orchestration
```

---

### 3.2 Desktop UI

Recommended UI framework:

```text
Compose Multiplatform for Desktop
```

The UI shall be a desktop application, not a browser-first application.

The browser dependency shall be isolated to the embedded code editor only.

---

### 3.3 Code Editor

Recommended editor:

```text
CodeMirror 6 embedded in WebView
```

The CodeMirror integration must be hidden behind a Kotlin-side adapter.

The rest of the application must not depend directly on CodeMirror APIs.

---

### 3.4 Serialization

Recommended serialization:

```text
kotlinx.serialization
```

The document model shall be serializable to and from JSON.

The storage backend shall initially be an in-memory Java/Kotlin object graph that can be saved to and loaded from a JSON file.

Kotlin serialization supports serializable Kotlin classes and JSON encode/decode workflows.

---

## 4. Module Structure

Recommended Gradle module layout:

```text
inflow/
├── settings.gradle.kts
├── build.gradle.kts
├── core/
├── storage-json/
├── app-desktop/
├── editor-codemirror/
├── compiler-api/
├── compiler-naive-kotlin/
└── test-fixtures/
```

---

## 5. Module Responsibilities

### 5.1 `core`

Contains shared entities and pure domain logic.

Responsibilities:

```text
- define Node
- define NodeId
- define document model
- define layout model
- define text section model
- define metadata model
- define port model
- define link model
- define diagnostics
- define validation primitives
- define tree traversal helpers
```

Must not depend on:

```text
- Compose UI
- WebView
- CodeMirror
- filesystem APIs, except if unavoidable for simple value types
- compiler implementation modules
```

---

### 5.2 `storage-json`

Contains in-memory repository and JSON persistence.

Responsibilities:

```text
- hold active document in memory
- provide repository operations
- save document as JSON
- load document from JSON
- track dirty state
- assign ids
- validate references after load
```

This module replaces the current database backend for the rewrite MVP.

The old MariaDB model is useful as prior art, but not required for the first rewrite.

---

### 5.3 `app-desktop`

Contains the Compose desktop application.

Responsibilities:

```text
- main application window
- menu bar
- project open/save actions
- left hierarchy tree
- central canvas graph view
- right inspector/help panel
- bottom or tabbed node editor panel
- selection handling
- command routing
```

Must communicate with the document through services/repositories.

Must not directly mutate low-level fields in uncontrolled ways.

---

### 5.4 `editor-codemirror`

Contains the WebView and CodeMirror bridge.

Responsibilities:

```text
- load CodeMirror editor HTML/JS bundle
- expose Kotlin editor adapter
- send document text to editor
- receive text changes from editor
- receive cursor position
- send completion items to editor
- send diagnostics to editor
- switch language mode
- switch theme
```

Must not own the node model.

Must not know business semantics except through editor context passed to it.

---

### 5.5 `compiler-api`

Contains compiler extension interfaces.

Responsibilities:

```text
- define CompilerPlugin
- define CompilerContext
- define GeneratedProject
- define GeneratedFile
- define BuildResult
- define compiler diagnostics
```

---

### 5.6 `compiler-naive-kotlin`

First compiler implementation.

Responsibilities:

```text
- consume node hierarchy
- generate Kotlin/JVM project files
- generate source files for terminal processing nodes
- generate composite runner files
- generate link-transfer code
- optionally invoke Gradle or Kotlin compiler
```

This compiler should be deliberately boring and deterministic.

It should not attempt concurrency.

---

## 6. Core Object Model

### 6.1 Single Node Class

The core object model shall use one primary class:

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
    var metadata: MutableMap<String, String> = mutableMapOf()
)
```

The system shall avoid separate subclasses such as:

```text
ProcessorNode
CompositeNode
LinkNode
```

Instead, semantic roles shall be inferred from:

```text
- kind
- children
- link data
- ports
- compiler rules
```

---

### 6.2 Node Identity

```kotlin
@Serializable
@JvmInline
value class NodeId(val value: String)
```

Node ids shall be stable across save/load.

Ids should not be derived from names.

Repository-generated node and link ids shall be UUID-backed values, for example
`node_0187c9cb-b0b3-49bb-89f5-5c72fc0c7b7b` or
`link_7d1f3142-d480-428a-bf45-cb5998f2a96f`. Counters are not acceptable for
new ids because opened documents may already contain arbitrary historical ids.

---

### 6.3 Node Kind

```kotlin
@Serializable
enum class NodeKind {
    Node,
    Processor,
    Link,
    Group,
    Note
}
```

The compiler may interpret `NodeKind.Group` with children as a composite.

The compiler may interpret `NodeKind.Processor` with children as a composite processor.

The model shall allow flexible interpretation.

---

### 6.4 Node Layout

```kotlin
@Serializable
data class NodeLayout(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 200.0,
    var height: Double = 100.0
)
```

This corresponds to the current implementation’s `anchor` and `size`.

The visual layout is part of the document.

---

### 6.5 Node Text Sections

Each node shall have four main text fields:

```kotlin
@Serializable
data class NodeText(
    var source: String = "",
    var specification: String = "",
    var tests: String = "",
    var aiInstructions: String = ""
)
```

Meaning:

```text
source
    Implementation text or code.

specification
    Human or machine-readable description of intended behavior.

tests
    Unit tests, examples, validation data, or manual tests.

aiInstructions
    Instructions for coding agents.
```

---

### 6.6 Technology Metadata

```kotlin
@Serializable
data class TechnologyMetadata(
    var languageId: String = "",
    var technologyId: String = "",
    var compilerId: String = "",
    var fileExtension: String = "",
    var contentType: String = ""
)
```

Examples:

```json
{
  "languageId": "kotlin",
  "technologyId": "kotlin-jvm",
  "compilerId": "naive-kotlin",
  "fileExtension": "kt",
  "contentType": "text/x-kotlin"
}
```

This replaces the previous `TECHNOLANG` and node-local technology JSON in the first rewrite.

A later version may reintroduce a technology catalog.

---

### 6.7 Ports

```kotlin
@Serializable
data class NodePort(
    val id: String,
    var name: String,
    var direction: PortDirection,
    var dataType: String = "",
    var metadata: MutableMap<String, String> = mutableMapOf()
)
```

```kotlin
@Serializable
enum class PortDirection {
    Input,
    Output
}
```

Ports are used by:

```text
- links
- compiler
- code suggestions
- graph rendering
- validation
```

---

### 6.8 Link Data

Links are nodes.

A link node has `kind = NodeKind.Link` and non-null `link`.

```kotlin
@Serializable
data class LinkData(
    var sourceNodeId: NodeId,
    var sourcePortName: String,
    var targetNodeId: NodeId,
    var targetPortName: String,
    var transportKind: String = "packet",
    var payloadDefinition: String = ""
)
```

Each link connects exactly one output to one input.

`payloadDefinition` is the user-controlled schema/contract for the transported
data. The compiler decides whether it is a C struct, Kotlin class, JSON schema,
CSV header, or another technology-specific declaration.

---

## 7. Document Model

The active project shall be represented by a document object:

```kotlin
@Serializable
data class InflowDocument(
    val id: String,
    var name: String,
    var rootNodeId: NodeId,
    val nodes: MutableMap<NodeId, Node> = mutableMapOf(),
    var metadata: MutableMap<String, String> = mutableMapOf()
)
```

The document owns all nodes.

Node hierarchy is represented by:

```text
node.parentId
node.children
```

Graph communication is represented by:

```text
link nodes
node.incomingLinks
node.outgoingLinks
```

When classifying a node as a generator, transformer, sink, or generic
processing unit, inbound usage/dependency links from service-library nodes do
not count as data inputs. They express imports or dependencies, not transported
runtime data.

The repository must keep these references consistent.

---

## 8. In-Memory Repository

### 8.1 Repository Purpose

The storage backend shall initially be an in-memory object repository.

It shall behave like a small document database, but without external database dependency.

The repository holds one active document.

---

### 8.2 Repository Interface

```kotlin
interface DocumentRepository {
    fun getDocument(): InflowDocument

    fun getNode(id: NodeId): Node?
    fun requireNode(id: NodeId): Node

    fun createNode(parentId: NodeId?, name: String, kind: NodeKind): Node
    fun deleteNode(id: NodeId)

    fun renameNode(id: NodeId, name: String)
    fun updateNodeLayout(id: NodeId, layout: NodeLayout)
    fun updateNodeText(id: NodeId, text: NodeText)
    fun updateNodeTechnology(id: NodeId, technology: TechnologyMetadata)

    fun addPort(nodeId: NodeId, port: NodePort)
    fun removePort(nodeId: NodeId, portId: String)

    fun createLink(
        parentId: NodeId?,
        name: String,
        sourceNodeId: NodeId,
        sourcePortName: String,
        targetNodeId: NodeId,
        targetPortName: String
    ): Node

    fun moveNode(id: NodeId, newParentId: NodeId?)

    fun markDirty()
    fun isDirty(): Boolean
}
```

---

### 8.3 JSON Store Interface

```kotlin
interface JsonDocumentStore {
    fun save(document: InflowDocument, filePath: Path)
    fun load(filePath: Path): InflowDocument
}
```

Save/load should be explicit operations.

There is no database server in the MVP.

---

## 9. JSON File Format

A saved project file shall contain the full document.

Suggested extension:

```text
.inflow.json
```

Example structure:

```json
{
  "id": "document_001",
  "name": "carl_bridge",
  "rootNodeId": "root",
  "nodes": [
    {
      "id": "root",
      "name": "carl_bridge",
      "kind": "Group",
      "parentId": null,
      "children": ["node_read_new_smt", "node_read_assoc_smt"],
      "incomingLinks": [],
      "outgoingLinks": [],
      "layout": {
        "x": 0.0,
        "y": 0.0,
        "width": 400.0,
        "height": 300.0
      },
      "text": {
        "source": "",
        "specification": "",
        "tests": "",
        "aiInstructions": ""
      },
      "technology": {
        "languageId": "",
        "technologyId": "",
        "compilerId": "",
        "fileExtension": "",
        "contentType": ""
      },
      "ports": [],
      "link": null,
      "metadata": {}
    }
  ],
  "metadata": {}
}
```

The persisted file format stores `nodes` as a list. Node identity comes only
from each node's `id` field; the application may rebuild an id-indexed map after
loading for runtime performance. Legacy map-shaped node files may be read for
compatibility, but new saves must emit the list format.

---

## 10. Application Services

The UI shall not directly perform complex mutations on the document.

Use services.

Recommended services:

```text
DocumentService
NodeService
LinkService
SelectionService
ViewportService
CompletionService
CompilerService
CommandHistoryService
```

---

### 10.1 Node Service

Responsible for:

```text
- create node
- delete node
- rename node
- move node
- update layout
- update text section
- update metadata
- add/remove ports
```

---

### 10.2 Link Service

Responsible for:

```text
- create link
- delete link
- validate endpoints
- update link endpoint
- synchronize incoming/outgoing link references
```

---

### 10.3 Selection Service

Responsible for:

```text
- selected node id
- selected link id
- multi-selection
- active text section
- active editor context
```

---

### 10.4 Viewport Service

Responsible for:

```text
- pan
- zoom
- world-to-screen transform
- screen-to-world transform
- canvas grid settings
```

---

## 11. Desktop UI Specification

### 11.1 Main Layout

The desktop UI shall have the following regions:

```text
+---------------------------------------------------------+
| Menu / Toolbar                                          |
+-------------+-------------------------------+-----------+
| Hierarchy   | Canvas / Graph View           | Inspector |
| Tree        |                               | / Help    |
+-------------+-------------------------------+-----------+
| Node Editor Panel                                    |
+---------------------------------------------------------+
```

---

### 11.2 Left Hierarchy Tree

Shows document hierarchy.

Required operations:

```text
- expand/collapse nodes
- select node
- create child node
- delete node
- rename node
- move node
```

---

### 11.3 Canvas Graph View

Shows visual layout.

Required operations:

```text
- draw grid
- draw nodes as rectangles
- draw links as polylines or straight lines
- select node
- drag node
- resize node
- pan viewport with middle/right mouse drag
- zoom viewport about the cursor/model point
- create link between ports
- redirect link source or target
- create child node inside a selected or clicked parent
- reparent nodes with the Ctrl-drag drop policy
- cut/copy/paste selected nodes with system keyboard shortcuts
- delete selected entities with Delete
- return to select mode with Escape
- select by click or window selection
- show selected state
- show node stereotype using shared core classification
- color nodes by stereotype, including libraries, tests, errors, and composites
- route links with readable port separation and labels
```

Canvas modes are limited to sticky `Select`, `Node`, and `Link` modes. Their
toolbar controls are state indicators as well as mode selectors; creation modes
remain active until the user selects another mode or presses Escape.

The canvas should use the `NodeLayout` stored in the document.

Node placement and parenting rules:

```text
- if a node is selected when creating a node, the selected node is the parent
- otherwise the new node is parented to the node under the placement cursor
- if the placement cursor is not inside a node, the new node is parented to the root
- dragging a node normally keeps its current parent
- dragging a composite moves the composite and all visible descendants
- Ctrl-drag is the explicit reparent/unparent gesture
- on Ctrl-drop, the drop cursor position, not the grabbed node center, determines the new parent
- Ctrl-drop on empty canvas reparents the moved node(s) to the root
```

Composite nodes are visual containers. Their displayed geometry shall expand to
the bounding box that envelopes their visible children, with padding. Composite
containers should use a distinct line type from terminal processing nodes.
The core canvas must not draw readiness or project-management state markers by
default; such overlays belong to plugins that define their own criteria.

Transport and error links are materialized as routed polylines between node
ports. Input and output ports must be distributed vertically on the left or right
edge of the node with readable spacing. Port slots start near the top edge and
progress downward; the required port stack height contributes to the calculated
node height. A materialized link displays one label, centered on the route, using
the link node name rather than generic input or output captions. Short links
between facing ports should use a direct segment instead of a kinked route. Link
routes should include directional arrow markers at readable intervals, at least
near the last quarter of the route. Links and port icons are drawn after node and
composite bodies so internal composite links remain visible.

Usage/dependency links are annotations, not data-flow lines. They should not draw
a direct line between the library and the dependent node. Instead, the service
library shows dependent flyouts on its right side, and each dependent processing
unit shows a top-anchored vertical dependency list.

The drawing surface should support technical drawing output. The MVP may expose
an ISO sheet preview with grid, drawing frame, cartouche/title block, and a
parts/list table; later versions may export SVG/PDF/print-ready documents.

---

### 11.4 Node Editor Panel

Shows editable content for selected node.

Required tabs:

```text
- Source
- Specification
- Tests
- AI Instructions
- Metadata
- Ports
- Link
```

The four text tabs shall use the code editor component where useful.

The metadata, ports, and link tabs may use native Compose controls.

---

## 12. CodeMirror WebView Integration

### 12.1 Principle

CodeMirror shall be treated as an embedded editor engine, not as the application platform.

The application remains Kotlin-first.

The WebView contains only:

```text
- CodeMirror editor
- minimal JavaScript bridge
- static editor HTML/CSS/JS
```

---

### 12.2 Kotlin Adapter Interface

```kotlin
interface CodeEditorAdapter {
    fun setText(text: String)
    fun getText(): String

    fun setLanguage(languageId: String)
    fun setTechnology(technology: TechnologyMetadata)

    fun setReadOnly(readOnly: Boolean)

    fun setDiagnostics(diagnostics: List<EditorDiagnostic>)
    fun setCompletionContext(context: EditorCompletionContext)

    fun focus()

    var onTextChanged: ((String) -> Unit)?
    var onCursorChanged: ((EditorCursor) -> Unit)?
    var onCompletionRequested: ((CompletionRequest) -> List<CompletionSuggestion>)?
}
```

No other module should depend on CodeMirror directly.

---

### 12.3 JavaScript Bridge

The WebView editor shall expose bridge messages such as:

```text
Kotlin -> WebView
- setText
- setLanguage
- setDiagnostics
- setCompletionItems
- setTheme
- focus

WebView -> Kotlin
- textChanged
- cursorChanged
- completionRequested
- editorReady
```

Bridge payloads shall be JSON.

---

### 12.4 CodeMirror Completion Source

The CodeMirror editor shall register a custom completion source.

When completion is requested, JavaScript sends the current editor state to Kotlin:

```json
{
  "nodeId": "node_read_assoc_smt",
  "textSection": "source",
  "languageId": "kotlin",
  "cursorOffset": 58,
  "prefix": "out",
  "lineText": "output[\""
}
```

Kotlin returns completion suggestions:

```json
[
  {
    "label": "read_assoc_smt",
    "insertText": "read_assoc_smt",
    "kind": "OutputPort",
    "detail": "output port",
    "documentation": "Output port of read_assoc_smt"
  }
]
```

CodeMirror displays them.

---

## 13. Metadata-Aware Completion Service

### 13.1 Purpose

The completion service generates suggestions from the current node context.

It shall not be tied to CodeMirror.

It is a core application service.

---

### 13.2 Completion Service Interface

```kotlin
interface NodeCompletionService {
    fun getSuggestions(request: CompletionRequest): List<CompletionSuggestion>
}
```

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

```kotlin
data class CompletionSuggestion(
    val label: String,
    val insertText: String,
    val kind: CompletionSuggestionKind,
    val detail: String = "",
    val documentation: String = ""
)
```

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
    CompilerSymbol
}
```

---

### 13.3 Required Suggestion Sources

For the selected node, the completion service should suggest:

```text
- input port names
- output port names
- incoming link source nodes
- outgoing link target nodes
- sibling node names
- child node names
- parent node name
- imported libraries
- technology-specific helper symbols
- compiler-provided runtime symbols
```

Example suggestions:

```text
input["smt_incident"]
input["car_wo"]

output["assoc_carl_smt"]
output["assoc_carl_snow"]

import["lib_carl_db"]
import["lib_smt_soap"]
```

---

### 13.4 Technology-Specific Suggestions

The completion service should delegate part of the work to technology providers.

```kotlin
interface TechnologyCompletionProvider {
    fun supports(languageId: String, technologyId: String): Boolean

    fun getSuggestions(
        node: Node,
        document: InflowDocument,
        request: CompletionRequest
    ): List<CompletionSuggestion>
}
```

For example, a Kotlin/JVM provider may suggest:

```text
input["name"]
output["name"].add(value)
output["name"].push(value)
```

A future Python provider may suggest:

```text
inputs["name"]
outputs["name"].append(value)
```

---

## 14. Compiler Architecture

### 14.1 Compiler Plugin Interface

```kotlin
interface CompilerPlugin {
    val id: String
    val displayName: String

    fun supports(document: InflowDocument): Boolean

    fun validate(document: InflowDocument): List<CompilerDiagnostic>

    fun compile(
        document: InflowDocument,
        options: CompilerOptions
    ): CompilationResult
}
```

---

### 14.2 Compilation Result

```kotlin
data class CompilationResult(
    val generatedProject: GeneratedProject?,
    val diagnostics: List<CompilerDiagnostic>,
    val success: Boolean
)
```

```kotlin
data class GeneratedProject(
    val name: String,
    val files: List<GeneratedFile>
)
```

```kotlin
data class GeneratedFile(
    val path: String,
    val content: String,
    val originNodeId: NodeId?,
    val reason: String
)
```

---

### 14.3 Naive Compiler Execution Model

The first compiler shall generate deterministic sequential code.

For each composite node:

```text
1. execute child processor/composite nodes in document order
2. execute child link nodes in document order
```

No concurrency.

No async scheduling.

No race analysis.

No optimization.

---

### 14.4 Generated Project Goal

The compiler should emit a real source-code project.

For the first Kotlin/JVM compiler:

```text
generated-project/
├── settings.gradle.kts
├── build.gradle.kts
└── src/
    └── main/
        └── kotlin/
            └── generated/
                ├── Main.kt
                ├── Runtime.kt
                └── nodes/
                    ├── ReadNewSmt.kt
                    ├── ReadAssocSmt.kt
                    └── ...
```

The generated project should be buildable with Gradle if the generated user code is valid.

---

## 15. Runtime Model for Generated Code

The first compiler shall emit a minimal runtime support layer.

Conceptual runtime:

```kotlin
class RuntimeContext {
    val inputs: MutableMap<String, MutableList<Any?>> = mutableMapOf()
    val outputs: MutableMap<String, MutableList<Any?>> = mutableMapOf()
    val imports: MutableMap<String, Any?> = mutableMapOf()
}
```

Processor function shape:

```kotlin
fun runNode(context: RuntimeContext) {
    // user source text inserted or referenced here
}
```

Link function shape:

```kotlin
fun runLink(context: RuntimeContext, source: String, target: String) {
    val sourceQueue = context.outputs.getOrPut(source) { mutableListOf() }
    val targetQueue = context.inputs.getOrPut(target) { mutableListOf() }

    while (sourceQueue.isNotEmpty()) {
        targetQueue.add(sourceQueue.removeAt(0))
    }
}
```

Composite function shape:

```kotlin
fun runComposite(context: RuntimeContext) {
    runProcessorA(context)
    runProcessorB(context)

    runLink(context, "processorA.output", "processorB.input")
}
```

---

## 16. Diagnostics

Diagnostics shall be associated with the original node where possible.

```kotlin
data class CompilerDiagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val nodeId: NodeId? = null,
    val textSection: NodeTextSection? = null,
    val line: Int? = null,
    val column: Int? = null
)
```

```kotlin
enum class DiagnosticSeverity {
    Info,
    Warning,
    Error
}
```

Diagnostics should be shown in:

```text
- node inspector
- editor gutter if location is known
- compiler output panel
```

---

## 17. Commands and Undo/Redo

The document should be modified through command objects where practical.

Example commands:

```text
CreateNodeCommand
DeleteNodeCommand
MoveNodeCommand
ResizeNodeCommand
UpdateNodeTextCommand
CreateLinkCommand
DeleteLinkCommand
UpdateMetadataCommand
```

This gives:

```text
- undo
- redo
- dirty-state tracking
- easier testing
```

For MVP, text editing may be batched rather than recording every keystroke.

---

## 18. Validation Rules

The document validation service should check:

```text
- every child id exists
- every parent id exists or is null
- parent/child references are consistent
- every incoming link id exists
- every outgoing link id exists
- link nodes have non-null link data
- link source node exists
- link target node exists
- source port exists
- target port exists
- source port direction is Output
- target port direction is Input
- no duplicate child id in same parent
- no duplicate port name for same direction on same node
```

Compiler validation may add stricter rules.

---

## 19. Migration from Current Implementation

The current implementation stores:

```text
- boxes/drawables
- metadata JSON
- technology/language rows
- calculated DB views for hierarchy and links
```

The rewrite shall not depend on MariaDB.

However, a migration importer may later read the existing exported JSON shape.

Useful mappings:

```text
Current anchor.x      -> NodeLayout.x
Current anchor.y      -> NodeLayout.y
Current size.x        -> NodeLayout.width
Current size.y        -> NodeLayout.height

Current parent.ref    -> Node.parentId
Current children      -> Node.children
Current incomingLinks -> Node.incomingLinks
Current outgoingLinks -> Node.outgoingLinks

Current metadata.text -> NodeText.source
Current technology    -> TechnologyMetadata
Current ent_type      -> NodeKind or metadata["legacyEntityType"]
```

---

## 20. MVP Scope

The MVP shall implement:

```text
1. Open desktop application
2. Create new document
3. Save document as JSON
4. Load document from JSON
5. Display hierarchy tree
6. Display canvas with nodes and links
7. Create, move, resize, rename, and delete nodes
8. Create links between nodes/ports
9. Edit four node text sections
10. Use CodeMirror for source/spec/test/AI text editing
11. Generate metadata-aware completions
12. Compile using first naive Kotlin/JVM compiler
13. Show compiler diagnostics
14. Export generated project to folder
```

---

## 21. Non-MVP Scope

The MVP shall not implement:

```text
- database backend
- real-time collaboration
- advanced concurrent scheduling
- advanced graph auto-layout
- full language-server integration
- plugin marketplace
- cross-language compilation
- cloud synchronization
- visual diff/merge
```

These should be avoided until the core model, editor, and naive compiler work reliably.

---

## 22. Agent Work Packages

The implementation should be split into coding-agent tasks.

### Package 1 — Core Model

Deliver:

```text
- Node
- InflowDocument
- NodeLayout
- NodeText
- NodePort
- LinkData
- TechnologyMetadata
- validation helpers
- unit tests
```

---

### Package 2 — In-Memory Repository and JSON Store

Deliver:

```text
- DocumentRepository
- in-memory implementation
- JSON save/load
- dirty-state tracking
- reference consistency checks
- tests
```

---

### Package 3 — Desktop Shell

Deliver:

```text
- Compose desktop application
- main window
- menu
- toolbar
- docking/panel layout
- selection state
```

---

### Package 4 — Canvas Graph View

Deliver:

```text
- grid rendering
- node rendering
- link rendering
- selection
- drag/move
- resize
- pan/zoom
```

---

### Package 5 — Tree View and Inspector

Deliver:

```text
- hierarchy tree
- node selection
- node property editor
- metadata editor
- port editor
- link endpoint editor
```

---

### Package 6 — CodeMirror WebView Adapter

Deliver:

```text
- embedded WebView
- CodeMirror bundle
- Kotlin/JS bridge
- set/get text
- language selection
- text change events
- completion request bridge
```

---

### Package 7 — Completion Service

Deliver:

```text
- NodeCompletionService
- model-derived suggestions
- technology-specific provider interface
- Kotlin/JVM provider
- tests
```

---

### Package 8 — Compiler API

Deliver:

```text
- CompilerPlugin
- CompilationResult
- GeneratedProject
- GeneratedFile
- diagnostics
```

---

### Package 9 — Naive Kotlin Compiler

Deliver:

```text
- generate Gradle project
- generate Runtime.kt
- generate node files
- generate composite runner
- generate link transfer code
- export project to directory
- diagnostics
```

---

## 23. Main Architectural Rules

### Rule 1

The core module owns the shared model.

No UI-specific types shall leak into the core model.

---

### Rule 2

The document is stored in memory during editing.

Persistence is explicit JSON save/load.

---

### Rule 3

CodeMirror is an implementation detail of the editor adapter.

The application shall not become a JavaScript application.

---

### Rule 4

Metadata-aware completions are generated by Kotlin services, not hardcoded inside CodeMirror JavaScript.

---

### Rule 5

Links are first-class nodes.

A link is not merely a canvas line.

---

### Rule 6

The first compiler is sequential and deterministic.

No concurrency until the model and generated-project workflow are stable.

---

## 24. Summary

The rewrite shall be a Kotlin desktop application centered around a single shared node model. The active document shall live in memory and be saved/loaded as JSON. The UI shall use Compose Multiplatform, with CodeMirror embedded in a WebView only for rich text/code editing. The editor must receive metadata-aware completions from Kotlin services based on the selected node, its ports, links, parent, children, siblings, imports, language, and technology. The first compiler shall be a naive Kotlin/JVM project generator that produces a buildable source-code project from the node hierarchy.
