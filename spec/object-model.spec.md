# Threadwork Object Model Specification

## 1. Universal Entity

`Node` is the document's universal entity. Processors, composites, links, shared
types, and notes use one serializable structure and are distinguished by
`NodeKind` plus optional role-specific data.

```text
NodeKind = Node | Processor | Link | Group | Type | Note
```

Hierarchy is structural: a node with children is composite and a node without
children is terminal. Semantic classification remains independent and may also
consider names, ports, links, and compiler metadata.

## 2. Node Data

A node contains stable identity and hierarchy, text, technology, drawing, and
accountability data:

```text
Node
- id, parentId, name, kind
- children, incomingLinks, outgoingLinks
- layout, fileLayoutStrategyId
- text, technology, metadata, pluginData
- revision, responsible, modified
- ports
- link?              // Link only
- typeDefinition?    // Type only
```

`NodeLayout` persists open and closed composite dimensions and expansion state.
Position changes affect presentation but do not change the node revision.

## 3. Text Sections

`NodeText` stores declaration, instantiation, specification, tests, and usage
instructions. Every section has its own syntax/language identifier. Declaration
and instantiation may inherit their programming language from ancestors; the
other sections default to their content formats.

Historical JSON field names remain mapped by serialization, but application and
compiler code use declaration and instantiation terminology.

## 4. Ports and Data Links

A processor-like node exposes named input and output `NodePort` entries. A data
link connects one output port to one input port. Links cannot use another Link or
a Type declaration as a normal endpoint.

```text
LinkData
- sourceNodeId, sourcePortName
- targetNodeId, targetPortName
- interactionKind
- transportKind
- typeDefinitionId
- compositeBoundaryIds
```

The link node name is the wire/buffer variable name. `typeDefinitionId` is either
a built-in type identifier or the stable ID of a Type node. Legacy `typeName` and
`payloadDefinition` data may be read by existing compilers, but new designs use
shared Type entities.

`interactionKind` is independent from the physical `transportKind`. Its values
are `auto`, `data`, `lib`, `src`, and `run`. `data` links carry unidirectional
packets. The other three provide a library instance, synchronous source builder,
or synchronous runnable builder from source endpoint to target endpoint and do
not allocate transport buffers. `auto` exists for files predating this field and
retains historical classifier behavior.

## 5. Link Ownership and Composite Boundaries

A link is owned by the closest common ancestor of its two endpoint nodes. The
repository computes this parent when the link is created and recomputes it when
an endpoint is reparented.

`compositeBoundaryIds` lists, in source-to-target order, every composite boundary
that the direct link crosses. The canvas renders one continuous route, marks each
boundary crossing, and does not treat those listed composites as routing
obstacles. Persisted boundary data is normalized when a document is loaded.

## 6. Shared Type Declarations

A Type node defines an ordered list of fields:

```text
TypeDefinition
- fields[]
  - name
  - typeId
  - isReference
```

Built-in type IDs are `string`, `number`, `date`, and `array`. `typeId` may also
refer to another Type node, allowing a project-owned object model. `isReference`
states that a custom field refers to another value rather than embedding it;
the exact representation is compiler-specific.

Type names and fields are edited in the Entities Edit view. Type boxes list the
links that use them. In Link mode, selecting a Type and then an existing Link
assigns the Type to that link without creating another graph edge.

The validator rejects blank or duplicate fields, unknown field types, unknown
link types, and Type nodes without a `TypeDefinition`.

## 7. Link Type Presentation

The canvas labels both ends of a typed link as:

```text
<link-name>:<type-name>
```

Hovering the route shows the selected Type and its fields. Editors at both
endpoint processors receive the link name and resolved Type through completion
context, so code can refer to the same contract without copying declarations.

## 8. Document Root

`ThreadworkDocument` owns a stable node map and identifies one node as its root.
The root is also the project entity: its node name is the effective project name,
and its technology, revision, responsibility, and layout defaults may be
inherited by descendants.

All mutation must go through `DocumentRepository`. Repository operations maintain
parent/child symmetry, endpoint link lists, link ownership, boundary traversal,
modification metadata, and revision propagation before persistence.
