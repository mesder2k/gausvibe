package com.javacodegraph.model.declaration;

import com.javacodegraph.model.Node;
import com.javacodegraph.model.NodeIdGenerator;
import com.javacodegraph.model.Position;

import java.nio.file.Path;

/**
 * Represents a local variable declaration in Java source code.
 * 
 * Properties:
 * - name: The variable name (e.g., "count")
 * - qualified_name: The fully qualified variable name (file + method + variable name)
 * - type: The fully qualified type name
 * - scope_method: The fully qualified name of the method this variable is in
 * - is_final: true if this is a final variable
 * - file: The source file path
 * - start/end: Position in source file
 */
public class VariableNode extends DeclarationNode {
    
    private final String type;
    private final String scopeMethod;
    private final boolean isFinal;
    
    /**
     * Creates a new variable node.
     * 
     * @param id the unique ID
     * @param name the variable name
     * @param qualifiedName the fully qualified variable name
     * @param type the fully qualified type name
     * @param scopeMethod the fully qualified name of the method containing this variable
     * @param isFinal true if this is a final variable
     * @param file the source file path
     * @param startPosition the start position in the file
     * @param endPosition the end position in the file
     */
    public VariableNode(String id, String name, String qualifiedName, String type,
                        String scopeMethod, boolean isFinal,
                        Path file, Position startPosition, Position endPosition) {
        super(id, name, qualifiedName, file, startPosition, endPosition);
        this.type = type != null ? type : "";
        this.scopeMethod = scopeMethod != null ? scopeMethod : "";
        this.isFinal = isFinal;
    }
    
    @Override
    public String getType() {
        return Node.TYPE_VARIABLE;
    }
    
    /**
     * Returns the fully qualified type name of this variable.
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
     * Returns the fully qualified name of the method this variable is in.
     */
    public String getScopeMethod() {
        return scopeMethod;
    }
    
    /**
     * Returns the simple name of the method this variable is in.
     */
    public String getScopeMethodName() {
        if (scopeMethod == null || scopeMethod.isBlank()) {
            return "";
        }
        int lastDot = scopeMethod.lastIndexOf('.');
        if (lastDot < 0) {
            return scopeMethod;
        }
        return scopeMethod.substring(lastDot + 1);
    }
    
    /**
     * Returns true if this is a final variable.
     */
    public boolean isFinal() {
        return isFinal;
    }
}
