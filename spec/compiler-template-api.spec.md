# Compiler Template API

`TemplateSetCompiler` supplies traversal, node classification, layout handling, file assembly, and template context construction. A compiler specialization can therefore be defined as a `CompilerTemplateSet` instead of implementing Kotlin generation methods.

Templates use Pebble syntax. Values use `{{ value }}`, branches use `{% if ... %}`, and collections use `{% for ... %}`. Legacy `${value}` placeholders remain accepted.

The built-in Node.js, PHP, and Kotlin compilers are template-set specializations. Their Kotlin classes contain only compiler identity, supported technology metadata, validation, and template-set loading; language generation lives under `compilers-impl/src/main/resources/compiler-templates/`.

## Resource Template Sets

`CompilerTemplateSetLoader` reads a classpath `compiler.properties` manifest. Use `template.<role>=<resource>` for role templates and these common properties:

- `fileExtension` and `defaultLayoutStrategyId` define the default generated artifact.
- `staticFiles` lists names copied literally from the design.
- `emitLinkFiles=false` keeps link declarations inside composite output.
- `skipCompilerTemplates=true` excludes graphical override nodes from generated source.
- `project.<n>.path` and `project.<n>.content` define support files. Optional `layouts` limits a file to named layout strategies.

This keeps compiler specializations declarative and allows templates to be inspected or replaced without rebuilding traversal and filesystem-layout logic.

## Template Roles

Entity roles have declaration and instantiation variants:

- `node.*`, `processor.*`, `link.*`, `group.*`, and `note.*` are `NodeKind` fallbacks.
- `<stereotype>.declaration` and `<stereotype>.instantiation`, such as `generator.declaration`, take precedence over kind fallbacks.
- A layout suffix has the highest precedence for entity generation, for example `processor.declaration.single-file` or `primary-file.path.source-set`.
- `composite.declaration` and `composite.instantiation` apply to any non-link node with children.
- `composite.single-file`, `composite.direct-file-system`, `composite.classified-file-system`, and `composite.source-set` assemble composite output for a layout.
- `composite.file-based` is the fallback assembly for non-single-file layouts.
- `child.import`, `runtime.support`, and `primary-file.path` generate supporting text or override the output path.

Project files are `TemplateGeneratedFile` entries with independent path and content templates. `static-file.path` controls literal-file paths, while `project.name` may normalize the compiler's project name.

## Template Context

Every entity template receives `document`, `options`, `node`/`self`, `parent`, `metadata`, `text`, `technology`, `layout`, `children`, `ports`, `incomingLinks`, `outgoingLinks`, and `dependencyInjectionLinks`. Compiled composites additionally receive `childArtifacts`, `inlineChildArtifacts`, `externalChildArtifacts`, declarations, instantiations, imports, the effective layout strategy, and the primary path. Artifact entries expose their generated path, module path, declaration, instantiation, and node symbols. Link templates receive `link`, `sourceNode`, `targetNode`, and qualified endpoint references.

## Wire Type Analysis

The payload type is defined by the link's `payloadDefinition`. This is the only
type-related field that the user must fill in. Its syntax belongs to the
selected compiler technology. The compiler should infer the nominal type name,
a default wire name, and an instantiation expression when the language permits
that inference.

The wire name and payload type name are distinct:

- The link node name is the variable, argument, or wire instance name.
- `LinkData.typeName` is the payload type reference.
- `LinkData.payloadDefinition` is the complete declaration or schema.

The inferred wire and type names shall be stored so they remain stable, but the
user may override them. Name inference is a convenience, not a semantic
guarantee: two links carrying `WorkOrder` may be named `requestedOrder` and
`validatedOrder`.

A compiler that supports type inference should expose the following capability:

```kotlin
data class LinkTypeAnalysis(
    val typeName: String,
    val suggestedLinkName: String,
    val normalizedDefinition: String,
    val instantiationExpression: String?,
    val diagnostics: List<Diagnostic> = emptyList(),
)

interface LinkTypeAnalyzer {
    fun analyzeType(
        document: InflowDocument,
        link: Node,
        definition: String,
    ): LinkTypeAnalysis
}
```

`instantiationExpression` is optional. A declaration such as
`WorkOrder(id: String)` cannot safely produce `WorkOrder()` without constructor
arguments. The compiler must report uncertainty instead of manufacturing an
invalid expression.

Pebble templates render analyzed values but are not expected to parse arbitrary
programming languages. Parsing and normalization belong to a
technology-specific `LinkTypeAnalyzer` or compiler plugin.

## Typed Link Artifacts

Link analysis runs before processor generation. It produces an artifact made
available to the link and both endpoint node contexts:

```kotlin
data class CompiledLinkType(
    val linkId: NodeId,
    val variableName: String,
    val typeName: String,
    val declaration: String,
    val instantiationExpression: String?,
    val generatedFile: VirtualFile?,
)
```

Compilation follows these rules:

1. Analyze every in-scope link and diagnose invalid or ambiguous definitions.
2. Deduplicate equivalent type declarations within their compilation scope.
3. Expose the resolved type to completions and read-only editor context at both
   endpoint nodes. Do not copy it into either node's stored declaration.
4. In a single-file layout, emit each unique type declaration once before the
   processor declarations. No imports are required.
5. In a multi-file layout, link compilation owns the generated type module or
   file. Every generated endpoint file must import or otherwise reference that
   artifact using technology-specific syntax.
6. Generate transport declarations and invocations using the resolved wire
   name and type reference.

Templates receive resolved links through `incomingLinks`, `outgoingLinks`, and
`dependencyInjectionLinks`. Each descriptor provides `variableName`,
`typeName`, `typeDefinition`, `argument`, endpoint identifiers, and port names.
Direct link templates additionally receive `link.sourceReference` and
`link.targetReference`.

## Graphical Overrides

The generic flow-design compiler builds a `CompilerTemplateSet` from descriptive nodes such as `@ProcessorDeclaration`, `@GeneratorInstantiation`, `@CompositeSingleFile`, `@ChildImport`, `@PrimaryFilePath`, `@StaticFilePath`, and `@ProjectName`. It then delegates compilation to the same `TemplateSetCompiler` kernel used by the built-in compilers. Existing names such as `@Generator` remain declaration aliases.

An `@ProjectFile` node defines a generated support file. Set its `path` metadata to the path template and put the content template in its declaration text. `@StaticFile` remains the literal-file mechanism.

`CompilerCompiler` embeds these role and project-file templates into a generated compiler class. The resulting compiler does not require its target project to contain the original override nodes.
