# Single-Node Object Model Specification

## 1. Core Principle

The entire document model is built from one object type:

```text
Node
```

Every object in the application is a node.

A node may behave as:

```text
- a composite
- a terminal source-code unit
- a link
- a visual container
- a workflow step
- a generated-project unit
```

But these are roles, not separate structural classes.

The node object should be flexible enough to represent all of them.

---

## 2. Node Structure

A node contains:

```text
Node
- id
- parent_id
- name
- kind
- metadata
- children
- source_text
- specification_text
- tests_text
- ai_instruction_text
- x
- y
- width
- height
- link_data
- ports
```

Where:

```text
children
```

may be empty.

If `children.length == 0`, the node is terminal from the hierarchy point of view.

If `children.length > 0`, the node is composite from the hierarchy point of view.

This does not necessarily prevent a node with children from also having source text.

---

## 3. Terminal vs Composite

A node is terminal when:

```text
children.length == 0
```

A node is composite when:

```text
children.length > 0
```

This is a structural distinction.

It is independent from semantic kind.

For example, the following are all allowed by the model:

```text
- terminal processor node
- composite processor node
- terminal link node
- composite node with source code
- node with no source code and no children
```

The compiler decides what is meaningful.

---

## 4. Four Text Fields

Each node has four main text fields:

```text
source_text
specification_text
tests_text
ai_instruction_text
```

### 4.1 Source Text

The implementation body, source code, script, generated template, or manual procedure body.

### 4.2 Specification Text

The human-readable or machine-readable description of what the node is supposed to do.

### 4.3 Tests Text

Tests, validation rules, expected examples, manual test cases, or generated test templates.

### 4.4 AI Instruction Text

Instructions intended for AI coding agents or other AI-based generation tools.

This field should be used to tell an AI agent how to implement, modify, test, or reason about the node.

---

## 5. Canvas Layout Data

Each node stores its own two-dimensional canvas placement:

```text
x
y
width
height
```

This means graphical layout is part of the document model.

That is acceptable and useful because the model is not merely abstract code; it is also an editable visual artifact.

The GUI can render the node directly using these fields.

Example:

```text
Node visual rectangle:
- x: 120
- y: 80
- width: 240
- height: 140
```

---

## 6. Node Kind

The `kind` field describes the intended semantic role of the node.

Recommended initial values:

```text
NodeKind
- node
- processor
- link
- group
- note
```

For the strict MVP, this can be reduced to:

```text
NodeKind
- processor
- link
- group
```

However, the core model should not become too dependent on this enum.

A compiler may use `kind`, metadata, children, ports, or links to decide what the node means.

---

## 7. Link Representation Inside a Node

Since links are also nodes, a link node should store its link-specific data inside optional fields.

```text
link_data
- source_node_id
- source_port_name
- target_node_id
- target_port_name
```

A link node still has:

```text
- children
- text fields
- metadata
- canvas coordinates
```

Even if most compilers ignore some of those fields for links.

This keeps the model uniform.

---

## 8. Ports

A processor-like node may define ports.

```text
ports
- name
- direction
- data_type
- metadata
```

Where direction is:

```text
- input
- output
```

A link connects one output port to one input port.

---

## 9. Metadata

Metadata remains generic.

```text
metadata
- programming_language
- technology
- compiler
- runtime
- user_properties
```

This lets the same node be interpreted differently by different compilers.

---

## 10. Important Architectural Decision

The node class is not just a data structure for source code.

It is the universal document atom.

It carries:

```text
- hierarchy
- implementation
- specification
- tests
- AI instructions
- visual position
- compiler metadata
- communication information
```

This is the heart of the application.

---

## 11. Consequence for the Rewrite

The rewrite should not begin by designing many model classes.

The first coding-agent task should be:

```text
Implement a robust single Node model and repository around it.
```

The model can later expose helper functions such as:

```text
is_terminal(node)
is_composite(node)
is_link(node)
is_processor(node)
get_child_nodes(node)
get_link_nodes(node)
get_processor_nodes(node)
```

But these should be derived interpretations, not separate base classes.

---

## 12. Recommended Core API

The core model package should expose operations like:

```text
create_node(parent_id, name)
delete_node(node_id)
move_node(node_id, new_parent_id)
rename_node(node_id, new_name)

set_node_text(node_id, text_kind, value)
get_node_text(node_id, text_kind)

set_node_layout(node_id, x, y, width, height)
get_node_layout(node_id)

add_child(parent_id, child_id)
remove_child(parent_id, child_id)

set_node_kind(node_id, kind)

set_link_endpoints(
    link_node_id,
    source_node_id,
    source_port_name,
    target_node_id,
    target_port_name
)
```

---

## 13. Compiler Interpretation

The compiler should receive the same generic node structure.

It should classify nodes by interpretation:

```text
if node.kind == "link":
    compile as link

else if node.children.length > 0:
    compile as composite

else:
    compile as terminal processor
```

This is a simple default rule.

More advanced compilers may override it.

---

## 14. Minimal JSON Shape

A practical serialized node may look like:

```json
{
  "id": "node_001",
  "parent_id": null,
  "name": "Root Application",
  "kind": "group",
  "metadata": {
    "programming_language": null,
    "technology": null,
    "compiler": null,
    "runtime": null,
    "user_properties": {}
  },
  "text": {
    "source": "",
    "specification": "",
    "tests": "",
    "ai_instructions": ""
  },
  "layout": {
    "x": 0,
    "y": 0,
    "width": 240,
    "height": 120
  },
  "ports": [],
  "link": null,
  "children": []
}
```

A link node:

```json
{
  "id": "link_001",
  "parent_id": "root",
  "name": "A.output -> B.input",
  "kind": "link",
  "metadata": {},
  "text": {
    "source": "",
    "specification": "Move packets from A.output to B.input.",
    "tests": "",
    "ai_instructions": ""
  },
  "layout": {
    "x": 300,
    "y": 160,
    "width": 120,
    "height": 40
  },
  "ports": [],
  "link": {
    "source_node_id": "node_a",
    "source_port_name": "output",
    "target_node_id": "node_b",
    "target_port_name": "input"
  },
  "children": []
}
```

---

## 15. Updated System Definition

The application is based on a single recursive node class. Every node may contain children, four editable text fields, metadata, link information, ports, and two-dimensional canvas layout. Terminal nodes are simply nodes without children. Composite nodes are nodes with children. Link nodes are ordinary nodes whose metadata describes a connection between one output and one input. The GUI edits and visualizes this node graph, while compiler plugins interpret the same node hierarchy into source-code projects, executables, documentation, AI-agent tasks, tests, or human workflows.
