package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.declaration.FieldNode;
import dk.gausdalfind.model.declaration.MethodNode;
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.server.GausVibeServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-5 bakeoff fixes: field-name search route, fuzzy-match confidence
 * gate, and relative answer paths. Each test mirrors a measured round-4/5
 * failure: "where is the separator pattern defined" (jackson Q5), the
 * "number 8094" fuzzy mis-match (r4 Q9), and absolute-path payload bloat.
 */
class AskRouterRound5Test {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-round5-test");
        writeFile("src/main/java/com/example/VersionUtil.java",
            "package com.example;\n" +
            "import java.util.regex.Pattern;\n" +
            "public class VersionUtil {\n" +
            "    private final static Pattern V_SEP = Pattern.compile(\"[-_./;:]\");\n" +
            "    public static int[] parseParts(String s) { return new int[]{0, 0, 0}; }\n" +
            "}\n");
        writeFile("src/main/java/com/example/ValueIndexTestHelper.java",
            "package com.example;\n" +
            "public class ValueIndexTestHelper {\n" +
            "    public int findsQuotedLiteralAndPlainNumber() { return 42; }\n" +
            "}\n");
        writeFile("src/test/java/com/example/VersionUtilTest.java",
            "package com.example;\n" +
            "public class VersionUtilTest {\n" +
            "    public void testParseParts() {\n" +
            "        int[] parts = VersionUtil.parseParts(\"2.10.0\");\n" +
            "        org.junit.jupiter.api.Assertions.assertEquals(3, parts.length);\n" +
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
    void fieldNameSegmentsSplitsCamelHumpsAndUnderscores() {
        assertEquals(List.of("v", "sep"),
            GausVibeServer.AskHandler.fieldNameSegments("V_SEP"));
        assertEquals(List.of("snapshot", "info"),
            GausVibeServer.AskHandler.fieldNameSegments("snapshotInfo"));
        assertEquals(List.of("port"),
            GausVibeServer.AskHandler.fieldNameSegments("PORT"));
    }

    @Test
    void separatorQuestionFindsFieldByStem() {
        // round-5 jackson Q5: the agent does not know the field name
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers(
            "Where is the separator pattern used to split a version string defined?");
        List<FieldNode> fields = GausVibeServer.AskHandler.findFieldsByStem(candidates, query);
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("V_SEP")),
            "separator question must find V_SEP, got: " + fields);
    }

    @Test
    void singleShortStemDoesNotFuzzyMatchAMethod() {
        // round-4 Q9: "where is the number 8094 defined" mis-matched
        // findsQuotedLiteralAndPlainNumber via the stem "number"
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers(
            "where is the number 8094 defined");
        MethodNode fuzzy = GausVibeServer.AskHandler.resolveMethodFuzzy(candidates, query);
        assertNull(fuzzy, "single short stem must not resolve to a method");
    }

    @Test
    void multiStemQuestionsStillFuzzyMatch() {
        List<String> candidates = GausVibeServer.AskHandler.extractIdentifiers(
            "where is quoted literal number finding done");
        MethodNode fuzzy = GausVibeServer.AskHandler.resolveMethodFuzzy(candidates, query);
        assertNotNull(fuzzy, "several question stems must still resolve a method");
        assertEquals("findsQuotedLiteralAndPlainNumber", fuzzy.getName());
    }

    @Test
    void coverageLinkFilterKeepsOnlyRequestedType() {
        // VersionUtilTest covers VersionUtil via call-graph; a class named
        // e.g. VersionUtilDocs would only be name-convention linked
        dk.gausdalfind.model.declaration.ClassNode prod =
            query.findClassByQualifiedName("com.example.VersionUtil").orElseThrow();
        var all = GausVibeServer.TestsHandler.coverageFor(prod, query, null, false);
        var strict = GausVibeServer.TestsHandler.coverageFor(prod, query, Set.of("call-graph"), false);
        List<?> allTests = (List<?>) all.get("tests");
        List<?> strictTests = (List<?>) strict.get("tests");
        assertFalse(allTests.isEmpty(), "name-convention + call-graph tests both present without filter");
        assertTrue(strictTests.stream().allMatch(t ->
                "call-graph".equals(((java.util.Map<?, ?>) t).get("coverage"))),
            "link=call-graph must keep only strict coverage, got: " + strictTests);
        for (Object t : strictTests) {
            assertFalse(((java.util.Map<?, ?>) t).containsKey("test_methods"),
                "includeMethods=false must drop the per-test-method map");
        }
    }

    @Test
    void relativePathStripsProjectRootWhenSet() throws Exception {
        Field projectPathField = GausVibeServer.class.getDeclaredField("projectPath");
        projectPathField.setAccessible(true);
        Object old = projectPathField.get(null);
        try {
            projectPathField.set(null, projectDir.toString());
            String abs = projectDir.resolve("src/main/java/com/example/VersionUtil.java").toString();
            assertEquals("src/main/java/com/example/VersionUtil.java",
                GausVibeServer.relativePath(abs));
            assertEquals("com/example/VersionUtil.java",
                GausVibeServer.relativePath(projectDir + "/com/example/VersionUtil.java"));
        } finally {
            projectPathField.set(null, old);
        }
    }

    @Test
    void relativePathPassthroughWithoutProject() {
        // in unit tests projectPath is not set: input comes back unchanged
        assertNull(GausVibeServer.relativePath((String) null));
        assertEquals("/some/other/path.java", GausVibeServer.relativePath("/some/other/path.java"));
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void longAskAnswersPaginateWithPageParameter() throws Exception {
        // r6d: truncating capped answers induced blind re-probes. Answers
        // now arrive as ~1.5KB pages with a followable page parameter -
        // nothing lost, more pulled only when the agent chooses to.
        Class<?> handler = Class.forName("dk.gausdalfind.server.GausVibeServer$AskHandler");
        java.lang.reflect.Constructor<?> ctor = handler.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object h = ctor.newInstance();
        java.lang.reflect.Field pageField = handler.getDeclaredField("PAGE_BYTES");
        pageField.setAccessible(true);
        int pageBytes = pageField.getInt(null);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < pageBytes * 3 / 10 + 20; i++) {
            big.append("line ").append(i).append(" 0123456789\n");
        }
        java.lang.reflect.Method m = handler.getDeclaredMethod("matched", String.class, String.class, String.class, int.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> result = (java.util.Map<String, Object>)
            m.invoke(h, "q", "tests", big.toString(), 42);
        String answer = (String) result.get("answer");
        assertEquals(big.toString(), answer, "matched() itself no longer truncates - "
            + "pagination is applied in the HTTP layer over the full answer");
    }
}
