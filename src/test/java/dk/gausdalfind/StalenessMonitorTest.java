package dk.gausdalfind;

import dk.gausdalfind.server.StalenessMonitor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests mtime-based staleness detection.
 */
class StalenessMonitorTest {

    private static Path projectDir;

    @BeforeAll
    static void setup() throws IOException {
        projectDir = Files.createTempDirectory("gausvibe-stale-test");
        writeFile("com/example/Foo.java", "public class Foo { }\n");
        writeFile("com/example/Bar.java", "public class Bar { }\n");
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
    void freshBaselineIsNotStale() throws IOException {
        StalenessMonitor monitor = new StalenessMonitor(projectDir);
        monitor.captureBaseline();
        monitor.checkIfDue(); // throttled; but a scan may run - still fresh
        assertFalse(monitor.isStale());
        assertTrue(monitor.getChangedFiles().isEmpty());
    }

    @Test
    void modifiedFileMarksStale() throws IOException {
        StalenessMonitor monitor = new StalenessMonitor(projectDir);
        monitor.captureBaseline();
        
        // modify a file (content change, new mtime)
        Path foo = projectDir.resolve("com/example/Foo.java");
        Files.writeString(foo, "public class Foo { int x; }\n");
        
        forceScan(monitor);
        assertTrue(monitor.isStale(), "modified file detected");
        assertTrue(monitor.getChangedFiles().stream().anyMatch(f -> f.endsWith("Foo.java")));
        
        // baseline recapture clears the flag
        monitor.captureBaseline();
        assertFalse(monitor.isStale());
    }

    @Test
    void deletedAndAddedFilesMarkStale() throws IOException {
        StalenessMonitor monitor = new StalenessMonitor(projectDir);
        monitor.captureBaseline();
        
        Files.delete(projectDir.resolve("com/example/Bar.java"));
        writeFile("com/example/Baz.java", "public class Baz { }\n");
        
        forceScan(monitor);
        assertTrue(monitor.isStale());
        assertTrue(monitor.getChangedFiles().stream().anyMatch(f -> f.contains("deleted")),
            "deletion reported: " + monitor.getChangedFiles());
        assertTrue(monitor.getChangedFiles().stream().anyMatch(f -> f.endsWith("Baz.java")),
            "addition reported: " + monitor.getChangedFiles());
    }

    /**
     * The monitor throttles scans to one per 30s; tests reset the last-scan
     * time via reflection to force an immediate scan.
     */
    private static void forceScan(StalenessMonitor monitor) throws IOException {
        try {
            java.lang.reflect.Field f = StalenessMonitor.class.getDeclaredField("lastScan");
            f.setAccessible(true);
            f.setLong(monitor, 0L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        monitor.checkIfDue();
    }

    private static void writeFile(String relativePath, String content) throws IOException {
        Path p = projectDir.resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
