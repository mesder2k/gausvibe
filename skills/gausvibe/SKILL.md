# GausVibe Skill

**ID:** gausvibe
**Version:** 1.0.0
**Author:** Magnus Find
**License:** Proprietary - All Rights Reserved
**Homepage:** https://github.com/magnusfind/gausvibe

---

## 📌 OVERVIEW

Build and query structured graphs of Java code. Enables LLMs to understand Java codebases without grep.

### What It Does

- **Parses** Java source code into a rich graph of nodes (classes, methods, fields, statements) and edges (relationships between them)
- **Queries** the graph to answer questions like "who calls this method?", "what are the subclasses?", "find all implementations"
- **Caches** graphs for efficient repeated querying
- **Integrates** natively with Vibe via tools and MCP

### Why It Exists

Java codebases are complex networks of classes, methods, and calls. Without structured understanding, LLMs resort to grep which:
- Misses semantic relationships (inheritance, polymorphism)
- Doesn't understand scope or visibility
- Can't answer "why" questions about code structure
- Fails on large codebases with token limits

GausVibe solves this by building a queryable graph that Vibe can use for precise code understanding.

---

## 🎯 CAPABILITIES

| Capability | Description |
|------------|-------------|
| Parse Java Projects | Build complete AST + semantic graphs from Java source |
| Query Graph | Execute structured queries against the graph |
| Natural Language | Accept plain English queries ("who calls add()?") |
| Real-time (MCP) | Stream results for interactive exploration |
| Offline Analysis | Build once, query many times with caching |

---

## 🔧 TOOLS

### `build-graph`

Build a structured graph from Java source code.

#### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `path` | string | ✅ | - | Path to Java project root or source directory |
| `output` | string | ❌ | `<auto>` | Output file path for graph JSON |
| `include_test` | boolean | ❌ | `true` | Include test source directories |
| `exclude` | string | ❌ | - | Glob pattern for files to exclude |
| `parallel` | boolean | ❌ | `true` | Parse files in parallel |
| `verbose` | boolean | ❌ | `false` | Enable verbose logging |

#### Returns

```json
{
  "graph": {
    "version": "1.0",
    "metadata": {
      "createdAt": "2026-09-17T10:00:00Z",
      "nodeCount": 42,
      "edgeCount": 156,
      "fileCount": 3,
      "parseTimeMs": 1234
    },
    "nodes": [...],
    "edges": [...],
    "indexes": {...}
  },
  "warnings": ["Skipped FileA.java: syntax error at line 42"]
}
```

#### Examples

```bash
# Build graph for current directory
vibe tool gausvibe:build --path .

# Build graph with specific output
vibe tool gausvibe:build --path ./src --output ./graph.json

# Build excluding test files
vibe tool gausvibe:build --path . --include_test false

# Build with exclusion pattern
vibe tool gausvibe:build --path . --exclude "**/Generated*.java"
```

---

### `query-graph`

Execute a query on a previously built graph.

#### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `graph` | string | ✅ | - | Path to graph JSON file |
| `query` | string | ✅ | - | Query to execute (natural language or structured) |
| `format` | string | ❌ | `json` | Output format: `json`, `text`, `summary` |
| `limit` | integer | ❌ | `100` | Maximum results to return |
| `include_source` | boolean | ❌ | `false` | Include source code snippets in results |

#### Returns

Format depends on `format` parameter.

**JSON format (default):**
```json
{
  "query": "who calls Calculator.add()",
  "results": [
    {
      "type": "METHOD_CALL",
      "caller": {
        "id": "mth:com/example/Main#main([Ljava/lang/String;)",
        "name": "main",
        "qualifiedName": "com.example.Main.main",
        "file": "Main.java",
        "line": 42
      },
      "callee": {
        "id": "mth:com/example/Calculator#add(int,int)",
        "name": "add",
        "qualifiedName": "com.example.Calculator.add"
      }
    }
  ],
  "count": 1,
  "queryTimeMs": 12
}
```

**Text format:**
```
Caller: Main.main([Ljava/lang/String;) at Main.java:42
```

**Summary format:**
```
Found 1 caller of Calculator.add(): Main.main()
```

#### Examples

```bash
# Find a class
vibe tool gausvibe:query --graph graph.json --query "find class com.example.Calculator"

# Get methods of a class
vibe tool gausvibe:query --graph graph.json --query "methods in Calculator"

# Find callers
vibe tool gausvibe:query --graph graph.json --query "who calls add()"

# Get subclasses
vibe tool gausvibe:query --graph graph.json --query "subclasses of Animal"

# Text format for readability
vibe tool gausvibe:query --graph graph.json --query "all classes" --format text
```

---

## 🔗 MCP SERVER

For real-time, interactive querying without file I/O.

### Endpoints

| Method | Description |
|--------|-------------|
| `get_nodes` | Retrieve nodes by type, file, or ID |
| `get_edges` | Retrieve edges by type or between nodes |
| `query` | Execute a query (same as query-graph tool) |
| `get_schema` | Get node/edge type definitions |
| `get_stats` | Get graph statistics |

### Example Session

```json
// Client connects to MCP server
{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}

// Server responds with available tools
{"jsonrpc": "2.0", "id": 1, "result": {
  "tools": ["get_nodes", "get_edges", "query", "get_schema", "get_stats"]
}}

// Client queries for classes
{"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {
  "name": "query",
  "arguments": {"query": "all classes"}
}}

// Server streams results
{"jsonrpc": "2.0", "id": 2, "result": {
  "content": [
    {"type": "CLASS", "id": "cls:com/example/Calculator", ...},
    {"type": "CLASS", "id": "cls:com/example/Main", ...}
  ]
}}
```

---

## 🗣️ QUERY DSL

### Natural Language Support

The query tool accepts plain English queries and maps them to the underlying `JavaGraphQuery` API.

| Natural Language Query | API Method | Example |
|------------------------|------------|---------|
| "find class X" | findClassByQualifiedName | "find class com.example.Calculator" |
| "find classes named X" | findClassesByName | "find classes named Calculator" |
| "all classes" | getAllClasses | "all classes" |
| "subclasses of X" | getSubclasses | "subclasses of Animal" |
| "implementations of X" | getImplementations | "implementations of Runnable" |
| "methods in X" | getMethods | "methods in Calculator" |
| "find method X" | findMethodBySignature | "find method add(int,int)" |
| "methods named X" | findMethodsByName | "methods named add" |
| "who calls X" | getCallers | "who calls add()" |
| "what does X call" | getCallees | "what does main() call" |
| "fields in X" | getFields | "fields in Calculator" |
| "variables in X" | getVariables | "variables in add()" |
| "superclass of X" | getSuperclass | "superclass of Calculator" |
| "what does X extend" | getSuperclass | "what does Calculator extend" |
| "what does X implement" | getInterfaces | "what does Calculator implement" |
| "statements in X" | getStatements | "statements in add()" |

### Structured Query Support

For precise queries, use the structured format:

```
FORMAT: methodName(param1=value1, param2=value2)

EXAMPLES:
  findClassByQualifiedName(qn="com.example.Calculator")
  getMethods(class="cls:com/example/Calculator")
  getCallers(method="mth:com/example/Calculator#add(int,int)")
  getSubclasses(class="cls:com/example/Animal")
```

---

## 📦 INSTALLATION

### Prerequisites

- Java 17+ (for running GausVibe)
- Maven (for building from source)
- Python 3.8+ (for MCP server)

### Quick Install

```bash
# Clone the skill
cd ~/.vibe/skills
git clone https://github.com/magnusfind/gausvibe gausvibe

# Build the Java component
cd gausvibe
mvn clean package

# Install dependencies
pip install -r mcp/requirements.txt
```

### Configuration

Create `~/.vibe/skills/gausvibe/config.json`:

```json
{
  "java_home": "/path/to/java",
  "maven_home": "/path/to/maven",
  "cache_dir": "~/.vibe/cache/gausvibe",
  "max_cache_size_mb": 500,
  "parallel_parsing": true,
  "parse_timeout_seconds": 300
}
```

---

## 🚀 USAGE PATTERNS

### Pattern 1: Build Once, Query Many

```
User: Analyze this Java project for me
Vibe: [calls build-graph --path /project --output /tmp/graph.json]
      Graph built: 42 classes, 156 methods, 342 edges
User: What classes extend Animal?
Vibe: [calls gausvibe:query --graph /tmp/graph.json --query "subclasses of Animal"]
      Animal is extended by: Dog, Cat, Bird
User: Who calls Dog.bark()?
Vibe: [calls gausvibe:query --graph /tmp/graph.json --query "who calls bark()"]
      Dog.bark() is called by: Main.playWithDog(), TestDog.testBark()
```

### Pattern 2: MCP Interactive Session

```
User: Let's explore this codebase
Vibe: [starts MCP server, loads graph]
      Connected to GausVibe MCP server
User: Show me the Calculator class
Vibe: [MCP call: get_nodes(type="CLASS", name="Calculator")]
      Calculator (cls:com/example/Calculator)
      - Methods: add, subtract, multiply, divide
      - Fields: none
      - Extends: Object
User: What's the call graph for add?
Vibe: [MCP call: query("getCallers(add) AND getCallees(add)")]
      add(int,int) is called by: Main.main
      add(int,int) calls: (none - leaf method)
```

### Pattern 3: Batch Analysis

```bash
# Build graph
vibe tool gausvibe:build --path ./my-project --output ./graph.json

# Extract all classes
vibe tool gausvibe:query --graph ./graph.json --query "all classes" --format text > classes.txt

# Extract call graph
vibe tool gausvibe:query --graph ./graph.json --query "all methods" --format json > callgraph.json
```

---

## 🎨 NODE & EDGE TYPES

### Node Types

| Type | Prefix | Description |
|------|--------|-------------|
| PACKAGE | `pkg:` | Java package |
| CLASS | `cls:` | Class or interface |
| METHOD | `mth:` | Method or constructor |
| FIELD | `fld:` | Class field |
| PARAMETER | `par:` | Method parameter |
| VARIABLE | `var:` | Local variable |
| BLOCK | `blk:` | Code block |
| IF | `if:` | If statement |
| FOR | `for:` | For loop |
| WHILE | `while:` | While loop |
| TRY | `try:` | Try-catch block |
| RETURN | `return:` | Return statement |
| METHOD_CALL | `call:` | Method call expression |
| FIELD_ACCESS | `field_acc:` | Field access expression |
| LITERAL | `lit:` | Literal value |
| BINARY_OP | `bin_op:` | Binary operation |

### Edge Types

| Type | Description |
|------|-------------|
| CONTAINS | Parent contains child |
| HAS_METHOD | Class has method |
| HAS_FIELD | Class has field |
| HAS_PARAMETER | Method has parameter |
| CALLS | Method/constructor call |
| ACCESSES | Field access |
| INHERITS | Class extends class |
| IMPLEMENTS | Class implements interface |
| EXTENDS | Interface extends interface |
| OVERRIDES | Method overrides method |
| REFERENCES | Generic reference |
| BODY | Method/control flow body |
| CONDITION | If/while/for condition |
| THEN_BRANCH | If then branch |
| ELSE_BRANCH | If else branch |

---

## ⚡ PERFORMANCE

### Benchmarks (Estimated)

| Project Size | Parse Time | Memory | Graph Size |
|--------------|------------|--------|------------|
| 10 files | < 1s | ~10MB | ~100KB |
| 100 files | 5-10s | ~50MB | ~1MB |
| 1000 files | 60-120s | ~300MB | ~10MB |
| 10000 files | 10-20min | ~2GB | ~100MB |

### Optimization Features

- Parallel file parsing (configurable)
- Incremental parsing (track file hashes)
- Graph caching (persist to disk)
- Indexed queries (O(1) lookups for most operations)
- Lazy loading (load AST on demand)

---

## 🛠️ DEVELOPMENT

### Building from Source

```bash
# Build Java components
mvn clean package

# Build will produce:
# - target/gausvibe-1.0.0.jar (main JAR)
# - target/gausvibe-1.0.0-cli.jar (CLI with main class)
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -Pintegration-tests
```

### Adding New Query Types

1. Add method to `JavaGraphQuery` interface
2. Implement in `GraphQueryEngine`
3. Add natural language mapping in query parser
4. Add tests

---

## 📚 EXAMPLES

### Example 1: Understanding a New Codebase

```
User: I just joined a project with a complex Java codebase. Help me understand it.

Vibe: I'll help you explore this codebase. First, let me build a graph of the code structure.
      [calls gausvibe:build --path .]
      
      Built graph: 87 classes, 432 methods, 1,248 edges
      
      Here's an overview:
      - 12 packages
      - 87 classes (6 abstract, 12 interfaces)
      - 432 methods
      - Deepest inheritance: 4 levels (BaseEntity -> UserEntity -> AdminUserEntity)
      
      Top-level packages: com.company.core, com.company.api, com.company.service

User: Show me the core classes

Vibe: [calls gausvibe:query --query "classes in package com.company.core" --format text]
      
      Core classes:
      - Application (main entry point)
      - Configuration (app config)
      - Database (DB connection)
      - EntityManager (entity management)
      - Cache (caching layer)

User: Who uses the Database class?

Vibe: [calls gausvibe:query --query "who calls Database.*"]
      
      Database is used by:
      - EntityManager (12 calls)
      - Cache (3 calls)
      - UserService (5 calls)
      - OrderService (8 calls)
```

### Example 2: Debugging Method Calls

```
User: The saveUser method is called from many places. Where is it called from?

Vibe: [calls gausvibe:query --query "getCallers(saveUser)"]
      
      saveUser is called from:
      1. UserController.createUser() - Line 42
      2. UserController.updateUser() - Line 87
      3. AdminService.createAdmin() - Line 23
      4. ImportService.importUsers() - Line 156 (in loop)
      5. Main.main() - Line 12 (initialization)

User: Show me the code at UserController.createUser line 42

Vibe: [calls query --query "getStatementAt(UserController.createUser, 42)" --include_source true]
      
      UserController.java:42
      ```java
      User user = new User(request.getName(), request.getEmail());
      userService.saveUser(user);  // <-- This line calls saveUser
      return Response.ok(user);
      ```
```

### Example 3: Understanding Inheritance

```
User: What's the class hierarchy for Payment?

Vibe: [calls gausvibe:query --query "getSuperclass(Payment) AND getSubclasses(Payment)"]
      
      Payment hierarchy:
      
      Payment (abstract)
      ├── CreditCardPayment
      │   ├── VisaPayment
      │   └── MastercardPayment
      ├── BankTransferPayment
      └── DigitalWalletPayment
          └── PayPalPayment

User: What methods does Payment define?

Vibe: [calls gausvibe:query --query "getMethods(Payment)"]
      
      Payment defines:
      - process() - abstract
      - validate() - abstract
      - getAmount() - returns BigDecimal
      - setAmount(BigDecimal) - void
      - isValid() - boolean

User: Which subclasses override process?

Vibe: [calls gausvibe:query --query "getOverriddenMethod(process)"]
      
      process() is overridden by:
      - CreditCardPayment.process()
      - BankTransferPayment.process()
      - DigitalWalletPayment.process()
```

---

## 🔍 TROUBLESHOOTING

### Common Issues

| Issue | Solution |
|-------|----------|
| Java not found | Set JAVA_HOME environment variable or config.json |
| Out of memory | Increase heap: `-Xmx2g` in config.json |
| Parse errors | Check syntax, exclude problematic files |
| Slow parsing | Reduce parallelism or exclude large directories |
| Cache full | Clear cache or increase max_cache_size_mb |

### Debug Mode

```bash
# Enable verbose logging
vibe tool gausvibe:build --path . --verbose

# This produces detailed logs to:
# ~/.vibe/logs/gausvibe/debug.log
```

---

## 📖 RELATED DOCUMENTATION

- [TASK_BREAKDOWN.md](../../TASK_BREAKDOWN.md) - Core project implementation details
- [DEBUGGER_INTEGRATION.md](../../DEBUGGER_INTEGRATION.md) - Differential debugging extension
- [LLM_INTEGRATION.md](../../LLM_INTEGRATION.md) - LLM integration scoping document

---

## 🤝 CONTRIBUTING

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a PR

### Contribution Areas

- New node types (annotations, modules, etc.)
- New query types (data flow, control flow)
- Performance optimizations
- Better natural language parsing
- MCP server enhancements

---

## 📜 LICENSE

Proprietary - All Rights Reserved. This software is the confidential and proprietary information of GausVibe. Use, reproduction, or distribution is permitted only with express written authorization.

---

## 🏷️ TAGS

`java` `code-analysis` `ast` `graph` `vibe` `skill` `mcp` `llm` `code-understanding`
