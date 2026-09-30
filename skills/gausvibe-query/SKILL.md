---
name: gausvibe-query
description: Use the GausVibe code-graph server to answer structural questions about the current Java codebase - where a class or method lives, what implements or subclasses a type, who calls a method, what methods or fields a class has. Ask GausVibe in plain language before grepping or reading files.
user-invocable: false
---

# GausVibe Codebase Q&A

Prefer GausVibe over grep/find/read_file for structural questions about the
indexed Java project. Ask the real question ("where is HttpTransportSettings
defined"), not a reformulated keyword.

## Tools (call via run_typescript as tools.mcp_gausvibe.<name>)

- `ask({question})` - 'where is X defined', 'what methods/fields does X
  have', 'what implements X' / 'what subclasses X', 'who calls M',
  'who uses F' (field), 'which tests verify C', 'where is <literal> defined'
- `search({query, limit})` - class/method name lookup
- `classes({package|prefix, limit})` - list classes in a package (no
  unfiltered listing)
- `packages({})` - list indexed packages
- `class_detail({fqn})` - one class: file, methods, fields
- `class_members({class_fqn, relation})` - methods/fields/subclasses/
  implementations of a class
- `tests({class_fqn})` - tests covering a class, with method-level coverage
- `callpath({from, to, depth})` - transitive call chains between methods
- `edited({path, kind, target})` - report EVERY Java file edit, one call per
  file, right after the edit; keeps answers accurate
- `feedback({rating, question, matched, comment})` - rate notably
  helpful/wrong/incomplete/too-big answers
- `changes({})` / `stats({})` - recent edits / graph health and staleness

Arg schemas and vocabularies (edit `kind` values, rating values) are in the
tool descriptors above; call the tools directly, no discovery needed.

## Reading answers

The response reports the matched query type. `matched: null` means GausVibe
could not answer that question - fall back to grep for it; do not retry the
same question.

## Daemon

If tools report the server unreachable, the graph daemon is down: start it
per "Running the daemon" in the gausvibe repo RUNBOOK.md, then retry.
Questions are logged server-side; asking costs nothing extra.
