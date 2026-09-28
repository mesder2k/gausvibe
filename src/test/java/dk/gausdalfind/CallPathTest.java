package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.EdgeTypes;
import dk.gausdalfind.model.Graph;
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
 * Tests transitive call-path queries between methods.
 */
class CallPathTest {

    private static Path projectDir;
    private static Graph graph;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-callpath-test");
        writeFile("src/main/java/com/example/Alpha.java",
            "package com.example;\n" +
            "public class Alpha {\n" +
            "    public void start() { new Beta().middle(); }\n" +
            "}\n");
        writeFile("src/main/java/com/example/Beta.java",
            "package com.example;\n" +
            "public class Beta {\n" +
            "    public void middle() { new Gamma().finish(); }\n" +
            "}\n");
        writeFile("src/main/java/com/example/Gamma.java",
            "package com.example;\n" +
            "public class Gamma {\n" +
            "    public void finish() { System.out.println(\"done\"); }\n" +
            "}\n");
        
        graph = new GausVibeBuilder(projectDir).build();
        // same enable+backfill the server performs
        graph.getIndexes().enableCallGraphIndex();
        graph.getIndexes().getCallGraphIndex()
            .indexCalls(graph.getIndexes().getEdgesByType(EdgeTypes.CALLS));
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
    void findsTransitivePath() {
        Map<String, Object> result = GausVibeServer.callPaths(
            "com.example.Alpha.start", "com.example.Gamma.finish", 5, graph);
        
        List<String> paths = (List<String>) result.get("paths");
        assertEquals(1, paths.size(), "one path: " + result);
        String path = paths.get(0);
        assertTrue(path.contains("com.example.Alpha.start"), path);
        assertTrue(path.contains("com.example.Beta.middle"), "intermediate hop present: " + path);
        assertTrue(path.contains("com.example.Gamma.finish"), path);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bareMethodNamesResolve() {
        Map<String, Object> result = GausVibeServer.callPaths("start", "finish", 5, graph);
        List<String> paths = (List<String>) result.get("paths");
        assertFalse(paths.isEmpty(), "bare names resolve: " + result);
    }

    @Test
    void unreachableTargetReportsNoPaths() {
        Map<String, Object> result = GausVibeServer.callPaths(
            "com.example.Gamma.finish", "com.example.Alpha.start", 5, graph);
        assertEquals(0, result.get("path_count"));
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
