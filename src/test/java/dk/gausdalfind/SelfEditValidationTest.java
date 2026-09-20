package dk.gausdalfind;

import dk.gausdalfind.editing.*;
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.parser.JavaFileCollector;
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.serializer.ASTSourceSerializer;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Phase 5 Validation Tests - Minimal tests that can run without full compilation
 * 
 * These tests validate the core components of self-editing without requiring
 * Maven or full project compilation.
 */
public class SelfEditValidationTest {
    
    private static final Path PROJECT_ROOT = Path.of("/Users/magnusfind/Documents/find-shadow-model/gausvibe");
    private static final Path TEST_WORK_DIR = Path.of("/tmp/gausvibe-validation");
    
    @BeforeAll
    public static void setUpAll() throws IOException {
        // Clean test work directory
        if (Files.exists(TEST_WORK_DIR)) {
            deleteDirectory(TEST_WORK_DIR);
        }
        Files.createDirectories(TEST_WORK_DIR);
    }
    
    @AfterAll
    public static void tearDownAll() throws IOException {
        if (Files.exists(TEST_WORK_DIR)) {
            deleteDirectory(TEST_WORK_DIR);
        }
    }
    
    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
        }
    }
    
    // ==================== PHASE 5: COMPONENT VALIDATION TESTS ====================
    
    /**
     * Test that we can parse GausVibe's own source code
     */
    @Test
    @Tag("Phase5")
    @Tag("ComponentValidation")
    public void testComponent_Parser_ParseGausVibe() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(PROJECT_ROOT);
        Graph graph = builder.build();
        
        assertNotNull(graph, "Graph should be built");
        assertTrue(graph.getNodeCount() > 0, "Graph should have nodes");
        
        // Verify we have classes from GausVibe
        Set<String> classNames = graph.getAllClassNames();
        assertTrue(classNames.contains("dk.gausdalfind.graph.GausVibeBuilder"),
            "Should find GausVibeBuilder");
        assertTrue(classNames.contains("dk.gausdalfind.cli.CommandLineInterface"),
            "Should find CommandLineInterface");
        assertTrue(classNames.contains("dk.gausdalfind.cli.EditCommand"),
            "Should find EditCommand");
        
        System.out.println("✅ Parser validation passed");
        System.out.println("  - Nodes: " + graph.getNodeCount());
        System.out.println("  - Classes: " + classNames.size());
        System.out.println("  - Packages: " + graph.getAllPackageNames().size());
    }
    
    /**
     * Test that we can query GausVibe's graph
     */
    @Test
    @Tag("Phase5")
    @Tag("ComponentValidation")
    public void testComponent_QueryEngine_FindClasses() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(PROJECT_ROOT);
        builder.setParallel(true);
        Graph graph = builder.build();
        
        GraphQueryEngine queryEngine = new GraphQueryEngine(graph);
        
        // Test finding a specific class
        Optional<ClassNode> builderClass = queryEngine.findClassByQualifiedName(
            "dk.gausdalfind.graph.GausVibeBuilder"
        );
        assertTrue(builderClass.isPresent(), "Should find GausVibeBuilder");
        assertEquals("dk.gausdalfind.graph.GausVibeBuilder", 
            builderClass.get().getQualifiedName());
        
        // Test finding methods
        List<MethodNode> methods = queryEngine.getMethods(builderClass.get());
        assertNotNull(methods, "Should have methods");
        assertTrue(methods.size() > 0, "GausVibeBuilder should have methods");
        
        System.out.println("✅ Query engine validation passed");
        System.out.println("  - Found GausVibeBuilder with " + methods.size() + " methods");
    }
    
    /**
     * Test that we can create edit operations
     */
    @Test
    @Tag("Phase5")
    @Tag("ComponentValidation")
    public void testComponent_EditOperations_Create() {
        // Test creating various operation types
        AddMethodOperation addMethod = new AddMethodOperation(
            "dk.gausdalfind.test.TestClass",
            "newMethod",
            "public",
            "void",
            Collections.emptyList(),
            Collections.emptyList(),
            "System.out.println(\"Hello\");"
        );
        assertNotNull(addMethod);
        assertEquals("ADD_METHOD", addMethod.getType().name());
        assertEquals("newMethod", addMethod.getName());
        
        RemoveMethodOperation removeMethod = new RemoveMethodOperation(
            "dk.gausdalfind.test.TestClass",
            "oldMethod"
        );
        assertNotNull(removeMethod);
        assertEquals("REMOVE_METHOD", removeMethod.getType().name());
        
        AddFieldOperation addField = new AddFieldOperation(
            "dk.gausdalfind.test.TestClass",
            "newField",
            "String",
            Collections.emptyList()
        );
        assertNotNull(addField);
        assertEquals("ADD_FIELD", addField.getType().name());
        
        System.out.println("✅ Edit operations validation passed");
        System.out.println("  - ADD_METHOD: OK");
        System.out.println("  - REMOVE_METHOD: OK");
        System.out.println("  - ADD_FIELD: OK");
    }
    
    /**
     * Test that we can serialize a graph back to source
     */
    @Test
    @Tag("Phase5")
    @Tag("ComponentValidation")
    public void testComponent_Serializer_Basic() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(PROJECT_ROOT);
        Graph graph = builder.build();
        
        ASTSourceSerializer serializer = new ASTSourceSerializer(graph);
        assertNotNull(serializer, "Serializer should be created");
        
        // Test serialization of a specific class
        Path outputDir = TEST_WORK_DIR.resolve("serialize-test");
        Files.createDirectories(outputDir);
        
        // Serialize all files
        serializer.serializeAll(outputDir);
        
        // Verify some files were created
        long fileCount = Files.walk(outputDir)
            .filter(Files::isRegularFile)
            .count();
        
        assertTrue(fileCount > 0, "Should serialize at least some files");
        
        System.out.println("✅ Serializer validation passed");
        System.out.println("  - Serialized " + fileCount + " files to " + outputDir);
    }
    
    /**
     * Test the complete workflow: Parse → Query → Edit → Serialize
     * This is the core round-trip test without compilation verification
     */
    @Test
    @Tag("Phase5")
    @Tag("RoundTrip")
    @Tag("Integration")
    public void testRoundTrip_ParseQueryEditSerialize_NoCompile() throws IOException {
        // PHASE 1: PARSE
        GausVibeBuilder builder = new GausVibeBuilder(PROJECT_ROOT);
        builder.setParallel(true);
        Graph graph = builder.build();
        assertNotNull(graph);
        
        // PHASE 2: QUERY
        GraphQueryEngine queryEngine = new GraphQueryEngine(graph);
        Optional<ClassNode> cliClass = queryEngine.findClassByQualifiedName(
            "dk.gausdalfind.cli.CommandLineInterface"
        );
        assertTrue(cliClass.isPresent(), "Should find CommandLineInterface");
        
        // PHASE 3: EDIT
        AddMethodOperation addMethodOp = new AddMethodOperation(
            cliClass.get().getQualifiedName(),
            "getSelfEditStatus",
            "public",
            "String",
            Collections.emptyList(),
            Collections.emptyList(),
            "return \"Self-Editing: Phase 5 In Progress\";"
        );
        
        EditCommand editCmd = new EditCommand(
            graph, 
            TEST_WORK_DIR.resolve("test-graph.json"), 
            false, 
            "text", 
            false
        );
        String result = editCmd.execute(Collections.singletonList(addMethodOp));
        assertNotNull(result, "Edit should return a result");
        assertTrue(result.length() > 0, "Result should not be empty");
        
        // PHASE 4: SERIALIZE
        ASTSourceSerializer serializer = new ASTSourceSerializer(graph);
        Path outputDir = TEST_WORK_DIR.resolve("roundtrip-test");
        Files.createDirectories(outputDir);
        serializer.serializeAll(outputDir);
        
        // Verify the new method is in the serialized file
        Path modifiedFile = outputDir.resolve("dk/gausdalfind/cli/CommandLineInterface.java");
        assertTrue(Files.exists(modifiedFile), "Modified file should exist");
        
        String content = Files.readString(modifiedFile);
        assertTrue(content.contains("getSelfEditStatus"), 
            "Should contain new method: getSelfEditStatus");
        
        System.out.println("✅ Round-trip test passed (parse → query → edit → serialize)");
        System.out.println("  - Parsed: " + graph.getNodeCount() + " nodes");
        System.out.println("  - Edited: Added getSelfEditStatus() to CommandLineInterface");
        System.out.println("  - Serialized: " + outputDir);
        System.out.println("  - ⚠️  Compilation verification requires Maven (see ISSUES.md)");
    }
    
    /**
     * Test multiple operations in a single edit
     */
    @Test
    @Tag("Phase5")
    @Tag("BatchEdit")
    public void testBatchEdit_MultipleOperations() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(PROJECT_ROOT);
        Graph graph = builder.build();
        GraphQueryEngine queryEngine = new GraphQueryEngine(graph);
        
        Optional<ClassNode> builderClass = queryEngine.findClassByQualifiedName(
            "dk.gausdalfind.graph.GausVibeBuilder"
        );
        assertTrue(builderClass.isPresent());
        
        // Create multiple operations
        List<Operation> operations = new ArrayList<>();
        
        // Add a method
        operations.add(new AddMethodOperation(
            builderClass.get().getQualifiedName(),
            "getBuildTimestamp",
            "public",
            "long",
            Collections.emptyList(),
            Collections.emptyList(),
            "return System.currentTimeMillis();"
        ));
        
        // Add a field
        operations.add(new AddFieldOperation(
            builderClass.get().getQualifiedName(),
            "lastModified",
            "long",
            Collections.singletonList("private")
        ));
        
        EditCommand editCmd = new EditCommand(
            graph, 
            TEST_WORK_DIR.resolve("batch-graph.json"), 
            false, 
            "text", 
            false
        );
        String result = editCmd.execute(operations);
        assertNotNull(result);
        
        // Serialize
        ASTSourceSerializer serializer = new ASTSourceSerializer(graph);
        Path outputDir = TEST_WORK_DIR.resolve("batch-test");
        Files.createDirectories(outputDir);
        serializer.serializeAll(outputDir);
        
        // Verify both changes
        Path modifiedFile = outputDir.resolve("dk/gausdalfind/graph/GausVibeBuilder.java");
        assertTrue(Files.exists(modifiedFile));
        
        String content = Files.readString(modifiedFile);
        assertTrue(content.contains("getBuildTimestamp"), "Should contain new method");
        assertTrue(content.contains("lastModified"), "Should contain new field");
        
        System.out.println("✅ Batch edit test passed");
        System.out.println("  - Applied 2 operations (1 method + 1 field)");
        System.out.println("  - Both changes serialized successfully");
    }
}
