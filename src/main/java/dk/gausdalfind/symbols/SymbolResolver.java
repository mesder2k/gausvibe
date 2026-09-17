package dk.gausdalfind.symbols;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Resolves symbols in the Java code graph by analyzing relationships
 * between nodes and establishing semantic edges.
 * 
 * This resolver processes the graph after parsing to:
 * 1. Resolve type references (class, interface, primitive)
 * 2. Resolve method calls to their target methods
 * 3. Resolve field accesses to their target fields
 * 4. Resolve variable references to their declarations
 * 5. Establish inheritance relationships (INHERITS, IMPLEMENTS, EXTENDS)
 * 6. Establish override relationships (OVERRIDES)
 * 7. Create semantic edges (CALLS, ACCESSES, REFERENCES, etc.)
 * 
 * The resolution is performed in multiple passes:
 * - Pass 1: Register all declarations in the symbol table
 * - Pass 2: Resolve type references
 * - Pass 3: Resolve method/field/variable references
 * - Pass 4: Establish inheritance and override relationships
 * - Pass 5: Create semantic edges
 */
public class SymbolResolver {
    
    private final Graph graph;
    private final SymbolTable symbolTable;
    
    // Statistics
    private int resolvedClasses = 0;
    private int resolvedMethods = 0;
    private int resolvedFields = 0;
    private int resolvedVariables = 0;
    private int unresolvedReferences = 0;
    
    /**
     * Creates a new symbol resolver.
     * 
     * @param graph the graph to resolve
     * @param symbolTable the symbol table to use
     */
    public SymbolResolver(Graph graph, SymbolTable symbolTable) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (symbolTable == null) {
            throw new IllegalArgumentException("Symbol table cannot be null");
        }
        this.graph = graph;
        this.symbolTable = symbolTable;
    }
    
    /**
     * Performs full symbol resolution on the graph.
     * 
     * This is the main entry point that performs all resolution passes.
     */
    public void resolve() {
        resolveDeclarations();
        resolveTypes();
        resolveReferences();
        establishInheritance();
        establishOverrides();
        createSemanticEdges();
    }
    
    // ==================== Pass 1: Register Declarations ====================
    
    /**
     * Registers all declaration nodes in the symbol table.
     */
    public void resolveDeclarations() {
        for (Node node : graph.getAllNodes()) {
            if (node instanceof PackageNode) {
                symbolTable.register((PackageNode) node);
            } else if (node instanceof ClassNode) {
                symbolTable.register((ClassNode) node);
            } else if (node instanceof MethodNode) {
                symbolTable.register((MethodNode) node);
            } else if (node instanceof FieldNode) {
                symbolTable.register((FieldNode) node);
            } else if (node instanceof VariableNode) {
                symbolTable.register((VariableNode) node);
            }
        }
    }
    
    // ==================== Pass 2: Resolve Types ====================
    
    /**
     * Resolves type references in the graph.
     * 
     * This pass:
     * - Resolves class type references in FieldNode, ParameterNode, VariableNode
     * - Resolves return types in MethodNode
     * - Resolves exception types in MethodNode
     * - Creates REFERENCES_TYPE edges
     */
    public void resolveTypes() {
        // Track which types we've seen to avoid duplicates
        Set<String> seenTypes = new HashSet<>();
        
        for (Node node : graph.getAllNodes()) {
            Path file = node.getFile();
            
            if (node instanceof FieldNode) {
                FieldNode field = (FieldNode) node;
                String type = field.getDataType();
                resolveTypeForNode(node, type, file, seenTypes);
            } else if (node instanceof ParameterNode) {
                ParameterNode param = (ParameterNode) node;
                String type = param.getDataType();
                resolveTypeForNode(node, type, file, seenTypes);
            } else if (node instanceof VariableNode) {
                VariableNode var = (VariableNode) node;
                String type = var.getDataType();
                resolveTypeForNode(node, type, file, seenTypes);
            } else if (node instanceof MethodNode) {
                MethodNode method = (MethodNode) node;
                
                // Resolve return type
                String returnType = method.getReturnType();
                resolveTypeForNode(node, returnType, file, seenTypes);
                
                // Resolve exception types
                for (String exception : method.getThrownExceptions()) {
                    resolveTypeForNode(node, exception, file, seenTypes);
                }
            } else if (node instanceof ClassNode) {
                ClassNode cls = (ClassNode) node;
                
                // Resolve superclass
                if (cls.hasSuperclass()) {
                    String superclass = cls.getSuperclass();
                    resolveTypeForNode(node, superclass, file, seenTypes);
                }
                
                // Resolve interfaces
                for (String iface : cls.getInterfaces()) {
                    resolveTypeForNode(node, iface, file, seenTypes);
                }
            }
        }
    }
    
    /**
     * Resolves a type reference for a node and creates appropriate edges.
     */
    private void resolveTypeForNode(Node node, String typeName, Path file, Set<String> seenTypes) {
        if (typeName == null || typeName.isBlank() || seenTypes.contains(typeName)) {
            return;
        }
        seenTypes.add(typeName);
        
        // Skip primitive types
        if (symbolTable.isPrimitive(typeName)) {
            return;
        }
        
        // Skip array types (handle separately)
        if (typeName.endsWith("[]")) {
            String elementType = typeName.substring(0, typeName.length() - 2);
            resolveTypeForNode(node, elementType, file, seenTypes);
            return;
        }
        
        // Resolve the class
        Optional<ClassNode> classOpt = symbolTable.resolveClass(typeName, file);
        if (classOpt.isPresent()) {
            ClassNode classNode = classOpt.get();
            // Create REFERENCES_TYPE edge
            graph.addEdge(new Edge(node.getId(), classNode.getId(), EdgeTypes.REFERENCES_TYPE));
        } else {
            // Type not found in symbol table - might be an external dependency
            // We'll still create a reference, but it won't be resolvable
            unresolvedReferences++;
        }
    }
    
    // ==================== Pass 3: Resolve References ====================
    
    /**
     * Resolves reference expressions to their target nodes.
     * 
     * This pass would process:
     * - MethodCallExpr -> MethodNode (CALLS edges)
     * - FieldAccessExpr -> FieldNode (ACCESSES edges)
     * - NameExpr -> VariableNode/FieldNode (REFERENCES edges)
     * - ObjectCreationExpr -> ClassNode (CREATES edges)
     * 
     * Note: This requires AST nodes for expressions, which we haven't fully
     * implemented yet. For now, we'll create a placeholder that will be
     * populated when expression nodes are added.
     */
    public void resolveReferences() {
        // This will be fully implemented when we have expression nodes
        // For now, we'll iterate through edges and try to resolve any that
        // reference unresolved nodes
        
        List<Edge> edgesToAdd = new ArrayList<>();
        
        for (Edge edge : graph.getAllEdges()) {
            String fromId = edge.getFromId();
            String toId = edge.getToId();
            String type = edge.getType();
            
            // Skip if both nodes exist
            if (graph.getNode(fromId).isPresent() && graph.getNode(toId).isPresent()) {
                continue;
            }
            
            // Try to resolve the target node
            Node fromNode = graph.getNode(fromId).orElse(null);
            if (fromNode != null) {
                Path file = fromNode.getFile();
                
                // This is a placeholder - actual resolution would require
                // knowing what the edge represents and how to resolve it
            }
        }
        
        for (Edge edge : edgesToAdd) {
            graph.addEdge(edge);
        }
    }
    
    // ==================== Pass 4: Establish Inheritance ====================
    
    /**
     * Establishes inheritance relationships between classes.
     * 
     * Creates:
     * - INHERITS edges from subclass to superclass
     * - IMPLEMENTS edges from class to interfaces
     * - EXTENDS edges from interface to parent interfaces
     */
    public void establishInheritance() {
        for (Node node : graph.getAllNodes()) {
            if (node instanceof ClassNode) {
                ClassNode cls = (ClassNode) node;
                
                // Create INHERITS edge for superclass
                if (cls.hasSuperclass()) {
                    String superclassName = cls.getSuperclass();
                    Optional<ClassNode> superclass = symbolTable.resolveClass(superclassName, cls.getFile());
                    if (superclass.isPresent()) {
                        graph.addEdge(new Edge(cls.getId(), superclass.get().getId(), EdgeTypes.INHERITS));
                        resolvedClasses++;
                    }
                }
                
                // Create IMPLEMENTS edges for interfaces
                for (String ifaceName : cls.getInterfaces()) {
                    Optional<ClassNode> iface = symbolTable.resolveClass(ifaceName, cls.getFile());
                    if (iface.isPresent()) {
                        graph.addEdge(new Edge(cls.getId(), iface.get().getId(), EdgeTypes.IMPLEMENTS));
                        resolvedClasses++;
                    }
                }
            }
        }
    }
    
    // ==================== Pass 5: Establish Overrides ====================
    
    /**
     * Establishes override relationships between methods.
     * 
     * A method overrides another if:
     * 1. The methods have the same name and signature
     * 2. The class of the overriding method is a subclass of the class of the overridden method
     * 3. The overridden method is not final or private
     */
    public void establishOverrides() {
        // Group methods by signature
        Map<String, List<MethodNode>> methodsBySig = new HashMap<>();
        
        for (Node node : graph.getAllNodes()) {
            if (node instanceof MethodNode) {
                MethodNode method = (MethodNode) node;
                String sig = method.getName() + method.getSignature();
                methodsBySig.computeIfAbsent(sig, k -> new ArrayList<>()).add(method);
            }
        }
        
        // For each signature, check for overrides
        for (List<MethodNode> methods : methodsBySig.values()) {
            if (methods.size() < 2) {
                continue;
            }
            
            // Sort by class hierarchy (subclasses come after superclasses)
            List<MethodNode> sorted = new ArrayList<>(methods);
            
            for (int i = 0; i < sorted.size(); i++) {
                MethodNode method1 = sorted.get(i);
                for (int j = i + 1; j < sorted.size(); j++) {
                    MethodNode method2 = sorted.get(j);
                    
                    // Check if method2 overrides method1
                    if (isOverride(method2, method1)) {
                        graph.addEdge(new Edge(method2.getId(), method1.getId(), EdgeTypes.OVERRIDES));
                        symbolTable.registerOverride(method2, method1);
                        resolvedMethods++;
                    } else if (isOverride(method1, method2)) {
                        graph.addEdge(new Edge(method1.getId(), method2.getId(), EdgeTypes.OVERRIDES));
                        symbolTable.registerOverride(method1, method2);
                        resolvedMethods++;
                    }
                }
            }
        }
    }
    
    /**
     * Checks if method1 overrides method2.
     */
    private boolean isOverride(MethodNode method1, MethodNode method2) {
        // Must have same name and signature
        if (!method1.getName().equals(method2.getName())) {
            return false;
        }
        if (!method1.getSignature().equals(method2.getSignature())) {
            return false;
        }
        
        // method1's class must be a subclass of method2's class
        ClassNode class1 = getClassForMethod(method1);
        ClassNode class2 = getClassForMethod(method2);
        
        if (class1 == null || class2 == null) {
            return false;
        }
        
        if (class1.getQualifiedName().equals(class2.getQualifiedName())) {
            return false; // Same class
        }
        
        // Check if class1 is a subclass of class2
        return isSubclass(class1, class2);
    }
    
    /**
     * Gets the class for a method.
     */
    private ClassNode getClassForMethod(MethodNode method) {
        String className = method.getClassName();
        if (className == null || className.isBlank()) {
            return null;
        }
        return symbolTable.getAllClasses().stream()
            .filter(c -> c.getQualifiedName().equals(className))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Checks if class1 is a subclass of class2 (directly or indirectly).
     */
    private boolean isSubclass(ClassNode class1, ClassNode class2) {
        if (class1 == null || class2 == null) {
            return false;
        }
        
        if (class1.getQualifiedName().equals(class2.getQualifiedName())) {
            return false;
        }
        
        // Check direct superclass
        if (class1.hasSuperclass() && class1.getSuperclass().equals(class2.getQualifiedName())) {
            return true;
        }
        
        // Check interfaces
        for (String iface : class1.getInterfaces()) {
            if (iface.equals(class2.getQualifiedName())) {
                return true;
            }
        }
        
        // Recursively check superclass
        if (class1.hasSuperclass()) {
            Optional<ClassNode> superclass = symbolTable.resolveClass(class1.getSuperclass(), null);
            if (superclass.isPresent()) {
                return isSubclass(superclass.get(), class2);
            }
        }
        
        return false;
    }
    
    // ==================== Pass 5: Create Semantic Edges ====================
    
    /**
     * Creates semantic edges based on resolved references.
     * 
     * This pass creates edges like:
     * - CALLS edges from method call nodes to method nodes
     * - ACCESSES edges from field access nodes to field nodes
     * - CREATES edges from new expressions to class nodes
     * - READS/WRITES edges for field access
     */
    public void createSemanticEdges() {
        // This will be implemented when we have expression nodes
        // For now, we'll just create edges based on what we can infer from the graph
        
        // Create CALLS edges from HAS_METHOD relationships
        // (This is a placeholder - actual CALLS edges would come from method call expressions)
        
        // Create CREATES edges for constructors
        for (Node node : graph.getAllNodes()) {
            if (node instanceof MethodNode) {
                MethodNode method = (MethodNode) node;
                if (method.isConstructor()) {
                    // Find the class and create CREATES edges from new expressions
                    // (Would need expression nodes to do this properly)
                }
            }
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Resolves all symbols for a specific file.
     * 
     * @param file the file to resolve
     */
    public void resolveFile(Path file) {
        // Get all nodes in the file
        List<Node> nodesInFile = graph.getNodesInFile(file);
        
        // Register imports and package
        for (Node node : nodesInFile) {
            if (node instanceof PackageNode) {
                symbolTable.register((PackageNode) node);
                symbolTable.registerPackage(file, node.getName());
            }
        }
        
        // Register all declarations
        for (Node node : nodesInFile) {
            if (node instanceof ClassNode) {
                symbolTable.register((ClassNode) node);
            } else if (node instanceof MethodNode) {
                symbolTable.register((MethodNode) node);
            } else if (node instanceof FieldNode) {
                symbolTable.register((FieldNode) node);
            } else if (node instanceof VariableNode) {
                symbolTable.register((VariableNode) node);
            }
        }
    }
    
    // ==================== Statistics ====================
    
    /**
     * Returns the number of resolved classes.
     */
    public int getResolvedClassCount() {
        return resolvedClasses;
    }
    
    /**
     * Returns the number of resolved methods.
     */
    public int getResolvedMethodCount() {
        return resolvedMethods;
    }
    
    /**
     * Returns the number of resolved fields.
     */
    public int getResolvedFieldCount() {
        return resolvedFields;
    }
    
    /**
     * Returns the number of resolved variables.
     */
    public int getResolvedVariableCount() {
        return resolvedVariables;
    }
    
    /**
     * Returns the number of unresolved references.
     */
    public int getUnresolvedReferenceCount() {
        return unresolvedReferences;
    }
    
    /**
     * Returns a summary of the resolution results.
     */
    public String getSummary() {
        return String.format(
            "Resolution Summary: Classes=%d, Methods=%d, Fields=%d, Variables=%d, Unresolved=%d",
            resolvedClasses, resolvedMethods, resolvedFields, resolvedVariables, unresolvedReferences
        );
    }
    
    /**
     * Resets the statistics.
     */
    public void resetStatistics() {
        resolvedClasses = 0;
        resolvedMethods = 0;
        resolvedFields = 0;
        resolvedVariables = 0;
        unresolvedReferences = 0;
    }
}
