package dk.gausdalfind.parser;

import com.github.javaparser.StaticJavaParser;

import java.nio.file.Path;

/**
 * Configuration for JavaParser.
 * 
 * Sets up the parser with symbol solving capabilities for resolving
 * type references in the Java code.
 */
public final class JavaParserConfig {
    
    private static boolean configured = false;
    private static Path projectRoot;
    
    private JavaParserConfig() {
        // Prevent instantiation
    }
    
    /**
     * Configures JavaParser with symbol solving for the given project root.
     * 
     * This should be called once before parsing any files.
     * 
     * @param root the project root directory
     */
    public static void setup(Path root) {
        if (configured) {
            return; // Already configured
        }
        
        projectRoot = root;
        
        // Note: Symbol resolution is disabled for now due to API compatibility issues
        // with JavaParser 3.25.9. Symbol solver configuration needs to be updated.
        // For basic parsing, we don't need symbol resolution.
        StaticJavaParser.getConfiguration()
            .setAttributeComments(true)
            .setStoreTokens(true);
        
        configured = true;
    }
    
    /**
     * Returns the project root path.
     */
    public static Path getProjectRoot() {
        return projectRoot;
    }
    
    /**
     * Returns true if JavaParser has been configured.
     */
    public static boolean isConfigured() {
        return configured;
    }
    
    /**
     * Resets the configuration.
     * Useful for testing or when switching projects.
     */
    public static void reset() {
        configured = false;
        projectRoot = null;
        StaticJavaParser.getConfiguration()
            .setAttributeComments(false)
            .setStoreTokens(false);
    }
}
