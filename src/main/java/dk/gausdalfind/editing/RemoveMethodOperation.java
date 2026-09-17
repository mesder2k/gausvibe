package dk.gausdalfind.editing;

import java.util.Objects;

/**
 * Operation to remove a method from a class.
 */
public class RemoveMethodOperation implements Operation {
    
    private final String methodQualifiedName;
    
    public RemoveMethodOperation(String methodQualifiedName) {
        this.methodQualifiedName = Objects.requireNonNull(methodQualifiedName);
    }
    
    @Override
    public OperationType getType() {
        return OperationType.REMOVE_METHOD;
    }
    
    @Override
    public String getDescription() {
        return "Remove method " + methodQualifiedName;
    }
    
    public String getMethodQualifiedName() {
        return methodQualifiedName;
    }
    
    public static RemoveMethodOperation of(String methodQualifiedName) {
        return new RemoveMethodOperation(methodQualifiedName);
    }
}
