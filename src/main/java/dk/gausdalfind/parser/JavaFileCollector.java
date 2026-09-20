package dk.gausdalfind.parser;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects Java source files from a project directory.
 * 
 * Supports filtering by:
 * - File extensions (.java by default)
 * - Directory exclusion patterns
 * - Custom predicates
 * - Caching for performance
 */
public final class JavaFileCollector {
    
    private static final String DEFAULT_EXTENSION = ".java";
    
    // Shared cache instance for file listings
    private static final FileSystemCache fileCache = new FileSystemCache();
    
    private JavaFileCollector() {
        // Prevent instantiation
    }
    
    /**
     * Collects all Java source files from the given directory.
     * Uses sequential processing.
     * 
     * @param directory the root directory to search
     * @return list of Java file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collect(Path directory) throws IOException {
        return collect(directory, DEFAULT_EXTENSION);
    }
    
    /**
     * Collects all Java source files from the given directory using parallel processing.
     * This provides better performance on multi-core systems for large codebases.
     * 
     * @param directory the root directory to search
     * @return list of Java file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectParallel(Path directory) throws IOException {
        return collectParallel(directory, DEFAULT_EXTENSION);
    }
    
    /**
     * Collects all Java source files from the given directory using parallel processing.
     * Uses cache for improved performance on repeated queries.
     * 
     * @param directory the root directory to search
     * @return list of Java file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectParallelCached(Path directory) throws IOException {
        return collectParallelCached(directory, DEFAULT_EXTENSION);
    }
    
    /**
     * Collects all Java source files from the given directory.
     * Uses cache for improved performance on repeated queries.
     * 
     * @param directory the root directory to search
     * @return list of Java file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectCached(Path directory) throws IOException {
        return collectCached(directory, DEFAULT_EXTENSION);
    }
    
    /**
     * Collects all files with the specified extension from the given directory.
     * 
     * @param directory the root directory to search
     * @param extension the file extension to match (e.g., ".java")
     * @return list of file paths with the specified extension
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collect(Path directory, String extension) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("Extension cannot be null or blank");
        }
        
        List<Path> files = new ArrayList<>();
        
        try (Stream<Path> paths = Files.find(directory, Integer.MAX_VALUE, 
                (path, attrs) -> isJavaFile(path, attrs, extension))) {
            paths.forEach(files::add);
        }
        
        return files;
    }
    
    /**
     * Collects all files with the specified extension from the given directory using parallel processing.
     * This provides better performance on multi-core systems for large codebases.
     * 
     * @param directory the root directory to search
     * @param extension the file extension to match (e.g., ".java")
     * @return list of file paths with the specified extension
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectParallel(Path directory, String extension) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("Extension cannot be null or blank");
        }
        
        List<Path> files;
        
        try (Stream<Path> paths = Files.find(directory, Integer.MAX_VALUE, 
                (path, attrs) -> isJavaFile(path, attrs, extension))) {
            files = paths.parallel()
                    .collect(Collectors.toList());
        }
        
        return files;
    }
    
    /**
     * Collects all files with the specified extension from the given directory using parallel processing.
     * Uses cache for improved performance on repeated queries.
     * 
     * @param directory the root directory to search
     * @param extension the file extension to match (e.g., ".java")
     * @return list of file paths with the specified extension
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectParallelCached(Path directory, String extension) throws IOException {
        // Try to get from cache first
        Optional<List<Path>> cached = fileCache.get(directory);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Use parallel collection
        List<Path> files = collectParallel(directory, extension);
        
        // Cache the result
        fileCache.put(directory, files);
        
        return files;
    }
    
    /**
     * Collects all files with the specified extension from the given directory.
     * Uses cache for improved performance on repeated queries.
     * 
     * @param directory the root directory to search
     * @param extension the file extension to match (e.g., ".java")
     * @return list of file paths with the specified extension
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectCached(Path directory, String extension) throws IOException {
        // Try to get from cache first
        Optional<List<Path>> cached = fileCache.get(directory);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Use the standard collect method
        List<Path> files = collect(directory, extension);
        
        // Cache the result
        fileCache.put(directory, files);
        
        return files;
    }
    
    /**
     * Collects Java files from multiple directories.
     * 
     * @param directories the directories to search
     * @return list of Java file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collect(List<Path> directories) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : directories) {
            files.addAll(collect(dir));
        }
        return files;
    }
    
    /**
     * Checks if a path represents a Java file with the specified extension.
     */
    private static boolean isJavaFile(Path path, BasicFileAttributes attrs, String extension) {
        if (!attrs.isRegularFile()) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.endsWith(extension);
    }
    
    /**
     * Collects Java files excluding specific directories.
     * 
     * @param directory the root directory to search
     * @param excludedDirs list of directory names to exclude (e.g., "test", "target")
     * @return list of Java file paths
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectWithExclusions(Path directory, List<String> excludedDirs) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }
        
        List<Path> files = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> isValidJavaFile(path, excludedDirs))
                .forEach(files::add);
        }
        
        return files;
    }
    
    /**
     * Checks if a path is a valid Java file (not in excluded directories).
     */
    private static boolean isValidJavaFile(Path path, List<String> excludedDirs) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        
        // Check extension
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(DEFAULT_EXTENSION)) {
            return false;
        }
        
        // Check if in excluded directory
        for (String excluded : excludedDirs) {
            if (path.toString().contains("/" + excluded + "/") || 
                path.toString().contains("\\" + excluded + "\\")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Collects Java files from the standard source directories.
     * Uses caching for improved performance.
     * 
     * @param projectRoot the project root directory
     * @return list of Java file paths from src/main/java and src/test/java
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectFromStandardDirectories(Path projectRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        
        // Standard source directories
        Path mainSrc = projectRoot.resolve("src/main/java");
        Path testSrc = projectRoot.resolve("src/test/java");
        
        if (Files.isDirectory(mainSrc)) {
            files.addAll(collectCached(mainSrc));
        }
        if (Files.isDirectory(testSrc)) {
            files.addAll(collectCached(testSrc));
        }
        
        return files;
    }
    
    /**
     * Collects Java files from the standard source directories without caching.
     * Useful when you need fresh results.
     * 
     * @param projectRoot the project root directory
     * @return list of Java file paths from src/main/java and src/test/java
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> collectFromStandardDirectoriesFresh(Path projectRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        
        // Standard source directories
        Path mainSrc = projectRoot.resolve("src/main/java");
        Path testSrc = projectRoot.resolve("src/test/java");
        
        if (Files.isDirectory(mainSrc)) {
            files.addAll(collect(mainSrc));
        }
        if (Files.isDirectory(testSrc)) {
            files.addAll(collect(testSrc));
        }
        
        return files;
    }
    
    /**
     * Returns the number of Java files in a directory.
     */
    public static int countJavaFiles(Path directory) throws IOException {
        return collect(directory).size();
    }
    
    /**
     * Returns the file system cache used by this collector.
     * Useful for cache management and statistics.
     * 
     * @return the file system cache instance
     */
    public static FileSystemCache getFileCache() {
        return fileCache;
    }
    
    /**
     * Clears the file system cache.
     * Useful when you need to force a fresh scan.
     */
    public static void clearCache() {
        fileCache.invalidateAll();
    }
    
    /**
     * Gets cache statistics.
     * 
     * @return cache statistics string
     */
    public static String getCacheStatistics() {
        return fileCache.getStatistics();
    }
}
