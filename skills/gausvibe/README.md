# GausVibe Vibe Skill

This is a Vibe skill that enables LLMs to understand Java code structure without grep.

## 📁 Structure

```
skills/gausvibe/
├── README.md              # This file
├── SKILL.md              # Skill manifest and documentation
├── tools/
│   ├── build-graph/
│   │   ├── tool.yaml      # Tool definition
│   │   └── execute.py    # Python execution wrapper
│   └── query-graph/
│       ├── tool.yaml      # Tool definition
│       └── execute.py    # Python execution wrapper
└── mcp/
    ├── server.py         # MCP server implementation
    └── requirements.txt   # Python dependencies
```

## 🚀 Quick Start

### Prerequisites

- Java 17+ (for running GausVibe)
- Maven (for building from source)
- Python 3.8+ (for MCP server)

### Build the Java Component

```bash
cd skills/gausvibe
mvn clean package
```

This creates the JAR files that the skill uses.

### Install MCP Dependencies

```bash
pip install -r mcp/requirements.txt
```

### Usage

#### Build a Graph

```bash
vibe tool gausvibe:build --path /path/to/java/project
```

This creates a structured graph of the Java codebase.

#### Query the Graph

```bash
vibe tool gausvibe:query --graph /path/to/graph.json --query "who calls add()"
```

## 📚 Documentation

- [SKILL.md](./SKILL.md) - Complete skill documentation
- [../../TASK_BREAKDOWN.md](../../TASK_BREAKDOWN.md) - Project implementation breakdown
- [../../DEBUGGER_INTEGRATION.md](../../DEBUGGER_INTEGRATION.md) - Debugger extension design
- [../../LLM_INTEGRATION.md](../../LLM_INTEGRATION.md) - LLM integration scoping

## 🛠️ Development

### Adding New Query Types

1. Add pattern to `QueryParser.PATTERNS` in `tools/query-graph/execute.py`
2. Add handler method to `QueryExecutor` class
3. Test with sample queries

### Testing

```bash
# Test build tool
python tools/build-graph/execute.py --path /path/to/java/files

# Test query tool
python tools/query-graph/execute.py --graph /path/to/graph.json --query "all classes"
```

## 🎯 Next Steps

1. **Implement Java Main class** with CLI for building graphs
2. **Complete Phase 6** (Query API implementation)
3. **Test with real Java projects**
4. **Package as distributable skill**

## 📜 License

MIT License
