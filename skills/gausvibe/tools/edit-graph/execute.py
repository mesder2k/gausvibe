#!/usr/bin/env python3
"""
GausVibe Edit Tool - Execute Script

This script is called by Vibe when the gausvibe:edit tool is invoked.
It loads a previously built graph and applies AST operations to it.
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional


# Configuration
SKILL_ROOT = Path(__file__).parent.parent.parent


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="Edit Java code graph with AST operations")
    
    parser.add_argument(
        "--graph",
        type=str,
        required=True,
        help="Path to graph JSON file",
    )
    
    parser.add_argument(
        "--operations",
        type=str,
        required=True,
        help="JSON array of AST operations to apply",
    )
    
    parser.add_argument(
        "--dry_run",
        type=bool,
        default=False,
        help="Preview changes without writing files",
    )
    
    parser.add_argument(
        "--output",
        type=str,
        default="json",
        choices=["json", "diff", "summary", "text"],
        help="Output format",
    )
    
    parser.add_argument(
        "--verbose",
        type=bool,
        default=False,
        help="Enable detailed logging",
    )
    
    return parser.parse_args()


def get_java_command() -> List[str]:
    """Get the Java command to execute."""
    import os
    
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        java_cmd = [str(Path(java_home) / "bin" / "java")]
    else:
        java_cmd = ["java"]
    
    return java_cmd


def get_classpath() -> str:
    """Get the classpath for running GausVibe."""
    target_dir = SKILL_ROOT / "target"
    
    # Pre-built JAR
    jar_path = target_dir / "gausvibe-1.0.0.jar"
    if jar_path.exists():
        return str(jar_path)
    
    # CLI JAR
    cli_jar = target_dir / "gausvibe-1.0.0-cli.jar"
    if cli_jar.exists():
        return str(cli_jar)
    
    raise RuntimeError(
        "GausVibe not built. Please run 'mvn clean package' first."
    )


def execute_java_edit(graph_path: str, operations_json: str, 
                      dry_run: bool, output_format: str, verbose: bool) -> Dict[str, Any]:
    """Execute the Java edit command."""
    java_cmd = get_java_command()
    classpath = get_classpath()
    
    # Build command
    cmd = [
        *java_cmd,
        "-cp", classpath,
        "dk.gausdalfind.cli.CommandLineInterface",
        "edit",
        "--graph", graph_path,
        "--operations", operations_json,
    ]
    
    if dry_run:
        cmd.append("--dry-run")
    
    if output_format:
        cmd.extend(["--output", output_format])
    
    if verbose:
        cmd.append("--verbose")
    
    if verbose:
        print(f"[DEBUG] Running: {' '.join(cmd)}", file=sys.stderr)
    
    # Execute
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=120,  # 2 minutes
        )
        
        if result.returncode != 0:
            error_msg = result.stderr or result.stdout or "Unknown error"
            return {
                "success": False,
                "error": f"Edit failed: {error_msg}",
                "stderr": result.stderr,
                "stdout": result.stdout,
                "returncode": result.returncode,
            }
        
        # Try to parse as JSON first
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError:
            # Return as text
            return {
                "success": True,
                "output": result.stdout,
                "stderr": result.stderr,
            }
        
    except subprocess.TimeoutExpired:
        return {
            "success": False,
            "error": "Edit timed out after 2 minutes",
        }
    except Exception as e:
        return {
            "success": False,
            "error": str(e),
        }


def parse_operations_json(operations_str: str) -> List[Dict[str, Any]]:
    """Parse the operations JSON string."""
    try:
        operations = json.loads(operations_str)
        if isinstance(operations, list):
            return operations
        elif isinstance(operations, dict):
            return [operations]
        else:
            raise ValueError("Operations must be a JSON array or object")
    except json.JSONDecodeError as e:
        # Try to parse as a simple operation string
        if "{" in operations_str and "}" in operations_str:
            raise ValueError(f"Invalid JSON: {e}")
        # Maybe it's a single operation in a simplified format
        return [{"operation": operations_str}]


def validate_operations(operations: List[Dict[str, Any]]) -> List[str]:
    """Validate operations and return list of errors."""
    errors = []
    
    for i, op in enumerate(operations):
        if not isinstance(op, dict):
            errors.append(f"Operation {i+1} is not a JSON object")
            continue
        
        if "type" not in op:
            errors.append(f"Operation {i+1} missing required field 'type'")
        else:
            op_type = op["type"]
            if op_type not in ["ADD_METHOD", "REMOVE_METHOD", "REPLACE_METHOD_BODY", 
                               "ADD_FIELD", "REMOVE_FIELD", "ADD_IMPORT", "REMOVE_IMPORT"]:
                errors.append(f"Operation {i+1} has unknown type: {op_type}")
    
    return errors


def main():
    """Main entry point."""
    try:
        args = parse_args()
        
        # Convert string "true"/"false" to boolean for args that come as strings
        for bool_arg in ["dry_run", "verbose"]:
            if isinstance(getattr(args, bool_arg), str):
                setattr(args, bool_arg, getattr(args, bool_arg).lower() == "true")
        
        # Validate graph file exists
        graph_path = Path(args.graph).absolute()
        if not graph_path.exists():
            raise ValueError(f"Graph file does not exist: {graph_path}")
        
        # Validate operations JSON
        try:
            operations = parse_operations_json(args.operations)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid operations JSON: {e}")
        except ValueError as e:
            raise ValueError(f"Invalid operations format: {e}")
        
        # Validate operations
        errors = validate_operations(operations)
        if errors:
            result = {
                "success": False,
                "errors": errors,
                "validationErrors": len(errors),
            }
            print(json.dumps(result, indent=2))
            sys.exit(1)
        
        # Execute edit
        result = execute_java_edit(
            str(graph_path),
            args.operations,
            args.dry_run,
            args.output,
            args.verbose,
        )
        
        # Output result as JSON
        print(json.dumps(result, indent=2))
        
    except Exception as e:
        error_result = {
            "success": False,
            "error": str(e),
        }
        print(json.dumps(error_result, indent=2))
        sys.exit(1)


if __name__ == "__main__":
    main()
