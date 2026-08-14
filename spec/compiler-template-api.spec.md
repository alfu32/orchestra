# Compiler Template API

`TemplateSetCompiler` supplies traversal, node classification, layout handling, file assembly, and template context construction. A compiler specialization can therefore be defined as a `CompilerTemplateSet` instead of implementing Kotlin generation methods.

Templates use Pebble syntax. Values use `{{ value }}`, branches use `{% if ... %}`, and collections use `{% for ... %}`. Legacy `${value}` placeholders remain accepted.

## Template Roles

Entity roles have declaration and instantiation variants:

- `node.*`, `processor.*`, `link.*`, `group.*`, and `note.*` are `NodeKind` fallbacks.
- `<stereotype>.declaration` and `<stereotype>.instantiation`, such as `generator.declaration`, take precedence over kind fallbacks.
- `composite.declaration` and `composite.instantiation` apply to any non-link node with children.
- `composite.single-file`, `composite.direct-file-system`, `composite.classified-file-system`, and `composite.source-set` assemble composite output for a layout.
- `composite.file-based` is the fallback assembly for non-single-file layouts.
- `child.import`, `runtime.support`, and `primary-file.path` generate supporting text or override the output path.

Project files are `TemplateGeneratedFile` entries with independent path and content templates.

## Template Context

Every entity template receives `document`, `options`, `node`/`self`, `parent`, `metadata`, `text`, `technology`, `layout`, `children`, `ports`, `incomingLinks`, `outgoingLinks`, and `dependencyInjectionLinks`. Compiled composites additionally receive `childArtifacts`, `linkArtifacts`, declarations, instantiations, imports, the effective layout strategy, and the primary path. Link templates receive `link`, `sourceNode`, and `targetNode`.

## Graphical Overrides

The generic flow-design compiler maps descriptive nodes such as `@ProcessorDeclaration`, `@GeneratorInstantiation`, `@CompositeSingleFile`, `@ChildImport`, and `@PrimaryFilePath` to the corresponding roles. Existing names such as `@Generator` remain declaration aliases.

An `@ProjectFile` node defines a generated support file. Set its `path` metadata to the path template and put the content template in its declaration text. `@StaticFile` remains the literal-file mechanism.

`CompilerCompiler` embeds these role and project-file templates into a generated compiler class. The resulting compiler does not require its target project to contain the original override nodes.
