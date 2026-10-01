package dk.gausdalfind.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;

import java.nio.file.Path;

/**
 * Configuration for JavaParser.
 */
public final class JavaParserConfig {
    
    private static boolean configured = false;
    private static Path projectRoot;
    
    private JavaParserConfig() {
        // Prevent instantiation
    }
    
    /**
     * Configures the shared static JavaParser for the given project root.
     *
     * Note: the static configuration was observed NOT to be reliably
     * visible to parallelStream worker threads (modern-syntax files failed
     * under parallel builds while parsing fine single-threaded). Use
     * {@link #newParser()} for actual parsing.
     *
     * @param root the project root directory
     */
    public static void setup(Path root) {
        if (configured) {
            return; // Already configured
        }
        
        projectRoot = root;
        
        StaticJavaParser.getParserConfiguration()
            .setAttributeComments(true)
            .setStoreTokens(true)
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        
        configured = true;
    }
    
    /**
     * Returns a new parser with the project configuration: Java 21
     * language level, comments and tokens stored. Each call gets its own
     * parser instance, which is safe under parallel parsing.
     *
     * Without the explicit Java 21 language level, files using records,
     * text blocks, pattern-matching instanceof or switch expressions
     * fail to parse and are dropped from the graph.
     */
    public static JavaParser newParser() {
        return new JavaParser(new ParserConfiguration()
            .setAttributeComments(true)
            .setStoreTokens(true)
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
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
    }
}
