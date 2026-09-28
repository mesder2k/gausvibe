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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests single-file incremental updates via GausVibeBuilder.updateFile:
 * the file's old nodes and edges are removed, the file is reparsed, and
 * call-site resolution re-links callers in other files.
 */
class UpdateFileTest {

    private static Path projectDir;
    private static GausVibeBuilder builder;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-update-test");
        writeFile("com/example/Producer.java",
            "package com.example;\n" +
            "public class Producer {\n" +
            "    public int produce(int x) { return x * 2; }\n" +
            "}\n");
        writeFile("com/example/Consumer.java",
            "package com.example;\n" +
            "public class Consumer {\n" +
            "    private final Producer producer = new Producer();\n" +
            "    public int consume(int x) { return producer.produce(x); }\n" +
            "}\n");
        
        builder = new GausVibeBuilder(projectDir);
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
    void updateAddsNewMethodFromFile() throws IOException {
        int methodsBefore = query.getMethods(producer()).size();
        assertEquals(1, methodsBefore);
        
        writeFile("com/example/Producer.java",
            "package com.example;\n" +
            "public class Producer {\n" +
            "    public int produce(int x) { return x * 2; }\n" +
            "    public int produceTwice(int x) { return produce(x) + produce(x); }\n" +
            "}\n");
        
        GausVibeBuilder.UpdateResult result = builder.updateFile(
            projectDir.resolve("com/example/Producer.java"));
        
        assertTrue(result.removedNodes() >= 2, "old class/method nodes removed: " + result.removedNodes());
        assertTrue(result.addedNodes() >= 2, "re-added nodes: " + result.addedNodes());
        List<MethodNode> methods = query.getMethods(producer());
        assertEquals(2, methods.size(), "new method visible in graph");
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("produceTwice")));
    }

    @Test
    void updateRelinksCallersAfterSignatureChange() throws IOException {
        // sanity: consume() calls produce()
        MethodNode produce = query.findMethodsByName("produce").get(0);
        assertFalse(query.getCallers(produce).isEmpty(), "caller edge exists before");
        String oldId = produce.getId();
        
        // change the signature: node ID changes but the name survives, so
        // the caller in the other file re-resolves against the new node
        writeFile("com/example/Producer.java",
            "package com.example;\n" +
            "public class Producer {\n" +
            "    public int produce(long x) { return (int) x * 2; }\n" +
            "}\n");
        builder.updateFile(projectDir.resolve("com/example/Producer.java"));
        
        List<MethodNode> newProduce = query.findMethodsByName("produce");
        assertEquals(1, newProduce.size());
        assertNotEquals(oldId, newProduce.get(0).getId(), "signature change produced a new node id");
        List<MethodNode> callers = query.getCallers(newProduce.get(0));
        assertFalse(callers.isEmpty(),
            "caller in other file re-linked to new signature node");
        assertTrue(callers.stream().anyMatch(c -> c.getName().equals("consume")));
    }

    @Test
    void updateHandlesDeletedContent() throws IOException {
        writeFile("com/example/Consumer.java",
            "package com.example;\n" +
            "public class Consumer {\n" +
            "    public int other() { return 42; }\n" +
            "}\n");
        builder.updateFile(projectDir.resolve("com/example/Consumer.java"));
        
        Optional<ClassNode> consumer = query.findClassByQualifiedName("com.example.Consumer");
        assertTrue(consumer.isPresent());
        List<MethodNode> methods = query.getMethods(consumer.get());
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("other")));
        assertTrue(methods.stream().noneMatch(m -> m.getName().equals("consume")));
    }

    private static ClassNode producer() {
        return query.findClassByQualifiedName("com.example.Producer").orElseThrow();
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
