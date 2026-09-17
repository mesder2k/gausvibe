# LLM Integration Strategy

**Project:** GausVibe  
**Goal:** Connect structured Java code graph to LLM (Vibe)  
**Date:** 2026-09-17  
**Status:** Design Document - Scoping Phase

---

## 🎯 OVERVIEW

This document scopes how to connect GausVibe to an LLM, specifically Mistral's Vibe agent. The project's core purpose is enabling Vibe to understand Java code structure without relying on grep, so this integration is the primary delivery mechanism.

**Note:** Codex is OpenAI's code model, not Mistral's. Mistral's equivalent is Codestral.

---

## 📊 INTEGRATION OPTIONS

### Option 1: Vibe Skill (RECOMMENDED)

**What it is:** A discoverable Vibe capability with tools and optional MCP server.

**Why it fits:**
- Native Vibe integration
- Your project directly addresses a core Vibe limitation (understanding Java without grep)
- Clear API already exists (`JavaGraphQuery`)
- Serialization (JSON) works for tool I/O
- Phase 7 of TASK_BREAKDOWN.md is explicitly "Vibe Integration"

**Structure:**
```
skills/gausvibe/
├── SKILL.md              # Skill manifest, discoverability
├── src/main/java/...     # Existing GausVibe code
├── tools/
│   ├── build-graph       # Parse Java → graph JSON
│   └── query-graph       # Execute queries on graph
└── mcp/
    └── server.py         # Optional: real-time query endpoint
```

**Pros:**
- First-class Vibe citizen
- Discoverable via skill system
- Can expose both tools and MCP endpoints
- Persistent graph caching possible

**Cons:**
- Requires skill packaging
- Java <-> Vibe interop setup

---

### Option 2: Vibe Tool

**What it is:** Simple CLI executables that Vibe calls via bash.

**Structure:**
```bash
# Vibe calls these directly
gausvibe build --path /project --output graph.json
gausvibe query --graph graph.json --query "findClass(com.example.Calculator)"
```

**Pros:**
- Minimal setup
- Works with existing Maven project
- No Vibe-specific packaging

**Cons:**
- Process startup overhead for each call
- Less integrated experience
- No MCP support (no streaming, no real-time)

---

### Option 3: PR to Codex (DISMISSED)

**Status:** Not viable - Codex is OpenAI, not Mistral.

Mistral's code understanding efforts use Codestral and other internal projects. Contribution would need to target Mistral's actual code analysis infrastructure, not Codex.

---

## ✅ RECOMMENDATION: Vibe Skill

**Rationale:**

1. **Alignment with Project Goals:** Phase 7 of TASK_BREAKDOWN.md is "Vibe Integration" - this is the intended delivery
2. **Architecture Readiness:** `JavaGraphQuery` interface is already the LLM-facing API
3. **Vibe's Design:** Skills are the mechanism for extending Vibe's capabilities
4. **Future-Proof:** MCP server enables real-time interaction for large codebases

---

## 🏗️ PROPOSED SKILL STRUCTURE

### File Layout

```
skills/gausvibe/
├── SKILL.md
├── package.json          # Vibe skill metadata
├── .vibe/
│   └── skill_config.json
├── src/
│   └── main/java/...     # Existing code (GausVibe)
├── tools/
│   ├── build-graph
│   │   ├── tool.yaml
│   │   └── execute.py    # Orchestrates Java execution
│   └── query-graph
│       ├── tool.yaml
│       └── execute.py
└── mcp/
    ├── server.py
    └── openapi.yaml
```

### SKILL.md Content Outline

```markdown
# GausVibe Skill

**ID:** gausvibe
**Version:** 1.0.0
**Description:** Build and query structured graphs of Java code for code understanding without grep

## Capabilities

- Parse Java projects into structured graphs
- Query graph for classes, methods, call hierarchies, inheritance
- Support for large codebases with serialization
- Real-time queries via MCP server

## Tools

### build-graph
Build a code graph from a Java project.

**Parameters:**
- `path` (required): Path to Java project root or source directory
- `output` (optional): Output file for graph JSON (default: auto-generated)
- `include_test` (optional): Include test sources (default: true)
- `exclude` (optional): Glob patterns to exclude

**Returns:** Graph JSON with nodes, edges, indexes

### query-graph
Execute a query on a code graph.

**Parameters:**
- `graph` (required): Path to graph JSON file
- `query` (required): Query string (see Query DSL below)
- `format` (optional): Output format (json, text, summary) (default: json)

**Returns:** Query results in specified format

## MCP Server

Exposes real-time graph operations:
- `get_nodes` - Retrieve nodes by type, file, or ID
- `get_edges` - Retrieve edges by type or between nodes
- `query` - Execute a query
- `get_schema` - Get node/edge type definitions

## Query DSL

Natural language queries mapped to JavaGraphQuery:
- "find class Calculator" → findClassByQualifiedName
- "methods in Calculator" → getMethods
- "who calls add()" → getCallers
- "what does main() call" → getCallees
- "subclasses of Animal" → getSubclasses
```

---

## 🔧 QUERY DSL DESIGN

### Natural Language → API Mapping

| User Query | API Method | Parameters |
|------------|------------|------------|
| "find class com.example.Foo" | findClassByQualifiedName | qn="com.example.Foo" |
| "find classes named Calculator" | findClassesByName | name="Calculator" |
| "all classes" | getAllClasses | - |
| "subclasses of Animal" | getSubclasses | classNode |
| "methods in Calculator" | getMethods | classNode |
| "find method add(int,int)" | findMethodBySignature | sig="add(int,int)" |
| "methods named add" | findMethodsByName | name="add" |
| "who calls add()" | getCallers | methodNode |
| "what does main() call" | getCallees | methodNode |
| "fields in Calculator" | getFields | classNode |
| "variables in add()" | getVariables | methodNode |

### Query Processing Flow

```
User Query → NLP Parsing → API Call → Graph Query → Results → Formatting
```

For MVP: Simple pattern matching (regex-based)  
For Future: Use LLM to parse queries into structured requests

---

## 📋 IMPLEMENTATION PHASES

### Phase 7A: Skill Scaffolding (1-2 days)
- [ ] Create skills/gausvibe/ directory structure
- [ ] Draft SKILL.md
- [ ] Define tool.yaml for build-graph
- [ ] Define tool.yaml for query-graph
- [ ] Create execute.py wrappers

### Phase 7B: Build Tool Implementation (2-3 days)
- [ ] Package existing GausVibeBuilder as executable
- [ ] Handle tool parameters (path, output, exclusions)
- [ ] JSON output format validation
- [ ] Error handling for invalid Java projects

### Phase 7C: Query Tool Implementation (3-5 days)
- [ ] Implement query parser (natural language → API calls)
- [ ] Handle query-graph parameters
- [ ] Result formatting (json, text, summary)
- [ ] Error handling for invalid queries

### Phase 7D: MCP Server (Optional, 3-5 days)
- [ ] Implement MCP server in Python
- [ ] Expose graph operations as MCP tools
- [ ] Add streaming support for large results
- [ ] Add subscription for graph changes

### Phase 7E: Integration Testing (2-3 days)
- [ ] Test with sample projects (Calculator, etc.)
- [ ] Validate query accuracy
- [ ] Performance testing
- [ ] Document examples

---

## 💡 EXAMPLE USAGE

### Via Tools (CLI)

```bash
# Build graph for a project
vibe tool gausvibe:build --path /path/to/project --output graph.json

# Query the graph
vibe tool gausvibe:query --graph graph.json --query "find class com.example.Calculator"
```

### Via MCP (Real-time)

```json
{
  "method": "tools/call",
  "params": {
    "name": "gausvibe:query",
    "arguments": {
      "query": "who calls Calculator.add()",
      "graph": "cache://project-graph"
    }
  }
}
```

### Via Vibe Conversation

```
User: I'm looking at a Java project and want to understand the Calculator class.

Vibe: [calls gausvibe:build]
       [calls gausvibe:query --query "getMethods(Calculator)"]
       
Vibe: The Calculator class has 4 methods:
      - add(int, int): int
      - subtract(int, int): int
      - multiply(int, int): int
      - divide(int, int): int
      
      The add() method is called by: Main.main(), TestCalculator.testAdd()

User: Who calls the add method?

Vibe: [calls gausvibe:query --query "getCallers(add)"]
       
Vibe: The add() method is called by:
      - Main.main() at line 42
      - TestCalculator.testAdd() at line 15
```

---

## ⚙️ TECHNICAL CONSIDERATIONS

### Java Execution
- Tools need to execute Java code (Maven project)
- Options:
  1. Pre-built JAR distribution
  2. Execute via `mvn exec:java`
  3. Native packaging with jlink

**Recommendation:** Pre-built JAR for tool execution, with Maven for development.

### Graph Caching
- Build graph once, query many times
- Cache key: project path + file hashes
- Cache location: `.vibe/cache/gausvibe/`
- Invalidation: On file change or explicit rebuild

### Performance
- Large projects: Consider lazy loading or partial graphs
- Query optimization: Use existing indexes from Graph class
- Parallel parsing: Already supported in GausVibeBuilder

### Error Handling
- Invalid Java syntax: Return partial graph + warnings
- Missing files: Skip with warning
- Out of memory: Configurable limits, graceful degradation

---

## 📚 RELATED DOCUMENTS

- [TASK_BREAKDOWN.md](./TASK_BREAKDOWN.md) - Core project implementation
- [DEBUGGER_INTEGRATION.md](./DEBUGGER_INTEGRATION.md) - Differential debugging extension

---

## ✅ DECISION RECORD

**Decision:** Proceed with Vibe Skill approach  
**Rationale:** Native integration, aligns with Phase 7, enables both tools and MCP  
**Alternatives Considered:** Vibe Tool (too simple), Codex PR (wrong project - Codex is OpenAI, not Mistral)  
**Next Action:** Create skill scaffolding and tool definitions

---

## 📋 IMPLEMENTATION STATUS

### Completed (Phase 7A)
- [x] Create skills/gausvibe/ directory structure
- [x] Draft SKILL.md with full documentation
- [x] Define tool.yaml for build-graph tool
- [x] Define tool.yaml for query-graph tool
- [x] Create execute.py wrapper for build-graph
- [x] Create execute.py wrapper for query-graph
- [x] Implement QueryParser with natural language patterns
- [x] Implement QueryExecutor with all query handlers
- [x] Create MCP server skeleton (server.py)
- [x] Create MCP requirements.txt
- [x] Create skill README.md

### Next Steps (Phase 7B-7E)
- [ ] Package existing GausVibeBuilder as executable JAR
- [ ] Add Main.java with CLI entry point for build operation
- [ ] Test tools with sample Java projects
- [ ] Complete MCP server implementation
- [ ] Integration testing with Vibe
- [ ] Performance testing and optimization

### Files Created
```
skills/gausvibe/
├── SKILL.md              # Full skill documentation
├── README.md             # Quick start and development guide
├── tools/
│   ├── build-graph/
│   │   ├── tool.yaml      # Build tool definition
│   │   └── execute.py    # Build execution wrapper
│   └── query-graph/
│       ├── tool.yaml      # Query tool definition
│       └── execute.py    # Query execution with NLP parser
└── mcp/
    ├── server.py         # MCP server with 8 tools
    └── requirements.txt   # Python dependencies
```

### Key Implementation Details

**QueryParser (Natural Language → API):**
- 15+ regex patterns for natural language queries
- Supports both natural language ("who calls add()") and structured queries ("findClassByQualifiedName(qn=\"X\")")
- Extensible pattern list for new query types

**QueryExecutor:**
- Builds in-memory indexes from graph JSON for fast lookups
- Implements all JavaGraphQuery API methods
- Supports CLASS, METHOD, FIELD, VARIABLE, CALL, INHERITANCE result types
- Multiple output formats: json, text, summary

**Build Tool:**
- File-based caching with modification time tracking
- Parallel parsing support (configurable)
- Error handling with warnings collection
- Java 17+ dependency with memory management

**MCP Server:**
- 8 tools: query, get_nodes, get_edges, get_schema, get_stats, load_graph, list_graphs, unload_graph
- Graph caching for multiple loaded graphs
- Async/await support for non-blocking operations
