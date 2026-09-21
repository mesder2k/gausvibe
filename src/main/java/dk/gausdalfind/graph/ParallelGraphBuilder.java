package dk.gausdalfind.graph;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.parser.*;
import dk.gausdalfind.symbols.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A parallel implementation of the graph builder that parses multiple Java files
 * concurrently for improved performance on large projects.
 * 
 * This builder uses a thread pool to parse files in parallel, which can significantly
 * reduce build time for projects with many Java files.
 * 
 * Note: The symbol resolution phase still runs sequentially after all files are parsed,
 * as it requires all declarations to be available in the symbol table.
 */
public class ParallelGraphBuilder {
    
    private final Path projectRoot;
    private final Graph graph;
    private final SymbolTable symbolTable;
    private final int threadPoolSize;
    
    // Statistics
    private final AtomicInteger filesParsed = new AtomicInteger(0);
    private final AtomicInteger filesFailed = new AtomicInteger(0);
    private long parseStartTime;
    private long parseEndTime;
    
    // Default thread pool size
    private static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
    /**
     * Creates a new parallel graph builder for the given project root.
     * Uses the default thread pool size (number of available processors).
     * 
     * @param projectRoot the root directory of the Java project
     */
    public ParallelGraphBuilder(Path projectRoot) {
        this(projectRoot, DEFAULT_THREAD_POOL_SIZE);
    }
    
    /**
     * Creates a new parallel graph builder with a custom thread pool size.
     * 
     * @param projectRoot the root directory of the Java project
     * @param threadPoolSize the number of threads to use for parallel parsing
     */
    public ParallelGraphBuilder(Path projectRoot, int threadPoolSize) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root cannot be null");
        }
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Thread pool size must be at least 1");
        }
        this.projectRoot = projectRoot;
        this.graph = new Graph();
        this.symbolTable = new SymbolTable();
        this.threadPoolSize = threadPoolSize;
    }
    
    /**
     * Builds the complete graph for the project using parallel parsing.
     * 
     * @return the constructed graph
     * @throws IOException if file collection or parsing fails
     * @throws InterruptedException if the parsing threads are interrupted
     * @throws ExecutionException if a parsing task throws an exception
     */
    public Graph build() throws IOException, InterruptedException, ExecutionException {
        parseStartTime = System.currentTimeMillis();
        
        try {
            // Phase 1: Setup JavaParser
            JavaParserConfig.setup(projectRoot);
            
            // Phase 2: Collect Java files
            List<Path> javaFiles = collectJavaFiles();
            
            if (javaFiles.isEmpty()) {
                parseEndTime = System.currentTimeMillis();
                return graph;
            }
            
            // Phase 3: Parse files in parallel
            parseFilesInParallel(javaFiles);
            
            // Phase 4: Resolve symbols (sequential - requires all declarations)
            SymbolResolver resolver = new SymbolResolver(graph, symbolTable);
            resolver.resolve();
            
            // Phase 5: Add derived edges
            addDerivedEdges();
            
            // Phase 6: Validate graph
            validateGraph();
            
            parseEndTime = System.currentTimeMillis();
            return graph;
            
        } catch (IOException e) {
            parseEndTime = System.currentTimeMillis();
            throw e;
        }
    }
    
    /**
     * Returns the graph being built.
     */
    public Graph getGraph() {
        return graph;
    }
    
    /**
     * Returns the symbol table.
     */
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
    
    /**
     * Returns the number of files successfully parsed.
     */
    public int getFilesParsed() {
        return filesParsed.get();
    }
    
    /**
     * Returns the number of files that failed to parse.
     */
    public int getFilesFailed() {
        return filesFailed.get();
    }
    
    /**
     * Returns the total parse time in milliseconds.
     */
    public long getParseTime() {
        return parseEndTime - parseStartTime;
    }
    
    /**
     * Collects all Java files from the project.
     */
    private List<Path> collectJavaFiles() throws IOException {
        return JavaFileCollector.collect(projectRoot);
    }
    
    /**
     * Parses all files in parallel using a thread pool.
     */
    private void parseFilesInParallel(List<Path> javaFiles) 
            throws InterruptedException, ExecutionException {
        
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        List<Future<?>> futures = new ArrayList<>();
        
        for (Path file : javaFiles) {
            Future<?> future = executor.submit(() -> {
                try {
                    parseFile(file);
                    filesParsed.incrementAndGet();
                } catch (Exception e) {
                    filesFailed.incrementAndGet();
                    System.err.println("Failed to parse file: " + file + ": " + e.getMessage());
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            future.get(); // This will throw if any task failed
        }
        
        executor.shutdown();
    }
    
    /**
     * Parses a single Java file and adds its nodes/edges to the graph.
     */
    private void parseFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        // Create visitor context for this file
        VisitorContext ctx = new VisitorContext(
            file,
            graph,
            null,  // package - will be set by PackageDeclarationVisitor
            null,  // current class
            null,  // current method
            0,
            new VisitorContext.Scope(null)
        );
        
        // Process declaration visitor
        new DeclarationVisitor(graph, ctx).visit(cu, ctx);
        
        // Process statement visitor
        new StatementVisitor(graph, ctx).visit(cu, ctx);
        
        // Process expression visitor
        new ExpressionVisitor(graph, ctx).visit(cu, ctx);
    }
    
    /**
     * Adds derived edges to the graph (edges that can be inferred from other edges).
     */
    private void addDerivedEdges() {
        // Add INHERITS edges for classes with superclasses
        for (Node node : graph.getAllNodes()) {
            if (node instanceof ClassNode) {
                ClassNode cls = (ClassNode) node;
                if (cls.hasSuperclass()) {
                    String superclassFqn = cls.getSuperclass();
                    graph.getIndexes().getClassByQualifiedName(superclassFqn)
                        .ifPresent(parent -> {
                            graph.addEdge(new Edge(
                                cls.getId(), 
                                parent.getId(), 
                                EdgeTypes.INHERITS
                            ));
                        });
                }
                
                // Add IMPLEMENTS edges
                for (String iface : cls.getInterfaces()) {
                    graph.getIndexes().getClassByQualifiedName(iface)
                        .ifPresent(interfaceNode -> {
                            graph.addEdge(new Edge(
                                cls.getId(), 
                                interfaceNode.getId(), 
                                EdgeTypes.IMPLEMENTS
                            ));
                        });
                }
            }
        }
    }
    
    /**
     * Validates the constructed graph.
     */
    private void validateGraph() {
        // Basic validation - check for null IDs
        for (Node node : graph.getAllNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                System.err.println("Warning: Node with null/blank ID found");
            }
        }
        
        // Check edge integrity
        for (Edge edge : graph.getAllEdges()) {
            if (edge.getFromId() == null || edge.getToId() == null) {
                System.err.println("Warning: Edge with null from/to ID found");
            } else if (!graph.getNode(edge.getFromId()).isPresent()) {
                System.err.println("Warning: Edge references non-existent from node: " + edge.getFromId());
            } else if (!graph.getNode(edge.getToId()).isPresent()) {
                System.err.println("Warning: Edge references non-existent to node: " + edge.getToId());
            }
        }
    }
    
    /**
     * Builder result containing the graph, statistics, and timing information.
     */
    public static class BuildResult {
        private final Graph graph;
        private final int filesParsed;
        private final int filesFailed;
        private final long parseTimeMs;
        
        public BuildResult(Graph graph, int filesParsed, int filesFailed, long parseTimeMs) {
            this.graph = graph;
            this.filesParsed = filesParsed;
            this.filesFailed = filesFailed;
            this.parseTimeMs = parseTimeMs;
        }
        
        public Graph getGraph() { return graph; }
        public int getFilesParsed() { return filesParsed; }
        public int getFilesFailed() { return filesFailed; }
        public long getParseTimeMs() { return parseTimeMs; }
    }
}
