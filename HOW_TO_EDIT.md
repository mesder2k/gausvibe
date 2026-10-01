# How to Edit GausVibe Using GausVibe (Self-Editing Guide)

**Version**: 1.0.0 - Draft  
**Status**: Phase 5 Implementation (Closed-Loop Validation)  
**Last Updated**: 2026-09-20

---

## 🎯 Overview

This guide explains how to modify GausVibe's own source code **using GausVibe itself**, rather than direct file editing. This is the **self-hosting** capability that makes GausVibe a self-improving system.

### Why Self-Editing?

1. **Structured Changes**: All modifications are expressed as structured AST operations, not text edits
2. **Consistency**: Operations are validated before application
3. **Auditability**: Every change is tracked through the ChangeTracker
4. **Reproducibility**: Operations can be replayed or reverted
5. **Safety**: Atomic transactions prevent partial modifications

---

## 📋 Prerequisites

Before you can use GausVibe to edit itself, ensure:

1. **GausVibe compiles successfully** - All compilation errors must be fixed
2. **Maven is installed** - Required for compilation verification (currently a blocker, see docs/history/ISSUES.md)
3. **Java 17+** - GausVibe requires Java 17

### Check Prerequisites

```bash
# Check Java version
java -version

# Check Maven version  
mvn -version

# Build GausVibe
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
mvn clean compile
```

---

## 🚀 Self-Editing Workflow

The self-editing workflow follows this pattern:

```
1. QUERY: Find the code to modify
   ↓
2. PLAN: Generate edit operations (JSON DSL)
   ↓
3. EXECUTE: Apply operations via EditCommand
   ↓
4. SERIALIZE: Write modified AST back to source files
   ↓
5. VERIFY: Recompile and test
```

---

## 🔍 Step 1: Query - Find Code to Modify

Use GausVibe's query interface to locate the code you want to modify.

### Query Commands

```bash
# Start GausVibe
gausvibe> 

# Find a class
gausvibe> query:class:dk.gausdalfind.graph.GausVibeBuilder

# Find a method
gausvibe> query:method:dk.gausdalfind.graph.GausVibeBuilder#build

# Find by text search
gausvibe> query:search:text:parseFile

# List all classes
gausvibe> query:class:*.java
```

### Example: Find the GausVibeBuilder class

```
gausvibe> query:class:dk.gausdalfind.graph.GausVibeBuilder

Classes (1):
  - dk.gausdalfind.graph.GausVibeBuilder
    File: /Users/magnusfind/Documents/find-shadow-model/gausvibe/src/main/java/dk/gausdalfind/graph/GausVibeBuilder.java
    Methods: 25
    Fields: 8
```

---

## ✏️ Step 2: Plan - Generate Edit Operations

Edit operations are specified in JSON format. Each operation defines a specific AST modification.

### Operation Types

| Type | Description | Required Fields |
|------|-------------|----------------|
| ADD_METHOD | Add a new method to a class | target_class, name, return_type, body |
| REMOVE_METHOD | Remove a method from a class | target_class, name |
| ADD_FIELD | Add a new field to a class | target_class, name, data_type |
| REMOVE_FIELD | Remove a field from a class | target_class, name |
| ADD_IMPORT | Add an import statement | target_class, import_statement |
| REMOVE_IMPORT | Remove an import statement | target_class, import_statement |
| REPLACE_METHOD_BODY | Replace method body | target_class, method_name, new_body |

### Operation JSON Schema

```json
{
  "type": "ADD_METHOD",
  "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
  "name": "getBuildSummary",
  "return_type": "String",
  "modifiers": ["public"],
  "parameters": [],
  "body": "return \"GausVibe v1.0.0\";"
}
```

### Example Operations

#### Add a Method

```json
{
  "type": "ADD_METHOD",
  "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
  "name": "getBuildSummary",
  "return_type": "String",
  "modifiers": ["public"],
  "parameters": [],
  "body": "return \"Built at: \" + new java.util.Date();"
}
```

#### Add a Field

```json
{
  "type": "ADD_FIELD",
  "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
  "name": "lastBuildTime",
  "data_type": "java.util.Date",
  "modifiers": ["private"]
}
```

#### Remove a Method

```json
{
  "type": "REMOVE_METHOD",
  "target_class": "dk.gausdalfind.cli.CommandLineInterface",
  "name": "printOldHelp"
}
```

#### Replace Method Body

```json
{
  "type": "REPLACE_METHOD_BODY",
  "target_class": "dk.gausdalfind.cli.CommandLineInterface",
  "method_name": "printVersion",
  "new_body": "System.out.println(\"GausVibe v1.0.0 - Self-Hosting MVP\");"
}
```

---

## 🎯 Step 3: Execute - Apply Operations

Use the `edit` command to apply your operations to the graph.

### Edit Command Syntax

```bash
# Single operation
gausvibe> edit --graph graph.json --operations '[{"type": "ADD_METHOD", ...}]'

# Multiple operations
gausvibe> edit --graph graph.json --operations '[{"type": "ADD_METHOD", ...}, {"type": "ADD_FIELD", ...}]'

# From file
gausvibe> edit --graph graph.json --operations-file edits.json
```

### Options

| Option | Description | Values | Default |
|--------|-------------|--------|---------|
| `--graph` | Path to graph file | file path | Required |
| `--operations` | JSON operations | JSON string | Required |
| `--operations-file` | File with operations | file path | - |
| `--dry-run` | Preview changes without saving | - | false |
| `--output` | Output format | text, json, diff | text |
| `--verbose` | Show detailed output | - | false |

### Example: Add a Method to GausVibeBuilder

```bash
# First, build the graph
gausvibe> build --project /Users/magnusfind/Documents/find-shadow-model/gausvibe --output graph.json

# Then apply the edit
gausvibe> edit --graph graph.json --operations '[{"type": "ADD_METHOD", "target_class": "dk.gausdalfind.graph.GausVibeBuilder", "name": "getBuildSummary", "return_type": "String", "modifiers": ["public"], "parameters": [], "body": "return \"GausVibe v1.0.0\";"}]'

# Output:
Applying 1 operation(s) to graph...
✓ Added method getBuildSummary to dk.gausdalfind.graph.GausVibeBuilder
1 operation(s) applied successfully
```

---

## 💾 Step 4: Serialize - Write Back to Source

After editing the graph, serialize the changes back to Java source files.

### Serialization Command

```bash
# Serialize all modified files
gausvibe> serialize --graph modified-graph.json --output /path/to/output/dir

# Serialize specific files
gausvibe> serialize --graph modified-graph.json --output /path/to/output/dir --files dk.gausdalfind.graph.GausVibeBuilder
```

### Example

```bash
# After editing
gausvibe> serialize --graph modified-graph.json --output /tmp/gausvibe-modified

# Check the modified file
cat /tmp/gausvibe-modified/dk/gausdalfind/graph/GausVibeBuilder.java
```

---

## ✅ Step 5: Verify - Compile and Test

After serialization, verify the changes work correctly.

### Compilation

```bash
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
mvn clean compile
```

### Run Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SelfEditRoundTripTest

# Run specific test method
mvn test -Dtest=SelfEditRoundTripTest#testRoundTrip_ParseQueryEditSerialize
```

### Manual Verification

```bash
# Check the modified file compiles
javac -cp "..." /tmp/gausvibe-modified/dk/gausdalfind/graph/GausVibeBuilder.java

# Or copy back to source and compile
cp /tmp/gausvibe-modified/dk/gausdalfind/graph/GausVibeBuilder.java \
   src/main/java/dk/gausdalfind/graph/GausVibeBuilder.java
mvn compile
```

---

## 🎓 Complete Example: Adding a Method

Let's go through a complete example of adding a method to GausVibe.

### Step 1: Build the Graph

```bash
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
gausvibe> build --project . --output /tmp/gausvibe-graph.json
```

### Step 2: Query to Verify Target

```bash
gausvibe> query:class:dk.gausdalfind.graph.GausVibeBuilder

Classes (1):
  - dk.gausdalfind.graph.GausVibeBuilder
    File: /Users/magnusfind/Documents/find-shadow-model/gausvibe/src/main/java/dk/gausdalfind/graph/GausVibeBuilder.java
    Methods: 25
    Fields: 8
```

### Step 3: Create Edit Operation

Create a JSON file with the operation:

```json
[
  {
    "type": "ADD_METHOD",
    "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
    "name": "getBuildSummary",
    "return_type": "String",
    "modifiers": ["public"],
    "parameters": [],
    "body": "return \"GausVibe Self-Hosting MVP - Built: \" + new java.util.Date();"
  }
]
```

Save as `add-method.json`.

### Step 4: Apply the Edit

```bash
gausvibe> edit --graph /tmp/gausvibe-graph.json --operations-file add-method.json --output /tmp/edits-result.txt
```

### Step 5: Serialize Changes

```bash
gausvibe> serialize --graph /tmp/gausvibe-graph.json --output /tmp/gausvibe-modified
```

### Step 6: Verify

```bash
# Check the modified file
cat /tmp/gausvibe-modified/dk/gausdalfind/graph/GausVibeBuilder.java

# Verify it contains the new method
grep -n "getBuildSummary" /tmp/gausvibe-modified/dk/gausdalfind/graph/GausVibeBuilder.java

# Copy back to source (optional)
cp /tmp/gausvibe-modified/dk/gausdalfind/graph/GausVibeBuilder.java \
   src/main/java/dk/gausdalfind/graph/GausVibeBuilder.java

# Recompile
mvn compile
```

---

## 🛠️ Common Edit Operations

### Add a Logger Field

```json
{
  "type": "ADD_FIELD",
  "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
  "name": "logger",
  "data_type": "org.slf4j.Logger",
  "modifiers": ["private", "static", "final"],
  "initializer": "LoggerFactory.getLogger(GausVibeBuilder.class)"
}
```

### Add an Import

```json
{
  "type": "ADD_IMPORT",
  "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
  "import_statement": "import org.slf4j.LoggerFactory;"
}
```

### Replace Method Body (Add Logging)

```json
{
  "type": "REPLACE_METHOD_BODY",
  "target_class": "dk.gausdalfind.graph.GausVibeBuilder",
  "method_name": "build",
  "new_body": "logger.info(\"Starting graph build...\");\n        // existing code..."
}
```

---

## 📊 Troubleshooting

### Issue: "Class not found"

**Cause**: The class name is incorrect or not in the graph

**Solution**:
```bash
# List all classes
gausvibe> query:class:*.java

# Find the correct qualified name
gausvibe> query:class:name:GausVibeBuilder
```

### Issue: "Method not found"

**Cause**: The method doesn't exist or the class name is wrong

**Solution**:
```bash
# Find the class first
gausvibe> query:class:dk.gausdalfind.graph.GausVibeBuilder

# List methods in the class
gausvibe> query:method:class:dk.gausdalfind.graph.GausVibeBuilder
```

### Issue: Serialization produces invalid Java

**Cause**: AST modification may produce syntax errors

**Solution**:
1. Check the serialized output carefully
2. Verify the operation parameters are correct
3. Try a simpler operation first
4. Report as issue with details

---

## 📚 Best Practices

1. **Start Small**: Test with simple operations first (add a method, add a field)
2. **Verify Each Step**: Check the graph after each operation
3. **Use Dry Run**: Test with `--dry-run` first to preview changes
4. **Backup**: Always keep a backup of your source before editing
5. **Test Compilation**: Always compile after serialization
6. **Single Operation**: Apply one operation at a time for easier debugging
7. **Check Logs**: Enable `--verbose` for detailed output

---

## 🎯 Success Criteria for Phase 5

According to docs/history/SELF_HOSTING_MVP.md, Phase 5 is complete when:

1. ✅ Test edit workflow (Query → Generate op → Apply → Serialize)
2. ⏳ First successful self-edit (Add simple method and recompile)
3. ⏳ Verify compilation of modified GausVibe
4. ⏳ Test removal of a method
5. ⏳ Test modification of a method
6. ⏳ Create regression test
7. ⏳ Document workflow (this guide)

---

## 📖 Related Documentation

- [SELF_HOSTING_MVP.md](docs/history/SELF_HOSTING_MVP.md) - Implementation plan for self-hosting (archived)
- [OPTIMIZATION_SUMMARY.md](docs/history/OPTIMIZATION_SUMMARY.md) - Optimization overview (archived)
- [PERFORMANCE_OPTIMIZATION_PLAN.md](docs/history/PERFORMANCE_OPTIMIZATION_PLAN.md) - Detailed optimization plan (archived)
- [ISSUES.md](docs/history/ISSUES.md) - Known issues and blockers (archived)

---

## 🔄 Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-09-20 | - | Initial draft for Phase 5 implementation |
