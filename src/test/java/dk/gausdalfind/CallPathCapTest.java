package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.CallGraphIndex;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-5b payload cap: a fan-out call graph produces more distinct
 * A -> ... -> B chains than the answer cap (MAX_CALL_PATHS = 10); the old
 * addAll-then-check let a single findCallPaths batch exceed it (260 chains,
 * ~40KB answer, one callpath question).
 *
 * The chains are injected through the public indexCall API because building
 * them from source hits a separate, still-open bug: call sites on
 * constructor-chained receivers (`new InterN().run()`) recorded but never
 * resolved into CALLS edges ("Resolved 3/11 call sites" - see the
 * cross-file resolution task).
 */
class CallPathCapTest {

    private static Path projectDir;
    private static Graph graph;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-callpath-cap-test");
        writeFile("src/main/java/com/example/Entry.java",
            "package com.example;\npublic class Entry {\n" +
            "    public void start() { new Inter0().run(); }\n}\n");
        for (int i = 0; i < 20; i++) {
            writeFile("src/main/java/com/example/Inter" + i + ".java",
                "package com.example;\npublic class Inter" + i + " {\n" +
                "    public void run() { }\n}\n");
        }
        writeFile("src/main/java/com/example/Target.java",
            "package com.example;\npublic class Target {\n" +
            "    public void finish() { System.out.println(\"done\"); }\n}\n");

        graph = new GausVibeBuilder(projectDir).build();
        graph.getIndexes().enableCallGraphIndex();
        CallGraphIndex callIndex = graph.getIndexes().getCallGraphIndex();
        callIndex.indexCalls(graph.getIndexes().getEdgesByType(EdgeTypes.CALLS));
        // fan-out: Entry.start -> 20 x InterN.run -> Target.finish
        String entry = "mth:com/example/Entry/start#start()";
        String target = "mth:com/example/Target/finish#finish()";
        for (int i = 0; i < 20; i++) {
            String inter = "mth:com/example/Inter" + i + "/run#run()";
            callIndex.indexCall(entry, inter);
            callIndex.indexCall(inter, target);
        }
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
    void callPathAnswerIsCappedAtTenChains() {
        Map<String, Object> result =
            GausVibeServer.callPaths("com.example.Entry.start", "com.example.Target.finish", 5, graph);
        java.util.List<?> paths = (java.util.List<?>) result.get("paths");
        assertNotNull(paths);
        assertTrue(paths.size() > 0, "the fan-out graph must produce chains, got: " + result);
        assertTrue(paths.size() <= 10,
            "20 distinct chains exist; the answer must cap at 10, got: " + paths.size());
        assertEquals(paths.size(), ((Number) result.get("path_count")).intValue());
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
