package dk.gausdalfind.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.declaration.ClassNode;
import dk.gausdalfind.model.declaration.FieldNode;
import dk.gausdalfind.model.declaration.MethodNode;
import dk.gausdalfind.model.declaration.PackageNode;
import dk.gausdalfind.queries.GraphQueryEngine;
import dk.gausdalfind.cli.QueryParser;
import dk.gausdalfind.cli.QueryCommand;
import dk.gausdalfind.serializer.JsonSerializer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Pure Java REST Server for GausVibe.
 * 
 * Zero external dependencies - uses built-in JDK HttpServer.
 * 
 * Usage:
 *   mvn clean compile
 *   java -cp target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
 *     dk.gausdalfind.server.GausVibeServer --port 8080 --project /path/to/java/project
 * 
 * Endpoints:
 *   GET  /                      - Server info
 *   GET  /classes              - List all classes
 *   GET  /classes/{fqn}        - Class details
 *   GET  /classes/{fqn}/methods - Methods of class
 *   GET  /classes/{fqn}/subclasses - Subclasses
 *   GET  /classes/{fqn}/implementations - Implementations
 *   GET  /methods              - All methods
 *   GET  /packages             - All packages
 *   GET  /query?q=QUERY        - Execute query
 *   GET  /stats               - Graph statistics
 *   GET  /search?q=NAME        - Search by name
 *   GET  /ask?q=QUESTION      - Ask a high-level question in plain language
 *                              - every question is logged to <project>/.gausvibe/ask-log.jsonl
 */
public class GausVibeServer {
    
    private static Graph graph;
    private static GraphQueryEngine queryEngine;
    private static GausVibeBuilder builder;
    private static int port = 8080;
    private static String projectPath;
    private static Path graphFile;
    private static HttpServer server;
    private static QuestionLogger questionLogger;
    private static String askLogPath;

    // Recent file updates reported via POST /edited (bounded)
    private static final Deque<Map<String, Object>> recentEdits = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT_EDITS = 200;

    public static String getProjectPath() {
        return projectPath;
    }
    
    public static void main(String[] args) throws IOException {
        parseArgs(args);
        
        if (projectPath == null) {
            System.err.println("Error: --project parameter is required");
            System.err.println("Usage: GausVibeServer --project /path/to/java --port 8080");
            System.exit(1);
        }
        
        // Build graph
        System.out.println("Building graph for: " + projectPath);
        builder = new GausVibeBuilder(Path.of(projectPath));
        builder.setParallel(true);
        builder.setIncludeTestSources(true);
        graph = builder.build();
        queryEngine = new GraphQueryEngine(graph);
        
        System.out.println("Graph built: " + graph.getNodeCount() + " nodes, " + graph.getEdgeCount() + " edges");

        questionLogger = QuestionLogger.create(projectPath, askLogPath);
        if (questionLogger != null) {
            System.out.println("Ask log:  " + questionLogger.getLogFile());
        }

        // Start server
        server = HttpServer.create(new InetSocketAddress(port), 0);
        registerHandlers();
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        
        System.out.println("============================================================");
        System.out.println("GausVibe Server (Pure Java) Running");
        System.out.println("Project:  " + projectPath);
        System.out.println("Port:     " + port);
        System.out.println("Nodes:    " + graph.getNodeCount());
        System.out.println("Edges:    " + graph.getEdgeCount());
        System.out.println("URL:      http://localhost:" + port);
        System.out.println("============================================================");
    }
    
    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--project":
                    projectPath = args[++i];
                    break;
                case "--ask-log":
                    askLogPath = args[++i];
                    break;
                default:
                    if (args[i].startsWith("--")) {
                        System.err.println("Unknown option: " + args[i]);
                        System.exit(1);
                    }
            }
        }
    }
    
    private static void registerHandlers() {
        server.createContext("/", new RootHandler());
        server.createContext("/classes", new ClassesHandler());
        server.createContext("/methods", new MethodsHandler());
        server.createContext("/packages", new PackagesHandler());
        server.createContext("/query", new QueryHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/search", new SearchHandler());
        server.createContext("/ask", new AskHandler());
        server.createContext("/feedback", new FeedbackHandler());
        server.createContext("/edited", new EditedHandler());
        server.createContext("/changes", new ChangesHandler());
        server.createContext("/tests", new TestsHandler());
        server.createContext("/classes/", new ClassDetailHandler());
    }
    
    /**
     * Base handler class with common functionality
     */
    static class BaseHandler implements HttpHandler {
        protected String responseJson(int status, Object data) {
            return responseJson(status, toJson(data));
        }
        
        protected String responseJson(int status, String json) {
            return json;
        }
        
        protected String toJson(Object obj) {
            // Simple JSON serialization - no external deps
            if (obj == null) return "null";
            if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
            
            StringBuilder sb = new StringBuilder();
            if (obj instanceof Map) {
                sb.append("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\"").append(entry.getKey()).append("\":").append(toJson(entry.getValue()));
                }
                sb.append("}");
            } else if (obj instanceof List) {
                sb.append("[");
                boolean first = true;
                for (Object item : (List<?>) obj) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append(toJson(item));
                }
                sb.append("]");
            } else {
                return "\"" + escapeJson(obj.toString()) + "\"";
            }
            return sb.toString();
        }
        
        protected String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Not implemented";
            try {
                response = handleRequest(exchange);
            } catch (Exception e) {
                response = responseJson(500, Map.of("error", e.getMessage()));
                exchange.sendResponseHeaders(500, response.length());
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        protected String handleRequest(HttpExchange exchange) throws Exception {
            return "{}";
        }
        
        protected Map<String, String> getQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) return params;
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    params.put(parts[0], parts[1]);
                } else if (parts.length == 1 && !parts[0].isEmpty()) {
                    params.put(parts[0], "");
                }
            }
            return params;
        }
    }
    
    static class RootHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", "GausVibe Server");
            info.put("version", "1.0.0");
            
            if (graph != null) {
                Map<String, Object> graphInfo = new LinkedHashMap<>();
                graphInfo.put("project", projectPath);
                graphInfo.put("nodes", graph.getNodeCount());
                graphInfo.put("edges", graph.getEdgeCount());
                info.put("graph", graphInfo);
            }
            
            Map<String, Object> endpoints = new LinkedHashMap<>();
            endpoints.put("GET /", "Server info");
            endpoints.put("GET /classes", "List all classes");
            endpoints.put("GET /classes/{fqn}", "Class details");
            endpoints.put("GET /classes/{fqn}/methods", "Methods of class");
            endpoints.put("GET /classes/{fqn}/subclasses", "Subclasses");
            endpoints.put("GET /classes/{fqn}/implementations", "Implementations");
            endpoints.put("GET /methods", "All methods");
            endpoints.put("GET /packages", "All packages");
            endpoints.put("GET /query?q=QUERY", "Execute query");
            endpoints.put("GET /stats", "Graph statistics");
            endpoints.put("GET /search?q=NAME", "Search by name");
            endpoints.put("GET /ask?q=QUESTION", "Ask a high-level question in plain language");
            endpoints.put("POST /feedback", "Rate an /ask answer (helpful, wrong, incomplete, too-big, other)");
            endpoints.put("POST /edited", "Report a file edit; GausVibe reparses just that file and updates the graph");
            endpoints.put("GET /changes", "Recent file updates reported via POST /edited");
            endpoints.put("GET /tests/{fqn}", "Tests covering a class (by name convention and call graph)");
            info.put("endpoints", endpoints);
            
            return toJson(info);
        }
    }
    
    static class StatsHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("project", projectPath);
            stats.put("nodes", graph.getNodeCount());
            stats.put("edges", graph.getEdgeCount());
            return toJson(stats);
        }
    }
    
    static class ClassesHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            List<String> classNames = new ArrayList<>();
            for (ClassNode cls : queryEngine.getAllClasses()) {
                classNames.add(cls.getQualifiedName());
            }
            Collections.sort(classNames);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", classNames.size());
            result.put("classes", classNames);
            return toJson(result);
        }
    }
    
    static class ClassDetailHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            String path = exchange.getRequestURI().getPath();
            // Extract FQN from /classes/{fqn} or /classes/{fqn}/methods etc.
            String[] parts = path.substring("/classes/".length()).split("/", 2);
            String fqn = parts[0];
            String subpath = parts.length > 1 ? parts[1] : null;
            
            if (subpath != null) {
                return handleSubpath(fqn, subpath);
            }
            
            // Get class details
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }
            
            return toJson(classToMap(cls.get()));
        }
        
        private String handleSubpath(String fqn, String subpath) {
            switch (subpath) {
                case "methods":
                    return getMethods(fqn);
                case "subclasses":
                    return getSubclasses(fqn);
                case "implementations":
                    return getImplementations(fqn);
                default:
                    return responseJson(404, Map.of("error", "Unknown subpath: " + subpath));
            }
        }
        
        private String getMethods(String fqn) {
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }
            
            List<String> methods = new ArrayList<>();
            for (MethodNode method : queryEngine.getMethods(cls.get())) {
                methods.add(method.getSignature());
            }
            Collections.sort(methods);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("class", fqn);
            result.put("count", methods.size());
            result.put("methods", methods);
            return toJson(result);
        }
        
        private String getSubclasses(String fqn) {
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }
            
            List<String> subclasses = new ArrayList<>();
            for (ClassNode subclass : queryEngine.getSubclasses(cls.get())) {
                subclasses.add(subclass.getQualifiedName());
            }
            Collections.sort(subclasses);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("class", fqn);
            result.put("count", subclasses.size());
            result.put("subclasses", subclasses);
            return toJson(result);
        }
        
        private String getImplementations(String fqn) {
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Interface not found: " + fqn));
            }
            
            List<String> implementations = new ArrayList<>();
            for (ClassNode impl : queryEngine.getImplementations(cls.get())) {
                implementations.add(impl.getQualifiedName());
            }
            Collections.sort(implementations);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("interface", fqn);
            result.put("count", implementations.size());
            result.put("implementations", implementations);
            return toJson(result);
        }
        
        private Map<String, Object> classToMap(ClassNode cls) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fqn", cls.getQualifiedName());
            map.put("name", cls.getName());
            map.put("file", cls.getFile());
            if (cls.hasSuperclass()) {
                map.put("superclass", cls.getSuperclass());
            }
            if (!cls.getInterfaces().isEmpty()) {
                map.put("interfaces", new ArrayList<>(cls.getInterfaces()));
            }
            map.put("method_count", queryEngine.getMethods(cls).size());
            map.put("field_count", queryEngine.getFields(cls).size());
            return map;
        }
    }
    
    static class MethodsHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            List<String> methods = new ArrayList<>();
            for (MethodNode method : queryEngine.getAllMethods()) {
                methods.add(method.getSignature());
            }
            Collections.sort(methods);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", methods.size());
            result.put("methods", methods);
            return toJson(result);
        }
    }
    
    static class PackagesHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            List<String> packages = new ArrayList<>();
            for (PackageNode pkg : queryEngine.getAllPackages()) {
                packages.add(pkg.getName());
            }
            Collections.sort(packages);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", packages.size());
            result.put("packages", packages);
            return toJson(result);
        }
    }
    
    static class QueryHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            String query = getQueryParams(exchange.getRequestURI().getQuery()).get("q");
            if (query == null || query.isEmpty()) {
                return responseJson(400, Map.of("error", "Missing 'q' query parameter"));
            }
            
            String result = executeGausVibeQuery(query);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", query);
            response.put("result", result);
            response.put("nodes", graph.getNodeCount());
            response.put("edges", graph.getEdgeCount());
            return toJson(response);
        }
        
        private String executeGausVibeQuery(String query) {
            QueryParser parser = new QueryParser();
            QueryCommand cmd = parser.parse(query);
            if (cmd == null) {
                return "Invalid query syntax";
            }
            return executeQueryCommand(cmd);
        }
        
        private String executeQueryCommand(QueryCommand cmd) {
            try {
                switch (cmd.getType()) {
                    case "class":
                        return formatClassQuery(cmd);
                    case "method":
                        return formatMethodQuery(cmd);
                    case "field":
                        return formatFieldQuery(cmd);
                    case "package":
                        return formatPackageQuery(cmd);
                    case "file":
                        return formatFileQuery(cmd);
                    case "search":
                        return formatSearchQuery(cmd);
                    case "stats":
                        return formatStatsQuery();
                    default:
                        return "Unknown query type: " + cmd.getType();
                }
            } catch (Exception e) {
                return "Error executing query: " + e.getMessage();
            }
        }
        
        private String formatClassQuery(QueryCommand cmd) {
            if (cmd.getArguments().isEmpty()) {
                List<String> names = new ArrayList<>();
                for (ClassNode cls : queryEngine.getAllClasses()) {
                    names.add(cls.getQualifiedName());
                }
                Collections.sort(names);
                StringBuilder sb = new StringBuilder();
                sb.append("Classes (").append(names.size()).append("):\n");
                for (String name : names) {
                    sb.append("  - ").append(name).append("\n");
                }
                return sb.toString();
            }
            
            String subCommand = cmd.getArguments().get(0);
            switch (subCommand) {
                case "all":
                    return formatClassQuery(new QueryCommand("class", new ArrayList<>()));
                case "name":
                    if (cmd.getArguments().size() < 2) return "Usage: class:name:NAME";
                    List<String> names = new ArrayList<>();
                    for (ClassNode cls : queryEngine.findClassesByName(cmd.getArguments().get(1))) {
                        names.add(cls.getQualifiedName());
                    }
                    if (names.isEmpty()) return "No classes found";
                    StringBuilder sb = new StringBuilder();
                    sb.append("Classes (").append(names.size()).append("):\n");
                    for (String name : names) {
                        sb.append("  - ").append(name).append("\n");
                    }
                    return sb.toString();
                case "subclasses":
                    if (cmd.getArguments().size() < 2) return "Usage: class:subclasses:FQN";
                    return formatClassList(queryEngine.findClassByQualifiedName(cmd.getArguments().get(1))
                            .map(queryEngine::getSubclasses)
                            .orElse(Collections.emptyList()));
                case "implementations":
                    if (cmd.getArguments().size() < 2) return "Usage: class:implementations:FQN";
                    return formatClassList(queryEngine.findClassByQualifiedName(cmd.getArguments().get(1))
                            .map(queryEngine::getImplementations)
                            .orElse(Collections.emptyList()));
                case "superclass":
                    if (cmd.getArguments().size() < 2) return "Usage: class:superclass:FQN";
                    return queryEngine.findClassByQualifiedName(cmd.getArguments().get(1))
                            .flatMap(queryEngine::getSuperclass)
                            .map(this::formatClass)
                            .orElse("No superclass");
                case "interfaces":
                    if (cmd.getArguments().size() < 2) return "Usage: class:interfaces:FQN";
                    return queryEngine.findClassByQualifiedName(cmd.getArguments().get(1))
                            .map(queryEngine::getInterfaces)
                            .map(this::formatClassList)
                            .orElse("Class not found");
                default:
                    return queryEngine.findClassByQualifiedName(subCommand)
                            .map(this::formatClass)
                            .orElse("Class not found: " + subCommand);
            }
        }
        
        private String formatClassList(List<ClassNode> classes) {
            if (classes.isEmpty()) return "No classes found";
            StringBuilder sb = new StringBuilder();
            sb.append("Classes (").append(classes.size()).append("):\n");
            for (ClassNode cls : classes) {
                sb.append("  - ").append(cls.getQualifiedName()).append("\n");
            }
            return sb.toString();
        }
        
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
        
        private String formatMethodQuery(QueryCommand cmd) {
            if (cmd.getArguments().isEmpty()) {
                return formatMethodList(queryEngine.getAllMethods());
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
                    return queryEngine.findClassByQualifiedName(cmd.getArguments().get(1))
                            .map(queryEngine::getMethods)
                            .map(this::formatMethodList)
                            .orElse("Class not found");
                default:
                    return queryEngine.findMethodBySignature(subCommand)
                            .map(this::formatMethod)
                            .orElse("Method not found: " + subCommand);
            }
        }
        
        private String formatMethodList(List<MethodNode> methods) {
            if (methods.isEmpty()) return "No methods found";
            StringBuilder sb = new StringBuilder();
            sb.append("Methods (").append(methods.size()).append("):\n");
            for (MethodNode method : methods) {
                sb.append("  - ").append(method.getSignature());
                if (method.isConstructor()) {
                    sb.append(" (constructor)");
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
        
        private String formatFieldQuery(QueryCommand cmd) {
            if (cmd.getArguments().isEmpty()) {
                return formatFieldList(queryEngine.getAllFields());
            }
            
            String subCommand = cmd.getArguments().get(0);
            switch (subCommand) {
                case "all":
                    return formatFieldList(queryEngine.getAllFields());
                case "class":
                    if (cmd.getArguments().size() < 2) return "Usage: field:class:FQN";
                    return queryEngine.findClassByQualifiedName(cmd.getArguments().get(1))
                            .map(queryEngine::getFields)
                            .map(this::formatFieldList)
                            .orElse("Class not found");
                default:
                    return queryEngine.findFieldByQualifiedName(subCommand)
                            .map(this::formatField)
                            .orElse("Field not found: " + subCommand);
            }
        }
        
        private String formatFieldList(List<FieldNode> fields) {
            if (fields.isEmpty()) return "No fields found";
            StringBuilder sb = new StringBuilder();
            sb.append("Fields (").append(fields.size()).append("):\n");
            for (FieldNode field : fields) {
                sb.append("  - ").append(field.getName()).append(": ").append(field.getDataType()).append("\n");
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
        
        private String formatPackageQuery(QueryCommand cmd) {
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
                    return queryEngine.findPackageByName(subCommand)
                            .map(this::formatPackage)
                            .orElse("Package not found: " + subCommand);
            }
        }
        
        private String formatPackageList(List<PackageNode> packages) {
            if (packages.isEmpty()) return "No packages found";
            StringBuilder sb = new StringBuilder();
            sb.append("Packages (").append(packages.size()).append("):\n");
            for (PackageNode pkg : packages) {
                sb.append("  - ").append(pkg.getName()).append("\n");
            }
            return sb.toString();
        }
        
        private String formatPackage(PackageNode pkg) {
            return "Package: " + pkg.getName() + "\n  File: " + pkg.getFile();
        }
        
        private String formatFileQuery(QueryCommand cmd) {
            if (cmd.getArguments().isEmpty()) {
                return "Usage: file:PATH";
            }
            return "File queries not yet implemented";
        }
        
        private String formatSearchQuery(QueryCommand cmd) {
            if (cmd.getArguments().isEmpty()) {
                return "Usage: search:nodes:TYPE or search:edges:TYPE";
            }
            String subCommand = cmd.getArguments().get(0);
            switch (subCommand) {
                case "nodes":
                case "type":
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
        
        private String formatNodeList(List<Node> nodes, int limit) {
            if (nodes.isEmpty()) return "No nodes found";
            StringBuilder sb = new StringBuilder();
            int count = Math.min(nodes.size(), limit);
            sb.append("Nodes (").append(nodes.size()).append(", showing ").append(count).append("):\n");
            for (int i = 0; i < count; i++) {
                Node node = nodes.get(i);
                sb.append("  - ").append(node.getType()).append(": ").append(node.getId()).append("\n");
            }
            return sb.toString();
        }
        
        private String formatEdgeList(List<dk.gausdalfind.model.Edge> edges, int limit) {
            if (edges.isEmpty()) return "No edges found";
            StringBuilder sb = new StringBuilder();
            int count = Math.min(edges.size(), limit);
            sb.append("Edges (").append(edges.size()).append(", showing ").append(count).append("):\n");
            for (int i = 0; i < count; i++) {
                dk.gausdalfind.model.Edge edge = edges.get(i);
                sb.append("  - ").append(edge.getType()).append(": ")
                  .append(edge.getFromId()).append(" -> ").append(edge.getToId()).append("\n");
            }
            return sb.toString();
        }
        
        private String formatStatsQuery() {
            return "Nodes: " + queryEngine.getTotalNodeCount() + ", Edges: " + queryEngine.getTotalEdgeCount();
        }
    }
    
    /**
     * Natural-language question endpoint.
     *
     * Accepts a high-level question about the codebase and answers it with
     * the graph. Every question - answered or not - is appended to the ask
     * log, so the distribution of questions the harness asks becomes
     * measurable data for designing the query index.
     */
    static class AskHandler extends BaseHandler {

        private static final int MAX_LINES = 50;

        private static final java.util.regex.Pattern TEST_QUESTION = java.util.regex.Pattern.compile(
            "\\btest(s|ed|ing)?\\b|\\bverif(y|ies|ied)\\b|\\bassert", java.util.regex.Pattern.CASE_INSENSITIVE);

        private static final Set<String> STOP_WORDS = Set.of(
            "who", "what", "where", "which", "when", "how", "why",
            "the", "a", "an", "of", "in", "on", "at", "to", "for", "with", "by",
            "is", "are", "was", "were", "do", "does", "did", "can", "could",
            "call", "calls", "caller", "callers", "calling", "called",
            "class", "classes", "method", "methods", "field", "fields",
            "implement", "implements", "implementation", "implementations",
            "subclass", "subclasses", "superclass", "extends", "override",
            "overrides", "overridden", "interface", "interfaces",
            "test", "tests", "testing", "testsuite",
            "find", "list", "all", "show", "get", "give", "me", "tell",
            "live", "lives", "defined", "define", "declared", "declare",
            "located", "locate", "location", "file", "files",
            "this", "that", "these", "those", "it", "its", "and", "or",
            "any", "many", "much", "please", "code", "codebase", "project",
            "java", "graph", "server", "question", "about", "used", "uses",
            "usages", "usage", "references", "referenced", "reference",
            "members", "member", "variables", "variable", "static", "public",
            "private", "protected", "signature", "signatures", "type", "types"
        );

        @Override
        protected String handleRequest(HttpExchange exchange) {
            String raw = getQueryParams(exchange.getRequestURI().getQuery()).get("q");
            if (raw == null || raw.isEmpty()) {
                return responseJson(400, Map.of("error", "Missing 'q' parameter"));
            }

            String question;
            try {
                question = java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return responseJson(400, Map.of("error", "Malformed query encoding"));
            }

            Map<String, Object> result = answerQuestion(question);
            String json = toJson(result);

            if (questionLogger != null) {
                questionLogger.log(question,
                    (String) result.get("matched"),
                    json.length(),
                    true);
            }
            return json;
        }

        /**
         * Routes the question to a graph query.
         * Returns a map with "question", "matched" (or null), "answer" and "count".
         */
        private Map<String, Object> answerQuestion(String question) {
            String lower = question.toLowerCase();
            List<String> candidates = extractIdentifiers(question);

            String classFqn = resolveClass(candidates);
            String methodName = resolveMethod(candidates);

            if (lower.contains("implement")) {
                return classQuery(question, classFqn, "implementations",
                    "No class or interface name found in the question");
            }
            if (lower.contains("subclass") || lower.contains("extends")) {
                return classQuery(question, classFqn, "subclasses",
                    "No class name found in the question");
            }
            if (lower.contains("call")) {
                return callersQuery(question, methodName);
            }
            if (TEST_QUESTION.matcher(lower).find()) {
                return testsQuery(question, candidates);
            }
            if (lower.contains("method")) {
                return classQuery(question, classFqn, "methods",
                    "No class name found in the question");
            }
            if (lower.contains("field") || lower.contains("variable") || lower.contains("member")) {
                return classQuery(question, classFqn, "fields",
                    "No class name found in the question");
            }
            if (lower.contains("where") || lower.contains("defined") || lower.contains("declared")
                || lower.contains("live") || lower.contains("located") || lower.contains("location")) {
                return locationQuery(question, classFqn, methodName);
            }

            // Fallback: name search
            if (classFqn != null) {
                return classQuery(question, classFqn, "class-detail", null);
            }
            if (methodName != null) {
                return methodQuery(question, methodName);
            }
            return unmatched(question, candidates);
        }

        private List<String> extractIdentifiers(String question) {
            List<String> tokens = new ArrayList<>();
            for (String part : question.split("[^A-Za-z0-9._]+")) {
                if (part == null || part.isEmpty()) continue;
                for (String tok : part.split("\\.")) {
                    if (tok == null || tok.isEmpty()) continue;
                    String t = tok.trim();
                    if (t.length() >= 2 && !STOP_WORDS.contains(t.toLowerCase())) {
                        tokens.add(t);
                    }
                }
            }
            return tokens;
        }

        /** First candidate that resolves to a class, by simple or qualified name. */
        private String resolveClass(List<String> candidates) {
            for (String c : candidates) {
                if (c.indexOf('.') >= 0) {
                    if (queryEngine.findClassByQualifiedName(c).isPresent()) return c;
                }
                List<ClassNode> classes = queryEngine.findClassesByName(c);
                if (!classes.isEmpty()) {
                    return classes.get(0).getQualifiedName();
                }
            }
            return null;
        }

        /** First candidate that resolves to a method name. */
        private String resolveMethod(List<String> candidates) {
            for (String c : candidates) {
                if (c.indexOf('.') >= 0) continue;
                if (!queryEngine.findMethodsByName(c).isEmpty()) return c;
            }
            return null;
        }

        private Map<String, Object> classQuery(String question, String classFqn,
                                               String kind, String missingMsg) {
            if (classFqn == null) {
                return unmatched(question, null, missingMsg);
            }
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(classFqn);
            if (cls.isEmpty()) {
                return unmatched(question, null, "Class not found: " + classFqn);
            }
            ClassNode c = cls.get();
            StringBuilder sb = new StringBuilder();
            int count;
            switch (kind) {
                case "implementations" -> {
                    List<ClassNode> impls = queryEngine.getImplementations(c);
                    count = impls.size();
                    sb.append("Implementations of ").append(classFqn).append(" (")
                      .append(count).append("):\n");
                    appendClassLines(sb, impls);
                }
                case "subclasses" -> {
                    List<ClassNode> subs = queryEngine.getSubclasses(c);
                    count = subs.size();
                    sb.append("Subclasses of ").append(classFqn).append(" (")
                      .append(count).append("):\n");
                    appendClassLines(sb, subs);
                }
                case "methods" -> {
                    List<MethodNode> methods = queryEngine.getMethods(c);
                    count = methods.size();
                    sb.append("Methods of ").append(classFqn).append(" (")
                      .append(count).append("):\n");
                    appendMethodLines(sb, methods);
                }
                case "fields" -> {
                    List<FieldNode> fields = queryEngine.getFields(c);
                    count = fields.size();
                    sb.append("Fields of ").append(classFqn).append(" (")
                      .append(count).append("):\n");
                    appendFieldLines(sb, fields);
                }
                default -> {
                    count = 1;
                    sb.append("Class ").append(classFqn).append(":\n");
                    sb.append("  File: ").append(c.getFile()).append("\n");
                    if (c.hasSuperclass()) {
                        sb.append("  Superclass: ").append(c.getSuperclass()).append("\n");
                    }
                    if (!c.getInterfaces().isEmpty()) {
                        sb.append("  Interfaces: ").append(String.join(", ", c.getInterfaces())).append("\n");
                    }
                    sb.append("  Methods: ").append(queryEngine.getMethods(c).size())
                      .append(", Fields: ").append(queryEngine.getFields(c).size()).append("\n");
                }
            }
            return matched(question, kind, sb.toString(), count);
        }

        /**
         * Routes test/harness questions to the test-coverage endpoint
         * logic. Falls back to fuzzy method resolution: "index name
         * validation" can resolve to validateIndexName, and the question
         * is then answered with the tests covering that method's class.
         */
        private Map<String, Object> testsQuery(String question, List<String> candidates) {
            List<String> targetClasses = new ArrayList<>();
            Set<String> targetMethods = new HashSet<>();
            
            String classFqn = resolveClass(candidates);
            if (classFqn != null) {
                targetClasses.add(classFqn);
            } else {
                // fuzzy method resolution: match a candidate stem against
                // method names (e.g. "validation" -> validateIndexName)
                MethodNode best = resolveMethodFuzzy(candidates);
                if (best != null) {
                    if (best.getClassName() != null) {
                        targetClasses.add(best.getClassName());
                    }
                    targetMethods.add(best.getSignature());
                }
            }
            
            if (targetClasses.isEmpty()) {
                return unmatched(question, candidates, null);
            }
            
            StringBuilder sb = new StringBuilder();
            int total = 0;
            for (String fqn : targetClasses) {
                Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
                if (cls.isEmpty()) continue;
                Map<String, Object> coverage = TestsHandler.coverageFor(cls.get(), queryEngine);
                List<?> tests = (List<?>) coverage.get("tests");
                sb.append("Tests covering ").append(fqn).append(" (")
                  .append(tests.size()).append("):\n");
                if (!targetMethods.isEmpty()) {
                    sb.append("  (matched via method: ").append(String.join(", ", targetMethods)).append(")\n");
                }
                for (Object t : tests) {
                    if (t instanceof Map) {
                        Map<?, ?> tm = (Map<?, ?>) t;
                        sb.append("  - ").append(tm.get("test_class"));
                        Object file = tm.get("file");
                        if (file != null) {
                            sb.append("  [").append(file).append("]");
                        }
                        Object cov = tm.get("coverage");
                        if (cov != null) {
                            sb.append("  (").append(cov).append(")");
                        }
                        sb.append("\n");
                        Object methods = tm.get("test_methods");
                        if (methods instanceof Map && !((Map<?, ?>) methods).isEmpty()) {
                            for (var e : ((Map<?, ?>) methods).entrySet()) {
                                sb.append("      ").append(e.getKey()).append("\n");
                            }
                        }
                        total++;
                    }
                }
            }
            
            return matched(question, "tests", sb.toString(), total);
        }
        
        /**
         * Fuzzy method resolution: normalizes a candidate (strips common
         * suffixes like plural/gerund/-ion), then finds methods whose name
         * contains the stem, scoring higher when the method name contains
         * more of the question's candidate tokens.
         */
        private MethodNode resolveMethodFuzzy(List<String> candidates) {
            MethodNode best = null;
            int bestScore = 0;
            for (String candidate : candidates) {
                String stem = candidate.toLowerCase();
                for (String suffix : new String[]{"ation", "ion", "ing", "ed", "es", "s"}) {
                    if (stem.endsWith(suffix) && stem.length() > suffix.length()) {
                        stem = stem.substring(0, stem.length() - suffix.length());
                        break;
                    }
                }
                if (stem.length() < 3) continue;
                
                for (MethodNode m : queryEngine.findMethodsByName(candidate)) {
                    if (bestScore < 100) {
                        best = m;
                        bestScore = 100;
                    }
                }
                for (MethodNode m : queryEngine.getAllMethods()) {
                    String name = m.getName().toLowerCase();
                    if (!name.contains(stem)) continue;
                    int score = stem.length();
                    for (String other : candidates) {
                        if (other.equals(candidate)) continue;
                        if (name.contains(other.toLowerCase())) {
                            score += 20;
                        }
                    }
                    if (score > bestScore) {
                        best = m;
                        bestScore = score;
                    }
                }
            }
            return best;
        }

        private Map<String, Object> callersQuery(String question, String methodName) {
            if (methodName == null) {
                return unmatched(question, null, "No method name found in the question");
            }
            List<MethodNode> methods = queryEngine.findMethodsByName(methodName);
            StringBuilder sb = new StringBuilder();
            int total = 0;
            sb.append("Callers of methods named '").append(methodName).append("':\n");
            for (MethodNode m : methods) {
                List<MethodNode> callers = queryEngine.getCallers(m);
                sb.append("  ").append(m.getSignature()).append(" <- ")
                  .append(callers.size()).append(" caller(s)\n");
                for (int i = 0; i < Math.min(callers.size(), MAX_LINES); i++) {
                    MethodNode caller = callers.get(i);
                    sb.append("    - ").append(caller.getQualifiedName());
                    if (caller.getFile() != null) {
                        sb.append("  [").append(caller.getFile()).append("]");
                    }
                    sb.append("\n");
                }
                total += callers.size();
            }
            if (methods.isEmpty()) {
                return unmatched(question, null, "No method found named: " + methodName);
            }
            return matched(question, "callers", sb.toString(), total);
        }

        private Map<String, Object> locationQuery(String question, String classFqn, String methodName) {
            if (classFqn != null) {
                return classQuery(question, classFqn, "class-location", null);
            }
            if (methodName != null) {
                return methodQuery(question, methodName);
            }
            return unmatched(question, null, "No class or method name found in the question");
        }

        private Map<String, Object> methodQuery(String question, String methodName) {
            List<MethodNode> methods = queryEngine.findMethodsByName(methodName);
            if (methods.isEmpty()) {
                return unmatched(question, null, "No method found named: " + methodName);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Methods named '").append(methodName).append("' (")
              .append(methods.size()).append("):\n");
            appendMethodLines(sb, methods);
            return matched(question, "method-location", sb.toString(), methods.size());
        }

        private void appendClassLines(StringBuilder sb, List<ClassNode> classes) {
            int count = Math.min(classes.size(), MAX_LINES);
            for (int i = 0; i < count; i++) {
                ClassNode c = classes.get(i);
                sb.append("  - ").append(c.getQualifiedName());
                if (c.getFile() != null) {
                    sb.append("  [").append(c.getFile()).append("]");
                }
                sb.append("\n");
            }
            if (classes.size() > count) {
                sb.append("  ... and ").append(classes.size() - count).append(" more\n");
            }
        }

        private void appendMethodLines(StringBuilder sb, List<MethodNode> methods) {
            int count = Math.min(methods.size(), MAX_LINES);
            for (int i = 0; i < count; i++) {
                MethodNode m = methods.get(i);
                sb.append("  - ").append(m.getSignature());
                if (m.getFile() != null) {
                    sb.append("  [").append(m.getFile()).append("]");
                }
                sb.append("\n");
            }
            if (methods.size() > count) {
                sb.append("  ... and ").append(methods.size() - count).append(" more\n");
            }
        }

        private void appendFieldLines(StringBuilder sb, List<FieldNode> fields) {
            int count = Math.min(fields.size(), MAX_LINES);
            for (int i = 0; i < count; i++) {
                FieldNode f = fields.get(i);
                sb.append("  - ").append(f.getQualifiedName()).append(": ").append(f.getDataType());
                if (f.getFile() != null) {
                    sb.append("  [").append(f.getFile()).append("]");
                }
                sb.append("\n");
            }
            if (fields.size() > count) {
                sb.append("  ... and ").append(fields.size() - count).append(" more\n");
            }
        }

        private Map<String, Object> matched(String question, String kind, String answer, int count) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("question", question);
            result.put("matched", kind);
            result.put("count", count);
            result.put("answer", answer);
            return result;
        }

        private Map<String, Object> unmatched(String question, List<String> candidates) {
            return unmatched(question, candidates, null);
        }

        private Map<String, Object> unmatched(String question, List<String> candidates, String reason) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("question", question);
            result.put("matched", null);
            result.put("count", 0);
            if (reason != null) {
                result.put("reason", reason);
            }
            if (candidates != null && !candidates.isEmpty()) {
                result.put("identifiers", candidates);
            }
            result.put("hint", "No graph query matched this question. " +
                "Available: /classes, /classes/{fqn}, /classes/{fqn}/methods, " +
                "/classes/{fqn}/subclasses, /classes/{fqn}/implementations, " +
                "/methods, /packages, /search?q=NAME. " +
                "Fall back to other tools (grep/read) for anything else.");
            return result;
        }
    }

    /**
     * Feedback endpoint for the harness to rate /ask answers.
     *
     * Accepts POST with a JSON body, or GET with query params:
     *   question (required), matched (optional), rating (required),
     *   comment (optional).
     * Ratings: helpful, wrong, incomplete, too-big, other.
     * Every entry is appended to the feedback log next to the ask log.
     */
    static class FeedbackHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) throws Exception {
            String question = null;
            String matched = null;
            String rating = null;
            String comment = null;
            
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && params.isEmpty()) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> bodyParams = parseSimpleJson(body);
                if (!bodyParams.isEmpty()) {
                    params = bodyParams;
                }
            }
            
            question = params.get("question");
            matched = params.get("matched");
            rating = params.get("rating");
            comment = params.get("comment");
            
            if (question == null || question.isEmpty() || rating == null || rating.isEmpty()) {
                return responseJson(400, Map.of(
                    "error", "Missing 'question' and 'rating' parameters",
                    "ratings", "helpful, wrong, incomplete, too-big, other"));
            }
            
            if (questionLogger != null) {
                questionLogger.logFeedback(question, matched, rating, comment);
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("logged", true);
            result.put("question", question);
            result.put("rating", rating);
            return toJson(result);
        }
        
        /**
         * Parses a flat JSON object with string values.
         */
        private Map<String, String> parseSimpleJson(String body) {
            Map<String, String> map = new LinkedHashMap<>();
            if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
                return map;
            }
            try {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                for (var entry : obj.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            } catch (Exception ignored) {
                // fall through to query-param handling
            }
            return map;
        }
    }

    /**
     * File-edit notification endpoint (Tier 1 incremental freshness).
     *
     * POST /edited with JSON body {"path": "..."} (or ?path=... query param):
     * the harness reports that it changed a source file; GausVibe removes
     * that file's nodes and edges, reparses the file, and re-resolves call
     * sites, so the graph stays consistent without a full rebuild.
     */
    static class EditedHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) throws Exception {
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            if (params.isEmpty() && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> bodyParams = parseSimpleJson(body);
                if (!bodyParams.isEmpty()) {
                    params = bodyParams;
                }
            }
            String path = params.get("path");
            if (path == null || path.isEmpty()) {
                return responseJson(400, Map.of("error", "Missing 'path' parameter"));
            }
            
            Path file = Path.of(path);
            GausVibeBuilder.UpdateResult result;
            try {
                result = builder.updateFile(file);
            } catch (Exception e) {
                String cause = e.getCause() != null ? " (cause: " + e.getCause() + ")" : "";
                return responseJson(500, Map.of(
                    "error", "Failed to update file",
                    "path", path,
                    "detail", e.getClass().getSimpleName() + ": " + e.getMessage() + cause));
            }
            
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", path);
            entry.put("kind", params.get("kind"));
            entry.put("target", params.get("target"));
            entry.put("note", params.get("note"));
            entry.put("removed_nodes", result.removedNodes());
            entry.put("added_nodes", result.addedNodes());
            entry.put("removed_edges", result.removedEdges());
            entry.put("added_edges", result.addedEdges());
            entry.put("ts", java.time.Instant.now().toString());
            
            recentEdits.addLast(new LinkedHashMap<>(entry));
            while (recentEdits.size() > MAX_RECENT_EDITS) {
                recentEdits.pollFirst();
            }
            if (questionLogger != null) {
                questionLogger.logEdit(path, result.removedNodes(), result.addedNodes(),
                    params.get("kind"), params.get("target"), params.get("note"));
            }
            
            entry.put("updated", true);
            return toJson(entry);
        }
        
        private Map<String, String> parseSimpleJson(String body) {
            Map<String, String> map = new LinkedHashMap<>();
            if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
                return map;
            }
            try {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                for (var e2 : obj.entrySet()) {
                    if (e2.getValue().isJsonPrimitive()) {
                        map.put(e2.getKey(), e2.getValue().getAsString());
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
            return map;
        }
    }
    
    /**
     * Recent changes endpoint: lists file updates reported via POST /edited.
     */
    static class ChangesHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, Object>> changes = new ArrayList<>(recentEdits);
            Collections.reverse(changes);
            result.put("count", changes.size());
            result.put("changes", changes);
            return toJson(result);
        }
    }

    /**
     * Test-coverage endpoint: which tests cover a production class.
     *
     * Coverage is derived two ways:
     * - name convention: test classes whose simple name contains the
     *   production class's simple name (FooTest, FooIT, FooTestCase)
     * - call graph: test classes with CALLS edges from their methods into
     *   the production class's methods (method-level coverage)
     */
    public static class TestsHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            String path = exchange.getRequestURI().getPath();
            String fqn = path.substring("/tests/".length());
            if (fqn.isEmpty()) {
                return responseJson(400, Map.of("error", "Missing class fqn in path"));
            }
            
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (cls.isEmpty()) {
                // tolerate simple names
                List<ClassNode> byName = queryEngine.findClassesByName(fqn);
                if (byName.size() == 1) {
                    cls = Optional.of(byName.get(0));
                }
            }
            if (cls.isEmpty()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }
            
            Map<String, Object> coverage = coverageFor(cls.get(), queryEngine);
            return toJson(coverage);
        }
        
        /**
         * Computes test coverage for a production class.
         */
        public static Map<String, Object> coverageFor(ClassNode prodClass, GraphQueryEngine engine) {
            String prodName = prodClass.getName();
            String prodFqn = prodClass.getQualifiedName();
            Set<String> prodMethodIds = new HashSet<>();
            for (MethodNode m : engine.getMethods(prodClass)) {
                prodMethodIds.add(m.getId());
            }
            
            // Map test-class fqn -> test method -> called prod methods
            Map<String, Map<String, Object>> coveringTests = new LinkedHashMap<>();
            
            for (ClassNode candidate : engine.getAllClasses()) {
                if (!isTestClass(candidate) || candidate.getQualifiedName().equals(prodFqn)) {
                    continue;
                }
                Map<String, List<String>> coveringMethods = new LinkedHashMap<>();
                
                for (MethodNode testMethod : engine.getMethods(candidate)) {
                    List<String> calledProd = new ArrayList<>();
                    for (MethodNode callee : engine.getCallees(testMethod)) {
                        if (prodMethodIds.contains(callee.getId())) {
                            calledProd.add(callee.getSignature());
                        }
                    }
                    if (!calledProd.isEmpty()) {
                        coveringMethods.put(testMethod.getSignature(), calledProd);
                    }
                }
                
                boolean nameMatch = candidate.getName().toLowerCase()
                    .contains(prodName.toLowerCase());
                if (!coveringMethods.isEmpty() || nameMatch) {
                    Map<String, Object> testEntry = new LinkedHashMap<>();
                    testEntry.put("test_class", candidate.getQualifiedName());
                    testEntry.put("file", candidate.getFile());
                    testEntry.put("coverage", nameMatch && coveringMethods.isEmpty()
                        ? "name-convention" : "call-graph");
                    if (!coveringMethods.isEmpty()) {
                        testEntry.put("test_methods", coveringMethods);
                    }
                    coveringTests.put(candidate.getQualifiedName(), testEntry);
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("class", prodFqn);
            result.put("test_count", coveringTests.size());
            result.put("tests", new ArrayList<>(coveringTests.values()));
            return result;
        }
        
        /**
         * Heuristic: is this class a test? Either its file lives in a test
         * source directory, or its name follows test conventions.
         */
        public static boolean isTestClass(ClassNode cls) {
            String name = cls.getName();
            if (name.endsWith("Test") || name.endsWith("Tests")
                || name.endsWith("IT") || name.endsWith("TestCase")
                || name.startsWith("Test")) {
                return true;
            }
            Path file = cls.getFile();
            if (file != null) {
                String p = file.toString().replace('\\', '/');
                if (p.contains("/test/") || p.contains("/internalClusterTest/")) {
                    return true;
                }
            }
            return false;
        }
    }

    static class SearchHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            String query = getQueryParams(exchange.getRequestURI().getQuery()).get("q");
            if (query == null || query.isEmpty()) {
                return responseJson(400, Map.of("error", "Missing 'q' parameter"));
            }
            
            // Search for classes by name
            List<ClassNode> classes = queryEngine.findClassesByName(query);
            if (!classes.isEmpty()) {
                List<String> classNames = new ArrayList<>();
                for (ClassNode cls : classes) {
                    classNames.add(cls.getQualifiedName());
                }
                Collections.sort(classNames);
                
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "class");
                result.put("query", query);
                result.put("count", classNames.size());
                result.put("results", classNames);
                return toJson(result);
            }
            
            // Search for methods by name
            List<MethodNode> methods = queryEngine.findMethodsByName(query);
            if (!methods.isEmpty()) {
                List<String> methodSigs = new ArrayList<>();
                for (MethodNode method : methods) {
                    methodSigs.add(method.getSignature());
                }
                Collections.sort(methodSigs);
                
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "method");
                result.put("query", query);
                result.put("count", methodSigs.size());
                result.put("results", methodSigs);
                return toJson(result);
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "none");
            result.put("query", query);
            result.put("results", new ArrayList<>());
            return toJson(result);
        }
    }
}
