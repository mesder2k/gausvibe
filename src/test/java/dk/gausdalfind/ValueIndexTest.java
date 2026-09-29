package dk.gausdalfind;

import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Indexes;
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
 * Regression test for the field-initializer value index: "where is 9200
 * defined" must resolve to the declaring field via the graph instead of
 * failing with matched=null (ES round 4 locate task).
 */
class ValueIndexTest {

    private static Path projectDir;
    private static Graph graph;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-value-test");
        writeFile("src/main/java/com/example/HttpTransportSettings.java",
            "package com.example;\n" +
            "public class HttpTransportSettings {\n" +
            "    public static final Object SETTING_HTTP_PORT = new Setting(\n" +
            "        \"http.port\",\n" +
            "        \"9200-9300\",\n" +
            "        8094,\n" +
            "        true\n" +
            "    );\n" +
            "    public static final int MAX_PORT = 9300;\n" +
            "    private static final String LABEL = \"default-http\";\n" +
            "}\n");
        writeFile("src/main/java/com/example/Setting.java",
            "package com.example;\n" +
            "public class Setting {\n" +
            "    public Setting(String name, String dflt, int port, boolean nodeScope) { }\n" +
            "}\n");

        GausVibeBuilder builder = new GausVibeBuilder(projectDir);
        graph = builder.build();
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
    void findsPortDefaultBySubstring() {
        List<Indexes.ValueOccurrence> hits =
            graph.getIndexes().findValuesContaining("9200", 20);
        assertTrue(hits.size() >= 1,
            "9200 must be found in the SETTING_HTTP_PORT initializer");
        Indexes.ValueOccurrence hit = hits.get(0);
        assertEquals("com.example.HttpTransportSettings", hit.classFqn());
        assertEquals("SETTING_HTTP_PORT", hit.fieldName());
        assertEquals("9200-9300", hit.value());
        assertTrue(hit.line() > 0, "occurrence must carry a line number");
    }

    @Test
    void findsQuotedLiteralAndPlainNumber() {
        List<Indexes.ValueOccurrence> label =
            graph.getIndexes().findValuesContaining("default-http", 20);
        assertEquals(1, label.size());
        assertEquals("LABEL", label.get(0).fieldName());

        List<Indexes.ValueOccurrence> maxPort =
            graph.getIndexes().findValuesContaining("9300", 20);
        assertTrue(maxPort.stream().anyMatch(v -> v.fieldName().equals("MAX_PORT")),
            "plain numeric constant must be indexed");
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
