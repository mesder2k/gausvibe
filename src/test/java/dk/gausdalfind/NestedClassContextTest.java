package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.declaration.ClassNode;
import dk.gausdalfind.model.declaration.MethodNode;
import dk.gausdalfind.queries.GraphQueryEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the ES round-4 "who calls createIndex = 0 callers"
 * bug. Root cause: NodeFactory.createClass set currentClass as a side
 * effect, so when a builder processed a NESTED type it captured the
 * nested name as the "old" class and restored it afterwards - every
 * member parsed after any nested type in the same file got a corrupted
 * class (MetadataCreateIndexService.createIndex became
 * MetadataCreateIndexService$ClusterBlocksTransformer.createIndex), so
 * call-site resolution could never find it.
 */
class NestedClassContextTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-nested-context-test");
        writeFile("src/main/java/com/example/Service.java",
            "package com.example;\n" +
            "public class Service {\n" +
            "    public void createIndex(String name) { }\n" +
            "}\n");
        // Mirrors MetadataCreateIndexService: a nested interface declared
        // BEFORE the method that must stay a member of the outer class.
        writeFile("src/main/java/com/example/Outer.java",
            "package com.example;\n" +
            "public class Outer {\n" +
            "    interface Transformer {\n" +
            "        String transform(String s);\n" +
            "    }\n" +
            "    static class Task {\n" +
            "        public String toString() { return \"t\"; }\n" +
            "    }\n" +
            "    private final Service service = new Service();\n" +
            "    public void callService() {\n" +
            "        service.createIndex(\"idx\");\n" +
            "    }\n" +
            "}\n");

        graph = new GausVibeBuilder(projectDir).build();
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
    void membersAfterNestedTypeKeepOuterClass() {
        ClassNode outer = query.findClassByQualifiedName("com.example.Outer").orElseThrow();
        MethodNode callService = query.getMethods(outer).stream()
            .filter(m -> m.getName().equals("callService"))
            .findFirst()
            .orElseThrow();
        assertEquals("com.example.Outer", callService.getClassName(),
            "method declared after a nested interface must belong to the outer class");
        assertEquals("com.example.Outer.callService", callService.getQualifiedName());
    }

    @Test
    void nestedClassNamesNestCorrectly() {
        ClassNode task = query.findClassByQualifiedName("com.example.Outer$Task").orElseThrow();
        assertEquals("com.example.Outer$Task", task.getQualifiedName());
        ClassNode transformer = query.findClassByQualifiedName("com.example.Outer$Transformer").orElseThrow();
        assertEquals("com.example.Outer$Transformer", transformer.getQualifiedName());
    }

    @Test
    void callAfterNestedTypeResolvesEdge() {
        ClassNode service = query.findClassByQualifiedName("com.example.Service").orElseThrow();
        MethodNode createIndex = query.getMethods(service).stream()
            .filter(m -> m.getName().equals("createIndex"))
            .findFirst()
            .orElseThrow();
        assertTrue(
            query.getCallers(createIndex).stream()
                .anyMatch(m -> m.getName().equals("callService")),
            "Outer.callService must reach Service.createIndex; under the bug the "
                + "call service was registered under the corrupted class name"
        );
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
