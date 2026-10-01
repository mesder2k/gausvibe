# GausVibe Pure Java REST Server

**Native Java implementation - Zero external dependencies (uses JDK HttpServer)**

## Quick Start

### 1. Build the shaded JAR
```bash
./scripts/build-artifact.sh
```
Produces `target/gausvibe-<version>-all.jar` (all dependencies included).
The version is the git commit count, embedded in the jar manifest and shown
by `java -jar target/gausvibe-<version>-all.jar version`.
To install it as the Vibe runtime artifact: `./scripts/build-artifact.sh --install`.

### 2. Start the Server

```bash
# Start server with project to index
java -jar target/gausvibe-<version>-all.jar server \
  --project ../bakeoff1 \
  --port 8080

# Or for GausVibe itself
java -jar target/gausvibe-<version>-all.jar server \
  --project . \
  --port 8080
```

### 3. Use cURL immediately

The graph builds **automatically** on startup:
```bash
# List all classes
curl http://localhost:8080/classes

# Get Calculator implementations
curl http://localhost:8080/classes/com.example.calculator.Calculator/implementations

# Search for add methods
curl http://localhost:8080/search?q=add

# Raw query
curl "http://localhost:8080/query?q=class:all"

# Get statistics
curl http://localhost:8080/graph/stats
```

## Why Pure Java?

| Approach | Pros | Cons |
|----------|------|------|
| **Pure Java (this)** | ✅ Zero deps, ✅ Native, ✅ Fast, ✅ Single process | ⚠️ JDK HttpServer is basic |
| Python wrapper | ⚠️ Extra dep, ⚠️ Process spawning, ⚠️ Slower | ✅ FastAPI is nice |

**Winner: Pure Java** - It's a Java project, so the server should be Java.

## Command Line Arguments

```
--project PATH   (REQUIRED)  Java project directory to index
--port NUM      (default: 8080) Port to listen on
```

## Examples

### Start server with different projects
```bash
# Calculator example on port 8080
java -jar target/gausvibe-<version>-all.jar server --project ../bakeoff1 --port 8080 &

# GausVibe itself on port 8081
java -jar target/gausvibe-<version>-all.jar server --project . --port 8081 &
```

### Query examples

```bash
# Server info
curl http://localhost:8080/

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
curl "http://localhost:8080/query?q=class:all"
curl "http://localhost:8080/query?q=method:name:add"
curl "http://localhost:8080/query?q=class:subclasses:java.lang.Object"

# Get graph statistics
curl http://localhost:8080/stats
```

## Response Formats

All endpoints return JSON.

### GET /
```json
{
  "name": "GausVibe Server",
  "version": "1.0.0",
  "graph": {
    "project": "/path/to/project",
    "nodes": 26,
    "edges": 21
  },
  "endpoints": {
    "GET /": "Server info",
    "GET /classes": "List all classes",
    "..."
  }
}
```

### GET /classes
```json
{
  "count": 3,
  "classes": [
    "com.example.calculator.Calculator",
    "com.example.calculator.Main",
    "com.example.calculator.StandardCalculator"
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
  "field_count": 3
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
  "count": 1,
  "results": [
    "public double add(double, double)"
  ]
}
```

### GET /query?q=class:all
```json
{
  "query": "class:all",
  "result": "Classes (3):\n  - com.example.calculator.Calculator\n  - com.example.calculator.Main\n  - com.example.calculator.StandardCalculator\n",
  "nodes": 26,
  "edges": 21
}
```

### GET /stats
```json
{
  "project": "/path/to/project",
  "nodes": 26,
  "edges": 21
}
```

## Endpoints Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Server info and current graph status |
| GET | `/graph/stats` | Graph statistics (nodes, edges) |
| GET | `/classes` | List all classes |
| GET | `/classes/{fqn}` | Get class details |
| GET | `/classes/{fqn}/methods` | List methods of a class |
| GET | `/classes/{fqn}/subclasses` | List subclasses |
| GET | `/classes/{fqn}/implementations` | List interface implementations |
| GET | `/methods` | List all methods |
| GET | `/packages` | List all packages |
| GET | `/query?q=QUERY` | Execute arbitrary GausVibe query |
| GET | `/search?q=NAME` | Search for classes/methods by name |

## Query Language

Use the `/query` endpoint with these query types:

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
| `search:nodes:TYPE` | `search:nodes:CLASS` | Search nodes by type |
| `search:edges:TYPE` | `search:edges:CALLS` | Search edges by type |
| `stats` | `stats` | Graph statistics |

## Running Multiple Servers

Run multiple servers on different ports for different projects:

```bash
CP=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)

# Terminal 1: Calculator project
java -cp "target/classes:$CP" dk.gausdalfind.server.GausVibeServer --project ../bakeoff1 --port 8080 &

# Terminal 2: GausVibe project  
java -cp "target/classes:$CP" dk.gausdalfind.server.GausVibeServer --project . --port 8081 &

# Query both
curl http://localhost:8080/classes        # Calculator classes
curl http://localhost:8081/classes        # GausVibe classes
```

## Startup Behavior

When you start the server:

1. It **requires** a `--project` parameter (will error if missing)
2. It **automatically** builds the graph for that project
3. It **prints** the graph statistics on startup
4. It **listens** for requests on the specified port

Example startup output:
```
Building graph for: /Users/magnusfind/Documents/find-shadow-model/bakeoff1
Graph built: 26 nodes, 21 edges

============================================================
GausVibe Server (Pure Java) Running
Project:  /Users/magnusfind/Documents/find-shadow-model/bakeoff1
Port:     8080
Nodes:    26
Edges:    21
URL:      http://localhost:8080
============================================================
```

## Dependencies

- Java 11+ (for HttpServer)
- Maven (for building)
- That's it! Zero external runtime dependencies

## Implementation Details

- Uses **JDK HttpServer** (com.sun.net.httpserver) - built into Java
- Single JVM process - no spawning external processes
- All query execution happens in-memory
- Graph is built once on startup and cached
- Thread pool for concurrent requests (10 threads)

## Architecture

```
HTTP Request
    ↓
JDK HttpServer (port 8080)
    ↓
GausVibeServer
    ↓
Handler (BaseHandler)
    ↓
GraphQueryEngine / QueryParser
    ↓
Graph (in-memory)
    ↓
Response (JSON)
```

## Performance

- Graph building: ~1-5 seconds for medium projects
- Query execution: ~1-10ms per query (in-memory)
- Memory: Holds entire graph in memory
- Concurrent requests: Thread pool handles up to 10 concurrent requests

## Troubleshooting

**Error: "Could not find or load main class"**
- Run `mvn clean compile` first
- Make sure you're using the correct classpath

**Error: "No graph loaded"**
- Make sure you specified `--project` on startup
- Check the project directory exists

**Port already in use**
- Change the port: `--port 8081`
- Or kill the existing process: `lsof -i :8080`

## Comparison with Python Version

| Aspect | Pure Java | Python Wrapper |
|--------|-----------|-----------------|
| Dependencies | Zero | Flask, FastAPI |
| Performance | ⚡ Faster | 🐢 Slower (process spawning) |
| Complexity | Medium | Low |
| Maintenance | Easy (Java project) | Harder (two languages) |
| Startup | ~1-2s | ~2-3s |
| Memory | Lower | Higher (two processes) |

**Pure Java wins for a Java project!**

## Development

To modify the server:
1. Edit `src/main/java/dk/gausdalfind/server/GausVibeServer.java`
2. Run `mvn clean compile`
3. Restart the server

The server uses:
- `com.sun.net.httpserver.HttpServer` for HTTP
- `dk.gausdalfind.graph.GausVibeBuilder` for graph building
- `dk.gausdalfind.queries.GraphQueryEngine` for queries
- `dk.gausdalfind.cli.QueryParser` for query parsing
