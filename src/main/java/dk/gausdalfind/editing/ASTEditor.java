package dk.gausdalfind.editing;

import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.Edge;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.model.EdgeTypes;
import dk.gausdalfind.parser.NodeIdGenerator;

import java.util.*;

/**
 * Core AST mutation interface for adding/removing/replacing nodes.
 * 
 * This class provides the capability for GausVibe to edit its own codebase
 * through structured AST operations rather than direct file editing.
 */
public class ASTEditor {
    
    private final Graph graph;
    private final ChangeTracker changeTracker;
    
    /**
     * Creates a new ASTEditor for the given graph.
     */
    public ASTEditor(Graph graph) {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        this.changeTracker = new ChangeTracker();
    }
    
    /**
     * Applies an operation to the graph.
     * 
     * @param operation the operation to apply
     * @return the result of the operation
     */
    public OperationResult apply(Operation operation) {
        Objects.requireNonNull(operation, "Operation cannot be null");
        
        try {
            switch (operation.getType()) {
                case ADD_METHOD:
                    return applyAddMethod((AddMethodOperation) operation);
                case REMOVE_METHOD:
                    return applyRemoveMethod((RemoveMethodOperation) operation);
                case REPLACE_METHOD_BODY:
                    return applyReplaceMethodBody((ReplaceMethodBodyOperation) operation);
                case ADD_FIELD:
                    return applyAddField((AddFieldOperation) operation);
                case REMOVE_FIELD:
                    return applyRemoveField((RemoveFieldOperation) operation);
                case ADD_IMPORT:
                    return applyAddImport((AddImportOperation) operation);
                case REMOVE_IMPORT:
                    return applyRemoveImport((RemoveImportOperation) operation);
                default:
                    return OperationResult.failure("Unknown operation type: " + operation.getType());
            }
        } catch (Exception e) {
            return OperationResult.failure("Error applying operation: " + e.getMessage());
        }
    }
    
    /**
     * Applies multiple operations to the graph.
     */
    public List<OperationResult> applyAll(List<Operation> operations) {
        List<OperationResult> results = new ArrayList<>();
        for (Operation op : operations) {
            results.add(apply(op));
        }
        return results;
    }
    
    // ==================== Operation Implementations ====================
    
    private OperationResult applyAddMethod(AddMethodOperation op) {
        // Find the target class
        Optional<ClassNode> targetClass = graph.getIndexes().getClassByQualifiedName(op.getTargetClass());
        if (targetClass.isEmpty()) {
            return OperationResult.failure("Class not found: " + op.getTargetClass());
        }
        
        // Create the method node
        MethodNode methodNode = createMethodNode(op, targetClass.get());
        graph.addNode(methodNode);
        
        // Add HAS_METHOD edge
        graph.addEdge(new Edge(
            targetClass.get().getId(),
            methodNode.getId(),
            EdgeTypes.HAS_METHOD
        ));
        
        // Track the change
        changeTracker.trackAddition(methodNode.getId(), "METHOD");
        
        return OperationResult.success(methodNode.getId());
    }
    
    private MethodNode createMethodNode(AddMethodOperation op, ClassNode targetClass) {
        String qualifiedName = op.getTargetClass() + "." + op.getName();
        String id = NodeIdGenerator.forDeclaration(
            NodeIdGenerator.NodeType.METHOD, 
            qualifiedName + "#" + op.getSignature()
        );
        
        return new MethodNode(
            id,
            op.getName(),
            op.getSignature(),
            qualifiedName,
            op.getReturnType(),
            op.getModifiers(),
            op.isConstructor(),
            op.isStatic(),
            op.isAbstract(),
            op.getThrownExceptions(),
            targetClass.getFile(),  // file path from parent class
            null, // start position
            null  // end position
        );
    }
    
    private OperationResult applyRemoveMethod(RemoveMethodOperation op) {
        // Find the method by qualified name
        Optional<MethodNode> method = graph.getIndexes().getMethodByQualifiedName(op.getMethodQualifiedName());
        if (method.isEmpty()) {
            return OperationResult.failure("Method not found: " + op.getMethodQualifiedName());
        }
        
        // Remove all edges to/from this method
        graph.removeEdgesFrom(method.get().getId());
        graph.removeEdgesTo(method.get().getId());
        
        // Remove the node
        graph.removeNode(method.get().getId());
        
        // Track the change
        changeTracker.trackRemoval(method.get().getId(), "METHOD");
        
        return OperationResult.success(method.get().getId());
    }
    
    private OperationResult applyReplaceMethodBody(ReplaceMethodBodyOperation op) {
        // Find the method
        Optional<MethodNode> method = graph.getIndexes().getMethodByQualifiedName(op.getMethodQualifiedName());
        if (method.isEmpty()) {
            return OperationResult.failure("Method not found: " + op.getMethodQualifiedName());
        }
        
        // For now, we'll just track that the body was replaced
        // In a full implementation, we'd parse the new body and replace the subtree
        changeTracker.trackModification(method.get().getId(), "METHOD", "body", op.getNewBody());
        
        return OperationResult.success(method.get().getId());
    }
    
    private OperationResult applyAddField(AddFieldOperation op) {
        // Find the target class
        Optional<ClassNode> targetClass = graph.getIndexes().getClassByQualifiedName(op.getTargetClass());
        if (targetClass.isEmpty()) {
            return OperationResult.failure("Class not found: " + op.getTargetClass());
        }
        
        // Create the field node
        FieldNode fieldNode = createFieldNode(op, targetClass.get());
        graph.addNode(fieldNode);
        
        // Add HAS_FIELD edge
        graph.addEdge(new Edge(
            targetClass.get().getId(),
            fieldNode.getId(),
            EdgeTypes.HAS_FIELD
        ));
        
        // Track the change
        changeTracker.trackAddition(fieldNode.getId(), "FIELD");
        
        return OperationResult.success(fieldNode.getId());
    }
    
    private FieldNode createFieldNode(AddFieldOperation op, ClassNode targetClass) {
        String qualifiedName = op.getTargetClass() + "." + op.getName();
        String id = NodeIdGenerator.forDeclaration(
            NodeIdGenerator.NodeType.FIELD, 
            qualifiedName
        );
        
        return new FieldNode(
            id,
            op.getName(),
            qualifiedName,
            op.getType(),
            op.getModifiers(),
            op.isStatic(),
            op.isFinal(),
            targetClass.getFile(),  // file path from parent class
            null, // start position
            null  // end position
        );
    }
    
    private OperationResult applyRemoveField(RemoveFieldOperation op) {
        // Find the field by qualified name
        Optional<FieldNode> field = graph.getIndexes().getFieldByQualifiedName(op.getFieldQualifiedName());
        if (field.isEmpty()) {
            return OperationResult.failure("Field not found: " + op.getFieldQualifiedName());
        }
        
        // Remove edges
        graph.removeEdgesFrom(field.get().getId());
        graph.removeEdgesTo(field.get().getId());
        
        // Remove the node
        graph.removeNode(field.get().getId());
        
        // Track the change
        changeTracker.trackRemoval(field.get().getId(), "FIELD");
        
        return OperationResult.success(field.get().getId());
    }
    
    private OperationResult applyAddImport(AddImportOperation op) {
        // Imports are typically handled at the file level
        // For now, we'll track this as a modification
        changeTracker.trackModification(op.getFile(), "FILE", "imports", op.getImportStatement());
        return OperationResult.success("Import added: " + op.getImportStatement());
    }
    
    private OperationResult applyRemoveImport(RemoveImportOperation op) {
        // Track import removal
        changeTracker.trackModification(op.getFile(), "FILE", "imports", null);
        return OperationResult.success("Import removed: " + op.getImportStatement());
    }
    
    // ==================== Getters ====================
    
    /**
     * Returns the change tracker.
     */
    public ChangeTracker getChangeTracker() {
        return changeTracker;
    }
    
    /**
     * Returns the graph.
     */
    public Graph getGraph() {
        return graph;
    }
}
