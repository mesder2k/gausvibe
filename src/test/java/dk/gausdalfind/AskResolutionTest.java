package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.declaration.MethodNode;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests /ask identifier resolution: fully qualified references, ambiguous
 * simple names, and fuzzy method matching.
 */
class AskResolutionTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-resolution-test");
        writeFile("src/main/java/com/example/common/Strings.java",
            "package com.example.common;\n" +
            "public class Strings {\n" +
            "    public static boolean hasText(String s) { return s != null && !s.isBlank(); }\n" +
            "}\n");
        // same simple name, nested, in another package: the ScriptDocValues$Strings trap
        writeFile("src/main/java/com/example/index/ScriptDocValues.java",
            "package com.example.index;\n" +
            "public class ScriptDocValues {\n" +
            "    public static class Strings {\n" +
            "        public String get(int docId) { return \"\"; }\n" +
            "    }\n" +
            "}\n");
        writeFile("src/main/java/com/example/cluster/MetadataCreateIndexService.java",
            "package com.example.cluster;\n" +
            "public class MetadataCreateIndexService {\n" +
            "    public void validateIndexName(String name) { if (name.isEmpty()) throw new RuntimeException(); }\n" +
            "    public void index(String name) { }\n" +
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
    void fullyQualifiedNameResolvesExactly() {
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers(
            "does org.example... wait com.example.common.Strings have a public static hasText method");
        List<String> withFqn = GausVibeServer.AskHandler.extractIdentifiers(
            "does com.example.common.Strings have a public static hasText method");
        assertTrue(withFqn.contains("com.example.common.Strings"));
        
        GausVibeServer.AskHandler.ClassResolution res =
            GausVibeServer.AskHandler.resolveClass(withFqn, query);
        assertEquals("com.example.common.Strings", res.fqn(),
            "FQN token wins over same-name nested class");
        assertNull(res.ambiguous());
    }

    @Test
    void ambiguousSimpleNameReportsCandidatesOrScores() {
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers("where is Strings defined");
        GausVibeServer.AskHandler.ClassResolution res =
            GausVibeServer.AskHandler.resolveClass(candidates, query);
        
        if (res.fqn() != null) {
            // scoring must have picked a non-nested class
            assertFalse(res.fqn().contains("$"), "nested class must not win by default: " + res.fqn());
        } else {
            // or the ambiguity is reported instead of guessed
            assertNotNull(res.ambiguous());
            assertTrue(res.ambiguous().size() >= 2);
        }
    }

    @Test
    void fuzzyMethodResolutionPrefersBestMatch() {
        // "index name validation" should resolve to validateIndexName,
        // not to the 52 index() methods (here: one index(String))
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers(
            "where is index name validation performed");
        MethodNode best = GausVibeServer.AskHandler.resolveMethodFuzzy(candidates, query);
        assertNotNull(best);
        assertEquals("validateIndexName", best.getName(),
            "stem 'validation' + token hints 'index'/'name' resolve validateIndexName");
    }

    @Test
    void exactMethodNameStillWinsOverFuzzy() {
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers("who calls hasText");
        assertEquals("hasText", GausVibeServer.AskHandler.resolveMethod(candidates, query));
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
