package dk.gausdalfind.graph;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.parser.*;
import dk.gausdalfind.symbols.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Builds a complete Java code graph from source files.
 * 
 * This is the main entry point for constructing the graph. It orchestrates:
 * 1. JavaParser configuration
 * 2. File collection
 * 3. AST parsing
 * 4. Node and edge creation via visitors
 * 5. Symbol resolution
 * 6. Derived edge creation
 * 
 * Usage:
 * <pre>
 * GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
 * Graph graph = builder.build();
 * </pre>
 */
public class GausVibeBuilder {
    
    private final Path projectRoot;
    private final Graph graph;
    private final SymbolTable symbolTable;
    
    // Configuration
    private boolean includeTestSources = true;
    private String excludePattern;
    private boolean parallel = true;
    
    // Statistics
    private int filesParsed = 0;
    private int filesFailed = 0;
    private long parseStartTime;
    private long parseEndTime;
    
    /**
     * Creates a new graph builder for the given project root.
     * 
     * @param projectRoot the root directory of the Java project
     */
    public GausVibeBuilder(Path projectRoot) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root cannot be null");
        }
        this.projectRoot = projectRoot;
        this.graph = new Graph();
        this.symbolTable = new SymbolTable();
    }
    
    /**
     * Sets whether to include test source directories.
     */
    public void setIncludeTestSources(boolean includeTestSources) {
        this.includeTestSources = includeTestSources;
    }
    
    /**
     * Sets the glob pattern for files to exclude.
     */
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }
    
    /**
     * Sets whether to parse files in parallel.
     */
    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }
    
    /**
     * Returns whether test sources are included.
     */
    public boolean isIncludeTestSources() {
        return includeTestSources;
    }
    
    /**
     * Returns the exclude pattern.
     */
    public String getExcludePattern() {
        return excludePattern;
    }
    
    /**
     * Returns whether parallel parsing is enabled.
     */
    public boolean isParallel() {
        return parallel;
    }
    
    /**
     * Builds the complete graph for the project.
     * 
     * @return the constructed graph
     * @throws IOException if file collection or parsing fails
     */
    public Graph build() throws IOException {
        parseStartTime = System.currentTimeMillis();
        
        try {
            // Phase 1: Setup JavaParser
            JavaParserConfig.setup(projectRoot);
            
            // Phase 2: Collect Java files
            List<Path> javaFiles = collectJavaFiles();
            
            // Phase 3: Parse each file and build nodes/edges
            if (parallel) {
                javaFiles.parallelStream().forEach(this::parseFile);
            } else {
                for (Path file : javaFiles) {
                    parseFile(file);
                }
            }
            
            // Phase 4: Resolve symbols
            SymbolResolver resolver = new SymbolResolver(graph, symbolTable);
            resolver.resolve();
            
            // Phase 5: Add derived edges
            addDerivedEdges();
            
            // Phase 6: Validate the graph
            validateGraph();
            
        } finally {
            parseEndTime = System.currentTimeMillis();
        }
        
        return graph;
    }
    
    // ==================== File Collection ====================
    
    /**
     * Collects all Java files from the project.
     */
    private List<Path> collectJavaFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        
        if (includeTestSources) {
            // Try standard Maven directories first (includes test)
            List<Path> standardFiles = JavaFileCollector.collectFromStandardDirectories(projectRoot);
            if (!standardFiles.isEmpty()) {
                files.addAll(filterByExcludePattern(standardFiles));
            } else {
                // Fall back to collecting from project root
                files.addAll(filterByExcludePattern(JavaFileCollector.collect(projectRoot)));
            }
        } else {
            // Only collect from main source directory
            Path mainSrc = projectRoot.resolve("src/main/java");
            if (Files.isDirectory(mainSrc)) {
                files.addAll(filterByExcludePattern(JavaFileCollector.collect(mainSrc)));
            } else {
                // Fall back to collecting from project root, filter out test directories
                files.addAll(filterByExcludePattern(JavaFileCollector.collect(projectRoot)));
                files.removeIf(path -> path.toString().contains("/test/") || path.toString().contains("\\test\\"));
            }
        }
        
        return files;
    }
    
    /**
     * Filters files based on the exclude pattern.
     */
    private List<Path> filterByExcludePattern(List<Path> files) {
        if (excludePattern == null || excludePattern.isBlank()) {
            return files;
        }
        
        List<Path> filtered = new ArrayList<>();
        for (Path file : files) {
            if (!matchesExcludePattern(file)) {
                filtered.add(file);
            }
        }
        return filtered;
    }
    
    /**
     * Checks if a file path matches the exclude pattern.
     */
    private boolean matchesExcludePattern(Path file) {
        if (excludePattern == null || excludePattern.isBlank()) {
            return false;
        }
        
        String pathStr = file.toString();
        // Simple glob pattern matching (supports ** for recursive)
        if (excludePattern.contains("**")) {
            // Recursive wildcard
            String pattern = excludePattern.replace("**", "");
            return pathStr.contains(pattern);
        } else if (excludePattern.contains("*")) {
            // Single wildcard
            String pattern = excludePattern.replace("*", "");
            return pathStr.contains(pattern);
        } else {
            // Exact match or substring
            return pathStr.contains(excludePattern);
        }
    }
    
    // ==================== File Parsing ====================
    
    /**
     * Parses a single Java file and adds its nodes/edges to the graph.
     * 
     * @param file the Java file to parse
     */
    private void parseFile(Path file) {
        try {
            // Parse the file
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Create visitor context for this file
            VisitorContext context = new VisitorContext(file, graph);
            
            // Process package and imports
            cu.getPackageDeclaration().ifPresent(pkg -> {
                PackageNode pkgNode = new PackageNode(
                    pkg.getName().toString(),
                    file,
                    Position.fromJavaParser(pkg.getBegin().orElse(null)),
                    Position.fromJavaParser(pkg.getEnd().orElse(null))
                );
                graph.addNode(pkgNode);
                context.setCurrentPackage(pkg.getName().toString());
                
                // Register in symbol table
                symbolTable.register(pkgNode);
                symbolTable.registerPackage(file, pkg.getName().toString());
            });
            
            // Process imports
            for (var importDecl : cu.getImports()) {
                context.addImport(importDecl.getName().toString());
            }
            symbolTable.registerImports(file, context.getImports());
            
            // Process types (classes, interfaces, enums)
            for (var typeDecl : cu.getTypes()) {
                processTypeDeclaration(typeDecl, context);
            }
            
            filesParsed++;
            
        } catch (Exception e) {
            System.err.println("Error parsing file " + file + ": " + e.getMessage());
            filesFailed++;
        }
    }
    
    /**
     * Processes a type declaration (class, interface, enum).
     */
    private void processTypeDeclaration(com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl, VisitorContext context) {
        if (typeDecl instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) {
            processClassOrInterface((com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) typeDecl, context);
        } else if (typeDecl instanceof com.github.javaparser.ast.body.EnumDeclaration) {
            processEnum((com.github.javaparser.ast.body.EnumDeclaration) typeDecl, context);
        } else if (typeDecl instanceof com.github.javaparser.ast.body.AnnotationDeclaration) {
            processAnnotation((com.github.javaparser.ast.body.AnnotationDeclaration) typeDecl, context);
        }
    }
    
    /**
     * Processes a class or interface declaration.
     */
    private void processClassOrInterface(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl, VisitorContext context) {
        // Use NodeFactory to create the class node
        NodeFactory nodeFactory = new NodeFactory(graph, context);
        ClassNode classNode = nodeFactory.createClass(classDecl);
        
        if (classNode == null) {
            return;
        }
        
        // Register in symbol table
        symbolTable.register(classNode);
        
        // Push class context
        String oldClass = context.getCurrentClass();
        context.setCurrentClass(classNode.getQualifiedName());
        
        // Process class members
        EdgeFactory edgeFactory = new EdgeFactory(graph, context);
        
        for (var member : classDecl.getMembers()) {
            processClassMember(member, context, nodeFactory, edgeFactory, classNode);
        }
        
        // Restore class context
        context.setCurrentClass(oldClass);
    }
    
    /**
     * Processes a class member (field, method, nested class).
     */
    private void processClassMember(
            com.github.javaparser.ast.body.BodyDeclaration<?> member,
            VisitorContext context,
            NodeFactory nodeFactory,
            EdgeFactory edgeFactory,
            ClassNode classNode) {
        
        if (member instanceof com.github.javaparser.ast.body.MethodDeclaration) {
            MethodNode methodNode = nodeFactory.createMethod(
                (com.github.javaparser.ast.body.MethodDeclaration) member
            );
            if (methodNode != null) {
                symbolTable.register(methodNode);
                edgeFactory.createHasMethod(classNode.getId(), methodNode.getId());
                
                // Process method body
                processMethodBody(
                    (com.github.javaparser.ast.body.MethodDeclaration) member,
                    context, nodeFactory, edgeFactory, methodNode
                );
            }
        } else if (member instanceof com.github.javaparser.ast.body.FieldDeclaration) {
            com.github.javaparser.ast.body.FieldDeclaration fieldDecl = (com.github.javaparser.ast.body.FieldDeclaration) member;
            // FieldDeclaration can have multiple variables
            for (var var : fieldDecl.getVariables()) {
                FieldNode fieldNode = nodeFactory.createField(fieldDecl);
                if (fieldNode != null) {
                    symbolTable.register(fieldNode);
                    edgeFactory.createHasField(classNode.getId(), fieldNode.getId());
                }
            }
        } else if (member instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) {
            processClassOrInterface(
                (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) member,
                context
            );
        } else if (member instanceof com.github.javaparser.ast.body.EnumDeclaration) {
            processEnum(
                (com.github.javaparser.ast.body.EnumDeclaration) member,
                context
            );
        }
    }
    
    /**
     * Processes a method body.
     */
    private void processMethodBody(
            com.github.javaparser.ast.body.MethodDeclaration methodDecl,
            VisitorContext context,
            NodeFactory nodeFactory,
            EdgeFactory edgeFactory,
            MethodNode methodNode) {
        
        methodDecl.getBody().ifPresent(body -> {
            // Push method context
            String oldMethod = context.getCurrentMethod();
            context.setCurrentMethod(methodNode.getQualifiedName() + "#" + methodNode.getSignature());
            
            // Process parameters
            int paramIndex = 0;
            for (var param : methodDecl.getParameters()) {
                ParameterNode paramNode = nodeFactory.createParameter(param);
                if (paramNode != null) {
                    symbolTable.register(paramNode);
                    edgeFactory.createHasParameter(
                        methodNode.getId(), paramNode.getId(), paramIndex
                    );
                    paramIndex++;
                }
            }
            
            // Process statements using StatementVisitor
            StatementVisitor stmtVisitor = new StatementVisitor(graph, context);
            body.accept(stmtVisitor, context);
            
            // Restore method context
            context.setCurrentMethod(oldMethod);
        });
    }
    
    /**
     * Processes an enum declaration.
     */
    private void processEnum(com.github.javaparser.ast.body.EnumDeclaration enumDecl, VisitorContext context) {
        // For now, treat enums similarly to classes
        NodeFactory nodeFactory = new NodeFactory(graph, context);
        
        // Create a class node for the enum
        String name = enumDecl.getName().toString();
        String qualifiedName = context.getCurrentPackage() != null ?
            context.getCurrentPackage() + "." + name : name;
        
        Set<String> modifiers = new HashSet<>();
        enumDecl.getModifiers().forEach(m -> modifiers.add(m.toString()));
        
        ClassNode enumNode = new ClassNode(
            NodeIdGenerator.forDeclaration(NodeIdGenerator.NodeType.CLASS, qualifiedName),
            name, qualifiedName, modifiers, "java.lang.Enum", List.of(),
            false, true, context.getCurrentFile(),
            Position.fromJavaParser(enumDecl.getBegin().orElse(null)),
            Position.fromJavaParser(enumDecl.getEnd().orElse(null))
        );
        
        graph.addNode(enumNode);
        symbolTable.register(enumNode);
        
        // Process enum constants
        for (var constant : enumDecl.getEntries()) {
            // Each enum constant is like a static field
            String constName = constant.getName().toString();
            String constFqn = qualifiedName + "." + constName;
            
            FieldNode constNode = new FieldNode(
                NodeIdGenerator.forDeclaration(NodeIdGenerator.NodeType.FIELD, constFqn),
                constName, constFqn, qualifiedName, Set.of("public", "static", "final"),
                true, true, context.getCurrentFile(),
                Position.fromJavaParser(constant.getBegin().orElse(null)),
                Position.fromJavaParser(constant.getEnd().orElse(null))
            );
            
            graph.addNode(constNode);
            symbolTable.register(constNode);
            
            EdgeFactory edgeFactory = new EdgeFactory(graph, context);
            edgeFactory.createHasField(enumNode.getId(), constNode.getId());
            
            // Process enum constant arguments
            constant.getArguments().forEach(arg -> {
                // Would process arguments if we had expression nodes
            });
            
            // Process enum constant body (methods)
            constant.getClassBody().forEach(member -> {
                processClassMember(member, context, nodeFactory, edgeFactory, enumNode);
            });
        }
    }
    
    /**
     * Processes an annotation declaration.
     */
    private void processAnnotation(
            com.github.javaparser.ast.body.AnnotationDeclaration annotationDecl,
            VisitorContext context) {
        // For now, skip annotation processing
        // Would need to create AnnotationNode class
    }
    
    // ==================== Derived Edges ====================
    
    /**
     * Adds derived edges that can be inferred from the graph structure.
     */
    private void addDerivedEdges() {
        // Add edges that can be derived from existing nodes/edges
        
        // 1. Add RETURN_VALUE edges from return statements to return type
        // (Would need ReturnStmtNode)
        
        // 2. Add THROW_VALUE edges from throw statements to exception type
        // (Would need ThrowStmtNode)
        
        // 3. Add CALLS_CONSTRUCTOR edges from new expressions
        // (Would need NewClassNode)
        
        // For now, we'll add some basic derived edges based on what we have
        
        // Add ANNOTATED_WITH edges (placeholder)
        for (Node node : graph.getAllNodes()) {
            if (node instanceof ClassNode) {
                ClassNode cls = (ClassNode) node;
                // Check for annotations on class modifiers
                // Would need annotation support
            }
        }
    }
    
    // ==================== Graph Validation ====================
    
    /**
     * Validates the constructed graph.
     */
    private void validateGraph() {
        // Check that all nodes have valid IDs
        for (Node node : graph.getAllNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                System.err.println("Warning: Node with null/blank ID: " + node);
            }
        }
        
        // Check that all edge nodes exist
        for (Edge edge : graph.getAllEdges()) {
            if (!graph.getNode(edge.getFromId()).isPresent()) {
                System.err.println("Warning: Edge with missing from node: " + edge.getFromId());
            }
            if (!graph.getNode(edge.getToId()).isPresent()) {
                System.err.println("Warning: Edge with missing to node: " + edge.getToId());
            }
        }
        
        // Validate using Graph's built-in validator
        if (!graph.validate()) {
            System.err.println("Warning: Graph validation failed");
        }
    }
    
    // ==================== Getters ====================
    
    /**
     * Returns the project root.
     */
    public Path getProjectRoot() {
        return projectRoot;
    }
    
    /**
     * Returns the symbol table.
     */
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
    
    /**
     * Returns the number of files parsed successfully.
     */
    public int getFilesParsed() {
        return filesParsed;
    }
    
    /**
     * Returns the number of files that failed to parse.
     */
    public int getFilesFailed() {
        return filesFailed;
    }
    
    /**
     * Returns the parse time in milliseconds.
     */
    public long getParseTime() {
        return parseEndTime - parseStartTime;
    }
    
    /**
     * Returns a summary of the build results.
     */
    public String getSummary() {
        return String.format(
            "Build Summary: Parsed=%d files, Failed=%d files, Time=%d ms, " +
            "Nodes=%d, Edges=%d",
            filesParsed, filesFailed, getParseTime(),
            graph.getNodeCount(), graph.getEdgeCount()
        );
    }
    
    // ==================== Builder Methods ====================
    
    /**
     * Builds the graph and returns both the graph and symbol table.
     */
    public BuildResult buildWithResult() throws IOException {
        build();
        return new BuildResult(graph, symbolTable, filesParsed, filesFailed, getParseTime());
    }
    
    /**
     * Result of a graph build operation.
     */
    public static class BuildResult {
        private final Graph graph;
        private final SymbolTable symbolTable;
        private final int filesParsed;
        private final int filesFailed;
        private final long parseTime;
        
        public BuildResult(Graph graph, SymbolTable symbolTable, 
                         int filesParsed, int filesFailed, long parseTime) {
            this.graph = graph;
            this.symbolTable = symbolTable;
            this.filesParsed = filesParsed;
            this.filesFailed = filesFailed;
            this.parseTime = parseTime;
        }
        
        public Graph getGraph() {
            return graph;
        }
        
        public SymbolTable getSymbolTable() {
            return symbolTable;
        }
        
        public int getFilesParsed() {
            return filesParsed;
        }
        
        public int getFilesFailed() {
            return filesFailed;
        }
        
        public long getParseTime() {
            return parseTime;
        }
        
        public String getSummary() {
            return String.format(
                "Build Result: Parsed=%d, Failed=%d, Time=%d ms, Nodes=%d, Edges=%d",
                filesParsed, filesFailed, parseTime,
                graph.getNodeCount(), graph.getEdgeCount()
            );
        }
    }
}
