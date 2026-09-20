// Generated GausVibe Improvements
// Based on GLM training data analysis
// Generated: 2026-09-20T17:44:09.782989

package dk.gausdalfind.improvements;

/**
 * Performance improvements for GausVibe based on GLM training data analysis.
 *
 * Key findings from GLM 5.2 coding and debugging traces:
 * - Models frequently use grep/find/sed for code exploration
 * - These operations are expensive and error-prone
 * - GausVibe can provide structured, efficient alternatives
 */

import dk.gausdalfind.model.*;
import dk.gausdalfind.queries.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;

/**
 * LRU cache for file system operations.
 * Reduces expensive Files.walk() and Files.find() calls.
 */
public final class FileSystemCache {
    private static final int DEFAULT_CACHE_SIZE = 100;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes

    private final LinkedHashMap<Path, CacheEntry> cache;
    private final int maxSize;

    @FunctionalInterface
    private interface CacheLoader<T> {
        T load(Path key) throws IOException;
    }

    private static class CacheEntry {
        final Object value;
        final long timestamp;

        CacheEntry(Object value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    public FileSystemCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<Path, CacheEntry>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, CacheEntry> eldest) {
                return size() > FileSystemCache.this.maxSize;
            }
        };
    }

    public FileSystemCache() {
        this(DEFAULT_CACHE_SIZE);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Path key, CacheLoader<T> loader) throws IOException {
        Path canonical = key.toAbsolutePath().normalize();
        synchronized (cache) {
            CacheEntry entry = cache.get(canonical);
            if (entry != null && !entry.isExpired(CACHE_TTL_MS)) {
                return (T) entry.value;
            }
            T value = loader.load(canonical);
            cache.put(canonical, new CacheEntry(value));
            return value;
        }
    }

    public void invalidate(Path key) {
        Path canonical = key.toAbsolutePath().normalize();
        synchronized (cache) {
            cache.remove(canonical);
        }
    }

    public void invalidateAll() {
        synchronized (cache) {
            cache.clear();
        }
    }

    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }
}

/**
 * Inverted index for text search in code.
 * Replaces grep operations with structured queries.
 */
public final class TextSearchIndex {
    private final Map<String, Set<Node>> index = new ConcurrentHashMap<>();
    private final Graph graph;

    public TextSearchIndex(Graph graph) {
        this.graph = graph;
    }

    public void build() {
        index.clear();
        for (Node node : graph.getAllNodes()) {
            index(node);
        }
    }

    public void index(Node node) {
        String content = getNodeContent(node);
        for (String token : tokenize(content)) {
            index.computeIfAbsent(token.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(node);
        }
    }

    public List<Node> search(String query) {
        Set<Node> results = new HashSet<>();
        for (String token : tokenize(query)) {
            Set<Node> nodes = index.get(token.toLowerCase());
            if (nodes != null) {
                results.addAll(nodes);
            }
        }
        return new ArrayList<>(results);
    }

    private String getNodeContent(Node node) {
        if (node instanceof MethodNode) {
            return ((MethodNode) node).getName() + " " + ((MethodNode) node).getSignature();
        } else if (node instanceof ClassNode) {
            return ((ClassNode) node).getQualifiedName();
        } else if (node instanceof FieldNode) {
            return ((FieldNode) node).getName();
        }
        return node.getId();
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) return tokens;
        // Split by camelCase, snake_case, and other common patterns
        // Simple tokenization for now - can be enhanced with proper lexer
        String[] parts = text.split("[^a-zA-Z0-9_]");
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}

/**
 * Pre-computed call graph index for fast call relationship queries.
 * Replaces manual traversal of CALLS edges.
 */
public final class CallGraphIndex {
    private final Map<String, Set<String>> callers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> callees = new ConcurrentHashMap<>();
    private final Graph graph;

    public CallGraphIndex(Graph graph) {
        this.graph = graph;
    }

    public void build() {
        callers.clear();
        callees.clear();
        for (Edge edge : graph.getEdgesByType(EdgeTypes.CALLS)) {
            String from = edge.getFromId();
            String to = edge.getToId();
            callers.computeIfAbsent(to, k -> ConcurrentHashMap.newKeySet()).add(from);
            callees.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet()).add(to);
        }
    }

    public Set<String> getCallers(String methodId) {
        return Collections.unmodifiableSet(callers.getOrDefault(methodId, Collections.emptySet()));
    }

    public Set<String> getCallees(String methodId) {
        return Collections.unmodifiableSet(callees.getOrDefault(methodId, Collections.emptySet()));
    }

    public Set<String> getCallersTransitive(String methodId) {
        return getTransitive(methodId, this::getCallers);
    }

    public Set<String> getCalleesTransitive(String methodId) {
        return getTransitive(methodId, this::getCallees);
    }

    private Set<String> getTransitive(String start, 
            java.util.function.Function<String, Set<String>> getter) {
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> result = ConcurrentHashMap.newKeySet();
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> next = getter.apply(current);
            for (String n : next) {
                if (!visited.contains(n)) {
                    visited.add(n);
                    result.add(n);
                    queue.add(n);
                }
            }
        }

        return result;
    }
}
