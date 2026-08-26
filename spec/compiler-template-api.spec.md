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

- `node.*`, `processor.*`, `link.*`, `group.*`, `type.*`, and `note.*` are `NodeKind` fallbacks.
- `<stereotype>.declaration` and `<stereotype>.instantiation`, such as `generator.declaration`, take precedence over kind fallbacks.
- A layout suffix has the highest precedence for entity generation, for example `processor.declaration.single-file` or `primary-file.path.source-set`.
- `composite.declaration` and `composite.instantiation` apply to any non-link node with children.
- `composite.single-file`, `composite.direct-file-system`, `composite.classified-file-system`, and `composite.source-set` assemble composite output for a layout.
- `composite.file-based` is the fallback assembly for non-single-file layouts.
- `child.import`, `runtime.support`, and `primary-file.path` generate supporting text or override the output path.

Project files are `TemplateGeneratedFile` entries with independent path and content templates. `static-file.path` controls literal-file paths, while `project.name` may normalize the compiler's project name.

## Template Context

Every entity template receives `document`, `options`, `node`/`self`, `parent`, `metadata`, `text`, `technology`, `layout`, `children`, `ports`, `incomingDataLinks`, `outgoingDataLinks`, and `dependencyInjectionLinks`. Type templates also receive `typeFields`. Compiled composites additionally receive `childArtifacts`, `inlineChildArtifacts`, `externalChildArtifacts`, declarations, instantiations, imports, the effective layout strategy, and the primary path. Artifact entries expose their generated path, module path, declaration, instantiation, and node symbols. Link templates receive `link`, `sourceNode`, `targetNode`, and qualified endpoint references.

## Shared Types and Typed Links

New designs define payload structures as first-class `NodeKind.Type` entities.
A link's `typeDefinitionId` selects either a built-in type (`string`, `number`,
`date`, or `array`) or a Type node ID. The link node name remains the user-defined
wire and buffer identifier; it is not inferred from the Type name.

`type.declaration` receives `typeFields` and emits the technology-specific shared
declaration. Each field exposes `name`, sanitized `symbol`, `typeId`, display
`typeName`, sanitized `typeSymbol`, and `isReference`. Compilers without a native
array representation must supply one in `runtime.support`.

When a selected compilation scope includes both endpoints of a link, the kernel
also includes that link's Type and recursively referenced custom Types. This
ensures that generated endpoint and transport code never loses its contract just
because the Type is a sibling elsewhere in the hierarchy.

Legacy inline `typeName` and `payloadDefinition` link fields remain available to
compiler templates as a fallback. They do not replace shared Type declarations.

## Double-Buffered Link Transport

Every ordinary data link compiles to two FIFO buffers and a named transport
operation. For a link whose sanitized symbol is `orders`, templates receive:

```text
link.transportSymbol = transport_orders
link.aPortSymbol      = orders_a_port
link.bPortSymbol      = orders_b_port
link.typeSymbol       = resolved payload type
```

Processor invocation uses incoming B buffers and outgoing A buffers. A composite
executes its child processors and then invokes each link transport operation:

```text
source(..., orders_a_port)
target(..., orders_b_port)
transport_orders(orders_a_port, orders_b_port)
```

The transport moves at most one packet from A to B per call. Separating endpoint
buffers prevents a producer and consumer from sharing one mutable queue and
preserves a path toward parallel processor execution. Dependency-injection links
remain library bindings and are excluded from data buffers and transport calls.

Templates receive resolved links through `incomingDataLinks`,
`outgoingDataLinks`, and `dependencyInjectionLinks`. Descriptors also provide
the original endpoint IDs and port names, Type fields, and source/target symbols.

## Graphical Overrides

The generic flow-design compiler builds a `CompilerTemplateSet` from descriptive nodes such as `@ProcessorDeclaration`, `@GeneratorInstantiation`, `@CompositeSingleFile`, `@ChildImport`, `@PrimaryFilePath`, `@StaticFilePath`, and `@ProjectName`. It then delegates compilation to the same `TemplateSetCompiler` kernel used by the built-in compilers. Existing names such as `@Generator` remain declaration aliases.

An `@ProjectFile` node defines a generated support file. Set its `path` metadata to the path template and put the content template in its declaration text. `@StaticFile` remains the literal-file mechanism.

`CompilerCompiler` embeds these role and project-file templates into a generated compiler class. The resulting compiler does not require its target project to contain the original override nodes.
