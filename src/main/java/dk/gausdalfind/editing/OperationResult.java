package dk.gausdalfind.editing;

import java.util.Objects;

/**
 * Result of an AST operation.
 */
public class OperationResult {
    
    private final boolean success;
    private final String message;
    private final String nodeId;
    
    private OperationResult(boolean success, String message, String nodeId) {
        this.success = success;
        this.message = message;
        this.nodeId = nodeId;
    }
    
    /**
     * Creates a successful result.
     */
    public static OperationResult success(String nodeId) {
        return new OperationResult(true, "Operation completed successfully", nodeId);
    }
    
    /**
     * Creates a successful result with a custom message.
     */
    public static OperationResult success(String message, String nodeId) {
        return new OperationResult(true, message, nodeId);
    }
    
    /**
     * Creates a failed result.
     */
    public static OperationResult failure(String message) {
        return new OperationResult(false, message, null);
    }
    
    // ==================== Getters ====================
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public String toString() {
        if (success) {
            return "SUCCESS: " + message + (nodeId != null ? " (node: " + nodeId + ")" : "");
        } else {
            return "FAILURE: " + message;
        }
    }
}
