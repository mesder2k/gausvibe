# Editing Implementation Plan: Project Modification for GausVibe

---

## Current State
✅ Basic AST editing framework exists
✅ Add/remove methods, fields, imports
✅ Change tracking
✅ CLI edit command
❌ Incomplete serialization (can't write full files back)
❌ No class-level or statement-level operations

---

## Goal
Enable **complete project modification** through structured AST operations, with the ability to write changes back to source files.

---

## Phases

### Phase 1: Core Completion (Week 1)
**Priority: High** | **Effort: 3-4 days**

**Deliverables:**
- `ASTSourceSerializer` - handle all node types (imports, statements, expressions)
- `AddClassOperation` / `RemoveClassOperation`
- `ModifyMethodSignatureOperation` (with call-site updates)
- Basic validation (type checking, syntax)

**Outcome:** Can create classes, modify methods, write changes to files

---

### Phase 2: Extended Operations (Week 2)
**Priority: High** | **Effort: 4-5 days**

**Deliverables:**
- Statement operations (add/remove/move: if, for, while, return)
- `ModifyClassOperation` (change superclass, interfaces, modifiers)
- Enhanced validation (semantic rules, visibility)
- Diff generation

**Outcome:** Full method and class modification capability

---

### Phase 3: Advanced Features (Week 3-4)
**Priority: Medium** | **Effort: 5-7 days**

**Deliverables:**
- Refactoring operations (rename method/field with references)
- Undo/redo support
- Natural language parser for edit requests
- Batch operations with atomic apply

**Outcome:** Production-ready editing with safety features

---

### Phase 4: Polish (Week 5)
**Priority: Low** | **Effort: 2-3 days**

**Deliverables:**
- Interactive edit mode in CLI
- Test generation for new code
- VCS integration (git diff, commit)
- Performance optimization

**Outcome:** Fully integrated editing experience

---

## Timeline

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| 1 | 3-4 days | Serialization fix, class ops, validation |
| 2 | 4-5 days | Statement ops, class modification, diff |
| 3 | 5-7 days | Refactoring, undo/redo, NLP parsing |
| 4 | 2-3 days | Interactive mode, tests, VCS |

**Total: ~3-4 weeks** for full implementation

---

## Starting Point

Start with **Phase 1:**
1. Fix `ASTSourceSerializer` (blocks all other progress)
2. Add `AddClassOperation`
3. Add basic validation

This gives a working foundation for all subsequent features.
