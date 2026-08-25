# C17 Template Set

This template set generates one portable C17 translation unit. `processor.peb` and
`composite.peb` define setup/run functions, while their forward-declaration templates
place prototypes before all generated bodies. Link payload definitions are collected by
`link-declaration.peb` into the translation-unit type prelude; link instantiations retain
the modeled link identity and endpoint references when invoking the byte-copying runtime.

`assembly-single.peb` owns includes, declaration ordering, and the sole `main` function.
The runtime copies bytes written by processors, moves runtime-owned packets between named
queues, and frees all remaining allocations when the context is destroyed. C-owned nodes
must use `single-file`; unsupported layouts and conflicting wire definitions are rejected
before rendering.
