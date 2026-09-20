package dk.gausdalfind.parser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileSystemCache.
 * Tests cover:
 * - Cache hit/miss behavior
 * - TTL expiration
 * - LRU eviction
 * - Thread safety
 * - Invalid paths
 */
class FileSystemCacheTest {
    
    @TempDir
    Path tempDir;
    
    private FileSystemCache cache;
    private Path testDir1;
    private Path testDir2;
    private Path testDir3;
    
    @BeforeEach
    void setUp() throws IOException {
        cache = new FileSystemCache();
        
        // Create test directories with files
        testDir1 = tempDir.resolve("dir1");
        testDir2 = tempDir.resolve("dir2");
        testDir3 = tempDir.resolve("dir3");
        
        Files.createDirectories(testDir1);
        Files.createDirectories(testDir2);
        Files.createDirectories(testDir3);
        
        // Create test files
        Files.writeString(testDir1.resolve("File1.java"), "public class File1 {}");
        Files.writeString(testDir1.resolve("File2.java"), "public class File2 {}");
        Files.writeString(testDir2.resolve("File3.java"), "public class File3 {}");
        Files.writeString(testDir3.resolve("File4.java"), "public class File4 {}");
    }
    
    @AfterEach
    void tearDown() {
        cache = null;
    }
    
    // ==================== BASIC OPERATIONS ====================
    
    @Test
    void testPutAndGet() throws IOException {
        List<Path> files1 = Arrays.asList(
            testDir1.resolve("File1.java"),
            testDir1.resolve("File2.java")
        );
        
        cache.put(testDir1, files1);
        
        Optional<List<Path>> cached = cache.get(testDir1);
        assertTrue(cached.isPresent());
        assertEquals(2, cached.get().size());
        assertEquals("File1.java", cached.get().get(0).getFileName().toString());
        assertEquals("File2.java", cached.get().get(1).getFileName().toString());
    }
    
    @Test
    void testGetNonExistent() {
        Optional<List<Path>> result = cache.get(testDir1);
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetOrLoad() throws IOException {
        FileSystemCache.CacheLoader loader = path -> {
            // Simulate loading files from directory
            if (path.equals(testDir1)) {
                return Arrays.asList(
                    testDir1.resolve("File1.java"),
                    testDir1.resolve("File2.java")
                );
            }
            return List.of();
        };
        
        // First call should load and cache
        List<Path> files1 = cache.getOrLoad(testDir1, loader);
        assertEquals(2, files1.size());
        
        // Second call should return cached
        List<Path> files2 = cache.getOrLoad(testDir1, loader);
        assertEquals(2, files2.size());
        assertSame(files1, files2, "Should return same cached instance");
    }
    
    @Test
    void testGetOrLoadWithException() {
        FileSystemCache.CacheLoader failingLoader = path -> {
            throw new IOException("Load failed");
        };
        
        assertThrows(IOException.class, () -> {
            cache.getOrLoad(testDir1, failingLoader);
        });
    }
    
    // ==================== CACHE INVALIDATION ====================
    
    @Test
    void testInvalidate() throws IOException {
        List<Path> files = Arrays.asList(testDir1.resolve("File1.java"));
        cache.put(testDir1, files);
        
        assertTrue(cache.get(testDir1).isPresent());
        
        cache.invalidate(testDir1);
        
        assertFalse(cache.get(testDir1).isPresent());
    }
    
    @Test
    void testInvalidateAll() throws IOException {
        List<Path> files1 = Arrays.asList(testDir1.resolve("File1.java"));
        List<Path> files2 = Arrays.asList(testDir2.resolve("File3.java"));
        
        cache.put(testDir1, files1);
        cache.put(testDir2, files2);
        
        assertEquals(2, cache.size());
        
        cache.invalidateAll();
        
        assertEquals(0, cache.size());
        assertFalse(cache.get(testDir1).isPresent());
        assertFalse(cache.get(testDir2).isPresent());
    }
    
    // ==================== CACHE STATISTICS ====================
    
    @Test
    void testSize() throws IOException {
        assertEquals(0, cache.size());
        
        cache.put(testDir1, List.of(testDir1.resolve("File1.java")));
        assertEquals(1, cache.size());
        
        cache.put(testDir2, List.of(testDir2.resolve("File3.java")));
        assertEquals(2, cache.size());
    }
    
    @Test
    void testIsEmpty() {
        assertTrue(cache.isEmpty());
        
        cache.put(testDir1, List.of());
        assertFalse(cache.isEmpty());
    }
    
    @Test
    void testGetStatistics() {
        String stats = cache.getStatistics();
        assertTrue(stats.contains("FileSystemCache"));
        assertTrue(stats.contains("size=0"));
        assertTrue(stats.contains("maxSize="));
        assertTrue(stats.contains("ttlMs="));
    }
    
    // ==================== TTL EXPIRATION ====================
    
    @Test
    void testTTLExpiration() throws IOException, InterruptedException {
        // Create cache with 100ms TTL
        FileSystemCache shortTtlCache = new FileSystemCache(100, 100);
        
        List<Path> files = Arrays.asList(testDir1.resolve("File1.java"));
        shortTtlCache.put(testDir1, files);
        
        // Should be valid immediately
        assertTrue(shortTtlCache.get(testDir1).isPresent());
        
        // Wait for TTL to expire
        Thread.sleep(150);
        
        // Should be expired
        assertFalse(shortTtlCache.get(testDir1).isPresent());
    }
    
    @Test
    void testTTLNotExpired() throws IOException, InterruptedException {
        // Create cache with 1 second TTL
        FileSystemCache oneSecCache = new FileSystemCache(100, 1000);
        
        List<Path> files = Arrays.asList(testDir1.resolve("File1.java"));
        oneSecCache.put(testDir1, files);
        
        // Should be valid immediately
        assertTrue(oneSecCache.get(testDir1).isPresent());
        
        // Wait for half of TTL
        Thread.sleep(50);
        
        // Should still be valid
        assertTrue(oneSecCache.get(testDir1).isPresent());
    }
    
    // ==================== LRU EVICTION ====================
    
    @Test
    void testLRUEviction() throws IOException {
        // Create cache with max size of 2
        FileSystemCache smallCache = new FileSystemCache(2, 300000);
        
        List<Path> files1 = Arrays.asList(testDir1.resolve("File1.java"));
        List<Path> files2 = Arrays.asList(testDir2.resolve("File3.java"));
        List<Path> files3 = Arrays.asList(testDir3.resolve("File4.java"));
        
        smallCache.put(testDir1, files1);
        smallCache.put(testDir2, files2);
        
        assertEquals(2, smallCache.size());
        assertTrue(smallCache.get(testDir1).isPresent());
        assertTrue(smallCache.get(testDir2).isPresent());
        
        // Add third entry, should evict first (LRU)
        smallCache.put(testDir3, files3);
        
        assertEquals(2, smallCache.size());
        
        // First entry should be evicted
        assertFalse(smallCache.get(testDir1).isPresent(), 
            "First entry should be evicted when adding third to size-2 cache");
        
        // Second and third should still be present
        assertTrue(smallCache.get(testDir2).isPresent());
        assertTrue(smallCache.get(testDir3).isPresent());
    }
    
    @Test
    void testLRUEvictionWithAccess() throws IOException {
        // Create cache with max size of 2
        FileSystemCache smallCache = new FileSystemCache(2, 300000);
        
        List<Path> files1 = Arrays.asList(testDir1.resolve("File1.java"));
        List<Path> files2 = Arrays.asList(testDir2.resolve("File3.java"));
        List<Path> files3 = Arrays.asList(testDir3.resolve("File4.java"));
        
        smallCache.put(testDir1, files1);
        smallCache.put(testDir2, files2);
        
        // Access first entry to make it recently used
        smallCache.get(testDir1);
        
        // Add third entry, should evict second (now least recently used)
        smallCache.put(testDir3, files3);
        
        assertEquals(2, smallCache.size());
        
        // First and third should be present
        assertTrue(smallCache.get(testDir1).isPresent());
        assertTrue(smallCache.get(testDir3).isPresent());
        
        // Second should be evicted
        assertFalse(smallCache.get(testDir2).isPresent());
    }
    
    // ==================== CONSTRUCTOR VALIDATION ====================
    
    @Test
    void testInvalidMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FileSystemCache(0, 300000);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new FileSystemCache(-1, 300000);
        });
    }
    
    @Test
    void testInvalidTTL() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FileSystemCache(100, 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new FileSystemCache(100, -1);
        });
    }
    
    // ==================== PATH HANDLING ====================
    
    @Test
    void testRelativePath() throws IOException {
        // Test with relative path
        Path relativePath = tempDir.relativize(testDir1);
        List<Path> files = Arrays.asList(testDir1.resolve("File1.java"));
        
        cache.put(relativePath, files);
        
        // Should work with absolute path too (canonicalization)
        assertTrue(cache.get(testDir1.toAbsolutePath()).isPresent());
    }
    
    @Test
    void testCanonicalization() throws IOException {
        List<Path> files = Arrays.asList(testDir1.resolve("File1.java"));
        
        // Put with one representation
        cache.put(testDir1, files);
        
        // Get with different representation (should work due to canonicalization)
        Path absolute = testDir1.toAbsolutePath().normalize();
        assertTrue(cache.get(absolute).isPresent());
    }
    
    // ==================== CACHE LOADER ====================
    
    @Test
    void testCacheLoaderInterface() throws IOException {
        FileSystemCache.CacheLoader loader = new FileSystemCache.CacheLoader() {
            @Override
            public List<Path> load(Path directory) throws IOException {
                return Arrays.asList(
                    directory.resolve("File1.java"),
                    directory.resolve("File2.java")
                );
            }
        };
        
        List<Path> result = cache.getOrLoad(testDir1, loader);
        assertEquals(2, result.size());
    }
    
    @Test
    void testDefaultConstructor() {
        FileSystemCache defaultCache = new FileSystemCache();
        assertNotNull(defaultCache);
        assertEquals(0, defaultCache.size());
    }
    
    // ==================== IMMUTABILITY ====================
    
    @Test
    void testReturnedListIsImmutable() throws IOException {
        List<Path> original = Arrays.asList(
            testDir1.resolve("File1.java"),
            testDir1.resolve("File2.java")
        );
        
        cache.put(testDir1, original);
        
        List<Path> cached = cache.get(testDir1).get();
        
        // The returned list should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            cached.add(testDir1.resolve("File3.java"));
        });
    }
}
