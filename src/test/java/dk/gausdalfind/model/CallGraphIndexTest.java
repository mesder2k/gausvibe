package dk.gausdalfind.model;

import dk.gausdalfind.model.declaration.MethodNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CallGraphIndex.
 * Tests cover:
 * - Direct call indexing and querying
 * - Transitive closure
 * - Call path finding
 * - Edge cases
 */
class CallGraphIndexTest {
    
    private CallGraphIndex index;
    
    @BeforeEach
    void setUp() {
        index = new CallGraphIndex();
    }
    
    @AfterEach
    void tearDown() {
        index = null;
    }
    
    // ==================== BASIC INDEXING ====================
    
    @Test
    void testIndexSingleCall() {
        index.indexCall("methodA", "methodB");
        
        Set<String> callees = index.getCallees("methodA");
        assertEquals(1, callees.size());
        assertTrue(callees.contains("methodB"));
        
        Set<String> callers = index.getCallers("methodB");
        assertEquals(1, callers.size());
        assertTrue(callers.contains("methodA"));
    }
    
    @Test
    void testIndexMultipleCalls() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodA", "methodC");
        index.indexCall("methodB", "methodC");
        
        Set<String> calleesA = index.getCallees("methodA");
        assertEquals(2, calleesA.size());
        assertTrue(calleesA.contains("methodB"));
        assertTrue(calleesA.contains("methodC"));
        
        Set<String> callersC = index.getCallers("methodC");
        assertEquals(2, callersC.size());
        assertTrue(callersC.contains("methodA"));
        assertTrue(callersC.contains("methodB"));
    }
    
    @Test
    void testIndexMultipleCallsFromList() {
        List<Edge> edges = Arrays.asList(
            new Edge("methodA", "methodB", EdgeTypes.CALLS),
            new Edge("methodA", "methodC", EdgeTypes.CALLS),
            new Edge("methodB", "methodC", EdgeTypes.CALLS)
        );
        
        index.indexCalls(edges);
        
        assertEquals(2, index.getCallees("methodA").size());
        assertEquals(2, index.getCallers("methodC").size());
    }
    
    @Test
    void testIndexDuplicateCall() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodA", "methodB"); // Duplicate
        
        Set<String> callees = index.getCallees("methodA");
        assertEquals(1, callees.size()); // Should not duplicate
        assertTrue(callees.contains("methodB"));
    }
    
    @Test
    void testIndexNullCalls() {
        index.indexCall(null, "methodB");
        index.indexCall("methodA", null);
        index.indexCall(null, null);
        index.indexCall("", "methodB");
        index.indexCall("methodA", "");
        
        assertEquals(0, index.getCallCount());
    }
    
    // ==================== UNINDEXING ====================
    
    @Test
    void testUnindexCall() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodA", "methodC");
        
        assertEquals(2, index.getCallees("methodA").size());
        
        index.unindexCall("methodA", "methodB");
        
        Set<String> callees = index.getCallees("methodA");
        assertEquals(1, callees.size());
        assertTrue(callees.contains("methodC"));
    }
    
    @Test
    void testUnindexMethod() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodA", "methodC");
        index.indexCall("methodD", "methodB");
        
        // methodB has 2 callers: methodA and methodD
        assertEquals(2, index.getCallers("methodB").size());
        
        index.unindexMethod("methodA");
        
        // methodB should now have 1 caller: methodD
        assertEquals(1, index.getCallers("methodB").size());
        assertTrue(index.getCallers("methodB").contains("methodD"));
        
        // methodA should have no entries
        assertEquals(0, index.getCallees("methodA").size());
    }
    
    @Test
    void testClear() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodC", "methodD");
        
        assertEquals(2, index.getCallCount());
        
        index.clear();
        
        assertEquals(0, index.getCallCount());
        assertEquals(0, index.getMethodCount());
    }
    
    // ==================== DIRECT QUERIES ====================
    
    @Test
    void testGetCallersEmpty() {
        Set<String> callers = index.getCallers("nonexistent");
        assertTrue(callers.isEmpty());
    }
    
    @Test
    void testGetCalleesEmpty() {
        Set<String> callees = index.getCallees("nonexistent");
        assertTrue(callees.isEmpty());
    }
    
    @Test
    void testGetCallersNull() {
        Set<String> callers = index.getCallers(null);
        assertTrue(callers.isEmpty());
    }
    
    @Test
    void testGetCalleesNull() {
        Set<String> callees = index.getCallees(null);
        assertTrue(callees.isEmpty());
    }
    
    @Test
    void testGetCallersEmptyString() {
        Set<String> callers = index.getCallers("");
        assertTrue(callers.isEmpty());
    }
    
    @Test
    void testHasDirectCall() {
        index.indexCall("methodA", "methodB");
        
        assertTrue(index.hasDirectCall("methodA", "methodB"));
        assertFalse(index.hasDirectCall("methodB", "methodA"));
        assertFalse(index.hasDirectCall("methodA", "methodC"));
        assertFalse(index.hasDirectCall("methodC", "methodB"));
    }
    
    @Test
    void testHasDirectCallNull() {
        assertFalse(index.hasDirectCall(null, "methodB"));
        assertFalse(index.hasDirectCall("methodA", null));
        assertFalse(index.hasDirectCall(null, null));
    }
    
    // ==================== TRANSITIVE QUERIES ====================
    
    @Test
    void testTransitiveCallers() {
        // methodA -> methodB -> methodC
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        
        // methodC has no direct callers, but has transitive caller methodA
        Set<String> transitiveCallers = index.getTransitiveCallers("methodC");
        
        assertTrue(transitiveCallers.contains("methodA"));
        assertTrue(transitiveCallers.contains("methodB")); // Direct caller
    }
    
    @Test
    void testTransitiveCallees() {
        // methodA -> methodB -> methodC
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        
        // methodA transitively calls methodC
        Set<String> transitiveCallees = index.getTransitiveCallees("methodA");
        
        assertTrue(transitiveCallees.contains("methodB")); // Direct callee
        assertTrue(transitiveCallees.contains("methodC")); // Transitive callee
    }
    
    @Test
    void testTransitiveWithDepthLimit() {
        // Create a chain: methodA -> methodB -> methodC -> methodD -> methodE
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        index.indexCall("methodC", "methodD");
        index.indexCall("methodD", "methodE");
        
        // With default max depth of 10, should find all
        Set<String> allTransitive = index.getTransitiveCallees("methodA");
        assertTrue(allTransitive.contains("methodE"));
    }
    
    @Test
    void testTransitiveCallersWithDepthLimit() {
        CallGraphIndex limitedIndex = new CallGraphIndex(2); // Max depth of 2
        
        // Create a chain: methodA -> methodB -> methodC -> methodD
        limitedIndex.indexCall("methodA", "methodB");
        limitedIndex.indexCall("methodB", "methodC");
        limitedIndex.indexCall("methodC", "methodD");
        
        // methodD's transitive callers should include methodA (depth 3) and methodB (depth 2) and methodC (depth 1)
        // But with max depth 2, methodA should not be included
        Set<String> transitiveCallers = limitedIndex.getTransitiveCallers("methodD");
        
        assertTrue(transitiveCallers.contains("methodC"));
        assertTrue(transitiveCallers.contains("methodB"));
        // methodA is at depth 3, so it should not be included with max depth 2
        assertFalse(transitiveCallers.contains("methodA"));
    }
    
    @Test
    void testTransitiveCalleesWithDepthLimit() {
        CallGraphIndex limitedIndex = new CallGraphIndex(2); // Max depth of 2
        
        // Create a chain: methodA -> methodB -> methodC -> methodD
        limitedIndex.indexCall("methodA", "methodB");
        limitedIndex.indexCall("methodB", "methodC");
        limitedIndex.indexCall("methodC", "methodD");
        
        // methodA's transitive callees should include methodB (depth 1), methodC (depth 2), but not methodD (depth 3)
        Set<String> transitiveCallees = limitedIndex.getTransitiveCallees("methodA");
        
        assertTrue(transitiveCallees.contains("methodB"));
        assertTrue(transitiveCallees.contains("methodC"));
        assertFalse(transitiveCallees.contains("methodD"));
    }
    
    @Test
    void testTransitiveEmpty() {
        Set<String> transitiveCallers = index.getTransitiveCallers("nonexistent");
        assertTrue(transitiveCallers.isEmpty());
        
        Set<String> transitiveCallees = index.getTransitiveCallees("nonexistent");
        assertTrue(transitiveCallees.isEmpty());
    }
    
    // ==================== COMBINED QUERIES ====================
    
    @Test
    void testGetCallersTransitive() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        
        // Direct callers of methodC
        Set<String> direct = index.getCallers("methodC", false);
        assertEquals(1, direct.size());
        assertTrue(direct.contains("methodB"));
        
        // Transitive callers of methodC
        Set<String> transitive = index.getCallers("methodC", true);
        assertEquals(2, transitive.size());
        assertTrue(transitive.contains("methodA"));
        assertTrue(transitive.contains("methodB"));
    }
    
    @Test
    void testGetCalleesTransitive() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        
        // Direct callees of methodA
        Set<String> direct = index.getCallees("methodA", false);
        assertEquals(1, direct.size());
        assertTrue(direct.contains("methodB"));
        
        // Transitive callees of methodA
        Set<String> transitive = index.getCallees("methodA", true);
        assertEquals(2, transitive.size());
        assertTrue(transitive.contains("methodB"));
        assertTrue(transitive.contains("methodC"));
    }
    
    // ==================== CALL PATH FINDING ====================
    
    @Test
    void testFindCallPathsDirect() {
        index.indexCall("methodA", "methodB");
        
        List<List<String>> paths = index.findCallPaths("methodA", "methodB", 10);
        
        assertEquals(1, paths.size());
        assertEquals(2, paths.get(0).size());
        assertEquals("methodA", paths.get(0).get(0));
        assertEquals("methodB", paths.get(0).get(1));
    }
    
    @Test
    void testFindCallPathsIndirect() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        
        List<List<String>> paths = index.findCallPaths("methodA", "methodC", 10);
        
        assertEquals(1, paths.size());
        assertEquals(3, paths.get(0).size());
        assertEquals("methodA", paths.get(0).get(0));
        assertEquals("methodB", paths.get(0).get(1));
        assertEquals("methodC", paths.get(0).get(2));
    }
    
    @Test
    void testFindCallPathsMultiple() {
        // methodA -> methodB -> methodD
        // methodA -> methodC -> methodD
        index.indexCall("methodA", "methodB");
        index.indexCall("methodA", "methodC");
        index.indexCall("methodB", "methodD");
        index.indexCall("methodC", "methodD");
        
        List<List<String>> paths = index.findCallPaths("methodA", "methodD", 10);
        
        assertEquals(2, paths.size());
    }
    
    @Test
    void testFindCallPathsNoPath() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodC", "methodD");
        
        List<List<String>> paths = index.findCallPaths("methodA", "methodD", 10);
        
        assertTrue(paths.isEmpty());
    }
    
    @Test
    void testFindCallPathsWithCycle() {
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        index.indexCall("methodC", "methodB"); // Cycle
        
        List<List<String>> paths = index.findCallPaths("methodA", "methodC", 10);
        
        // Should find path without cycle
        assertFalse(paths.isEmpty());
        // Should not include cycles in path
        for (List<String> path : paths) {
            assertTrue(isPathValid(path));
        }
    }
    
    @Test
    void testFindCallPathsWithDepthLimit() {
        // methodA -> methodB -> methodC -> methodD
        index.indexCall("methodA", "methodB");
        index.indexCall("methodB", "methodC");
        index.indexCall("methodC", "methodD");
        
        // With max depth 2, should not reach methodD
        List<List<String>> paths = index.findCallPaths("methodA", "methodD", 2);
        assertTrue(paths.isEmpty());
        
        // With max depth 3, should reach methodD
        paths = index.findCallPaths("methodA", "methodD", 3);
        assertEquals(1, paths.size());
    }
    
    @Test
    void testFindCallPathsNull() {
        List<List<String>> paths1 = index.findCallPaths(null, "methodB", 10);
        List<List<String>> paths2 = index.findCallPaths("methodA", null, 10);
        List<List<String>> paths3 = index.findCallPaths(null, null, 10);
        
        assertTrue(paths1.isEmpty());
        assertTrue(paths2.isEmpty());
        assertTrue(paths3.isEmpty());
    }
    
    // ==================== STATISTICS ====================
    
    @Test
    void testGetCallCount() {
        assertEquals(0, index.getCallCount());
        
        index.indexCall("methodA", "methodB");
        assertEquals(1, index.getCallCount());
        
        index.indexCall("methodA", "methodC");
        assertEquals(2, index.getCallCount());
    }
    
    @Test
    void testGetMethodCount() {
        assertEquals(0, index.getMethodCount());
        
        index.indexCall("methodA", "methodB");
        // methodA and methodB are both new methods
        assertTrue(index.getMethodCount() >= 2);
    }
    
    @Test
    void testGetMaxTransitiveDepth() {
        assertEquals(10, index.getMaxTransitiveDepth()); // Default
        
        CallGraphIndex customIndex = new CallGraphIndex(5);
        assertEquals(5, customIndex.getMaxTransitiveDepth());
    }
    
    @Test
    void testGetStatistics() {
        String stats = index.getStatistics();
        assertTrue(stats.contains("CallGraphIndex"));
        assertTrue(stats.contains("methods=0"));
        assertTrue(stats.contains("calls=0"));
    }
    
    // ==================== CONSTRUCTOR VALIDATION ====================
    
    @Test
    void testInvalidMaxTransitiveDepth() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CallGraphIndex(0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new CallGraphIndex(-1);
        });
    }
    
    // ==================== NODE-BASED METHODS ====================
    
    @Test
    void testGetCallerNodes() {
        index.indexCall("methodA", "methodB");
        
        // Create a graph with real method nodes
        Graph graph = new Graph();
        graph.addNode(methodNode("methodA"));
        graph.addNode(methodNode("methodB"));
        
        Set<MethodNode> callers = index.getCallerNodes(methodNode("methodB"), graph);
        
        assertEquals(1, callers.size());
        assertEquals("methodA", callers.iterator().next().getId());
    }
    
    @Test
    void testGetCalleeNodes() {
        index.indexCall("methodA", "methodB");
        
        Graph graph = new Graph();
        graph.addNode(methodNode("methodA"));
        graph.addNode(methodNode("methodB"));
        
        Set<MethodNode> callees = index.getCalleeNodes(methodNode("methodA"), graph);
        
        assertEquals(1, callees.size());
        assertEquals("methodB", callees.iterator().next().getId());
    }
    
    // ==================== BUILDER ====================
    
    @Test
    void testBuilderDefault() {
        CallGraphIndex builtIndex = CallGraphIndex.builder().build();
        assertNotNull(builtIndex);
        assertEquals(10, builtIndex.getMaxTransitiveDepth());
    }
    
    @Test
    void testBuilderWithMaxDepth() {
        CallGraphIndex builtIndex = CallGraphIndex.builder()
            .setMaxTransitiveDepth(5)
            .build();
        
        assertEquals(5, builtIndex.getMaxTransitiveDepth());
    }
    
    // ==================== HELPER METHODS ====================
    
    private boolean isPathValid(List<String> path) {
        Set<String> seen = new HashSet<>();
        for (String node : path) {
            if (seen.contains(node)) {
                return false; // Cycle detected
            }
            seen.add(node);
        }
        return true;
    }
    
    /**
     * Creates a minimal MethodNode for the node-based tests.
     */
    private static MethodNode methodNode(String id) {
        return new MethodNode(
            id, id, id + "()", id, "void",
            Set.of(), false, false, List.of(),
            null, null, null
        );
    }
}
