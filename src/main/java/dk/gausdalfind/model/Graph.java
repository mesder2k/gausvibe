package dk.gausdalfind.model;

import java.util.*;

/**
 * Represents a directed graph of Java code elements.
 * 
 * The graph consists of nodes (representing code elements) and edges 
 * (representing relationships between elements). Indexes are maintained 
 * for efficient querying.
 */
public class Graph {
    
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Indexes indexes = new Indexes();
    
    // ==================== Node Management ====================
    
    /**
     * Adds a node to the graph.
     * The node is automatically indexed.
     * 
     * @param node the node to add
     * @return true if the node was added, false if it already existed
     */
    public boolean addNode(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        
        String id = node.getId();
        if (nodes.containsKey(id)) {
            return false; // Node already exists
        }
        
        nodes.put(id, node);
        indexes.index(node);
        return true;
    }
    
    /**
     * Adds multiple nodes to the graph.
     */
    public void addNodes(Collection<Node> newNodes) {
        if (newNodes == null) {
            throw new IllegalArgumentException("Nodes collection cannot be null");
        }
        for (Node node : newNodes) {
            addNode(node);
        }
    }
    
    /**
     * Removes a node from the graph.
     * All edges connected to the node are also removed.
     * 
     * @param nodeId the ID of the node to remove
     * @return the removed node, or null if not found
     */
    public Node removeNode(String nodeId) {
        Node node = nodes.remove(nodeId);
        if (node != null) {
            indexes.unindex(node);
            // Remove all edges connected to this node
            removeEdgesFrom(nodeId);
            removeEdgesTo(nodeId);
        }
        return node;
    }
    
    /**
     * Removes a node from the graph.
     */
    public boolean removeNode(Node node) {
        if (node == null) {
            return false;
        }
        return removeNode(node.getId()) != null;
    }
    
    /**
     * Returns a node by its ID.
     */
    public Optional<Node> getNode(String id) {
        return Optional.ofNullable(nodes.get(id));
    }
    
    /**
     * Returns all nodes in the graph.
     */
    public Collection<Node> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }
    
    /**
     * Returns the number of nodes in the graph.
     */
    public int getNodeCount() {
        return nodes.size();
    }
    
    // ==================== Edge Management ====================
    
    /**
     * Adds an edge to the graph.
     * The edge is automatically indexed.
     * 
     * @param edge the edge to add
     * @return true if the edge was added
     */
    public boolean addEdge(Edge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("Edge cannot be null");
        }
        
        // Validate that both nodes exist
        if (!nodes.containsKey(edge.getFromId())) {
            throw new IllegalArgumentException(
                "From node does not exist: " + edge.getFromId()
            );
        }
        if (!nodes.containsKey(edge.getToId())) {
            throw new IllegalArgumentException(
                "To node does not exist: " + edge.getToId()
            );
        }
        
        edges.add(edge);
        indexes.index(edge);
        return true;
    }
    
    /**
     * Adds multiple edges to the graph.
     */
    public void addEdges(Collection<Edge> newEdges) {
        if (newEdges == null) {
            throw new IllegalArgumentException("Edges collection cannot be null");
        }
        for (Edge edge : newEdges) {
            addEdge(edge);
        }
    }
    
    /**
     * Removes an edge from the graph.
     */
    public boolean removeEdge(Edge edge) {
        if (edge == null) {
            return false;
        }
        boolean removed = edges.remove(edge);
        if (removed) {
            indexes.unindex(edge);
        }
        return removed;
    }
    
    /**
     * Removes all edges from a specific node.
     */
    public List<Edge> removeEdgesFrom(String nodeId) {
        List<Edge> removed = new ArrayList<>();
        Iterator<Edge> it = edges.iterator();
        while (it.hasNext()) {
            Edge edge = it.next();
            if (edge.getFromId().equals(nodeId)) {
                it.remove();
                indexes.unindex(edge);
                removed.add(edge);
            }
        }
        return removed;
    }
    
    /**
     * Removes all edges to a specific node.
     */
    public List<Edge> removeEdgesTo(String nodeId) {
        List<Edge> removed = new ArrayList<>();
        Iterator<Edge> it = edges.iterator();
        while (it.hasNext()) {
            Edge edge = it.next();
            if (edge.getToId().equals(nodeId)) {
                it.remove();
                indexes.unindex(edge);
                removed.add(edge);
            }
        }
        return removed;
    }
    
    /**
     * Returns all edges in the graph.
     */
    public List<Edge> getAllEdges() {
        return Collections.unmodifiableList(edges);
    }
    
    /**
     * Returns the number of edges in the graph.
     */
    public int getEdgeCount() {
        return edges.size();
    }
    
    // ==================== Index Access ====================
    
    /**
     * Returns the indexes for this graph.
     */
    public Indexes getIndexes() {
        return indexes;
    }
    
    // ==================== Query Convenience Methods ====================
    
    /**
     * Returns all nodes of a specific type.
     */
    public List<Node> getNodesByType(String type) {
        return indexes.getNodesByType(type);
    }
    
    /**
     * Returns all edges of a specific type.
     */
    public List<Edge> getEdgesByType(String type) {
        return indexes.getEdgesByType(type);
    }
    
    /**
     * Returns all outgoing edges from a node.
     */
    public List<Edge> getEdgesFrom(String nodeId) {
        return indexes.getEdgesFrom(nodeId);
    }
    
    /**
     * Returns all incoming edges to a node.
     */
    public List<Edge> getEdgesTo(String nodeId) {
        return indexes.getEdgesTo(nodeId);
    }
    
    /**
     * Returns all outgoing edges from a node filtered by type.
     */
    public List<Edge> getEdgesFrom(String nodeId, String type) {
        return indexes.getEdgesFrom(nodeId, type);
    }
    
    /**
     * Returns all incoming edges to a node filtered by type.
     */
    public List<Edge> getEdgesTo(String nodeId, String type) {
        return indexes.getEdgesTo(nodeId, type);
    }
    
    /**
     * Returns nodes in a specific file.
     */
    public List<Node> getNodesInFile(java.nio.file.Path file) {
        return indexes.getNodesByFile(file);
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Clears all nodes and edges from the graph.
     */
    public void clear() {
        nodes.clear();
        edges.clear();
        indexes.clear();
    }
    
    /**
     * Adds all nodes and edges from another graph to this graph.
     */
    public void addAll(Graph other) {
        if (other == null) {
            throw new IllegalArgumentException("Other graph cannot be null");
        }
        addNodes(other.getAllNodes());
        addEdges(other.getAllEdges());
    }
    
    /**
     * Removes all edges of a specific type from the graph.
     * 
     * @param type the edge type to remove
     * @return the number of edges removed
     */
    public int removeEdgesByType(String type) {
        if (type == null || type.isBlank()) {
            return 0;
        }
        
        int count = 0;
        Iterator<Edge> it = edges.iterator();
        while (it.hasNext()) {
            Edge edge = it.next();
            if (type.equals(edge.getType())) {
                it.remove();
                indexes.unindex(edge);
                count++;
            }
        }
        return count;
    }
    
    /**
     * Returns a string representation of the graph.
     */
    @Override
    public String toString() {
        return "Graph{nodes=" + getNodeCount() + ", edges=" + getEdgeCount() + "}";
    }
    
    /**
     * Validates the graph integrity.
     * Checks that all edge nodes exist in the graph.
     * 
     * @return true if the graph is valid
     */
    public boolean validate() {
        for (Edge edge : edges) {
            if (!nodes.containsKey(edge.getFromId())) {
                return false;
            }
            if (!nodes.containsKey(edge.getToId())) {
                return false;
            }
        }
        return true;
    }
}
