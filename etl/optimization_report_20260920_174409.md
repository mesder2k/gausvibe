# GausVibe Optimization Report

Generated: 2026-09-20T17:44:09.782709

Analyzed 0 trajectories

## Summary Statistics

- Total trajectories: 0
- Language distribution:

## Expensive Operations Found

Operations that could be replaced with GausVibe queries:

## Recommended Code Improvements

## Pattern Analysis

## Task Category Analysis


## GausVibe Integration Suggestions

Based on the analysis, here are suggested improvements:

### 1. File System Cache
Implement a caching layer for file system operations:
```java
// In JavaFileCollector.java
private static final Map<Path, List<Path>> fileCache = new ConcurrentHashMap<>();
private static final Map<Path, Long> directoryTimestamps = new ConcurrentHashMap<>();

public static List<Path> collectWithCache(Path directory) throws IOException {
    Path canonicalDir = directory.toAbsolutePath().normalize();
    Long lastModified = Files.getLastModifiedTime(canonicalDir).toMillis();
    Long cachedTime = directoryTimestamps.get(canonicalDir);
    
    if (cachedTime != null && cachedTime == lastModified) {
        return fileCache.get(canonicalDir);
    }
    
    List<Path> files = collect(canonicalDir);
    fileCache.put(canonicalDir, files);
    directoryTimestamps.put(canonicalDir, lastModified);
    return files;
}
```

### 2. Text Search Index
Add inverted index for fast text search:
```java
// In GraphQueryEngine.java
private final Map<String, Set<Node>> textIndex = new ConcurrentHashMap<>();

public void buildTextIndex() {
    for (Node node : graph.getAllNodes()) {
        String content = getNodeContent(node);
        for (String token : extractTokens(content)) {
            textIndex.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet()).add(node);
        }
    }
}

public List<Node> searchText(String query) {
    Set<Node> results = new HashSet<>();
    for (String token : extractTokens(query)) {
        Set<Node> nodes = textIndex.get(token);
        if (nodes != null) results.addAll(nodes);
    }
    return new ArrayList<>(results);
}
```

### 3. Call Graph Index
Pre-compute and cache call relationships:
```java
// In Indexes.java
private final Map<String, Set<String>> callersIndex = new ConcurrentHashMap<>();
private final Map<String, Set<String>> calleesIndex = new ConcurrentHashMap<>();

public void buildCallGraphIndex() {
    for (Edge edge : graph.getEdgesByType(EdgeTypes.CALLS)) {
        String caller = edge.getFromId();
        String callee = edge.getToId();
        callersIndex.computeIfAbsent(callee, k -> ConcurrentHashMap.newKeySet()).add(caller);
        calleesIndex.computeIfAbsent(caller, k -> ConcurrentHashMap.newKeySet()).add(callee);
    }
}

public Set<String> getCallers(String methodId) {
    return callersIndex.getOrDefault(methodId, Collections.emptySet());
}

public Set<String> getCallees(String methodId) {
    return calleesIndex.getOrDefault(methodId, Collections.emptySet());
}
```

