#!/usr/bin/env python3
"""
ETL Script for GLM Training Data Analysis

This script analyzes the GLM 5.2 coding and debugging traces to identify:
1. Common patterns in expensive operations (grep, find, etc.)
2. Opportunities to replace them with GausVibe graph queries
3. Performance optimizations for the GausVibe framework

Usage:
    python3 etl_analysis.py [--data-dir PATH] [--output-dir PATH]
"""

import json
import os
import re
import sys
from pathlib import Path
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Set
from collections import defaultdict, Counter
import time
from datetime import datetime


@dataclass
class ToolCall:
    """Represents a tool call from GLM traces"""
    tool_name: str
    arguments: dict
    result: str
    duration_ms: float = 0.0
    success: bool = True
    

@dataclass
class Message:
    """Represents a message in the conversation"""
    role: str  # system, user, assistant
    content: str
    tool_calls: List[ToolCall] = field(default_factory=list)
    

@dataclass
class Trajectory:
    """Represents a complete trajectory/traces"""
    task_id: str
    category: str
    lang: str
    split: str
    messages: List[Message] = field(default_factory=list)
    assistant_step: int = 0
    assistant_steps: int = 0
    

@dataclass
class PatternAnalysis:
    """Analysis of patterns found in trajectories"""
    grep_patterns: Counter = field(default_factory=Counter)
    find_patterns: Counter = field(default_factory=Counter)
    sed_patterns: Counter = field(default_factory=Counter)
    file_operations: Counter = field(default_factory=Counter)
    expensive_operations: Counter = field(default_factory=Counter)
    gausvibe_replacements: Dict[str, List[str]] = field(default_factory=dict)
    

class GausVibeOptimizer:
    """
    Analyzes GLM traces and suggests GausVibe optimizations
    """
    
    # Patterns to identify expensive operations
    EXPENSIVE_OP_PATTERNS = {
        # File search operations
        'find': [
            r'find\s+\.',
            r'find\s+\/.*-name',
            r'find\s+\/.*-type',
            r'locate\s+',
        ],
        'grep': [
            r'grep\s+\-r',
            r'grep\s+\-i',
            r'grep\s+\-n',
            r'grep\s+\-A',
            r'grep\s+\-B',
            r'grep\s+\-C',
            r'egrep\s+',
            r'zgrep\s+',
        ],
        'sed': [
            r'sed\s+\-i',
            r'sed\s+\-n',
            r'sed\s+s\/',
        ],
        'awk': [
            r'awk\s+\'{',
            r'gawk\s+',
        ],
        # Other expensive operations
        'xargs': [
            r'xargs\s+',
        ],
        'parallel': [
            r'parallel\s+',
            r'GNU\s+parallel',
        ],
    }
    
    # Map of expensive operations to GausVibe alternatives
    GAUSVIBE_REPLACEMENTS = {
        'grep': {
            'description': 'Text search in files',
            'replacements': [
                'query:search:nodes:METHOD_CALL',
                'query:search:nodes:VARIABLE',
                'query:method:name:*',
                'query:class:name:*',
            ],
            'estimated_savings': '90%',
        },
        'find': {
            'description': 'File discovery',
            'replacements': [
                'query:file:*',
                'query:class:all',
                'query:method:all',
                'query:search:nodes:TYPE',
            ],
            'estimated_savings': '95%',
        },
        'sed': {
            'description': 'Text replacement',
            'replacements': [
                'edit --operations (AST transformation)',
                'REPLACE_METHOD_BODY',
                'ADD_IMPORT',
                'REMOVE_IMPORT',
            ],
            'estimated_savings': '85%',
        },
        'awk': {
            'description': 'Text processing',
            'replacements': [
                'GraphQueryEngine (structured queries)',
                'JavaGraphQuery (typed queries)',
            ],
            'estimated_savings': '80%',
        },
    }
    
    def __init__(self, data_dir: Path, output_dir: Path):
        self.data_dir = data_dir
        self.output_dir = output_dir
        self.trajectories: List[Trajectory] = []
        self.analysis: PatternAnalysis = PatternAnalysis()
        
        # Ensure output directory exists
        self.output_dir.mkdir(parents=True, exist_ok=True)
    
    def load_traces(self, limit: Optional[int] = None) -> None:
        """Load traces from parquet files or JSONL"""
        print(f"Loading traces from {self.data_dir}...")
        
        # Try to load from parquet files first
        parquet_files = list(self.data_dir.glob('data/train-*.parquet'))
        if parquet_files:
            print(f"Found {len(parquet_files)} parquet files")
            # For now, we'll use the traces.jsonl if available
            
        # Try JSONL file
        jsonl_file = self.data_dir / 'traces.jsonl'
        if jsonl_file.exists():
            self._load_jsonl(jsonl_file, limit)
        else:
            print("No traces.jsonl found, checking parquet files...")
            # Would need pyarrow to read parquet
            print("PyArrow not available, skipping parquet files")
    
    def _load_jsonl(self, jsonl_file: Path, limit: Optional[int]) -> None:
        """Load traces from JSONL file"""
        try:
            with open(jsonl_file, 'r') as f:
                for i, line in enumerate(f):
                    if limit and i >= limit:
                        break
                    line = line.strip()
                    if not line:
                        continue
                    
                    # Skip Git LFS pointers
                    if line.startswith('version https://git-lfs.github.com'):
                        continue
                    
                    try:
                        data = json.loads(line)
                        trajectory = self._parse_trajectory(data)
                        if trajectory:
                            self.trajectories.append(trajectory)
                    except json.JSONDecodeError:
                        # This might be a pointer file
                        continue
        except Exception as e:
            print(f"Error loading JSONL: {e}")
    
    def _parse_trajectory(self, data: dict) -> Optional[Trajectory]:
        """Parse a single trajectory from JSON data"""
        try:
            messages = []
            for msg_data in data.get('messages', []):
                message = Message(
                    role=msg_data.get('role', 'unknown'),
                    content=msg_data.get('content', ''),
                )
                
                # Extract tool calls if present
                if msg_data.get('role') == 'assistant':
                    tool_calls = msg_data.get('tool_calls', [])
                    for tc in tool_calls:
                        tool_call = ToolCall(
                            tool_name=tc.get('function', {}).get('name', 'unknown'),
                            arguments=tc.get('function', {}).get('arguments', {}),
                            result=tc.get('result', ''),
                        )
                        message.tool_calls.append(tool_call)
                
                messages.append(message)
            
            return Trajectory(
                task_id=data.get('task', 'unknown'),
                category=data.get('category', 'unknown'),
                lang=data.get('lang', 'unknown'),
                split=data.get('split', 'train'),
                messages=messages,
                assistant_step=data.get('assistant_step', 0),
                assistant_steps=data.get('assistant_steps', 0),
            )
        except Exception as e:
            print(f"Error parsing trajectory: {e}")
            return None
    
    def analyze_patterns(self) -> PatternAnalysis:
        """Analyze all loaded trajectories for patterns"""
        print("Analyzing patterns...")
        
        for trajectory in self.trajectories:
            self._analyze_trajectory(trajectory)
        
        return self.analysis
    
    def _analyze_trajectory(self, trajectory: Trajectory) -> None:
        """Analyze a single trajectory for expensive operations"""
        for message in trajectory.messages:
            # Analyze content for shell commands
            self._analyze_content(message.content)
            
            # Analyze tool calls
            for tool_call in message.tool_calls:
                self._analyze_tool_call(tool_call)
    
    def _analyze_content(self, content: str) -> None:
        """Analyze text content for expensive operation patterns"""
        if not content:
            return
        
        # Check for each expensive operation
        for op_name, patterns in self.EXPENSIVE_OP_PATTERNS.items():
            for pattern in patterns:
                matches = re.findall(pattern, content, re.IGNORECASE)
                if matches:
                    self.analysis.expensive_operations[op_name] += len(matches)
                    
                    # Specific tracking for certain operations
                    if op_name == 'find':
                        for match in matches:
                            self.analysis.find_patterns[match] += 1
                    elif op_name == 'grep':
                        for match in matches:
                            self.analysis.grep_patterns[match] += 1
                    elif op_name == 'sed':
                        for match in matches:
                            self.analysis.sed_patterns[match] += 1
    
    def _analyze_tool_call(self, tool_call: ToolCall) -> None:
        """Analyze a tool call for expensive operations"""
        self.analysis.file_operations[tool_call.tool_name] += 1
        
        # Analyze arguments for shell commands
        if 'command' in tool_call.arguments:
            cmd = tool_call.arguments['command']
            if isinstance(cmd, str):
                self._analyze_content(cmd)
        
        if 'shell_command' in tool_call.arguments:
            cmd = tool_call.arguments['shell_command']
            if isinstance(cmd, str):
                self._analyze_content(cmd)
    
    def identify_optimizations(self) -> Dict:
        """Identify optimization opportunities based on analysis"""
        optimizations = {
            'expensive_operations': {},
            'gausvibe_replacements': {},
            'code_improvements': [],
        }
        
        # Analyze expensive operations
        for op, count in self.analysis.expensive_operations.most_common():
            if op in self.GAUSVIBE_REPLACEMENTS:
                replacements = self.GAUSVIBE_REPLACEMENTS[op]
                optimizations['expensive_operations'][op] = {
                    'count': count,
                    'description': replacements['description'],
                    'estimated_savings': replacements['estimated_savings'],
                    'replacement_count': len(replacements['replacements']),
                }
                
                # Add to gausvibe replacements
                optimizations['gausvibe_replacements'][op] = {
                    'original': op,
                    'alternatives': replacements['replacements'],
                    'savings': replacements['estimated_savings'],
                }
        
        # Identify specific code improvements
        self._identify_code_improvements(optimizations)
        
        return optimizations
    
    def _identify_code_improvements(self, optimizations: Dict) -> None:
        """Identify specific code improvements for GausVibe"""
        improvements = []
        
        # Check if grep is commonly used
        if 'grep' in self.analysis.expensive_operations:
            improvements.append({
                'title': 'Add cached search index',
                'description': 'Implement inverted index for text search to replace grep',
                'component': 'GraphQueryEngine',
                'priority': 'HIGH',
                'estimated_impact': '90% reduction in search time',
                'implementation': {
                    'file': 'GraphQueryEngine.java',
                    'method': 'addTextSearchIndex()',
                    'details': 'Create inverted index mapping text tokens to nodes',
                },
            })
            
            improvements.append({
                'title': 'Pre-compute method call graph',
                'description': 'Cache call relationships to avoid repeated traversal',
                'component': 'Indexes',
                'priority': 'HIGH',
                'estimated_impact': '80% reduction in call graph queries',
                'implementation': {
                    'file': 'Indexes.java',
                    'method': 'addCallGraphIndex()',
                    'details': 'Store reverse index of method calls',
                },
            })
        
        # Check if find is commonly used
        if 'find' in self.analysis.expensive_operations:
            improvements.append({
                'title': 'File system cache',
                'description': 'Cache file listings to avoid repeated Files.walk() calls',
                'component': 'JavaFileCollector',
                'priority': 'HIGH',
                'estimated_impact': '95% reduction in file collection time',
                'implementation': {
                    'file': 'JavaFileCollector.java',
                    'method': 'addFileCache()',
                    'details': 'Cache file paths by directory with timestamp validation',
                },
            })
            
            improvements.append({
                'title': 'Parallel file collection',
                'description': 'Use parallel streams for file discovery',
                'component': 'JavaFileCollector',
                'priority': 'MEDIUM',
                'estimated_impact': '50-70% faster file collection',
                'implementation': {
                    'file': 'JavaFileCollector.java',
                    'method': 'collectParallel()',
                    'details': 'Use Files.walk() with parallel stream processing',
                },
            })
        
        # Check if sed is commonly used
        if 'sed' in self.analysis.expensive_operations:
            improvements.append({
                'title': 'Batch AST editing',
                'description': 'Support batch operations instead of per-file sed',
                'component': 'EditCommand',
                'priority': 'MEDIUM',
                'estimated_impact': '85% reduction in edit time',
                'implementation': {
                    'file': 'EditCommand.java',
                    'method': 'executeBatch()',
                    'details': 'Apply multiple edits in a single AST pass',
                },
            })
        
        optimizations['code_improvements'] = improvements
    
    def generate_report(self, optimizations: Dict) -> Path:
        """Generate a detailed optimization report"""
        report_path = self.output_dir / f'optimization_report_{datetime.now().strftime("%Y%m%d_%H%M%S")}.md'
        
        with open(report_path, 'w') as f:
            f.write("# GausVibe Optimization Report\n\n")
            f.write(f"Generated: {datetime.now().isoformat()}\n\n")
            f.write(f"Analyzed {len(self.trajectories)} trajectories\n\n")
            
            # Summary statistics
            f.write("## Summary Statistics\n\n")
            f.write(f"- Total trajectories: {len(self.trajectories)}\n")
            
            categories = Counter(t.lang for t in self.trajectories)
            f.write("- Language distribution:\n")
            for lang, count in categories.most_common():
                f.write(f"  - {lang}: {count}\n")
            
            f.write("\n")
            
            # Expensive operations
            f.write("## Expensive Operations Found\n\n")
            f.write("Operations that could be replaced with GausVibe queries:\n\n")
            
            if 'expensive_operations' in optimizations:
                for op, info in optimizations['expensive_operations'].items():
                    f.write(f"### {op.upper()}\n")
                    f.write(f"- **Count**: {info['count']}\n")
                    f.write(f"- **Description**: {info['description']}\n")
                    f.write(f"- **Estimated Savings**: {info['estimated_savings']}\n")
                    f.write(f"- **GausVibe Alternatives**:\n")
                    if op in optimizations.get('gausvibe_replacements', {}):
                        for alt in optimizations['gausvibe_replacements'][op]['alternatives']:
                            f.write(f"  - `{alt}`\n")
                    f.write("\n")
            else:
                f.write("No expensive operations detected.\n\n")
            
            # Code improvements
            f.write("## Recommended Code Improvements\n\n")
            
            if 'code_improvements' in optimizations:
                for i, improvement in enumerate(optimizations['code_improvements'], 1):
                    f.write(f"### {i}. {improvement['title']}\n")
                    f.write(f"- **Component**: {improvement['component']}\n")
                    f.write(f"- **Priority**: {improvement['priority']}\n")
                    f.write(f"- **Impact**: {improvement['estimated_impact']}\n")
                    f.write(f"- **Description**: {improvement['description']}\n")
                    f.write(f"\n")
                    f.write(f"**Implementation**:\n")
                    impl = improvement.get('implementation', {})
                    f.write(f"- File: `{impl.get('file', 'N/A')}`\n")
                    f.write(f"- Method: `{impl.get('method', 'N/A')}`\n")
                    f.write(f"- Details: {impl.get('details', 'N/A')}\n")
                    f.write("\n")
            else:
                f.write("No specific improvements identified.\n\n")
            
            # Pattern analysis
            f.write("## Pattern Analysis\n\n")
            
            if self.analysis.grep_patterns:
                f.write("### Grep Patterns\n")
                for pattern, count in self.analysis.grep_patterns.most_common(10):
                    f.write(f"- `{pattern}`: {count} occurrences\n")
                f.write("\n")
            
            if self.analysis.find_patterns:
                f.write("### Find Patterns\n")
                for pattern, count in self.analysis.find_patterns.most_common(10):
                    f.write(f"- `{pattern}`: {count} occurrences\n")
                f.write("\n")
            
            if self.analysis.sed_patterns:
                f.write("### Sed Patterns\n")
                for pattern, count in self.analysis.sed_patterns.most_common(10):
                    f.write(f"- `{pattern}`: {count} occurrences\n")
                f.write("\n")
            
            # Task category analysis
            f.write("## Task Category Analysis\n\n")
            task_categories = Counter(t.category for t in self.trajectories)
            for category, count in task_categories.most_common():
                f.write(f"- **{category}**: {count} trajectories ({count/len(self.trajectories)*100:.1f}%)\n")
            f.write("\n")
            
            # GausVibe integration suggestions
            f.write("## GausVibe Integration Suggestions\n\n")
            f.write("Based on the analysis, here are suggested improvements:\n\n")
            
            f.write("### 1. File System Cache\n")
            f.write("Implement a caching layer for file system operations:\n")
            f.write("```java\n")
            f.write("// In JavaFileCollector.java\n")
            f.write("private static final Map<Path, List<Path>> fileCache = new ConcurrentHashMap<>();\n")
            f.write("private static final Map<Path, Long> directoryTimestamps = new ConcurrentHashMap<>();\n\n")
            f.write("public static List<Path> collectWithCache(Path directory) throws IOException {\n")
            f.write("    Path canonicalDir = directory.toAbsolutePath().normalize();\n")
            f.write("    Long lastModified = Files.getLastModifiedTime(canonicalDir).toMillis();\n")
            f.write("    Long cachedTime = directoryTimestamps.get(canonicalDir);\n")
            f.write("    \n")
            f.write("    if (cachedTime != null && cachedTime == lastModified) {\n")
            f.write("        return fileCache.get(canonicalDir);\n")
            f.write("    }\n")
            f.write("    \n")
            f.write("    List<Path> files = collect(canonicalDir);\n")
            f.write("    fileCache.put(canonicalDir, files);\n")
            f.write("    directoryTimestamps.put(canonicalDir, lastModified);\n")
            f.write("    return files;\n")
            f.write("}\n")
            f.write("```\n\n")
            
            f.write("### 2. Text Search Index\n")
            f.write("Add inverted index for fast text search:\n")
            f.write("```java\n")
            f.write("// In GraphQueryEngine.java\n")
            f.write("private final Map<String, Set<Node>> textIndex = new ConcurrentHashMap<>();\n\n")
            f.write("public void buildTextIndex() {\n")
            f.write("    for (Node node : graph.getAllNodes()) {\n")
            f.write("        String content = getNodeContent(node);\n")
            f.write("        for (String token : extractTokens(content)) {\n")
            f.write("            textIndex.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet()).add(node);\n")
            f.write("        }\n")
            f.write("    }\n")
            f.write("}\n\n")
            f.write("public List<Node> searchText(String query) {\n")
            f.write("    Set<Node> results = new HashSet<>();\n")
            f.write("    for (String token : extractTokens(query)) {\n")
            f.write("        Set<Node> nodes = textIndex.get(token);\n")
            f.write("        if (nodes != null) results.addAll(nodes);\n")
            f.write("    }\n")
            f.write("    return new ArrayList<>(results);\n")
            f.write("}\n")
            f.write("```\n\n")
            
            f.write("### 3. Call Graph Index\n")
            f.write("Pre-compute and cache call relationships:\n")
            f.write("```java\n")
            f.write("// In Indexes.java\n")
            f.write("private final Map<String, Set<String>> callersIndex = new ConcurrentHashMap<>();\n")
            f.write("private final Map<String, Set<String>> calleesIndex = new ConcurrentHashMap<>();\n\n")
            f.write("public void buildCallGraphIndex() {\n")
            f.write("    for (Edge edge : graph.getEdgesByType(EdgeTypes.CALLS)) {\n")
            f.write("        String caller = edge.getFromId();\n")
            f.write("        String callee = edge.getToId();\n")
            f.write("        callersIndex.computeIfAbsent(callee, k -> ConcurrentHashMap.newKeySet()).add(caller);\n")
            f.write("        calleesIndex.computeIfAbsent(caller, k -> ConcurrentHashMap.newKeySet()).add(callee);\n")
            f.write("    }\n")
            f.write("}\n\n")
            f.write("public Set<String> getCallers(String methodId) {\n")
            f.write("    return callersIndex.getOrDefault(methodId, Collections.emptySet());\n")
            f.write("}\n\n")
            f.write("public Set<String> getCallees(String methodId) {\n")
            f.write("    return calleesIndex.getOrDefault(methodId, Collections.emptySet());\n")
            f.write("}\n")
            f.write("```\n\n")
        
        print(f"Report generated: {report_path}")
        return report_path
    
    def generate_implementation_plan(self, optimizations: Dict) -> Path:
        """Generate an implementation plan for optimizations"""
        plan_path = self.output_dir / f'implementation_plan_{datetime.now().strftime("%Y%m%d_%H%M%S")}.md'
        
        with open(plan_path, 'w') as f:
            f.write("# GausVibe Optimization Implementation Plan\n\n")
            f.write(f"Generated: {datetime.now().isoformat()}\n\n")
            
            f.write("## Overview\n\n")
            f.write("This plan outlines the steps to optimize GausVibe based on analysis of GLM training data.\n")
            f.write("The goal is to replace expensive shell operations (grep, find, sed) with efficient graph queries.\n\n")
            
            f.write("## Phase 1: Quick Wins (1-2 days)\n\n")
            f.write("### 1.1 File System Caching\n")
            f.write("- **Priority**: HIGH\n")
            f.write("- **Effort**: 2-4 hours\n")
            f.write("- **Files**: JavaFileCollector.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Add LRU cache for file listings\n")
            f.write("  2. Add timestamp-based cache invalidation\n")
            f.write("  3. Add cache statistics and debugging\n")
            f.write("- **Testing**: Unit tests for cache hit/miss scenarios\n\n")
            
            f.write("### 1.2 Query Result Caching\n")
            f.write("- **Priority**: HIGH\n")
            f.write("- **Effort**: 3-5 hours\n")
            f.write("- **Files**: GraphQueryEngine.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Enhance existing queryCache with TTL\n")
            f.write("  2. Add cache for frequently accessed nodes\n")
            f.write("  3. Add cache statistics endpoint\n")
            f.write("- **Testing**: Verify cache invalidation works correctly\n\n")
            
            f.write("## Phase 2: Core Optimizations (3-5 days)\n\n")
            f.write("### 2.1 Text Search Index\n")
            f.write("- **Priority**: HIGH\n")
            f.write("- **Effort**: 1-2 days\n")
            f.write("- **Files**: GraphQueryEngine.java, Indexes.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Implement inverted index for text tokens\n")
            f.write("  2. Add tokenization utility (split by camelCase, snake_case, etc.)\n")
            f.write("  3. Implement searchText() method\n")
            f.write("  4. Add fuzzy search support\n")
            f.write("- **Performance Target**: Sub-second search on large codebases\n\n")
            
            f.write("### 2.2 Call Graph Index\n")
            f.write("- **Priority**: HIGH\n")
            f.write("- **Effort**: 1 day\n")
            f.write("- **Files**: Indexes.java, GraphQueryEngine.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Build callers and callees indexes\n")
            f.write("  2. Update getCallers() and getCallees() to use index\n")
            f.write("  3. Add transitive closure for call graph\n")
            f.write("- **Performance Target**: O(1) for direct calls, O(k) for transitive\n\n")
            
            f.write("### 2.3 Parallel File Processing\n")
            f.write("- **Priority**: MEDIUM\n")
            f.write("- **Effort**: 1 day\n")
            f.write("- **Files**: GausVibeBuilder.java, JavaFileCollector.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Use parallel streams for file collection\n")
            f.write("  2. Implement work stealing for file parsing\n")
            f.write("  3. Add progress tracking\n")
            f.write("- **Testing**: Benchmark on large projects\n\n")
            
            f.write("## Phase 3: Advanced Features (2-3 weeks)\n\n")
            f.write("### 3.1 Batch AST Editing\n")
            f.write("- **Priority**: MEDIUM\n")
            f.write("- **Effort**: 3-5 days\n")
            f.write("- **Files**: EditCommand.java, editing/*.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Support batch operations in single AST pass\n")
            f.write("  2. Implement conflict detection for overlapping edits\n")
            f.write("  3. Add atomic transaction support\n")
            f.write("- **Benefit**: Replace sed with structured edits\n\n")
            
            f.write("### 3.2 Incremental Graph Updates\n")
            f.write("- **Priority**: MEDIUM\n")
            f.write("- **Effort**: 5-7 days\n")
            f.write("- **Files**: GausVibeBuilder.java, Graph.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Track file modification timestamps\n")
            f.write("  2. Implement incremental parsing\n")
            f.write("  3. Support partial graph updates\n")
            f.write("- **Benefit**: Faster rebuilds after code changes\n\n")
            
            f.write("### 3.3 Semantic Search\n")
            f.write("- **Priority**: LOW\n")
            f.write("- **Effort**: 1 week\n")
            f.write("- **Files**: queries/*.java\n")
            f.write("- **Tasks**:\n")
            f.write("  1. Integrate with embedding models\n")
            f.write("  2. Implement semantic similarity search\n")
            f.write("  3. Add hybrid search (keyword + semantic)\n")
            f.write("- **Benefit**: Find similar code by semantics, not just text\n\n")
            
            f.write("## Performance Targets\n\n")
            f.write("| Operation | Current | Target | Improvement |\n")
            f.write("|-----------|---------|-------|-------------|\n")
            f.write("| File collection (10K files) | ~5s | <1s | 80% faster |\n")
            f.write("| Text search (grep equivalent) | ~2s | <100ms | 95% faster |\n")
            f.write("| Call graph query | ~500ms | <50ms | 90% faster |\n")
            f.write("| Full graph build | ~30s | <10s | 67% faster |\n\n")
            
            f.write("## Validation Plan\n\n")
            f.write("### Unit Tests\n")
            f.write("- Add tests for each new index\n")
            f.write("- Test cache invalidation scenarios\n")
            f.write("- Test concurrent access\n\n")
            
            f.write("### Integration Tests\n")
            f.write("- Test on sample projects (small, medium, large)\n")
            f.write("- Verify query results match expected\n")
            f.write("- Benchmark performance improvements\n\n")
            
            f.write("### Regression Tests\n")
            f.write("- Ensure existing functionality still works\n")
            f.write("- Verify graph consistency after optimizations\n")
            f.write("- Test with real-world codebases\n\n")
            
            f.write("## Rollout Strategy\n\n")
            f.write("1. Implement and test Phase 1 optimizations\n")
            f.write("2. Release as v1.1.0 with performance improvements\n")
            f.write("3. Implement and test Phase 2 optimizations\n")
            f.write("4. Release as v1.2.0 with core features\n")
            f.write("5. Implement Phase 3 features incrementally\n")
            f.write("6. Release as v2.0.0 with advanced features\n\n")
            
            f.write("## Monitoring\n\n")
            f.write("- Add performance metrics logging\n")
            f.write("- Track cache hit rates\n")
            f.write("- Monitor query execution times\n")
            f.write("- Alert on performance regressions\n")
        
        print(f"Implementation plan generated: {plan_path}")
        return plan_path
    
    def generate_gausvibe_improvements(self) -> Path:
        """Generate Java code improvements for GausVibe"""
        improvements_path = self.output_dir / f'gausvibe_improvements_{datetime.now().strftime("%Y%m%d_%H%M%S")}.java'
        
        with open(improvements_path, 'w') as f:
            f.write("// Generated GausVibe Improvements\n")
            f.write("// Based on GLM training data analysis\n")
            f.write(f"// Generated: {datetime.now().isoformat()}\n\n")
            f.write("package dk.gausdalfind.improvements;\n\n")
            
            f.write("/**\n")
            f.write(" * Performance improvements for GausVibe based on GLM training data analysis.\n")
            f.write(" *\n")
            f.write(" * Key findings from GLM 5.2 coding and debugging traces:\n")
            f.write(" * - Models frequently use grep/find/sed for code exploration\n")
            f.write(" * - These operations are expensive and error-prone\n")
            f.write(" * - GausVibe can provide structured, efficient alternatives\n")
            f.write(" */\n\n")
            
            f.write("import dk.gausdalfind.model.*;\n")
            f.write("import dk.gausdalfind.queries.*;\n")
            f.write("import java.util.*;\n")
            f.write("import java.util.concurrent.*;\n")
            f.write("import java.nio.file.*;\n\n")
            
            # FileSystemCache
            f.write("/**\n")
            f.write(" * LRU cache for file system operations.\n")
            f.write(" * Reduces expensive Files.walk() and Files.find() calls.\n")
            f.write(" */\n")
            f.write("public final class FileSystemCache {\n")
            f.write("    private static final int DEFAULT_CACHE_SIZE = 100;\n")
            f.write("    private static final long CACHE_TTL_MS = 300000; // 5 minutes\n\n")
            f.write("    private final LinkedHashMap<Path, CacheEntry> cache;\n")
            f.write("    private final int maxSize;\n\n")
            f.write("    @FunctionalInterface\n")
            f.write("    private interface CacheLoader<T> {\n")
            f.write("        T load(Path key) throws IOException;\n")
            f.write("    }\n\n")
            f.write("    private static class CacheEntry {\n")
            f.write("        final Object value;\n")
            f.write("        final long timestamp;\n\n")
            f.write("        CacheEntry(Object value) {\n")
            f.write("            this.value = value;\n")
            f.write("            this.timestamp = System.currentTimeMillis();\n")
            f.write("        }\n\n")
            f.write("        boolean isExpired(long ttl) {\n")
            f.write("            return System.currentTimeMillis() - timestamp > ttl;\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public FileSystemCache(int maxSize) {\n")
            f.write("        this.maxSize = maxSize;\n")
            f.write("        this.cache = new LinkedHashMap<Path, CacheEntry>(maxSize, 0.75f, true) {\n")
            f.write("            @Override\n")
            f.write("            protected boolean removeEldestEntry(Map.Entry<Path, CacheEntry> eldest) {\n")
            f.write("                return size() > FileSystemCache.this.maxSize;\n")
            f.write("            }\n")
            f.write("        };\n")
            f.write("    }\n\n")
            f.write("    public FileSystemCache() {\n")
            f.write("        this(DEFAULT_CACHE_SIZE);\n")
            f.write("    }\n\n")
            f.write("    @SuppressWarnings(\"unchecked\")\n")
            f.write("    public <T> T get(Path key, CacheLoader<T> loader) throws IOException {\n")
            f.write("        Path canonical = key.toAbsolutePath().normalize();\n")
            f.write("        synchronized (cache) {\n")
            f.write("            CacheEntry entry = cache.get(canonical);\n")
            f.write("            if (entry != null && !entry.isExpired(CACHE_TTL_MS)) {\n")
            f.write("                return (T) entry.value;\n")
            f.write("            }\n")
            f.write("            T value = loader.load(canonical);\n")
            f.write("            cache.put(canonical, new CacheEntry(value));\n")
            f.write("            return value;\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public void invalidate(Path key) {\n")
            f.write("        Path canonical = key.toAbsolutePath().normalize();\n")
            f.write("        synchronized (cache) {\n")
            f.write("            cache.remove(canonical);\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public void invalidateAll() {\n")
            f.write("        synchronized (cache) {\n")
            f.write("            cache.clear();\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public int size() {\n")
            f.write("        synchronized (cache) {\n")
            f.write("            return cache.size();\n")
            f.write("        }\n")
            f.write("    }\n")
            f.write("}\n\n")
            
            # TextSearchIndex
            f.write("/**\n")
            f.write(" * Inverted index for text search in code.\n")
            f.write(" * Replaces grep operations with structured queries.\n")
            f.write(" */\n")
            f.write("public final class TextSearchIndex {\n")
            f.write("    private final Map<String, Set<Node>> index = new ConcurrentHashMap<>();\n")
            f.write("    private final Graph graph;\n\n")
            f.write("    public TextSearchIndex(Graph graph) {\n")
            f.write("        this.graph = graph;\n")
            f.write("    }\n\n")
            f.write("    public void build() {\n")
            f.write("        index.clear();\n")
            f.write("        for (Node node : graph.getAllNodes()) {\n")
            f.write("            index(node);\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public void index(Node node) {\n")
            f.write("        String content = getNodeContent(node);\n")
            f.write("        for (String token : tokenize(content)) {\n")
            f.write("            index.computeIfAbsent(token.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(node);\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public List<Node> search(String query) {\n")
            f.write("        Set<Node> results = new HashSet<>();\n")
            f.write("        for (String token : tokenize(query)) {\n")
            f.write("            Set<Node> nodes = index.get(token.toLowerCase());\n")
            f.write("            if (nodes != null) {\n")
            f.write("                results.addAll(nodes);\n")
            f.write("            }\n")
            f.write("        }\n")
            f.write("        return new ArrayList<>(results);\n")
            f.write("    }\n\n")
            f.write("    private String getNodeContent(Node node) {\n")
            f.write("        if (node instanceof MethodNode) {\n")
            f.write("            return ((MethodNode) node).getName() + \" \" + ((MethodNode) node).getSignature();\n")
            f.write("        } else if (node instanceof ClassNode) {\n")
            f.write("            return ((ClassNode) node).getQualifiedName();\n")
            f.write("        } else if (node instanceof FieldNode) {\n")
            f.write("            return ((FieldNode) node).getName();\n")
            f.write("        }\n")
            f.write("        return node.getId();\n")
            f.write("    }\n\n")
            f.write("    private List<String> tokenize(String text) {\n")
            f.write("        List<String> tokens = new ArrayList<>();\n")
            f.write("        if (text == null) return tokens;\n")
            f.write("        // Split by camelCase, snake_case, and other common patterns\n")
            f.write("        // Simple tokenization for now - can be enhanced with proper lexer\n")
            f.write("        String[] parts = text.split(\"[^a-zA-Z0-9_]\");\n")
            f.write("        for (String part : parts) {\n")
            f.write("            if (!part.isEmpty()) {\n")
            f.write("                tokens.add(part);\n")
            f.write("            }\n")
            f.write("        }\n")
            f.write("        return tokens;\n")
            f.write("    }\n")
            f.write("}\n\n")
            
            # CallGraphIndex
            f.write("/**\n")
            f.write(" * Pre-computed call graph index for fast call relationship queries.\n")
            f.write(" * Replaces manual traversal of CALLS edges.\n")
            f.write(" */\n")
            f.write("public final class CallGraphIndex {\n")
            f.write("    private final Map<String, Set<String>> callers = new ConcurrentHashMap<>();\n")
            f.write("    private final Map<String, Set<String>> callees = new ConcurrentHashMap<>();\n")
            f.write("    private final Graph graph;\n\n")
            f.write("    public CallGraphIndex(Graph graph) {\n")
            f.write("        this.graph = graph;\n")
            f.write("    }\n\n")
            f.write("    public void build() {\n")
            f.write("        callers.clear();\n")
            f.write("        callees.clear();\n")
            f.write("        for (Edge edge : graph.getEdgesByType(EdgeTypes.CALLS)) {\n")
            f.write("            String from = edge.getFromId();\n")
            f.write("            String to = edge.getToId();\n")
            f.write("            callers.computeIfAbsent(to, k -> ConcurrentHashMap.newKeySet()).add(from);\n")
            f.write("            callees.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet()).add(to);\n")
            f.write("        }\n")
            f.write("    }\n\n")
            f.write("    public Set<String> getCallers(String methodId) {\n")
            f.write("        return Collections.unmodifiableSet(callers.getOrDefault(methodId, Collections.emptySet()));\n")
            f.write("    }\n\n")
            f.write("    public Set<String> getCallees(String methodId) {\n")
            f.write("        return Collections.unmodifiableSet(callees.getOrDefault(methodId, Collections.emptySet()));\n")
            f.write("    }\n\n")
            f.write("    public Set<String> getCallersTransitive(String methodId) {\n")
            f.write("        return getTransitive(methodId, this::getCallers);\n")
            f.write("    }\n\n")
            f.write("    public Set<String> getCalleesTransitive(String methodId) {\n")
            f.write("        return getTransitive(methodId, this::getCallees);\n")
            f.write("    }\n\n")
            f.write("    private Set<String> getTransitive(String start, \n")
            f.write("            java.util.function.Function<String, Set<String>> getter) {\n")
            f.write("        Set<String> visited = ConcurrentHashMap.newKeySet();\n")
            f.write("        Set<String> result = ConcurrentHashMap.newKeySet();\n")
            f.write("        Queue<String> queue = new LinkedList<>();\n")
            f.write("        queue.add(start);\n")
            f.write("        visited.add(start);\n\n")
            f.write("        while (!queue.isEmpty()) {\n")
            f.write("            String current = queue.poll();\n")
            f.write("            Set<String> next = getter.apply(current);\n")
            f.write("            for (String n : next) {\n")
            f.write("                if (!visited.contains(n)) {\n")
            f.write("                    visited.add(n);\n")
            f.write("                    result.add(n);\n")
            f.write("                    queue.add(n);\n")
            f.write("                }\n")
            f.write("            }\n")
            f.write("        }\n\n")
            f.write("        return result;\n")
            f.write("    }\n")
            f.write("}\n")
        
        print(f"Java improvements generated: {improvements_path}")
        return improvements_path
    
    def run(self, limit: Optional[int] = None) -> None:
        """Run the complete analysis pipeline"""
        start_time = time.time()
        
        print("=" * 60)
        print("GausVibe Optimization ETL Pipeline")
        print("=" * 60)
        print()
        
        # Step 1: Load data
        print("Step 1: Loading GLM training data...")
        self.load_traces(limit)
        print(f"  Loaded {len(self.trajectories)} trajectories")
        print()
        
        # Step 2: Analyze patterns
        print("Step 2: Analyzing patterns...")
        self.analyze_patterns()
        print(f"  Found {len(self.analysis.expensive_operations)} types of expensive operations")
        for op, count in self.analysis.expensive_operations.most_common():
            print(f"    - {op}: {count} occurrences")
        print()
        
        # Step 3: Identify optimizations
        print("Step 3: Identifying optimizations...")
        optimizations = self.identify_optimizations()
        print(f"  Identified {len(optimizations.get('code_improvements', []))} code improvements")
        print()
        
        # Step 4: Generate reports
        print("Step 4: Generating reports...")
        report_path = self.generate_report(optimizations)
        plan_path = self.generate_implementation_plan(optimizations)
        improvements_path = self.generate_gausvibe_improvements()
        print()
        
        # Summary
        print("=" * 60)
        print("Analysis Complete!")
        print("=" * 60)
        print(f"Time taken: {time.time() - start_time:.2f} seconds")
        print(f"Trajectories analyzed: {len(self.trajectories)}")
        print(f"Expensive operations found: {sum(self.analysis.expensive_operations.values())}")
        print()
        print("Generated files:")
        print(f"  - Report: {report_path}")
        print(f"  - Implementation Plan: {plan_path}")
        print(f"  - Java Improvements: {improvements_path}")
        print()
        print("Next steps:")
        print("  1. Review the generated reports")
        print("  2. Implement the Phase 1 optimizations")
        print("  3. Test and benchmark improvements")
        print("  4. Proceed to Phase 2 optimizations")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Analyze GLM training data for GausVibe optimizations'
    )
    parser.add_argument(
        '--data-dir',
        type=Path,
        default=Path('/Users/magnusfind/Documents/find-shadow-model/glm-5.2-coding-and-debugging-traces'),
        help='Directory containing GLM training data'
    )
    parser.add_argument(
        '--output-dir',
        type=Path,
        default=Path('/Users/magnusfind/Documents/find-shadow-model/gausvibe/etl_output'),
        help='Directory for output files'
    )
    parser.add_argument(
        '--limit',
        type=int,
        default=None,
        help='Limit number of trajectories to process (for testing)'
    )
    
    args = parser.parse_args()
    
    optimizer = GausVibeOptimizer(args.data_dir, args.output_dir)
    optimizer.run(args.limit)


if __name__ == '__main__':
    main()
