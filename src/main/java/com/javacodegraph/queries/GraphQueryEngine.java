package com.javacodegraph.queries;

import com.javacodegraph.model.*;
import com.javacodegraph.model.declaration.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of JavaGraphQuery that executes queries against a Graph.
 * 
 * This engine uses the Graph's indexes for efficient querying.
 * Most queries are O(1) or O(k) where k is the number of results.
 * 
 * The engine delegates to the Graph's Indexes for most lookups,
 * providing a convenient query interface on top.
 */
public class GraphQueryEngine implements JavaGraphQuery {
    
    private final Graph graph;
    private final Indexes indexes;
    
    // Query cache for performance
    private final Map<String, Object> queryCache = new HashMap<>();
    
    // Statistics
    private long queryCount = 0;
    private long cacheHits = 0;
    private long totalQueryTime = 0;
    
    /**
     * Creates a new query engine for the given graph.
     * 
     * @param graph the graph to query
     */
    public GraphQueryEngine(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        this.graph = graph;
        this.indexes = graph.getIndexes();
    }
    
    // ==================== CLASS QUERIES ====================
    
    @Override
    public Optional<ClassNode> findClassByQualifiedName(String qn) {
        if (qn == null || qn.isBlank()) {
            return Optional.empty();
        }
        return indexes.getClassByQualifiedName(qn);
    }
    
    @Override
    public List<ClassNode> findClassesByName(String name) {
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }
        return indexes.getClassesByName(name);
    }
    
    @Override
    public List<ClassNode> getAllClasses() {
        return indexes.getAllClasses();
    }
    
    @Override
    public List<MethodNode> getAllMethods() {
        return indexes.getAllMethods();
    }
    
    @Override
    public List<FieldNode> getAllFields() {
        return indexes.getAllFields();
    }
    
    @Override
    public List<ClassNode> getSubclasses(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        String fqn = clazz.getQualifiedName();
        if (fqn == null || fqn.isBlank()) {
            return Collections.emptyList();
        }
        
        return indexes.getAllClasses().stream()
            .filter(c -> hasSuperclass(c, clazz))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ClassNode> getImplementations(ClassNode iface) {
        if (iface == null) {
            return Collections.emptyList();
        }
        
        String fqn = iface.getQualifiedName();
        if (fqn == null || fqn.isBlank()) {
            return Collections.emptyList();
        }
        
        return indexes.getAllClasses().stream()
            .filter(c -> implementsInterface(c, iface))
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<ClassNode> getSuperclass(ClassNode clazz) {
        if (clazz == null || !clazz.hasSuperclass()) {
            return Optional.empty();
        }
        
        return indexes.getClassByQualifiedName(clazz.getSuperclass());
    }
    
    @Override
    public List<ClassNode> getInterfaces(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        List<ClassNode> interfaces = new ArrayList<>();
        for (String ifaceFqn : clazz.getInterfaces()) {
            indexes.getClassByQualifiedName(ifaceFqn).ifPresent(interfaces::add);
        }
        return interfaces;
    }
    
    // ==================== METHOD QUERIES ====================
    
    @Override
    public Optional<MethodNode> findMethodBySignature(String signature) {
        if (signature == null || signature.isBlank()) {
            return Optional.empty();
        }
        return indexes.getMethodBySignature(signature);
    }
    
    @Override
    public List<MethodNode> findMethodsByName(String name) {
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }
        return indexes.getMethodsByName(name);
    }
    
    @Override
    public List<MethodNode> getMethods(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return indexes.getEdgesFrom(clazz.getId(), EdgeTypes.HAS_METHOD).stream()
            .map(edge -> graph.getNode(edge.getToId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(node -> node instanceof MethodNode)
            .map(node -> (MethodNode) node)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MethodNode> getCallers(MethodNode method) {
        if (method == null) {
            return Collections.emptyList();
        }
        
        // Find all CALLS edges pointing to this method
        return indexes.getEdgesTo(method.getId(), EdgeTypes.CALLS).stream()
            .map(edge -> graph.getNode(edge.getFromId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(node -> node instanceof MethodNode)
            .map(node -> (MethodNode) node)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MethodNode> getCallees(MethodNode method) {
        if (method == null) {
            return Collections.emptyList();
        }
        
        // Find all CALLS edges originating from this method
        return indexes.getEdgesFrom(method.getId(), EdgeTypes.CALLS).stream()
            .map(edge -> graph.getNode(edge.getToId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(node -> node instanceof MethodNode)
            .map(node -> (MethodNode) node)
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<MethodNode> getOverriddenMethod(MethodNode method) {
        if (method == null) {
            return Optional.empty();
        }
        
        String methodFqn = method.getQualifiedName();
        String signature = method.getSignature();
        if (methodFqn == null || signature == null) {
            return Optional.empty();
        }
        
        // Find methods with the same signature in superclasses
        Optional<ClassNode> superclass = getSuperclass(method.getClassNode());
        if (superclass.isEmpty()) {
            return Optional.empty();
        }
        
        // Recursively check superclass hierarchy
        return findOverriddenInHierarchy(method, superclass.get());
    }
    
    @Override
    public List<MethodNode> getOverridingMethods(MethodNode method) {
        if (method == null) {
            return Collections.emptyList();
        }
        
        String signature = method.getSignature();
        if (signature == null) {
            return Collections.emptyList();
        }
        
        // Find all methods with the same signature that are in subclasses
        List<MethodNode> overriding = new ArrayList<>();
        
        for (MethodNode m : indexes.getAllMethods()) {
            if (m.getSignature().equals(signature) && !m.getId().equals(method.getId())) {
                // Check if m's class is a subclass of method's class
                Optional<ClassNode> mClass = getClassForMethod(m);
                Optional<ClassNode> methodClass = getClassForMethod(method);
                
                if (mClass.isPresent() && methodClass.isPresent()) {
                    if (isSubclass(mClass.get(), methodClass.get())) {
                        overriding.add(m);
                    }
                }
            }
        }
        
        return overriding;
    }
    
    @Override
    public List<MethodNode> getConstructors(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return getMethods(clazz).stream()
            .filter(MethodNode::isConstructor)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MethodNode> getStaticMethods(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return getMethods(clazz).stream()
            .filter(MethodNode::isStatic)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MethodNode> getPublicMethods(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return getMethods(clazz).stream()
            .filter(MethodNode::isPublic)
            .collect(Collectors.toList());
    }
    
    // ==================== FIELD QUERIES ====================
    
    @Override
    public Optional<FieldNode> findFieldByQualifiedName(String qn) {
        if (qn == null || qn.isBlank()) {
            return Optional.empty();
        }
        return indexes.getFieldByQualifiedName(qn);
    }
    
    @Override
    public List<FieldNode> getFields(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return indexes.getEdgesFrom(clazz.getId(), EdgeTypes.HAS_FIELD).stream()
            .map(edge -> graph.getNode(edge.getToId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(node -> node instanceof FieldNode)
            .map(node -> (FieldNode) node)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Node> getFieldAccesses(FieldNode field) {
        if (field == null) {
            return Collections.emptyList();
        }
        
        // Find all ACCESSES edges pointing to this field
        return indexes.getEdgesTo(field.getId(), EdgeTypes.ACCESSES).stream()
            .map(edge -> graph.getNode(edge.getFromId()).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<FieldNode> getStaticFields(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return getFields(clazz).stream()
            .filter(FieldNode::isStatic)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<FieldNode> getFinalFields(ClassNode clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        
        return getFields(clazz).stream()
            .filter(FieldNode::isFinal)
            .collect(Collectors.toList());
    }
    
    // ==================== VARIABLE QUERIES ====================
    
    @Override
    public List<VariableNode> getVariables(MethodNode method) {
        if (method == null) {
            return Collections.emptyList();
        }
        
        // Find all variable declarations in the method
        // This would be more accurate with statement nodes
        return indexes.getAllNodes().stream()
            .filter(node -> node instanceof VariableNode)
            .map(node -> (VariableNode) node)
            .filter(var -> method.getQualifiedName().equals(var.getScopeMethod()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Node> getVariableUses(VariableNode var) {
        if (var == null) {
            return Collections.emptyList();
        }
        
        // Find all REFERENCES edges pointing to this variable
        return indexes.getEdgesTo(var.getId(), EdgeTypes.REFERENCES).stream()
            .map(edge -> graph.getNode(edge.getFromId()).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    // ==================== AST QUERIES ====================
    
    @Override
    public List<Node> getStatements(MethodNode method) {
        if (method == null) {
            return Collections.emptyList();
        }
        
        // Find all statement nodes that belong to this method
        // This requires statement nodes to have a reference to their method
        return indexes.getAllNodes().stream()
            .filter(node -> isStatementNode(node))
            .filter(node -> belongsToMethod(node, method))
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Node> getStatementAt(MethodNode method, int line) {
        if (method == null) {
            return Optional.empty();
        }
        
        return getStatements(method).stream()
            .filter(node -> node.getStartPosition() != null)
            .filter(node -> node.getStartPosition().line() == line)
            .findFirst();
    }
    
    // ==================== FILE QUERIES ====================
    
    @Override
    public List<Node> getNodesInFile(Path file) {
        if (file == null) {
            return Collections.emptyList();
        }
        return indexes.getNodesByFile(file);
    }
    
    @Override
    public List<ClassNode> getClassesInFile(Path file) {
        if (file == null) {
            return Collections.emptyList();
        }
        
        return indexes.getNodesByFile(file).stream()
            .filter(node -> node instanceof ClassNode)
            .map(node -> (ClassNode) node)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MethodNode> getMethodsInFile(Path file) {
        if (file == null) {
            return Collections.emptyList();
        }
        
        return indexes.getNodesByFile(file).stream()
            .filter(node -> node instanceof MethodNode)
            .map(node -> (MethodNode) node)
            .collect(Collectors.toList());
    }
    
    // ==================== PACKAGE QUERIES ====================
    
    @Override
    public Optional<PackageNode> findPackageByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return indexes.getPackageByName(name);
    }
    
    @Override
    public List<PackageNode> getAllPackages() {
        return indexes.getAllPackages();
    }
    
    @Override
    public List<ClassNode> getClassesInPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return Collections.emptyList();
        }
        
        return indexes.getAllClasses().stream()
            .filter(cls -> packageName.equals(cls.getPackageName()))
            .collect(Collectors.toList());
    }
    
    // ==================== CONTROL FLOW QUERIES ====================
    
    @Override
    public Graph buildCFG(MethodNode method) {
        // TODO: Implement control flow graph construction
        // This will be fully implemented in future phases
        throw new UnsupportedOperationException("CFG construction not yet implemented");
    }
    
    @Override
    public boolean isReachable(Node from, Node to) {
        // TODO: Implement reachability analysis
        // This requires CFG to be built first
        throw new UnsupportedOperationException("Reachability analysis not yet implemented");
    }
    
    // ==================== SEARCH QUERIES ====================
    
    @Override
    public List<Node> getNodesByType(String type) {
        if (type == null || type.isBlank()) {
            return Collections.emptyList();
        }
        return indexes.getNodesByType(type);
    }
    
    @Override
    public List<Edge> getEdgesByType(String type) {
        if (type == null || type.isBlank()) {
            return Collections.emptyList();
        }
        return indexes.getEdgesByType(type);
    }
    
    @Override
    public List<Node> getNodesWithModifier(String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return Collections.emptyList();
        }
        
        return indexes.getAllNodes().stream()
            .filter(node -> hasModifier(node, modifier))
            .collect(Collectors.toList());
    }
    
    // ==================== STATISTICS QUERIES ====================
    
    @Override
    public int getTotalNodeCount() {
        return graph.getNodeCount();
    }
    
    @Override
    public int getTotalEdgeCount() {
        return graph.getEdgeCount();
    }
    
    @Override
    public int getNodeCountByType(String type) {
        if (type == null || type.isBlank()) {
            return 0;
        }
        return indexes.getNodesByType(type).size();
    }
    
    @Override
    public int getEdgeCountByType(String type) {
        if (type == null || type.isBlank()) {
            return 0;
        }
        return indexes.getEdgesByType(type).size();
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Checks if a node is a statement node.
     */
    private boolean isStatementNode(Node node) {
        if (node == null) {
            return false;
        }
        String type = node.getType();
        return type != null && (
            type.equals(Node.TYPE_BLOCK) ||
            type.equals(Node.TYPE_IF) ||
            type.equals(Node.TYPE_FOR) ||
            type.equals(Node.TYPE_WHILE) ||
            type.equals(Node.TYPE_DO) ||
            type.equals(Node.TYPE_SWITCH) ||
            type.equals(Node.TYPE_TRY) ||
            type.equals(Node.TYPE_RETURN) ||
            type.equals(Node.TYPE_THROW) ||
            type.equals(Node.TYPE_BREAK) ||
            type.equals(Node.TYPE_CONTINUE) ||
            type.equals(Node.TYPE_EXPRESSION_STMT) ||
            type.equals(Node.TYPE_VARIABLE_DECL)
        );
    }
    
    /**
     * Checks if a node belongs to a method.
     */
    private boolean belongsToMethod(Node node, MethodNode method) {
        if (node == null || method == null) {
            return false;
        }
        
        // Check if node's file matches method's file
        if (node.getFile() != null && method.getFile() != null) {
            if (!node.getFile().equals(method.getFile())) {
                return false;
            }
        }
        
        // For variable nodes, check scope method
        if (node instanceof VariableNode) {
            VariableNode var = (VariableNode) node;
            return method.getQualifiedName().equals(var.getScopeMethod());
        }
        
        // For other nodes, this is a simplified check
        // Would need more sophisticated tracking
        return true;
    }
    
    /**
     * Checks if a class has the given superclass (directly or indirectly).
     */
    private boolean hasSuperclass(ClassNode clazz, ClassNode superclass) {
        if (clazz == null || superclass == null) {
            return false;
        }
        
        if (clazz.getQualifiedName().equals(superclass.getQualifiedName())) {
            return false;
        }
        
        // Check direct superclass
        if (clazz.hasSuperclass() && clazz.getSuperclass().equals(superclass.getQualifiedName())) {
            return true;
        }
        
        // Recursively check
        if (clazz.hasSuperclass()) {
            Optional<ClassNode> parent = getSuperclass(clazz);
            if (parent.isPresent()) {
                return hasSuperclass(parent.get(), superclass);
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a class implements the given interface (directly or indirectly).
     */
    private boolean implementsInterface(ClassNode clazz, ClassNode iface) {
        if (clazz == null || iface == null) {
            return false;
        }
        
        if (clazz.getQualifiedName().equals(iface.getQualifiedName())) {
            return false;
        }
        
        // Check direct interfaces
        for (String ifaceFqn : clazz.getInterfaces()) {
            if (ifaceFqn.equals(iface.getQualifiedName())) {
                return true;
            }
        }
        
        // Recursively check superclass
        if (clazz.hasSuperclass()) {
            Optional<ClassNode> parent = getSuperclass(clazz);
            if (parent.isPresent()) {
                return implementsInterface(parent.get(), iface);
            }
        }
        
        // Recursively check interfaces
        for (ClassNode implemented : getInterfaces(clazz)) {
            if (implementsInterface(implemented, iface)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Finds the overridden method in the superclass hierarchy.
     */
    private Optional<MethodNode> findOverriddenInHierarchy(MethodNode method, ClassNode current) {
        if (method == null || current == null) {
            return Optional.empty();
        }
        
        // Get all methods in the current class
        List<MethodNode> methods = getMethods(current);
        
        // Find a method with matching signature
        for (MethodNode m : methods) {
            if (m.getSignature().equals(method.getSignature()) &&
                !m.getId().equals(method.getId())) {
                return Optional.of(m);
            }
        }
        
        // Recursively check superclass
        return getSuperclass(current).flatMap(superclass -> 
            findOverriddenInHierarchy(method, superclass));
    }
    
    /**
     * Gets the class for a method.
     */
    private Optional<ClassNode> getClassForMethod(MethodNode method) {
        if (method == null) {
            return Optional.empty();
        }
        
        String className = method.getClassName();
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        
        return findClassByQualifiedName(className);
    }
    
    /**
     * Checks if class1 is a subclass of class2.
     */
    private boolean isSubclass(ClassNode class1, ClassNode class2) {
        if (class1 == null || class2 == null) {
            return false;
        }
        return hasSuperclass(class1, class2);
    }
    
    /**
     * Checks if a node has a specific modifier.
     */
    private boolean hasModifier(Node node, String modifier) {
        if (node == null || modifier == null || modifier.isBlank()) {
            return false;
        }
        
        if (node instanceof ClassNode) {
            return ((ClassNode) node).hasModifier(modifier);
        } else if (node instanceof MethodNode) {
            return ((MethodNode) node).hasModifier(modifier);
        } else if (node instanceof FieldNode) {
            return ((FieldNode) node).hasModifier(modifier);
        }
        
        return false;
    }
    
    // ==================== CACHE MANAGEMENT ====================
    
    /**
     * Clears the query cache.
     */
    public void clearCache() {
        queryCache.clear();
    }
    
    /**
     * Enables or disables query caching.
     */
    public void setCacheEnabled(boolean enabled) {
        // Cache is always enabled; clearing is done manually
    }
    
    // ==================== STATISTICS ====================
    
    /**
     * Returns the total number of queries executed.
     */
    public long getQueryCount() {
        return queryCount;
    }
    
    /**
     * Returns the number of cache hits.
     */
    public long getCacheHits() {
        return cacheHits;
    }
    
    /**
     * Returns the total query time in milliseconds.
     */
    public long getTotalQueryTime() {
        return totalQueryTime;
    }
    
    /**
     * Returns the average query time in milliseconds.
     */
    public double getAverageQueryTime() {
        return queryCount > 0 ? (double) totalQueryTime / queryCount : 0;
    }
    
    /**
     * Resets the query statistics.
     */
    public void resetStatistics() {
        queryCount = 0;
        cacheHits = 0;
        totalQueryTime = 0;
    }
}
