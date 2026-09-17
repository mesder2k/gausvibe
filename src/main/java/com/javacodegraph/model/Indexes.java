package com.javacodegraph.model;

import com.javacodegraph.model.declaration.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Maintains indexes for efficient querying of the Java code graph.
 * 
 * Indexes are updated automatically when nodes and edges are added to the graph.
 * This provides O(1) or O(k) lookup times for common queries.
 */
public class Indexes {
    
    // ==================== Node Indexes ====================
    
    /** Maps node ID to node */
    private final Map<String, Node> nodesById = new ConcurrentHashMap<>();
    
    /** Maps node type to list of nodes */
    private final Map<String, List<Node>> nodesByType = new ConcurrentHashMap<>();
    
    /** Maps file path to list of nodes */
    private final Map<Path, List<Node>> nodesByFile = new ConcurrentHashMap<>();
    
    // ==================== Class Indexes ====================
    
    /** Maps fully qualified class name to ClassNode */
    private final Map<String, ClassNode> classesByFqn = new ConcurrentHashMap<>();
    
    /** Maps class name (without package) to list of ClassNodes */
    private final Map<String, List<ClassNode>> classesByName = new ConcurrentHashMap<>();
    
    // ==================== Method Indexes ====================
    
    /** Maps method signature to MethodNode */
    private final Map<String, MethodNode> methodsBySignature = new ConcurrentHashMap<>();
    
    /** Maps method name to list of MethodNodes */
    private final Map<String, List<MethodNode>> methodsByName = new ConcurrentHashMap<>();
    
    // ==================== Field Indexes ====================
    
    /** Maps fully qualified field name to FieldNode */
    private final Map<String, FieldNode> fieldsByFqn = new ConcurrentHashMap<>();
    
    /** Maps field name to list of FieldNodes */
    private final Map<String, List<FieldNode>> fieldsByName = new ConcurrentHashMap<>();
    
    // ==================== Package Indexes ====================
    
    /** Maps package name to PackageNode */
    private final Map<String, PackageNode> packagesByName = new ConcurrentHashMap<>();
    
    // ==================== Edge Indexes ====================
    
    /** Maps edge type to list of edges */
    private final Map<String, List<Edge>> edgesByType = new ConcurrentHashMap<>();
    
    /** Maps from node ID to list of outgoing edges */
    private final Map<String, List<Edge>> edgesFrom = new ConcurrentHashMap<>();
    
    /** Maps to node ID to list of incoming edges */
    private final Map<String, List<Edge>> edgesTo = new ConcurrentHashMap<>();
    
    // ==================== Index Management ====================
    
    /**
     * Indexes a node for fast lookup.
     */
    public void index(Node node) {
        if (node == null) {
            return;
        }
        
        String id = node.getId();
        String type = node.getType();
        Path file = node.getFile();
        
        // Index by ID
        nodesById.put(id, node);
        
        // Index by type
        nodesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(node);
        
        // Index by file
        if (file != null) {
            nodesByFile.computeIfAbsent(file, k -> new ArrayList<>()).add(node);
        }
        
        // Type-specific indexing
        switch (type) {
            case Node.TYPE_CLASS:
                indexClass(node);
                break;
            case Node.TYPE_METHOD:
                indexMethod(node);
                break;
            case Node.TYPE_FIELD:
                indexField(node);
                break;
            case Node.TYPE_PACKAGE:
                indexPackage(node);
                break;
        }
    }
    
    private void indexClass(Node node) {
        if (!(node instanceof ClassNode)) return;
        ClassNode cls = (ClassNode) node;
        
        String fqn = cls.getQualifiedName();
        String name = cls.getName();
        
        if (fqn != null && !fqn.isBlank()) {
            classesByFqn.put(fqn, cls);
        }
        if (name != null && !name.isBlank()) {
            classesByName.computeIfAbsent(name, k -> new ArrayList<>()).add(cls);
        }
    }
    
    private void indexMethod(Node node) {
        if (!(node instanceof MethodNode)) return;
        MethodNode method = (MethodNode) node;
        
        String signature = method.getSignature();
        String name = method.getName();
        
        if (signature != null && !signature.isBlank()) {
            methodsBySignature.put(signature, method);
        }
        if (name != null && !name.isBlank()) {
            methodsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
        }
    }
    
    private void indexField(Node node) {
        if (!(node instanceof FieldNode)) return;
        FieldNode field = (FieldNode) node;
        
        String fqn = field.getQualifiedName();
        String name = field.getName();
        
        if (fqn != null && !fqn.isBlank()) {
            fieldsByFqn.put(fqn, field);
        }
        if (name != null && !name.isBlank()) {
            fieldsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(field);
        }
    }
    
    private void indexPackage(Node node) {
        if (!(node instanceof PackageNode)) return;
        PackageNode pkg = (PackageNode) node;
        
        String name = pkg.getName();
        if (name != null && !name.isBlank()) {
            packagesByName.put(name, pkg);
        }
    }
    
    /**
     * Removes a node from all indexes.
     */
    public void unindex(Node node) {
        if (node == null) {
            return;
        }
        
        String id = node.getId();
        String type = node.getType();
        Path file = node.getFile();
        
        // Remove from ID index
        nodesById.remove(id);
        
        // Remove from type index
        List<Node> typeList = nodesByType.get(type);
        if (typeList != null) {
            typeList.remove(node);
        }
        
        // Remove from file index
        if (file != null) {
            List<Node> fileList = nodesByFile.get(file);
            if (fileList != null) {
                fileList.remove(node);
            }
        }
        
        // Type-specific removal
        switch (type) {
            case Node.TYPE_CLASS:
                unindexClass(node);
                break;
            case Node.TYPE_METHOD:
                unindexMethod(node);
                break;
            case Node.TYPE_FIELD:
                unindexField(node);
                break;
            case Node.TYPE_PACKAGE:
                unindexPackage(node);
                break;
        }
    }
    
    private void unindexClass(Node node) {
        if (!(node instanceof ClassNode)) return;
        ClassNode cls = (ClassNode) node;
        
        String fqn = cls.getQualifiedName();
        String name = cls.getName();
        
        if (fqn != null && !fqn.isBlank()) {
            classesByFqn.remove(fqn);
        }
        if (name != null && !name.isBlank()) {
            List<ClassNode> list = classesByName.get(name);
            if (list != null) {
                list.remove(cls);
            }
        }
    }
    
    private void unindexMethod(Node node) {
        if (!(node instanceof MethodNode)) return;
        MethodNode method = (MethodNode) node;
        
        String signature = method.getSignature();
        String name = method.getName();
        
        if (signature != null && !signature.isBlank()) {
            methodsBySignature.remove(signature);
        }
        if (name != null && !name.isBlank()) {
            List<MethodNode> list = methodsByName.get(name);
            if (list != null) {
                list.remove(method);
            }
        }
    }
    
    private void unindexField(Node node) {
        if (!(node instanceof FieldNode)) return;
        FieldNode field = (FieldNode) node;
        
        String fqn = field.getQualifiedName();
        String name = field.getName();
        
        if (fqn != null && !fqn.isBlank()) {
            fieldsByFqn.remove(fqn);
        }
        if (name != null && !name.isBlank()) {
            List<FieldNode> list = fieldsByName.get(name);
            if (list != null) {
                list.remove(field);
            }
        }
    }
    
    private void unindexPackage(Node node) {
        if (!(node instanceof PackageNode)) return;
        PackageNode pkg = (PackageNode) node;
        
        String name = pkg.getName();
        if (name != null && !name.isBlank()) {
            packagesByName.remove(name);
        }
    }
    
    /**
     * Indexes an edge for fast lookup.
     */
    public void index(Edge edge) {
        if (edge == null) {
            return;
        }
        
        String type = edge.getType();
        String fromId = edge.getFromId();
        String toId = edge.getToId();
        
        // Index by type
        edgesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(edge);
        
        // Index by from node
        edgesFrom.computeIfAbsent(fromId, k -> new ArrayList<>()).add(edge);
        
        // Index by to node
        edgesTo.computeIfAbsent(toId, k -> new ArrayList<>()).add(edge);
    }
    
    /**
     * Removes an edge from all indexes.
     */
    public void unindex(Edge edge) {
        if (edge == null) {
            return;
        }
        
        String type = edge.getType();
        String fromId = edge.getFromId();
        String toId = edge.getToId();
        
        // Remove from type index
        List<Edge> typeList = edgesByType.get(type);
        if (typeList != null) {
            typeList.remove(edge);
        }
        
        // Remove from from index
        List<Edge> fromList = edgesFrom.get(fromId);
        if (fromList != null) {
            fromList.remove(edge);
        }
        
        // Remove from to index
        List<Edge> toList = edgesTo.get(toId);
        if (toList != null) {
            toList.remove(edge);
        }
    }
    
    // ==================== Node Lookup Methods ====================
    
    /** Returns all nodes in this graph. */
    public Collection<Node> getAllNodes() {
        return Collections.unmodifiableCollection(nodesById.values());
    }
    
    /** Returns node by ID, or null if not found. */
    public Node getNode(String id) {
        return nodesById.get(id);
    }
    
    /** Returns nodes by type. */
    public List<Node> getNodesByType(String type) {
        return Collections.unmodifiableList(
            nodesByType.getOrDefault(type, Collections.emptyList())
        );
    }
    
    /** Returns nodes in a specific file. */
    public List<Node> getNodesByFile(Path file) {
        return Collections.unmodifiableList(
            nodesByFile.getOrDefault(file, Collections.emptyList())
        );
    }
    
    // ==================== Class Lookup Methods ====================
    
    /** Returns class by fully qualified name. */
    public Optional<ClassNode> getClassByQualifiedName(String fqn) {
        return Optional.ofNullable(classesByFqn.get(fqn));
    }
    
    /** Returns all classes with a given name (without package). */
    public List<ClassNode> getClassesByName(String name) {
        return Collections.unmodifiableList(
            classesByName.getOrDefault(name, Collections.emptyList())
        );
    }
    
    /** Returns all classes. */
    public List<ClassNode> getAllClasses() {
        return Collections.unmodifiableList(new ArrayList<>(classesByFqn.values()));
    }
    
    // ==================== Method Lookup Methods ====================
    
    /** Returns method by signature. */
    public Optional<MethodNode> getMethodBySignature(String signature) {
        return Optional.ofNullable(methodsBySignature.get(signature));
    }
    
    /** Returns all methods with a given name. */
    public List<MethodNode> getMethodsByName(String name) {
        return Collections.unmodifiableList(
            methodsByName.getOrDefault(name, Collections.emptyList())
        );
    }
    
    /** Returns all methods. */
    public List<MethodNode> getAllMethods() {
        return Collections.unmodifiableList(new ArrayList<>(methodsBySignature.values()));
    }
    
    // ==================== Field Lookup Methods ====================
    
    /** Returns field by fully qualified name. */
    public Optional<FieldNode> getFieldByQualifiedName(String fqn) {
        return Optional.ofNullable(fieldsByFqn.get(fqn));
    }
    
    /** Returns all fields with a given name. */
    public List<FieldNode> getFieldsByName(String name) {
        return Collections.unmodifiableList(
            fieldsByName.getOrDefault(name, Collections.emptyList())
        );
    }
    
    /** Returns all fields. */
    public List<FieldNode> getAllFields() {
        return Collections.unmodifiableList(new ArrayList<>(fieldsByFqn.values()));
    }
    
    // ==================== Package Lookup Methods ====================
    
    /** Returns package by name. */
    public Optional<PackageNode> getPackageByName(String name) {
        return Optional.ofNullable(packagesByName.get(name));
    }
    
    /** Returns all packages. */
    public List<PackageNode> getAllPackages() {
        return Collections.unmodifiableList(new ArrayList<>(packagesByName.values()));
    }
    
    // ==================== Edge Lookup Methods ====================
    
    /** Returns all edges in this graph. */
    public Collection<Edge> getAllEdges() {
        List<Edge> allEdges = new ArrayList<>();
        for (List<Edge> list : edgesByType.values()) {
            allEdges.addAll(list);
        }
        return Collections.unmodifiableCollection(allEdges);
    }
    
    /** Returns edges by type. */
    public List<Edge> getEdgesByType(String type) {
        return Collections.unmodifiableList(
            edgesByType.getOrDefault(type, Collections.emptyList())
        );
    }
    
    /** Returns outgoing edges from a node. */
    public List<Edge> getEdgesFrom(String nodeId) {
        return Collections.unmodifiableList(
            edgesFrom.getOrDefault(nodeId, Collections.emptyList())
        );
    }
    
    /** Returns incoming edges to a node. */
    public List<Edge> getEdgesTo(String nodeId) {
        return Collections.unmodifiableList(
            edgesTo.getOrDefault(nodeId, Collections.emptyList())
        );
    }
    
    /** Returns outgoing edges from a node filtered by type. */
    public List<Edge> getEdgesFrom(String nodeId, String type) {
        return edgesFrom.getOrDefault(nodeId, Collections.emptyList())
            .stream()
            .filter(e -> e.getType().equals(type))
            .collect(Collectors.toList());
    }
    
    /** Returns incoming edges to a node filtered by type. */
    public List<Edge> getEdgesTo(String nodeId, String type) {
        return edgesTo.getOrDefault(nodeId, Collections.emptyList())
            .stream()
            .filter(e -> e.getType().equals(type))
            .collect(Collectors.toList());
    }
    
    /** Returns the number of indexed nodes. */
    public int getNodeCount() {
        return nodesById.size();
    }
    
    /** Returns the number of indexed edges. */
    public int getEdgeCount() {
        return getAllEdges().size();
    }
    
    /** Clears all indexes. */
    public void clear() {
        nodesById.clear();
        nodesByType.clear();
        nodesByFile.clear();
        classesByFqn.clear();
        classesByName.clear();
        methodsBySignature.clear();
        methodsByName.clear();
        fieldsByFqn.clear();
        fieldsByName.clear();
        packagesByName.clear();
        edgesByType.clear();
        edgesFrom.clear();
        edgesTo.clear();
    }
}
