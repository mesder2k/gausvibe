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
 */
public class GausVibeServer {
    
    private static Graph graph;
    private static GraphQueryEngine queryEngine;
    private static int port = 8080;
    private static String projectPath;
    private static Path graphFile;
    private static HttpServer server;
    
    public static void main(String[] args) throws IOException {
        parseArgs(args);
        
        if (projectPath == null) {
            System.err.println("Error: --project parameter is required");
            System.err.println("Usage: GausVibeServer --project /path/to/java --port 8080");
            System.exit(1);
        }
        
        // Build graph
        System.out.println("Building graph for: " + projectPath);
        GausVibeBuilder builder = new GausVibeBuilder(Path.of(projectPath));
        builder.setParallel(true);
        builder.setIncludeTestSources(false);
        graph = builder.build();
        queryEngine = new GraphQueryEngine(graph);
        
        System.out.println("Graph built: " + graph.getNodeCount() + " nodes, " + graph.getEdgeCount() + " edges");
        
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
