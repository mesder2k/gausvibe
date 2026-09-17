package com.javacodegraph;

import com.javacodegraph.cli.CommandLineInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for JavaCodeGraph.
 * 
 * This is the starting point for building and querying the Java code graph.
 * It delegates to the CommandLineInterface for handling CLI operations.
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("JavaCodeGraph - Structured graph of Java code");
        logger.info("Version: 1.0.0");
        logger.info("Starting command line interface...");
        
        // Delegate to CLI
        CommandLineInterface.main(args);
    }
}
