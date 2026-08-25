# Threadwork C Compiler — Implementation Specification

## 1. Scope

The C compiler SHALL implement a native C17 backend for Threadwork using the existing `TemplateSetCompiler` infrastructure.

Initial identifiers SHALL be:

```text
compilerId    = c-compiler
languageId    = c
technologyId  = c-native
extension     = c
defaultLayout = single-file
```

The first implementation SHALL generate exactly one C translation unit for the C-owned compilation root.

The compiler SHALL NOT initially generate:

* `.h` files;
* multiple `.c` files;
* Makefiles;
* CMake projects;
* an include hierarchy;
* separate runtime files.

This follows the existing C design: Threadwork already owns the complete graph, traverses it child-first, and can therefore assemble the complete C translation unit without manufacturing an include tree.

A compilation involving delegated non-C technologies MAY produce additional artifacts from those compilers, but the C compiler itself SHALL emit one C translation unit.

---

# 2. Architectural Principle

`CCompiler` SHALL remain a specialization of:

```text
TemplateSetCompiler
```

It SHALL NOT implement its own graph traversal.

The generic compiler remains responsible for:

1. determining compilation scope;
2. child-first traversal;
3. technology delegation;
4. effective-layout resolution;
5. construction of node/link context;
6. template selection;
7. composite assembly;
8. application of `SingleFileLayoutStrategy`.

The C compiler SHALL be responsible only for:

```text
C syntax
+
C-specific validation
+
C-specific declaration ordering
+
C runtime ABI
+
C symbol rules
```

This division follows the same architecture used by Kotlin, Node.js and PHP.

---

# 3. Required Resource Set

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

    note.peb

    runtime.peb
    assembly-single.peb
```

`note.peb` is explicitly required.

## The current C guide references it from `compiler.properties` but accidentally omits it from the proposed file list.

# 4. Manifest

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

No `child.import` role SHALL exist for the initial C implementation.

No file-based composite assembly SHALL exist.

---

# 5. Compilation Model

The generated translation unit SHALL conceptually have five regions:

```text
1. prelude
2. type declarations
3. function prototypes
4. function definitions
5. entry point
```

The exact resulting source SHALL have this structure:

```c
/* generated banner */

/* standard includes */

/* Threadwork runtime declarations/types */

/* complete modeled data-type declarations */

/* generated function prototypes */

/* generated child function definitions */

/* generated root function definitions */

/* runtime implementation if not emitted above */

/* main() */
```

More precisely:

```text
includes
↓
runtime type declarations
↓
complete wire/domain type definitions
↓
forward declarations
↓
processor/composite definitions
↓
main
```

All declarations and definitions SHALL remain at file scope.

The compiler MUST NOT rely on GCC nested-function extensions. The C guide correctly requires portable file-scope functions.

---

# 6. Important Addition: Hoisted Type Prelude

Function prototypes alone are insufficient for C.

Threadwork SHALL distinguish between:

```text
type declarations required before code
```

and:

```text
function forward declarations
```

These are separate compilation concepts.

## 6.1 Reason

Consider:

```text
Root
 ├── Composite A
 │    └── Processor P
 └── link WorkOrder
```

where `P` contains:

```c
WorkOrder order;
```

If the parent's `WorkOrder` definition is emitted after the inlined definition of `P`, the generated source is invalid C.

Therefore link definitions cannot merely remain inside their owning composite.

They must be hoisted.

## 6.2 Kernel extension

The generic artifact model SHOULD gain a declaration aggregation equivalent to the existing forward-declaration aggregation.

Recommended fields:

```text
ownHoistedDeclaration
childHoistedDeclarations
linkHoistedDeclarations
descendantHoistedDeclarations
hoistedDeclarations
```

The semantic relationship becomes:

```text
hoistedDeclarations
    = recursively required declarations-before-code

forwardDeclarations
    = recursively required function prototypes
```

For C:

```text
link.declaration
        ↓
linkHoistedDeclarations
        ↓
hoistedDeclarations
        ↓
root assembly
```

Other languages may simply ignore these fields.

This is preferable to abusing `link.forward-declaration` to contain complete typedef definitions.

---

# 7. Type Declaration Rules

`link.name` and `link.typeName` SHALL remain separate concepts:

```text
link.name      = transport instance identity
link.typeName  = C data type
```

The existing model already intentionally distinguishes them.

For example:

```text
link.name       = orders-to-validator
link.typeName   = WorkOrder
link.typeDefinition =
    typedef struct WorkOrder {
        int id;
        double total;
    } WorkOrder;
```

The generated source contains the type exactly once:

```c
typedef struct WorkOrder {
    int id;
    double total;
} WorkOrder;
```

## 7.1 Deduplication

The compiler SHALL construct a translation-unit type registry:

```text
typeName -> typeDefinition
```

Rules:

```text
same typeName + same definition
    => emit once

same typeName + different definition
    => compilation error
```

Whitespace normalization MAY be used when testing equality, but the emitted declaration SHOULD preserve the original user source.

## 7.2 Forward-declarable structures

A declaration such as:

```c
typedef struct WorkOrder WorkOrder;
```

MAY be emitted by:

```text
link-forward-declaration.peb
```

but only when the compiler knows that the supplied type definition actually represents such a named structure.

The compiler SHALL NOT manufacture forward declarations for arbitrary user-supplied C syntax.

This restriction is already correctly identified in the C guide.

## 7.3 Version-1 recommendation

Do not build a C parser into the template compiler.

For v1:

* preserve `typeDefinition` verbatim;
* deduplicate by `typeName`;
* detect conflicting declarations;
* allow the system C compiler to perform final syntactic/type validation.

A dedicated C parser can be introduced later if Threadwork needs semantic inspection of declarations.

---

# 8. Generated Symbol Rules

C has one translation-unit symbol namespace for generated functions, so generated identifiers MUST be globally unique within the generated unit.

Display names SHALL never directly become C symbols.

Use the existing indexed/generated symbols where possible, but the C backend SHALL additionally apply C identifier validation.

Generated symbols SHOULD follow:

```text
tw_init_<symbol>
tw_run_<symbol>
```

Example:

```c
static int tw_init_order_reader(threadwork_context *context);
static int tw_run_order_reader(threadwork_context *context);
```

Generated application functions SHOULD have internal linkage:

```c
static
```

Only `main` needs external linkage.

The sanitizer SHALL guarantee:

```regex
[A-Za-z_][A-Za-z0-9_]*
```

and SHALL handle:

* spaces;
* punctuation;
* identifiers beginning with numbers;
* duplicate display names;
* C keywords;
* collisions after sanitization;
* collisions with Threadwork runtime symbols.

For example:

```text
"Order Reader"    -> order_reader
"Order-Reader"    -> order_reader_2
"123 parser"      -> _123_parser
"switch"          -> switch_
```

The compiler SHOULD reserve:

```text
threadwork_*
tw_*
```

for generated/runtime symbols.

---

# 9. Processor Contract

Every executable terminal processor SHALL generate two functions:

```c
static int {{ initializerSymbol }}(threadwork_context *context);
static int {{ runSymbol }}(threadwork_context *context);
```

The forward-declaration template therefore emits:

```c
static int {{ initializerSymbol }}(threadwork_context *context);
static int {{ runSymbol }}(threadwork_context *context);
```

The implementation template emits:

```c
static int {{ initializerSymbol }}(threadwork_context *context)
{
{{ instantiationIndent4 }}

    return THREADWORK_OK;
}

static int {{ runSymbol }}(threadwork_context *context)
{
{{ declarationIndent4 }}

    return THREADWORK_OK;
}
```

The mapping SHALL be:

```text
node.instantiation
    -> initialization/setup code

node.declaration
    -> recurring execution code
```

This preserves the same setup/run distinction used by the Kotlin compiler.

---

# 10. Composite Contract

A composite SHALL expose the same two-function interface as a processor:

```c
static int init_composite(threadwork_context *context);
static int run_composite(threadwork_context *context);
```

## Initialization order

```text
composite's own instantiation
↓
child initializer 1
↓
child initializer 2
↓
...
```

Generated shape:

```c
static int tw_init_pipeline(threadwork_context *context)
{
    /* own setup */

    if (tw_init_reader(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    if (tw_init_validator(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    return THREADWORK_OK;
}
```

## Execution order

```text
composite's own declaration
↓
all child run functions
↓
all link transport operations
```

Example:

```c
static int tw_run_pipeline(threadwork_context *context)
{
    /* own execution */

    if (tw_run_reader(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    if (tw_run_validator(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    if (threadwork_transport(
            context,
            "reader-validator",
            "reader.output",
            "validator.input") != THREADWORK_OK)
        return THREADWORK_ERROR;

    return THREADWORK_OK;
}
```

This ordering is important because it preserves the existing compiler semantics used by both Node.js and PHP: child execution occurs first, followed by link transport.
It effectively gives Threadwork a barriered execution step:

```text
execute nodes
↓
transport produced messages
↓
next execution step sees transported messages
```

The C backend SHALL NOT silently introduce different scheduling semantics.

---

# 11. Forward Declaration Phase

Forward declarations SHALL be generated separately from function definitions.

For each processor/composite:

```c
static int {{ initializerSymbol }}(threadwork_context *context);
static int {{ runSymbol }}(threadwork_context *context);
```

The generic compiler already exposes:

```text
ownForwardDeclaration
childForwardDeclarations
linkForwardDeclarations
descendantForwardDeclarations
forwardDeclarations
```

and recursively accumulates these declarations.

At the compilation root the C assembly SHALL emit:

```text
hoistedDeclarations

then

forwardDeclarations
```

before emitting any executable function body.

Consequently source ordering becomes independent of call direction.

---

# 12. Link Declaration

`link-declaration.peb` SHALL represent the modeled data contract.

Its principal source SHALL be:

```text
link.typeDefinition
```

It SHALL NOT synthesize C declarations from arbitrary descriptive text.

Example input:

```text
typeName = WorkOrder
```

```c
typedef struct WorkOrder {
    int id;
} WorkOrder;
```

produces exactly that declaration in the type prelude.

The guide already specifies that definitions should be emitted as source rather than inferred and that conflicting definitions for one `typeName` must be rejected.

---

# 13. Link Instantiation

A link represents runtime transport, not a C variable declaration.

Generated code:

```c
threadwork_transport(
    context,
    "{{ link.escapedNameDoubleQuoted }}",
    "{{ link.escapedSourceReferenceDoubleQuoted }}",
    "{{ link.escapedTargetReferenceDoubleQuoted }}"
)
```

Recommended final form with error propagation:

```c
if (threadwork_transport(
        context,
        "{{ link.escapedNameDoubleQuoted }}",
        "{{ link.escapedSourceReferenceDoubleQuoted }}",
        "{{ link.escapedTargetReferenceDoubleQuoted }}"
    ) != THREADWORK_OK)
{
    return THREADWORK_ERROR;
}
```

The link name MUST be retained.

Using only:

```text
input
output
```

is insufficient because several modeled links can connect the same processors or ports.

The existing C design correctly treats link identity as a runtime-visible value.

---

# 14. Runtime ABI

The C backend SHALL own a small runtime implementation.

The runtime SHALL NOT contain application-specific processor logic.

Its responsibilities are:

```text
context lifetime
endpoint queues
packet storage
link transport
error state
optional future scheduling hooks
```

This follows the same runtime/application separation used by Node.js, PHP and Kotlin.

## Required API

Recommended v1 ABI:

```c
typedef struct threadwork_context threadwork_context;

enum {
    THREADWORK_OK = 0,
    THREADWORK_ERROR = 1
};

int threadwork_context_init(threadwork_context *context);

void threadwork_context_destroy(threadwork_context *context);

int threadwork_output_write(
    threadwork_context *context,
    const char *endpoint,
    const void *data,
    size_t size
);

int threadwork_input_read(
    threadwork_context *context,
    const char *endpoint,
    void *destination,
    size_t capacity,
    size_t *actual_size
);

int threadwork_transport(
    threadwork_context *context,
    const char *link_reference,
    const char *source,
    const char *target
);
```

## Memory ownership

For v1 the ownership policy SHOULD be explicit and simple:

```text
threadwork_output_write()
    copies the supplied bytes into runtime-owned storage

threadwork_transport()
    moves runtime-owned packets between queues

threadwork_input_read()
    copies the packet into caller-provided memory and removes it

threadwork_context_destroy()
    frees every remaining runtime allocation
```

Thus user code never owns runtime queue nodes.

This avoids the most common ambiguity in C runtime APIs: who is responsible for freeing transported data.

---

# 15. Runtime Representation

A simple initial implementation MAY internally use:

```c
typedef struct threadwork_packet {
    void *data;
    size_t size;
    struct threadwork_packet *next;
} threadwork_packet;

typedef struct threadwork_queue {
    threadwork_packet *head;
    threadwork_packet *tail;
} threadwork_queue;
```

The context MAY hold dynamic endpoint/link tables.

The runtime representation is deliberately private.

Generated processors interact only through the runtime API.

Changing queue implementation later SHALL NOT require regeneration-template changes.

---

# 16. Entry Point

Only the compilation root SHALL emit `main`.

Nested composites SHALL never emit:

```c
main()
```

The root SHALL:

1. initialize the runtime context;
2. initialize the root Threadwork node;
3. run the root once;
4. destroy the runtime context;
5. return process status.

Example:

```c
int main(void)
{
    threadwork_context context;

    if (threadwork_context_init(&context) != THREADWORK_OK) {
        return EXIT_FAILURE;
    }

    int status = {{ initializerSymbol }}(&context);

    if (status == THREADWORK_OK) {
        status = {{ runSymbol }}(&context);
    }

    threadwork_context_destroy(&context);

    return status == THREADWORK_OK
        ? EXIT_SUCCESS
        : EXIT_FAILURE;
}
```

A permanent scheduler loop SHALL NOT be implicitly introduced by the C compiler.

If repeated execution is required later, it belongs in the runtime/scheduling contract.

The existing design already requires root-only runtime/entry-point emission via `isCompilationRoot`.

---

# 17. Notes

`note.peb` SHALL always produce valid C.

Example:

```peb
/*
{{ node.declarationBlockComment }}
*/
```

A note SHALL never become executable C merely because a layout emits it into the translation unit.

## This follows the behavior of the Node.js and PHP compilers, which convert note content into language comments.

# 18. Single-File Assembly Template

`assembly-single.peb` is the critical C template.

Conceptually:

```peb
{% if isCompilationRoot %}
/* generated source */

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

{{ runtimeSupport }}

{{ hoistedDeclarations }}

{{ forwardDeclarations }}
{% endif %}

{{ inlineChildDeclarations }}

{{ ownDeclaration }}

{% if isCompilationRoot %}
int main(void)
{
    ...
}
{% endif %}
```

The actual implementation MAY split runtime declarations from runtime function definitions if necessary, but the observable ordering SHALL remain:

```text
required runtime types
types
prototypes
functions
main
```

---

# 19. CCompiler Class

```kotlin
class CCompiler : TemplateSetCompiler() {

    override val id = "c-compiler"

    override val displayName = "C Compiler"

    override val supportedLanguageIds =
        setOf("c")

    override val supportedTechnologyIds =
        setOf("c-native")

    override val providedTechnologies =
        listOf(
            CompilerTechnology(
                "c",
                "c-native"
            )
        )

    override val magicFileNames =
        TEMPLATES.staticFileNames

    override fun supports(
        document: ThreadworkDocument
    ) = true

    override fun validate(
        document: ThreadworkDocument
    ): List<Diagnostic> {
        ...
    }

    override fun templatesFor(
        document: ThreadworkDocument,
        options: CompilerOptions
    ): CompilerTemplateSet =
        TEMPLATES

    private companion object {
        val TEMPLATES =
            CompilerTemplateSetLoader.load(
                "/compiler-templates/c/compiler.properties"
            )
    }
}
```

The class structure can remain essentially the one already proposed by the guide.

---

# 20. Validation

C-specific validation SHALL occur before rendering.

## 20.1 Layout

Every C-owned executable node MUST resolve to:

```text
single-file
```

or the framework's void/no-layout state where applicable.

Anything else:

```text
ERROR:
C compilation supports only the single-file layout.
```

This validation applies only to nodes whose effective technology is:

```text
c-native
```

so delegated foreign nodes are not incorrectly rejected.

## 20.2 Symbols

Reject or sanitize:

* invalid C identifiers;
* C keywords;
* duplicate generated symbols;
* collision with runtime symbols.

Generated collisions SHOULD normally be resolved automatically rather than reported.

## 20.3 Link types

Validate:

```text
typeName exists when required
typeName is usable as configured
same typeName never has conflicting definitions
```

## 20.4 Root

There MUST be one unambiguous C entry root.

## 20.5 Foreign technologies

A C composite MAY contain a subtree owned by another compiler only if that subtree represents a supported external boundary.

The C compiler MUST NOT generate:

```c
run_someJavascriptNode(context);
```

for an artifact implemented in JavaScript/PHP/Kotlin.

Therefore v1 SHOULD reject direct executable cross-technology calls unless an explicit interoperability/FFI adapter exists.

Compiler delegation itself remains valid; pretending the delegated artifact is callable C does not.

---

# 21. User Code

As with the other template sets, Pebble performs no semantic transformation of user code.

User C source in:

```text
declaration
instantiation
typeDefinition
```

SHALL be inserted verbatim except for required indentation.

The template layer SHALL NOT:

* rewrite C expressions;
* infer types from prose;
* escape arbitrary C code;
* reorder statements inside user snippets.

The existing implementations follow the same principle: templates provide syntactically valid wrappers while long-text source is inserted as supplied.

---

# 22. Generated Example

For:

```text
Pipeline
 ├── Producer
 ├── Consumer
 └── WorkLink
```

the resulting source should conceptually be:

```c
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

/* ---------- runtime types ---------- */

typedef struct threadwork_context {
    /* ... */
} threadwork_context;

/* ---------- modeled types ---------- */

typedef struct WorkOrder {
    int id;
} WorkOrder;

/* ---------- forward declarations ---------- */

static int tw_init_producer(threadwork_context *);
static int tw_run_producer(threadwork_context *);

static int tw_init_consumer(threadwork_context *);
static int tw_run_consumer(threadwork_context *);

static int tw_init_pipeline(threadwork_context *);
static int tw_run_pipeline(threadwork_context *);

/* ---------- implementations ---------- */

static int tw_init_producer(threadwork_context *context)
{
    return THREADWORK_OK;
}

static int tw_run_producer(threadwork_context *context)
{
    WorkOrder order = { .id = 42 };

    threadwork_output_write(
        context,
        "producer.output",
        &order,
        sizeof(order)
    );

    return THREADWORK_OK;
}

static int tw_init_consumer(threadwork_context *context)
{
    return THREADWORK_OK;
}

static int tw_run_consumer(threadwork_context *context)
{
    WorkOrder order;
    size_t size;

    threadwork_input_read(
        context,
        "consumer.input",
        &order,
        sizeof(order),
        &size
    );

    return THREADWORK_OK;
}

static int tw_init_pipeline(threadwork_context *context)
{
    if (tw_init_producer(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    if (tw_init_consumer(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    return THREADWORK_OK;
}

static int tw_run_pipeline(threadwork_context *context)
{
    if (tw_run_producer(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    if (tw_run_consumer(context) != THREADWORK_OK)
        return THREADWORK_ERROR;

    if (threadwork_transport(
            context,
            "WorkLink",
            "producer.output",
            "consumer.input") != THREADWORK_OK)
        return THREADWORK_ERROR;

    return THREADWORK_OK;
}

/* ---------- process entry point ---------- */

int main(void)
{
    threadwork_context context;

    if (threadwork_context_init(&context) != THREADWORK_OK)
        return EXIT_FAILURE;

    int status = tw_init_pipeline(&context);

    if (status == THREADWORK_OK)
        status = tw_run_pipeline(&context);

    threadwork_context_destroy(&context);

    return status == THREADWORK_OK
        ? EXIT_SUCCESS
        : EXIT_FAILURE;
}
```

---

# 23. Required Tests

## Compiler structure

1. terminal processor;
2. simple composite;
3. nested composite;
4. producer → link → consumer;
5. multiple sibling processors;
6. multiple nested links.

## Forward declarations

7. exactly one prototype per generated function;
8. prototypes appear before definitions;
9. prototypes permit references to later-defined functions;
10. nested composite prototypes are recursively hoisted.

## Types

11. type definition appears before every function body;
12. identical type declarations are emitted once;
13. conflicting declarations for the same `typeName` fail;
14. parent-owned type used by descendant works;
15. child-owned type used across composite levels works.

## Symbols

16. duplicate display names;
17. punctuation;
18. names beginning with digits;
19. C keywords;
20. collision after sanitization.

## Assembly

21. exactly one generated `.c`;
22. exactly one `main`;
23. exactly one runtime;
24. no duplicate function definition;
25. no nested functions;
26. valid note output.

## Layout

27. C-owned `single-file` succeeds;
28. C-owned file-based layout fails.

## Technology boundaries

29. foreign subtree delegates to its compiler;
30. unsupported direct C → foreign execution boundary fails explicitly.

## Runtime

31. producer output is transported;
32. consumer receives it on the expected execution step;
33. multiple links remain distinct;
34. context destruction releases queued packets.

---

# 24. Native Compiler Verification

Template rendering success is not sufficient.

Generated C SHALL be compiled during tests using a real C compiler.

Minimum command:

```bash
cc \
    -std=c17 \
    -Wall \
    -Wextra \
    -Werror \
    generated.c \
    -o generated
```

CI SHOULD additionally use:

```bash
-pedantic
```

where generated/user snippets are expected to be strictly portable C17.

The existing guide already correctly requires native compilation because successful Pebble rendering only proves that text was generated, not that the generated text is legal C.

---

# 25. Implementation Sequence

Implement in this order:

```text
1. CCompiler registration
        ↓
2. compiler.properties
        ↓
3. processor + composite templates
        ↓
4. function forward-declaration aggregation
        ↓
5. hoisted link/type declaration aggregation
        ↓
6. assembly-single.peb
        ↓
7. minimal runtime
        ↓
8. main()
        ↓
9. C-specific validation
        ↓
10. generated-source compilation tests
        ↓
11. runtime behavior tests
```

The hoisted-type phase should be implemented before attempting meaningful nested-composite tests.

---

# 26. Explicit Non-Goals for v1

Do not implement yet:

```text
headers
multiple translation units
automatic include dependency analysis
CMake
Makefile generation
shared libraries
dynamic libraries
FFI
cross-language calls
threads
async scheduling
typed generated queue implementations
automatic parsing/reconstruction of arbitrary C declarations
```

Those can be added after the generated C ABI is stable.

---

# 27. Final Compiler Pipeline

The intended pipeline is therefore:

```text
Threadwork graph
       │
       ▼
TemplateSetCompiler
       │
       ├── child-first compilation
       ├── technology delegation
       ├── link compilation
       │
       ▼
C semantic aggregation
       │
       ├── type registry
       ├── hoistedDeclarations
       ├── forwardDeclarations
       └── symbol validation
       │
       ▼
assembly-single.peb
       │
       ├── includes
       ├── runtime
       ├── types
       ├── prototypes
       ├── child definitions
       ├── root definition
       └── main
       │
       ▼
<project>.c
       │
       ▼
cc -std=c17
```

The key architectural point is that **C should not be implemented as a fundamentally different compiler**.

The existing generic compiler already models virtually everything needed:

```text
hierarchy
artifacts
link ownership
runtime support
single-file assembly
symbols
forward declarations
technology delegation
```

The only substantial generic capability I would add is a recursively aggregated **hoisted declaration/prelude** channel for definitions that must precede all executable declarations. That cleanly solves C type placement without overloading the meaning of function forward declarations.
