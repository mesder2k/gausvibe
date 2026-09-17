package com.javacodegraph.queries;

import com.javacodegraph.model.*;
import com.javacodegraph.model.declaration.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A caching implementation of JavaGraphQuery that wraps another query engine
 * and caches query results for improved performance.
 * 
 * This is useful when the same queries are executed multiple times,
 * such as in an interactive shell or when processing multiple related requests.
 * 
 * Thread-safe: Uses ConcurrentHashMap for cache storage.
 */
public class CachingQueryEngine implements JavaGraphQuery {
    
    private final JavaGraphQuery delegate;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    
    // Cache statistics
    private long cacheHits = 0;
    private long cacheMisses = 0;
    
    /**
     * Creates a new caching query engine that wraps the given delegate.
     * 
     * @param delegate the underlying query engine to cache results from
     */
    public CachingQueryEngine(JavaGraphQuery delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate query engine cannot be null");
        }
        this.delegate = delegate;
    }
    
    /**
     * Returns the underlying delegate query engine.
     */
    public JavaGraphQuery getDelegate() {
        return delegate;
    }
    
    /**
     * Clears all cached query results.
     */
    public void clearCache() {
        cache.clear();
        cacheHits = 0;
        cacheMisses = 0;
    }
    
    /**
     * Returns the number of cache hits.
     */
    public long getCacheHits() {
        return cacheHits;
    }
    
    /**
     * Returns the number of cache misses.
     */
    public long getCacheMisses() {
        return cacheMisses;
    }
    
    /**
     * Returns the cache hit rate as a value between 0.0 and 1.0.
     */
    public double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0.0 : (double) cacheHits / total;
    }
    
    /**
     * Returns the current cache size (number of cached entries).
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    // Helper method to generate cache keys
    private String cacheKey(String methodName, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append(":");
        for (Object arg : args) {
            if (arg != null) {
                sb.append(arg.toString());
            }
            sb.append(":");
        }
        return sb.toString();
    }
    
    // ==================== CLASS QUERIES ====================
    
    @Override
    public Optional<ClassNode> findClassByQualifiedName(String qn) {
        String key = cacheKey("findClassByQualifiedName", qn);
        Optional<ClassNode> cached = (Optional<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        Optional<ClassNode> result = delegate.findClassByQualifiedName(qn);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> findClassesByName(String name) {
        String key = cacheKey("findClassesByName", name);
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.findClassesByName(name);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> getAllClasses() {
        String key = cacheKey("getAllClasses");
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.getAllClasses();
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> getSubclasses(ClassNode clazz) {
        String key = cacheKey("getSubclasses", clazz != null ? clazz.getId() : null);
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.getSubclasses(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> getImplementations(ClassNode iface) {
        String key = cacheKey("getImplementations", iface != null ? iface.getId() : null);
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.getImplementations(iface);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public Optional<ClassNode> getSuperclass(ClassNode clazz) {
        String key = cacheKey("getSuperclass", clazz != null ? clazz.getId() : null);
        Optional<ClassNode> cached = (Optional<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        Optional<ClassNode> result = delegate.getSuperclass(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> getInterfaces(ClassNode clazz) {
        String key = cacheKey("getInterfaces", clazz != null ? clazz.getId() : null);
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.getInterfaces(clazz);
        cache.put(key, result);
        return result;
    }
    
    // ==================== METHOD QUERIES ====================
    
    @Override
    public Optional<MethodNode> findMethodBySignature(String sig) {
        String key = cacheKey("findMethodBySignature", sig);
        Optional<MethodNode> cached = (Optional<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        Optional<MethodNode> result = delegate.findMethodBySignature(sig);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> findMethodsByName(String name) {
        String key = cacheKey("findMethodsByName", name);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.findMethodsByName(name);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getMethods(ClassNode clazz) {
        String key = cacheKey("getMethods", clazz != null ? clazz.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getMethods(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getCallers(MethodNode method) {
        String key = cacheKey("getCallers", method != null ? method.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getCallers(method);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getCallees(MethodNode method) {
        String key = cacheKey("getCallees", method != null ? method.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getCallees(method);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public Optional<MethodNode> getOverriddenMethod(MethodNode method) {
        String key = cacheKey("getOverriddenMethod", method != null ? method.getId() : null);
        Optional<MethodNode> cached = (Optional<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        Optional<MethodNode> result = delegate.getOverriddenMethod(method);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getOverridingMethods(MethodNode method) {
        String key = cacheKey("getOverridingMethods", method != null ? method.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getOverridingMethods(method);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getConstructors(ClassNode clazz) {
        String key = cacheKey("getConstructors", clazz != null ? clazz.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getConstructors(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getStaticMethods(ClassNode clazz) {
        String key = cacheKey("getStaticMethods", clazz != null ? clazz.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getStaticMethods(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getPublicMethods(ClassNode clazz) {
        String key = cacheKey("getPublicMethods", clazz != null ? clazz.getId() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getPublicMethods(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getAllMethods() {
        String key = cacheKey("getAllMethods");
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getAllMethods();
        cache.put(key, result);
        return result;
    }
    
    // ==================== FIELD QUERIES ====================
    
    @Override
    public Optional<FieldNode> findFieldByQualifiedName(String qn) {
        String key = cacheKey("findFieldByQualifiedName", qn);
        Optional<FieldNode> cached = (Optional<FieldNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        Optional<FieldNode> result = delegate.findFieldByQualifiedName(qn);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<FieldNode> getFields(ClassNode clazz) {
        String key = cacheKey("getFields", clazz != null ? clazz.getId() : null);
        List<FieldNode> cached = (List<FieldNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<FieldNode> result = delegate.getFields(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<Node> getFieldAccesses(FieldNode field) {
        // Not caching - result may vary based on graph state
        return delegate.getFieldAccesses(field);
    }
    
    @Override
    public List<FieldNode> getStaticFields(ClassNode clazz) {
        String key = cacheKey("getStaticFields", clazz != null ? clazz.getId() : null);
        List<FieldNode> cached = (List<FieldNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<FieldNode> result = delegate.getStaticFields(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<FieldNode> getFinalFields(ClassNode clazz) {
        String key = cacheKey("getFinalFields", clazz != null ? clazz.getId() : null);
        List<FieldNode> cached = (List<FieldNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<FieldNode> result = delegate.getFinalFields(clazz);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<FieldNode> getAllFields() {
        String key = cacheKey("getAllFields");
        List<FieldNode> cached = (List<FieldNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<FieldNode> result = delegate.getAllFields();
        cache.put(key, result);
        return result;
    }
    
    // ==================== VARIABLE QUERIES ====================
    
    @Override
    public List<VariableNode> getVariables(MethodNode method) {
        String key = cacheKey("getVariables", method != null ? method.getId() : null);
        List<VariableNode> cached = (List<VariableNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<VariableNode> result = delegate.getVariables(method);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<Node> getVariableUses(VariableNode var) {
        // Not caching - result may vary based on graph state
        return delegate.getVariableUses(var);
    }
    
    // ==================== AST QUERIES ====================
    
    @Override
    public List<Node> getStatements(MethodNode method) {
        // Not caching - AST queries may be expensive but context-dependent
        return delegate.getStatements(method);
    }
    
    @Override
    public Optional<Node> getStatementAt(MethodNode method, int line) {
        // Not caching - very specific query
        return delegate.getStatementAt(method, line);
    }
    
    // ==================== FILE QUERIES ====================
    
    @Override
    public List<Node> getNodesInFile(Path file) {
        String key = cacheKey("getNodesInFile", file != null ? file.toString() : null);
        List<Node> cached = (List<Node>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<Node> result = delegate.getNodesInFile(file);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> getClassesInFile(Path file) {
        String key = cacheKey("getClassesInFile", file != null ? file.toString() : null);
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.getClassesInFile(file);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<MethodNode> getMethodsInFile(Path file) {
        String key = cacheKey("getMethodsInFile", file != null ? file.toString() : null);
        List<MethodNode> cached = (List<MethodNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<MethodNode> result = delegate.getMethodsInFile(file);
        cache.put(key, result);
        return result;
    }
    
    // ==================== PACKAGE QUERIES ====================
    
    @Override
    public Optional<PackageNode> findPackageByName(String name) {
        String key = cacheKey("findPackageByName", name);
        Optional<PackageNode> cached = (Optional<PackageNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        Optional<PackageNode> result = delegate.findPackageByName(name);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<PackageNode> getAllPackages() {
        String key = cacheKey("getAllPackages");
        List<PackageNode> cached = (List<PackageNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<PackageNode> result = delegate.getAllPackages();
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<ClassNode> getClassesInPackage(String packageName) {
        String key = cacheKey("getClassesInPackage", packageName);
        List<ClassNode> cached = (List<ClassNode>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<ClassNode> result = delegate.getClassesInPackage(packageName);
        cache.put(key, result);
        return result;
    }
    
    // ==================== CONTROL FLOW QUERIES ====================
    
    @Override
    public Graph buildCFG(MethodNode method) {
        // Not caching - CFG is expensive and context-dependent
        return delegate.buildCFG(method);
    }
    
    @Override
    public boolean isReachable(Node from, Node to) {
        // Not caching - reachability may change
        return delegate.isReachable(from, to);
    }
    
    // ==================== SEARCH QUERIES ====================
    
    @Override
    public List<Node> getNodesByType(String type) {
        String key = cacheKey("getNodesByType", type);
        List<Node> cached = (List<Node>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<Node> result = delegate.getNodesByType(type);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<Edge> getEdgesByType(String type) {
        String key = cacheKey("getEdgesByType", type);
        List<Edge> cached = (List<Edge>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<Edge> result = delegate.getEdgesByType(type);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public List<Node> getNodesWithModifier(String modifier) {
        String key = cacheKey("getNodesWithModifier", modifier);
        List<Node> cached = (List<Node>) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        List<Node> result = delegate.getNodesWithModifier(modifier);
        cache.put(key, result);
        return result;
    }
    
    // ==================== STATISTICS QUERIES ====================
    
    @Override
    public int getTotalNodeCount() {
        // Not caching - should always be current
        return delegate.getTotalNodeCount();
    }
    
    @Override
    public int getTotalEdgeCount() {
        // Not caching - should always be current
        return delegate.getTotalEdgeCount();
    }
    
    @Override
    public int getNodeCountByType(String type) {
        String key = cacheKey("getNodeCountByType", type);
        Integer cached = (Integer) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        int result = delegate.getNodeCountByType(type);
        cache.put(key, result);
        return result;
    }
    
    @Override
    public int getEdgeCountByType(String type) {
        String key = cacheKey("getEdgeCountByType", type);
        Integer cached = (Integer) cache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        int result = delegate.getEdgeCountByType(type);
        cache.put(key, result);
        return result;
    }
}
