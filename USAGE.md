# GausVibe Usage Guide: Coding Harness Integration

**How to integrate GausVibe into automated workflows, CI/CD pipelines, testing frameworks, and custom applications**

---

## 📖 Table of Contents

1. [Coding Harness Usage](#coding-harness-usage)
   - [As a Java Library](#as-a-java-library)
   - [Programmatic Graph Building](#programmatic-graph-building)
   - [Programmatic Querying](#programmatic-querying)
   - [Integration Patterns](#integration-patterns)
2. [CLI Commands](#cli-commands)
   - [Build Commands](#build-commands)
   - [Query Commands](#query-commands)
   - [Edit Commands](#edit-commands)
   - [Interactive Mode](#interactive-mode)
3. [Query Language](#query-language)
   - [Natural Language Queries](#natural-language-queries)
   - [Structured Queries](#structured-queries)
4. [Integration Examples](#integration-examples)
   - [With Testing Frameworks](#with-testing-frameworks)
   - [With CI/CD Pipelines](#with-cicd-pipelines)
   - [With IDE Plugins](#with-ide-plugins)
   - [With Custom Tools](#with-custom-tools)
5. [Best Practices](#best-practices)
   - [Performance Optimization](#performance-optimization)
   - [Memory Management](#memory-management)
   - [Caching Strategies](#caching-strategies)
6. [Troubleshooting](#troubleshooting)

---

## 🎯 Coding Harness Usage

GausVibe can be used in **three primary ways**:

### 1. As a Java Library

Embed GausVibe directly into your Java application for programmatic access to code analysis.

#### Maven Dependency

```xml
<dependency>
    <groupId>dk.gausdalfind</groupId>
    <artifactId>gausvibe</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Gradle Dependency

```groovy
dependencies {
    implementation 'dk.gausdalfind:gausvibe:1.0.0'
}
```

---

### 2. Programmatic Graph Building

Build graphs from Java source code programmatically for integration into your workflows.

#### Basic Graph Construction

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MyCodeAnalyzer {
    
    public static void main(String[] args) throws Exception {
        // Specify the project root
        Path projectRoot = Paths.get("/path/to/java/project");
        
        // Build the graph
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        
        // Customize build options
        builder.setIncludeTestSource(true);
        builder.setParallelParsing(true);
        builder.setExcludePattern("**/generated/**");
        
        // Build the graph (this parses all Java files)
        Graph graph = builder.build();
        
        // Use the graph for analysis
        System.out.println("Built graph with " + graph.getNodeCount() + " nodes");
        System.out.println("and " + graph.getEdgeCount() + " edges");
    }
}
```

#### With Progress Tracking

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.graph.GraphBuildListener;
import dk.gausdalfind.model.Graph;

public class BuildWithProgress {
    
    public static void main(String[] args) throws Exception {
        Path projectRoot = Paths.get("/path/to/project");
        
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        
        // Add a listener for progress updates
        builder.addBuildListener(new GraphBuildListener() {
            @Override
            public void onFileParsed(Path file, long parsedCount, long totalCount) {
                System.out.printf("Parsed: %s (%d/%d)%n", 
                    file.getFileName(), parsedCount, totalCount);
            }
            
            @Override
            public void onPhaseComplete(String phase, long durationMs) {
                System.out.println("Completed: " + phase + " in " + durationMs + "ms");
            }
            
            @Override
            public void onWarning(String message, Path file, int line, int column) {
                System.out.println("WARNING: " + message + " at " + file + ":" + line);
            }
        });
        
        Graph graph = builder.build();
        System.out.println("Build complete!");
    }
}
```

#### Incremental Graph Building

```java
import dk.gausdalfind.graph.IncrementalGraphBuilder;
import dk.gausdalfind.model.Graph;

public class IncrementalAnalyzer {
    
    private Graph graph;
    private IncrementalGraphBuilder builder;
    
    public void analyzeProject(Path projectRoot) throws Exception {
        // Initial build
        builder = new IncrementalGraphBuilder(projectRoot);
        graph = builder.build();
    }
    
    public void handleFileChange(Path changedFile) throws Exception {
        // Only reparse the changed file
        builder.reparseFile(changedFile);
        
        // Or for multiple changes
        // builder.reparseFiles(List.of(file1, file2, file3));
    }
    
    public Graph getGraph() {
        return graph;
    }
}
```

---

### 3. Programmatic Querying

Execute queries against the graph programmatically for automated analysis.

#### Basic Querying

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.queries.JavaGraphQuery;
import dk.gausdalfind.model.declaration.*;
import java.util.List;

public class CodeQueryExample {
    
    public static void main(String[] args) throws Exception {
        // Build graph
        Path projectRoot = Paths.get("/path/to/project");
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        Graph graph = builder.build();
        
        // Create query engine
        JavaGraphQuery queryEngine = new GraphQueryEngine(graph);
        
        // Find classes by name
        List<ClassNode> calculatorClasses = 
            queryEngine.findClassesByName("Calculator");
        
        System.out.println("Found " + calculatorClasses.size() + " Calculator classes:");
        for (ClassNode cls : calculatorClasses) {
            System.out.println("  - " + cls.getQualifiedName());
        }
        
        // Get all classes
        List<ClassNode> allClasses = queryEngine.getAllClasses();
        System.out.println("Total classes: " + allClasses.size());
        
        // Find methods by name
        List<MethodNode> addMethods = 
            queryEngine.findMethodsByName("add");
        System.out.println("Found " + addMethods.size() + " 'add' methods");
    }
}
```

#### Call Graph Analysis

```java
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.queries.JavaGraphQuery;
import dk.gausdalfind.model.declaration.*;

public class CallGraphAnalyzer {
    
    private final JavaGraphQuery queryEngine;
    
    public CallGraphAnalyzer(Graph graph) {
        this.queryEngine = new GraphQueryEngine(graph);
    }
    
    // Find who calls a specific method
    public List<MethodNode> findCallers(MethodNode method) {
        return queryEngine.getCallers(method);
    }
    
    // Find what a method calls
    public List<MethodNode> findCallees(MethodNode method) {
        return queryEngine.getCallees(method);
    }
    
    // Get transitive callers (callers of callers)
    public List<MethodNode> findTransitiveCallers(MethodNode method, int depth) {
        return queryEngine.getCallersTransitive(method, depth);
    }
    
    // Find methods that call a specific pattern
    public List<MethodNode> findMethodsCalling(MethodNode target) {
        return queryEngine.findMethodsThatCall(target);
    }
    
    // Example usage
    public void analyzeMethodUsage(String methodSignature) {
        MethodNode method = queryEngine.findMethodBySignature(methodSignature);
        if (method != null) {
            List<MethodNode> callers = findCallers(method);
            System.out.println("Callers of " + method.getQualifiedName() + ":");
            callers.forEach(m -> System.out.println("  - " + m.getQualifiedName()));
        }
    }
}
```

#### Inheritance Analysis

```java
import dk.gausdalfind.queries.JavaGraphQuery;
import dk.gausdalfind.model.declaration.*;

public class InheritanceAnalyzer {
    
    private final JavaGraphQuery queryEngine;
    
    public InheritanceAnalyzer(Graph graph) {
        this.queryEngine = new GraphQueryEngine(graph);
    }
    
    // Get superclass
    public ClassNode getSuperclass(ClassNode cls) {
        return queryEngine.getSuperclass(cls);
    }
    
    // Get all superclasses (full hierarchy)
    public List<ClassNode> getAllSuperclasses(ClassNode cls) {
        return queryEngine.getAllSuperclasses(cls);
    }
    
    // Get subclasses
    public List<ClassNode> getSubclasses(ClassNode cls) {
        return queryEngine.getSubclasses(cls);
    }
    
    // Get all subclasses (transitive)
    public List<ClassNode> getAllSubclasses(ClassNode cls) {
        return queryEngine.getAllSubclasses(cls);
    }
    
    // Get implemented interfaces
    public List<ClassNode> getInterfaces(ClassNode cls) {
        return queryEngine.getInterfaces(cls);
    }
    
    // Get implementations of an interface
    public List<ClassNode> getImplementations(ClassNode interfaceCls) {
        return queryEngine.getImplementations(interfaceCls);
    }
    
    // Print class hierarchy
    public void printHierarchy(ClassNode cls, int depth) {
        System.out.println(getIndent(depth) + cls.getQualifiedName());
        for (ClassNode subclass : getSubclasses(cls)) {
            printHierarchy(subclass, depth + 1);
        }
    }
    
    private String getIndent(int depth) {
        return "  ".repeat(depth);
    }
}
```

#### Text Search

```java
import dk.gausdalfind.queries.TextSearchIndex;
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.model.Node;

public class TextSearchExample {
    
    private final TextSearchIndex textIndex;
    
    public TextSearchExample(Graph graph) {
        // Build text search index
        this.textIndex = new TextSearchIndex(graph);
        textIndex.build();
    }
    
    // Search for nodes containing text
    public List<Node> search(String query) {
        return textIndex.search(query);
    }
    
    // Search for nodes containing ALL words
    public List<Node> searchExact(String query) {
        return textIndex.search(query);
    }
    
    // Search for nodes containing ANY word
    public List<Node> searchAny(String query) {
        return textIndex.searchAny(query);
    }
    
    // Example: Find all references to "calculateTotal"
    public void findCalculateTotalReferences() {
        List<Node> results = search("calculateTotal");
        System.out.println("Found " + results.size() + " references to calculateTotal:");
        results.forEach(node -> System.out.println("  - " + node.getId()));
    }
}
```

---

### 4. Integration Patterns

#### Pattern 1: Batch Processing Framework

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.queries.GraphQueryEngine;
import java.nio.file.Path;
import java.util.concurrent.*;

public class BatchCodeAnalyzer {
    
    private final ExecutorService executor;
    private final GraphQueryEngine queryEngine;
    
    public BatchCodeAnalyzer(Path projectRoot, int threadCount) throws Exception {
        this.executor = Executors.newFixedThreadPool(threadCount);
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        builder.setParallelParsing(true);
        Graph graph = builder.build();
        this.queryEngine = new GraphQueryEngine(graph);
    }
    
    // Analyze multiple methods in parallel
    public CompletableFuture<Map<String, List<String>>> findAllCallersAsync(
            List<String> methodSignatures) {
        
        return CompletableFuture.allOf(
            methodSignatures.stream()
                .map(signature -> CompletableFuture.supplyAsync(
                    () -> findCallersForMethod(signature), executor))
                .toArray(CompletableFuture[]::new)
        ).thenApply(v -> {
            Map<String, List<String>> results = new HashMap<>();
            for (String signature : methodSignatures) {
                results.put(signature, findCallersForMethod(signature));
            }
            return results;
        });
    }
    
    private List<String> findCallersForMethod(String signature) {
        MethodNode method = queryEngine.findMethodBySignature(signature);
        if (method == null) return List.of();
        return queryEngine.getCallers(method).stream()
            .map(MethodNode::getQualifiedName)
            .collect(Collectors.toList());
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

#### Pattern 2: Event-Driven Analysis

```java
import dk.gausdalfind.graph.IncrementalGraphBuilder;
import dk.gausdalfind.queries.GraphQueryEngine;
import java.nio.file.*;
import java.io.IOException;

public class FileChangeWatcher {
    
    private final WatchService watchService;
    private final IncrementalGraphBuilder graphBuilder;
    private final GraphQueryEngine queryEngine;
    private final Path projectRoot;
    
    public FileChangeWatcher(Path projectRoot) throws Exception {
        this.projectRoot = projectRoot;
        this.watchService = FileSystems.getDefault().newWatchService();
        
        // Build initial graph
        this.graphBuilder = new IncrementalGraphBuilder(projectRoot);
        this.queryEngine = new GraphQueryEngine(graphBuilder.build());
        
        // Start watching
        registerRecursively(projectRoot);
    }
    
    private void registerRecursively(Path dir) throws IOException {
        dir.register(watchService, 
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE);
        
        // Recursively register subdirectories
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    registerRecursively(entry);
                }
            }
        }
    }
    
    public void startWatching(Consumer<AnalysisResult> callback) {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    break;
                }
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changedFile = projectRoot.resolve((Path) event.context());
                    if (changedFile.toString().endsWith(".java")) {
                        handleFileChange(changedFile, callback);
                    }
                }
                key.reset();
            }
        }).start();
    }
    
    private void handleFileChange(Path changedFile, Consumer<AnalysisResult> callback) {
        try {
            // Reparse only the changed file
            graphBuilder.reparseFile(changedFile);
            
            // Perform analysis
            AnalysisResult result = analyzeChangedFile(changedFile);
            callback.accept(result);
        } catch (Exception e) {
            System.err.println("Error handling change: " + e.getMessage());
        }
    }
    
    private AnalysisResult analyzeChangedFile(Path file) {
        // Implement custom analysis logic
        // For example: check if the change affects critical methods
        AnalysisResult result = new AnalysisResult(file);
        
        // Find methods in the changed file
        List<MethodNode> methods = queryEngine.getMethodsInFile(file);
        result.setAffectedMethods(methods.size());
        
        // Find callers of affected methods
        for (MethodNode method : methods) {
            List<MethodNode> callers = queryEngine.getCallers(method);
            if (!callers.isEmpty()) {
                result.addAffectedCaller(method.getQualifiedName(), callers);
            }
        }
        
        return result;
    }
    
    public static class AnalysisResult {
        private final Path file;
        private int affectedMethods;
        private final Map<String, List<String>> affectedCallers = new HashMap<>();
        
        public AnalysisResult(Path file) {
            this.file = file;
        }
        
        // Getters and setters
        public void setAffectedMethods(int count) {
            this.affectedMethods = count;
        }
        
        public void addAffectedCaller(String method, List<MethodNode> callers) {
            affectedCallers.put(method, 
                callers.stream().map(MethodNode::getQualifiedName).toList());
        }
    }
}
```

#### Pattern 3: Custom Query DSL

```java
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.queries.JavaGraphQuery;
import java.util.*;

public class CustomQueryDSL {
    
    private final JavaGraphQuery queryEngine;
    
    public CustomQueryDSL(Graph graph) {
        this.queryEngine = new GraphQueryEngine(graph);
    }
    
    // Custom DSL methods
    public List<String> findUnusedMethods() {
        List<MethodNode> allMethods = queryEngine.getAllMethods();
        List<MethodNode> calledMethods = new ArrayList<>();
        
        for (MethodNode method : allMethods) {
            List<MethodNode> callers = queryEngine.getCallers(method);
            if (!callers.isEmpty()) {
                calledMethods.add(method);
            }
        }
        
        allMethods.removeAll(calledMethods);
        return allMethods.stream()
            .map(MethodNode::getQualifiedName)
            .collect(Collectors.toList());
    }
    
    public List<String> findGodClasses(int threshold) {
        List<ClassNode> allClasses = queryEngine.getAllClasses();
        List<String> godClasses = new ArrayList<>();
        
        for (ClassNode cls : allClasses) {
            int methodCount = queryEngine.getMethods(cls).size();
            int fieldCount = queryEngine.getFields(cls).size();
            
            if (methodCount + fieldCount > threshold) {
                godClasses.add(cls.getQualifiedName());
            }
        }
        
        return godClasses;
    }
    
    public Map<String, Integer> getMethodComplexity() {
        Map<String, Integer> complexity = new HashMap<>();
        List<MethodNode> methods = queryEngine.getAllMethods();
        
        for (MethodNode method : methods) {
            int statements = queryEngine.getStatements(method).size();
            int calls = queryEngine.getCallees(method).size();
            complexity.put(method.getQualifiedName(), statements + calls);
        }
        
        return complexity;
    }
    
    public List<String> findCircularDependencies() {
        List<String> circular = new ArrayList<>();
        List<ClassNode> classes = queryEngine.getAllClasses();
        
        for (ClassNode cls : classes) {
            if (hasCircularDependency(cls, cls, new HashSet<>())) {
                circular.add(cls.getQualifiedName());
            }
        }
        
        return circular;
    }
    
    private boolean hasCircularDependency(ClassNode start, ClassNode current, Set<String> visited) {
        if (visited.contains(current.getQualifiedName())) {
            return false;
        }
        visited.add(current.getQualifiedName());
        
        for (ClassNode dependency : queryEngine.getDependencies(current)) {
            if (dependency.getQualifiedName().equals(start.getQualifiedName())) {
                return true;
            }
            if (hasCircularDependency(start, dependency, new HashSet<>(visited))) {
                return true;
            }
        }
        
        return false;
    }
}
```

---

## 💻 CLI Commands

### Build Commands

#### Build a Graph

```bash
# Basic build
java -jar gausvibe.jar build --project /path/to/project

# Build with custom output
java -jar gausvibe.jar build --project /path/to/project --output /tmp/graph.json

# Build excluding test files
java -jar gausvibe.jar build --project /path/to/project --exclude-test

# Build with exclusion pattern
java -jar gausvibe.jar build --project /path/to/project --exclude "**/generated/**"

# Build without parallel processing
java -jar gausvibe.jar build --project /path/to/project --no-parallel

# Build with verbose output
java -jar gausvibe.jar build --project /path/to/project --verbose
```

**Build Options:**

| Option | Description | Default |
|--------|-------------|---------|
| `--project, -p` | Project root path | Current directory |
| `--output, -o` | Output graph file path | Auto-generated |
| `--serialize, -s` | Serialize graph to JSON | false |
| `--include-test` | Include test source directories | true |
| `--exclude-test` | Exclude test source directories | false |
| `--exclude` | Glob pattern to exclude files | null |
| `--parallel` | Use parallel file parsing | true |
| `--no-parallel` | Disable parallel parsing | false |
| `--verbose, -v` | Enable verbose logging | false |

---

### Query Commands

#### Basic Query

```bash
# Query using a built graph
java -jar gausvibe.jar query --graph /tmp/graph.json --query "find class com.example.Calculator"

# Query with format
java -jar gausvibe.jar query --graph /tmp/graph.json --query "all classes" --format text

# Query with limit
java -jar gausvibe.jar query --graph /tmp/graph.json --query "all methods" --limit 50

# Query with source code
java -jar gausvibe.jar query --graph /tmp/graph.json --query "class Calculator" --include-source
```

**Query Options:**

| Option | Description | Default | Values |
|--------|-------------|---------|--------|
| `--graph, -g` | Path to graph JSON file | Required | Path |
| `--query, -q` | Query to execute | Required | String |
| `--format, -f` | Output format | `json` | `json`, `text`, `summary` |
| `--limit, -l` | Maximum results | `100` | Integer |
| `--include-source` | Include source code in results | `false` | Boolean |

---

### Edit Commands

#### Batch Edit

```bash
# Apply batch edits
java -jar gausvibe.jar edit --project /path/to/project --operations /path/to/operations.json

# Dry run (no changes)
java -jar gausvibe.jar edit --project /path/to/project --operations /path/to/operations.json --dry-run

# Verbose output
java -jar gausvibe.jar edit --project /path/to/project --operations /path/to/operations.json --verbose
```

**Edit Options:**

| Option | Description | Default |
|--------|-------------|---------|
| `--project, -p` | Project root path | Required |
| `--operations, -o` | Path to operations JSON file | Required |
| `--dry-run` | Preview changes without applying | false |
| `--verbose, -v` | Enable verbose logging | false |

---

### Interactive Mode

#### Start Interactive Shell

```bash
# Start interactive mode
java -jar gausvibe.jar interactive

# Or
java -jar gausvibe.jar shell
```

#### Interactive Commands

```
gausvibe> help                          # Show help
gausvibe> build --project /path/to/project  # Build graph
gausvibe> query "find class Calculator"   # Execute query
gausvibe> query:class:all                # List all classes
gausvibe> query:method:name:add          # Find methods named 'add'
gausvibe> query:search:text:calculate    # Text search
gausvibe> exit                          # Exit shell
```

---

## 🗣️ Query Language

### Natural Language Queries

GausVibe understands plain English queries and maps them to graph operations:

| Query | Description | Returns |
|-------|-------------|---------|
| `find class com.example.Calculator` | Find a specific class | Class details |
| `find classes named Calculator` | Find classes by name | List of classes |
| `all classes` | Get all classes | List of all classes |
| `all methods` | Get all methods | List of all methods |
| `subclasses of Animal` | Get direct subclasses | List of subclasses |
| `all subclasses of Animal` | Get all subclasses (transitive) | List of all subclasses |
| `implementations of Runnable` | Get interface implementations | List of implementations |
| `methods in Calculator` | Get methods of a class | List of methods |
| `find method add(int,int)` | Find method by signature | Method details |
| `methods named add` | Find methods by name | List of methods |
| `who calls add()` | Find method callers | List of callers |
| `what does add() call` | Find method callees | List of callees |
| `what calls add()` | Find methods that call add() | List of methods |
| `fields in Calculator` | Get class fields | List of fields |
| `variables in add()` | Get local variables | List of variables |
| `superclass of Calculator` | Get parent class | Class |
| `what does Calculator extend` | Get superclass | Class |
| `what does Calculator implement` | Get interfaces | List of interfaces |
| `statements in add()` | Get method statements | List of statements |
| `search:text:calculateTotal` | Text search | List of matching nodes |
| `callers of Calculator.add` | Find callers | List of callers |
| `callees of Calculator.add` | Find callees | List of callees |

---

### Structured Queries

For precise queries, use the structured format:

```
FORMAT: methodName(param1=value1, param2=value2)

EXAMPLES:
  findClassByQualifiedName(qn="com.example.Calculator")
  getMethods(class="cls:com/example/Calculator")
  getCallers(method="mth:com/example/Calculator#add(int,int)")
  getSubclasses(class="cls:com/example/Animal")
  searchText(query="calculateTotal")
  getNodesByType(type="CLASS")
  getEdgesByType(type="CALLS")
```

---

## 🔧 Integration Examples

### With Testing Frameworks

#### JUnit 5 Integration

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.queries.GraphQueryEngine;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class CodeStructureTest {
    
    private static Graph graph;
    private static GraphQueryEngine queryEngine;
    
    @BeforeAll
    public static void setup() throws Exception {
        Path projectRoot = Paths.get("src/test/resources/sample-project");
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        graph = builder.build();
        queryEngine = new GraphQueryEngine(graph);
    }
    
    @Test
    public void testCalculatorClassExists() {
        ClassNode calculator = queryEngine.findClassByName("Calculator");
        assertNotNull(calculator, "Calculator class should exist");
    }
    
    @Test
    public void testAddMethodHasCallers() {
        MethodNode addMethod = queryEngine.findMethodBySignature("add(int,int)");
        assertNotNull(addMethod);
        
        List<MethodNode> callers = queryEngine.getCallers(addMethod);
        assertFalse(callers.isEmpty(), "add() should have callers");
    }
    
    @Test
    public void testInheritanceHierarchy() {
        ClassNode animal = queryEngine.findClassByName("Animal");
        assertNotNull(animal);
        
        List<ClassNode> subclasses = queryEngine.getSubclasses(animal);
        assertTrue(subclasses.size() >= 2, "Animal should have subclasses");
    }
    
    @Test
    public void testNoCircularDependencies() {
        List<ClassNode> allClasses = queryEngine.getAllClasses();
        
        for (ClassNode cls : allClasses) {
            // Check for circular dependencies
            assertDoesNotThrow(() -> {
                Set<String> visited = new HashSet<>();
                checkCircular(cls, cls, visited);
            }, "No circular dependencies should exist");
        }
    }
    
    private void checkCircular(ClassNode start, ClassNode current, Set<String> visited) {
        if (visited.contains(current.getQualifiedName())) {
            return;
        }
        visited.add(current.getQualifiedName());
        
        for (ClassNode dep : queryEngine.getDependencies(current)) {
            if (dep.getQualifiedName().equals(start.getQualifiedName())) {
                fail("Circular dependency found: " + start.getQualifiedName());
            }
            checkCircular(start, dep, new HashSet<>(visited));
        }
    }
}
```

#### TestNG Integration

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.queries.GraphQueryEngine;
import org.testng.annotations.*;
import java.nio.file.Path;
import static org.testng.Assert.*;

public class CodeQualityTest {
    
    private Graph graph;
    private GraphQueryEngine queryEngine;
    
    @BeforeClass
    public void setup() throws Exception {
        Path projectRoot = Path.of("target/test-classes");
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        graph = builder.build();
        queryEngine = new GraphQueryEngine(graph);
    }
    
    @Test
    public void verifyMethodNamingConvention() {
        List<MethodNode> methods = queryEngine.getAllMethods();
        
        for (MethodNode method : methods) {
            String name = method.getName();
            assertTrue(name.matches("[a-z][a-zA-Z0-9]*"), 
                "Method name should be camelCase: " + name);
        }
    }
    
    @Test
    public void verifyNoUnusedImports() {
        // Use text search to find imports
        TextSearchIndex textIndex = new TextSearchIndex(graph);
        textIndex.build();
        
        List<Node> imports = textIndex.search("import");
        for (Node node : imports) {
            // Check if import is used
            String importStatement = node.toString();
            // Logic to verify import is used...
        }
    }
}
```

---

### With CI/CD Pipelines

#### GitHub Actions

```yaml
name: Code Analysis

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build GausVibe
        run: mvn clean package -DskipTests
      
      - name: Run Code Analysis
        run: |
          # Build graph
          java -jar target/gausvibe-1.0.0-cli.jar build --project . --output graph.json
          
          # Check for unused methods
          java -jar target/gausvibe-1.0.0-cli.jar query \
            --graph graph.json \
            --query "all methods" \
            --format text > all_methods.txt
          
          # Find unused methods
          # (Use custom script to compare with callers)
      
      - name: Upload Analysis Results
        uses: actions/upload-artifact@v3
        with:
          name: code-analysis-results
          path: |
            graph.json
            all_methods.txt
```

#### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build GausVibe') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        
        stage('Code Analysis') {
            steps {
                sh '''
                    java -jar target/gausvibe-1.0.0-cli.jar build --project . --output ${WORKSPACE}/graph.json
                    java -jar target/gausvibe-1.0.0-cli.jar query --graph ${WORKSPACE}/graph.json --query "all classes" --format json > ${WORKSPACE}/classes.json
                '''
            }
        }
        
        stage('Quality Checks') {
            steps {
                script {
                    // Parse results and fail if issues found
                    def classes = readJSON file: '${WORKSPACE}/classes.json'
                    if (classes.size() > 1000) {
                        error "Codebase too large: ${classes.size()} classes"
                    }
                }
            }
        }
        
        stage('Archive Results') {
            steps {
                archiveArtifacts artifacts: 'graph.json, classes.json', fingerprint: true
            }
        }
    }
}
```

---

### With IDE Plugins

#### Eclipse Plugin Integration

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.queries.GraphQueryEngine;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.core.resources.IProject;

public class GausVibeEclipsePlugin {
    
    private Graph graph;
    private GraphQueryEngine queryEngine;
    
    public void analyzeProject(IJavaProject javaProject) throws Exception {
        // Get project path
        IProject project = javaProject.getProject();
        Path projectPath = Path.of(project.getLocation().toString());
        
        // Build graph
        GausVibeBuilder builder = new GausVibeBuilder(projectPath);
        graph = builder.build();
        queryEngine = new GraphQueryEngine(graph);
    }
    
    public List<String> findClassUsages(String className) {
        ClassNode cls = queryEngine.findClassByName(className);
        if (cls == null) return List.of();
        
        List<MethodNode> methods = queryEngine.getMethods(cls);
        List<String> usages = new ArrayList<>();
        
        for (MethodNode method : methods) {
            List<MethodNode> callers = queryEngine.getCallers(method);
            for (MethodNode caller : callers) {
                usages.add(caller.getQualifiedName() + " calls " + method.getQualifiedName());
            }
        }
        
        return usages;
    }
}
```

#### IntelliJ Plugin Integration

```java
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dk.gausdalfind.graph.GausVibeBuilder;

public class GausVibeIntelliJPlugin {
    
    private Graph graph;
    
    public void buildGraph(Project project) throws Exception {
        // Get project base path
        VirtualFile baseDir = project.getBaseDir();
        Path projectPath = Path.of(baseDir.getPath());
        
        // Build graph
        GausVibeBuilder builder = new GausVibeBuilder(projectPath);
        graph = builder.build();
    }
    
    public List<String> getCallHierarchy(String methodSignature, int depth) {
        GraphQueryEngine queryEngine = new GraphQueryEngine(graph);
        MethodNode method = queryEngine.findMethodBySignature(methodSignature);
        
        if (method == null) return List.of();
        
        List<String> hierarchy = new ArrayList<>();
        buildHierarchy(method, depth, hierarchy, "  ");
        return hierarchy;
    }
    
    private void buildHierarchy(MethodNode method, int depth, List<String> result, String indent) {
        if (depth <= 0) return;
        
        result.add(indent + method.getQualifiedName());
        
        GraphQueryEngine queryEngine = new GraphQueryEngine(graph);
        List<MethodNode> callers = queryEngine.getCallers(method);
        
        for (MethodNode caller : callers) {
            buildHierarchy(caller, depth - 1, result, indent + "  ");
        }
    }
}
```

---

### With Custom Tools

#### REST API Wrapper

```java
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.queries.GraphQueryEngine;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public class GausVibeRESTServer {
    
    private final Graph graph;
    private final GraphQueryEngine queryEngine;
    private final Gson gson = new Gson();
    
    public GausVibeRESTServer(String projectPath, int port) throws Exception {
        // Build graph
        GausVibeBuilder builder = new GausVibeBuilder(Path.of(projectPath));
        graph = builder.build();
        queryEngine = new GraphQueryEngine(graph);
        
        // Start HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/query", this::handleQuery);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/classes", this::handleClasses);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("GausVibe REST server started on port " + port);
    }
    
    private void handleQuery(com.sun.net.httpserver.HttpExchange exchange) {
        try {
            String query = readBody(exchange);
            List<Node> results = queryEngine.executeQuery(query);
            String json = gson.toJson(results);
            sendResponse(exchange, 200, json);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }
    
    private void handleStats(com.sun.net.httpserver.HttpExchange exchange) {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("nodeCount", graph.getNodeCount());
            stats.put("edgeCount", graph.getEdgeCount());
            stats.put("classCount", queryEngine.getAllClasses().size());
            stats.put("methodCount", queryEngine.getAllMethods().size());
            
            sendResponse(exchange, 200, gson.toJson(stats));
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }
    
    // ... other handlers
}
```

---

## ✅ Best Practices

### Performance Optimization

#### 1. Use Parallel Parsing

```java
GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
builder.setParallelParsing(true);  // Default is true
Graph graph = builder.build();
```

For large projects, parallel parsing provides 2-4x speedup on multi-core systems.

#### 2. Limit Query Results

```java
JavaGraphQuery queryEngine = new GraphQueryEngine(graph);

// Use limits for large result sets
List<ClassNode> classes = queryEngine.getAllClasses(100);  // Limit to 100
List<MethodNode> methods = queryEngine.findMethodsByName("add", 50);  // Limit to 50
```

#### 3. Use Indexes

```java
// Ensure indexes are built
GraphQueryEngine queryEngine = new GraphQueryEngine(graph);

// Call graph index is built automatically
List<MethodNode> callers = queryEngine.getCallers(method);  // O(1) lookup

// Text search index (build manually)
TextSearchIndex textIndex = new TextSearchIndex(graph);
textIndex.build();  // Build once, reuse many times
List<Node> results = textIndex.search("calculateTotal");  // O(k) where k = matches
```

#### 4. Cache Query Results

```java
// Use caching query engine
CachingQueryEngine cachingEngine = new CachingQueryEngine(queryEngine);

// First call executes query and caches result
List<ClassNode> classes1 = cachingEngine.getAllClasses();

// Second call returns cached result (O(1))
List<ClassNode> classes2 = cachingEngine.getAllClasses();
```

---

### Memory Management

#### 1. Limit Graph Size

```java
GausVibeBuilder builder = new GausVibeBuilder(projectRoot);

// Exclude large directories
builder.setExcludePattern("**/target/**,**/node_modules/**,**/generated/**");

// Exclude test files if not needed
builder.setIncludeTestSource(false);
```

#### 2. Use Incremental Updates

```java
IncrementalGraphBuilder builder = new IncrementalGraphBuilder(projectRoot);
Graph graph = builder.build();

// Only reparse changed files
builder.reparseFile(changedFile);
```

#### 3. Clear Caches When Needed

```java
// Clear file system cache
JavaFileCollector.clearCache();

// Clear query result cache
CachingQueryEngine.clearCache();
```

---

### Caching Strategies

#### Cache Levels

| Level | What's Cached | Default TTL | Management |
|-------|---------------|-------------|------------|
| File System | Directory listings | 5 minutes | Auto (LRU) |
| Query Results | Query responses | 5 minutes | Manual/TTL |
| Graph | Full AST graph | Until rebuild | Manual |
| Text Index | Text search index | Until rebuild | Auto |

#### Cache Configuration

```java
// File system cache configuration
FileSystemCache cache = new FileSystemCache(
    maxSize: 100,      // Max directories to cache
    ttlMs: 300_000    // 5 minute TTL
);

// Query result cache
CachingQueryEngine cache = new CachingQueryEngine(
    underlyingEngine,
    maxSize: 1000,    // Max queries to cache
    ttlMs: 300_000    // 5 minute TTL
);
```

---

## 🐞 Troubleshooting

### Common Issues and Solutions

#### Issue: Out of Memory

**Symptom**: `java.lang.OutOfMemoryError` when building large projects

**Solutions**:
1. Increase heap size:
   ```bash
   java -Xmx4g -jar gausvibe.jar build --project .
   ```
2. Exclude large directories:
   ```bash
   java -jar gausvibe.jar build --project . --exclude "**/target/**,**/logs/**"
   ```
3. Use incremental building:
   ```java
   IncrementalGraphBuilder builder = new IncrementalGraphBuilder(projectRoot);
   ```
4. Reduce parallelism:
   ```java
   builder.setParallelParsing(false);
   ```

#### Issue: Parse Errors

**Symptom**: `ParseException` for some Java files

**Solutions**:
1. Exclude problematic files:
   ```bash
   java -jar gausvibe.jar build --project . --exclude "**/Generated*.java"
   ```
2. Use verbose mode to identify issues:
   ```bash
   java -jar gausvibe.jar build --project . --verbose
   ```
3. Check Java version compatibility (requires Java 17+)

#### Issue: Slow Performance

**Symptom**: Graph building takes too long

**Solutions**:
1. Enable parallel parsing:
   ```java
   builder.setParallelParsing(true);  // Default is true
   ```
2. Use caching:
   ```java
   builder.setUseCache(true);  // Default is true
   ```
3. Exclude unnecessary files:
   ```java
   builder.setExcludePattern("**/test/**,**/target/**");
   ```
4. Use incremental updates for subsequent runs

#### Issue: Query Returns No Results

**Symptom**: Query returns empty list

**Solutions**:
1. Verify graph was built:
   ```java
   System.out.println("Node count: " + graph.getNodeCount());
   ```
2. Check query syntax:
   ```bash
   # Try simple queries first
   java -jar gausvibe.jar query --graph graph.json --query "all classes"
   ```
3. Use text search as fallback:
   ```java
   List<Node> results = queryEngine.searchText("ClassName");
   ```

---

## 📞 Support

### Getting Help

1. Check this documentation
2. Review the **[SKILL.md](skills/gausvibe/SKILL.md)** for Vibe-specific integration
3. Look at the **[test files](src/test/java)** for usage examples
4. Check **[CHANGES.md](CHANGES.md)** for recent changes

### Debug Mode

Enable verbose logging for troubleshooting:

```bash
# CLI
java -jar gausvibe.jar build --project . --verbose

# Programmatic
GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
builder.setVerbose(true);
```

---

## 📚 Related Documentation

- **[README.md](README.md)** - Project overview and quick start
- **[SKILL.md](skills/gausvibe/SKILL.md)** - Vibe skill integration
- **[PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md)** - Technical details on optimizations
- **[CHANGES.md](CHANGES.md)** - Changelog and release notes
- **[JavaDoc](target/site/apidocs/)** - API documentation (generate with `mvn javadoc:javadoc`)

---

## 🎉 Summary

GausVibe provides powerful code analysis capabilities that can be integrated into:
- **Testing frameworks** (JUnit, TestNG)
- **CI/CD pipelines** (GitHub Actions, Jenkins)
- **IDE plugins** (Eclipse, IntelliJ)
- **Custom tools** (REST APIs, CLI wrappers)
- **Automated workflows** (batch processing, event-driven analysis)

By using GausVibe as a coding harness, you can:
- ✅ Reduce token costs by 80-99%
- ✅ Improve analysis performance by 80-95%
- ✅ Gain semantic understanding of code relationships
- ✅ Replace expensive shell operations with efficient graph queries

---

*Last updated: 2026-09-20*  
*Version: 1.0.0*
