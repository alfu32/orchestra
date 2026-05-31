# Repository Guidelines

## Project Structure & Module Organization

This repository currently contains product and architecture specifications for Orchestra. Source code has not been added yet.

- `README.md`: project entry point; currently empty.
- `spec/`: Markdown specifications that define system behavior and architecture.
- `spec/system.spec.md`: system-level behavior and model description.
- `spec/object-model.spec.md`: single-node object model details.
- `spec/techincal.spec.md` and `spec/technical.addendum.spec.md`: technical notes and addenda. Preserve existing filenames unless a coordinated rename is requested.

When adding implementation code, keep it outside `spec/` and document the new layout here.

## Build, Test, and Development Commands

No build system, package manager, or test runner is configured yet. Useful repository checks are:

- `rg --files`: list tracked working files quickly.
- `git status --short`: inspect local changes before committing.
- `sed -n '1,120p' spec/system.spec.md`: preview a spec section from the terminal.

If a runtime stack is introduced, add its canonical commands here, for example `npm test`, `cargo test`, or `make build`.

## Coding Style & Naming Conventions

Current files are Markdown. Use concise headings, short paragraphs, and fenced code blocks for schemas or examples. Prefer ASCII punctuation and plain technical language. Keep spec filenames lowercase and descriptive, using `.spec.md` for primary specifications and `.addendum.spec.md` for supplementary material.

For future code, follow the formatter and naming conventions of the chosen language, and commit the formatter configuration with the code.

## Testing Guidelines

There are no automated tests yet. For specification changes, verify consistency manually across related files in `spec/`, especially duplicated model concepts such as `Node`, `children`, `metadata`, ports, and link data.

When implementation begins, place tests near the relevant source or in a clearly named `test/` or `tests/` directory. Use names that describe behavior, such as `node_serialization_test` or `NodeLayoutSpec`.

## Commit & Pull Request Guidelines

This repository has no commit history yet, so use a simple imperative style for commits:

- `Add object model addendum`
- `Clarify node link semantics`
- `Document initial test strategy`

Pull requests should include a short summary, the affected spec files, and any unresolved design questions. Link related issues when available. For UI or generated-output changes added later, include screenshots or before/after examples.

## Agent-Specific Instructions

Keep edits scoped and preserve user-authored content. Do not rename files, reorganize specs, or introduce tooling unless the task explicitly requires it.
