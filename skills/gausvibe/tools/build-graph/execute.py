#!/usr/bin/env python3
"""
GausVibe Build Tool - Execute Script

This script is called by Vibe when the gausvibe:build tool is invoked.
It orchestrates the Java code parsing and graph construction.
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


# Configuration
SKILL_ROOT = Path(__file__).parent.parent.parent
JAVA_SRC = SKILL_ROOT / "src" / "main" / "java"
CACHE_DIR = Path.home() / ".vibe" / "cache" / "gausvibe"

# Ensure cache directory exists
CACHE_DIR.mkdir(parents=True, exist_ok=True)


class BuildConfig:
    """Configuration for graph building."""
    
    def __init__(
        self,
        path: str,
        output: Optional[str] = None,
        include_test: bool = True,
        exclude: Optional[str] = None,
        parallel: bool = True,
        verbose: bool = False,
    ):
        self.path = Path(path).absolute()
        self.output = Path(output).absolute() if output else None
        self.include_test = include_test
        self.exclude = exclude
        self.parallel = parallel
        self.verbose = verbose
        
        # Validate path exists
        if not self.path.exists():
            raise ValueError(f"Path does not exist: {self.path}")
        
        if not self.path.is_dir():
            raise ValueError(f"Path is not a directory: {self.path}")


class GraphBuilder:
    """Builds Java code graphs using the GausVibe library."""
    
    def __init__(self, config: BuildConfig):
        self.config = config
        self._java_cmd: Optional[List[str]] = None
    
    def _get_java_command(self) -> List[str]:
        """Get the Java command to execute."""
        if self._java_cmd is not None:
            return self._java_cmd
        
        # Find Java
        java_home = os.environ.get("JAVA_HOME")
        if java_home:
            java_cmd = [str(Path(java_home) / "bin" / "java")]
        else:
            java_cmd = ["java"]
        
        # Check if Java is available
        try:
            result = subprocess.run(
                [java_cmd[0], "-version"],
                capture_output=True,
                timeout=5,
            )
            if result.returncode != 0:
                raise RuntimeError("Java not found or not working. Please install Java 17+.")
        except FileNotFoundError:
            raise RuntimeError("Java not found. Please install Java 17+ or set JAVA_HOME.")
        
        # Add memory settings
        java_cmd.extend(["-Xmx2g", "-Xms256m"])
        
        self._java_cmd = java_cmd
        return self._java_cmd
    
    def _get_classpath(self) -> str:
        """Get the classpath for running GausVibe."""
        # Look for built JAR
        target_dir = SKILL_ROOT / "target"
        
        # Shaded JAR with all dependencies (from mvn package)
        shaded_jar = target_dir / "gausvibe-1.0.0-all.jar"
        if shaded_jar.exists():
            return str(shaded_jar)

        # Pre-built JAR (from mvn package)
        jar_path = target_dir / "gausvibe-1.0.0.jar"
        if jar_path.exists():
            return str(jar_path)
        
        # CLI JAR
        cli_jar = target_dir / "gausvibe-1.0.0-cli.jar"
        if cli_jar.exists():
            return str(cli_jar)
        
        # Need to build
        raise RuntimeError(
            "GausVibe not built. Please run 'mvn clean package' first."
        )
    
    def _build_cache_key(self) -> str:
        """Build a cache key based on file paths and hashes."""
        # Simple cache key based on path and modification times
        files = []
        for root, dirs, filenames in os.walk(self.config.path):
            # Skip common non-source directories
            dirs[:] = [d for d in dirs if d not in ("target", "build", ".git", "node_modules")]
            
            for filename in filenames:
                if filename.endswith(".java"):
                    filepath = Path(root) / filename
                    # Use modification time as part of cache key
                    mtime = filepath.stat().st_mtime
                    files.append(f"{filepath}:{mtime}")
        
        files.sort()
        cache_key = ":".join([self.config.path.name] + files)
        return cache_key
    
    def _get_cached_graph(self) -> Optional[str]:
        """Check if a cached graph exists for this configuration."""
        cache_key = self._build_cache_key()
        cache_file = CACHE_DIR / f"{cache_key[:2]}/{cache_key}.json"
        cache_file.parent.mkdir(parents=True, exist_ok=True)
        
        if cache_file.exists():
            if self.config.verbose:
                print(f"[CACHE] Using cached graph: {cache_file}", file=sys.stderr)
            return str(cache_file)
        
        return None
    
    def _store_cached_graph(self, graph_path: str) -> None:
        """Store the graph in cache."""
        cache_key = self._build_cache_key()
        cache_file = CACHE_DIR / f"{cache_key[:2]}/{cache_key}.json"
        cache_file.parent.mkdir(parents=True, exist_ok=True)
        
        # Copy the graph to cache
        import shutil
        shutil.copy2(graph_path, cache_file)
        
        if self.config.verbose:
            print(f"[CACHE] Stored graph in cache: {cache_file}", file=sys.stderr)
    
    def build(self) -> Dict[str, Any]:
        """Build the graph and return results."""
        start_time = datetime.now(timezone.utc)
        
        # Check cache first
        cached = self._get_cached_graph()
        if cached:
            return self._load_graph_from_file(cached, start_time)
        
        # Determine output path
        output_path = self.config.output
        if output_path is None:
            output_path = Path(tempfile.gettempdir()) / f"gausvibe-{start_time.timestamp()}.json"
        
        # Build via Java
        result = self._build_via_java(output_path)
        
        # Store in cache
        if result.get("success", False):
            self._store_cached_graph(str(output_path))
        
        return result
    
    def _build_via_java(self, output_path: Path) -> Dict[str, Any]:
        """Build graph by executing Java code."""
        java_cmd = self._get_java_command()
        classpath = self._get_classpath()
        
        # Build command
        cmd = [
            *java_cmd,
            "-cp", classpath,
            "dk.gausdalfind.Main",
            "build",
            "--path", str(self.config.path),
            "--output", str(output_path),
        ]
        
        if not self.config.include_test:
            cmd.append("--exclude-test")
        
        if self.config.exclude:
            cmd.extend(["--exclude", self.config.exclude])
        
        if not self.config.parallel:
            cmd.append("--no-parallel")
        
        if self.config.verbose:
            cmd.append("--verbose")
            print(f"[DEBUG] Running: {' '.join(cmd)}", file=sys.stderr)
        
        # Execute
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=300,  # 5 minutes
            )
            
            if result.returncode != 0:
                error_msg = result.stderr or result.stdout or "Unknown error"
                return {
                    "success": False,
                    "error": f"Build failed: {error_msg}",
                    "stderr": result.stderr,
                    "stdout": result.stdout,
                    "returncode": result.returncode,
                }
            
            # Load the graph
            return self._load_graph_from_file(str(output_path))
            
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "error": "Build timed out after 5 minutes",
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
            }
    
    def _load_graph_from_file(self, file_path: str, start_time: Optional[datetime] = None) -> Dict[str, Any]:
        """Load and validate a graph from a JSON file."""
        start_time = start_time or datetime.now(timezone.utc)
        
        try:
            with open(file_path, "r") as f:
                graph_data = json.load(f)
            
            # Calculate parse time if not present
            end_time = datetime.now(timezone.utc)
            parse_time_ms = int((end_time - start_time).total_seconds() * 1000)
            
            # Ensure metadata exists
            if "metadata" not in graph_data:
                graph_data["metadata"] = {}
            
            graph_data["metadata"]["loadedAt"] = end_time.isoformat()
            if "parseTimeMs" not in graph_data["metadata"]:
                graph_data["metadata"]["parseTimeMs"] = parse_time_ms
            
            return {
                "success": True,
                "graph": graph_data,
                "warnings": graph_data.get("warnings", []),
                "errors": graph_data.get("errors", []),
            }
            
        except json.JSONDecodeError as e:
            return {
                "success": False,
                "error": f"Invalid graph JSON: {e}",
            }
        except Exception as e:
            return {
                "success": False,
                "error": f"Failed to load graph: {e}",
            }


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="Build Java code graph")
    
    parser.add_argument(
        "--path",
        type=str,
        required=True,
        help="Path to Java project root or source directory",
    )
    
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output file path for graph JSON",
    )
    
    parser.add_argument(
        "--include_test",
        type=bool,
        default=True,
        help="Include test source directories",
    )
    
    parser.add_argument(
        "--exclude",
        type=str,
        default=None,
        help="Glob pattern for files to exclude",
    )
    
    parser.add_argument(
        "--parallel",
        type=bool,
        default=True,
        help="Parse files in parallel",
    )
    
    parser.add_argument(
        "--verbose",
        type=bool,
        default=False,
        help="Enable verbose logging",
    )
    
    return parser.parse_args()


def main():
    """Main entry point."""
    try:
        args = parse_args()
        
        # Convert string "true"/"false" to boolean for args that come as strings
        for bool_arg in ["include_test", "parallel", "verbose"]:
            if isinstance(getattr(args, bool_arg), str):
                setattr(args, bool_arg, getattr(args, bool_arg).lower() == "true")
        
        config = BuildConfig(
            path=args.path,
            output=args.output,
            include_test=args.include_test,
            exclude=args.exclude,
            parallel=args.parallel,
            verbose=args.verbose,
        )
        
        builder = GraphBuilder(config)
        result = builder.build()
        
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
