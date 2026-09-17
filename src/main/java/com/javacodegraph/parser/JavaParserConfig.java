package com.javacodegraph.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

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
        
        // Create a type solver that can resolve symbols
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        
        // Add reflection type solver for JDK classes
        typeSolver.add(new ReflectionTypeSolver(true, true));
        
        // Add JavaParser type solver for parsing Java files in the project
        if (root != null) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }
        
        // Create symbol solver with the type solver
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        
        // Configure the parser to use the symbol solver
        StaticJavaParser.getConfiguration()
            .setSymbolSolver(symbolSolver)
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
            .setSymbolSolver(null)
            .setAttributeComments(false)
            .setStoreTokens(false);
    }
}
