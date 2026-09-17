package dk.gausdalfind.editing;

import java.util.Objects;

/**
 * Operation to add an import statement to a file.
 */
public class AddImportOperation implements Operation {
    
    private final String file;
    private final String importStatement;
    
    public AddImportOperation(String file, String importStatement) {
        this.file = Objects.requireNonNull(file);
        this.importStatement = Objects.requireNonNull(importStatement);
    }
    
    @Override
    public OperationType getType() {
        return OperationType.ADD_IMPORT;
    }
    
    @Override
    public String getDescription() {
        return "Add import " + importStatement + " to " + file;
    }
    
    public String getFile() {
        return file;
    }
    
    public String getImportStatement() {
        return importStatement;
    }
    
    public static AddImportOperation of(String file, String importStatement) {
        return new AddImportOperation(file, importStatement);
    }
}
