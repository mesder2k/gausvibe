package dk.gausdalfind.editing;

import java.util.Objects;

/**
 * Operation to remove an import statement from a file.
 */
public class RemoveImportOperation implements Operation {
    
    private final String file;
    private final String importStatement;
    
    public RemoveImportOperation(String file, String importStatement) {
        this.file = Objects.requireNonNull(file);
        this.importStatement = Objects.requireNonNull(importStatement);
    }
    
    @Override
    public OperationType getType() {
        return OperationType.REMOVE_IMPORT;
    }
    
    @Override
    public String getDescription() {
        return "Remove import " + importStatement + " from " + file;
    }
    
    public String getFile() {
        return file;
    }
    
    public String getImportStatement() {
        return importStatement;
    }
    
    public static RemoveImportOperation of(String file, String importStatement) {
        return new RemoveImportOperation(file, importStatement);
    }
}
