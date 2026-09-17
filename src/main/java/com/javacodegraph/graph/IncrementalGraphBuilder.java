package com.javacodegraph.graph;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.javacodegraph.model.*;
import com.javacodegraph.model.declaration.*;
import com.javacodegraph.parser.*;
import com.javacodegraph.symbols.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An incremental graph builder that maintains a graph and updates it
 * as source files change, rather than rebuilding from scratch each time.
 * 
 * This is useful for:
 * - IDE integration where files are edited frequently
 * - Continuous analysis where only changed files need reprocessing
 * - Large projects where full rebuilds are expensive
 * 
 * The builder tracks file modification times and only reparses files
 * that have changed since the last build.
 */
public class IncrementalGraphBuilder {
    
    private final Path projectRoot;
    private final Graph graph;
    private final SymbolTable symbolTable;
    
    // Map of file path to last modification time
    private final Map<Path, Long> fileModificationTimes = new ConcurrentHashMap<>();
    
    // Map of file path to nodes created from that file
    private final Map<Path, List<Node>> nodesByFile = new ConcurrentHashMap<>();
    
    // Track which files have been processed
    private final Set<Path> processedFiles = new HashSet<>();
    
    // Statistics
    private int totalFilesParsed = 0;
    private int filesUpdated = 0;
    private int filesSkipped = 0;
    private long lastBuildTime = 0;
    
    /**
     * Creates a new incremental graph builder for the given project root.
     * 
     * @param projectRoot the root directory of the Java project
     */
    public IncrementalGraphBuilder(Path projectRoot) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root cannot be null");
        }
        this.projectRoot = projectRoot;
        this.graph = new Graph();
        this.symbolTable = new SymbolTable();
    }
    
    /**
     * Creates an incremental builder from an existing graph.
     * Useful for loading a previously saved graph and continuing from there.
     * 
     * @param projectRoot the root directory of the Java project
     * @param existingGraph the existing graph to build upon
     * @param existingSymbolTable the existing symbol table
     */
    public IncrementalGraphBuilder(Path projectRoot, Graph existingGraph, SymbolTable existingSymbolTable) {
        this(projectRoot);
        if (existingGraph != null) {
            this.graph.addAll(existingGraph);
        }
        if (existingSymbolTable != null) {
            this.symbolTable.merge(existingSymbolTable);
        }
    }
    
    /**
     * Builds or updates the graph incrementally.
     * Only files that have been modified since the last build will be reparsed.
     * 
     * @return the updated graph
     * @throws IOException if file collection or parsing fails
     */
    public Graph build() throws IOException {
        long startTime = System.currentTimeMillis();
        
        // Phase 1: Setup JavaParser
        JavaParserConfig.setup(projectRoot);
        
        // Phase 2: Collect Java files
        List<Path> javaFiles = JavaFileCollector.collect(projectRoot);
        
        // Phase 3: Determine which files need to be (re)parsed
        List<Path> filesToParse = new ArrayList<>();
        for (Path file : javaFiles) {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            
            if (!processedFiles.contains(file)) {
                // New file - needs parsing
                filesToParse.add(file);
            } else if (fileModificationTimes.containsKey(file)) {
                long previousModified = fileModificationTimes.get(file);
                if (lastModified > previousModified) {
                    // Modified file - needs reparsing
                    filesToParse.add(file);
                } else {
                    // Unchanged file - skip
                    filesSkipped++;
                }
            } else {
                // First time seeing this file
                filesToParse.add(file);
            }
        }
        
        // Phase 4: Parse changed files
        for (Path file : filesToParse) {
            parseFile(file);
            fileModificationTimes.put(file, Files.getLastModifiedTime(file).toMillis());
            processedFiles.add(file);
            filesUpdated++;
        }
        
        totalFilesParsed += filesToParse.size();
        
        // Phase 5: Resolve symbols
        SymbolResolver resolver = new SymbolResolver(graph, symbolTable);
        resolver.resolve();
        
        // Phase 6: Add derived edges
        addDerivedEdges();
        
        // Phase 7: Validate graph
        validateGraph();
        
        lastBuildTime = System.currentTimeMillis() - startTime;
        return graph;
    }
    
    /**
     * Forces a full rebuild, clearing all cached state.
     */
    public void forceFullRebuild() {
        fileModificationTimes.clear();
        nodesByFile.clear();
        processedFiles.clear();
        totalFilesParsed = 0;
        filesUpdated = 0;
        filesSkipped = 0;
    }
    
    /**
     * Removes a file from the graph and its associated state.
     * Useful when a file is deleted from the project.
     */
    public void removeFile(Path file) {
        // Remove nodes associated with this file
        List<Node> nodes = nodesByFile.get(file);
        if (nodes != null) {
            for (Node node : nodes) {
                graph.removeNode(node.getId());
            }
            nodesByFile.remove(file);
        }
        
        // Remove from tracking
        fileModificationTimes.remove(file);
        processedFiles.remove(file);
    }
    
    /**
     * Returns the graph being maintained.
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
     * Returns the total number of files parsed across all builds.
     */
    public int getTotalFilesParsed() {
        return totalFilesParsed;
    }
    
    /**
     * Returns the number of files updated in the last build.
     */
    public int getFilesUpdated() {
        return filesUpdated;
    }
    
    /**
     * Returns the number of files skipped in the last build (unchanged).
     */
    public int getFilesSkipped() {
        return filesSkipped;
    }
    
    /**
     * Returns the time taken for the last build in milliseconds.
     */
    public long getLastBuildTime() {
        return lastBuildTime;
    }
    
    /**
     * Returns the number of files currently being tracked.
     */
    public int getTrackedFileCount() {
        return processedFiles.size();
    }
    
    /**
     * Returns true if a file is currently being tracked.
     */
    public boolean isTrackingFile(Path file) {
        return processedFiles.contains(file);
    }
    
    /**
     * Parses a single Java file and adds its nodes/edges to the graph.
     */
    private void parseFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        // Create visitor context for this file
        VisitorContext ctx = new VisitorContext(
            file,
            null,  // package - will be set by PackageDeclarationVisitor
            null,  // current class
            null,  // current method
            symbolTable,
            0,
            new Scope(null)
        );
        
        // Track nodes created from this file
        List<Node> fileNodes = new ArrayList<>();
        
        // Temporarily capture nodes as they're added
        int nodeCountBefore = graph.getNodeCount();
        
        // Process declaration visitor
        new DeclarationVisitor(graph, ctx).visit(cu, ctx);
        
        // Process statement visitor
        new StatementVisitor(graph, ctx).visit(cu, ctx);
        
        // Process expression visitor
        new ExpressionVisitor(graph, ctx).visit(cu, ctx);
        
        // Capture all nodes added from this file
        List<Node> allNodes = graph.getAllNodes();
        fileNodes.addAll(allNodes.subList(nodeCountBefore, allNodes.size()));
        
        nodesByFile.put(file, fileNodes);
    }
    
    /**
     * Adds derived edges to the graph (edges that can be inferred from other edges).
     */
    private void addDerivedEdges() {
        // Clear existing derived edges first to avoid duplicates
        graph.removeEdgesByType(EdgeTypes.INHERITS);
        graph.removeEdgesByType(EdgeTypes.IMPLEMENTS);
        
        // Add INHERITS edges for classes with superclasses
        for (Node node : graph.getAllNodes()) {
            if (node instanceof ClassNode) {
                ClassNode cls = (ClassNode) node;
                if (cls.hasSuperclass()) {
                    String superclassFqn = cls.getSuperclass();
                    symbolTable.getClassByQualifiedName(superclassFqn)
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
                    symbolTable.getClassByQualifiedName(iface)
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
            }
        }
    }
}
