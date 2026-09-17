package dk.gausdalfind.model.declaration;

import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.NodeIdGenerator;
import dk.gausdalfind.model.Position;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Represents a field declaration in Java source code.
 * 
 * Properties:
 * - name: The field name (e.g., "count")
 * - qualified_name: The fully qualified field name (e.g., "com.example.Calculator.count")
 * - type: The fully qualified type name
 * - modifiers: Set of modifiers (public, private, static, final, etc.)
 * - is_static: true if this is a static field
 * - is_final: true if this is a final field
 * - file: The source file path
 * - start/end: Position in source file
 */
public class FieldNode extends DeclarationNode {
    
    private final String type;
    private final Set<String> modifiers;
    private final boolean isStatic;
    private final boolean isFinal;
    
    /**
     * Creates a new field node.
     * 
     * @param id the unique ID
     * @param name the field name
     * @param qualifiedName the fully qualified field name
     * @param type the fully qualified type name
     * @param modifiers the set of modifiers
     * @param isStatic true if this is a static field
     * @param isFinal true if this is a final field
     * @param file the source file path
     * @param startPosition the start position in the file
     * @param endPosition the end position in the file
     */
    public FieldNode(String id, String name, String qualifiedName, String type,
                     Set<String> modifiers, boolean isStatic, boolean isFinal,
                     Path file, Position startPosition, Position endPosition) {
        super(id, name, qualifiedName, file, startPosition, endPosition);
        this.type = type != null ? type : "";
        this.modifiers = modifiers != null ? Set.copyOf(modifiers) : Set.of();
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }
    
    @Override
    public String getType() {
        return Node.TYPE_FIELD;
    }
    
    /**
     * Returns the fully qualified type name of this field.
     */
    public String getDataType() {
        return type;
    }
    
    /**
     * Returns the simple type name (without package).
     */
    public String getSimpleType() {
        if (type == null || type.isBlank()) {
            return "";
        }
        int lastDot = type.lastIndexOf('.');
        if (lastDot < 0) {
            return type;
        }
        return type.substring(lastDot + 1);
    }
    
    /**
     * Returns the set of modifiers for this field.
     */
    public Set<String> getModifiers() {
        return Collections.unmodifiableSet(modifiers);
    }
    
    /**
     * Returns true if this field has the specified modifier.
     */
    public boolean hasModifier(String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return false;
        }
        return modifiers.contains(modifier);
    }
    
    /**
     * Returns true if this is a static field.
     */
    public boolean isStatic() {
        return isStatic;
    }
    
    /**
     * Returns true if this is a final field.
     */
    public boolean isFinal() {
        return isFinal;
    }
    
    /**
     * Returns true if this field is public.
     */
    public boolean isPublic() {
        return modifiers.contains("public");
    }
    
    /**
     * Returns true if this field is private.
     */
    public boolean isPrivate() {
        return modifiers.contains("private");
    }
    
    /**
     * Returns true if this field is protected.
     */
    public boolean isProtected() {
        return modifiers.contains("protected");
    }
    
    /**
     * Returns the class name that this field belongs to.
     */
    public String getClassName() {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return qualifiedName.substring(0, lastDot);
    }
}
