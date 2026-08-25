# Developing a C Compiler Template Set

This guide describes a practical, single-file C compiler for Threadwork using
the existing `TemplateSetCompiler` kernel. It is an implementation plan, not a
claim that C can use the JavaScript/PHP templates unchanged. C requires
declarations visible before use, globally unique symbols, and a deliberate entry
point. Threadwork should satisfy those requirements in one generated translation
unit rather than construct an include tree that the C preprocessor immediately
merges again.

## Target Contract

Use these initial compiler identifiers:

- Compiler ID: `c-compiler`
- Language ID: `c`
- Technology ID: `c-native`
- Primary extension: `c`
- Required layout: `single-file`

The compiler should turn each processor/composite into callable functions,
materialize modeled link contracts as C type declarations, generate transport
operations for owned links, and provide a root `main`. The result is one `.c`
file named from the compiled project or selected node. Keep generated identifiers
independent from display names by using the supplied `symbol`, `runSymbol`, or a
C-specific sanitizer if stricter rules are required.

### Why single-file only

An include hierarchy does not buy this initial compiler useful isolation: the C
preprocessor ultimately presents the compiler with one expanded translation
unit. Threadwork already owns the complete graph and compiles children before
parents, so it can order and deduplicate includes, wire types, prototypes,
functions, transport operations, and the entry point directly. This avoids
generating headers, include guards, relative include paths, and a source-file
dependency tree before those features provide a concrete benefit.

"Single file" refers to generated C source. Optional build metadata such as a
Makefile may accompany it later, but it is not part of the source layout and
must never be merged into the `.c` content.

## Understand the Kernel

`TemplateSetCompiler` performs the work that should not be repeated in a C
specialization:

1. Determine compilation scope and compile child nodes before parents.
2. Delegate a subtree when its effective technology belongs to another compiler.
3. Resolve each node's effective layout; the C specialization rejects any
   explicit non-`single-file` result.
4. Build node/link/artifact context maps.
5. Select declaration/instantiation templates by stereotype and kind.
6. Assemble composites from inline child declarations, link declarations, and
   the composite's own declaration.
7. Emit one root generated file through `SingleFileLayoutStrategy`.

The C compiler should therefore provide syntax templates, not reimplement graph
walking or filesystem traversal.

## Recommended Files

Create this resource set:

```text
compilers-impl/src/main/resources/compiler-templates/c/
  README.md
  compiler.properties
  processor-forward-declaration.peb
  processor.peb
  composite-forward-declaration.peb
  composite.peb
  link-forward-declaration.peb
  link-declaration.peb
  link-instantiation.peb
  runtime.peb
  assembly-single.peb
```

No `child.import`, file-based assembly, header, or separate `main.c` template is
needed. Child declarations are supplied by `inlineChildDeclarations`, and the
root single-file assembly owns runtime emission and `main`.

## Suggested Manifest

```properties
fileExtension=c
defaultLayoutStrategyId=single-file
emitLinkFiles=false
skipCompilerTemplates=true

template.processor.declaration=processor.peb
template.node.declaration=processor.peb
template.processor.forward-declaration=processor-forward-declaration.peb
template.node.forward-declaration=processor-forward-declaration.peb
template.group.declaration=composite.peb
template.composite.declaration=composite.peb
template.group.forward-declaration=composite-forward-declaration.peb
template.composite.forward-declaration=composite-forward-declaration.peb
template.link.declaration=link-declaration.peb
template.link.forward-declaration=link-forward-declaration.peb
template.link.instantiation=link-instantiation.peb
template.note.declaration=note.peb
template.runtime.support=runtime.peb
template.composite.single-file=assembly-single.peb
```

The template kernel currently advertises every standard layout. A production C
compiler should additionally validate that every node it owns resolves to
`single-file` and report any override as an error. This keeps the contract honest
until supported layouts can be declared by an individual template set.

## Runtime Shape

Each compiler technology should own a small runtime because queue representation,
memory ownership, transport, scheduling, error propagation, and library injection
are language- and platform-specific. Templates generate application structure;
the runtime supplies the stable execution ABI used by those snippets.

For C, `runtime.peb` should be emitted exactly once by the root
`assembly-single.peb`. It should at minimum define context storage and named-link
transport. One possible ABI is:

```c
typedef struct threadwork_context threadwork_context;

void threadwork_transport(
    threadwork_context *context,
    const char *link_reference,
    const char *source,
    const char *target
);
```

The implementation may use generated buffers, queues, callbacks, or another
explicit ownership policy. The modeled transport kind can later select
specialized implementations. Initially, preserve the link reference as an
explicit argument so diagnostics and runtime state can identify the exact modeled
wire.

Keep the runtime deliberately narrow. It should provide reusable mechanics, not
contain project-specific processor logic. A useful division is:

- generated templates: processor functions, composite ordering, link/type
  declarations, endpoint references, and `main`;
- technology runtime: context lifetime, buffers/queues, packet movement, errors,
  and optional scheduling hooks;
- user declarations: domain behavior and concrete wire type definitions.

The existing Node.js and PHP sets already provide a `runtime.support` template
that implements named-link queue transport. Kotlin provides the same concepts as
`RuntimeContext`/`runLink`, emitted in `Runtime.kt` for file layouts and inline at
the root for single-file output. C should follow the latter root-inline model.

## Template Responsibilities

### Forward declarations

Forward declarations are a separate generation phase from definitions. A
`*.forward-declaration` template receives the same node context as its matching
`*.declaration` template, but emits only the file-scope line needed before the
definition is encountered. For processors and composites this normally means:

```c
void {{ initializerSymbol }}(threadwork_context *context);
void {{ runSymbol }}(threadwork_context *context);
```

If one entity requires both entry points, its template may emit both prototype
lines. The compiler artifact records the entity's `forwardDeclaration` and the
recursively accumulated `forwardDeclarations`. During composite assembly the
following values are available:

- `ownForwardDeclaration`: prototype text for the current entity;
- `childForwardDeclarations`: recursively collected processor/composite child
  prototypes;
- `linkForwardDeclarations`: recursively collected wire/type prototypes;
- `descendantForwardDeclarations`: child and link blocks combined;
- `forwardDeclarations`: the complete, order-preserving, deduplicated block for
  the current subtree, including the current entity.

The root `assembly-single.peb` emits `forwardDeclarations` once after runtime
types and before any function definitions. This permits functions to call later
definitions and makes generated ordering independent from call direction.

A link forward declaration is valid only when its C type grammar supports one.
For a named structure it can be:

```c
typedef struct {{ link.typeName }} {{ link.typeName }};
```

Do not invent a forward declaration for scalar typedefs, anonymous structures,
or arbitrary user definitions. In those cases leave
`link-forward-declaration.peb` empty and place the complete type definition
before the function prototype block.

### Processor declaration

`processor.peb` receives full node context. Emit two functions if the setup/run
split is retained:

```c
void {{ initializerSymbol }}(threadwork_context *context) {
{{ instantiationIndent4 }}
}

void {{ runSymbol }}(threadwork_context *context) {
{{ declarationIndent4 }}
}
```

The instantiation text is setup code; declaration text is recurring execution
code. Include or generate `incomingTypeDefinitions` and
`outgoingTypeDefinitions` only under a policy that avoids duplicate declarations
inside one translation unit.

### Composite declaration

`composite.peb` receives `childArtifacts` and `linkArtifacts`. Its initializer
calls child initializers. Its runner calls child runners and then emits each link
artifact's instantiation. This is the C control-flow representation of hierarchy.
The composite does not solve filesystem layout. It always contributes inline
file-scope C declarations that its logical parent can incorporate.

### Link declaration

`link-declaration.peb` receives link-only context:

- `link.name` is the modeled variable/transport instance name.
- `link.typeName` is the C type name supplied by the user.
- `link.typeDefinition` is the complete C declaration stored on the wire.
- `sourceNode` and `targetNode` describe both endpoint nodes.

Emit `link.typeDefinition` as source, preferably with an include guard or another
deduplication mechanism. Do not infer a C declaration from arbitrary text unless
the compiler owns and validates that grammar. A first implementation can require:

```c
typedef struct WorkOrder {
    int id;
} WorkOrder;
```

The editor exposes this definition at both endpoint nodes. It must appear once
before any processor that references it. Deduplicate identical definitions and
reject one `typeName` associated with conflicting source.

### Link instantiation

Use the modeled link and endpoint references:

```c
threadwork_transport(
    context,
    "{{ link.escapedNameDoubleQuoted }}",
    "{{ link.escapedSourceReferenceDoubleQuoted }}",
    "{{ link.escapedTargetReferenceDoubleQuoted }}"
);
```

This snippet belongs in the owning composite runner after the relevant producer
has executed. It must reference the link identity; generic `in`/`out` labels are
not sufficient to distinguish multiple wires.

### Single-file assembly

`assembly-single.peb` additionally receives `inlineChildDeclarations`. A valid C
single-file assembly must keep all function/type definitions at file scope; C
does not portably support nested functions. Order the output as:

1. standard includes, runtime types, and complete wire type definitions, only when
   `isCompilationRoot` is true;
2. the deduplicated `forwardDeclarations` prototype block;
3. inline child function definitions;
4. current composite function definitions;
5. root-only `main`.

Use `isCompilationRoot` to prevent nested composites from emitting extra entry
points.

The root assembly should be the only generated C source file and should contain
the only `main`. Avoid project-file templates initially: `StructuredCompiler`
collects those alongside source artifacts, and auxiliary text must not be
mistaken for C source. Build metadata can be introduced as a separately packaged
artifact once the generated-project contract distinguishes source from auxiliary
files explicitly.

## Type Placement Strategy

Start with a clear policy:

- collect owned link declarations in each composite assembly and deduplicate by
  type name before final output if a type crosses composite levels;
- reject conflicting definitions that use one `typeName` with different source;
- preserve `link.name` as the variable/instance name and `link.typeName` as the C
  type. They are intentionally different concepts.

The generic context already supplies `incomingLinks`, `outgoingLinks`,
`incomingTypeDefinitions`, `outgoingTypeDefinitions`, `incomingArguments`, and
`outgoingArguments`. The aggregate argument strings use Threadwork's neutral
`name:type` notation, not C syntax; iterate the descriptor lists when generating
C parameters such as `WorkOrder *input_record`.

## Compiler Class

Add `compilers-impl/src/main/kotlin/com/threadwork/compiler/c/CCompiler.kt`:

```kotlin
package com.threadwork.compiler.c

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.generic.CompilerTemplateSet
import com.threadwork.compiler.generic.CompilerTemplateSetLoader
import com.threadwork.compiler.generic.TemplateSetCompiler
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.VOID_LAYOUT_STRATEGY_ID
import com.threadwork.core.validation.DocumentValidator
import com.threadwork.core.model.effectiveLayoutStrategyId
import com.threadwork.core.model.effectiveTechnologyId

class CCompiler : TemplateSetCompiler() {
    override val id = "c-compiler"
    override val displayName = "C Compiler"
    override val supportedLanguageIds = setOf("c")
    override val supportedTechnologyIds = setOf("c-native")
    override val providedTechnologies = listOf(CompilerTechnology("c", "c-native"))
    override val magicFileNames = TEMPLATES.staticFileNames

    override fun supports(document: ThreadworkDocument) = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        DocumentValidator.validate(document) + document.nodes.values.mapNotNull { node ->
            val layoutId = document.effectiveLayoutStrategyId(node.id)
            val ownedByC = document.effectiveTechnologyId(node.id) == "c-native"
            if (
                ownedByC &&
                layoutId != VOID_LAYOUT_STRATEGY_ID &&
                layoutId != SingleFileLayoutStrategy.id
            ) {
                Diagnostic(
                    DiagnosticSeverity.Error,
                    "C compilation supports only the single-file layout.",
                    node.id,
                    sourcePluginId = id,
                )
            } else {
                null
            }
        }

    override fun templatesFor(
        document: ThreadworkDocument,
        options: CompilerOptions,
    ): CompilerTemplateSet = TEMPLATES

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load(
            "/compiler-templates/c/compiler.properties",
        )
    }
}
```

Add C-specific validation once the templates work: valid C identifiers, unique
generated symbols, non-empty link type names, parseable type definitions, and one
unambiguous root entry point.

## Register in the Current Application

For a built-in compiler:

1. Import and append `CCompiler()` in `compilersFrom` in
   `app-desktop/src/main/kotlin/com/threadwork/app/Main.kt`.
2. Import and append `CCompiler()` to `compilerPlugins` in
   `app-desktop/src/main/kotlin/com/threadwork/app/ui/ThreadworkDesktopApp.kt`.
3. Assign language `c`, technology `c-native`, and compiler `c-compiler` to the
   project root or relevant composite. Descendants inherit unspecified values.
4. Ensure C is present in syntax highlighting and extension metadata.

For an external plugin JAR, implement the same class against `compiler-api`, add
this service descriptor to the JAR:

```text
META-INF/services/com.threadwork.compiler.api.CompilerPlugin
```

with this content:

```text
com.example.threadwork.compiler.c.CCompiler
```

Place the JAR in the configured plugins folder. The desktop and CLI use
`ServiceLoader` for that folder, so no application source edit is required.

## Verification

Add compiler tests covering at least:

1. one terminal processor;
2. a producer/link/consumer chain with a C struct definition;
3. one nested composite;
4. output with exactly one `.c` file, one `main`, one runtime, and no duplicate
   functions, prototypes, or types;
5. rejection of a C-owned node whose effective layout is not `single-file`;
6. a mixed-technology tree delegated to another compiler;
7. invalid or conflicting link type definitions.

Run:

```bash
GRADLE_USER_HOME=.gradle-user ./gradlew :compilers-impl:test
GRADLE_USER_HOME=.gradle-user ./gradlew test
```

Inspect generated C with a real compiler in CI, for example `cc -std=c17 -Wall
-Wextra -Werror`, because successful Pebble rendering proves only that text was
generated, not that the result is valid C.
