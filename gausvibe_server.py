#!/usr/bin/env python3
"""
GausVibe REST Server (FastAPI)
A high-performance HTTP server that wraps GausVibe functionality.

Usage:
    python3 gausvibe_server.py --project /path/to/java/project --port 8080

The graph is built automatically on startup.

Endpoints:
    GET  /                      - Server info
    GET  /graph/query?q=QUERY   - Execute a query
    GET  /graph/stats           - Graph statistics
    GET  /classes               - List all classes
    GET  /classes/{fqn}         - Class details
    GET  /classes/{fqn}/methods - Methods of a class
    GET  /classes/{fqn}/subclasses - Subclasses
    GET  /classes/{fqn}/implementations - Implementations
    GET  /methods                - All methods
    GET  /packages               - All packages
    GET  /search?q=NAME          - Search by name
"""

import argparse
import subprocess
import sys
import os
import time
from pathlib import Path
from typing import Optional, List
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

app = FastAPI(
    title="GausVibe Server",
    description="REST API for querying Java code graphs",
    version="1.0.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Configuration
GAUSVIBE_DIR = os.path.dirname(os.path.abspath(__file__))

# Global state
current_graph = {
    "file": None,
    "built_at": None,
    "nodes": 0,
    "edges": 0,
    "project": None
}


def get_classpath() -> str:
    """Get Maven classpath from GausVibe project, with caching"""
    cp_file = os.path.join(GAUSVIBE_DIR, "classpath.txt")
    
    if os.path.exists(cp_file):
        with open(cp_file) as f:
            return f.read().strip()
    
    result = subprocess.run(
        ["mvn", "-q", "dependency:build-classpath", "-Dmdep.outputFile=/dev/stdout"],
        cwd=GAUSVIBE_DIR,
        capture_output=True,
        text=True,
        timeout=120
    )
    
    if result.returncode == 0:
        cp = result.stdout.strip()
        with open(cp_file, "w") as f:
            f.write(cp)
        return cp
    else:
        raise RuntimeError(f"Failed to get classpath: {result.stderr}")


def run_gausvibe(args: List[str], project_dir: Optional[str] = None) -> dict:
    """Run GausVibe CLI command and return result"""
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
        timeout=180
    )
    
    return {
        "stdout": result.stdout,
        "stderr": result.stderr,
        "returncode": result.returncode
    }


def build_graph(project_dir: str, graph_file: str) -> None:
    """Build graph for a project"""
    global current_graph
    
    args = [
        "build",
        "--project", project_dir,
        "--output", graph_file,
        "--serialize",
        "--parallel"
    ]
    
    print(f"Building graph for: {project_dir}")
    result = run_gausvibe(args, project_dir=project_dir)
    
    if result["returncode"] != 0:
        print(f"Build failed: {result['stderr']}")
        raise RuntimeError(f"Build failed: {result['stderr']}")
    
    # Parse stats from output
    nodes = 0
    edges = 0
    for line in result["stdout"].split("\n"):
        if "Nodes:" in line:
            try:
                nodes = int(line.split("Nodes:")[1].split(",")[0].strip())
            except:
                pass
        if "Edges:" in line:
            try:
                edges = int(line.split("Edges:")[1].strip())
            except:
                pass
    
    current_graph = {
        "file": graph_file,
        "built_at": time.time(),
        "nodes": nodes,
        "edges": edges,
        "project": project_dir
    }
    
    print(f"Graph built: {nodes} nodes, {edges} edges")


def require_graph():
    """Ensure graph is loaded"""
    if not current_graph["project"] or not os.path.exists(current_graph["file"]):
        raise HTTPException(
            status_code=500,
            detail="No graph loaded. Server requires --project on startup."
        )


# ============ Endpoints ============

@app.get("/")
def info():
    """Server info"""
    return {
        "name": "GausVibe Server",
        "version": "1.0.0",
        "docs": "/docs",
        "current_graph": {
            "project": current_graph["project"],
            "file": current_graph["file"],
            "nodes": current_graph["nodes"],
            "edges": current_graph["edges"],
            "built_at": current_graph["built_at"]
        } if current_graph["project"] else None
    }


@app.get("/graph/query")
def query_graph(q: str = Query(..., description="GausVibe query string")):
    """Execute a query on the graph"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], q])
    
    if result["returncode"] != 0:
        raise HTTPException(
            status_code=500,
            detail=f"Query failed: {result['stderr']}"
        )
    
    return {
        "query": q,
        "result": result["stdout"],
        "nodes": current_graph["nodes"],
        "edges": current_graph["edges"]
    }


@app.get("/graph/stats")
def get_stats():
    """Get graph statistics"""
    require_graph()
    
    return {
        "project": current_graph["project"],
        "graph_file": current_graph["file"],
        "nodes": current_graph["nodes"],
        "edges": current_graph["edges"],
        "built_at": current_graph["built_at"]
    }


@app.get("/classes")
def list_classes():
    """List all classes"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], "class:all"])
    
    if result["returncode"] != 0:
        raise HTTPException(status_code=500, detail=result["stderr"])
    
    classes = []
    for line in result["stdout"].split("\n"):
        if line.strip() and not line.startswith("Classes") and "-" in line:
            fqn = line.strip().split("-")[1].strip()
            if fqn and not fqn.startswith("("):
                classes.append(fqn)
    
    return {
        "count": len(classes),
        "classes": classes
    }


@app.get("/classes/{fqn}")
def get_class(fqn: str):
    """Get class details"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:{fqn}"])
    
    if result["returncode"] != 0:
        raise HTTPException(
            status_code=404,
            detail=f"Class not found: {fqn}"
        )
    
    class_info = {"fqn": fqn, "raw": result["stdout"]}
    
    for line in result["stdout"].split("\n"):
        line = line.strip()
        if line.startswith("Class:"):
            class_info["name"] = line.split("Class:")[1].strip()
        elif line.startswith("Name:"):
            class_info["simple_name"] = line.split("Name:")[1].strip()
        elif line.startswith("File:"):
            class_info["file"] = line.split("File:")[1].strip()
        elif line.startswith("Superclass:"):
            class_info["superclass"] = line.split("Superclass:")[1].strip()
        elif line.startswith("Interfaces:"):
            class_info["interfaces"] = line.split("Interfaces:")[1].strip().split(",")
        elif line.startswith("Methods:"):
            class_info["method_count"] = int(line.split("Methods:")[1].strip())
        elif line.startswith("Fields:"):
            class_info["field_count"] = int(line.split("Fields:")[1].strip())
    
    return class_info


@app.get("/classes/{fqn}/methods")
def get_class_methods(fqn: str):
    """Get methods of a class"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"method:class:{fqn}"])
    
    if result["returncode"] != 0:
        raise HTTPException(
            status_code=404,
            detail=f"Class not found: {fqn}"
        )
    
    methods = []
    for line in result["stdout"].split("\n"):
        if line.strip() and not line.startswith("Methods") and "-" in line:
            method = line.strip().split("-")[1].strip()
            if method:
                methods.append(method)
    
    return {
        "class": fqn,
        "count": len(methods),
        "methods": methods
    }


@app.get("/classes/{fqn}/subclasses")
def get_subclasses(fqn: str):
    """Get subclasses of a class"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:subclasses:{fqn}"])
    
    if result["returncode"] != 0:
        raise HTTPException(status_code=500, detail=result["stderr"])
    
    subclasses = []
    for line in result["stdout"].split("\n"):
        if line.strip() and "-" in line:
            subclass = line.strip().split("-")[1].strip()
            if subclass:
                subclasses.append(subclass)
    
    return {
        "class": fqn,
        "count": len(subclasses),
        "subclasses": subclasses
    }


@app.get("/classes/{fqn}/implementations")
def get_implementations(fqn: str):
    """Get implementations of an interface"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:implementations:{fqn}"])
    
    if result["returncode"] != 0:
        raise HTTPException(status_code=500, detail=result["stderr"])
    
    implementations = []
    for line in result["stdout"].split("\n"):
        if line.strip() and "-" in line:
            impl = line.strip().split("-")[1].strip()
            if impl:
                implementations.append(impl)
    
    return {
        "interface": fqn,
        "count": len(implementations),
        "implementations": implementations
    }


@app.get("/methods")
def list_methods():
    """List all methods"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], "method:all"])
    
    if result["returncode"] != 0:
        raise HTTPException(status_code=500, detail=result["stderr"])
    
    methods = []
    for line in result["stdout"].split("\n"):
        if line.strip() and not line.startswith("Methods") and "-" in line:
            method = line.strip().split("-")[1].strip()
            if method:
                methods.append(method)
    
    return {
        "count": len(methods),
        "methods": methods
    }


@app.get("/packages")
def list_packages():
    """List all packages"""
    require_graph()
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], "package:all"])
    
    if result["returncode"] != 0:
        raise HTTPException(status_code=500, detail=result["stderr"])
    
    packages = []
    for line in result["stdout"].split("\n"):
        if line.strip() and "-" in line:
            pkg = line.strip().split("-")[1].strip()
            if pkg:
                packages.append(pkg)
    
    return {
        "count": len(packages),
        "packages": packages
    }


@app.get("/search")
def search(q: str = Query(..., description="Name to search for")):
    """Search for classes/methods by name"""
    require_graph()
    
    # Search for classes by name
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:name:{q}"])
    
    if result["returncode"] == 0:
        classes = []
        for line in result["stdout"].split("\n"):
            if line.strip() and "-" in line:
                fqn = line.strip().split("-")[1].strip()
                if fqn:
                    classes.append(fqn)
        
        if classes:
            return {
                "type": "class",
                "query": q,
                "count": len(classes),
                "results": classes
            }
    
    # Search for methods by name
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"method:name:{q}"])
    
    if result["returncode"] == 0:
        methods = []
        for line in result["stdout"].split("\n"):
            if line.strip() and "-" in line:
                method = line.strip().split("-")[1].strip()
                if method:
                    methods.append(method)
        
        if methods:
            return {
                "type": "method",
                "query": q,
                "count": len(methods),
                "results": methods
            }
    
    return {
        "type": "none",
        "query": q,
        "results": []
    }


# ============ Main ============

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='GausVibe REST Server (FastAPI) - Graph builds on startup'
    )
    parser.add_argument('--project', type=str, required=True,
                       help='Project directory to index (REQUIRED)')
    parser.add_argument('--port', type=int, default=8080,
                       help='Port to listen on (default: 8080)')
    parser.add_argument('--host', type=str, default='0.0.0.0',
                       help='Host to bind to (default: 0.0.0.0)')
    parser.add_argument('--graph-file', type=str, default=None,
                       help='Graph output file (default: ~/.vibe/cache/gausvibe/graph_<project>.json)')
    parser.add_argument('--reload', action='store_true',
                       help='Enable auto-reload for development')
    
    args = parser.parse_args()
    
    # Build graph on startup
    project_dir = os.path.abspath(args.project)
    if not os.path.exists(project_dir):
        print(f"Error: Project directory not found: {project_dir}")
        sys.exit(1)
    
    # Use a temp file for the graph
    graph_file = args.graph_file or os.path.join(
        os.path.expanduser("~/.vibe/cache/gausvibe"),
        f"graph_{os.path.basename(project_dir)}.json"
    )
    os.makedirs(os.path.dirname(graph_file), exist_ok=True)
    
    build_graph(project_dir, graph_file)
    
    print(f"\n{'='*60}")
    print(f"GausVibe Server (FastAPI) Running")
    print(f"Project:  {project_dir}")
    print(f"Graph:    {graph_file}")
    print(f"Nodes:    {current_graph['nodes']}")
    print(f"Edges:    {current_graph['edges']}")
    print(f"URL:      http://{args.host}:{args.port}")
    print(f"Docs:     http://{args.host}:{args.port}/docs")
    print(f"{'='*60}\n")
    
    uvicorn.run(
        app,
        host=args.host,
        port=args.port,
        reload=args.reload
    )
