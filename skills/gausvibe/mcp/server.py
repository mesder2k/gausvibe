#!/usr/bin/env python3
"""
GausVibe MCP Server

A Model Context Protocol (MCP) server that provides real-time access to
Java code graphs for LLM code understanding.

Usage:
    python server.py --graph /path/to/graph.json
    
    Or let Vibe manage the graph:
    python server.py  # Graph can be loaded via tool calls
"""

import argparse
import asyncio
import json
import logging
import os
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

# Add parent directory to path for imports
SKILL_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(SKILL_ROOT))

from tools.query_graph.execute import QueryExecutor, QueryParser, format_result_text, format_summary


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger("gausvibe.mcp")


@dataclass
class MCPServerConfig:
    """Configuration for the MCP server."""
    host: str = "127.0.0.1"
    port: int = 8080
    graph_path: Optional[str] = None
    verbose: bool = False


@dataclass
class GraphCache:
    """Cache for loaded graphs."""
    graphs: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    executors: Dict[str, QueryExecutor] = field(default_factory=dict)
    
    def load_graph(self, graph_path: str) -> QueryExecutor:
        """Load a graph from file and return an executor."""
        path = Path(graph_path).absolute()
        key = str(path)
        
        if key in self.executors:
            return self.executors[key]
        
        # Load graph
        with open(path, "r") as f:
            graph_data = json.load(f)
        
        # Store in cache
        self.graphs[key] = graph_data
        self.executors[key] = QueryExecutor(graph_data, verbose=False)
        
        logger.info(f"Loaded graph from {path}")
        return self.executors[key]
    
    def get_executor(self, graph_key: str) -> Optional[QueryExecutor]:
        """Get an executor for a loaded graph."""
        return self.executors.get(graph_key)


# Global cache
_graph_cache = GraphCache()


class GausVibeMCPServer:
    """
    MCP server implementation for GausVibe.
    
    Provides tools for:
    - Querying code graphs
    - Getting nodes and edges
    - Retrieving schema information
    - Managing loaded graphs
    """
    
    def __init__(self, config: MCPServerConfig):
        self.config = config
        self.server_name = "gausvibe"
        self.server_version = "1.0.0"
        self.server_capabilities = {
            "tools": self._get_tool_definitions(),
            "resources": self._get_resource_definitions(),
        }
        
        # Load initial graph if specified
        if config.graph_path:
            _graph_cache.load_graph(config.graph_path)
    
    def _get_tool_definitions(self) -> List[Dict[str, Any]]:
        """Get definitions of all available tools."""
        return [
            {
                "name": "query",
                "description": "Execute a query on a loaded Java code graph",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "graph": {
                            "type": "string",
                            "description": "Path or key of the graph to query",
                        },
                        "query": {
                            "type": "string",
                            "description": "Query to execute (natural language or structured)",
                        },
                        "format": {
                            "type": "string",
                            "enum": ["json", "text", "summary"],
                            "default": "json",
                            "description": "Output format",
                        },
                        "limit": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 1000,
                            "default": 100,
                            "description": "Maximum results to return",
                        },
                    },
                    "required": ["query"],
                },
            },
            {
                "name": "get_nodes",
                "description": "Get nodes from the graph by type, file, or ID",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "graph": {
                            "type": "string",
                            "description": "Path or key of the graph",
                        },
                        "node_type": {
                            "type": "string",
                            "description": "Filter by node type (CLASS, METHOD, etc.)",
                        },
                        "node_id": {
                            "type": "string",
                            "description": "Get specific node by ID",
                        },
                        "file": {
                            "type": "string",
                            "description": "Filter by source file",
                        },
                        "name": {
                            "type": "string",
                            "description": "Filter by node name",
                        },
                        "limit": {
                            "type": "integer",
                            "default": 100,
                            "description": "Maximum nodes to return",
                        },
                    },
                },
            },
            {
                "name": "get_edges",
                "description": "Get edges from the graph by type or between nodes",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "graph": {
                            "type": "string",
                            "description": "Path or key of the graph",
                        },
                        "edge_type": {
                            "type": "string",
                            "description": "Filter by edge type",
                        },
                        "from_id": {
                            "type": "string",
                            "description": "Filter by source node ID",
                        },
                        "to_id": {
                            "type": "string",
                            "description": "Filter by target node ID",
                        },
                        "limit": {
                            "type": "integer",
                            "default": 100,
                            "description": "Maximum edges to return",
                        },
                    },
                },
            },
            {
                "name": "get_schema",
                "description": "Get node and edge type definitions",
                "inputSchema": {
                    "type": "object",
                    "properties": {},
                },
            },
            {
                "name": "get_stats",
                "description": "Get graph statistics",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "graph": {
                            "type": "string",
                            "description": "Path or key of the graph",
                        },
                    },
                },
            },
            {
                "name": "load_graph",
                "description": "Load a graph from a JSON file",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path to graph JSON file",
                        },
                    },
                    "required": ["path"],
                },
            },
            {
                "name": "list_graphs",
                "description": "List all loaded graphs",
                "inputSchema": {
                    "type": "object",
                    "properties": {},
                },
            },
            {
                "name": "unload_graph",
                "description": "Unload a graph from memory",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "graph": {
                            "type": "string",
                            "description": "Path or key of the graph to unload",
                        },
                    },
                    "required": ["graph"],
                },
            },
        ]
    
    def _get_resource_definitions(self) -> List[Dict[str, Any]]:
        """Get resource definitions for MCP."""
        return []
    
    async def call_tool(self, name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a tool by name with arguments."""
        start_time = time.time()
        
        try:
            # Route to appropriate handler
            handler = getattr(self, f"_tool_{name}", None)
            if handler:
                result = await handler(**arguments)
            else:
                result = {
                    "success": False,
                    "error": f"Unknown tool: {name}",
                }
            
            elapsed_ms = int((time.time() - start_time) * 1000)
            result["tool"] = name
            result["executionTimeMs"] = elapsed_ms
            
            return result
            
        except Exception as e:
            logger.exception(f"Error calling tool {name}")
            return {
                "success": False,
                "error": str(e),
                "tool": name,
                "executionTimeMs": int((time.time() - start_time) * 1000),
            }
    
    # Tool implementations
    
    async def _tool_query(
        self,
        query: str,
        graph: Optional[str] = None,
        format: str = "json",
        limit: int = 100,
    ) -> Dict[str, Any]:
        """Execute a query on a graph."""
        # Get executor
        executor = self._get_executor(graph)
        if not executor:
            return {
                "success": False,
                "error": f"No graph loaded. Please load a graph first or specify graph path.",
            }
        
        # Parse query
        parsed_query = QueryParser.parse(query)
        
        # Execute
        results = executor.execute(parsed_query, limit=limit)
        results["query"] = query
        
        # Format
        if format == "text":
            text_results = []
            for result in results.get("results", []):
                text = format_result_text(result)
                if text:
                    text_results.append({"type": "GENERIC", "data": {"text": text}})
            results["results"] = text_results
        elif format == "summary":
            summary = format_summary(results)
            results["results"] = [{"type": "GENERIC", "data": {"summary": summary}}]
        
        return results
    
    async def _tool_get_nodes(
        self,
        graph: Optional[str] = None,
        node_type: Optional[str] = None,
        node_id: Optional[str] = None,
        file: Optional[str] = None,
        name: Optional[str] = None,
        limit: int = 100,
    ) -> Dict[str, Any]:
        """Get nodes from the graph."""
        executor = self._get_executor(graph)
        if not executor:
            return {"success": False, "error": "No graph loaded"}
        
        # Get all nodes
        all_nodes = executor._indexes.get("byType", {}).values()
        nodes = [node for sublist in all_nodes for node in sublist]
        
        # Apply filters
        if node_type:
            nodes = executor._indexes.get("byType", {}).get(node_type.upper(), [])
        
        if node_id:
            node = executor._indexes.get("byId", {}).get(node_id)
            if node:
                nodes = [node]
            else:
                nodes = []
        
        if file:
            nodes = [n for n in nodes if n.get("file") == file]
        
        if name:
            nodes = [n for n in nodes if n.get("name") == name]
        
        # Apply limit
        nodes = nodes[:limit]
        
        # Format
        formatted_nodes = []
        for node in nodes:
            formatted_nodes.append(executor._format_node(node))
        
        return {
            "success": True,
            "nodes": formatted_nodes,
            "count": len(formatted_nodes),
            "total": len(nodes),
            "truncated": len(nodes) > limit,
        }
    
    async def _tool_get_edges(
        self,
        graph: Optional[str] = None,
        edge_type: Optional[str] = None,
        from_id: Optional[str] = None,
        to_id: Optional[str] = None,
        limit: int = 100,
    ) -> Dict[str, Any]:
        """Get edges from the graph."""
        executor = self._get_executor(graph)
        if not executor:
            return {"success": False, "error": "No graph loaded"}
        
        # Get all edges
        all_edges = []
        for edge_list in executor._indexes.get("edgesByType", {}).values():
            all_edges.extend(edge_list)
        
        # Apply filters
        edges = all_edges
        
        if edge_type:
            edges = executor._indexes.get("edgesByType", {}).get(edge_type.upper(), [])
        
        if from_id:
            edges = executor._indexes.get("edgesByFrom", {}).get(from_id, [])
        
        if to_id:
            edges = executor._indexes.get("edgesByTo", {}).get(to_id, [])
        
        # Apply limit
        edges = edges[:limit]
        
        return {
            "success": True,
            "edges": edges,
            "count": len(edges),
            "total": len(edges),
            "truncated": len(all_edges) > limit,
        }
    
    async def _tool_get_schema(self) -> Dict[str, Any]:
        """Get node and edge type definitions."""
        return {
            "success": True,
            "nodeTypes": [
                {"name": "PACKAGE", "prefix": "pkg", "description": "Java package"},
                {"name": "CLASS", "prefix": "cls", "description": "Class or interface"},
                {"name": "INTERFACE", "prefix": "cls", "description": "Interface"},
                {"name": "ENUM", "prefix": "cls", "description": "Enum"},
                {"name": "METHOD", "prefix": "mth", "description": "Method or constructor"},
                {"name": "FIELD", "prefix": "fld", "description": "Class field"},
                {"name": "PARAMETER", "prefix": "par", "description": "Method parameter"},
                {"name": "VARIABLE", "prefix": "var", "description": "Local variable"},
                {"name": "BLOCK", "prefix": "blk", "description": "Code block"},
                {"name": "IF", "prefix": "if", "description": "If statement"},
                {"name": "FOR", "prefix": "for", "description": "For loop"},
                {"name": "WHILE", "prefix": "while", "description": "While loop"},
                {"name": "SWITCH", "prefix": "switch", "description": "Switch statement"},
                {"name": "TRY", "prefix": "try", "description": "Try-catch block"},
                {"name": "RETURN", "prefix": "return", "description": "Return statement"},
                {"name": "THROW", "prefix": "throw", "description": "Throw statement"},
                {"name": "METHOD_CALL", "prefix": "call", "description": "Method call expression"},
                {"name": "FIELD_ACCESS", "prefix": "field_acc", "description": "Field access expression"},
                {"name": "LITERAL", "prefix": "lit", "description": "Literal value"},
                {"name": "BINARY_OP", "prefix": "bin_op", "description": "Binary operation"},
            ],
            "edgeTypes": [
                {"name": "CONTAINS", "description": "Parent contains child"},
                {"name": "HAS_METHOD", "description": "Class has method"},
                {"name": "HAS_FIELD", "description": "Class has field"},
                {"name": "HAS_PARAMETER", "description": "Method has parameter"},
                {"name": "CALLS", "description": "Method/constructor call"},
                {"name": "ACCESSES", "description": "Field access"},
                {"name": "INHERITS", "description": "Class extends class"},
                {"name": "IMPLEMENTS", "description": "Class implements interface"},
                {"name": "EXTENDS", "description": "Interface extends interface"},
                {"name": "OVERRIDES", "description": "Method overrides method"},
                {"name": "BODY", "description": "Method/control flow body"},
                {"name": "CONDITION", "description": "If/while/for condition"},
                {"name": "THEN_BRANCH", "description": "If then branch"},
                {"name": "ELSE_BRANCH", "description": "If else branch"},
            ],
        }
    
    async def _tool_get_stats(
        self,
        graph: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Get graph statistics."""
        executor = self._get_executor(graph)
        if not executor:
            return {"success": False, "error": "No graph loaded"}
        
        graph_data = executor.graph
        metadata = graph_data.get("metadata", {})
        
        # Count nodes by type
        nodes_by_type = {}
        for node_type, nodes in executor._indexes.get("byType", {}).items():
            nodes_by_type[node_type] = len(nodes)
        
        # Count edges by type
        edges_by_type = {}
        for edge_type, edges in executor._indexes.get("edgesByType", {}).items():
            edges_by_type[edge_type] = len(edges)
        
        return {
            "success": True,
            "metadata": metadata,
            "nodeCount": sum(nodes_by_type.values()),
            "edgeCount": sum(edges_by_type.values()),
            "nodesByType": nodes_by_type,
            "edgesByType": edges_by_type,
        }
    
    async def _tool_load_graph(self, path: str) -> Dict[str, Any]:
        """Load a graph from a JSON file."""
        try:
            executor = _graph_cache.load_graph(path)
            return {
                "success": True,
                "graph": path,
                "nodeCount": len(executor._indexes.get("byType", {}).values()),
                "edgeCount": len(executor._indexes.get("edgesByType", {}).values()),
            }
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    async def _tool_list_graphs(self) -> Dict[str, Any]:
        """List all loaded graphs."""
        graphs = []
        for key, graph_data in _graph_cache.graphs.items():
            metadata = graph_data.get("metadata", {})
            graphs.append({
                "key": key,
                "nodeCount": metadata.get("nodeCount", 0),
                "edgeCount": metadata.get("edgeCount", 0),
                "fileCount": metadata.get("fileCount", 0),
            })
        
        return {
            "success": True,
            "graphs": graphs,
            "count": len(graphs),
        }
    
    async def _tool_unload_graph(self, graph: str) -> Dict[str, Any]:
        """Unload a graph from memory."""
        if graph in _graph_cache.executors:
            del _graph_cache.executors[graph]
            del _graph_cache.graphs[graph]
            return {"success": True, "graph": graph}
        else:
            return {"success": False, "error": f"Graph not loaded: {graph}"}
    
    def _get_executor(self, graph_key: Optional[str] = None) -> Optional[QueryExecutor]:
        """Get an executor for the specified graph."""
        if graph_key:
            return _graph_cache.get_executor(graph_key)
        
        # Return first available executor
        if _graph_cache.executors:
            return list(_graph_cache.executors.values())[0]
        
        return None


async def run_server(config: MCPServerConfig):
    """Run the MCP server."""
    server = GausVibeMCPServer(config)
    
    logger.info(f"Starting GausVibe MCP Server v{server.server_version}")
    logger.info(f"Server name: {server.server_name}")
    logger.info(f"Listening on {config.host}:{config.port}")
    
    # For now, just log that we're ready
    # In a real implementation, this would connect to Vibe's MCP transport
    logger.info("Server ready. Waiting for connections...")
    
    # Keep server running
    try:
        while True:
            await asyncio.sleep(1)
    except KeyboardInterrupt:
        logger.info("Shutting down...")


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="GausVibe MCP Server")
    
    parser.add_argument(
        "--host",
        type=str,
        default="127.0.0.1",
        help="Host to bind to",
    )
    
    parser.add_argument(
        "--port",
        type=int,
        default=8080,
        help="Port to listen on",
    )
    
    parser.add_argument(
        "--graph",
        type=str,
        default=None,
        help="Path to graph JSON file to load on startup",
    )
    
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Enable verbose logging",
    )
    
    args = parser.parse_args()
    
    config = MCPServerConfig(
        host=args.host,
        port=args.port,
        graph_path=args.graph,
        verbose=args.verbose,
    )
    
    if args.verbose:
        logger.setLevel(logging.DEBUG)
    
    asyncio.run(run_server(config))


if __name__ == "__main__":
    main()
