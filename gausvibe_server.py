#!/usr/bin/env python3
"""
GausVibe REST Server
A simple HTTP server that wraps GausVibe functionality.

Usage:
    python gausvibe_server.py --project /path/to/java/project --port 8080

The server automatically builds the graph for the specified project on startup.
All endpoints are ready to use immediately after the server starts.

Endpoints:
    GET  /                      - Server info
    GET  /graph/query?q=QUERY   - Execute a query on the graph
    GET  /graph/stats           - Get graph statistics
    GET  /classes               - List all classes
    GET  /classes/<fqn>         - Get class details
    GET  /classes/<fqn>/methods - List methods of a class
    GET  /classes/<fqn>/subclasses - List subclasses
    GET  /classes/<fqn>/implementations - List implementations
    GET  /methods                - List all methods
    GET  /packages               - List all packages
    GET  /search?q=NAME          - Search for classes/methods by name
"""

import argparse
import json
import subprocess
import sys
import os
import time
from pathlib import Path
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

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


def get_classpath():
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


def run_gausvibe(args, project_dir=None):
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


def build_graph(project_dir, graph_file):
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
    return graph_file


def require_graph():
    """Decorator to ensure graph is loaded"""
    def decorator(f):
        def wrapper(*args, **kwargs):
            if not current_graph["project"] or not os.path.exists(current_graph["file"]):
                return jsonify({"error": "No graph loaded. Server requires --project on startup."}), 500
            return f(*args, **kwargs)
        return wrapper
    return decorator


# ============ Endpoints ============

@app.route('/')
def index():
    """Server info"""
    return jsonify({
        "name": "GausVibe Server",
        "version": "1.0.0",
        "current_graph": {
            "project": current_graph["project"],
            "file": current_graph["file"],
            "nodes": current_graph["nodes"],
            "edges": current_graph["edges"],
            "built_at": current_graph["built_at"]
        } if current_graph["project"] else None,
        "endpoints": {
            "info": "GET /",
            "query": "GET /graph/query?q=QUERY",
            "stats": "GET /graph/stats",
            "classes": "GET /classes",
            "class_detail": "GET /classes/<fqn>",
            "class_methods": "GET /classes/<fqn>/methods",
            "class_subclasses": "GET /classes/<fqn>/subclasses",
            "class_implementations": "GET /classes/<fqn>/implementations",
            "methods": "GET /methods",
            "packages": "GET /packages",
            "search": "GET /search?q=NAME"
        }
    })


@app.route('/graph/query', methods=['GET'])
@require_graph()
def query_graph():
    """Execute a query on the graph"""
    query = request.args.get('q', '')
    
    if not query:
        return jsonify({"error": "Missing 'q' query parameter"}), 400
    
    result = run_gausvibe(["query", "--graph", current_graph["file"], query])
    
    if result["returncode"] != 0:
        return jsonify({
            "error": result["stderr"],
            "query": query,
            "partial": result["stdout"]
        }), 500
    
    return jsonify({
        "query": query,
        "result": result["stdout"],
        "nodes": current_graph["nodes"],
        "edges": current_graph["edges"]
    })


@app.route('/graph/stats', methods=['GET'])
@require_graph()
def get_stats():
    """Get graph statistics"""
    return jsonify({
        "project": current_graph["project"],
        "graph_file": current_graph["file"],
        "nodes": current_graph["nodes"],
        "edges": current_graph["edges"],
        "built_at": current_graph["built_at"]
    })


@app.route('/classes', methods=['GET'])
@require_graph()
def list_classes():
    """List all classes"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], "class:all"])
    
    if result["returncode"] != 0:
        return jsonify({"error": result["stderr"]}), 500
    
    # Parse classes from output
    classes = []
    for line in result["stdout"].split("\n"):
        if line.strip() and not line.startswith("Classes") and "-" in line:
            fqn = line.strip().split("-")[1].strip()
            if fqn and not fqn.startswith("("):
                classes.append(fqn)
    
    return jsonify({
        "count": len(classes),
        "classes": classes
    })


@app.route('/classes/<path:fqn>', methods=['GET'])
@require_graph()
def get_class(fqn):
    """Get class details"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:{fqn}"])
    
    if result["returncode"] != 0:
        return jsonify({"error": f"Class not found: {fqn}", "details": result["stderr"]}), 404
    
    # Parse class info
    class_info = {
        "fqn": fqn,
        "raw": result["stdout"]
    }
    
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
    
    return jsonify(class_info)


@app.route('/classes/<path:fqn>/methods', methods=['GET'])
@require_graph()
def get_class_methods(fqn):
    """Get methods of a class"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"method:class:{fqn}"])
    
    if result["returncode"] != 0:
        return jsonify({"error": f"Class not found: {fqn}", "details": result["stderr"]}), 404
    
    methods = []
    for line in result["stdout"].split("\n"):
        if line.strip() and not line.startswith("Methods") and "-" in line:
            method = line.strip().split("-")[1].strip()
            if method:
                methods.append(method)
    
    return jsonify({
        "class": fqn,
        "count": len(methods),
        "methods": methods
    })


@app.route('/classes/<path:fqn>/subclasses', methods=['GET'])
@require_graph()
def get_subclasses(fqn):
    """Get subclasses of a class"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:subclasses:{fqn}"])
    
    if result["returncode"] != 0:
        return jsonify({"error": result["stderr"]}), 500
    
    subclasses = []
    for line in result["stdout"].split("\n"):
        if line.strip() and "-" in line:
            subclass = line.strip().split("-")[1].strip()
            if subclass:
                subclasses.append(subclass)
    
    return jsonify({
        "class": fqn,
        "count": len(subclasses),
        "subclasses": subclasses
    })


@app.route('/classes/<path:fqn>/implementations', methods=['GET'])
@require_graph()
def get_implementations(fqn):
    """Get implementations of an interface"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:implementations:{fqn}"])
    
    if result["returncode"] != 0:
        return jsonify({"error": result["stderr"]}), 500
    
    implementations = []
    for line in result["stdout"].split("\n"):
        if line.strip() and "-" in line:
            impl = line.strip().split("-")[1].strip()
            if impl:
                implementations.append(impl)
    
    return jsonify({
        "interface": fqn,
        "count": len(implementations),
        "implementations": implementations
    })


@app.route('/methods', methods=['GET'])
@require_graph()
def list_methods():
    """List all methods"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], "method:all"])
    
    if result["returncode"] != 0:
        return jsonify({"error": result["stderr"]}), 500
    
    methods = []
    for line in result["stdout"].split("\n"):
        if line.strip() and not line.startswith("Methods") and "-" in line:
            method = line.strip().split("-")[1].strip()
            if method:
                methods.append(method)
    
    return jsonify({
        "count": len(methods),
        "methods": methods
    })


@app.route('/packages', methods=['GET'])
@require_graph()
def list_packages():
    """List all packages"""
    result = run_gausvibe(["query", "--graph", current_graph["file"], "package:all"])
    
    if result["returncode"] != 0:
        return jsonify({"error": result["stderr"]}), 500
    
    packages = []
    for line in result["stdout"].split("\n"):
        if line.strip() and "-" in line:
            pkg = line.strip().split("-")[1].strip()
            if pkg:
                packages.append(pkg)
    
    return jsonify({
        "count": len(packages),
        "packages": packages
    })


@app.route('/search', methods=['GET'])
@require_graph()
def search():
    """Search for classes/methods by name"""
    query = request.args.get('q', '')
    
    if not query:
        return jsonify({"error": "Missing 'q' parameter"}), 400
    
    # Search for classes by name
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"class:name:{query}"])
    
    if result["returncode"] == 0:
        classes = []
        for line in result["stdout"].split("\n"):
            if line.strip() and "-" in line:
                fqn = line.strip().split("-")[1].strip()
                if fqn:
                    classes.append(fqn)
        
        if classes:
            return jsonify({
                "type": "class",
                "query": query,
                "count": len(classes),
                "results": classes
            })
    
    # Search for methods by name
    result = run_gausvibe(["query", "--graph", current_graph["file"], f"method:name:{query}"])
    
    if result["returncode"] == 0:
        methods = []
        for line in result["stdout"].split("\n"):
            if line.strip() and "-" in line:
                method = line.strip().split("-")[1].strip()
                if method:
                    methods.append(method)
        
        if methods:
            return jsonify({
                "type": "method",
                "query": query,
                "count": len(methods),
                "results": methods
            })
    
    return jsonify({
        "type": "none",
        "query": query,
        "results": []
    })


# ============ Main ============

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='GausVibe REST Server - Builds graph on startup')
    parser.add_argument('--project', type=str, required=True,
                       help='Project directory to index (REQUIRED)')
    parser.add_argument('--port', type=int, default=8080,
                       help='Port to listen on (default: 8080)')
    parser.add_argument('--host', type=str, default='0.0.0.0',
                       help='Host to bind to (default: 0.0.0.0)')
    parser.add_argument('--graph-file', type=str, default=None,
                       help='Graph output file (default: <temp file>)')
    
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
    print(f"GausVibe Server Running")
    print(f"Project:  {project_dir}")
    print(f"Graph:    {graph_file}")
    print(f"Nodes:    {current_graph['nodes']}")
    print(f"Edges:    {current_graph['edges']}")
    print(f"URL:      http://{args.host}:{args.port}")
    print(f"{'='*60}\n")
    
    app.run(host=args.host, port=args.port, debug=False)
