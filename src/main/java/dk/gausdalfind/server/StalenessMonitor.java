package dk.gausdalfind.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Detects when the indexed project has changed on disk since the last
 * build, so /ask responses can be stamped as stale.
 *
 * Detection is mtime-based and throttled: a scan runs at most once per
 * scan interval, and only when a query endpoint is actually used. False
 * positives (same-content new mtime) only cost a refresh hint; false
 * negatives are not possible for files the walk can see.
 */
public class StalenessMonitor {

    private static final long SCAN_INTERVAL_MS = 30_000;

    private final Path projectRoot;
    private volatile Map<Path, Long> baseline = Map.of();
    private volatile List<String> changedFiles = List.of();
    private volatile boolean stale = false;
    private volatile long lastScan = 0;

    public StalenessMonitor(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * Records the current file state as the fresh baseline.
     */
    public synchronized void captureBaseline() throws IOException {
        baseline = scanFiles();
        changedFiles = List.of();
        stale = false;
        lastScan = System.currentTimeMillis();
    }

    /**
     * Runs a scan if the throttle interval has elapsed.
     */
    public synchronized void checkIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastScan < SCAN_INTERVAL_MS) {
            return;
        }
        try {
            scan();
        } catch (IOException e) {
            System.err.println("Warning: staleness scan failed: " + e.getMessage());
        }
        lastScan = now;
    }

    /**
     * Compares the current file state against the baseline.
     */
    private void scan() throws IOException {
        Map<Path, Long> current = scanFiles();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<Path, Long> e : current.entrySet()) {
            Long before = baseline.get(e.getKey());
            if (before == null || !before.equals(e.getValue())) {
                changed.add(e.getKey().toString());
            }
        }
        for (Path p : baseline.keySet()) {
            if (!current.containsKey(p)) {
                changed.add(p + " (deleted)");
            }
        }
        this.changedFiles = changed;
        this.stale = !changed.isEmpty();
    }

    private Map<Path, Long> scanFiles() throws IOException {
        Map<Path, Long> files = new HashMap<>();
        if (!Files.isDirectory(projectRoot)) {
            return files;
        }
        try (Stream<Path> paths = Files.find(projectRoot, Integer.MAX_VALUE,
                (p, attrs) -> attrs.isRegularFile() && p.toString().endsWith(".java"))) {
            paths.forEach(p -> {
                try {
                    files.put(p, Files.getLastModifiedTime(p).toMillis());
                } catch (IOException ignored) {
                    // file vanished mid-scan; skip
                }
            });
        }
        return files;
    }

    public boolean isStale() {
        return stale;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }
}
