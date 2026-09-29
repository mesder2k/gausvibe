package dk.gausdalfind.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Indexes;
import dk.gausdalfind.serializer.JsonSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Disk cache for the parsed graph so daemon startup does not re-parse a
 * project that has not changed. The ES server module takes ~20 minutes
 * to parse; loading the serialized graph takes seconds.
 *
 * Layout under <project>/.gausvibe/:
 * - graph-cache.json  - the serialized graph (JsonSerializer format)
 * - graph-cache.meta.json - fingerprint, cache version, value index
 *
 * The fingerprint covers the java sources (file count, total size, max
 * mtime, skipping target/ and .gausvibe/). Any mismatch or parse failure
 * falls back to a full rebuild. POST /edited invalidates the cache;
 * POST /refresh rewrites it after a rebuild.
 */
final class GraphCache {

    /** Bump when node/edge semantics change so old caches are ignored. */
    private static final String CACHE_VERSION = "2";

    private GraphCache() {}

    /**
     * Returns the cached graph when it exists, its version and fingerprint
     * match the current sources; null otherwise. On a successful load the
     * value index is restored into the graph's indexes.
     */
    static Graph load(Path projectRoot) {
        Path metaFile = metaFile(projectRoot);
        Path graphFile = graphFile(projectRoot);
        if (!Files.isRegularFile(metaFile) || !Files.isRegularFile(graphFile)) {
            return null;
        }
        try {
            JsonObject meta = JsonParser.parseString(Files.readString(metaFile)).getAsJsonObject();
            if (!meta.has("version") || !CACHE_VERSION.equals(meta.get("version").getAsString())) {
                return null;
            }
            String fingerprint = sourceFingerprint(projectRoot);
            if (!meta.has("fingerprint") || !fingerprint.equals(meta.get("fingerprint").getAsString())) {
                return null;
            }
            long start = System.currentTimeMillis();
            Graph loaded = new JsonSerializer().deserialize(graphFile);
            if (loaded == null || loaded.getNodeCount() == 0) {
                return null;
            }
            restoreValues(loaded, meta);
            System.out.println("Graph cache hit: " + loaded.getNodeCount() + " nodes, "
                + loaded.getEdgeCount() + " edges loaded in "
                + (System.currentTimeMillis() - start) + " ms (fingerprint matched)");
            return loaded;
        } catch (Exception e) {
            System.out.println("Graph cache unusable, rebuilding: " + e);
            return null;
        }
    }

    /** Writes the cache after a fresh build. Best effort. */
    static void write(Path projectRoot, Graph graph) {
        try {
            Path dir = projectRoot.resolve(".gausvibe");
            Files.createDirectories(dir);
            new JsonSerializer().serialize(graph, graphFile(projectRoot));

            JsonObject meta = new JsonObject();
            meta.addProperty("version", CACHE_VERSION);
            meta.addProperty("fingerprint", sourceFingerprint(projectRoot));
            meta.addProperty("createdAt", java.time.Instant.now().toString());
            JsonArray values = new JsonArray();
            // the value index lives only in Indexes; persist it explicitly
            for (Indexes.ValueOccurrence occ : graph.getIndexes().getAllValues()) {
                JsonObject o = new JsonObject();
                o.addProperty("value", occ.value());
                o.addProperty("class", occ.classFqn());
                o.addProperty("field", occ.fieldName());
                o.addProperty("file", occ.file() != null ? occ.file().toString() : null);
                o.addProperty("line", occ.line());
                values.add(o);
            }
            meta.add("values", values);
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            Files.writeString(metaFile(projectRoot), gson.toJson(meta));
            System.out.println("Graph cache written: " + graph.getNodeCount() + " nodes, "
                + graph.getEdgeCount() + " edges");
        } catch (Exception e) {
            System.out.println("Graph cache write failed (ignored): " + e);
        }
    }

    /** Invalidates the cache (after POST /edited reparses a file). */
    static void invalidate(Path projectRoot) {
        try {
            Files.deleteIfExists(metaFile(projectRoot));
        } catch (IOException e) {
            // best effort
        }
    }

    private static void restoreValues(Graph graph, JsonObject meta) {
        if (!meta.has("values")) {
            return;
        }
        JsonArray values = meta.getAsJsonArray("values");
        for (int i = 0; i < values.size(); i++) {
            JsonObject o = values.get(i).getAsJsonObject();
            String file = o.has("file") && !o.get("file").isJsonNull()
                ? o.get("file").getAsString() : null;
            graph.getIndexes().indexValue(new Indexes.ValueOccurrence(
                o.get("value").getAsString(),
                o.get("class").getAsString(),
                o.get("field").getAsString(),
                file != null ? Path.of(file) : null,
                o.get("line").getAsInt()));
        }
    }

    /**
     * Cheap fingerprint of the java sources: file count, total size and
     * newest mtime, skipping build output and gausvibe state directories.
     */
    static String sourceFingerprint(Path projectRoot) {
        int count = 0;
        long totalSize = 0;
        long maxMtime = 0;
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            List<Path> files = walk
                .filter(p -> {
                    String s = p.toString();
                    return !s.contains("/target/") && !s.contains("/.gausvibe/")
                        && !s.contains("/.git/") && s.endsWith(".java");
                })
                .toList();
            for (Path p : files) {
                try {
                    var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
                    count++;
                    totalSize += attrs.size();
                    if (attrs.lastModifiedTime().toMillis() > maxMtime) {
                        maxMtime = attrs.lastModifiedTime().toMillis();
                    }
                } catch (IOException e) {
                    // unreadable file: treat as change
                    maxMtime = Long.MAX_VALUE;
                }
            }
        } catch (IOException e) {
            return "unreadable:" + System.nanoTime();
        }
        return count + ":" + totalSize + ":" + maxMtime;
    }

    private static Path graphFile(Path projectRoot) {
        return projectRoot.resolve(".gausvibe/graph-cache.json");
    }

    private static Path metaFile(Path projectRoot) {
        return projectRoot.resolve(".gausvibe/graph-cache.meta.json");
    }
}
