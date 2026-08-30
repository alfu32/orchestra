# C17 Template Set

This template set generates one portable C17 translation unit. `processor.peb` and
`composite.peb` define setup/run functions, while their forward-declaration templates
place prototypes before all generated bodies. `type-declaration.peb` maps shared Type
entities to structs. Built-in arrays use `ThreadworkArray` from the runtime; reference
fields become pointers to custom struct tags.

Every data link owns two `threadwork_buffer` values sized from the resolved Type and a
named transport function. Generated processors receive incoming B buffers and outgoing A
buffers. Composite run functions execute children, then move at most one packet per link
from A to B. Library, source, and runnable capability links are excluded from this
transport contract. Library links retain single-instance dependency injection. Source
links generate a typed facade whose `getSource` function synchronously interpolates
consumer-provided `threadwork_build_parameter` values into the provider declaration.
The C17 compiler rejects `run` links because a type-safe runtime compiler or explicit
toolchain adapter is required for a runnable product.

`assembly-single.peb` owns includes, declaration ordering, and the sole `main` function.
The runtime copies bytes written by processors, moves runtime-owned packets between named
buffers, and frees all remaining allocations when the context is destroyed. C-owned nodes
must use `single-file`; unsupported layouts and conflicting wire definitions are rejected
before rendering.
