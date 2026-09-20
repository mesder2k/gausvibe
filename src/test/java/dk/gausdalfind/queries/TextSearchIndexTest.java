package dk.gausdalfind.queries;

import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.declaration.ClassNode;
import dk.gausdalfind.model.declaration.MethodNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TextSearchIndex.
 * Tests cover:
 * - Tokenization (camelCase, snake_case, PascalCase)
 * - Indexing and unindexing
 * - AND, OR, and phrase searches
 * - Stop words filtering
 * - Token length filtering
 */
class TextSearchIndexTest {
    
    private TextSearchIndex index;
    private MockNode node1;
    private MockNode node2;
    private MockNode node3;
    
    @BeforeEach
    void setUp() {
        index = new TextSearchIndex();
        
        // Create mock nodes
        node1 = new MockNode("node1", "MyClass", "com.example.MyClass", 
            Path.of("/test/MyClass.java"), "class");
        node2 = new MockNode("node2", "calculateTotal", "com.example.MyClass#calculateTotal", 
            Path.of("/test/MyClass.java"), "method");
        node3 = new MockNode("node3", "getValue", "com.example.OtherClass#getValue", 
            Path.of("/test/OtherClass.java"), "method");
    }
    
    @AfterEach
    void tearDown() {
        index = null;
    }
    
    // ==================== TOKENIZATION TESTS ====================
    
    @Test
    void testTokenizeCamelCase() {
        Set<String> tokens = index.tokenize("calculateTotalAmount");
        assertTrue(tokens.contains("calculate"));
        assertTrue(tokens.contains("Total"));
        assertTrue(tokens.contains("Amount"));
        assertEquals(3, tokens.size());
    }
    
    @Test
    void testTokenizeSnakeCase() {
        Set<String> tokens = index.tokenize("calculate_total_amount");
        assertTrue(tokens.contains("calculate"));
        assertTrue(tokens.contains("total"));
        assertTrue(tokens.contains("amount"));
        assertEquals(3, tokens.size());
    }
    
    @Test
    void testTokenizePascalCase() {
        Set<String> tokens = index.tokenize("CalculateTotalAmount");
        assertTrue(tokens.contains("Calculate"));
        assertTrue(tokens.contains("Total"));
        assertTrue(tokens.contains("Amount"));
        assertEquals(3, tokens.size());
    }
    
    @Test
    void testTokenizeMixed() {
        Set<String> tokens = index.tokenize("getHTTPResponseCode");
        assertTrue(tokens.contains("get"));
        assertTrue(tokens.contains("HTTP"));
        assertTrue(tokens.contains("Response"));
        assertTrue(tokens.contains("Code"));
        assertEquals(4, tokens.size());
    }
    
    @Test
    void testTokenizeWithSpaces() {
        Set<String> tokens = index.tokenize("calculate total amount");
        assertTrue(tokens.contains("calculate"));
        assertTrue(tokens.contains("total"));
        assertTrue(tokens.contains("amount"));
        assertEquals(3, tokens.size());
    }
    
    @Test
    void testTokenizeEmpty() {
        Set<String> tokens1 = index.tokenize(null);
        Set<String> tokens2 = index.tokenize("");
        Set<String> tokens3 = index.tokenize("   ");
        
        assertTrue(tokens1.isEmpty());
        assertTrue(tokens2.isEmpty());
        assertTrue(tokens3.isEmpty());
    }
    
    @Test
    void testTokenizeSingleWord() {
        Set<String> tokens = index.tokenize("calculate");
        assertTrue(tokens.contains("calculate"));
        assertEquals(1, tokens.size());
    }
    
    // ==================== INDEXING TESTS ====================
    
    @Test
    void testIndexAndSearch() {
        index.index(node1);
        index.index(node2);
        index.index(node3);
        
        Set<String> results = index.search("MyClass");
        assertEquals(1, results.size());
        assertTrue(results.contains("node1"));
    }
    
    @Test
    void testIndexMultipleNodes() {
        List<Node> nodes = Arrays.asList(node1, node2, node3);
        index.index(nodes);
        
        assertEquals(3, index.getIndexedNodeCount());
    }
    
    @Test
    void testUnindex() {
        index.index(node1);
        index.index(node2);
        
        assertEquals(2, index.getIndexedNodeCount());
        
        index.unindex(node1);
        
        assertEquals(1, index.getIndexedNodeCount());
        assertFalse(index.search("MyClass").contains("node1"));
    }
    
    @Test
    void testUnindexMultiple() {
        List<Node> nodes = Arrays.asList(node1, node2, node3);
        index.index(nodes);
        
        assertEquals(3, index.getIndexedNodeCount());
        
        index.unindex(nodes);
        
        assertEquals(0, index.getIndexedNodeCount());
    }
    
    @Test
    void testReindex() {
        index.index(node1);
        
        // Modify node (simulate change)
        node1 = new MockNode("node1", "UpdatedClass", "com.example.UpdatedClass", 
            Path.of("/test/MyClass.java"), "class");
        
        index.reindex(node1);
        
        // Old token should be gone
        assertFalse(index.search("MyClass").contains("node1"));
        
        // New token should be present
        assertTrue(index.search("UpdatedClass").contains("node1"));
    }
    
    // ==================== SEARCH MODES ====================
    
    @Test
    void testSearchAnd() {
        index.index(node1);
        index.index(node2);
        
        // "MyClass" is in node1, "calculateTotal" is in node2
        // No node contains both, so AND should return empty
        Set<String> results = index.searchAnd(Arrays.asList("MyClass", "calculateTotal"));
        assertTrue(results.isEmpty());
        
        // "calculate" and "Total" are both in node2
        results = index.searchAnd(Arrays.asList("calculate", "Total"));
        assertEquals(1, results.size());
        assertTrue(results.contains("node2"));
    }
    
    @Test
    void testSearchOr() {
        index.index(node1);
        index.index(node2);
        index.index(node3);
        
        // "MyClass" is in node1, "calculateTotal" is in node2
        Set<String> results = index.searchOr(Arrays.asList("MyClass", "calculateTotal"));
        assertEquals(2, results.size());
        assertTrue(results.contains("node1"));
        assertTrue(results.contains("node2"));
    }
    
    @Test
    void testSearchPhrase() {
        index.index(node1);
        index.index(node2);
        
        // Phrase search for "calculate total" (AND of all tokens)
        Set<String> results = index.searchPhrase("calculate total");
        assertEquals(1, results.size());
        assertTrue(results.contains("node2")); // node2 has "calculateTotal"
    }
    
    @Test
    void testSearchQueryAnd() {
        index.index(node1);
        index.index(node2);
        
        // AND query
        Set<String> results = index.searchQuery("calculate Total");
        assertEquals(1, results.size());
        assertTrue(results.contains("node2"));
    }
    
    @Test
    void testSearchQueryOr() {
        index.index(node1);
        index.index(node2);
        index.index(node3);
        
        // OR query
        Set<String> results = index.searchQuery("calculate OR get");
        assertEquals(2, results.size());
        assertTrue(results.contains("node2"));
        assertTrue(results.contains("node3"));
    }
    
    @Test
    void testSearchQueryPhrase() {
        index.index(node2);
        
        // Phrase query
        Set<String> results = index.searchQuery("\"calculate total\"");
        assertEquals(1, results.size());
        assertTrue(results.contains("node2"));
    }
    
    @Test
    void testSearchEmpty() {
        Set<String> results1 = index.search(null);
        Set<String> results2 = index.search("");
        Set<String> results3 = index.search("   ");
        
        assertTrue(results1.isEmpty());
        assertTrue(results2.isEmpty());
        assertTrue(results3.isEmpty());
    }
    
    // ==================== STOP WORDS ====================
    
    @Test
    void testStopWordsDefault() {
        TextSearchIndex customIndex = new TextSearchIndex();
        
        MockNode node = new MockNode("node", "the class", "com.example.TheClass", 
            Path.of("/test/TheClass.java"), "class");
        
        customIndex.index(node);
        
        // "the" is a stop word, should not be indexed
        assertFalse(customIndex.search("the").contains("node"));
        
        // "class" is not a stop word
        assertTrue(customIndex.search("class").contains("node"));
    }
    
    @Test
    void testCustomStopWords() {
        Set<String> customStopWords = new HashSet<>(Arrays.asList("custom", "word"));
        TextSearchIndex customIndex = new TextSearchIndex(customStopWords);
        
        MockNode node = new MockNode("node", "custom class", "com.example.CustomClass", 
            Path.of("/test/CustomClass.java"), "class");
        
        customIndex.index(node);
        
        // "custom" is a stop word
        assertFalse(customIndex.search("custom").contains("node"));
        
        // "class" is not
        assertTrue(customIndex.search("class").contains("node"));
    }
    
    @Test
    void testStopWordsCaseInsensitive() {
        TextSearchIndex customIndex = new TextSearchIndex();
        
        MockNode node = new MockNode("node", "THE CLASS", "com.example.THE_CLASS", 
            Path.of("/test/THE_CLASS.java"), "class");
        
        customIndex.index(node);
        
        // "THE" should be filtered as stop word (case insensitive)
        assertFalse(customIndex.search("THE").contains("node"));
        assertFalse(customIndex.search("the").contains("node"));
    }
    
    // ==================== TOKEN LENGTH FILTERING ====================
    
    @Test
    void testMinTokenLength() {
        TextSearchIndex customIndex = new TextSearchIndex(Collections.emptySet(), 3, 100);
        
        MockNode node = new MockNode("node", "a ab abc", "com.example.Node", 
            Path.of("/test/Node.java"), "class");
        
        customIndex.index(node);
        
        // "a" and "ab" are too short (min length 3)
        assertFalse(customIndex.search("a").contains("node"));
        assertFalse(customIndex.search("ab").contains("node"));
        
        // "abc" meets minimum length
        assertTrue(customIndex.search("abc").contains("node"));
    }
    
    @Test
    void testMaxTokenLength() {
        TextSearchIndex customIndex = new TextSearchIndex(Collections.emptySet(), 1, 5);
        
        MockNode node = new MockNode("node", "abc abcdef abcdefgh", "com.example.Node", 
            Path.of("/test/Node.java"), "class");
        
        customIndex.index(node);
        
        // "abc" and "abcdef" meet max length
        assertTrue(customIndex.search("abc").contains("node"));
        assertTrue(customIndex.search("abcdef").contains("node"));
        
        // "abcdefgh" is too long (max length 5)
        assertFalse(customIndex.search("abcdefgh").contains("node"));
    }
    
    // ==================== LOOKUP METHODS ====================
    
    @Test
    void testGetTokens() {
        index.index(node1);
        
        Set<String> tokens = index.getTokens("node1");
        assertTrue(tokens.contains("MyClass"));
        assertTrue(tokens.contains("com"));
        assertTrue(tokens.contains("example"));
    }
    
    @Test
    void testGetNodes() {
        index.index(node1);
        index.index(node2);
        
        Set<String> nodeIds = index.getNodes("calculate");
        assertEquals(1, nodeIds.size());
        assertTrue(nodeIds.contains("node2"));
    }
    
    @Test
    void testGetNodeCount() {
        index.index(node1);
        index.index(node2);
        
        assertEquals(2, index.getNodeCount("com"));
    }
    
    // ==================== STATISTICS ====================
    
    @Test
    void testGetIndexedNodeCount() {
        assertEquals(0, index.getIndexedNodeCount());
        
        index.index(node1);
        assertEquals(1, index.getIndexedNodeCount());
        
        index.index(node2);
        assertEquals(2, index.getIndexedNodeCount());
    }
    
    @Test
    void testGetTokenCount() {
        assertEquals(0, index.getTokenCount());
        
        index.index(node1);
        // node1 has multiple tokens
        assertTrue(index.getTokenCount() > 0);
    }
    
    @Test
    void testGetStatistics() {
        String stats = index.getStatistics();
        assertTrue(stats.contains("TextSearchIndex"));
        assertTrue(stats.contains("nodes=0"));
        assertTrue(stats.contains("tokens=0"));
    }
    
    // ==================== CLEAR ====================
    
    @Test
    void testClear() {
        index.index(node1);
        index.index(node2);
        
        assertEquals(2, index.getIndexedNodeCount());
        
        index.clear();
        
        assertEquals(0, index.getIndexedNodeCount());
        assertEquals(0, index.getTokenCount());
    }
    
    // ==================== BUILDER ====================
    
    @Test
    void testBuilderDefault() {
        TextSearchIndex builtIndex = TextSearchIndex.builder().build();
        assertNotNull(builtIndex);
    }
    
    @Test
    void testBuilderWithStopWords() {
        TextSearchIndex builtIndex = TextSearchIndex.builder()
            .addStopWord("custom")
            .addStopWords(Arrays.asList("word1", "word2"))
            .build();
        
        assertTrue(builtIndex.getStopWords().contains("custom"));
        assertTrue(builtIndex.getStopWords().contains("word1"));
        assertTrue(builtIndex.getStopWords().contains("word2"));
    }
    
    @Test
    void testBuilderWithMinMaxLength() {
        TextSearchIndex builtIndex = TextSearchIndex.builder()
            .setMinTokenLength(5)
            .setMaxTokenLength(20)
            .build();
        
        assertEquals(5, builtIndex.getMinTokenLength());
        assertEquals(20, builtIndex.getMaxTokenLength());
    }
    
    // ==================== NULL HANDLING ====================
    
    @Test
    void testIndexNullNode() {
        index.index((Node) null);
        assertEquals(0, index.getIndexedNodeCount());
    }
    
    @Test
    void testUnindexNullNode() {
        index.unindex((Node) null);
        assertEquals(0, index.getIndexedNodeCount());
    }
    
    @Test
    void testSearchNullToken() {
        assertTrue(index.search(null).isEmpty());
    }
    
    // ==================== MOCK NODE ====================
    
    /**
     * Mock Node implementation for testing.
     */
    private static class MockNode implements Node {
        private final String id;
        private final String name;
        private final String qualifiedName;
        private final Path file;
        private final String type;
        
        public MockNode(String id, String name, String qualifiedName, Path file, String type) {
            this.id = id;
            this.name = name;
            this.qualifiedName = qualifiedName;
            this.file = file;
            this.type = type;
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public Path getFile() {
            return file;
        }
        
        @Override
        public Position getStartPosition() {
            return null;
        }
        
        @Override
        public Position getEndPosition() {
            return null;
        }
        
        @Override
        public Map<String, Object> getProperties() {
            return Collections.emptyMap();
        }
        
        // Additional methods for convenience
        public String getName() {
            return name;
        }
        
        public String getQualifiedName() {
            return qualifiedName;
        }
        
        public String getPackageName() {
            if (qualifiedName != null && qualifiedName.contains(".")) {
                return qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
            }
            return null;
        }
        
        public String getDescription() {
            return null;
        }
    }
}
