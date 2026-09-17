package dk.gausdalfind.editing;

import java.util.*;

/**
 * Base interface for all AST modification operations.
 * 
 * Operations are JSON-serializable and represent a single AST modification.
 */
public interface Operation {
    
    /**
     * Returns the type of operation.
     */
    OperationType getType();
    
    /**
     * Returns a unique identifier for this operation.
     */
    default String getId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Returns a description of this operation.
     */
    default String getDescription() {
        return getType().name();
    }
}
