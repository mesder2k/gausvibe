# GausVibe Vibe Plugin Setup

## Overview

This guide shows how to integrate GausVibe as a first-class tool in Vibe, so that when you ask questions like "List all classes that implement Calculator", Vibe automatically recognizes it as a Java code query and routes it to GausVibe.

## Architecture

```
Vibe CLI
  ├── Plugins (~/.vibe/plugins/)
  │   └── gausvibe/
  │       ├── plugin.json          # Plugin manifest
  │       ├── mcp.json             # MCP server definitions
  │       └── mcp_server.py        # Python wrapper for GausVibe CLI
  │
  └── Skills
      └── gausvibe-query/SKILL.md  # NL to query mapping
```

## Step 1: Create Plugin Directory

```bash
mkdir -p ~/.vibe/plugins/gausvibe
mkdir -p ~/.vibe/plugins/gausvibe/skills/gausvibe-query
```

## Step 2: Plugin Manifest

Create `~/.vibe/plugins/gausvibe/plugin.json`:

```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "gausvibe",
  "version": "1.0.0",
  "description": "GausVibe code graph tools for Java projects - enables natural language queries on Java codebases",
  "author": {
    "name": "GausVibe Team"
  },
  "extensions": {
    "ai.mistral.vibe": {
      "schemaVersion": 1,
      "toolNamespace": "gausvibe"
    }
  }
}
```

## Step 3: MCP Server Configuration

Create `~/.vibe/plugins/gausvibe/mcp.json`:

```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
  "mcpServers": {
    "gausvibe": {
      "type": "stdio",
      "command": "python3",
      "args": ["${PLUGIN_ROOT}/mcp_server.py"],
      "cwd": "${PLUGIN_ROOT}",
      "env": {
        "GAUSVIBE_DIR": "/Users/magnusfind/Documents/find-shadow-model/gausvibe"
      }
    }
  }
}
```

## Step 4: MCP Server (Python Wrapper)

Create `~/.vibe/plugins/gausvibe/mcp_server.py`:

```python
#!/usr/bin/env python3
"""
MCP Server for GausVibe
Translates MCP tool calls to GausVibe Java CLI commands
"""
import json
import subprocess
import sys
import os

GAUSVIBE_DIR = os.environ.get("GAUSVIBE_DIR", "/Users/magnusfind/Documents/find-shadow-model/gausvibe")
CLASSPATH_CACHE = os.path.expanduser("~/.vibe/cache/gausvibe-classpath.txt")

def get_classpath():
    """Get Maven classpath, with caching"""
    if os.path.exists(CLASSPATH_CACHE) and os.path.getmtime(CLASSPATH_CACHE) > os.path.getmtime("/tmp"):
        with open(CLASSPATH_CACHE) as f:
            return f.read().strip()
    
    result = subprocess.run(
        ["mvn", "-q", "dependency:build-classpath", "-Dmdep.outputFile=/dev/stdout"],
        cwd=GAUSVIBE_DIR,
        capture_output=True,
        text=True,
        timeout=60
    )
    
    cp = result.stdout.strip()
    os.makedirs(os.path.dirname(CLASSPATH_CACHE), exist_ok=True)
    with open(CLASSPATH_CACHE, "w") as f:
        f.write(cp)
    
    return cp

def run_gausvibe(args, project_dir=None):
    """Run GausVibe CLI command"""
    cp = get_classpath()
    cmd = [
        "java", "-cp", f"target/classes:{cp}",
        "dk.gausdalfind.Main"
    ] + args
    
    result = subprocess.run(
        cmd,
        cwd=project_dir or GAUSVIBE_DIR,
        capture_output=True,
        text=True,
        timeout=120
    )
    
    return {
        "stdout": result.stdout,
        "stderr": result.stderr,
        "returncode": result.returncode
    }

def handle_build(params):
    """Build graph from project"""
    args = ["build", "--project", params.get("project", ".")]
    
    if params.get("output"):
        args.extend(["--output", params["output"]])
    if params.get("serialize", True):
        args.append("--serialize")
    if params.get("excludeTest", False):
        args.append("--exclude-test")
    if params.get("parallel", True):
        args.append("--parallel")
    
    result = run_gausvibe(args, project_dir=params.get("project"))
    
    if result["returncode"] != 0:
        return {"error": result["stderr"], "success": False}
    
    # Parse nodes and edges from output
    nodes = 0
    edges = 0
    for line in result["stdout"].split("\n"):
        if "Nodes:" in line:
            nodes = int(line.split("Nodes:")[1].split(",")[0].strip())
        if "Edges:" in line:
            edges = int(line.split("Edges:")[1].split("\n")[0].strip())
    
    return {
        "success": True,
        "output": result["stdout"],
        "graphFile": params.get("output", "graph.json"),
        "nodes": nodes,
        "edges": edges
    }

def handle_query(params):
    """Execute query on graph"""
    args = ["query"]
    
    if params.get("graph"):
        args.extend(["--graph", params["graph"]])
    
    query = params.get("query", "")
    if query:
        args.append(query)
    
    result = run_gausvibe(args)
    
    if result["returncode"] != 0:
        return {"error": result["stderr"], "success": False, "result": result["stdout"]}
    
    return {
        "success": True,
        "result": result["stdout"],
        "query": query
    }

def handle_interactive(params):
    """Start interactive shell (for debugging)"""
    args = ["interactive"]
    result = run_gausvibe(args)
    return {
        "success": result["returncode"] == 0,
        "output": result["stdout"],
        "error": result["stderr"]
    }

def handle_stats(params):
    """Get graph statistics"""
    result = handle_query({"graph": params.get("graph"), "query": "stats"})
    return result

def main():
    """MCP Server main loop"""
    os.makedirs(os.path.dirname(CLASSPATH_CACHE), exist_ok=True)
    
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
            
        try:
            msg = json.loads(line)
            
            # Handle MCP initialization
            if msg.get("jsonrpc") == "2.0":
                if msg.get("method") == "tools/list":
                    tools = {
                        "tools": [
                            {
                                "name": "gausvibe.build",
                                "description": "Build a code graph from a Java project",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": {
                                        "project": {
                                            "type": "string",
                                            "description": "Path to project directory",
                                            "default": "."
                                        },
                                        "output": {
                                            "type": "string",
                                            "description": "Output file for serialized graph"
                                        },
                                        "serialize": {
                                            "type": "boolean",
                                            "description": "Serialize graph to file",
                                            "default": True
                                        },
                                        "excludeTest": {
                                            "type": "boolean",
                                            "description": "Exclude test sources",
                                            "default": False
                                        },
                                        "parallel": {
                                            "type": "boolean",
                                            "description": "Use parallel processing",
                                            "default": True
                                        }
                                    }
                                }
                            },
                            {
                                "name": "gausvibe.query",
                                "description": "Execute a query on a code graph",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": {
                                        "graph": {
                                            "type": "string",
                                            "description": "Path to serialized graph file"
                                        },
                                        "query": {
                                            "type": "string",
                                            "description": "GausVibe query string (e.g., 'class:all', 'method:class:com.example.MyClass')"
                                        }
                                    },
                                    "required": ["query"]
                                }
                            },
                            {
                                "name": "gausvibe.stats",
                                "description": "Get graph statistics (nodes, edges)",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": {
                                        "graph": {
                                            "type": "string",
                                            "description": "Path to serialized graph file"
                                        }
                                    }
                                }
                            },
                            {
                                "name": "gausvibe.interactive",
                                "description": "Start GausVibe interactive shell",
                                "inputSchema": {"type": "object", "properties": {}}
                            }
                        ]
                    }
                    print(json.dumps(msg))
                    sys.stdout.flush()
                    continue
                
                elif msg.get("method") == "tools/call":
                    tool_name = msg["params"].get("name", "")
                    tool_args = msg["params"].get("arguments", {})
                    call_id = msg.get("id", 1)
                    
                    response = {"id": call_id, "jsonrpc": "2.0"}
                    
                    try:
                        if tool_name == "gausvibe.build":
                            result = handle_build(tool_args)
                            response["result"] = result
                        elif tool_name == "gausvibe.query":
                            result = handle_query(tool_args)
                            response["result"] = result
                        elif tool_name == "gausvibe.stats":
                            result = handle_stats(tool_args)
                            response["result"] = result
                        elif tool_name == "gausvibe.interactive":
                            result = handle_interactive(tool_args)
                            response["result"] = result
                        else:
                            response["error"] = {"message": f"Unknown tool: {tool_name}"}
                    except Exception as e:
                        response["error"] = {"message": str(e)}
                    
                    print(json.dumps(response))
                    sys.stdout.flush()
                    continue
            
            # Legacy simple protocol (if Vibe uses it)
            if "method" in msg:
                method = msg["method"]
                params = msg.get("params", {})
                call_id = msg.get("id")
                
                if method == "gausvibe/build":
                    result = handle_build(params.get("arguments", {}))
                    response = {"id": call_id, "result": result}
                elif method == "gausvibe/query":
                    result = handle_query(params.get("arguments", {}))
                    response = {"id": call_id, "result": result}
                elif method == "gausvibe/stats":
                    result = handle_stats(params.get("arguments", {}))
                    response = {"id": call_id, "result": result}
                else:
                    response = {"id": call_id, "error": f"Unknown method: {method}"}
                
                print(json.dumps(response))
                sys.stdout.flush()
                continue
                
        except json.JSONDecodeError:
            # Not JSON, might be MCP initialization
            if line == "ping":
                print("pong")
                sys.stdout.flush()
            continue
        except Exception as e:
            sys.stderr.write(f"Error: {e}\n")
            continue

if __name__ == "__main__":
    main()
```

## Step 5: Create the Skill

Create `~/.vibe/plugins/gausvibe/skills/gausvibe-query/SKILL.md`:

```markdown
---
name: gausvibe-query
description: Route Java code structure questions to GausVibe graph tools
user-invocable: false
allowed-tools:
  - gausvibe.build
  - gausvibe.query
  - gausvibe.stats
  - file_system.bash
  - file_system.read_file
---

# GausVibe Query Router Skill

## Purpose

This skill enables Vibe to automatically recognize Java code structure questions and route them to GausVibe for execution.

## Activation Triggers

Load this skill when user questions contain:
- Java-specific keywords (class, method, interface, implements, extends, package, etc.)
- Code analysis requests (list, find, show, get, query about Java code)
- Project structure questions
- Code dependency questions

## Natural Language to Query Mapping

| User Intent | Pattern Match | GausVibe Query | Notes |
|-------------|--------------|----------------|-------|
| List implementations | "implement(s)?", "implementations? of", "classes that implement" | `class:implementations:{FQN}` | Need full qualified name |
| Find subclasses | "subclass(es)?", "extends", "children of", "derived from" | `class:subclasses:{FQN}` | |
| Show superclass | "superclass", "parent class", "extends from" | `class:superclass:{FQN}` | |
| List interfaces | "interface(s)?", "implements interface" | `class:interfaces:{FQN}` | |
| Find class | "class(es)?", "find class", "locate class" | `class:name:{NAME}` or `class:{FQN}` | |
| List all classes | "all classes", "every class", "all java classes" | `class:all` | |
| List methods | "method(s)?", "functions", "all methods" | `method:all` or `method:class:{FQN}` | |
| Find method | "method named", "function called", "find method" | `method:name:{NAME}` | |
| Get methods of class | "methods of", "methods in", "in class" | `method:class:{FQN}` | |
| List fields | "field(s)?", "properties", "members" | `field:all` or `field:class:{FQN}` | |
| Find field | "field named", "property called" | `field:class:{FQN}` + filter | |
| List packages | "package(s)?", "java packages" | `package:all` | |
| Show dependencies | "depend(s)?", "uses", "imports" | `search:edges:DEPENDS_ON` | Filter by source |
| Show callers | "caller(s)?", "who calls", "called by" | `search:edges:CALLS` | Filter by target |
| Show callee | "callee", "calls", "invokes" | `search:edges:CALLS` | Filter by source |
| Graph stats | "stats", "statistics", "how many" | `stats` | |

## Query Translation Flow

1. **Detect**: Check if the question is about Java code structure
   - Look for Java keywords
   - Check for code analysis intent
   
2. **Extract**: Identify entities (class names, method names, etc.)
   - Parse user input for class/method names
   - Use FQN (Fully Qualified Name) when possible
   - For simple names, try to resolve from graph
   
3. **Map**: Convert natural language to GausVibe query
   - Use the mapping table above
   - Handle variations and synonyms
   
4. **Execute**: Run the query
   - Build graph if not already built
   - Execute query
   - Parse and format results

## Implementation Examples

### Example 1: "List all classes that implement Calculator"

```
Detection: Contains "implement" and "Calculator" (class name)
Translation:
  - Entity: Calculator
  - Pattern: "implement" -> class:implementations
  - Need FQN: Try "com.example.calculator.Calculator"
  - Query: class:implementations:com.example.calculator.Calculator
  
Execution:
  1. Check if graph exists for current project
  2. If not: tools.gausvibe.build(project: PROJECT_DIR)
  3. tools.gausvibe.query(graph: GRAPH_FILE, query: "class:implementations:com.example.calculator.Calculator")
  4. Format results for user
```

### Example 2: "Show me all methods in StandardCalculator"

```
Detection: Contains "methods" and "StandardCalculator"
Translation:
  - Entity: StandardCalculator
  - Pattern: "methods in" -> method:class
  - Query: method:class:com.example.calculator.StandardCalculator
  
Execution:
  1. tools.gausvibe.query(graph: GRAPH_FILE, query: "method:class:com.example.calculator.StandardCalculator")
  2. Format as list of methods
```

### Example 3: "What classes extend Object?"

```
Detection: Contains "extend" and "Object"
Translation:
  - Entity: Object (java.lang.Object)
  - Pattern: "extend" -> class:subclasses
  - Query: class:subclasses:java.lang.Object
  
Execution:
  1. tools.gausvibe.query(graph: GRAPH_FILE, query: "class:subclasses:java.lang.Object")
  2. Note: This may return many results, consider limiting
```

### Example 4: "Find all methods named 'add'"

```
Detection: Contains "methods" and "named" and "add"
Translation:
  - Entity: add
  - Pattern: "methods named" -> method:name
  - Query: method:name:add
  
Execution:
  1. tools.gausvibe.query(graph: GRAPH_FILE, query: "method:name:add")
  2. Format results
```

## Default Configuration

- Default graph file: `/tmp/gausvibe-graph.json`
- Default project: Current working directory
- Graph rebuild: Only if graph file doesn't exist or is older than source files

## Entity Resolution

When user provides simple names (e.g., "Calculator" instead of "com.example.calculator.Calculator"):

1. Try exact match in graph
2. If multiple matches, list them and ask user to clarify
3. If no match, try case-insensitive
4. If still no match, return "No classes found with name X"

## Response Formatting

Format query results based on type:

- **class:all** or **class:name**: List of class names with packages
- **class:implementations**: List of implementing classes
- **class:subclasses**: List of subclasses
- **method:all** or **method:class**: List of method signatures
- **method:name**: List of methods with their classes
- **stats**: Nodes: X, Edges: Y

## Caching Strategy

- Cache the built graph file
- Cache classpath from Maven
- Rebuild graph only when:
  - Graph file doesn't exist
  - Source files are newer than graph file
  - User explicitly requests rebuild

## Error Handling

- If GausVibe CLI fails: Report error and suggest checking GausVibe installation
- If query fails: Report error with query string for debugging
- If no results: Return "No results found" (not an error)
- If ambiguous names: Ask user for clarification

## Advanced Features

### Multi-part Questions

For questions like "Show me all classes that implement Calculator and have a method called add":
1. First query: `class:implementations:com.example.calculator.Calculator`
2. For each result, query: `method:class:{CLASS_FQN}`
3. Filter classes that have 'add' method

### Context-Aware Queries

Maintain context between queries:
- Remember current project
- Remember current graph file
- Remember last query results

### Query History

Track recent queries for:
- Reusing graph files
- Providing suggestions
- Debugging

## Integration with Other Tools

When analyzing code:
- Use `read_file` to show source code alongside graph results
- Use `bash` for file operations
- Combine with other analysis tools as needed
