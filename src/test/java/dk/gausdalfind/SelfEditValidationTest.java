package dk.gausdalfind;

import dk.gausdalfind.cli.EditCommand;
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
        // Graph no longer exposes getAllClassNames(); derive it from the class index
        Set<String> classNames = new HashSet<>();
        for (ClassNode clazz : graph.getIndexes().getAllClasses()) {
            classNames.add(clazz.getQualifiedName());
        }
        assertTrue(classNames.contains("dk.gausdalfind.graph.GausVibeBuilder"),
            "Should find GausVibeBuilder");
        assertTrue(classNames.contains("dk.gausdalfind.cli.CommandLineInterface"),
            "Should find CommandLineInterface");
        assertTrue(classNames.contains("dk.gausdalfind.cli.EditCommand"),
            "Should find EditCommand");
        
        System.out.println("✅ Parser validation passed");
        System.out.println("  - Nodes: " + graph.getNodeCount());
        System.out.println("  - Classes: " + classNames.size());
        System.out.println("  - Packages: " + graph.getIndexes().getAllPackages().size());
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
        // Test creating various operation types (current API is builder-based)
        AddMethodOperation addMethod = AddMethodOperation.builder()
            .targetClass("dk.gausdalfind.test.TestClass")
            .name("newMethod")
            .returnType("void")
            .addModifier("public")
            .body("System.out.println(\"Hello\");")
            .build();
        assertNotNull(addMethod);
        assertEquals("ADD_METHOD", addMethod.getType().name());
        assertEquals("newMethod", addMethod.getName());
        
        // RemoveMethodOperation takes a single method qualified name
        // (ClassFQN.methodName), not class + method separately
        RemoveMethodOperation removeMethod = RemoveMethodOperation.of(
            "dk.gausdalfind.test.TestClass.oldMethod"
        );
        assertNotNull(removeMethod);
        assertEquals("REMOVE_METHOD", removeMethod.getType().name());
        
        AddFieldOperation addField = AddFieldOperation.builder()
            .targetClass("dk.gausdalfind.test.TestClass")
            .name("newField")
            .type("String")
            .addModifier("private")
            .build();
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
        
        // serializeAll(Path) no longer exists; the current API serializes the
        // graph's files to an in-memory map. (The disk-writing variants resolve
        // the graph's absolute source paths, so they are not used here.)
        Map<String, String> serialized = serializer.serializeModifiedFiles();
        
        // Verify some files were serialized
        assertFalse(serialized.isEmpty(), "Should serialize at least some files");
        
        // Verify a known class file was serialized with its class declaration
        Optional<ClassNode> builderClass = graph.getIndexes().getClassByQualifiedName(
            "dk.gausdalfind.graph.GausVibeBuilder"
        );
        assertTrue(builderClass.isPresent(), "Should find GausVibeBuilder");
        String builderSource = serialized.get(builderClass.get().getFile().toString());
        assertNotNull(builderSource, "GausVibeBuilder file should be serialized");
        assertTrue(builderSource.contains("GausVibeBuilder"),
            "Serialized source should contain the class declaration");
        
        System.out.println("✅ Serializer validation passed");
        System.out.println("  - Serialized " + serialized.size() + " files");
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
        AddMethodOperation addMethodOp = AddMethodOperation.builder()
            .targetClass(cliClass.get().getQualifiedName())
            .name("getSelfEditStatus")
            .returnType("String")
            .addModifier("public")
            .body("return \"Self-Editing: Phase 5 In Progress\";")
            .build();
        
        // Dry run: the operation is applied to the in-memory graph, but
        // EditCommand must not write files back (it would resolve the graph's
        // absolute source paths and overwrite the real source tree)
        EditCommand editCmd = new EditCommand(
            graph, 
            TEST_WORK_DIR.resolve("test-graph.json"), 
            true, 
            "text", 
            false
        );
        String result = editCmd.execute(Collections.singletonList(addMethodOp));
        assertNotNull(result, "Edit should return a result");
        assertTrue(result.length() > 0, "Result should not be empty");
        
        // PHASE 4: SERIALIZE
        // serializeAll(Path) no longer exists; serialize the modified file
        // in-memory and verify the new method is in the serialized source
        ASTSourceSerializer serializer = new ASTSourceSerializer(graph);
        String cliSource = serializer.serializeFile(cliClass.get().getFile().toString());
        assertNotNull(cliSource, "Modified file should serialize");
        
        assertTrue(cliSource.contains("getSelfEditStatus"), 
            "Should contain new method: getSelfEditStatus");
        
        System.out.println("✅ Round-trip test passed (parse → query → edit → serialize)");
        System.out.println("  - Parsed: " + graph.getNodeCount() + " nodes");
        System.out.println("  - Edited: Added getSelfEditStatus() to CommandLineInterface");
        System.out.println("  - Serialized: dk/gausdalfind/cli/CommandLineInterface.java");
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
        operations.add(AddMethodOperation.builder()
            .targetClass(builderClass.get().getQualifiedName())
            .name("getBuildTimestamp")
            .returnType("long")
            .addModifier("public")
            .body("return System.currentTimeMillis();")
            .build()
        );
        
        // Add a field
        operations.add(AddFieldOperation.builder()
            .targetClass(builderClass.get().getQualifiedName())
            .name("lastModified")
            .type("long")
            .addModifier("private")
            .build()
        );
        
        // Dry run: operations are applied to the in-memory graph, but
        // EditCommand must not write files back to the real source tree
        EditCommand editCmd = new EditCommand(
            graph, 
            TEST_WORK_DIR.resolve("batch-graph.json"), 
            true, 
            "text", 
            false
        );
        String result = editCmd.execute(operations);
        assertNotNull(result);
        
        // Serialize
        ASTSourceSerializer serializer = new ASTSourceSerializer(graph);
        String builderSource = serializer.serializeFile(
            builderClass.get().getFile().toString());
        assertNotNull(builderSource, "Modified file should serialize");
        
        // Verify both changes
        assertTrue(builderSource.contains("getBuildTimestamp"), "Should contain new method");
        assertTrue(builderSource.contains("lastModified"), "Should contain new field");
        
        System.out.println("✅ Batch edit test passed");
        System.out.println("  - Applied 2 operations (1 method + 1 field)");
        System.out.println("  - Both changes serialized successfully");
    }
}
