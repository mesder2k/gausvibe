#!/usr/bin/env python3
"""
GausVibe Query Tool - Execute Script

This script is called by Vibe when the gausvibe:query tool is invoked.
It loads a previously built graph and executes queries against it.
"""

import argparse
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Union


# Configuration
SKILL_ROOT = Path(__file__).parent.parent.parent


class QueryParser:
    """Parses natural language queries into structured query operations."""
    
    # Patterns for natural language queries
    PATTERNS = [
        # Find class by qualified name
        (r"find\s+class\s+([a-zA-Z0-9._]+)", "findClassByQualifiedName", {"qn": r"\1"}),
        
        # Find classes by name
        (r"find\s+classes?\s+named\s+([a-zA-Z0-9_]+)", "findClassesByName", {"name": r"\1"}),
        (r"classes?\s+named\s+([a-zA-Z0-9_]+)", "findClassesByName", {"name": r"\1"}),
        
        # All classes
        (r"all\s+classes?", "getAllClasses", {}),
        (r"list\s+all\s+classes?", "getAllClasses", {}),
        
        # Subclasses
        (r"subclasses?\s+of\s+([a-zA-Z0-9._]+)", "getSubclasses", {"className": r"\1"}),
        (r"what\s+(extends|subclasses)\s+([a-zA-Z0-9._]+)", "getSubclasses", {"className": r"\2"}),
        
        # Implementations
        (r"implementations?\s+of\s+([a-zA-Z0-9._]+)", "getImplementations", {"interfaceName": r"\1"}),
        (r"what\s+implements\s+([a-zA-Z0-9._]+)", "getImplementations", {"interfaceName": r"\1"}),
        
        # Methods in class
        (r"methods?\s+in\s+([a-zA-Z0-9._]+)", "getMethods", {"className": r"\1"}),
        (r"what\s+methods?\s+(does|has)\s+([a-zA-Z0-9._]+)\s+(have|has)?", "getMethods", {"className": r"\2"}),
        
        # Find method by signature
        (r"find\s+method\s+([a-zA-Z0-9._]+)\s*\(([^)]*)\)", "findMethodBySignature", {
            "signature": lambda m: f"{m.group(1)}({m.group(2)})"
        }),
        
        # Methods by name
        (r"methods?\s+named\s+([a-zA-Z0-9_]+)", "findMethodsByName", {"name": r"\1"}),
        
        # Callers (who calls X)
        (r"who\s+calls\s+([a-zA-Z0-9._]+)", "getCallers", {"methodName": r"\1"}),
        (r"callers?\s+of\s+([a-zA-Z0-9._]+)", "getCallers", {"methodName": r"\1"}),
        (r"what\s+calls\s+([a-zA-Z0-9._]+)", "getCallers", {"methodName": r"\1"}),
        
        # Callees (what does X call)
        (r"what\s+does\s+([a-zA-Z0-9._]+)\s+call", "getCallees", {"methodName": r"\1"}),
        (r"what\s+calls?\s+([a-zA-Z0-9._]+)", "getCallees", {"methodName": r"\1"}),
        (r"callees?\s+of\s+([a-zA-Z0-9._]+)", "getCallees", {"methodName": r"\1"}),
        
        # Fields in class
        (r"fields?\s+in\s+([a-zA-Z0-9._]+)", "getFields", {"className": r"\1"}),
        (r"what\s+fields?\s+(does|has)\s+([a-zA-Z0-9._]+)\s+(have|has)?", "getFields", {"className": r"\2"}),
        
        # Variables in method
        (r"variables?\s+in\s+([a-zA-Z0-9._]+)", "getVariables", {"methodName": r"\1"}),
        
        # Superclass
        (r"superclass\s+of\s+([a-zA-Z0-9._]+)", "getSuperclass", {"className": r"\1"}),
        (r"what\s+does\s+([a-zA-Z0-9._]+)\s+extend", "getSuperclass", {"className": r"\1"}),
        
        # Overridden method
        (r"overridden\s+method\s+of\s+([a-zA-Z0-9._]+)", "getOverriddenMethod", {"methodName": r"\1"}),
        
        # Statements in method
        (r"statements?\s+in\s+([a-zA-Z0-9._]+)", "getStatements", {"methodName": r"\1"}),
        
        # Statement at line
        (r"statement\s+at\s+line\s+(\d+)", "getStatementAt", {"line": r"\1"}),
        
        # Nodes in file
        (r"nodes?\s+in\s+file\s+([^\s]+)", "getNodesInFile", {"file": r"\1"}),
    ]
    
    # Structured query pattern
    STRUCTURED_PATTERN = re.compile(
        r"^([a-zA-Z0-9_]+)\(\s*([^)]*)\s*\)$"
    )
    
    @classmethod
    def parse(cls, query: str) -> Dict[str, Any]:
        """Parse a query string into a structured query."""
        query = query.strip()
        
        # Try structured format first
        structured = cls._parse_structured(query)
        if structured:
            return structured
        
        # Try natural language patterns
        for pattern, method, params in cls.PATTERNS:
            match = re.match(pattern, query, re.IGNORECASE)
            if match:
                # Handle lambda params
                resolved_params = {}
                for key, value in params.items():
                    if callable(value):
                        resolved_params[key] = value(match)
                    else:
                        resolved_params[key] = value
                
                return {
                    "method": method,
                    "params": resolved_params,
                    "originalQuery": query,
                }
        
        # Default: try to interpret as a method name
        return {
            "method": "interpret",
            "params": {"query": query},
            "originalQuery": query,
        }
    
    @classmethod
    def _parse_structured(cls, query: str) -> Optional[Dict[str, Any]]:
        """Parse structured query format: methodName(param1=value1, param2=value2)"""
        match = cls.STRUCTURED_PATTERN.match(query)
        if not match:
            return None
        
        method_name = match.group(1)
        args_str = match.group(2)
        
        params = {}
        if args_str.strip():
            # Parse key=value pairs
            for pair in args_str.split(","):
                pair = pair.strip()
                if "=" in pair:
                    key, value = pair.split("=", 1)
                    key = key.strip()
                    value = value.strip().strip('"\'')
                    params[key] = value
        
        return {
            "method": method_name,
            "params": params,
            "originalQuery": query,
        }


class QueryExecutor:
    """Executes queries against a loaded graph."""
    
    def __init__(self, graph: Dict[str, Any], verbose: bool = False):
        self.graph = graph
        self.verbose = verbose
        self._indexes: Dict[str, Dict] = {}
        
        # Build indexes for fast lookup
        self._build_indexes()
    
    def _build_indexes(self):
        """Build lookup indexes from the graph."""
        nodes = self.graph.get("nodes", [])
        edges = self.graph.get("edges", [])
        
        # Index by type
        self._indexes["byType"] = {}
        for node in nodes:
            node_type = node.get("type", "UNKNOWN")
            if node_type not in self._indexes["byType"]:
                self._indexes["byType"][node_type] = []
            self._indexes["byType"][node_type].append(node)
        
        # Index by ID
        self._indexes["byId"] = {node["id"]: node for node in nodes}
        
        # Index by qualified name (for classes, methods, etc.)
        self._indexes["byQualifiedName"] = {}
        for node in nodes:
            qn = node.get("qualifiedName")
            if qn:
                self._indexes["byQualifiedName"][qn] = node
        
        # Index by name
        self._indexes["byName"] = {}
        for node in nodes:
            name = node.get("name")
            if name:
                if name not in self._indexes["byName"]:
                    self._indexes["byName"][name] = []
                self._indexes["byName"][name].append(node)
        
        # Edge indexes
        self._indexes["edgesByType"] = {}
        self._indexes["edgesByFrom"] = {}
        self._indexes["edgesByTo"] = {}
        
        for edge in edges:
            edge_type = edge.get("type", "UNKNOWN")
            from_id = edge.get("fromId", "")
            to_id = edge.get("toId", "")
            
            if edge_type not in self._indexes["edgesByType"]:
                self._indexes["edgesByType"][edge_type] = []
            self._indexes["edgesByType"][edge_type].append(edge)
            
            if from_id not in self._indexes["edgesByFrom"]:
                self._indexes["edgesByFrom"][from_id] = []
            self._indexes["edgesByFrom"][from_id].append(edge)
            
            if to_id not in self._indexes["edgesByTo"]:
                self._indexes["edgesByTo"][to_id] = []
            self._indexes["edgesByTo"][to_id].append(edge)
    
    def execute(self, parsed_query: Dict[str, Any], limit: int = 100) -> Dict[str, Any]:
        """Execute a parsed query."""
        method = parsed_query.get("method")
        params = parsed_query.get("params", {})
        
        start_time = time.time()
        
        try:
            # Route to appropriate handler
            handler_method = getattr(self, f"_handle_{method}", None)
            if handler_method:
                results = handler_method(**params)
            else:
                results = self._handle_interpret(query=parsed_query.get("originalQuery", ""))
            
            # Apply limit
            if isinstance(results, list):
                truncated = len(results) > limit
                results = results[:limit]
            else:
                truncated = False
            
            elapsed_ms = int((time.time() - start_time) * 1000)
            
            return {
                "success": True,
                "results": results,
                "count": len(results) if isinstance(results, list) else 1,
                "total": len(results) if isinstance(results, list) else 1,
                "queryTimeMs": elapsed_ms,
                "truncated": truncated,
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
                "queryTimeMs": int((time.time() - start_time) * 1000),
            }
    
    def _resolve_class(self, className: str) -> Optional[Dict]:
        """Resolve a class by name or qualified name."""
        # Try qualified name first
        if className in self._indexes["byQualifiedName"]:
            return self._indexes["byQualifiedName"][className]
        
        # Try by name
        if className in self._indexes["byName"]:
            classes = self._indexes["byName"][className]
            # Prefer non-test classes
            for cls in classes:
                if cls.get("type") == "CLASS":
                    return cls
            return classes[0] if classes else None
        
        return None
    
    def _resolve_method(self, methodName: str) -> Optional[Dict]:
        """Resolve a method by name or qualified name."""
        # Try qualified name
        if methodName in self._indexes["byQualifiedName"]:
            return self._indexes["byQualifiedName"][methodName]
        
        # Try by name
        if methodName in self._indexes["byName"]:
            methods = self._indexes["byName"][methodName]
            return methods[0] if methods else None
        
        return None
    
    # Query handlers
    
    def _handle_findClassByQualifiedName(self, qn: str) -> List[Dict]:
        """Find a class by its qualified name."""
        cls = self._indexes["byQualifiedName"].get(qn)
        if cls:
            return [self._format_class(cls)]
        return []
    
    def _handle_findClassesByName(self, name: str) -> List[Dict]:
        """Find classes by simple name."""
        classes = self._indexes["byName"].get(name, [])
        return [self._format_class(cls) for cls in classes if cls.get("type") == "CLASS"]
    
    def _handle_getAllClasses(self) -> List[Dict]:
        """Get all classes."""
        classes = self._indexes["byType"].get("CLASS", [])
        return [self._format_class(cls) for cls in classes]
    
    def _handle_getSubclasses(self, className: str) -> List[Dict]:
        """Get all subclasses of a class."""
        parent = self._resolve_class(className)
        if not parent:
            return []
        
        subclasses = []
        inherits_edges = self._indexes["edgesByType"].get("INHERITS", [])
        
        for edge in inherits_edges:
            if edge.get("fromId") == parent.get("id"):
                child_id = edge.get("toId")
                child = self._indexes["byId"].get(child_id)
                if child:
                    subclasses.append({
                        "type": "INHERITANCE",
                        "relationship": "INHERITS",
                        "parent": self._format_class(parent),
                        "child": self._format_class(child),
                    })
        
        return subclasses
    
    def _handle_getImplementations(self, interfaceName: str) -> List[Dict]:
        """Get all implementations of an interface."""
        iface = self._resolve_class(interfaceName)
        if not iface:
            return []
        
        implementations = []
        implements_edges = self._indexes["edgesByType"].get("IMPLEMENTS", [])
        
        for edge in implements_edges:
            if edge.get("fromId") == iface.get("id"):
                impl_id = edge.get("toId")
                impl = self._indexes["byId"].get(impl_id)
                if impl:
                    implementations.append({
                        "type": "INHERITANCE",
                        "relationship": "IMPLEMENTS",
                        "parent": self._format_class(iface),
                        "child": self._format_class(impl),
                    })
        
        return implementations
    
    def _handle_getMethods(self, className: str) -> List[Dict]:
        """Get all methods in a class."""
        cls = self._resolve_class(className)
        if not cls:
            return []
        
        # Find HAS_METHOD edges from this class
        has_method_edges = self._indexes["edgesByFrom"].get(cls.get("id"), [])
        methods = []
        
        for edge in has_method_edges:
            if edge.get("type") == "HAS_METHOD":
                method_id = edge.get("toId")
                method = self._indexes["byId"].get(method_id)
                if method:
                    methods.append(self._format_method(method))
        
        return methods
    
    def _handle_findMethodBySignature(self, signature: str) -> List[Dict]:
        """Find a method by its signature."""
        # Search all methods
        methods = self._indexes["byType"].get("METHOD", [])
        
        for method in methods:
            if method.get("signature") == signature:
                return [self._format_method(method)]
        
        return []
    
    def _handle_findMethodsByName(self, name: str) -> List[Dict]:
        """Find methods by name."""
        methods = self._indexes["byName"].get(name, [])
        return [self._format_method(m) for m in methods if m.get("type") == "METHOD"]
    
    def _handle_getCallers(self, methodName: str) -> List[Dict]:
        """Get all methods that call the specified method."""
        target_method = self._resolve_method(methodName)
        if not target_method:
            return []
        
        # Find CALLS edges to this method
        calls_edges = self._indexes["edgesByTo"].get(target_method.get("id"), [])
        callers = []
        
        for edge in calls_edges:
            if edge.get("type") == "CALLS":
                caller_id = edge.get("fromId")
                caller = self._indexes["byId"].get(caller_id)
                if caller:
                    callers.append({
                        "type": "CALL",
                        "caller": self._format_method(caller),
                        "callee": self._format_method(target_method),
                        "callType": "DIRECT",
                    })
        
        return callers
    
    def _handle_getCallees(self, methodName: str) -> List[Dict]:
        """Get all methods called by the specified method."""
        source_method = self._resolve_method(methodName)
        if not source_method:
            return []
        
        # Find CALLS edges from this method
        calls_edges = self._indexes["edgesByFrom"].get(source_method.get("id"), [])
        callees = []
        
        for edge in calls_edges:
            if edge.get("type") == "CALLS":
                callee_id = edge.get("toId")
                callee = self._indexes["byId"].get(callee_id)
                if callee:
                    callees.append({
                        "type": "CALL",
                        "caller": self._format_method(source_method),
                        "callee": self._format_method(callee),
                        "callType": "DIRECT",
                    })
        
        return callees
    
    def _handle_getFields(self, className: str) -> List[Dict]:
        """Get all fields in a class."""
        cls = self._resolve_class(className)
        if not cls:
            return []
        
        # Find HAS_FIELD edges from this class
        has_field_edges = self._indexes["edgesByFrom"].get(cls.get("id"), [])
        fields = []
        
        for edge in has_field_edges:
            if edge.get("type") == "HAS_FIELD":
                field_id = edge.get("toId")
                field = self._indexes["byId"].get(field_id)
                if field:
                    fields.append(self._format_field(field))
        
        return fields
    
    def _handle_getVariables(self, methodName: str) -> List[Dict]:
        """Get all variables in a method."""
        method = self._resolve_method(methodName)
        if not method:
            return []
        
        # Find DECLARES edges from the method's body
        body_edges = self._indexes["edgesByFrom"].get(method.get("id"), [])
        variables = []
        
        for edge in body_edges:
            if edge.get("type") == "BODY":
                body_id = edge.get("toId")
                body = self._indexes["byId"].get(body_id)
                if body:
                    # Find DECLARES edges from body
                    declares_edges = self._indexes["edgesByFrom"].get(body_id, [])
                    for decl_edge in declares_edges:
                        if decl_edge.get("type") == "DECLARES":
                            var_id = decl_edge.get("toId")
                            var = self._indexes["byId"].get(var_id)
                            if var and var.get("type") == "VARIABLE":
                                variables.append(self._format_variable(var))
        
        return variables
    
    def _handle_getSuperclass(self, className: str) -> List[Dict]:
        """Get the superclass of a class."""
        cls = self._resolve_class(className)
        if not cls:
            return []
        
        # Find INHERITS edges from this class
        inherits_edges = self._indexes["edgesByFrom"].get(cls.get("id"), [])
        
        for edge in inherits_edges:
            if edge.get("type") == "INHERITS":
                parent_id = edge.get("toId")
                parent = self._indexes["byId"].get(parent_id)
                if parent:
                    return [self._format_class(parent)]
        
        return []
    
    def _handle_getOverriddenMethod(self, methodName: str) -> List[Dict]:
        """Get the method that this method overrides."""
        method = self._resolve_method(methodName)
        if not method:
            return []
        
        # Find OVERRIDES edges from this method
        overrides_edges = self._indexes["edgesByFrom"].get(method.get("id"), [])
        
        for edge in overrides_edges:
            if edge.get("type") == "OVERRIDES":
                parent_id = edge.get("toId")
                parent = self._indexes["byId"].get(parent_id)
                if parent:
                    return [self._format_method(parent)]
        
        return []
    
    def _handle_getStatements(self, methodName: str) -> List[Dict]:
        """Get all statements in a method."""
        method = self._resolve_method(methodName)
        if not method:
            return []
        
        statements = []
        body_edges = self._indexes["edgesByFrom"].get(method.get("id"), [])
        
        for edge in body_edges:
            if edge.get("type") == "BODY":
                body_id = edge.get("toId")
                # Get CONTAINS edges from body
                contains_edges = self._indexes["edgesByFrom"].get(body_id, [])
                for contains_edge in contains_edges:
                    if contains_edge.get("type") == "CONTAINS":
                        stmt_id = contains_edge.get("toId")
                        stmt = self._indexes["byId"].get(stmt_id)
                        if stmt:
                            statements.append(self._format_node(stmt))
        
        return statements
    
    def _handle_getStatementAt(self, line: int, methodName: Optional[str] = None) -> List[Dict]:
        """Get statement at a specific line."""
        statements = []
        
        for node in self._indexes["byType"].get("STATEMENT", []):
            start = node.get("startPosition", {})
            if start.get("line") == line:
                statements.append(self._format_node(node))
        
        return statements
    
    def _handle_getNodesInFile(self, file: str) -> List[Dict]:
        """Get all nodes in a specific file."""
        nodes = []
        for node in self._indexes["byType"].values():
            for n in node:
                if n.get("file") == file:
                    nodes.append(self._format_node(n))
        return nodes
    
    def _handle_interpret(self, query: str) -> List[Dict]:
        """Interpret a generic query."""
        # Try to find nodes matching the query as a name
        results = []
        
        # Search in class names
        for cls in self._indexes["byType"].get("CLASS", []):
            if query.lower() in cls.get("name", "").lower():
                results.append(self._format_class(cls))
        
        # Search in method names
        for method in self._indexes["byType"].get("METHOD", []):
            if query.lower() in method.get("name", "").lower():
                results.append(self._format_method(method))
        
        return results
    
    # Format helpers
    
    def _format_class(self, node: Dict) -> Dict:
        """Format a class node for output."""
        return {
            "type": "CLASS",
            "class": {
                "id": node.get("id"),
                "name": node.get("name"),
                "qualifiedName": node.get("qualifiedName"),
                "file": node.get("file"),
                "startPosition": node.get("startPosition"),
                "endPosition": node.get("endPosition"),
                "modifiers": node.get("modifiers", []),
                "superclass": node.get("superclass"),
                "interfaces": node.get("interfaces", []),
                "isInterface": node.get("isInterface", False),
                "isEnum": node.get("isEnum", False),
                "isAbstract": node.get("isAbstract", False),
                "methodCount": len(self._indexes["edgesByFrom"].get(node.get("id"), [])),
                "fieldCount": sum(
                    1 for e in self._indexes["edgesByFrom"].get(node.get("id"), [])
                    if e.get("type") == "HAS_FIELD"
                ),
            },
        }
    
    def _format_method(self, node: Dict) -> Dict:
        """Format a method node for output."""
        return {
            "type": "METHOD",
            "method": {
                "id": node.get("id"),
                "name": node.get("name"),
                "qualifiedName": node.get("qualifiedName"),
                "signature": node.get("signature"),
                "file": node.get("file"),
                "startPosition": node.get("startPosition"),
                "endPosition": node.get("endPosition"),
                "returnType": node.get("returnType"),
                "modifiers": node.get("modifiers", []),
                "isConstructor": node.get("isConstructor", False),
                "isStatic": node.get("isStatic", False),
                "isAbstract": node.get("isAbstract", False),
                "parameterCount": len(self._indexes["edgesByFrom"].get(node.get("id"), [])),
                "thrownExceptions": node.get("thrownExceptions", []),
                "belongingClass": node.get("belongingClass"),
            },
        }
    
    def _format_field(self, node: Dict) -> Dict:
        """Format a field node for output."""
        return {
            "type": "FIELD",
            "field": {
                "id": node.get("id"),
                "name": node.get("name"),
                "qualifiedName": node.get("qualifiedName"),
                "type": node.get("type"),
                "file": node.get("file"),
                "startPosition": node.get("startPosition"),
                "endPosition": node.get("endPosition"),
                "modifiers": node.get("modifiers", []),
                "isStatic": node.get("isStatic", False),
                "isFinal": node.get("isFinal", False),
                "belongingClass": node.get("belongingClass"),
            },
        }
    
    def _format_variable(self, node: Dict) -> Dict:
        """Format a variable node for output."""
        return {
            "type": "VARIABLE",
            "variable": {
                "id": node.get("id"),
                "name": node.get("name"),
                "type": node.get("type"),
                "file": node.get("file"),
                "startPosition": node.get("startPosition"),
                "endPosition": node.get("endPosition"),
                "scopeMethod": node.get("scopeMethod"),
                "isFinal": node.get("isFinal", False),
            },
        }
    
    def _format_node(self, node: Dict) -> Dict:
        """Format any node for output."""
        node_type = node.get("type", "UNKNOWN")
        
        formatters = {
            "CLASS": self._format_class,
            "INTERFACE": self._format_class,
            "ENUM": self._format_class,
            "METHOD": self._format_method,
            "CONSTRUCTOR": self._format_method,
            "FIELD": self._format_field,
            "VARIABLE": self._format_variable,
        }
        
        formatter = formatters.get(node_type, lambda n: {"type": node_type, node_type.lower(): n})
        return formatter(node)


class QueryConfig:
    """Configuration for query execution."""
    
    def __init__(
        self,
        graph: str,
        query: str,
        format: str = "json",
        limit: int = 100,
        include_source: bool = False,
        timeout: int = 30,
    ):
        self.graph = Path(graph).absolute()
        self.query = query
        self.format = format
        self.limit = limit
        self.include_source = include_source
        self.timeout = timeout
        
        # Validate graph file exists
        if not self.graph.exists():
            raise ValueError(f"Graph file does not exist: {self.graph}")


def format_results(results: Dict[str, Any], config: QueryConfig) -> Dict[str, Any]:
    """Format results based on output format."""
    if config.format == "json":
        return results
    
    if config.format == "text":
        text_results = []
        for result in results.get("results", []):
            text = format_result_text(result)
            if text:
                text_results.append({"type": "GENERIC", "data": {"text": text}})
        results["results"] = text_results
        return results
    
    if config.format == "summary":
        summary = format_summary(results)
        results["results"] = [{"type": "GENERIC", "data": {"summary": summary}}]
        return results
    
    return results


def format_result_text(result: Dict) -> str:
    """Format a single result as text."""
    result_type = result.get("type")
    
    if result_type == "CLASS":
        cls = result.get("class", {})
        name = cls.get("name", "Unknown")
        qn = cls.get("qualifiedName", "")
        if qn and qn != name:
            return f"{name} ({qn})"
        return name
    
    if result_type == "METHOD":
        method = result.get("method", {})
        name = method.get("name", "Unknown")
        sig = method.get("signature", "")
        return_type = method.get("returnType", "")
        file = method.get("file", "")
        line = method.get("startPosition", {}).get("line", 0)
        
        parts = [name]
        if sig:
            parts.append(f"({sig})")
        if return_type:
            parts.append(f"-> {return_type}")
        if file and line:
            parts.append(f"at {file}:{line}")
        return " ".join(parts)
    
    if result_type == "FIELD":
        field = result.get("field", {})
        name = field.get("name", "Unknown")
        type_ = field.get("type", "")
        return f"{name}: {type_}"
    
    if result_type == "VARIABLE":
        var = result.get("variable", {})
        name = var.get("name", "Unknown")
        type_ = var.get("type", "")
        return f"{name}: {type_}"
    
    if result_type == "CALL":
        call = result
        caller = call.get("caller", {})
        callee = call.get("callee", {})
        caller_name = caller.get("name", "Unknown")
        callee_name = callee.get("name", "Unknown")
        file = caller.get("file", "")
        line = caller.get("line", 0)
        
        result = f"{caller_name} calls {callee_name}"
        if file and line:
            result += f" at {file}:{line}"
        return result
    
    if result_type == "INHERITANCE":
        rel = result.get("relationship", "")
        parent = result.get("parent", {})
        child = result.get("child", {})
        parent_name = parent.get("name", "Unknown")
        child_name = child.get("name", "Unknown")
        return f"{child_name} {rel.lower()} {parent_name}"
    
    return str(result)


def format_summary(results: Dict) -> str:
    """Format results as a summary."""
    count = results.get("count", 0)
    query = results.get("originalQuery", "query")
    
    if count == 0:
        return f"No results found for '{query}'"
    
    if count == 1:
        result = results.get("results", [{}])[0]
        text = format_result_text(result)
        return f"Found 1 result for '{query}': {text}"
    
    # Multiple results
    result_type = results.get("results", [{}])[0].get("type", "items")
    
    if result_type == "CALL":
        callers = [format_result_text(r) for r in results.get("results", [])]
        return f"Found {count} callers: {', '.join(callers[:5])}" + ("..." if count > 5 else "")
    
    if result_type == "CLASS":
        classes = [format_result_text(r) for r in results.get("results", [])]
        return f"Found {count} classes: {', '.join(classes[:5])}" + ("..." if count > 5 else "")
    
    if result_type == "METHOD":
        methods = [format_result_text(r) for r in results.get("results", [])]
        return f"Found {count} methods: {', '.join(methods[:5])}" + ("..." if count > 5 else "")
    
    return f"Found {count} results for '{query}'"


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="Query Java code graph")
    
    parser.add_argument(
        "--graph",
        type=str,
        required=True,
        help="Path to graph JSON file",
    )
    
    parser.add_argument(
        "--query",
        type=str,
        required=True,
        help="Query to execute",
    )
    
    parser.add_argument(
        "--format",
        type=str,
        default="json",
        choices=["json", "text", "summary"],
        help="Output format",
    )
    
    parser.add_argument(
        "--limit",
        type=int,
        default=100,
        help="Maximum results to return",
    )
    
    parser.add_argument(
        "--include_source",
        type=bool,
        default=False,
        help="Include source code snippets",
    )
    
    parser.add_argument(
        "--timeout",
        type=int,
        default=30,
        help="Query timeout in seconds",
    )
    
    return parser.parse_args()


def main():
    """Main entry point."""
    try:
        args = parse_args()
        
        # Convert string "true"/"false" to boolean for args that come as strings
        for bool_arg in ["include_source"]:
            if isinstance(getattr(args, bool_arg), str):
                setattr(args, bool_arg, getattr(args, bool_arg).lower() == "true")
        
        config = QueryConfig(
            graph=args.graph,
            query=args.query,
            format=args.format,
            limit=args.limit,
            include_source=args.include_source,
            timeout=args.timeout,
        )
        
        # Load graph
        with open(config.graph, "r") as f:
            graph_data = json.load(f)
        
        # Parse query
        parsed_query = QueryParser.parse(config.query)
        
        # Execute query
        executor = QueryExecutor(graph_data, verbose=False)
        results = executor.execute(parsed_query, limit=config.limit)
        
        # Add original query to results
        results["query"] = config.query
        
        # Format results
        formatted = format_results(results, config)
        
        # Output
        print(json.dumps(formatted, indent=2))
        
    except Exception as e:
        error_result = {
            "success": False,
            "error": str(e),
            "query": args.query if hasattr(args, 'query') else None,
        }
        print(json.dumps(error_result, indent=2))
        sys.exit(1)


if __name__ == "__main__":
    main()
