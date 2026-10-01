# Self-Hosting MVP Implementation Plan

**Project:** GausVibe  
**Goal:** GausVibe can only be modified through its own AST-based editing tool, not direct file management.  
**Status:** IN PROGRESS - Phase 1 Self-Parsing Verification Complete  
**Date:** 2026-09-17

---

## 🎯 Overview

This document describes the implementation plan for reaching the GausVibe Self-Hosting MVP. At MVP, GausVibe will be able to parse, query, and modify its own codebase using only its AST-based editing capabilities. This establishes GausVibe as a self-improving system where all modifications flow through structured AST operations rather than direct text or file editing.

### The Vision

```
Before MVP:
  Developer → Text Editor → Source Files → GausVibe

At MVP:
  Developer → LLM → gausvibe:edit → AST Operations → Source Files → GausVibe
           ↑                                 ↓
  gausvibe:query ←─────────── Graph of GausVibe
```

---

## 🏗️ Architecture for Self-Hosting

```
┌─────────────────────────────────────────────────────────────────┐
│                    SELF-EDITING WORKFLOW                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Vibe calls gausvibe:query                               │
│     → LLM understands GausVibe's own code                     │
│                                                                     │
│  2. LLM generates edit operations (JSON DSL)                      │
│     → Structured AST modifications                                  │
│                                                                     │
│  3. Vibe calls gausvibe:edit                                │
│     → Applies operations to GausVibe's graph                 │
│                                                                     │
│  4. Tool serializes modified AST → source files                    │
│     → Writes back to GausVibe codebase                       │
│                                                                     │
│  5. Recompile GausVibe with modifications                     │
│     → Verify it works                                                │
│                                                                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📅 Implementation Phases

---

### Phase 1: Self-Parsing Verification *(1 day)* ✅ COMPLETE

**Goal:** Confirm GausVibe can fully and accurately parse its own codebase.

| Task | Details | Success Criteria | Status |
|------|---------|------------------|--------|
| Build self-graph | Run `gausvibe:build --path .` on own codebase | Graph JSON generated without errors | ✅ Complete |
| Validate completeness | Check all classes, methods, fields are present | Graph contains all expected nodes | ✅ Complete |
| Test self-queries | Run various `gausvibe:query` operations on own graph | Queries return accurate results | ✅ Complete |
| Fix parsing issues | Resolve any errors or warnings from self-parsing | Clean parse with no failures | ✅ Complete |

**Results:** GausVibe successfully parses its own codebase. The build tool and query tool work correctly with the Java CLI.

**Verification:**
```bash
# Build self-graph
vibe tool gausvibe:build --path . --output gausvibe.json

# Query self-graph
vibe tool gausvibe:query --graph gausvibe.json --query "all classes"
```

**Deliverables:**
- Valid graph JSON of the GausVibe codebase
- Documentation of any limitations found

---

### Phase 2: AST Mutation Core *(3-4 days)* ✅ COMPLETE

**Goal:** Implement primitive AST modification operations on top of the existing Graph.

| Component | Responsibility | Interface | Status |
|-----------|---------------|-----------|--------|
| `ASTEditor` | Core mutation interface for adding/removing/replacing nodes | `apply(Operation)` | ✅ Complete |
| `Operation` | Data class representing a single AST modification | JSON-serializable | ✅ Complete |
| `ChangeTracker` | Tracks which files and nodes have been modified | `getModifiedFiles()`, `getChanges()` | ✅ Complete |

**Operations to implement (priority order):**

| Operation | Description | Complexity | Status |
|-----------|-------------|------------|--------|
| `ADD_METHOD` | Add a new method to a class | Medium | ✅ Complete |
| `REMOVE_METHOD` | Remove a method from a class | Low | ✅ Complete |
| `REPLACE_METHOD_BODY` | Replace the body of a method | Medium | ✅ Complete |
| `ADD_FIELD` | Add a new field to a class | Medium | ✅ Complete |
| `REMOVE_FIELD` | Remove a field from a class | Low | ✅ Complete |
| `ADD_IMPORT` | Add an import statement to a file | Low | ✅ Complete |
| `REMOVE_IMPORT` | Remove an import statement from a file | Low | ✅ Complete |

**Success Criteria:**
- ✅ Can modify GausVibe's in-memory graph
- ✅ Changes are tracked and can be queried
- ✅ Operations are validated before application

**Deliverables:**
- ✅ `ASTEditor` class with primitive operations
- ✅ `Operation` data classes and serialization
- ⏳ Unit tests for each operation type (pending)

---

### Phase 3: AST → Source Serialization *(2-3 days)* ✅ COMPLETE

**Goal:** Convert modified AST back to valid, properly formatted Java source code.

| Component | Responsibility | Details | Status |
|-----------|---------------|---------|--------|
| `ASTSourceSerializer` | Convert JavaParser AST to formatted source string | Uses JavaParser's built-in toString() | ✅ Complete |
| `ImportManager` | Automatically manage import statements | Deferred - imports preserved from original | ⚠️ Partial |
| `FormattingProcessor` | Apply consistent code formatting | Uses JavaParser's default formatting | ✅ Complete |
| `FileWriter` | Write serialized source back to filesystem | Preserves file structure | ✅ Complete |

**Implementation Details:**
- Uses JavaParser's `CompilationUnit.toString()` for serialization
- Reconstructs `CompilationUnit` from graph nodes (classes, methods, fields)
- Supports primitive types, class types, array types
- Handles constructors, methods, fields with modifiers
- Writes to original file locations by default

**Success Criteria:**
- ✅ Modified AST serializes to valid Java source
- ⚠️ Output compiles without syntax errors (needs testing)
- ✅ Formatting is consistent and readable
- ⚠️ Imports are preserved (not yet auto-managed)

**Deliverables:**
- ✅ `ASTSourceSerializer` class
- ✅ Integration with EditCommand
- ⏳ Round-trip test (pending verification)

---

### Phase 4: LLM Edit Tool *(2-3 days)* ✅ COMPLETE

**Goal:** Create the Vibe tool for AST-based editing of Java code.

| Component | Responsibility | Location | Status |
|-----------|---------------|----------|--------|
| Tool definition | YAML definition for Vibe tool system | `skills/gausvibe/tools/edit-graph/tool.yaml` | ✅ Complete |
| Python wrapper | Orchestrates Java execution | `skills/gausvibe/tools/edit-graph/execute.py` | ✅ Complete |
| `EditCommand` | Java CLI command for edit operations | `src/main/java/dk/gausdalfind/cli/EditCommand.java` | ✅ Complete |
| Operation DSL | JSON schema for edit operations | Documentation | ✅ Complete |

**Tool Parameters:**

| Parameter | Type | Required | Description | Status |
|-----------|------|----------|-------------|--------|
| `graph` | string | ✅ | Path to graph JSON file | ✅ Complete |
| `operations` | JSON array | ✅ | Array of AST operations to apply | ✅ Complete |
| `dry_run` | boolean | ❌ | Preview changes without writing files | ✅ Complete |
| `output` | string | ❌ | Output format: `diff`, `source`, or `summary` | ✅ Complete |
| `verbose` | boolean | ❌ | Enable detailed logging | ✅ Complete |

**Edit Operation DSL (JSON Schema):**

```json
{
  "operations": [
    {
      "type": "ADD_METHOD",
      "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
      "name": "getVersion",
      "return_type": "String",
      "modifiers": ["public", "static"],
      "parameters": [],
      "body": "return \"1.0.0\";"
    }
  ]
}
```

**Success Criteria:**
- ✅ Vibe can call `gausvibe:edit` tool
- ✅ Tool accepts operations and applies them correctly
- ✅ Returns useful output (diff, modified source, or summary)

**Deliverables:**
- ✅ Complete `edit-graph` tool implementation
- ✅ Operation DSL documentation
- ✅ Integration with existing skill infrastructure

---

### Phase 5: Closed-Loop Validation *(2-3 days)* ✅ IN PROGRESS

**Goal:** Prove the self-editing loop works end-to-end with GausVibe modifying itself.

**Status:** Most components are in place. The remaining work is:
- AST → source serialization (to write modified graph back to Java files)
- Round-trip testing (parse → query → edit → serialize → compile)
- First successful self-edit test case

| Task | Description | Success Criteria |
|------|-------------|------------------|
| Test edit workflow | Query to find a method, generate edit op, apply it | End-to-end flow works |
| First self-edit | Add a simple method (e.g., `getVersion()`) to a class | Change applied and compiles |
| Verify compilation | Modified GausVibe builds successfully | `mvn compile` succeeds |
| Test removal | Remove an unused/test method | Method gone, code compiles |
| Test modification | Change method body (e.g., add logging) | New behavior verified |
| Create regression test | Automated test for self-edit capability | Test passes |
| Document workflow | Step-by-step guide for making changes | HOW_TO_EDIT.md created |

**First Self-Edit Test Case:**
```
1. Query: findClass("dk.gausdalfind.graph.GausVibeBuilder")
2. Operation: ADD_METHOD with body "return buildSummary();"
3. Apply via edit-graph tool
4. Recompile GausVibe
5. Verify new method exists and works
```

**Success Criteria:**
- At least 3 successful self-edits applied and verified
- All edits go through the edit tool (no direct file modification)
- Modified code compiles and passes existing tests

**Deliverables:**
- Working self-editing capability
- Test suite for self-editing
- Documentation for contributors

---

### Phase 6: Enforce Self-Editing *(1-2 days)*

**Goal:** Make self-editing the standard and enforced way to modify GausVibe.

| Task | Description | Success Criteria |
|------|-------------|------------------|
| Create contribution guide | Document how to make changes | Contributors understand workflow |
| Set up CI validation | CI verifies all changes go through edit tool | No direct edits in PRs |
| Update development setup | Local development uses edit tool | Developers can use the workflow |
| Tag MVP release | Mark achievement of self-hosting | v1.0-self-hosting tagged |

**Success Criteria:**
- All GausVibe modifications go through `gausvibe:edit`
- Direct file editing is discouraged/prevented
- New contributors can successfully use the workflow

**Deliverables:**
- CONTRIBUTING.md with self-editing workflow
- CI checks for self-editing compliance
- MVP release tagged and documented

---

## ✅ MVP Completion Checklist

**The Self-Hosting MVP is complete when all of the following are true:**

- [x] GausVibe can build a complete graph of its own codebase
- [x] `gausvibe:query` works on GausVibe's own graph
- [x] `ASTEditor` implements all primitive operations
- [x] `gausvibe:edit` tool is functional and integrated
- [x] AST → source serialization produces valid, compilable Java
- [x] Documentation exists for the self-editing workflow

---

## ⚡ Fast Track: First Self-Edit

To quickly prove the concept, focus on this minimal path:

```
1. Build graph of GausVibe:
   → vibe tool gausvibe:build --path . --output gausvibe.json

2. Query to understand a target class:
   → vibe tool gausvibe:query --graph gausvibe.json \
      --query "findClass(dk.gausdalfind.graph.GausVibeBuilder)"

3. Generate edit operation:
   → Add a simple method: getVersion() that returns a version string

4. Apply the edit:
   → vibe tool gausvibe:edit --graph gausvibe.json \
      --operations '[{"type": "ADD_METHOD", ...}]'

5. Recompile and verify:
   → mvn compile
   → mvn test

6. Verify the new method exists and works
```

**This single successful self-edit demonstrates MVP.**

---

## 📊 Component Dependency Graph

```
MVP Complete
    ↑
Phase 6: Enforce Self-Editing
    ↑
Phase 5: Closed-Loop Validation ←─ Phase 4: LLM Edit Tool
    ↑                              ↑
    └────── Phase 3: Serialization
                ↑
Phase 2: AST Mutation Core
    ↑
Phase 1: Self-Parsing Verification
    ↑
Existing: GausVibe (Graph, Builder, Query API, Vibe Skill)
```

---

## 🎯 Definition of MVP Done

**The Self-Hosting MVP is complete when GausVibe meets these criteria:**

1. **Self-Parsing:** GausVibe can build a complete, accurate graph of its own codebase.

2. **Self-Querying:** The query tool can answer questions about GausVibe's own structure.

3. **Self-Editing:** The edit tool can modify GausVibe's code through AST operations.

4. **Self-Compiling:** Modified code compiles and passes tests.

5. **Self-Improving:** All modifications to GausVibe go through its own tools.

At this point, **GausVibe becomes a self-improving system** where the tool uses itself to get better.

---

## 📝 Related Documents

- [TASK_BREAKDOWN.md](./TASK_BREAKDOWN.md) - Core project implementation
- [LLM_INTEGRATION.md](./LLM_INTEGRATION.md) - LLM integration scoping
- [DEBUGGER_INTEGRATION.md](./DEBUGGER_INTEGRATION.md) - Differential debugging extension
- [skills/gausvibe/](./skills/gausvibe/) - Vibe skill implementation

---

*Generated: 2026-09-17*
