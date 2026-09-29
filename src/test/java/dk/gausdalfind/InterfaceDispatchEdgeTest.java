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
 * Regression test for interface-dispatch CALLS edges: a call resolved to an
 * interface method must also produce CALLS edges to concrete overrides, so
 * callpath and callers queries cross dynamic dispatch (ES round 4:
 * callpath could not cross RestHandler.handleRequest -> implementations).
 */
class InterfaceDispatchEdgeTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-dispatch-test");
        writeFile("src/main/java/com/example/Greeter.java",
            "package com.example;\n" +
            "public interface Greeter {\n" +
            "    void greet();\n" +
            "}\n");
        writeFile("src/main/java/com/example/EnglishGreeter.java",
            "package com.example;\n" +
            "public class EnglishGreeter implements Greeter {\n" +
            "    @Override\n" +
            "    public void greet() { System.out.println(\"hello\"); }\n" +
            "}\n");
        writeFile("src/main/java/com/example/AbstractBase.java",
            "package com.example;\n" +
            "public abstract class AbstractBase {\n" +
            "    protected abstract void doExecute(String request);\n" +
            "}\n");
        writeFile("src/main/java/com/example/ConcreteAction.java",
            "package com.example;\n" +
            "public class ConcreteAction extends AbstractBase {\n" +
            "    @Override\n" +
            "    protected void doExecute(String request) { System.out.println(request); }\n" +
            "}\n");
        writeFile("src/main/java/com/example/Caller.java",
            "package com.example;\n" +
            "public class Caller {\n" +
            "    private final Greeter greeter = new EnglishGreeter();\n" +
            "    private final AbstractBase action = new ConcreteAction();\n" +
            "    public void run() {\n" +
            "        greeter.greet();\n" +
            "        action.doExecute(\"x\");\n" +
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
    void interfaceCallReachesConcreteImplementation() {
        ClassNode impl = query.findClassByQualifiedName("com.example.EnglishGreeter").orElseThrow();
        MethodNode greet = query.getMethods(impl).stream()
            .filter(m -> m.getName().equals("greet"))
            .findFirst()
            .orElseThrow();

        List<MethodNode> callers = query.getCallers(greet);
        assertTrue(
            callers.stream().anyMatch(m -> m.getName().equals("run")),
            "Caller.run must reach EnglishGreeter.greet through the interface edge, got: "
                + callers.stream().map(MethodNode::getQualifiedName).toList()
        );
    }

    @Test
    void abstractClassCallReachesConcreteOverride() {
        ClassNode impl = query.findClassByQualifiedName("com.example.ConcreteAction").orElseThrow();
        ClassNode base = query.findClassByQualifiedName("com.example.AbstractBase").orElseThrow();
        MethodNode abstractMethod = query.getMethods(base).stream()
            .filter(m -> m.getName().equals("doExecute"))
            .findFirst()
            .orElseThrow();
        MethodNode doExecute = query.getMethods(impl).stream()
            .filter(m -> m.getName().equals("doExecute"))
            .findFirst()
            .orElseThrow();

        List<MethodNode> abstractCallers = query.getCallers(abstractMethod);
        List<MethodNode> callers = query.getCallers(doExecute);
        assertTrue(
            callers.stream().anyMatch(m -> m.getName().equals("run")),
            "Caller.run must reach ConcreteAction.doExecute through the abstract edge. "
                + "abstract method callers=" + abstractCallers.stream().map(MethodNode::getQualifiedName).toList()
                + ", concrete callers=" + callers.stream().map(MethodNode::getQualifiedName).toList()
        );
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
