package dk.gausdalfind.editing;

import java.util.*;

/**
 * Tracks which files and nodes have been modified.
 */
public class ChangeTracker {
    
    private final Set<String> modifiedFiles = new HashSet<>();
    private final Set<String> addedNodes = new HashSet<>();
    private final Set<String> removedNodes = new HashSet<>();
    private final Map<String, List<Change>> modifications = new HashMap<>();
    
    /**
     * Tracks a node addition.
     */
    public void trackAddition(String nodeId, String nodeType) {
        addedNodes.add(nodeId);
        logChange("ADD", nodeType, nodeId, null);
    }
    
    /**
     * Tracks a node removal.
     */
    public void trackRemoval(String nodeId, String nodeType) {
        removedNodes.add(nodeId);
        logChange("REMOVE", nodeType, nodeId, null);
    }
    
    /**
     * Tracks a node modification.
     */
    public void trackModification(String nodeId, String nodeType, String property, Object value) {
        modifications.computeIfAbsent(nodeId, k -> new ArrayList<>())
            .add(new Change("MODIFY", nodeType, property, value));
        logChange("MODIFY", nodeType, nodeId, property + "=" + value);
    }
    
    /**
     * Tracks a file modification.
     */
    public void trackFileModification(String filePath) {
        modifiedFiles.add(filePath);
    }
    
    /**
     * Logs a change for debugging.
     */
    private void logChange(String action, String nodeType, String nodeId, String details) {
        System.out.println("[CHANGE] " + action + " " + nodeType + " " + nodeId + 
            (details != null ? " (" + details + ")" : ""));
    }
    
    // ==================== Getters ====================
    
    /**
     * Returns all modified files.
     */
    public Set<String> getModifiedFiles() {
        return Set.copyOf(modifiedFiles);
    }
    
    /**
     * Returns all added node IDs.
     */
    public Set<String> getAddedNodes() {
        return Set.copyOf(addedNodes);
    }
    
    /**
     * Returns all removed node IDs.
     */
    public Set<String> getRemovedNodes() {
        return Set.copyOf(removedNodes);
    }
    
    /**
     * Returns all modifications.
     */
    public Map<String, List<Change>> getModifications() {
        Map<String, List<Change>> copy = new HashMap<>();
        modifications.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return copy;
    }
    
    /**
     * Returns all changes as a flattened list.
     */
    public List<Change> getAllChanges() {
        List<Change> all = new ArrayList<>();
        addedNodes.forEach(nodeId -> 
            all.add(new Change("ADD", "NODE", nodeId, null)));
        removedNodes.forEach(nodeId -> 
            all.add(new Change("REMOVE", "NODE", nodeId, null)));
        modifications.values().forEach(changes -> all.addAll(changes));
        return all;
    }
    
    /**
     * Returns whether any changes have been tracked.
     */
    public boolean hasChanges() {
        return !addedNodes.isEmpty() || !removedNodes.isEmpty() || !modifications.isEmpty();
    }
    
    /**
     * Clears all tracked changes.
     */
    public void clear() {
        modifiedFiles.clear();
        addedNodes.clear();
        removedNodes.clear();
        modifications.clear();
    }
    
    // ==================== Change Record ====================
    
    /**
     * Represents a single change.
     */
    public static class Change {
        private final String action;
        private final String nodeType;
        private final String nodeId;
        private final Object value;
        
        public Change(String action, String nodeType, String nodeId, Object value) {
            this.action = action;
            this.nodeType = nodeType;
            this.nodeId = nodeId;
            this.value = value;
        }
        
        public String getAction() {
            return action;
        }
        
        public String getNodeType() {
            return nodeType;
        }
        
        public String getNodeId() {
            return nodeId;
        }
        
        public Object getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return action + " " + nodeType + " " + nodeId + 
                (value != null ? "=" + value : "");
        }
    }
}
