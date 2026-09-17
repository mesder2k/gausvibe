package dk.gausdalfind.cli;

import dk.gausdalfind.editing.*;
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.queries.*;
import dk.gausdalfind.serializer.JsonSerializer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Command Line Interface for GausVibe.
 * 
 * Provides a CLI for building graphs and executing queries.
 * 
 * Usage:
 *   java -jar gausvibe.jar [command] [options]
 * 
 * Commands:
 *   build [--project PATH] [--output PATH] - Build graph from project
 *   query [--graph PATH] [--query TYPE:PARAM] - Execute query on graph
 *   interactive - Start interactive query shell
 *   help - Show help
 */
public class CommandLineInterface {
    
    private static final String VERSION = "1.0.0";
    private static final String PROMPT = "gausvibe> ";
    
    private Graph graph;
    private JavaGraphQuery queryEngine;
    private Path projectRoot;
    private Path graphFile;
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        CommandLineInterface cli = new CommandLineInterface();
        
        if (args.length == 0) {
            cli.startInteractiveMode();
        } else {
            cli.runCommandMode(args);
        }
    }
    
    /**
     * Runs in command mode (non-interactive).
     */
    public void runCommandMode(String[] args) {
        try {
            List<String> arguments = Arrays.asList(args);
            
            if (arguments.isEmpty()) {
                printHelp();
                return;
            }
            
            String command = arguments.get(0);
            
            switch (command) {
                case "build":
                    runBuildCommand(arguments.subList(1, arguments.size()));
                    break;
                case "query":
                    runQueryCommand(arguments.subList(1, arguments.size()));
                    break;
                case "edit":
                    runEditCommand(arguments.subList(1, arguments.size()));
                    break;
                case "interactive":
                case "shell":
                    startInteractiveMode();
                    break;
                case "help":
                case "--help":
                case "-h":
                    printHelp();
                    break;
                case "version":
                case "--version":
                case "-v":
                    printVersion();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printHelp();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Runs the build command.
     */
    private void runBuildCommand(List<String> args) throws IOException {
        String projectPath = null;
        String outputPath = null;
        boolean serialize = false;
        boolean includeTest = true;
        String excludePattern = null;
        boolean parallel = true;
        boolean verbose = false;
        
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--project":
                case "-p":
                case "--path":
                    projectPath = args.get(++i);
                    break;
                case "--output":
                case "-o":
                    outputPath = args.get(++i);
                    serialize = true;
                    break;
                case "--serialize":
                case "-s":
                    serialize = true;
                    break;
                case "--include-test":
                    includeTest = true;
                    break;
                case "--exclude-test":
                    includeTest = false;
                    break;
                case "--exclude":
                    excludePattern = args.get(++i);
                    break;
                case "--parallel":
                    parallel = true;
                    break;
                case "--no-parallel":
                    parallel = false;
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        printBuildHelp();
                        System.exit(1);
                    } else {
                        projectPath = arg;
                    }
            }
        }
        
        if (projectPath == null) {
            projectPath = "."; // Default to current directory
        }
        
        Path projectRoot = Path.of(projectPath).toAbsolutePath().normalize();
        
        if (verbose) {
            System.out.println("Building graph for project: " + projectRoot);
            System.out.println("Include test: " + includeTest);
            if (excludePattern != null) {
                System.out.println("Exclude pattern: " + excludePattern);
            }
            System.out.println("Parallel: " + parallel);
        }
        
        long startTime = System.currentTimeMillis();
        
        GausVibeBuilder builder = new GausVibeBuilder(projectRoot);
        builder.setIncludeTestSources(includeTest);
        if (excludePattern != null) {
            builder.setExcludePattern(excludePattern);
        }
        builder.setParallel(parallel);
        graph = builder.build();
        
        long buildTime = System.currentTimeMillis() - startTime;
        System.out.println("Build complete in " + buildTime + " ms");
        System.out.println("Nodes: " + graph.getNodeCount() + ", Edges: " + graph.getEdgeCount());
        
        if (serialize) {
            if (outputPath == null) {
                outputPath = "graph.json";
            }
            Path outputFile = Path.of(outputPath);
            JsonSerializer serializer = new JsonSerializer();
            serializer.serialize(graph, outputFile);
            System.out.println("Graph serialized to: " + outputFile);
        }
        
        if (serialize && outputPath != null) {
            this.graphFile = Path.of(outputPath);
        }
        this.projectRoot = projectRoot;
    }
    
    /**
     * Runs the edit command.
     */
    private void runEditCommand(List<String> args) throws IOException {
        String graphPath = null;
        String operationsJson = null;
        boolean dryRun = false;
        String outputFormat = "json";
        boolean verbose = false;
        
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--graph":
                case "-g":
                    graphPath = args.get(++i);
                    break;
                case "--operations":
                case "-o":
                    operationsJson = args.get(++i);
                    break;
                case "--dry-run":
                    dryRun = true;
                    break;
                case "--output":
                    outputFormat = args.get(++i);
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        printEditHelp();
                        System.exit(1);
                    }
            }
        }
        
        if (operationsJson == null) {
            System.err.println("No operations specified");
            printEditHelp();
            System.exit(1);
        }
        
        // Load graph
        if (graph == null) {
            if (graphPath != null) {
                loadGraph(graphPath);
            } else if (this.graphFile != null) {
                loadGraph(this.graphFile);
            } else {
                System.err.println("No graph loaded and no graph file specified");
                System.err.println("Use 'build' command first or specify --graph option");
                System.exit(1);
            }
        }
        
        // Parse operations and execute
        try {
            List<Operation> operations = EditCommand.parseOperations(operationsJson);
            EditCommand editCmd = new EditCommand(graph, graphFile, dryRun, outputFormat, verbose);
            String result = editCmd.execute(operations);
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("Error executing edit: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Runs the query command.
     */
    private void runQueryCommand(List<String> args) throws IOException {
        String graphPath = null;
        String query = null;
        
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--graph":
                case "-g":
                    graphPath = args.get(++i);
                    break;
                case "--query":
                case "-q":
                    query = args.get(++i);
                    break;
                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        printQueryHelp();
                        System.exit(1);
                    } else if (query == null) {
                        query = arg;
                    }
            }
        }
        
        if (query == null) {
            System.err.println("No query specified");
            printQueryHelp();
            System.exit(1);
        }
        
        // Load graph if needed
        if (graph == null) {
            if (graphPath != null) {
                loadGraph(graphPath);
            } else if (this.graphFile != null) {
                loadGraph(this.graphFile);
            } else {
                System.err.println("No graph loaded and no graph file specified");
                System.err.println("Use 'build' command first or specify --graph option");
                System.exit(1);
            }
        }
        
        // Execute query
        String result = executeQuery(query);
        System.out.println(result);
    }
    
    /**
     * Loads a graph from a file.
     */
    private void loadGraph(String path) throws IOException {
        JsonSerializer serializer = new JsonSerializer();
        graph = serializer.deserialize(Path.of(path));
        queryEngine = new GraphQueryEngine(graph);
        System.out.println("Graph loaded from: " + path);
    }
    
    /**
     * Loads a graph from a file.
     */
    private void loadGraph(Path path) throws IOException {
        loadGraph(path.toString());
    }
    
    /**
     * Executes a query string and returns the result.
     * 
     * Query format: TYPE:PARAMETERS
     * Examples:
     *   class:com.example.MyClass - Find class by FQN
     *   class:name:Calculator - Find classes by name
     *   methods:com.example.MyClass - Get methods of class
     *   callers:com.example.MyClass#method() - Get callers of method
     */
    private String executeQuery(String query) {
        if (queryEngine == null) {
            queryEngine = new GraphQueryEngine(graph);
        }
        
        QueryParser parser = new QueryParser();
        QueryCommand cmd = parser.parse(query);
        
        return executeQueryCommand(cmd);
    }
    
    /**
     * Executes a parsed query command.
     */
    private String executeQueryCommand(QueryCommand cmd) {
        try {
            switch (cmd.getType()) {
                case "class":
                    return executeClassQuery(cmd);
                case "method":
                    return executeMethodQuery(cmd);
                case "field":
                    return executeFieldQuery(cmd);
                case "package":
                    return executePackageQuery(cmd);
                case "file":
                    return executeFileQuery(cmd);
                case "search":
                    return executeSearchQuery(cmd);
                case "stats":
                    return executeStatsQuery();
                default:
                    return "Unknown query type: " + cmd.getType();
            }
        } catch (Exception e) {
            return "Error executing query: " + e.getMessage();
        }
    }
    
    private String executeClassQuery(QueryCommand cmd) {
        if (cmd.getArguments().isEmpty()) {
            // List all classes
            List<ClassNode> classes = queryEngine.getAllClasses();
            return formatClassList(classes);
        }
        
        String subCommand = cmd.getArguments().get(0);
        
        switch (subCommand) {
            case "all":
                return formatClassList(queryEngine.getAllClasses());
            case "name":
                if (cmd.getArguments().size() < 2) return "Usage: class:name:NAME";
                return formatClassList(queryEngine.findClassesByName(cmd.getArguments().get(1)));
            case "subclasses":
                if (cmd.getArguments().size() < 2) return "Usage: class:subclasses:FQN";
                Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(cmd.getArguments().get(1));
                return cls.map(c -> formatClassList(queryEngine.getSubclasses(c)))
                       .orElse("Class not found");
            case "implementations":
                if (cmd.getArguments().size() < 2) return "Usage: class:implementations:FQN";
                cls = queryEngine.findClassByQualifiedName(cmd.getArguments().get(1));
                return cls.map(c -> formatClassList(queryEngine.getImplementations(c)))
                       .orElse("Class not found");
            case "superclass":
                if (cmd.getArguments().size() < 2) return "Usage: class:superclass:FQN";
                cls = queryEngine.findClassByQualifiedName(cmd.getArguments().get(1));
                return cls.flatMap(queryEngine::getSuperclass)
                       .map(this::formatClass)
                       .orElse("No superclass");
            case "interfaces":
                if (cmd.getArguments().size() < 2) return "Usage: class:interfaces:FQN";
                cls = queryEngine.findClassByQualifiedName(cmd.getArguments().get(1));
                return cls.map(c -> formatClassList(queryEngine.getInterfaces(c)))
                       .orElse("Class not found");
            default:
                // Try to find class by FQN
                cls = queryEngine.findClassByQualifiedName(subCommand);
                return cls.map(this::formatClass)
                       .orElse("Class not found: " + subCommand);
        }
    }
    
    private String executeMethodQuery(QueryCommand cmd) {
        if (cmd.getArguments().isEmpty()) {
            return "Usage: method:[all|name:NAME|class:FQN|signature:SIG]";
        }
        
        String subCommand = cmd.getArguments().get(0);
        
        switch (subCommand) {
            case "all":
                return formatMethodList(queryEngine.getAllMethods());
            case "name":
                if (cmd.getArguments().size() < 2) return "Usage: method:name:NAME";
                return formatMethodList(queryEngine.findMethodsByName(cmd.getArguments().get(1)));
            case "class":
                if (cmd.getArguments().size() < 2) return "Usage: method:class:FQN";
                Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(cmd.getArguments().get(1));
                return cls.map(c -> formatMethodList(queryEngine.getMethods(c)))
                       .orElse("Class not found");
            case "signature":
                if (cmd.getArguments().size() < 2) return "Usage: method:signature:SIG";
                Optional<MethodNode> method = queryEngine.findMethodBySignature(cmd.getArguments().get(1));
                return method.map(this::formatMethod)
                       .orElse("Method not found");
            default:
                // Try to find method by signature
                method = queryEngine.findMethodBySignature(subCommand);
                return method.map(this::formatMethod)
                       .orElse("Method not found: " + subCommand);
        }
    }
    
    private String executeFieldQuery(QueryCommand cmd) {
        if (cmd.getArguments().isEmpty()) {
            return "Usage: field:[all|class:FQN|name:NAME]";
        }
        
        String subCommand = cmd.getArguments().get(0);
        
        switch (subCommand) {
            case "all":
                return formatFieldList(queryEngine.getAllFields());
            case "class":
                if (cmd.getArguments().size() < 2) return "Usage: field:class:FQN";
                Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(cmd.getArguments().get(1));
                return cls.map(c -> formatFieldList(queryEngine.getFields(c)))
                       .orElse("Class not found");
            case "name":
                if (cmd.getArguments().size() < 2) return "Usage: field:name:NAME";
                Optional<FieldNode> field = queryEngine.findFieldByQualifiedName(cmd.getArguments().get(1));
                return field.map(this::formatField)
                       .orElse("Field not found");
            default:
                // Try to find field by FQN
                field = queryEngine.findFieldByQualifiedName(subCommand);
                return field.map(this::formatField)
                       .orElse("Field not found: " + subCommand);
        }
    }
    
    private String executePackageQuery(QueryCommand cmd) {
        if (cmd.getArguments().isEmpty()) {
            return formatPackageList(queryEngine.getAllPackages());
        }
        
        String subCommand = cmd.getArguments().get(0);
        
        switch (subCommand) {
            case "all":
                return formatPackageList(queryEngine.getAllPackages());
            case "classes":
                if (cmd.getArguments().size() < 2) return "Usage: package:classes:NAME";
                return formatClassList(queryEngine.getClassesInPackage(cmd.getArguments().get(1)));
            default:
                Optional<PackageNode> pkg = queryEngine.findPackageByName(subCommand);
                return pkg.map(this::formatPackage)
                       .orElse("Package not found: " + subCommand);
        }
    }
    
    private String executeFileQuery(QueryCommand cmd) {
        if (cmd.getArguments().isEmpty()) {
            return "Usage: file:PATH";
        }
        
        String filePath = cmd.getArguments().get(0);
        Path path = Path.of(filePath);
        
        List<Node> nodes = queryEngine.getNodesInFile(path);
        return formatNodeList(nodes, 10); // Limit to 10 nodes
    }
    
    private String executeSearchQuery(QueryCommand cmd) {
        if (cmd.getArguments().isEmpty()) {
            return "Usage: search:type:TYPE or search:nodes:TYPE or search:edges:TYPE";
        }
        
        String subCommand = cmd.getArguments().get(0);
        
        switch (subCommand) {
            case "type":
            case "nodes":
                if (cmd.getArguments().size() < 2) return "Usage: search:nodes:TYPE";
                return formatNodeList(queryEngine.getNodesByType(cmd.getArguments().get(1)), 20);
            case "edges":
                if (cmd.getArguments().size() < 2) return "Usage: search:edges:TYPE";
                return formatEdgeList(queryEngine.getEdgesByType(cmd.getArguments().get(1)), 20);
            case "modifier":
                if (cmd.getArguments().size() < 2) return "Usage: search:modifier:MODIFIER";
                return formatNodeList(queryEngine.getNodesWithModifier(cmd.getArguments().get(1)), 20);
            default:
                return "Unknown search type: " + subCommand;
        }
    }
    
    private String executeStatsQuery() {
        return "Nodes: " + queryEngine.getTotalNodeCount() + 
               ", Edges: " + queryEngine.getTotalEdgeCount();
    }
    
    // ==================== Interactive Mode ====================
    
    /**
     * Starts the interactive query shell.
     */
    public void startInteractiveMode() {
        System.out.println("GausVibe Interactive Shell v" + VERSION);
        System.out.println("Type 'help' for commands, 'exit' to quit");
        System.out.println();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        try {
            while (true) {
                System.out.print(PROMPT);
                String line = reader.readLine();
                
                if (line == null || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    break;
                }
                
                if (line.isBlank()) {
                    continue;
                }
                
                if (line.equalsIgnoreCase("help")) {
                    printInteractiveHelp();
                    continue;
                }
                
                if (line.equalsIgnoreCase("clear")) {
                    System.out.println("\033[H\033[2J"); // ANSI clear screen
                    System.out.println("GausVibe v" + VERSION);
                    continue;
                }
                
                // Parse and execute query
                QueryParser parser = new QueryParser();
                QueryCommand cmd = parser.parse(line);
                
                if (cmd == null) {
                    System.out.println("Invalid query. Type 'help' for usage.");
                    continue;
                }
                
                if (cmd.getType().equalsIgnoreCase("build")) {
                    // Handle build command
                    if (cmd.getArguments().isEmpty()) {
                        runBuildCommand(Collections.emptyList());
                    } else {
                        runBuildCommand(cmd.getArguments());
                    }
                } else {
                    String result = executeQueryCommand(cmd);
                    System.out.println(result);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        } finally {
            System.out.println("Goodbye!");
        }
    }
    
    // ==================== Help Methods ====================
    
    private void printHelp() {
        System.out.println("GausVibe v" + VERSION);
        System.out.println();
        System.out.println("Usage: java -jar gausvibe.jar [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  build [options]   - Build graph from project");
        System.out.println("  query [options]   - Execute query on graph");
        System.out.println("  edit [options]    - Edit graph with AST operations");
        System.out.println("  interactive       - Start interactive shell");
        System.out.println("  help             - Show this help");
        System.out.println("  version          - Show version");
        System.out.println();
        System.out.println("Type 'java -jar gausvibe.jar build --help' for build options");
        System.out.println("Type 'java -jar gausvibe.jar query --help' for query options");
        System.out.println("Type 'java -jar gausvibe.jar edit --help' for edit options");
    }
    
    private void printBuildHelp() {
        System.out.println("Usage: build [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --project PATH, -p PATH   - Project root directory (default: current directory)");
        System.out.println("  --output PATH, -o PATH   - Output file for serialized graph");
        System.out.println("  --serialize, -s          - Serialize graph to file");
    }
    
    private void printQueryHelp() {
        System.out.println("Usage: query [options] QUERY");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --graph PATH, -g PATH   - Graph file to load");
        System.out.println("  --query QUERY, -q QUERY - Query to execute");
        System.out.println();
        System.out.println("Query Types:");
        System.out.println("  class:FQN              - Find class by fully qualified name");
        System.out.println("  class:name:NAME        - Find classes by name");
        System.out.println("  class:subclasses:FQN  - Get subclasses of class");
        System.out.println("  class:implementations:FQN - Get implementations of interface");
        System.out.println("  class:superclass:FQN  - Get superclass of class");
        System.out.println("  class:interfaces:FQN  - Get interfaces of class");
        System.out.println("  method:class:FQN       - Get methods of class");
        System.out.println("  method:name:NAME       - Find methods by name");
        System.out.println("  method:signature:SIG   - Find method by signature");
        System.out.println("  field:class:FQN        - Get fields of class");
        System.out.println("  package:NAME           - Find package by name");
        System.out.println("  file:PATH              - Get nodes in file");
        System.out.println("  search:nodes:TYPE      - Search nodes by type");
        System.out.println("  search:edges:TYPE      - Search edges by type");
        System.out.println("  stats                  - Show graph statistics");
    }
    
    private void printEditHelp() {
        System.out.println("Usage: edit [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --graph PATH, -g PATH     - Graph file to load");
        System.out.println("  --operations OPERATIONS, -o OPERATIONS - JSON array of operations");
        System.out.println("  --dry-run               - Preview changes without writing files");
        System.out.println("  --output FORMAT         - Output format: json, diff, summary, text");
        System.out.println("  --verbose, -v           - Enable detailed logging");
        System.out.println();
        System.out.println("Operation Types:");
        System.out.println("  ADD_METHOD           - Add a new method to a class");
        System.out.println("  REMOVE_METHOD        - Remove a method from a class");
        System.out.println("  REPLACE_METHOD_BODY  - Replace the body of a method");
        System.out.println("  ADD_FIELD            - Add a new field to a class");
        System.out.println("  REMOVE_FIELD         - Remove a field from a class");
        System.out.println("  ADD_IMPORT           - Add an import statement");
        System.out.println("  REMOVE_IMPORT        - Remove an import statement");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  edit --graph graph.json --operations '[{"type": "ADD_METHOD", "target_class": "com.example.MyClass", "name": "newMethod", "return_type": "void"}]'");
    }
    
    private void printInteractiveHelp() {
        System.out.println("GausVibe Interactive Shell");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  build [--project PATH] [--output PATH] [-s] - Build graph");
        System.out.println("  Query types (see query help for full list):");
        System.out.println("    class:FQN, class:name:NAME, method:class:FQN, etc.");
        System.out.println("  help - Show this help");
        System.out.println("  clear - Clear screen");
        System.out.println("  exit, quit - Exit the shell");
        System.out.println();
        System.out.println("For query syntax, type: help query");
    }
    
    private void printVersion() {
        System.out.println("GausVibe v" + VERSION);
    }
    
    // ==================== Formatting Methods ====================
    
    private String formatClass(ClassNode cls) {
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(cls.getQualifiedName()).append("\n");
        sb.append("  Name: ").append(cls.getName()).append("\n");
        sb.append("  File: ").append(cls.getFile()).append("\n");
        if (cls.hasSuperclass()) {
            sb.append("  Superclass: ").append(cls.getSuperclass()).append("\n");
        }
        if (!cls.getInterfaces().isEmpty()) {
            sb.append("  Interfaces: ").append(String.join(", ", cls.getInterfaces())).append("\n");
        }
        sb.append("  Methods: ").append(queryEngine.getMethods(cls).size()).append("\n");
        sb.append("  Fields: ").append(queryEngine.getFields(cls).size()).append("\n");
        return sb.toString();
    }
    
    private String formatClassList(List<ClassNode> classes) {
        if (classes.isEmpty()) {
            return "No classes found";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Classes ("").append(classes.size()).append("):\n");
        for (ClassNode cls : classes) {
            sb.append("  - ").append(cls.getQualifiedName());
            if (!cls.getInterfaces().isEmpty()) {
                sb.append(" implements ").append(String.join(", ", cls.getInterfaces()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    
    private String formatMethod(MethodNode method) {
        StringBuilder sb = new StringBuilder();
        sb.append("Method: ").append(method.getQualifiedName()).append("\n");
        sb.append("  Signature: ").append(method.getSignature()).append("\n");
        sb.append("  Return type: ").append(method.getReturnType()).append("\n");
        sb.append("  File: ").append(method.getFile()).append("\n");
        if (method.isConstructor()) {
            sb.append("  Type: Constructor\n");
        }
        if (method.isStatic()) {
            sb.append("  Modifier: static\n");
        }
        if (method.isPublic()) {
            sb.append("  Modifier: public\n");
        }
        return sb.toString();
    }
    
    private String formatMethodList(List<MethodNode> methods) {
        if (methods.isEmpty()) {
            return "No methods found";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Methods ("").append(methods.size()).append("):\n");
        for (MethodNode method : methods) {
            sb.append("  - ").append(method.getSignature());
            if (method.isConstructor()) {
                sb.append(" (constructor)");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    
    private String formatField(FieldNode field) {
        StringBuilder sb = new StringBuilder();
        sb.append("Field: ").append(field.getQualifiedName()).append("\n");
        sb.append("  Type: ").append(field.getDataType()).append("\n");
        sb.append("  File: ").append(field.getFile()).append("\n");
        if (field.isStatic()) {
            sb.append("  Modifier: static\n");
        }
        if (field.isFinal()) {
            sb.append("  Modifier: final\n");
        }
        return sb.toString();
    }
    
    private String formatFieldList(List<FieldNode> fields) {
        if (fields.isEmpty()) {
            return "No fields found";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Fields ("").append(fields.size()).append("):\n");
        for (FieldNode field : fields) {
            sb.append("  - ").append(field.getName()).append(": ").append(field.getDataType()).append("\n");
        }
        return sb.toString();
    }
    
    private String formatPackage(PackageNode pkg) {
        return "Package: " + pkg.getName() + "\n  File: " + pkg.getFile();
    }
    
    private String formatPackageList(List<PackageNode> packages) {
        if (packages.isEmpty()) {
            return "No packages found";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Packages ("").append(packages.size()).append("):\n");
        for (PackageNode pkg : packages) {
            sb.append("  - ").append(pkg.getName()).append("\n");
        }
        return sb.toString();
    }
    
    private String formatNodeList(List<Node> nodes, int limit) {
        if (nodes.isEmpty()) {
            return "No nodes found";
        }
        StringBuilder sb = new StringBuilder();
        int count = Math.min(nodes.size(), limit);
        sb.append("Nodes ("").append(nodes.size()).append(", showing ").append(count).append("):\n");
        for (int i = 0; i < count; i++) {
            Node node = nodes.get(i);
            sb.append("  - ").append(node.getType()).append(": ").append(node.getId());
            if (node instanceof DeclarationNode) {
                DeclarationNode decl = (DeclarationNode) node;
                sb.append(" ("").append(decl.getName()).append(")");
            }
            sb.append("\n");
        }
        if (nodes.size() > limit) {
            sb.append("  ... and ").append(nodes.size() - limit).append(" more\n");
        }
        return sb.toString();
    }
    
    private String formatEdgeList(List<Edge> edges, int limit) {
        if (edges.isEmpty()) {
            return "No edges found";
        }
        StringBuilder sb = new StringBuilder();
        int count = Math.min(edges.size(), limit);
        sb.append("Edges ("").append(edges.size()).append(", showing ").append(count).append("):\n");
        for (int i = 0; i < count; i++) {
            Edge edge = edges.get(i);
            sb.append("  - ").append(edge.getType()).append(": ")
              .append(edge.getFromId()).append(" -> ").append(edge.getToId()).append("\n");
        }
        if (edges.size() > limit) {
            sb.append("  ... and ").append(edges.size() - limit).append(" more\n");
        }
        return sb.toString();
    }
}
