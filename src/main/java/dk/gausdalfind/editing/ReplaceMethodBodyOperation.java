package dk.gausdalfind.editing;

import java.util.Objects;

/**
 * Operation to replace the body of a method.
 */
public class ReplaceMethodBodyOperation implements Operation {
    
    private final String methodQualifiedName;
    private final String newBody;
    
    public ReplaceMethodBodyOperation(String methodQualifiedName, String newBody) {
        this.methodQualifiedName = Objects.requireNonNull(methodQualifiedName);
        this.newBody = Objects.requireNonNull(newBody);
    }
    
    @Override
    public OperationType getType() {
        return OperationType.REPLACE_METHOD_BODY;
    }
    
    @Override
    public String getDescription() {
        return "Replace body of method " + methodQualifiedName;
    }
    
    public String getMethodQualifiedName() {
        return methodQualifiedName;
    }
    
    public String getNewBody() {
        return newBody;
    }
    
    public static ReplaceMethodBodyOperation of(String methodQualifiedName, String newBody) {
        return new ReplaceMethodBodyOperation(methodQualifiedName, newBody);
    }
}
