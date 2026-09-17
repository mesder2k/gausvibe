package dk.gausdalfind.cli;

import dk.gausdalfind.editing.*;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.serializer.JsonSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * CLI command for AST-based editing of Java code.
 * 
 * This command allows editing a graph by applying AST operations.
 * It's the Java-side implementation for the edit-graph Vibe tool.
 */
public class EditCommand {
    
    private final Graph graph;
    private final Path graphFile;
    private final boolean dryRun;
    private final String outputFormat;
    private final boolean verbose;
    
    /**
     * Creates a new EditCommand.
     */
    public EditCommand(Graph graph, Path graphFile, boolean dryRun, 
                      String outputFormat, boolean verbose) {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        this.graphFile = graphFile;
        this.dryRun = dryRun;
        this.outputFormat = outputFormat != null ? outputFormat : "diff";
        this.verbose = verbose;
    }
    
    /**
     * Executes the edit command with the given operations.
     */
    public String execute(List<Operation> operations) {
        if (operations.isEmpty()) {
            return "No operations to apply";
        }
        
        ASTEditor editor = new ASTEditor(graph);
        List<OperationResult> results = editor.applyAll(operations);
        
        if (verbose) {
            System.out.println("Applied " + operations.size() + " operations");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + results.get(i));
            }
        }
        
        // Format output
        switch (outputFormat) {
            case "json":
                return formatAsJson(results, editor);
            case "diff":
                return formatAsDiff(editor);
            case "summary":
                return formatAsSummary(results);
            default:
                return formatAsText(results);
        }
    }
    
    /**
     * Formats results as JSON.
     */
    private String formatAsJson(List<OperationResult> results, ASTEditor editor) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", results.stream().allMatch(OperationResult::isSuccess));
        output.put("appliedCount", results.size());
        output.put("successCount", results.stream().filter(OperationResult::isSuccess).count());
        output.put("failureCount", results.stream().filter(r -> !r.isSuccess()).count());
        
        // Add change summary
        ChangeTracker tracker = editor.getChangeTracker();
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("modifiedFiles", tracker.getModifiedFiles());
        changes.put("addedNodes", tracker.getAddedNodes());
        changes.put("removedNodes", tracker.getRemovedNodes());
        output.put("changes", changes);
        
        // Add individual results
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (OperationResult result : results) {
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("success", result.isSuccess());
            resultMap.put("message", result.getMessage());
            resultMap.put("nodeId", result.getNodeId());
            resultList.add(resultMap);
        }
        output.put("results", resultList);
        
        // Serialize to JSON
        try {
            JsonSerializer serializer = new JsonSerializer();
            return serializer.toJson(output);
        } catch (Exception e) {
            return "Error formatting JSON: " + e.getMessage();
        }
    }
    
    /**
     * Formats changes as a diff.
     */
    private String formatAsDiff(ASTEditor editor) {
        ChangeTracker tracker = editor.getChangeTracker();
        StringBuilder sb = new StringBuilder();
        
        sb.append("Diff of changes:\n\n");
        
        // Added nodes
        for (String nodeId : tracker.getAddedNodes()) {
            sb.append("+ Added node: ").append(nodeId).append("\n");
        }
        
        // Removed nodes
        for (String nodeId : tracker.getRemovedNodes()) {
            sb.append("- Removed node: ").append(nodeId).append("\n");
        }
        
        // Modifications
        for (Map.Entry<String, List<ChangeTracker.Change>> entry : tracker.getModifications().entrySet()) {
            for (ChangeTracker.Change change : entry.getValue()) {
                sb.append("~ Modified ").append(change.getNodeType()).append(" ")
                  .append(change.getNodeId()).append(": ")
                  .append(change.getValue()).append("\n");
            }
        }
        
        if (tracker.getAddedNodes().isEmpty() && 
            tracker.getRemovedNodes().isEmpty() && 
            tracker.getModifications().isEmpty()) {
            sb.append("No changes\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Formats results as a summary.
     */
    private String formatAsSummary(List<OperationResult> results) {
        long successCount = results.stream().filter(OperationResult::isSuccess).count();
        long failureCount = results.stream().filter(r -> !r.isSuccess()).count();
        
        return String.format(
            "Edit Summary: Applied=%d, Succeeded=%d, Failed=%d",
            results.size(), successCount, failureCount
        );
    }
    
    /**
     * Formats results as plain text.
     */
    private String formatAsText(List<OperationResult> results) {
        StringBuilder sb = new StringBuilder();
        for (OperationResult result : results) {
            sb.append(result.toString()).append("\n");
        }
        return sb.toString();
    }
    
    // ==================== Static Factory Methods ====================
    
    /**
     * Parses an operation from a JSON-like string representation.
     */
    public static Operation parseOperation(String opString) {
        // Simple parsing - in a full implementation, use proper JSON parsing
        opString = opString.trim();
        if (opString.startsWith("{" ) && opString.endsWith("}")) {
            // Try to parse as JSON
            try {
                return parseJsonOperation(opString);
            } catch (Exception e) {
                System.err.println("Error parsing JSON operation: " + e.getMessage());
            }
        }
        
        // Fallback: try to parse as simple format: "type:target:value"
        String[] parts = opString.split(":", 3);
        if (parts.length >= 2) {
            OperationType type = OperationType.valueOf(parts[0].toUpperCase());
            switch (type) {
                case ADD_METHOD:
                    // Format: ADD_METHOD:targetClass:methodName
                    if (parts.length >= 3) {
                        String[] subParts = parts[2].split("\\|");
                        String methodName = subParts[0];
                        String returnType = subParts.length > 1 ? subParts[1] : "void";
                        String body = subParts.length > 2 ? subParts[2] : "";
                        
                        return AddMethodOperation.builder()
                            .targetClass(parts[1])
                            .name(methodName)
                            .returnType(returnType)
                            .body(body)
                            .build();
                    }
                    break;
                case REMOVE_METHOD:
                    return RemoveMethodOperation.of(parts[1]);
                case ADD_FIELD:
                    if (parts.length >= 3) {
                        String[] subParts = parts[2].split("\\:");
                        String fieldName = subParts[0];
                        String fieldType = subParts.length > 1 ? subParts[1] : "Object";
                        return AddFieldOperation.builder()
                            .targetClass(parts[1])
                            .name(fieldName)
                            .type(fieldType)
                            .build();
                    }
                    break;
                case REMOVE_FIELD:
                    return RemoveFieldOperation.of(parts[1]);
                default:
                    break;
            }
        }
        
        throw new IllegalArgumentException("Invalid operation format: " + opString);
    }
    
    private static Operation parseJsonOperation(String json) {
        // Placeholder for JSON parsing
        // In a full implementation, use Gson or similar
        return null;
    }
    
    /**
     * Parses multiple operations from a JSON array string.
     */
    public static List<Operation> parseOperations(String operationsJson) {
        List<Operation> operations = new ArrayList<>();
        
        // Simple parsing - split by lines or newlines
        String[] lines = operationsJson.split("[\[\]\{\}]+");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                try {
                    operations.add(parseOperation(line));
                } catch (Exception e) {
                    System.err.println("Skipping invalid operation: " + line);
                }
            }
        }
        
        return operations;
    }
}
