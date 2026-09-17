package dk.gausdalfind.model.declaration;

import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.Position;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for declaration nodes.
 * 
 * Declaration nodes represent named elements in Java code:
 * - Packages
 * - Classes/Interfaces
 * - Methods
 * - Fields
 * - Parameters
 * - Variables
 */
public abstract class DeclarationNode implements Node {
    
    protected final String id;
    protected final String name;
    protected final String qualifiedName;
    protected final Path file;
    protected final Position startPosition;
    protected final Position endPosition;
    protected final Map<String, Object> properties;
    
    /**
     * Creates a new declaration node.
     */
    protected DeclarationNode(String id, String name, String qualifiedName, 
                              Path file, Position startPosition, Position endPosition) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
        
        this.id = id;
        this.name = name;
        this.qualifiedName = qualifiedName != null ? qualifiedName : name;
        this.file = file;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.properties = new HashMap<>();
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public abstract String getType();
    
    /**
     * Returns the simple name of this declaration.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the fully qualified name of this declaration.
     */
    public String getQualifiedName() {
        return qualifiedName;
    }
    
    @Override
    public Path getFile() {
        return file;
    }
    
    @Override
    public Position getStartPosition() {
        return startPosition;
    }
    
    @Override
    public Position getEndPosition() {
        return endPosition;
    }
    
    @Override
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
    
    /**
     * Sets a property value.
     */
    public void setProperty(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Property key cannot be null or blank");
        }
        properties.put(key, value);
    }
    
    /**
     * Gets a property value.
     */
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
    
    /**
     * Gets a property value with a default.
     */
    public <T> T getProperty(String key, Class<T> type, T defaultValue) {
        T value = getProperty(key, type);
        return value != null ? value : defaultValue;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeclarationNode that = (DeclarationNode) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "', name='" + name + "', qualifiedName='" + qualifiedName + "'}";
    }
}
