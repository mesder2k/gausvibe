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
    
    // Call sites recorded during parsing, resolved after all files are parsed
    private final List<VisitorContext.CallRecord> pendingCalls =
        Collections.synchronizedList(new ArrayList<>());
    
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
            
            // Phase 4.5: Resolve recorded call sites into CALLS edges
            resolveCallSites();

            // Phase 4.6: Expand CALLS edges through interface/abstract
            // dispatch so callpath and callers cross dynamic dispatch
            expandDispatchEdges();
            
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
    
    /**
     * Result of a single-file incremental update.
     */
    public record UpdateResult(int removedNodes, int addedNodes, int removedEdges, int addedEdges) {}
    
    /**
     * Incrementally updates one file: removes the file's old nodes (and all
     * edges touching them), reparses the file, and re-resolves call sites.
     *
     * Callers in other files whose CALLS edges pointed into this file are
     * re-linked automatically: their recorded call sites are re-resolved
     * against the new method nodes (method IDs include signatures, so a
     * signature change relinks only if the name still resolves).
     *
     * @param file the source file to update (absolute or relative)
     * @return counts of removed/added nodes and edges
     * @throws IOException if the file cannot be read or parsed
     */
    public UpdateResult updateFile(Path file) throws IOException {
        List<Node> oldNodes = new ArrayList<>(findFileNodes(file));
        int removedEdges = 0;
        for (Node n : oldNodes) {
            removedEdges += graph.getIndexes().getEdgesFrom(n.getId()).size()
                + graph.getIndexes().getEdgesTo(n.getId()).size();
        }
        for (Node n : oldNodes) {
            graph.removeNode(n.getId());
        }
        
        int nodesBefore = graph.getNodeCount();
        int edgesBefore = graph.getEdgeCount();
        
        parseFile(file);
        resolveCallSites();
        expandDispatchEdges();
        
        int nodesAfter = graph.getNodeCount();
        int edgesAfter = graph.getEdgeCount();
        
        return new UpdateResult(
            oldNodes.size(),
            nodesAfter - (nodesBefore - oldNodes.size()),
            removedEdges,
            edgesAfter - (edgesBefore - removedEdges));
    }
    
    /**
     * Finds the graph nodes owned by a file, tolerating absolute/relative
     * path differences between how the graph was built and how the caller
     * refers to the file.
     */
    private List<Node> findFileNodes(Path file) {
        List<Node> exact = graph.getIndexes().getNodesByFile(file);
        if (!exact.isEmpty()) {
            return exact;
        }
        // Fallback: suffix / normalized-absolute matching
        String fileStr = file.toString();
        String fileAbs = file.toAbsolutePath().normalize().toString();
        List<Node> matches = new ArrayList<>();
        for (Node n : graph.getAllNodes()) {
            Path f = n.getFile();
            if (f == null) continue;
            String fs = f.toString();
            if (fs.equals(fileStr) || fs.endsWith("/" + fileStr)
                || f.toAbsolutePath().normalize().toString().equals(fileAbs)) {
                matches.add(n);
            }
        }
        return matches;
    }
    
    // ==================== File Parsing ====================
    
    /**
     * Parses a single Java file and adds its nodes/edges to the graph.
     * 
     * @param file the Java file to parse
     */
    private void parseFile(Path file) {
        try {
            // Parse the file with an explicitly configured parser (the shared
            // static configuration is not reliably visible to parallel
            // worker threads)
            com.github.javaparser.ParseResult<CompilationUnit> parseResult =
                JavaParserConfig.newParser().parse(file);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                throw new IOException("parse errors: "
                    + parseResult.getProblems().stream()
                        .map(p -> String.valueOf(p.getMessage()))
                        .limit(3)
                        .collect(java.util.stream.Collectors.joining("; ")));
            }
            CompilationUnit cu = parseResult.getResult().get();
            
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
            
            // Harvest call sites recorded by the expression visitor
            pendingCalls.addAll(context.getRecordedCalls());
            
            filesParsed++;
            
        } catch (Exception e) {
            // Log the exception type and cause, not just getMessage():
            // NPEs have a null message and silently hid real failures before
            String cause = e.getCause() != null ? " (cause: " + e.getCause() + ")" : "";
            System.err.println("Error parsing file " + file + ": " + e.getClass().getSimpleName()
                + ": " + e.getMessage() + cause);
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
        } else if (typeDecl instanceof com.github.javaparser.ast.body.RecordDeclaration) {
            processRecord((com.github.javaparser.ast.body.RecordDeclaration) typeDecl, context);
        } else if (typeDecl instanceof com.github.javaparser.ast.body.AnnotationDeclaration) {
            processAnnotation((com.github.javaparser.ast.body.AnnotationDeclaration) typeDecl, context);
        }
    }
    
    /**
     * Processes a class or interface declaration.
     */
    private void processClassOrInterface(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl, VisitorContext context) {
        // Capture the outer class BEFORE createClass (which must stay
        // side-effect free) so nested types restore the right context
        String outerClass = context.getCurrentClass();
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
        } else if (member instanceof com.github.javaparser.ast.body.ConstructorDeclaration) {
            MethodNode ctorNode = nodeFactory.createConstructor(
                (com.github.javaparser.ast.body.ConstructorDeclaration) member
            );
            if (ctorNode != null) {
                symbolTable.register(ctorNode);
                edgeFactory.createHasMethod(classNode.getId(), ctorNode.getId());
                
                // Process constructor body (records call sites with the
                // constructor as caller, and parameters)
                processConstructorBody(
                    (com.github.javaparser.ast.body.ConstructorDeclaration) member,
                    context, nodeFactory, edgeFactory, ctorNode
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
                    indexFieldInitializerValues(var, classNode, context);
                }
            }
        } else if (member instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) {
            processClassOrInterface(
                (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) member,
                context
            );
        } else if (member instanceof com.github.javaparser.ast.body.RecordDeclaration) {
            processRecord(
                (com.github.javaparser.ast.body.RecordDeclaration) member,
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
            String oldMethodId = context.getCurrentMethodId();
            context.setCurrentMethod(methodNode.getQualifiedName() + "#" + methodNode.getSignature());
            context.setCurrentMethodId(methodNode.getId());
            
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
            context.setCurrentMethodId(oldMethodId);
        });
    }
    
    /**
     * Captures literal values from a field initializer into the value
     * index, so questions like "where is 9200 defined" can be answered
     * from the graph. Walks the whole initializer expression, including
     * constructor-call arguments inside it (the Settings/Setting idiom).
     */
    private void indexFieldInitializerValues(
            com.github.javaparser.ast.body.VariableDeclarator var,
            ClassNode owner, VisitorContext context) {
        var.getInitializer().ifPresent(init -> {
            for (com.github.javaparser.ast.expr.LiteralExpr lit
                    : init.findAll(com.github.javaparser.ast.expr.LiteralExpr.class)) {
                if (lit instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr) {
                    continue;
                }
                String value = lit.toString();
                if (lit instanceof com.github.javaparser.ast.expr.StringLiteralExpr) {
                    value = ((com.github.javaparser.ast.expr.StringLiteralExpr) lit).getValue();
                }
                if (value == null || value.isBlank() || value.length() > 200) {
                    continue;
                }
                int line = lit.getBegin().map(p -> p.line).orElse(0);
                graph.getIndexes().indexValue(new Indexes.ValueOccurrence(
                    value, owner.getQualifiedName(), var.getName().toString(),
                    context.getCurrentFile(), line));
            }
        });
    }

    /**
     * Processes a constructor body, mirroring processMethodBody.
     */
    private void processConstructorBody(
            com.github.javaparser.ast.body.ConstructorDeclaration ctorDecl,
            VisitorContext context,
            NodeFactory nodeFactory,
            EdgeFactory edgeFactory,
            MethodNode ctorNode) {
        
        // Push method context
        String oldMethod = context.getCurrentMethod();
        String oldMethodId = context.getCurrentMethodId();
        context.setCurrentMethod(ctorNode.getQualifiedName() + "#" + ctorNode.getSignature());
        context.setCurrentMethodId(ctorNode.getId());
        
        // Process parameters
        int paramIndex = 0;
        for (var param : ctorDecl.getParameters()) {
            ParameterNode paramNode = nodeFactory.createParameter(param);
            if (paramNode != null) {
                symbolTable.register(paramNode);
                edgeFactory.createHasParameter(
                    ctorNode.getId(), paramNode.getId(), paramIndex
                );
                paramIndex++;
            }
        }
        
        // Process statements using StatementVisitor
        StatementVisitor stmtVisitor = new StatementVisitor(graph, context);
        ctorDecl.getBody().accept(stmtVisitor, context);
        
        // Restore method context
        context.setCurrentMethod(oldMethod);
        context.setCurrentMethodId(oldMethodId);
    }
    
    /**
     * Processes a record declaration. Records are modeled as class nodes;
     * their components are not parameter nodes but methods/fields are
     * processed as regular members.
     */
    private void processRecord(com.github.javaparser.ast.body.RecordDeclaration recordDecl, VisitorContext context) {
        String name = recordDecl.getName().toString();
        String qualifiedName = context.getCurrentPackage() != null && !context.getCurrentPackage().isBlank()
            ? context.getCurrentPackage() + "." + name : name;
        // Nested records use the enclosing class prefix
        if (context.getCurrentClass() != null && !context.getCurrentClass().isBlank()) {
            qualifiedName = context.getCurrentClass() + "$" + name;
        }
        
        Set<String> modifiers = new HashSet<>();
        recordDecl.getModifiers().forEach(m -> modifiers.add(m.toString().trim()));
        
        ClassNode recordNode = new ClassNode(
            NodeIdGenerator.forDeclaration(NodeIdGenerator.NodeType.CLASS, qualifiedName),
            name, qualifiedName, modifiers, "java.lang.Record", List.of(),
            false, false, context.getCurrentFile(),
            Position.fromJavaParser(recordDecl.getBegin().orElse(null)),
            Position.fromJavaParser(recordDecl.getEnd().orElse(null))
        );
        
        if (!graph.addNode(recordNode)) {
            return;
        }
        symbolTable.register(recordNode);
        
        // Process members (methods, fields, nested types)
        NodeFactory nodeFactory = new NodeFactory(graph, context);
        EdgeFactory edgeFactory = new EdgeFactory(graph, context);
        
        String oldClass = context.getCurrentClass();
        context.setCurrentClass(qualifiedName);
        for (var member : recordDecl.getMembers()) {
            processClassMember(member, context, nodeFactory, edgeFactory, recordNode);
        }
        context.setCurrentClass(oldClass);
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
        enumDecl.getModifiers().forEach(m -> modifiers.add(m.toString().trim()));
        
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
     * Resolves the call sites recorded during parsing into CALLS edges.
     * Runs after all files are parsed, so the full class graph is available.
     */
    private void resolveCallSites() {
        Indexes indexes = graph.getIndexes();
        Set<String> createdEdges = new HashSet<>();
        int resolved = 0;
        
        List<VisitorContext.CallRecord> records;
        synchronized (pendingCalls) {
            records = new ArrayList<>(pendingCalls);
        }
        
        // Precompute class FQN -> method name -> methods, and method name ->
        // methods, so per-record resolution is a map lookup instead of
        // scanning every same-named method in the project.
        Map<String, Map<String, List<MethodNode>>> methodsByClass = new HashMap<>();
        Map<String, List<MethodNode>> methodsByNameGlobal = new HashMap<>();
        for (MethodNode m : indexes.getAllMethods()) {
            String cls = m.getClassName();
            if (cls != null && !cls.isBlank()) {
                methodsByClass.computeIfAbsent(cls, k -> new HashMap<>())
                    .computeIfAbsent(m.getName(), k -> new ArrayList<>())
                    .add(m);
            }
            methodsByNameGlobal.computeIfAbsent(m.getName(), k -> new ArrayList<>())
                .add(m);
        }
        
        for (VisitorContext.CallRecord rec : records) {
            for (MethodNode callee : resolveCallee(rec, indexes, methodsByClass, methodsByNameGlobal)) {
                String key = rec.callerMethodId + "|" + callee.getId();
                if (!createdEdges.add(key)) {
                    continue;
                }
                // Skip records whose endpoints are missing from the graph
                // (e.g. methods in files that failed to parse)
                if (graph.getNode(rec.callerMethodId).isEmpty()
                    || graph.getNode(callee.getId()).isEmpty()) {
                    continue;
                }
                graph.addEdge(new Edge(
                    rec.callerMethodId, callee.getId(), EdgeTypes.CALLS, Map.of()));
                resolved++;
            }
        }
        
        if (!records.isEmpty()) {
            System.out.println("Resolved " + resolved + "/" + records.size()
                + " call sites into CALLS edges");
        }
    }
    
    /**
     * Expands CALLS edges through dynamic dispatch: a call site resolved
     * to an interface or abstract method also produces CALLS edges to the
     * concrete overriding methods in descendant classes, so callpath and
     * callers queries can cross e.g. RestHandler.handleRequest ->
     * an implementation. Only abstract callees are expanded (concrete
     * calls keep their single edge), and expansion per call site is
     * capped to keep huge hierarchies from exploding the graph.
     */
    private void expandDispatchEdges() {
        Indexes indexes = graph.getIndexes();
        final int cap = 50;

        Map<String, Map<String, List<MethodNode>>> methodsByClass = new HashMap<>();
        for (MethodNode m : indexes.getAllMethods()) {
            String cls = m.getClassName();
            if (cls != null && !cls.isBlank()) {
                methodsByClass.computeIfAbsent(cls, k -> new HashMap<>())
                    .computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
            }
        }

        // direct descendants: superclass -> subclasses, interface -> implementors
        Map<String, List<ClassNode>> childrenByType = new HashMap<>();
        for (ClassNode c : indexes.getAllClasses()) {
            if (c.hasSuperclass() && c.getSuperclass() != null) {
                childrenByType.computeIfAbsent(c.getSuperclass(), k -> new ArrayList<>()).add(c);
            }
            for (String iface : c.getInterfaces()) {
                childrenByType.computeIfAbsent(iface, k -> new ArrayList<>()).add(c);
            }
        }

        // dedup against existing CALLS edges so updateFile re-runs stay idempotent
        Set<String> existing = new HashSet<>();
        for (Edge e : indexes.getEdgesByType(EdgeTypes.CALLS)) {
            existing.add(e.getFromId() + "|" + e.getToId());
        }

        // methods of interfaces and abstract classes are dispatch targets even
        // when not flagged abstract (interface methods are implicitly abstract)
        Set<String> dispatchOwnerTypes = new HashSet<>();
        for (ClassNode c : indexes.getAllClasses()) {
            if (c.isInterface() || c.isAbstract()) {
                dispatchOwnerTypes.add(c.getQualifiedName());
            }
        }

        int expanded = 0;
        for (Edge edge : new ArrayList<>(indexes.getEdgesByType(EdgeTypes.CALLS))) {
            Node to = graph.getNode(edge.getToId()).orElse(null);
            if (!(to instanceof MethodNode)) {
                continue;
            }
            MethodNode callee = (MethodNode) to;
            if (callee.getClassName() == null) {
                continue;
            }
            boolean dispatchTarget = callee.isAbstract()
                || dispatchOwnerTypes.contains(callee.getClassName());
            if (!dispatchTarget) {
                continue;
            }

            // transitive descendants of the callee's owner type
            Deque<String> frontier = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            frontier.add(callee.getClassName());
            int perSite = 0;
            while (!frontier.isEmpty() && perSite < cap) {
                String typeFqn = frontier.poll();
                if (!visited.add(typeFqn)) {
                    continue;
                }
                for (ClassNode child : childrenByType.getOrDefault(typeFqn, List.of())) {
                    frontier.add(child.getQualifiedName());
                    if (child.getQualifiedName().equals(callee.getClassName())) {
                        continue;
                    }
                    List<MethodNode> overrides = methodsByClass
                        .getOrDefault(child.getQualifiedName(), Map.of())
                        .get(callee.getName());
                    if (overrides == null) {
                        continue;
                    }
                    for (MethodNode impl : overrides) {
                        if (impl.isAbstract() || !impl.getSignature().equals(callee.getSignature())) {
                            continue;
                        }
                        String key = edge.getFromId() + "|" + impl.getId();
                        if (existing.contains(key)) {
                            continue;
                        }
                        existing.add(key);
                        graph.addEdge(new Edge(
                            edge.getFromId(), impl.getId(), EdgeTypes.CALLS, Map.of()));
                        expanded++;
                        perSite++;
                        if (perSite >= cap) {
                            break;
                        }
                    }
                }
            }
        }

        if (expanded > 0) {
            System.out.println("Expanded " + expanded
                + " dispatch CALLS edges (interface/abstract -> concrete)");
        }
    }

    /**
     * Resolves one recorded call site to callee method nodes.
     */
    private List<MethodNode> resolveCallee(VisitorContext.CallRecord rec, Indexes indexes,
                                           Map<String, Map<String, List<MethodNode>>> methodsByClass,
                                           Map<String, List<MethodNode>> methodsByNameGlobal) {
        List<MethodNode> callees = new ArrayList<>();
        
        List<String> candidateClassFqns = new ArrayList<>();
        if (rec.receiverType != null && !rec.receiverType.isBlank()) {
            String typeName = rec.receiverType.replaceAll("<[^<>]*>", "")
                                              .replaceAll("\\[\\]", "").trim();
            if (typeName.contains(".")) {
                indexes.getClassByQualifiedName(typeName)
                    .ifPresent(c -> candidateClassFqns.add(c.getQualifiedName()));
            } else {
                List<ClassNode> byName = indexes.getClassesByName(typeName);
                if (byName.size() > 1 && rec.callerClassFqn != null) {
                    // prefer same-package matches
                    String pkg = rec.callerClassFqn.contains(".")
                        ? rec.callerClassFqn.substring(0, rec.callerClassFqn.lastIndexOf('.'))
                        : "";
                    List<ClassNode> samePkg = new ArrayList<>();
                    for (ClassNode c : byName) {
                        if (pkg.equals(c.getPackageName())) {
                            samePkg.add(c);
                        }
                    }
                    if (!samePkg.isEmpty()) {
                        byName = samePkg;
                    }
                }
                for (ClassNode c : byName) {
                    candidateClassFqns.add(c.getQualifiedName());
                }
            }
        } else if (rec.callerClassFqn != null) {
            // unqualified call: own class first
            indexes.getClassByQualifiedName(rec.callerClassFqn)
                .ifPresent(c -> candidateClassFqns.add(c.getQualifiedName()));
        }
        
        for (String classFqn : candidateClassFqns) {
            Map<String, List<MethodNode>> byName = methodsByClass.get(classFqn);
            if (byName != null) {
                List<MethodNode> methods = byName.get(rec.methodName);
                if (methods != null) {
                    callees.addAll(methods);
                }
            }
        }
        
        // Fallback: if nothing matched but exactly one method with this
        // name exists project-wide, resolve to it (handles unresolved receivers).
        if (callees.isEmpty()) {
            List<MethodNode> byName = methodsByNameGlobal.get(rec.methodName);
            if (byName != null && byName.size() == 1) {
                callees.add(byName.get(0));
            }
        }
        
        return callees;
    }
    
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
