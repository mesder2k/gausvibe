# Token Savings Implementation Checklist

## 🎯 **Objective**
Implement optimizations that **save LLM tokens** by replacing verbose shell operations with concise, structured GausVibe queries.

**Target**: 85-99% token reduction for common operations

---

## 📋 **PHASE 1: Quick Token Wins (3-5 days)**

### ✅ **Already Implemented**
- [x] File System Cache - Avoids repeated `find` commands

### 🎯 **Token-Saving Features to Implement**

#### **1. Compact Query Responses (Priority: ⭐⭐⭐⭐⭐)**
**Token Savings**: 80-90%
**Effort**: 1-2 days

- [ ] Add `compactMode` flag to `GraphQueryEngine` (default: true)
- [ ] Modify all `format*()` methods to have compact versions
  - [ ] `formatClass()` → Return only FQN in compact mode
  - [ ] `formatClassList()` → Return FQNs, one per line
  - [ ] `formatMethod()` → Return signature only
  - [ ] `formatMethodList()` → Return signatures, one per line
  - [ ] `formatField()` → Return name:type
  - [ ] `formatFieldList()` → Return name:type, one per line
- [ ] Add `--compact` and `--verbose` CLI flags
- [ ] Update `CommandLineInterface` to support compact mode

**Test Cases**:
- [ ] Verify compact mode returns minimal data
- [ ] Verify verbose mode still works
- [ ] Verify token count reduction (target: 80-90%)

---

#### **2. Smart Result Limiting (Priority: ⭐⭐⭐⭐⭐)**
**Token Savings**: 95-99%
**Effort**: 1 day

- [ ] Add `defaultLimit` to `GraphQueryEngine` (default: 10)
- [ ] Add `--limit N` parameter to all list queries
- [ ] Add `--show-all` flag to disable limiting
- [ ] Modify query methods to support limits:
  - [ ] `findClassesByName(String name, int limit)`
  - [ ] `findMethodsByName(String name, int limit)`
  - [ ] `getAllClasses(int limit)`
  - [ ] `getAllMethods(int limit)`
  - [ ] `getNodesByType(String type, int limit)`
- [ ] Add "... and N more" message when results are truncated
- [ ] Update CLI to default to limit=10

**Test Cases**:
- [ ] Verify default limit is 10
- [ ] Verify `--limit 20` works
- [ ] Verify `--show-all` returns all results
- [ ] Verify truncation message appears
- [ ] Verify token count reduction (target: 95-99%)

---

#### **3. Minimal Call Graph Queries (Priority: ⭐⭐⭐⭐)**
**Token Savings**: 90-95%
**Effort**: 1 day

- [ ] Add minimal versions of call graph queries:
  - [ ] `getCallersMinimal(MethodNode)` → Returns only FQNs, one per line
  - [ ] `getCalleesMinimal(MethodNode)` → Returns only FQNs, one per line
  - [ ] `getOverriddenMethodMinimal(MethodNode)` → Returns only FQN
  - [ ] `getOverridingMethodsMinimal(MethodNode)` → Returns only FQNs, one per line
- [ ] Add CLI support for minimal call graph queries

**CLI Examples**:
```
gausvibe> query:method:com.example.Calculator#calculateTotal:callers
com.example.OrderService#processOrder
com.example.CalculatorTest#testCalculateTotal
```

**Test Cases**:
- [ ] Verify callers query returns only FQNs
- [ ] Verify one result per line
- [ ] Verify token count reduction (target: 90-95%)

---

## 📋 **PHASE 2: High-Impact Token Savings (1 week)**

#### **4. Text Search Index (Minimal Output) (Priority: ⭐⭐⭐⭐⭐)**
**Token Savings**: 95%+
**Effort**: 2 days

- [ ] Implement `TextSearchIndex` class
- [ ] Add `build()` method to index all nodes
- [ ] Add `searchMinimal(String query)` → Returns only FQNs
- [ ] Add `searchMinimal(String query, int limit)` → With limit
- [ ] Integrate with `GraphQueryEngine`
- [ ] Add CLI command: `query:search:text:QUERY`
- [ ] Add `--limit` parameter support

**CLI Examples**:
```
gausvibe> query:search:text:calculateTotal
com.example.Calculator#calculateTotal
com.example.OrderService#processOrder
```

**Test Cases**:
- [ ] Verify text search returns only FQNs
- [ ] Verify limit parameter works
- [ ] Verify token count reduction (target: 95%+)

---

#### **5. Structured File Content Queries (Priority: ⭐⭐⭐)**
**Token Savings**: 80-90%
**Effort**: 1 day

- [ ] Add `getFileStructured(Path file)` method
- [ ] Add `getFileStructured(Path file, String focus)` method
  - Focus options: `all`, `classes`, `methods`, `fields`, `imports`
- [ ] Return only structure, not full file content
- [ ] Add CLI command: `query:file:PATH [--focus FOCUS]`

**CLI Examples**:
```
gausvibe> query:file:src/main/java/Calculator.java
Class: com.example.Calculator
  Methods: calculateTotal(), add(), subtract()
  Fields: value

# Or focused
gausvibe> query:file:src/main/java/Calculator.java --focus methods
calculateTotal()
add(double, double)
subtract(double, double)
```

**Test Cases**:
- [ ] Verify structured output doesn't include method bodies
- [ ] Verify focus parameter works
- [ ] Verify token count reduction (target: 80-90%)

---

## 📋 **PHASE 3: Advanced Token Savings (1-2 weeks)**

#### **6. Paginated Queries (Priority: ⭐⭐⭐⭐)**
**Token Savings**: 98%+
**Effort**: 2 days

- [ ] Create `PaginatedResult<T>` class
- [ ] Add pagination support to query methods:
  - [ ] `findAllClassesPaginated(int limit, String cursor)`
  - [ ] `findClassesByNamePaginated(String name, int limit, String cursor)`
  - [ ] `findMethodsByNamePaginated(String name, int limit, String cursor)`
- [ ] Add CLI support for pagination:
  - [ ] `--limit N` parameter
  - [ ] `--cursor CURSOR` parameter
  - [ ] `--next-page` helper command
- [ ] Add "Next page: ..." hint to output

**CLI Examples**:
```
gausvibe> query:class:all --limit 10
Class: com.example.Calculator
Class: com.example.OrderService
... (8 more)
---
Next page: query:class:all --limit 10 --cursor eyJpIjoiMTAi

# Next page
gausvibe> query:class:all --limit 10 --cursor eyJpIjoiMTAi
Class: com.example.UserService
... (9 more)
```

**Test Cases**:
- [ ] Verify pagination returns correct page
- [ ] Verify cursor works for next page
- [ ] Verify token count for first page (target: 98% reduction)

---

#### **7. Diff Queries (Priority: ⭐⭐⭐)**
**Token Savings**: 90%+
**Effort**: 2 days

- [ ] Add file timestamp tracking
- [ ] Add `getChangesSince(Path file, long timestamp)` method
- [ ] Return structured diff (added, removed, modified)
- [ ] Add CLI command: `query:diff:FILE [--since TIMESTAMP]`
- [ ] Add `--since` parameter support

**CLI Examples**:
```
gausvibe> query:diff:src/main/java/Calculator.java --since 2026-09-20T10:00:00Z
Added:
  + com.example.Calculator#newMethod

Modified:
  ~ com.example.Calculator#calculateTotal
```

**Test Cases**:
- [ ] Verify diff returns only changes
- [ ] Verify timestamp parameter works
- [ ] Verify token count reduction (target: 90%+)

---

#### **8. Query Result Caching (Priority: ⭐⭐⭐⭐)**
**Token Savings**: 100% (reuses previous results)
**Effort**: 1 day

- [ ] Add query result cache to `GraphQueryEngine`
- [ ] Cache key: query type + parameters hash
- [ ] Cache value: result + timestamp
- [ ] Add TTL for cache entries (default: 5 minutes)
- [ ] Add cache statistics
- [ ] Add `--no-cache` flag for fresh results

**Benefit**: Repeated queries don't consume new tokens

**Test Cases**:
- [ ] Verify cache hit returns same result
- [ ] Verify cache miss executes query
- [ ] Verify cache invalidation after TTL
- [ ] Verify `--no-cache` flag works

---

## 📊 **Token Savings Matrix**

| Optimization | Token Savings | Effort | Priority | Phase | Status |
|--------------|---------------|--------|----------|-------|--------|
| Compact Query Responses | 80-90% | 1-2 days | ⭐⭐⭐⭐⭐ | 1 | 📋 |
| Smart Result Limiting | 95-99% | 1 day | ⭐⭐⭐⭐⭐ | 1 | 📋 |
| Minimal Call Graph | 90-95% | 1 day | ⭐⭐⭐⭐ | 1 | 📋 |
| Text Search Index | 95%+ | 2 days | ⭐⭐⭐⭐⭐ | 2 | 📋 |
| Structured File Content | 80-90% | 1 day | ⭐⭐⭐ | 2 | 📋 |
| Paginated Queries | 98%+ | 2 days | ⭐⭐⭐⭐ | 3 | 📋 |
| Diff Queries | 90%+ | 2 days | ⭐⭐⭐ | 3 | 📋 |
| Query Result Caching | 100% | 1 day | ⭐⭐⭐⭐ | 3 | 📋 |

**Total Potential**: 85-99% token savings across all operations

---

## 🚀 **Implementation Roadmap**

### **Week 1: Phase 1 (Quick Wins)**
**Goal**: 80-95% token savings on most common operations

| Day | Task | Token Savings | Status |
|-----|------|---------------|--------|
| 1 | Compact Query Responses | 80-90% | ⏳ |
| 2 | Smart Result Limiting | 95-99% | ⏳ |
| 3 | Minimal Call Graph Queries | 90-95% | ⏳ |
| 4 | Integration & Testing | - | ⏳ |
| 5 | Documentation & Cleanup | - | ⏳ |

**Week 1 Impact**: 80-95% token reduction for most queries

---

### **Week 2: Phase 2 (High-Impact)**
**Goal**: 90-95% token savings on search and file operations

| Day | Task | Token Savings | Status |
|-----|------|---------------|--------|
| 6 | Text Search Index (Minimal) | 95%+ | ⏳ |
| 7 | Structured File Content | 80-90% | ⏳ |
| 8 | Integration & Testing | - | ⏳ |
| 9 | Documentation & Cleanup | - | ⏳ |
| 10 | Performance Benchmarking | - | ⏳ |

**Week 2 Impact**: 90-95% token reduction for search and file content

---

### **Weeks 3-4: Phase 3 (Advanced)**
**Goal**: 90-99%+ token savings on all operations

| Day | Task | Token Savings | Status |
|-----|------|---------------|--------|
| 11-12 | Paginated Queries | 98%+ | ⏳ |
| 13-14 | Diff Queries | 90%+ | ⏳ |
| 15 | Query Result Caching | 100% | ⏳ |
| 16-18 | Integration & Testing | - | ⏳ |
| 19-20 | Documentation & Benchmarking | - | ⏳ |

**Weeks 3-4 Impact**: 90-99%+ token reduction for all operations

---

## 📈 **Expected Token Savings by Phase**

### **Current State (Before Any Optimizations)**
- File discovery: 500-2000 tokens
- Text search: 1000-5000 tokens
- Method lookup: 200-500 tokens
- File content: 1000-10000 tokens
- Callers: 500-2000 tokens

**Typical session**: 50,000-100,000 tokens

---

### **After Phase 1 (Quick Wins)**
- File discovery: 10-50 tokens (90-99% savings) ✅ Already done
- Text search: 1000-5000 tokens (no change yet)
- Method lookup: 10-20 tokens (90-95% savings)
- File content: 1000-10000 tokens (no change yet)
- Callers: 10-50 tokens (90-95% savings)

**Typical session**: 10,000-20,000 tokens (60-80% savings)

---

### **After Phase 2 (High-Impact)**
- File discovery: 10-50 tokens ✅
- Text search: 10-50 tokens (95%+ savings)
- Method lookup: 10-20 tokens ✅
- File content: 50-200 tokens (80-90% savings)
- Callers: 10-50 tokens ✅

**Typical session**: 2,000-5,000 tokens (90-95% savings)

---

### **After Phase 3 (Advanced)**
- File discovery: 10-50 tokens ✅
- Text search: 10-50 tokens ✅
- Method lookup: 10-20 tokens ✅
- File content: 50-200 tokens ✅
- Callers: 10-50 tokens ✅
- Large results: 10-100 tokens per page (98%+ savings)
- Diffs: 20-100 tokens (90%+ savings)

**Typical session**: 500-2,000 tokens (95-99% savings)

---

## 💰 **Cost Impact**

Assuming:
- **GPT-4**: $0.03 per 1K tokens (input) + $0.06 per 1K tokens (output) = $0.09 per 1K tokens
- **Typical session**: 10 interactions, ~5,000 tokens each = 50,000 tokens

| Phase | Tokens per Session | Cost per Session | Savings |
|-------|-------------------|------------------|---------|
| Current | 50,000-100,000 | $4.50-$9.00 | - |
| Phase 1 | 10,000-20,000 | $0.90-$1.80 | 60-80% |
| Phase 2 | 2,000-5,000 | $0.18-$0.45 | 90-95% |
| Phase 3 | 500-2,000 | $0.05-$0.18 | 95-99% |

**Annual Savings** (100 sessions/month):
- Current: $450-$900/month = $5,400-$10,800/year
- Phase 1: $90-$180/month = $1,080-$2,160/year (Save: $4,320-$8,640)
- Phase 2: $18-$45/month = $216-$540/year (Save: $5,184-$10,260)
- Phase 3: $5-$18/month = $60-$216/year (Save: $5,340-$10,584)

---

## 🎯 **Quick Start: 1-Day Token Optimization**

If you only have **1 day**, implement these two changes for **immediate 80-95% token savings**:

### **Task 1: Add Compact Mode (4 hours)**

```java
// In GraphQueryEngine.java
private boolean compactMode = true;

public void setCompactMode(boolean compact) {
    this.compactMode = compact;
}

private String formatClassList(List<ClassNode> classes) {
    if (compactMode) {
        return classes.stream()
            .map(ClassNode::getQualifiedName)
            .collect(Collectors.joining("\n"));
    }
    // ... existing verbose format
}

// Do the same for all format methods
```

### **Task 2: Add Result Limits (4 hours)**

```java
// In GraphQueryEngine.java
private int defaultLimit = 10;

public void setDefaultLimit(int limit) {
    this.defaultLimit = limit;
}

@Override
public List<ClassNode> findClassesByName(String name) {
    List<ClassNode> all = indexes.getClassesByName(name);
    return all.stream().limit(defaultLimit).collect(Collectors.toList());
}

// Do the same for all list-returning methods
```

**Result**: 80-95% token savings on day 1 with minimal effort!

---

## ✅ **Acceptance Criteria**

### **Phase 1 Complete**
- [ ] All query responses default to compact mode
- [ ] All list queries default to limit=10
- [ ] `--verbose` flag restores old behavior
- [ ] `--limit N` works for all queries
- [ ] Token count reduced by 80-95% for common operations
- [ ] All existing tests pass

### **Phase 2 Complete**
- [ ] Text search available via `query:search:text:QUERY`
- [ ] Structured file content via `query:file:PATH`
- [ ] Token count reduced by 90-95% for search and file operations
- [ ] All new features tested

### **Phase 3 Complete**
- [ ] Paginated queries work for large result sets
- [ ] Diff queries available
- [ ] Query result caching implemented
- [ ] Token count reduced by 95-99%+ for all operations
- [ ] All features tested and documented

---

## 📊 **Success Metrics**

| Metric | Current | Phase 1 Target | Phase 2 Target | Phase 3 Target |
|--------|---------|----------------|----------------|----------------|
| Avg tokens/query | 500-2000 | 50-200 | 10-50 | 5-20 |
| Avg tokens/session | 50,000 | 10,000 | 2,000 | 500 |
| Cost/session | $4.50 | $0.90 | $0.18 | $0.05 |
| Token savings | - | 60-80% | 90-95% | 95-99% |

---

## 🔗 **Related Documents**

- [TOKEN_SAVINGS_PLAN.md](TOKEN_SAVINGS_PLAN.md) - Detailed analysis and examples
- [PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md) - Complete optimization plan
- [OPTIMIZATION_SUMMARY.md](OPTIMIZATION_SUMMARY.md) - Summary of all work

---

## 💡 **Key Takeaways**

1. **Focus on compact, structured responses** - This saves the most tokens
2. **Limit results by default** - LLMs rarely need all results
3. **Replace grep with text search** - Huge token savings
4. **Avoid full file content** - Return structure, not implementation
5. **Cache query results** - Zero tokens for repeated queries

**Bottom line**: With 3-4 weeks of focused effort, GausVibe can reduce LLM token usage by **95-99%** for coding operations, saving thousands of dollars per year in production use.
