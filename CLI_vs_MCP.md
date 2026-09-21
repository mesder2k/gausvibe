# GausVibe CLI vs MCP: Comparison Guide

## Overview

GausVibe provides **three distinct interfaces** for Java code analysis, each with different use cases:

---

## 📋 Comparison Matrix

| Feature | **Java CLI** | **Vibe Skill (Tools)** | **MCP Server** |
|---------|--------------|------------------------|----------------|
| **Interface** | Command-line Java app | Vibe tool calls | MCP protocol (stdio) |
| **Language** | Java | Python (wrapper) | Python |
| **Start Time** | ~1-2 seconds | ~1-2 seconds | ~2-3 seconds |
| **Use Case** | Direct usage, scripting | Vibe CLI integration | LLM assistant integration |
| **Query Syntax** | Structured queries | Natural language OR structured | Natural language OR structured |
| **Graph Loading** | Automatic from project | Requires pre-built graph | Requires pre-built graph |
| **Persistence** | Can serialize to JSON | Works with JSON files | Works with JSON files |
| **Best For** | Scripting, automation | Vibe CLI users | AI assistants (Cursor, etc.) |

---

## 🎯 Java CLI (Command-Line Interface)

The **native Java application** with direct execution.

### Usage

```bash
# Build graph from project
java -cp "target/classes:$CLASSPATH" dk.gausdalfind.Main \
  build --project /path/to/java --output graph.json --serialize

# Query the graph
java -cp "target/classes:$CLASSPATH" dk.gausdalfind.Main \
  query --graph graph.json "class:all"

# Interactive shell
java -cp "target/classes:$CLASSPATH" dk.gausdalfind.Main interactive
```

### Supported Commands

| Command | Description | Example |
|---------|-------------|---------|
| `build` | Build graph from Java source | `build --project ./src --output graph.json` |
| `query` | Execute query on graph | `query --graph graph.json "class:all"` |
| `edit` | Apply AST edits to graph | `edit --graph graph.json --operations '[...]'` |
| `interactive` | Start REPL shell | `interactive` |

### Supported Queries

**Class Queries:**
```
class:all                           # All classes
class:com.example.Calculator         # Class by FQN
class:name:Calculator               # Classes by name
class:subclasses:com.example.Animal # Subclasses
class:implementations:Calculator     # Interface implementations
class:superclass:com.example.Dog    # Superclass
class:interfaces:com.example.MyClass # Implemented interfaces
```

**Method Queries:**
```
method:all                          # All methods
method:class:com.example.Calculator # Methods in class
method:name:add                    # Methods named 'add'
method:signature:add(int,int)     # Method by signature
```

**Field Queries:**
```
field:all                          # All fields
field:class:com.example.MyClass    # Fields in class
field:name:count                   # Fields by name
```

**Package Queries:**
```
package:all                        # All packages
package:com.example                # Specific package
package:classes:com.example        # Classes in package
```

**File Queries:**
```
file:src/main/java/MyClass.java    # Nodes in file
```

**Search Queries:**
```
search:nodes:CLASS                # Nodes by type
search:edges:CALLS                # Edges by type
search:modifier:public            # Nodes with modifier
```

**Statistics:**
```
stats                              # Total nodes and edges
```

### Pros & Cons

**✅ Pros:**
- Fastest execution (native Java)
- Full control over graph building
- Can build graph on-the-fly
- Interactive shell for exploration
- Good for scripting and automation

**❌ Cons:**
- Requires Java 17+ and Maven
- Requires manual classpath setup
- Must remember query syntax
- No natural language understanding

### When to Use
- ✅ Batch processing of codebases
- ✅ Automated builds in CI/CD
- ✅ Scripting and integration with other tools
- ✅ Exploring code structure interactively

---

## 🤖 Vibe Skill (Tool-based)

**Vibe-integrated tools** that use Python wrappers around the Java CLI.

### Usage

```bash
# Build graph (uses Java CLI under the hood)
vibe tool gausvibe:build --path /path/to/java --output graph.json

# Query graph (natural language or structured)
vibe tool gausvibe:query --graph graph.json --query "who calls add()"

# With natural language
vibe tool gausvibe:query --graph graph.json --query "find all methods in Calculator"

# With structured query
vibe tool gausvibe:query --graph graph.json --query "findClassByQualifiedName(qn='com.example.Calculator')"
```

### Natural Language Support

The query tool supports **natural language queries** that are automatically converted to structured queries:

| Natural Language | Structured Query |
|-------------------|------------------|
| `find class com.example.Calculator` | `findClassByQualifiedName(qn="com.example.Calculator")` |
| `methods in Calculator` | `getMethods(className="Calculator")` |
| `who calls add()` | `getCallers(methodName="add()")` |
| `subclasses of Animal` | `getSubclasses(className="Animal")` |
| `what implements Calculator` | `getImplementations(interfaceName="Calculator")` |
| `what does Main call` | `getCallees(methodName="Main")` |

### Tool Definitions

**`gausvibe:build`** - Build graph from Java source
- Parameters: `path`, `output`, `include_test`, `exclude`, `parallel`, `verbose`
- Returns: Complete graph JSON with metadata

**`gausvibe:query`** - Query a graph
- Parameters: `graph`, `query`, `format` (json/text/summary), `limit`, `include_source`
- Returns: Query results in specified format

### Pros & Cons

**✅ Pros:**
- Integrated with Vibe CLI
- Natural language query support
- Consistent with other Vibe tools
- Works in Vibe scripts
- JSON or text output formats

**❌ Cons:**
- Requires Vibe installation
- Slight overhead from Python wrapper
- Graph must be pre-built (can't build on-the-fly in queries)

### When to Use
- ✅ Using GausVibe within Vibe workflows
- ✅ Prefer natural language queries
- ✅ Need JSON or text output for other tools
- ✅ Scripting with Vibe CLI

---

## 🌐 MCP Server (Model Context Protocol)

**Long-running server** that provides GausVibe functionality via MCP protocol.

### What is MCP?

The **Model Context Protocol** is an open standard for connecting AI models to external tools and data sources. It allows AI assistants (like Vibe, Cursor, etc.) to:
- Discover available tools
- Call tools with structured arguments
- Receive structured responses
- Stream results in real-time

### Usage

```bash
# Start the server
cd gausvibe/skills/gausvibe/mcp
python server.py --graph /path/to/graph.json --port 8080

# Connect from MCP client (e.g., Vibe, Cursor)
# Client will automatically discover and use these tools:
# - query
# - get_nodes
# - get_edges
# - get_schema
# - get_stats
# - load_graph
# - list_graphs
# - unload_graph
```

### Available MCP Tools

| Tool | Description | Parameters |
|------|-------------|------------|
| `query` | Execute a query on a loaded graph | `graph`, `query`, `format`, `limit` |
| `get_nodes` | Get nodes by type, file, or ID | `graph`, `node_type`, `node_id`, `file`, `name`, `limit` |
| `get_edges` | Get edges by type or between nodes | `graph`, `edge_type`, `from_id`, `to_id`, `limit` |
| `get_schema` | Get node/edge type definitions | - |
| `get_stats` | Get graph statistics | `graph` |
| `load_graph` | Load a graph from JSON file | `path` |
| `list_graphs` | List all loaded graphs | - |
| `unload_graph` | Unload a graph from memory | `graph` |

### Supported Queries via MCP

Same as Vibe Skill, but through MCP protocol:

```python
# Example MCP client usage (pseudo-code)
tools = mcp_client.list_tools()
result = mcp_client.call_tool("query", {
    "graph": "/path/to/graph.json",
    "query": "who calls save()",
    "format": "json"
})
```

### Pros & Cons

**✅ Pros:**
- Works with **any MCP-compatible client** (Vibe, Cursor, etc.)
- Real-time interaction during LLM sessions
- AI can automatically discover and use tools
- No need to manually call commands
- Graph loaded once, queried many times

**❌ Cons:**
- Requires MCP-compatible client
- Server must be running separately
- Graph must be pre-built
- More complex setup

### When to Use
- ✅ Using with AI assistants that support MCP (Vibe, Cursor, etc.)
- ✅ Want AI to automatically discover and use GausVibe
- ✅ Need real-time graph querying during coding sessions
- ✅ Using multiple MCP servers/tools together

---

## 📊 Which One Should You Use?

### Decision Flowchart

```
Do you use Vibe CLI?
    │
    ├─ Yes
    │   │
    │   Do you want natural language queries?
    │       │
    │       ├─ Yes → Use **Vibe Skill (gausvibe:build, gausvibe:query)**
    │       │
    │       └─ No → Use **Java CLI** directly
    │
    └─ No
        │
        Do you use an MCP-compatible AI assistant?
            │
            ├─ Yes → Use **MCP Server**
            │
            └─ No → Use **Java CLI** directly
```

### Use Case Recommendations

| Scenario | Recommended Interface | Why |
|----------|----------------------|-----|
| Quick code exploration | Java CLI interactive mode | Fast, no setup |
| Scripting/automation | Java CLI | Direct, fast, scriptable |
| Vibe CLI user | Vibe Skill | Natural integration |
| Cursor user | MCP Server | Native MCP support |
| Any MCP client | MCP Server | Universal MCP compatibility |
| Batch processing | Java CLI | Fastest for many files |
| AI-assisted coding | MCP Server | AI can auto-discover tools |

---

## 🔧 Setup Instructions

### Java CLI

```bash
# Prerequisites
- Java 17+
- Maven

# Build
cd gausvibe
mvn clean compile

# Get classpath
mvn dependency:build-classpath -Dmdep.outputFile=/tmp/classpath.txt
CP=$(cat /tmp/classpath.txt)

# Run
java -cp "target/classes:$CP" dk.gausdalfind.Main [command] [options]
```

### Vibe Skill

```bash
# Prerequisites
- Vibe CLI installed
- Java 17+, Maven, Python 3.8+

# Build Java component
cd gausvibe
mvn clean package

# Use tools
vibe tool gausvibe:build --path ./my-project
vibe tool gausvibe:query --graph graph.json --query "all classes"
```

### MCP Server

```bash
# Prerequisites
- Python 3.8+
- pip install -r gausvibe/skills/gausvibe/mcp/requirements.txt
- Java 17+, Maven (for building Java component)

# Build Java component
cd gausvibe
mvn clean package

# Start server
cd gausvibe/skills/gausvibe/mcp
python server.py --graph /path/to/graph.json --port 8080

# Connect from MCP client
# (Client-specific setup)
```

---

## 📝 Example Workflows

### Workflow 1: Analyze a New Project (Java CLI)

```bash
# Build graph
java -cp "target/classes:$CP" dk.gausdalfind.Main \
  build --project /path/to/project --output project-graph.json --serialize

# Explore classes
java -cp "target/classes:$CP" dk.gausdalfind.Main \
  query --graph project-graph.json "class:all"

# Find method callers
java -cp "target/classes:$CP" dk.gausdalfind.Main \
  query --graph project-graph.json "method:class:com.example.Service"

# Interactive exploration
java -cp "target/classes:$CP" dk.gausdalfind.Main interactive
```

### Workflow 2: Vibe CLI Usage

```bash
# Build graph
vibe tool gausvibe:build --path ./my-project --output my-graph.json

# Query with natural language
vibe tool gausvibe:query --graph my-graph.json --query "who calls the save method"

# Query with structured format
vibe tool gausvibe:query --graph my-graph.json --query "method:class:com.example.UserService" --format text
```

### Workflow 3: MCP with AI Assistant

```bash
# Terminal 1: Start server
cd gausvibe/skills/gausvibe/mcp
python server.py --graph /path/to/graph.json

# Terminal 2: Use with MCP client
# Open your MCP-compatible editor (Cursor, etc.)
# It will automatically connect to the MCP server
# You can then ask: "What classes implement the Calculator interface?"
# The AI will use GausVibe MCP tools to answer
```

---

## 🔄 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        GAUSVIBE                               │
├─────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌───────────┐ │
│  │  Java CLI       │    │  Vibe Skill     │    │ MCP Server│ │
│  │                 │    │                 │    │           │ │
│  │ Main.java       │    │ execute.py      │    │ server.py │ │
│  │ CommandLine...  │    │ tool.yaml       │    │           │ │
│  │                 │    │                 │    │           │ │
│  └────────┬────────┘    └────────┬────────┘    └─────┬─────┘ │
│           │                      │                   │          │
│           └──────────────────────┼───────────────────┘          │
│                                  │                                │
│                         ┌────────▼────────┐                          │
│                         │  Java Core       │                          │
│                         │  (gausvibe.jar)  │                          │
│                         │                 │                          │
│                         │ - Graph building │                          │
│                         │ - Query engine   │                          │
│                         │ - AST parsing    │                          │
│                         │ - Serialization  │                          │
│                         └────────┬────────┘                          │
│                                  │                                │
│                         ┌────────▼────────┐                          │
│                         │  Graph JSON      │                          │
│                         │  (Standard format)│                          │
│                         └─────────────────┘                          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
              ┌─────────────────────────────────────┐
              │            User / Client              │
              ├─────────────────────────────────────┤
              │ - Terminal (Java CLI)                 │
              │ - Vibe CLI (Vibe Skill)              │
              │ - AI Assistant (MCP Server)           │
              └─────────────────────────────────────┘
```

---

## 🎯 Summary: Which to Use When

| Interface | Best For | Learning Curve | Setup Complexity | Speed |
|-----------|----------|----------------|------------------|-------|
| **Java CLI** | Scripting, automation, direct use | Medium | Low | ⚡ Fastest |
| **Vibe Skill** | Vibe users, natural language | Low | Low | ⚡ Fast |
| **MCP Server** | AI assistants, MCP clients | Low | Medium | ⚡ Fast |

### Recommendation

1. **If you use Vibe CLI**: Start with **Vibe Skill** - it's the easiest integration
2. **If you need automation**: Use **Java CLI** - fastest and most flexible
3. **If you use an AI assistant**: Use **MCP Server** - best AI integration

All three use the **same underlying Java code** and **same graph format**, so you can switch between them freely!

---

## 📚 Documentation

- **Java CLI**: See `CommandLineInterface.java` for full command reference
- **Vibe Skill**: See `skills/gausvibe/tools/*.yaml` and `execute.py` files
- **MCP Server**: See `skills/gausvibe/mcp/server.py`
