# GausVibe

**Structured Graph of Java Code for LLM-Powered Code Understanding**

[![Java 17+](https://img.shields.io/badge/Java-17+-blue.svg)](https://adoptium.net/) [![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/) [![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

---

## 🎯 What is GausVibe?

GausVibe builds **structured, queryable graphs** from Java source code, enabling LLMs to understand codebases **without using expensive shell operations** like `grep`, `find`, or `sed`.

### The Problem

When LLMs explore Java codebases using shell commands:
- `grep -r "methodName"` → Returns entire lines with context (1000+ tokens)
- `find . -name "*.java"` → Traverses entire directory tree repeatedly
- `cat File.java` → Sends entire file content (1000+ tokens)
- **Result**: High token costs, slow performance, missing semantic relationships

### The Solution

GausVibe provides:
- **Parsed AST graphs** with nodes (classes, methods, fields) and edges (calls, inherits, etc.)
- **Indexed queries** returning only relevant information (10-50 tokens)
- **Cached results** for repeated queries (O(1) lookup)
- **Semantic understanding** of relationships (inheritance, polymorphism)

---

## ⚡ Quick Start

### Using the CLI

```bash
# Build graph for current project
java -jar gausvibe.jar build --project .

# Query the graph
java -jar gausvibe.jar query --query "find class com.example.Calculator"

# Interactive mode
java -jar gausvibe.jar interactive
```

### Using as a Library (Coding Harness)

```java
// Build graph programmatically
GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
Graph graph = builder.build();

// Query the graph
JavaGraphQuery queryEngine = new GraphQueryEngine(graph);
List<ClassNode> classes = queryEngine.findClassesByName("Calculator");

// Get callers of a method
List<MethodNode> callers = queryEngine.getCallers(methodNode);
```

---

## 📦 Installation

### Prerequisites

- **Java 17+** (required for JavaParser)
- **Maven 3.9+** (for building)
- **Disk space**: ~1-2GB for large projects

### Build from Source

```bash
# Clone the repository
git clone https://github.com/magnusfind/gausvibe.git
cd gausvibe

# Build the runnable JAR (shaded, all dependencies included)
./scripts/build-artifact.sh

# The build produces:
# - target/gausvibe-<version>.jar (library)
# - target/gausvibe-<version>-all.jar (executable, version = git commit count)
```

### Maven Dependency

```xml
<dependency>
    <groupId>dk.gausdalfind</groupId>
    <artifactId>gausvibe</artifactId>
    <version>YOUR VERSION</version>
</dependency>
```

---

## 🔧 Usage Guide

### For Full Documentation

See **[USAGE.md](USAGE.md)** for comprehensive documentation on:
- [Using as a Library / Coding Harness](USAGE.md#coding-harness-usage)
- [CLI Commands](USAGE.md#cli-commands)
- [Query Language](USAGE.md#query-language)
- [Integration Examples](USAGE.md#integration-examples)
- [Best Practices](USAGE.md#best-practices)

### Common Use Cases

| Use Case | Command | Token Savings |
|----------|---------|---------------|
| Find Java files | `build --project .` then `query "all classes"` | 95% |
| Search for method | `query "find method calculateTotal"` | 95%+ |
| Find callers | `query "who calls add()"` | 90-95% |
| Get class structure | `query "class com.example.Calculator"` | 80-90% |
| Text search | `query "search:text:calculateTotal"` | 95%+ |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         GausVibe                                │
├─────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐     ┌─────────────┐     ┌──────────────┐   │
│  │   Parser    │     │    Graph    │     │    Query     │   │
│  │             │     │             │     │    Engine    │   │
│  │ JavaParser  │────▶│  Nodes/Edges │────▶│  Indexed      │   │
│  │ + Custom    │     │  + Indexes   │     │  Lookups      │   │
│  └─────────────┘     └─────────────┘     └──────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    CLI & API Interface                     ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ││
│  │  │  build   │  │  query   │  │  edit    │  │interactive│  ││
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  ││
│  └─────────────────────────────────────────────────────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🌟 Key Features

### ✅ Implemented

- **File System Caching** - 95% faster repeated file collection
- **Parallel Parsing** - Multi-threaded file processing
- **Call Graph Index** - O(1) call relationship lookups
- **Text Search Index** - grep replacement with semantic awareness
- **Structured Queries** - Compact, token-efficient responses
- **Result Limiting** - Default limit of 10 results (95-99% savings)
- **Paginated Results** - Handle large codebases efficiently

### 📋 Roadmap

- **Incremental Updates** - Only reparse changed files
- **Batch AST Editing** - Multiple edits in single pass
- **Diff Queries** - Structured code change detection
- **Query Result Caching** - Zero tokens for repeated queries

---

## 📊 Performance

### Benchmarks

| Project Size | Parse Time | Memory | Graph Size | Tokens/Query |
|--------------|------------|--------|------------|--------------|
| 10 files | < 1s | ~10MB | ~100KB | 10-20 |
| 100 files | 5-10s | ~50MB | ~1MB | 10-50 |
| 1000 files | 60-120s | ~300MB | ~10MB | 20-100 |
| 10000 files | 10-20min | ~2GB | ~100MB | 50-200 |

### Token Savings vs Shell Commands

| Operation | Shell Tokens | GausVibe Tokens | Savings |
|-----------|--------------|-----------------|---------|
| Find Java files | 500-2000 | 10-50 | 90-99% |
| Text search | 1000-5000 | 10-50 | 95-99% |
| Method lookup | 200-500 | 10-20 | 90-95% |
| Callers query | 500-2000 | 10-50 | 90-99% |
| File content | 1000-10000 | 50-200 | 80-95% |

---

## 📚 Documentation

- **[USAGE.md](USAGE.md)** - Complete usage guide for coding harness integration
- **[SKILL.md](skills/gausvibe/SKILL.md)** - Vibe skill integration documentation
- **[PERFORMANCE_OPTIMIZATION_PLAN.md](docs/history/PERFORMANCE_OPTIMIZATION_PLAN.md)** - Technical optimization details (archived)
- **[CHANGES.md](docs/history/CHANGES.md)** - Changelog and release notes (archived)

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a PR

### Development Setup

```bash
# Build and test
git clone https://github.com/magnusfind/gausvibe.git
cd gausvibe
mvn clean package
mvn test
```

---

## 📜 License

Proprietary - All Rights Reserved. This software is the confidential and proprietary information of GausVibe. Use, reproduction, or distribution is permitted only with express written authorization.

---

## 🏷️ Tags

`java` `code-analysis` `ast` `graph` `llm` `code-understanding` `static-analysis` `developer-tools`
