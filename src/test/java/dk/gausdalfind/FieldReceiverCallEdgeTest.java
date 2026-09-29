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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the ES round-4 finding: a call through a private
 * final field whose name is also a constructor parameter
 * (TransportCreateIndexAction.createIndexService.createIndex(...))
 * produced no CALLS edge, so "who calls createIndex" answered 0 callers.
 */
class FieldReceiverCallEdgeTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-field-receiver-test");
        writeFile("src/main/java/com/example/TimeValue.java",
            "package com.example;\n" +
            "public class TimeValue { private final long v; public TimeValue(long v) { this.v = v; } }\n");
        writeFile("src/main/java/com/example/MetadataCreateIndexService.java",
            "package com.example;\n" +
            "public class MetadataCreateIndexService {\n" +
            "    public void createIndex(\n" +
            "        TimeValue masterNodeTimeout,\n" +
            "        TimeValue ackTimeout,\n" +
            "        TimeValue waitForActiveShardsTimeout,\n" +
            "        String request,\n" +
            "        String listener) { }\n" +
            "}\n");
        // Same shape as TransportCreateIndexAction: field, constructor
        // parameter with the same name, then a method calling through the field.
        writeFile("src/main/java/com/example/TransportCreateIndexAction.java",
            "package com.example;\n" +
            "public class TransportCreateIndexAction extends BaseAction {\n" +
            "    private final MetadataCreateIndexService createIndexService;\n" +
            "    private final String listener;\n" +
            "\n" +
            "    public TransportCreateIndexAction(\n" +
            "        MetadataCreateIndexService createIndexService,\n" +
            "        String listener) {\n" +
            "        this.createIndexService = createIndexService;\n" +
            "        this.listener = listener;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void masterOperation(\n" +
            "        String task,\n" +
            "        final String request) {\n" +
            "        createIndexService.createIndex(\n" +
            "            new TimeValue(1),\n" +
            "            new TimeValue(2),\n" +
            "            new TimeValue(2),\n" +
            "            request,\n" +
            "            listener.length() > 0 ? task : null\n" +
            "        );\n" +
            "    }\n" +
            "}\n");
        writeFile("src/main/java/com/example/BaseAction.java",
            "package com.example;\n" +
            "public abstract class BaseAction {\n" +
            "    protected abstract void masterOperation(String task, String request);\n" +
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
    void fieldReceiverCallProducesCallsEdge() {
        ClassNode service = query.findClassByQualifiedName("com.example.MetadataCreateIndexService").orElseThrow();
        MethodNode createIndex = query.getMethods(service).stream()
            .filter(m -> m.getName().equals("createIndex"))
            .findFirst()
            .orElseThrow();

        List<MethodNode> callers = query.getCallers(createIndex);
        assertTrue(
            callers.stream().anyMatch(m -> m.getName().equals("masterOperation")),
            "masterOperation must appear as a caller of createIndex, got: "
                + callers.stream().map(MethodNode::getQualifiedName).toList()
        );
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
