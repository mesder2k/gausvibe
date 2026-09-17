package dk.gausdalfind.editing;

import java.util.Objects;

/**
 * Operation to remove a field from a class.
 */
public class RemoveFieldOperation implements Operation {
    
    private final String fieldQualifiedName;
    
    public RemoveFieldOperation(String fieldQualifiedName) {
        this.fieldQualifiedName = Objects.requireNonNull(fieldQualifiedName);
    }
    
    @Override
    public OperationType getType() {
        return OperationType.REMOVE_FIELD;
    }
    
    @Override
    public String getDescription() {
        return "Remove field " + fieldQualifiedName;
    }
    
    public String getFieldQualifiedName() {
        return fieldQualifiedName;
    }
    
    public static RemoveFieldOperation of(String fieldQualifiedName) {
        return new RemoveFieldOperation(fieldQualifiedName);
    }
}
