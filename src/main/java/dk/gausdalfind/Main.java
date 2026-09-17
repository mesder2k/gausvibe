package dk.gausdalfind;

import dk.gausdalfind.cli.CommandLineInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for GausVibe.
 * 
 * This is the starting point for building and querying the Java code graph.
 * It delegates to the CommandLineInterface for handling CLI operations.
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("GausVibe - Structured graph of Java code");
        logger.info("Version: 1.0.0");
        logger.info("Starting command line interface...");
        
        // Delegate to CLI
        CommandLineInterface.main(args);
    }
}
