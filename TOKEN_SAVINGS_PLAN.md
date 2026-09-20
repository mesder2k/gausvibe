# GausVibe Token Savings Implementation Plan

## 🎯 **Objective**

**Prioritize optimizations that save LLM tokens** by replacing verbose, expensive operations with concise, structured graph queries that return minimal, high-signal data.

**Key Insight**: Token costs are typically the largest expense in LLM operations. A single `grep -r` or `find` command can return thousands of tokens of irrelevant data. GausVibe's structured queries return only what's needed.

---

## 📊 **Token Cost Analysis**

### Current Expensive Operations (Token Perspective)

| Operation | Typical Output | Tokens Used | Replacement | Tokens Saved |
|-----------|----------------|-------------|-------------|--------------|
| `find . -name "*.java"` | 1000+ file paths | 500-2000+ | `query:file:*.java` | 90-95% |
| `grep -r "method" src/` | 50+ matching lines | 1000-5000+ | `query:method:name:method` | 95-99% |
| `grep -r "class" src/ \| head -20` | 20+ lines + grep metadata | 200-500 | `query:class:name:Class` | 90-95% |
| `find . -type f -name "*.java" -exec grep -l "import" {} \;` | 500+ file paths | 2000-10000 | `query:search:nodes:IMPORT` | 95-99% |
| `cat File.java` (full file) | Entire file content | File size in tokens | `query:class:com.example.File` | 80-95% |
| Shell command + output | Command + verbose output | 100-10000 | Structured query | 80-99% |

### Token Savings Examples

**Example 1: Finding Java Files**
```bash
# Shell (expensive)
$ find . -name "*.java" -type f
./src/main/java/com/example/UserService.java
./src/main/java/com/example/UserRepository.java
./src/main/java/com/example/UserController.java
# ... 500 more lines
# Tokens: ~1500 (500 paths × 3 tokens each)
```

```java
// GausVibe (token-efficient)
gausvibe> query:class:all
Class: com.example.UserService
Class: com.example.UserRepository
Class: com.example.UserController
# Returns: 3 classes (structured, minimal)
# Tokens: ~50 (3 results × 15 tokens each)
# SAVINGS: 96.7%
```

**Example 2: Searching for Method Usage**
```bash
# Shell (very expensive)
$ grep -r "calculateTotal" src/
src/main/java/com/example/Calculator.java:    public double calculateTotal() {
src/main/java/com/example/OrderService.java:        double total = calculator.calculateTotal();
src/test/java/com/example/CalculatorTest.java:        assertEquals(100.0, calculator.calculateTotal(), 0.01);
# ... 50 more matches
# Tokens: ~3000 (10 matches × 300 tokens each with context)
```

```java
// GausVibe (token-efficient)
gausvibe> query:method:name:calculateTotal
Method: com.example.Calculator#calculateTotal()
  File: src/main/java/com/example/Calculator.java
  Signature: public double calculateTotal()
  
Callers (3):
  - com.example.OrderService#processOrder()
  - com.example.CalculatorTest#testCalculateTotal()
# Tokens: ~50
# SAVINGS: 98.3%
```

---

## 🎯 **Token-Focused Implementation Plan**

### **Priority 1: HIGH TOKEN SAVINGS (Implement First)**

#### **1. Structured Query Responses (SAVES: 90-99% tokens)**

**Problem**: Current query responses are verbose and include unnecessary metadata.

**Solution**: Return minimal, structured data that LLMs can parse efficiently.

**Implementation**:

```java
// In GraphQueryEngine.java
// Change response format from verbose to compact

// BEFORE (verbose)
private String formatClass(ClassNode cls) {
    StringBuilder sb = new StringBuilder();
    sb.append("Class: ").append(cls.getQualifiedName()).append("\n");
    sb.append("  Name: ").append(cls.getName()).append("\n");
    sb.append("  File: ").append(cls.getFile()).append("\n");
    if (cls.hasSuperclass()) {
        sb.append("  Superclass: ").append(cls.getSuperclass()).append("\n");
    }
    if (!cls.getInterfaces().isEmpty()) {
        sb.append("  Interfaces: ").append(String.join(", ", cls.getInterfaces())).append("\n");
    }
    sb.append("  Methods: ").append(queryEngine.getMethods(cls).size()).append("\n");
    sb.append("  Fields: ").append(queryEngine.getFields(cls).size()).append("\n");
    return sb.toString();
}
// Tokens: ~150 per class

// AFTER (compact JSON - saves 80% tokens)
private String formatClassCompact(ClassNode cls) {
    return String.format("{" +
        "\"type\":\"class\"," +
        "\"qn\":\"%s\"," +
        "\"file\":\"%s\"," +
        "\"super\":%s," +
        "\"interfaces\":%s," +
        "\"methods\":%d," +
        "\"fields\":%d" +
        "}",
        escapeJson(cls.getQualifiedName()),
        escapeJson(cls.getFile().toString()),
        cls.hasSuperclass() ? "\"" + cls.getSuperclass() + "\"" : "null",
        cls.getInterfaces().isEmpty() ? "[]" : toJsonArray(cls.getInterfaces()),
        queryEngine.getMethods(cls).size(),
        queryEngine.getFields(cls).size()
    );
}
// Tokens: ~30 per class (80% reduction)
```

**Files to modify**:
- `GraphQueryEngine.java` - All format methods
- `CommandLineInterface.java` - Add `--compact` flag

**Token savings**: 80-90% on query responses

**Effort**: 1-2 days

---

#### **2. Smart Result Limiting (SAVES: 95-99% tokens)**

**Problem**: Queries return all results, even when LLM only needs the first few.

**Solution**: Implement intelligent result limiting with "show more" capability.

**Implementation**:

```java
// In GraphQueryEngine.java
private static final int DEFAULT_LIMIT = 10;
private static final int MAX_LIMIT = 100;

@Override
public List<ClassNode> findClassesByName(String name) {
    return findClassesByName(name, DEFAULT_LIMIT);
}

public List<ClassNode> findClassesByName(String name, int limit) {
    limit = Math.min(limit, MAX_LIMIT);
    List<ClassNode> all = indexes.getClassesByName(name);
    return all.stream().limit(limit).collect(Collectors.toList());
}

// Add metadata to response
public QueryResult<ClassNode> findClassesByNameWithMeta(String name, int limit) {
    List<ClassNode> all = indexes.getClassesByName(name);
    List<ClassNode> results = all.stream().limit(limit).collect(Collectors.toList());
    boolean hasMore = all.size() > limit;
    return new QueryResult<>(results, hasMore, all.size());
}
```

**CLI Integration**:

```java
// In CommandLineInterface.java
case "class:name":
    if (cmd.getArguments().size() < 2) {
        return "Usage: class:name:NAME [--limit N] [--show-all]"
    }
    String name = cmd.getArguments().get(1);
    int limit = 10;
    boolean showAll = false;
    
    // Parse optional limit
    for (int i = 2; i < cmd.getArguments().size(); i++) {
        if (cmd.getArguments().get(i).equals("--show-all")) {
            showAll = true;
        } else if (cmd.getArguments().get(i).startsWith("--limit")) {
            limit = Integer.parseInt(cmd.getArguments().get(i).split("=")[1]);
        }
    }
    
    List<ClassNode> classes = showAll ? 
        queryEngine.findClassesByName(name) :
        queryEngine.findClassesByName(name, limit);
    
    return formatClassList(classes, showAll);
```

**Token savings**: 95-99% when limiting to 10 results instead of 1000

**Effort**: 1 day

---

#### **3. Paginated Queries (SAVES: 98%+ tokens)**

**Problem**: Large codebases return thousands of results, overwhelming the LLM.

**Solution**: Implement pagination with cursor-based iteration.

**Implementation**:

```java
// In GraphQueryEngine.java
public PaginatedResult<ClassNode> findAllClassesPaginated(int limit, String cursor) {
    List<ClassNode> allClasses = indexes.getAllClasses();
    
    // Decode cursor (position in list)
    int startIndex = cursor != null ? Integer.parseInt(cursor) : 0;
    
    // Get page
    int endIndex = Math.min(startIndex + limit, allClasses.size());
    List<ClassNode> page = allClasses.subList(startIndex, endIndex);
    
    // Next cursor
    String nextCursor = endIndex < allClasses.size() ? String.valueOf(endIndex) : null;
    
    return new PaginatedResult<>(
        page,
        nextCursor,
        allClasses.size(),
        startIndex,
        endIndex
    );
}

// In CLI
case "class:all:paginated":
    int limit = cmd.getIntArg("--limit", 10);
    String cursor = cmd.getArg("--cursor");
    PaginatedResult<ClassNode> result = queryEngine.findAllClassesPaginated(limit, cursor);
    
    StringBuilder sb = new StringBuilder();
    sb.append("Classes (").append(result.getStart()).append("-").append(result.getEnd())
       .append(" of ").append(result.getTotal()).append("):\n");
    
    for (ClassNode cls : result.getItems()) {
        sb.append("  - ").append(cls.getQualifiedName()).append("\n");
    }
    
    if (result.getNextCursor() != null) {
        sb.append("\n---\nNext page: class:all:paginated --limit ").append(limit)
           .append(" --cursor ").append(result.getNextCursor());
    }
    
    return sb.toString();
```

**Token savings**: 98%+ for large result sets

**Effort**: 2 days

---

### **Priority 2: HIGH TOKEN SAVINGS (Implement Next)**

#### **4. Text Search Index with Token-Aware Ranking (SAVES: 95%+ tokens)**

**Problem**: Without text search, LLMs must use `grep` which returns entire lines with context, wasting tokens.

**Solution**: Implement inverted index that returns only matching node IDs/qualified names.

**Implementation**:

```java
// In TextSearchIndex.java
public List<String> searchMinimal(String query) {
    // Returns only qualified names, not full node details
    Set<Node> results = search(query);
    return results.stream()
        .map(node -> {
            if (node instanceof ClassNode) {
                return ((ClassNode) node).getQualifiedName();
            } else if (node instanceof MethodNode) {
                return ((MethodNode) node).getQualifiedName();
            } else if (node instanceof FieldNode) {
                return ((FieldNode) node).getQualifiedName();
            }
            return node.getId();
        })
        .collect(Collectors.toList());
}

// Returns: ["com.example.Calculator", "com.example.Calculator#add", ...]
// Tokens: ~10 per result instead of ~100
```

**CLI Query**:
```
gausvibe> query:search:text:calculateTotal --minimal
com.example.Calculator#calculateTotal
com.example.OrderService#processOrder
com.test.CalculatorTest#testCalculateTotal
```

**Token savings**: 95% compared to grep output

**Effort**: 1-2 days

---

#### **5. Call Graph Queries (SAVES: 90-95% tokens)**

**Problem**: Finding callers requires parsing grep output or manual traversal.

**Solution**: Pre-computed call graph returns only relevant method references.

**Implementation**:

```java
// In GraphQueryEngine.java
@Override
public String getCallersMinimal(MethodNode method) {
    if (method == null) return "";
    
    List<MethodNode> callers = getCallers(method);
    if (callers.isEmpty()) return "No callers";
    
    // Return only qualified names, one per line
    return callers.stream()
        .map(m -> m.getQualifiedName())
        .collect(Collectors.joining("\n"));
}

// CLI: gausvibe> query:method:com.example.Calculator#calculateTotal:callers
// Output:
// com.example.OrderService#processOrder
// com.example.CalculatorTest#testCalculateTotal
// Tokens: ~20 instead of ~500 for grep output
```

**Token savings**: 90-95%

**Effort**: 1 day

---

### **Priority 3: MEDIUM TOKEN SAVINGS**

#### **6. Structured File Content Queries (SAVES: 80-90% tokens)**

**Problem**: LLMs use `cat file.java` to see file content, which sends the entire file (often 1000+ tokens).

**Solution**: Query specific parts of files (methods, classes) instead of full content.

**Implementation**:

```java
// New query type: file:content
@Override
public String getFileContentStructured(Path file, String focus) {
    List<Node> nodes = getNodesInFile(file);
    
    StringBuilder sb = new StringBuilder();
    sb.append("File: ").append(file).append("\n\n");
    
    for (Node node : nodes) {
        if (node instanceof ClassNode) {
            ClassNode cls = (ClassNode) node;
            sb.append("Class: ").append(cls.getQualifiedName()).append("\n");
            
            // Only include method signatures, not bodies
            if (focus == null || focus.equals("methods") || focus.equals("all")) {
                for (MethodNode method : getMethods(cls)) {
                    sb.append("  Method: ").append(method.getSignature()).append("\n");
                }
            }
            
            if (focus == null || focus.equals("fields") || focus.equals("all")) {
                for (FieldNode field : getFields(cls)) {
                    sb.append("  Field: ").append(field.getName())
                       .append(": ").append(field.getDataType()).append("\n");
                }
            }
        }
    }
    
    return sb.toString();
}
```

**CLI Query**:
```
gausvibe> query:file:src/main/java/Calculator.java
# Returns: Class structure with method signatures (not bodies)
# Tokens: ~100 instead of ~2000 for full file

# Or more focused
gausvibe> query:file:src/main/java/Calculator.java:methods
# Returns: Only method signatures
# Tokens: ~50 instead of ~2000
```

**Token savings**: 80-90%

**Effort**: 1 day

---

#### **7. Diff Queries (SAVES: 90%+ tokens)**

**Problem**: LLMs use `git diff` which can be very verbose.

**Solution**: Structured diff queries showing only what changed.

**Implementation**:

```java
// In GraphQueryEngine.java
@Override
public String getChangesSince(Path file, long timestamp) {
    // Get current nodes from file
    List<Node> currentNodes = getNodesInFile(file);
    
    // Get previous version from cache/snapshot
    List<Node> previousNodes = getPreviousVersion(file, timestamp);
    
    // Compute diff
    return computeDiff(previousNodes, currentNodes);
}

private String computeDiff(List<Node> previous, List<Node> current) {
    // Only show changed nodes
    List<Node> added = current.stream()
        .filter(n -> !previous.contains(n))
        .collect(Collectors.toList());
    
    List<Node> removed = previous.stream()
        .filter(n -> !current.contains(n))
        .collect(Collectors.toList());
    
    List<Node> modified = current.stream()
        .filter(n -> previous.contains(n) && isModified(n))
        .collect(Collectors.toList());
    
    // Return minimal diff representation
    StringBuilder sb = new StringBuilder();
    if (!added.isEmpty()) {
        sb.append("Added:\n");
        added.forEach(n -> sb.append("  + ").append(getNodeSignature(n)).append("\n"));
    }
    if (!removed.isEmpty()) {
        sb.append("Removed:\n");
        removed.forEach(n -> sb.append("  - ").append(getNodeSignature(n)).append("\n"));
    }
    if (!modified.isEmpty()) {
        sb.append("Modified:\n");
        modified.forEach(n -> sb.append("  ~ ").append(getNodeSignature(n)).append("\n"));
    }
    
    return sb.toString();
}
```

**CLI Query**:
```
gausvibe> query:diff:src/main/java/Calculator.java --since 2026-09-20T10:00:00Z
# Output:
# Modified:
#   ~ com.example.Calculator#calculateTotal
# Tokens: ~20 instead of ~500 for git diff
```

**Token savings**: 90%+

**Effort**: 2 days

---

## 📈 **Token Savings Summary**

| Optimization | Token Savings | Effort | Priority | Status |
|--------------|---------------|--------|----------|--------|
| Structured Query Responses | 80-90% | 1-2 days | ⭐⭐⭐⭐⭐ | 📋 Ready |
| Smart Result Limiting | 95-99% | 1 day | ⭐⭐⭐⭐⭐ | 📋 Ready |
| Paginated Queries | 98%+ | 2 days | ⭐⭐⭐⭐⭐ | 📋 Ready |
| Text Search Index (Minimal) | 95%+ | 1-2 days | ⭐⭐⭐⭐⭐ | 📋 Ready |
| Call Graph Queries (Minimal) | 90-95% | 1 day | ⭐⭐⭐⭐ | 📋 Ready |
| Structured File Content | 80-90% | 1 day | ⭐⭐⭐ | 📋 Ready |
| Diff Queries | 90%+ | 2 days | ⭐⭐⭐ | 📋 Ready |

**Total Potential Token Savings**: **80-99%** across common operations

---

## 🚀 **Recommended Implementation Order**

### **Phase 1: Quick Token Wins (3-5 days)**
1. ✅ **File System Cache** (already implemented) - Saves tokens by avoiding repeated `find` commands
2. ⏳ **Structured Query Responses** - Compact JSON output (80-90% savings)
3. ⏳ **Smart Result Limiting** - Default limit of 10 results (95-99% savings)
4. ⏳ **Call Graph Queries (Minimal)** - Only return method names (90-95% savings)

**Impact**: 80-95% token savings on most common queries

### **Phase 2: High-Impact Token Savings (1 week)**
5. ⏳ **Text Search Index (Minimal)** - Replace grep with structured search (95%+ savings)
6. ⏳ **Structured File Content** - Query specific parts instead of full files (80-90% savings)

**Impact**: 80-95% token savings on search and file content operations

### **Phase 3: Advanced Token Savings (1-2 weeks)**
7. ⏳ **Paginated Queries** - Large result sets with cursor (98%+ savings)
8. ⏳ **Diff Queries** - Structured diffs instead of git diff (90%+ savings)

**Impact**: 90-98%+ token savings on large operations

---

## 💰 **Token Cost Comparison**

### Example Workflow: Find and Fix a Bug

**Before (Using Shell Commands):**
```
User: Find all usages of calculateTotal method
AI:  $ grep -r "calculateTotal" src/
     [Output: 50 lines × 100 tokens = 5000 tokens]

User: Show me the Calculator class
AI:  $ cat src/main/java/com/example/Calculator.java
     [Output: 500 lines × 20 tokens = 10000 tokens]

User: Who calls calculateTotal?
AI:  $ grep -r "calculateTotal(" src/
     [Output: 20 lines × 100 tokens = 2000 tokens]

Total: 17,000 tokens
```

**After (Using Optimized GausVibe):**
```
User: Find all usages of calculateTotal method
AI:  gausvibe> query:method:name:calculateTotal
     Method: com.example.Calculator#calculateTotal
     Callers: com.example.OrderService#processOrder, com.test.CalculatorTest#test
     [Output: 50 tokens]

User: Show me the Calculator class
AI:  gausvibe> query:class:com.example.Calculator
     Class: com.example.Calculator
     Methods: calculateTotal(), add(), subtract()
     Fields: value
     [Output: 20 tokens]

User: Who calls calculateTotal?
AI:  gausvibe> query:method:com.example.Calculator#calculateTotal:callers
     com.example.OrderService#processOrder
     com.test.CalculatorTest#test
     [Output: 10 tokens]

Total: 80 tokens
```

**Token Savings: 99.5%** (17,000 → 80 tokens)

---

## 🎯 **Implementation Checklist**

### Phase 1 (Start Here)
- [ ] Implement compact JSON output for all query types
- [ ] Add `--limit` parameter to all list queries (default: 10)
- [ ] Add `--minimal` flag for ultra-compact output
- [ ] Update CLI to support compact mode by default
- [ ] Add token counting to responses (for monitoring)

### Phase 2
- [ ] Implement TextSearchIndex with minimal output
- [ ] Add structured file content queries
- [ ] Implement call graph queries with minimal output

### Phase 3
- [ ] Implement paginated queries with cursors
- [ ] Add diff queries
- [ ] Add cache for query results (token reuse)

---

## 📊 **Expected Impact**

### Token Savings by Operation Type

| Operation Type | Before (tokens) | After (tokens) | Savings | Priority |
|----------------|-----------------|----------------|---------|----------|
| File discovery | 500-2000 | 10-50 | 90-99% | ⭐⭐⭐⭐⭐ |
| Text search | 1000-5000 | 10-50 | 95-99% | ⭐⭐⭐⭐⭐ |
| Method lookup | 200-500 | 10-20 | 90-95% | ⭐⭐⭐⭐ |
| Class lookup | 200-500 | 10-20 | 90-95% | ⭐⭐⭐⭐ |
| File content | 1000-10000 | 50-200 | 80-95% | ⭐⭐⭐ |
| Callers/Callees | 500-2000 | 10-50 | 90-99% | ⭐⭐⭐⭐ |
| Diffs | 500-5000 | 20-100 | 90-99% | ⭐⭐⭐ |

**Average token savings: 85-95%** across all operations

---

## 🔧 **Quick Start: Token Optimization**

### Step 1: Add Compact Mode (1 day)

Modify `GraphQueryEngine.java` to add a compact mode:

```java
public class GraphQueryEngine {
    private boolean compactMode = true; // Default to compact
    
    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
    }
    
    private String formatClass(ClassNode cls) {
        if (compactMode) {
            return cls.getQualifiedName(); // Just the FQN
        }
        // ... existing verbose format
    }
    
    private String formatClassList(List<ClassNode> classes) {
        if (compactMode) {
            return String.join("\n", classes.stream()
                .map(ClassNode::getQualifiedName)
                .collect(Collectors.toList()));
        }
        // ... existing verbose format
    }
}
```

### Step 2: Add Result Limits (1 day)

Add default limits to all query methods:

```java
public class GraphQueryEngine {
    private int defaultLimit = 10;
    
    public void setDefaultLimit(int limit) {
        this.defaultLimit = limit;
    }
    
    @Override
    public List<ClassNode> findClassesByName(String name) {
        return findClassesByName(name, defaultLimit);
    }
    
    public List<ClassNode> findClassesByName(String name, int limit) {
        List<ClassNode> all = indexes.getClassesByName(name);
        return all.stream().limit(limit).collect(Collectors.toList());
    }
}
```

### Step 3: Add Minimal Text Search (2 days)

Implement basic text search with minimal output:

```java
// In GraphQueryEngine.java
public List<String> searchTextMinimal(String query) {
    // Simple string matching for now
    return graph.getAllNodes().stream()
        .filter(node -> nodeToString(node).contains(query))
        .map(this::getNodeSignature)
        .limit(defaultLimit)
        .collect(Collectors.toList());
}

private String getNodeSignature(Node node) {
    if (node instanceof ClassNode) {
        return ((ClassNode) node).getQualifiedName();
    } else if (node instanceof MethodNode) {
        return ((MethodNode) node).getQualifiedName();
    } else if (node instanceof FieldNode) {
        return ((FieldNode) node).getQualifiedName();
    }
    return node.getId();
}
```

**CLI Integration**:
```java
// In CommandLineInterface.java
case "search:text":
    String query = cmd.getArguments().get(0);
    int limit = cmd.getIntArg("--limit", defaultLimit);
    boolean minimal = cmd.hasFlag("--minimal");
    
    List<String> results = queryEngine.searchTextMinimal(query, limit);
    return minimal ? String.join("\n", results) : formatDetailedResults(results);
```

---

## 🎯 **Conclusion**

By focusing on **token savings** rather than just performance, we can achieve **85-99% reduction in token usage** for common LLM coding operations. This directly reduces costs and allows for more complex, multi-step reasoning within token budgets.

**Recommended approach**: Implement Phase 1 (Structured Query Responses + Result Limiting) first, as this provides immediate 80-95% token savings with minimal effort (3-5 days).

**Total potential savings**: For a typical coding session that might use 50,000-100,000 tokens with shell commands, the optimized GausVibe could use **500-5,000 tokens** - a **90-99% reduction**.
