# GausVibe REST Server

## Quick Start

### 1. Start the Server

The graph is built **automatically** on startup - no manual build step needed.

```bash
# Install dependencies (one-time)
pip install fastapi uvicorn

# Start server, pointing to a directory to index
python3 gausvibe_server.py --project ../bakeoff1 --port 8080

# Or for GausVibe itself
python3 gausvibe_server.py --project . --port 8080
```

### 2. Use cURL immediately

The server is ready to answer queries as soon as it starts:
```bash
# List all classes
curl http://localhost:8080/classes

# Browse interactive docs
open http://localhost:8080/docs

# Get Calculator implementations
curl http://localhost:8080/classes/com.example.calculator.Calculator/implementations

# Search for add methods
curl http://localhost:8080/search?q=add

# Raw query
curl "http://localhost:8080/graph/query?q=class:all"

# Get statistics
curl http://localhost:8080/graph/stats
```

## Overview

The GausVibe REST Server provides a simple HTTP interface to query Java code graphs. **The graph is built automatically when the server starts** - callers don't need to worry about graph construction.

### Key Design
- **Start server** with `--project` pointing to Java source directory
- **Graph builds on startup** - server waits until graph is ready
- **All endpoints available immediately** after startup
- **No manual build step** required from API consumers

## Command Line Arguments

```
--project PATH   (REQUIRED)  Java project directory to index
--port NUM      (default: 8080) Port to listen on
--host STR      (default: 0.0.0.0) Host to bind to
--graph-file PATH           Custom graph file location (default: ~/.vibe/cache/gausvibe/graph_<project>.json)
```

## Examples

### Start server with your projects
```bash
# Calculator example
python3 gausvibe_server.py --project ../bakeoff1 --port 8080

# GausVibe itself
python3 gausvibe_server.py --project . --port 8081

# Another project
python3 gausvibe_server.py --project /path/to/my/java/project --port 8082
```

### Query examples

```bash
# List all classes
curl http://localhost:8080/classes

# Get details about a specific class
curl http://localhost:8080/classes/com.example.calculator.StandardCalculator

# List methods of a class
curl http://localhost:8080/classes/com.example.calculator.StandardCalculator/methods

# Find subclasses of a class
curl http://localhost:8080/classes/java.lang.Object/subclasses

# Find implementations of an interface
curl http://localhost:8080/classes/com.example.calculator.Calculator/implementations

# List all methods
curl http://localhost:8080/methods

# List all packages
curl http://localhost:8080/packages

# Search by name (finds classes or methods matching "add")
curl http://localhost:8080/search?q=add

# Execute arbitrary GausVibe query
curl "http://localhost:8080/graph/query?q=class:all"
curl "http://localhost:8080/graph/query?q=method:name:add"
curl "http://localhost:8080/graph/query?q=class:subclasses:java.lang.Object"

# Get graph statistics
curl http://localhost:8080/graph/stats

# Get server info
curl http://localhost:8080/
```

## Response Formats

All endpoints return JSON.

### GET /
```json
{
  "name": "GausVibe Server",
  "version": "1.0.0",
  "current_graph": {
    "project": "/path/to/project",
    "file": "/path/to/graph.json",
    "nodes": 150,
    "edges": 300,
    "built_at": 1699999999.999
  },
  "endpoints": { ... }
}
```

### GET /classes
```json
{
  "count": 5,
  "classes": [
    "com.example.calculator.Calculator",
    "com.example.calculator.StandardCalculator",
    "com.example.calculator.Main"
  ]
}
```

### GET /classes/com.example.calculator.StandardCalculator
```json
{
  "fqn": "com.example.calculator.StandardCalculator",
  "name": "StandardCalculator",
  "simple_name": "StandardCalculator",
  "file": "/path/to/StandardCalculator.java",
  "superclass": "java.lang.Object",
  "interfaces": ["com.example.calculator.Calculator"],
  "method_count": 10,
  "field_count": 3,
  "raw": "Class: com.example.calculator.StandardCalculator..."
}
```

### GET /classes/com.example.calculator.StandardCalculator/methods
```json
{
  "class": "com.example.calculator.StandardCalculator",
  "count": 10,
  "methods": [
    "public double add(double, double)",
    "public double subtract(double, double)",
    "..."
  ]
}
```

### GET /search?q=add
```json
{
  "type": "method",
  "query": "add",
  "count": 3,
  "results": [
    "public double add(double, double)"
  ]
}
```

### GET /graph/query?q=class:all
```json
{
  "query": "class:all",
  "result": "Classes (5):\n  - com.example.Calculator\n  - ...",
  "nodes": 150,
  "edges": 300
}
```

### GET /graph/stats
```json
{
  "project": "/path/to/project",
  "graph_file": "/path/to/graph.json",
  "nodes": 150,
  "edges": 300,
  "built_at": 1699999999.999
}
```

## Error Responses

```json
{
  "error": "Class not found: com.example.NonExistent"
}
```

## Endpoints Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Server info and current graph status |
| GET | `/graph/query?q=QUERY` | Execute arbitrary GausVibe query |
| GET | `/graph/stats` | Graph statistics (nodes, edges) |
| GET | `/classes` | List all classes |
| GET | `/classes/<fqn>` | Get class details |
| GET | `/classes/<fqn>/methods` | List methods of a class |
| GET | `/classes/<fqn>/subclasses` | List subclasses |
| GET | `/classes/<fqn>/implementations` | List interface implementations |
| GET | `/methods` | List all methods |
| GET | `/packages` | List all packages |
| GET | `/search?q=NAME` | Search by name |

## Query Language

GausVibe supports these query types (use in `/graph/query` or via dedicated endpoints):

| Query Type | Example | Description |
|------------|---------|-------------|
| `class:all` | `class:all` | List all classes |
| `class:FQN` | `class:com.example.MyClass` | Get specific class |
| `class:name:NAME` | `class:name:Calculator` | Find by simple name |
| `class:implementations:FQN` | `class:implementations:com.example.Interface` | Get implementations |
| `class:subclasses:FQN` | `class:subclasses:java.lang.Object` | Get subclasses |
| `class:superclass:FQN` | `class:superclass:com.example.MyClass` | Get superclass |
| `class:interfaces:FQN` | `class:interfaces:com.example.MyClass` | Get interfaces |
| `method:all` | `method:all` | List all methods |
| `method:class:FQN` | `method:class:com.example.MyClass` | Methods of class |
| `method:name:NAME` | `method:name:add` | Find by name |
| `method:signature:SIG` | `method:signature:public void method()` | Find by signature |
| `field:all` | `field:all` | List all fields |
| `field:class:FQN` | `field:class:com.example.MyClass` | Fields of class |
| `package:all` | `package:all` | List all packages |
| `package:NAME` | `package:com.example` | Get specific package |
| `search:nodes:TYPE` | `search:nodes:CLASS` | Search nodes by type |
| `search:edges:TYPE` | `search:edges:CALLS` | Search edges by type |
| `stats` | `stats` | Graph statistics |

## Running Multiple Servers

Run multiple servers on different ports for different projects:

```bash
# Terminal 1: Calculator project
python gausvibe_server.py --project ../bakeoff1 --port 8080 &

# Terminal 2: GausVibe project  
python gausvibe_server.py --project . --port 8081 &

# Terminal 3: Query both
curl http://localhost:8080/classes        # Calculator classes
curl http://localhost:8081/classes        # GausVibe classes
```

## Startup Behavior

When you start the server:

1. It **requires** a `--project` parameter (will error if missing)
2. It **automatically** builds the graph for that project
3. It **caches** the classpath from Maven (only computed once)
4. It **prints** the graph statistics on startup
5. It **listens** for requests on the specified port

Example startup output:
```
Building graph for: /Users/magnusfind/Documents/find-shadow-model/bakeoff1
Graph built: 15 nodes, 25 edges

============================================================
GausVibe Server (FastAPI) Running
Project:  /Users/magnusfind/Documents/find-shadow-model/bakeoff1
Graph:    /Users/magnusfind/.vibe/cache/gausvibe/graph_bakeoff1.json
Nodes:    15
Edges:    25
URL:      http://0.0.0.0:8080
Docs:     http://0.0.0.0:8080/docs
============================================================
```

## Dependencies

- Python 3.7+
- FastAPI: `pip install fastapi uvicorn`
- GausVibe: Already compiled in the project
- Maven: For classpath resolution

## Features

- **FastAPI** - Modern, fast, async-ready
- **Auto-generated docs** at `/docs` and `/redoc`
- **Type hints** for better IDE support
- **CORS enabled** for browser access
- **Automatic graph building** on startup

## Performance

- Graph building: ~1-5 seconds for medium projects
- Query execution: ~10-100ms per query
- Memory: Holds graph in memory for fast repeated queries
- Graph caching: Graph file stored in `~/.vibe/cache/gausvibe/` by default

## Custom Graph Location

Use `--graph-file` to specify where the graph JSON should be stored:

```bash
python gausvibe_server.py --project ../bakeoff1 --graph-file /tmp/my-graph.json
```

## Troubleshooting

**Error: "No graph loaded"**
- Make sure you specified `--project` on startup
- Check the project directory exists

**Error: "Project directory not found"**
- Verify the path is correct
- Use absolute paths if having issues

**Error: "Failed to get classpath"**
- Run `mvn compile` in the GausVibe directory first
- Ensure Maven is installed and in PATH

**Server doesn't start**
- Check Python and Flask are installed: `python -c "import flask; print(flask.__version__)"`
- Check port is available: `lsof -i :8080`

## Security Considerations

For production use, consider adding:
- Authentication (currently open to all)
- Rate limiting
- HTTPS support
- Input validation
- CORS restrictions

## Development

To modify the server:
1. Edit `gausvibe_server.py`
2. Restart the server
3. Changes take effect immediately

The server uses Flask's development mode by default (debug=False in production).
