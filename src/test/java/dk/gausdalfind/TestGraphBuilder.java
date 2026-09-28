package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.queries.*;
import dk.gausdalfind.serializer.JsonSerializer;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Integration tests for GausVibeBuilder.
 * 
 * Tests the complete graph building process including:
 * - File collection
 * - Parsing
 * - Node creation
 * - Edge creation
 * - Symbol resolution
 */
public class TestGraphBuilder {
    
    private static Path testProjectDir;
    
    @BeforeAll
    static void setupTestProject() throws IOException {
        // Create a temporary test project
        testProjectDir = Files.createTempDirectory("gausvibe-test");
        
        // Create test Java files
        createTestFile("com/example/Calculator.java", 
            "package com.example;\n" +
            "public class Calculator {\n" +
            "    public int add(int a, int b) { return a + b; }\n" +
            "    public int subtract(int a, int b) { return a - b; }\n" +
            "    private int result;\n" +
            "}\n");
        
        createTestFile("com/example/Person.java",
            "package com.example;\n" +
            "public class Person {\n" +
            "    private String name;\n" +
            "    public Person(String name) { this.name = name; }\n" +
            "    public String getName() { return name; }\n" +
            "}\n");
        
        createTestFile("com/example/Animal.java",
            "package com.example;\n" +
            "public interface Animal {\n" +
            "    String getName();\n" +
            "    void makeSound();\n" +
            "}\n");
        
        createTestFile("com/example/Dog.java",
            "package com.example;\n" +
            "public class Dog implements Animal {\n" +
            "    private String name;\n" +
            "    public Dog(String name) { this.name = name; }\n" +
            "    @Override\n" +
            "    public String getName() { return name; }\n" +
            "    @Override\n" +
            "    public void makeSound() { System.out.println(\"Woof!\"); }\n" +
            "}\n");
    }
    
    @AfterAll
    static void cleanupTestProject() throws IOException {
        if (testProjectDir != null) {
            deleteDirectory(testProjectDir);
        }
    }
    
    private static void createTestFile(String relativePath, String content) throws IOException {
        Path filePath = testProjectDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
    
    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
        }
    }
    
    @Test
    void testBuildEmptyProject() throws IOException {
        Path emptyDir = Files.createTempDirectory("empty-test");
        try {
            GausVibeBuilder builder = new GausVibeBuilder(emptyDir);
            Graph graph = builder.build();
            
            assertNotNull(graph);
            assertEquals(0, graph.getNodeCount());
            assertEquals(0, graph.getEdgeCount());
        } finally {
            deleteDirectory(emptyDir);
        }
    }
    
    @Test
    void testBuildSingleClass() throws IOException {
        Path singleFile = Files.createTempDirectory("single-test").resolve("Test.java");
        Files.createDirectories(singleFile.getParent());
        Files.writeString(singleFile, 
            "public class Test {\n" +
            "    public void testMethod() {}\n" +
            "}\n");
        
        try {
            GausVibeBuilder builder = new GausVibeBuilder(singleFile.getParent());
            Graph graph = builder.build();
            
            assertNotNull(graph);
            assertTrue(graph.getNodeCount() > 0);
            
            // Check that we have a class node
            List<Node> classNodes = graph.getNodesByType("CLASS");
            assertEquals(1, classNodes.size());
            
            ClassNode testClass = (ClassNode) classNodes.get(0);
            assertEquals("Test", testClass.getName());
            
        } finally {
            deleteDirectory(singleFile.getParent());
        }
    }
    
    @Test
    void testBuildMultipleClasses() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(testProjectDir);
        Graph graph = builder.build();
        
        assertNotNull(graph);
        assertTrue(graph.getNodeCount() > 0);
        
        // Check class nodes
        List<Node> classNodes = graph.getNodesByType("CLASS");
        assertTrue(classNodes.size() >= 3); // Calculator, Person, Dog
        
        // Check interface nodes
        List<Node> interfaceNodes = graph.getNodesByType("INTERFACE");
        assertEquals(1, interfaceNodes.size()); // Animal
    }
    
    @Test
    void testQueryClasses() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(testProjectDir);
        Graph graph = builder.build();
        JavaGraphQuery query = new GraphQueryEngine(graph);
        
        // Find class by FQN
        Optional<ClassNode> calculator = query.findClassByQualifiedName("com.example.Calculator");
        assertTrue(calculator.isPresent());
        assertEquals("Calculator", calculator.get().getName());
        
        // Find classes by name
        List<ClassNode> personClasses = query.findClassesByName("Person");
        assertEquals(1, personClasses.size());
        assertEquals("com.example.Person", personClasses.get(0).getQualifiedName());
        
        // Get all classes
        List<ClassNode> allClasses = query.getAllClasses();
        assertTrue(allClasses.size() >= 3);
    }
    
    @Test
    void testQueryMethods() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(testProjectDir);
        Graph graph = builder.build();
        JavaGraphQuery query = new GraphQueryEngine(graph);
        
        // Find class
        Optional<ClassNode> calculator = query.findClassByQualifiedName("com.example.Calculator");
        assertTrue(calculator.isPresent());
        
        // Get methods
        List<MethodNode> methods = query.getMethods(calculator.get());
        assertTrue(methods.size() >= 2); // add, subtract
        
        // Check method names
        Set<String> methodNames = new HashSet<>();
        for (MethodNode method : methods) {
            methodNames.add(method.getName());
        }
        assertTrue(methodNames.contains("add"));
        assertTrue(methodNames.contains("subtract"));
    }
    
    @Test
    void testInheritanceResolution() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(testProjectDir);
        Graph graph = builder.build();
        JavaGraphQuery query = new GraphQueryEngine(graph);
        
        // Find Dog class
        Optional<ClassNode> dog = query.findClassByQualifiedName("com.example.Dog");
        assertTrue(dog.isPresent());
        
        // Find Animal interface
        Optional<ClassNode> animal = query.findClassByQualifiedName("com.example.Animal");
        assertTrue(animal.isPresent());
        
        // Check that Dog implements Animal
        List<ClassNode> implementations = query.getImplementations(animal.get());
        assertTrue(implementations.size() >= 1);
        
        // Check Dog's interfaces
        List<ClassNode> dogInterfaces = query.getInterfaces(dog.get());
        assertTrue(dogInterfaces.size() >= 1);
    }
    
    @Test
    void testRoundTripSerialization() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(testProjectDir);
        Graph originalGraph = builder.build();
        
        // Serialize to JSON
        Path tempFile = Files.createTempFile("graph-test", ".json");
        try {
            JsonSerializer serializer = new JsonSerializer();
            serializer.serialize(originalGraph, tempFile);
            
            // Deserialize
            Graph deserializedGraph = serializer.deserialize(tempFile);
            
            // Check counts match
            assertEquals(originalGraph.getNodeCount(), deserializedGraph.getNodeCount());
            assertEquals(originalGraph.getEdgeCount(), deserializedGraph.getEdgeCount());
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Test
    void testCachingQueryEngine() throws IOException {
        GausVibeBuilder builder = new GausVibeBuilder(testProjectDir);
        Graph graph = builder.build();
        
        // Create caching engine
        JavaGraphQuery baseQuery = new GraphQueryEngine(graph);
        CachingQueryEngine cachingQuery = new CachingQueryEngine(baseQuery);
        
        // Execute queries to populate cache
        cachingQuery.getAllClasses();
        cachingQuery.getAllMethods();
        
        // Check cache statistics
        assertEquals(2, cachingQuery.getCacheMisses());
        assertEquals(0, cachingQuery.getCacheHits());
        
        // Execute same queries again
        cachingQuery.getAllClasses();
        cachingQuery.getAllMethods();
        
        // Check cache hits
        assertEquals(2, cachingQuery.getCacheMisses());
        assertEquals(2, cachingQuery.getCacheHits());
        assertEquals(0.5, cachingQuery.getCacheHitRate(), 0.001);
    }
}
