package dk.gausdalfind.model.declaration;

import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.NodeIdGenerator;
import dk.gausdalfind.model.Position;

import java.nio.file.Path;

/**
 * Represents a parameter declaration in Java source code.
 * 
 * Properties:
 * - name: The parameter name (e.g., "a")
 * - qualified_name: The fully qualified parameter name (method FQN + parameter name)
 * - type: The fully qualified type name
 * - position: The parameter position (0-indexed)
 * - belonging_method: The fully qualified name of the method this parameter belongs to
 * - file: The source file path
 * - start/end: Position in source file
 */
public class ParameterNode extends DeclarationNode {
    
    private final String type;
    private final int position;
    private final String belongingMethod;
    
    /**
     * Creates a new parameter node.
     * 
     * @param id the unique ID
     * @param name the parameter name
     * @param qualifiedName the fully qualified parameter name
     * @param type the fully qualified type name
     * @param position the parameter position (0-indexed)
     * @param belongingMethod the fully qualified name of the method
     * @param file the source file path
     * @param startPosition the start position in the file
     * @param endPosition the end position in the file
     */
    public ParameterNode(String id, String name, String qualifiedName, String type,
                         int position, String belongingMethod,
                         Path file, Position startPosition, Position endPosition) {
        super(id, name, qualifiedName, file, startPosition, endPosition);
        this.type = type != null ? type : "";
        this.position = position;
        this.belongingMethod = belongingMethod != null ? belongingMethod : "";
    }
    
    @Override
    public String getType() {
        return Node.TYPE_PARAMETER;
    }
    
    /**
     * Returns the fully qualified type name of this parameter.
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
     * Returns the parameter position (0-indexed).
     */
    public int getPosition() {
        return position;
    }
    
    /**
     * Returns the fully qualified name of the method this parameter belongs to.
     */
    public String getBelongingMethod() {
        return belongingMethod;
    }
    
    /**
     * Returns the method name (simple name) that this parameter belongs to.
     */
    public String getMethodName() {
        if (belongingMethod == null || belongingMethod.isBlank()) {
            return "";
        }
        int lastDot = belongingMethod.lastIndexOf('.');
        if (lastDot < 0) {
            return belongingMethod;
        }
        return belongingMethod.substring(lastDot + 1);
    }
}
