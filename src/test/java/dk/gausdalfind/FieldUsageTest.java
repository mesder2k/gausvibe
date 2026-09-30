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
 * Regression test for the field-usage index: "who uses
 * SETTING_HTTP_PORT" style questions returned matched=null in ES rounds
 * 4-5 because fields were not linked to referencing methods.
 */
class FieldUsageTest {

    private static Path projectDir;
    private static Graph graph;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-field-usage-test");
        writeFile("src/main/java/com/example/Config.java",
            "package com.example;\n" +
            "public class Config {\n" +
            "    public static final String PORT = \"9200-9300\";\n" +
            "}\n");
        // simple-name usage inside the same file + qualified usage from
        // another class
        writeFile("src/main/java/com/example/Server.java",
            "package com.example;\n" +
            "public class Server {\n" +
            "    private final String port = Config.PORT;\n" +
            "    public String describe() {\n" +
            "        return \"port=\" + Config.PORT;\n" +
            "    }\n" +
            "}\n");
        writeFile("src/main/java/com/example/Holder.java",
            "package com.example;\n" +
            "public class Holder {\n" +
            "    private String label = \"x\";\n" +
            "    public String render() {\n" +
            "        return label + Config.PORT;\n" +
            "    }\n" +
            "}\n");

        graph = new GausVibeBuilder(projectDir).build();
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
    void staticFieldUsagesResolveAcrossFiles() {
        List<Indexes.FieldUsage> usages =
            graph.getIndexes().getFieldUsages("com.example.Config.PORT");
        assertTrue(usages.stream().anyMatch(u -> u.methodQualifiedName().endsWith("Server.describe")),
            "Config.PORT used inside Server.describe must be indexed, got: " + usages);
        assertTrue(usages.stream().anyMatch(u -> u.methodQualifiedName().endsWith("Holder.render")),
            "Config.PORT used inside Holder.render must be indexed");
        assertTrue(usages.stream().allMatch(u -> u.line() > 0), "usages carry line numbers");
    }

    @Test
    void instanceFieldUsageOfSameClassResolves() {
        List<Indexes.FieldUsage> usages =
            graph.getIndexes().getFieldUsages("com.example.Holder.label");
        assertTrue(usages.stream().anyMatch(u -> u.methodQualifiedName().endsWith("Holder.render")),
            "Holder.label used in Holder.render must be indexed, got: " + usages);
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
