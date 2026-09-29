package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.declaration.ClassNode;
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.server.GausVibeServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the test-coverage map: given a production class, find the test
 * classes covering it via name convention and the call graph.
 */
class TestCoverageTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-coverage-test");
        writeFile("src/main/java/com/example/Widget.java",
            "package com.example;\n" +
            "public class Widget {\n" +
            "    public int size() { return 1; }\n" +
            "    public void reset() { }\n" +
            "}\n");
        writeFile("src/test/java/com/example/WidgetTest.java",
            "package com.example;\n" +
            "public class WidgetTest {\n" +
            "    public void testSize() {\n" +
            "        Widget w = new Widget();\n" +
            "        if (w.size() == 1) { System.out.println(\"ok\"); }\n" +
            "    }\n" +
            "}\n");
        // covers nothing by call graph and has no name match: must not appear
        writeFile("src/test/java/com/example/UnrelatedTest.java",
            "package com.example;\n" +
            "public class UnrelatedTest {\n" +
            "    public void testNothing() { System.out.println(\"x\"); }\n" +
            "}\n");
        // production class with a test-like name: must NOT be a test class
        writeFile("src/main/java/com/example/TestsHandler.java",
            "package com.example;\n" +
            "public class TestsHandler {\n" +
            "    public void handle() { }\n" +
            "}\n");
        
        GausVibeBuilder builder = new GausVibeBuilder(projectDir);
        builder.setIncludeTestSources(true);
        graph = builder.build();
        query = new GraphQueryEngine(graph);
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (projectDir != null) {
            Files.walk(projectDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void coverageFindsCallingTests() {
        ClassNode widget = query.findClassByQualifiedName("com.example.Widget").orElseThrow();
        Map<String, Object> coverage = GausVibeServer.TestsHandler.coverageFor(widget, query);
        
        assertEquals("com.example.Widget", coverage.get("class"));
        List<Map<String, Object>> tests = (List<Map<String, Object>>) coverage.get("tests");
        assertEquals(1, tests.size(), "only WidgetTest covers Widget: " + tests);
        
        Map<String, Object> widgetTest = tests.get(0);
        assertEquals("com.example.WidgetTest", widgetTest.get("test_class"));
        assertEquals("call-graph", widgetTest.get("coverage"));
        
        Map<String, List<String>> methods = (Map<String, List<String>>) widgetTest.get("test_methods");
        assertNotNull(methods);
        assertTrue(methods.containsKey("testSize()"), "testSize listed as covering method");
        assertTrue(methods.get("testSize()").contains("size()"), "testSize calls size()");
    }

    @Test
    void testClassDetectionByPathAndName() {
        ClassNode widgetTest = query.findClassByQualifiedName("com.example.WidgetTest").orElseThrow();
        ClassNode widget = query.findClassByQualifiedName("com.example.Widget").orElseThrow();
        ClassNode unrelated = query.findClassByQualifiedName("com.example.UnrelatedTest").orElseThrow();
        ClassNode testsHandler = query.findClassByQualifiedName("com.example.TestsHandler").orElseThrow();
        
        assertTrue(GausVibeServer.TestsHandler.isTestClass(widgetTest));
        assertTrue(GausVibeServer.TestsHandler.isTestClass(unrelated));
        assertFalse(GausVibeServer.TestsHandler.isTestClass(widget),
            "production class in src/main is not a test class");
        assertFalse(GausVibeServer.TestsHandler.isTestClass(testsHandler),
            "test-like name in src/main is not a test class");
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
