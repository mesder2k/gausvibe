package com.javacodegraph.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses query strings into QueryCommand objects.
 * 
 * Query syntax:
 *   - Simple query: "type:arg1:arg2:..."
 *   - Query with spaces: "type:arg 1:arg 2" (spaces are trimmed)
 *   - Empty arguments are allowed: "type::arg" -> type with empty first arg
 * 
 * The parser splits the query string by colons (':') to extract:
 *   - First part: query type
 *   - Remaining parts: arguments
 * 
 * Examples:
 *   - "class:com.example.MyClass" -> type="class", args=["com.example.MyClass"]
 *   - "method:class:com.example.MyClass" -> type="method", args=["class", "com.example.MyClass"]
 *   - "search:nodes:CLASS" -> type="search", args=["nodes", "CLASS"]
 *   - "build --project /path/to/project" -> type="build", args=["--project /path/to/project"]
 *   - "stats" -> type="stats", args=[]
 */
public class QueryParser {
    
    private static final String DELIMITER = ":";
    
    /**
     * Parses a query string into a QueryCommand.
     * 
     * @param query the query string to parse
     * @return a QueryCommand, or null if the query is null or empty
     */
    public QueryCommand parse(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        
        String trimmed = query.trim();
        
        // Handle build command specially - it may have flags
        if (trimmed.equalsIgnoreCase("build")) {
            return new QueryCommand("build");
        }
        
        // Handle help command
        if (trimmed.equalsIgnoreCase("help") || trimmed.equalsIgnoreCase("?")) {
            return new QueryCommand("help");
        }
        
        // Handle version command
        if (trimmed.equalsIgnoreCase("version") || trimmed.equalsIgnoreCase("-v") || 
            trimmed.equalsIgnoreCase("--version")) {
            return new QueryCommand("version");
        }
        
        // Handle exit/quit commands
        if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
            return new QueryCommand("exit");
        }
        
        // Handle clear command
        if (trimmed.equalsIgnoreCase("clear")) {
            return new QueryCommand("clear");
        }
        
        // Split by colon delimiter
        String[] parts = trimmed.split(DELIMITER, -1); // -1 to keep trailing empty strings
        
        if (parts.length == 0) {
            return null;
        }
        
        String type = parts[0].trim();
        List<String> arguments = new ArrayList<>();
        
        for (int i = 1; i < parts.length; i++) {
            arguments.add(parts[i].trim());
        }
        
        return new QueryCommand(type, arguments);
    }
    
    /**
     * Checks if a query string is a build command.
     * 
     * @param query the query string
     * @return true if the query is a build command
     */
    public boolean isBuildCommand(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String trimmed = query.trim().toLowerCase();
        return trimmed.equals("build") || 
               trimmed.startsWith("build ") ||
               trimmed.startsWith("build:");
    }
    
    /**
     * Checks if a query string is a help command.
     * 
     * @param query the query string
     * @return true if the query is a help command
     */
    public boolean isHelpCommand(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String trimmed = query.trim().toLowerCase();
        return trimmed.equals("help") || 
               trimmed.equals("?") ||
               trimmed.equals("--help") ||
               trimmed.equals("-h");
    }
    
    /**
     * Checks if a query string is an exit command.
     * 
     * @param query the query string
     * @return true if the query is an exit command
     */
    public boolean isExitCommand(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String trimmed = query.trim().toLowerCase();
        return trimmed.equals("exit") || 
               trimmed.equals("quit") ||
               trimmed.equals("q");
    }
}
