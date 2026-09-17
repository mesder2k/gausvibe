package com.javacodegraph.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a directed edge between two nodes in the Java code graph.
 * 
 * Edges represent relationships between nodes such as:
 * - Structural relationships (CONTAINS, BODY, CONDITION, etc.)
 * - Declaration relationships (HAS_METHOD, INHERITS, IMPLEMENTS, etc.)
 * - Expression relationships (RECEIVER, ARGUMENT, LEFT_OPERAND, etc.)
 * - Semantic relationships (CALLS, REFERENCES, ACCESSES, etc.)
 */
public class Edge {
    
    private final String fromId;
    private final String toId;
    private final String type;
    private final Map<String, Object> properties;
    
    /**
     * Creates an edge with no properties.
     */
    public Edge(String fromId, String toId, String type) {
        this(fromId, toId, type, Map.of());
    }
    
    /**
     * Creates an edge with properties.
     */
    public Edge(String fromId, String toId, String type, Map<String, Object> properties) {
        if (fromId == null || fromId.isBlank()) {
            throw new IllegalArgumentException("fromId cannot be null or blank");
        }
        if (toId == null || toId.isBlank()) {
            throw new IllegalArgumentException("toId cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type cannot be null or blank");
        }
        this.fromId = fromId;
        this.toId = toId;
        this.type = type;
        this.properties = Map.copyOf(properties);
    }
    
    public String getFromId() {
        return fromId;
    }
    
    public String getToId() {
        return toId;
    }
    
    public String getType() {
        return type;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    /**
     * Returns a property value, or null if not present.
     */
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
    
    /**
     * Returns a property value, or the default value if not present.
     */
    public <T> T getProperty(String key, Class<T> type, T defaultValue) {
        T value = getProperty(key, type);
        return value != null ? value : defaultValue;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(fromId, edge.fromId) &&
               Objects.equals(toId, edge.toId) &&
               Objects.equals(type, edge.type) &&
               Objects.equals(properties, edge.properties);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fromId, toId, type, properties);
    }
    
    @Override
    public String toString() {
        return "Edge{from=" + fromId + ", to=" + toId + ", type=" + type + ", properties=" + properties + "}";
    }
}
