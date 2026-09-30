package dk.gausdalfind;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.declaration.ClassNode;
import dk.gausdalfind.model.declaration.MethodNode;
import dk.gausdalfind.parser.JavaParserConfig;
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
 * Regression test for the 41 ES files that failed to parse and were
 * silently dropped from the graph (round-5 build log). Three syntax
 * shapes were responsible: a local variable named `permits` (a
 * contextual keyword that javaparser 3.25.9 over-reserved), pattern
 * matching in switch (final only at language level 21), and local
 * interfaces. Fixed by upgrading javaparser to 3.28.2 and the language
 * level to JAVA_21.
 */
class ModernSyntaxParseTest {

    private static Path projectDir;
    private static Graph graph;
    private static GraphQueryEngine query;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-modern-syntax-test");

        // `permits` used as a legal local variable name (TaskManager.java:832)
        writeFile("src/main/java/com/example/Tracker.java",
            "package com.example;\n" +
            "public class Tracker {\n" +
            "    public void drain() throws InterruptedException {\n" +
            "        final Object permits = new Object();\n" +
            "        synchronized (permits) { permits.notifyAll(); }\n" +
            "    }\n" +
            "}\n");

        // Pattern matching in switch (IndexReshardingMetadata.java:148-167)
        writeFile("src/main/java/com/example/Resharding.java",
            "package com.example;\n" +
            "public class Resharding {\n" +
            "    sealed interface State permits State.Noop, State.Split {\n" +
            "        record Noop() implements State { }\n" +
            "        record Split() implements State { }\n" +
            "    }\n" +
            "    public String name(State state) {\n" +
            "        return switch (state) {\n" +
            "            case State.Noop ignored -> \"noop\";\n" +
            "            case State.Split ignored -> \"split\";\n" +
            "        };\n" +
            "    }\n" +
            "}\n");

        // Local interface inside a method (PluginIntrospectorTests.java)
        writeFile("src/main/java/com/example/Introspector.java",
            "package com.example;\n" +
            "public class Introspector {\n" +
            "    public Object describe(Object o) {\n" +
            "        interface Named { String name(); }\n" +
            "        if (o instanceof Named named) { return named.name(); }\n" +
            "        return o;\n" +
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
    void contextualKeywordIdentifierParses() {
        ClassNode tracker = query.findClassByQualifiedName("com.example.Tracker").orElseThrow();
        assertTrue(query.getMethods(tracker).stream().anyMatch(m -> m.getName().equals("drain")),
            "Tracker.drain must be in the graph");
    }

    @Test
    void sealedAndPatternSwitchParse() {
        ClassNode resharding = query.findClassByQualifiedName("com.example.Resharding").orElseThrow();
        assertTrue(query.getMethods(resharding).stream().anyMatch(m -> m.getName().equals("name")),
            "pattern-switch method must be in the graph");
        // the nested sealed interface and its records must be indexed too
        query.findClassByQualifiedName("com.example.Resharding$State").orElseThrow();
        query.findClassByQualifiedName("com.example.Resharding$State$Noop").orElseThrow();
        query.findClassByQualifiedName("com.example.Resharding$State$Split").orElseThrow();
    }

    @Test
    void localInterfaceParses() {
        ClassNode introspector = query.findClassByQualifiedName("com.example.Introspector").orElseThrow();
        assertTrue(query.getMethods(introspector).stream().anyMatch(m -> m.getName().equals("describe")),
            "method containing a local interface must be in the graph");
    }

    @Test
    void parserConfigAcceptsModernSyntaxDirectly() {
        ParseResult<CompilationUnit> result = JavaParserConfig.newParser().parse(
            "package com.example;\n"
            + "public class P { Object o = new Object();\n"
            + "  String f(Object x) { return switch (x) { case Integer i -> \"i\"; default -> \"?\"; }; } }\n");
        assertTrue(result.isSuccessful(), "pattern switch must parse: " + result.getProblems());
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
