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
 * Regression test for ES round 5's harness-recall gap: tests that verify
 * behavior through a helper (two calls away) or through asserted
 * exceptions never showed up as coverage. coverageFor now links them as
 * call-graph-2hop and exception-link.
 */
class IndirectCoverageTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-indirect-coverage-test");

        writeFile("src/main/java/com/example/Validator.java",
            "package com.example;\n" +
            "public class Validator {\n" +
            "    public void validate(String name) {\n" +
            "        if (name.isEmpty()) { throw new IllegalArgumentException(\"empty\"); }\n" +
            "    }\n" +
            "}\n");
        // helper between the test and the validator (one extra hop)
        writeFile("src/main/java/com/example/Service.java",
            "package com.example;\n" +
            "public class Service {\n" +
            "    private final Validator validator = new Validator();\n" +
            "    public void run(String name) { validator.validate(name); }\n" +
            "}\n");
        // two-hop test: calls Service.run, never Validator directly
        writeFile("src/test/java/com/example/ServiceTest.java",
            "package com.example;\n" +
            "public class ServiceTest {\n" +
            "    public void testRun() { new Service().run(\"\"); }\n" +
            "}\n");
        // exception-link test: asserts the exception the validator throws
        // while exercising a class with no call path to it - the only link
        // is the shared, observable exception type (the ES resolver-test
        // pattern: assert InvalidIndexNameException, never call the
        // validator directly)
        writeFile("src/main/java/com/example/Unrelated.java",
            "package com.example;\n" +
            "public class Unrelated {\n" +
            "    public void go() { }\n" +
            "}\n");
        writeFile("src/test/java/com/example/ValidatorBehaviorTest.java",
            "package com.example;\n" +
            "public class ValidatorBehaviorTest {\n" +
            "    public void testRejectsEmpty() {\n" +
            "        org.junit.jupiter.api.Assertions.assertThrows(\n" +
            "            IllegalArgumentException.class,\n" +
            "            () -> new Unrelated().go());\n" +
            "    }\n" +
            "}\n");

        GausVibeBuilder builder = new GausVibeBuilder(projectDir);
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
    void twoHopTestsAreLinked() {
        ClassNode validator = query.findClassByQualifiedName("com.example.Validator").orElseThrow();
        Map<String, Object> coverage = GausVibeServer.TestsHandler.coverageFor(validator, query);
        List<Map<String, Object>> tests = (List<Map<String, Object>>) coverage.get("tests");

        Map<String, Object> serviceTest = tests.stream()
            .filter(t -> t.get("test_class").equals("com.example.ServiceTest"))
            .findFirst().orElse(null);
        assertNotNull(serviceTest, "ServiceTest must be linked to Validator");
        assertEquals("call-graph-2hop", serviceTest.get("coverage"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exceptionAssertingTestsAreLinked() {
        ClassNode validator = query.findClassByQualifiedName("com.example.Validator").orElseThrow();
        Map<String, Object> coverage = GausVibeServer.TestsHandler.coverageFor(validator, query);
        List<Map<String, Object>> tests = (List<Map<String, Object>>) coverage.get("tests");

        Map<String, Object> behaviorTest = tests.stream()
            .filter(t -> t.get("test_class").equals("com.example.ValidatorBehaviorTest"))
            .findFirst().orElse(null);
        assertNotNull(behaviorTest, "ValidatorBehaviorTest must be linked to Validator");
        assertEquals("exception-link", behaviorTest.get("coverage"));
        Map<String, List<String>> methods = (Map<String, List<String>>) behaviorTest.get("test_methods");
        assertTrue(methods.get("testRejectsEmpty()").stream()
                .anyMatch(s -> s.contains("IllegalArgumentException")),
            "link must name the asserted exception");
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
