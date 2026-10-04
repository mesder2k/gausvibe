package dk.gausdalfind.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dk.gausdalfind.graph.GausVibeBuilder;
import dk.gausdalfind.model.CallGraphIndex;
import dk.gausdalfind.model.EdgeTypes;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Indexes;
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

    // Staleness detection + background rebuild
    private static StalenessMonitor staleness;
    private static final java.util.concurrent.atomic.AtomicBoolean refreshing =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final Object SWAP_LOCK = new Object();

    public static String getProjectPath() {
        return projectPath;
    }

    /**
     * Strips the project root prefix from an absolute file path so answers
     * stay short and repo-relative (round-4/5 finding: the absolute prefix
     * alone is ~15-18% of the tests answer). Falls back to the input when
     * the path is already relative or does not live under the project.
     */
    public static String relativePath(String file) {
        if (file == null || projectPath == null) {
            return file;
        }
        String norm = file.replace('\\', '/');
        String root = projectPath.replace('\\', '/');
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        if (norm.startsWith(root)) {
            return norm.substring(root.length());
        }
        return file;
    }

    /** Path overload for node file references. */
    public static String relativePath(java.nio.file.Path file) {
        return file == null ? null : relativePath(file.toString());
    }

    public static void main(String[] args) throws IOException {
        parseArgs(args);
        
        if (projectPath == null) {
            System.err.println("Error: --project parameter is required");
            System.err.println("Usage: GausVibeServer --project /path/to/java --port 8080");
            System.exit(1);
        }
        
        // Build graph (or load it from cache when the sources are unchanged)
        System.out.println("Building graph for: " + projectPath);
        java.nio.file.Path projectRoot = java.nio.file.Path.of(projectPath);
        Graph cached = GraphCache.load(projectRoot);
        if (cached != null) {
            graph = cached;
            // builder without build() so POST /edited can still reparse files
            builder = new GausVibeBuilder(projectRoot);
            builder.setParallel(true);
            builder.setIncludeTestSources(true);
        } else {
            builder = new GausVibeBuilder(projectRoot);
            builder.setParallel(true);
            builder.setIncludeTestSources(true);
            graph = builder.build();
            GraphCache.write(projectRoot, graph);
        }
        queryEngine = new GraphQueryEngine(graph);
        
        // Enable the call graph index and backfill existing CALLS edges
        graph.getIndexes().enableCallGraphIndex();
        graph.getIndexes().getCallGraphIndex()
            .indexCalls(graph.getIndexes().getEdgesByType(EdgeTypes.CALLS));
        
        // Capture the file baseline for staleness detection
        staleness = new StalenessMonitor(Path.of(projectPath));
        try {
            staleness.captureBaseline();
        } catch (java.io.IOException e) {
            System.err.println("Warning: staleness baseline capture failed: " + e.getMessage());
            staleness = null;
        }
        
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
        server.createContext("/callpath", new CallPathHandler());
        server.createContext("/refresh", new RefreshHandler());
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
            String response;
            int status = 200;
            try {
                response = handleRequest(exchange);
            } catch (Throwable t) {
                // Throwable, not Exception: Errors (StackOverflowError,
                // OutOfMemoryError) otherwise kill the handler thread
                // silently - an empty reply with nothing in the log
                // (found during round-6 dubbo spot checks). Log loudly.
                t.printStackTrace();
                status = 500;
                String msg = t.getMessage() != null
                    ? t.getMessage() : t.getClass().getSimpleName();
                response = responseJson(500, Map.of("error", msg));
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            // exactly one sendResponseHeaders: the old 500 path sent 500 AND
            // fell through to a second send, throwing IllegalStateException
            // and converting every handler error into a silent empty reply.
            // Content-Length must be the UTF-8 BYTE count, not the char
            // count: dubbo round-6 spot check - an answer containing one
            // 71-char/79-byte value (a zh-cn doc URL) overran the declared
            // length by 8 bytes and the connection was killed after the
            // answer had already been computed and logged ("empty reply",
            // nothing in the log). Any non-ASCII answer hits this.
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
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
            endpoints.put("GET /classes", "List classes (optional ?package=X&prefix=Y&limit=N filters; 'count' is the returned slice, 'total' all matches)");
            endpoints.put("GET /classes/{fqn}", "Class details");
            endpoints.put("GET /classes/{fqn}/methods", "Methods of class (compact names only; ?verbose=true adds signature+line details)");
            endpoints.put("GET /classes/{fqn}/fields", "Fields of class");
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
            endpoints.put("GET /tests/{fqn}", "Tests covering a class (by name convention and call graph; ?methods=true adds the per-test-method coverage map, ?link=call-graph filters coverage semantics, ?limit=N bounds the payload)");
            endpoints.put("GET /callpath?from=A&to=B", "Transitive call chains between two methods");
            endpoints.put("POST /refresh", "Full background rebuild; old graph serves until the swap");
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
            stats.put("rebuilding", refreshing.get());
            if (staleness != null) {
                stats.put("stale", staleness.isStale());
                stats.put("stale_files", staleness.getChangedFiles().size());
            }
            return toJson(stats);
        }
    }
    
    static class ClassesHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            String pkg = params.get("package");
            String prefix = params.get("prefix");
            String limitParam = params.get("limit");
            
            List<String> classNames = new ArrayList<>();
            for (ClassNode cls : queryEngine.getAllClasses()) {
                String qn = cls.getQualifiedName();
                if (pkg != null && !pkg.isEmpty() && !qn.startsWith(pkg + ".")) {
                    continue;
                }
                if (prefix != null && !prefix.isEmpty() && !qn.startsWith(prefix)) {
                    continue;
                }
                classNames.add(qn);
            }
            Collections.sort(classNames);
            
            int total = classNames.size();
            if (limitParam != null && !limitParam.isEmpty()) {
                int limit = Integer.parseInt(limitParam);
                if (limit >= 0 && classNames.size() > limit) {
                    classNames = classNames.subList(0, limit);
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", classNames.size());
            result.put("total", total);
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
                return handleSubpath(fqn, subpath, exchange.getRequestURI().getQuery());
            }
            
            // Get class details
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }
            
            return toJson(classToMap(cls.get()));
        }
        
        private String handleSubpath(String fqn, String subpath, String rawQuery) {
            String sub = subpath;
            boolean verbose = rawQuery != null && rawQuery.contains("verbose=true");
            switch (sub) {
                case "methods":
                    return getMethods(fqn, verbose);
                case "fields":
                    return getFields(fqn);
                case "subclasses":
                    return getSubclasses(fqn);
                case "implementations":
                    return getImplementations(fqn);
                default:
                    return responseJson(404, Map.of("error", "Unknown subpath: " + sub));
            }
        }

        /**
         * Compact by default: method names only. The signature+line detail
         * list duplicates every entry and dominated the class_members
         * payload (round-5: 41KB for one class); ?verbose=true brings it
         * back.
         */
        private String getMethods(String fqn, boolean verbose) {
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }

            List<String> methods = new ArrayList<>();
            List<Map<String, Object>> methodDetails = new ArrayList<>();
            for (MethodNode method : queryEngine.getMethods(cls.get())) {
                methods.add(method.getSignature());
                if (verbose) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("signature", method.getSignature());
                    if (method.getStartPosition() != null) {
                        detail.put("line", method.getStartPosition().line());
                    }
                    methodDetails.add(detail);
                }
            }
            Collections.sort(methods);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("class", fqn);
            result.put("count", methods.size());
            result.put("methods", methods);
            if (verbose) {
                result.put("method_details", methodDetails);
            }
            return toJson(result);
        }

        private String getFields(String fqn) {
            Optional<ClassNode> cls = queryEngine.findClassByQualifiedName(fqn);
            if (!cls.isPresent()) {
                return responseJson(404, Map.of("error", "Class not found: " + fqn));
            }

            List<Map<String, Object>> fields = new ArrayList<>();
            for (FieldNode field : queryEngine.getFields(cls.get())) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("name", field.getName());
                f.put("qualified_name", field.getQualifiedName());
                f.put("type", field.getDataType());
                if (field.getFile() != null) {
                    f.put("file", relativePath(field.getFile()));
                    if (field.getStartPosition() != null) {
                        f.put("line", field.getStartPosition().line());
                    }
                }
                fields.add(f);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("class", fqn);
            result.put("count", fields.size());
            result.put("fields", fields);
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
            map.put("file", relativePath(cls.getFile()));
            if (cls.getStartPosition() != null) {
                map.put("line", cls.getStartPosition().line());
            }
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
    public static class AskHandler extends BaseHandler {

        private static final int MAX_LINES = 50;

        /** Tests-answer row cap: round 6 measured 50 rows x ~200B
         * (FQN + path + coverage) = 9-12KB per ask, re-asked 2-3x
         * per question. 20 rows keeps the direct coverage visible. */
        private static final int MAX_TEST_ROWS = 20;

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

            // The JDK's HttpServer (19+) already percent-DECODES
            // getRequestURI().getQuery() once - an explicit URLDecoder
            // here decoded a second time, turning a literal "%3F" in a
            // question into "?" (dubbo round-6 spot check). Only '+'
            // still needs decoding to a space.
            String question = raw.replace('+', ' ');

            Map<String, Object> result = answerQuestion(question);
            
            // Stamp the response as stale when the project changed on disk
            if (staleness != null) {
                staleness.checkIfDue();
                if (staleness.isStale()) {
                    result.put("stale", true);
                    result.put("stale_files", staleness.getChangedFiles().size());
                    result.put("stale_hint", "Files changed since the graph was built; "
                        + "POST /edited with the changed paths for a targeted update, "
                        + "or POST /refresh for a full background rebuild.");
                }
            }
            
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

            ClassResolution resolution = resolveClass(candidates, queryEngine);
            String classFqn = resolution.fqn();
            String methodName = resolveMethod(candidates, queryEngine);
            MethodNode fuzzyMethod = resolveMethodFuzzy(candidates, queryEngine);

            if (lower.contains("implement")) {
                Map<String, Object> implResult = classQuery(question, resolution, "implementations",
                    "No class or interface name found in the question");
                // "where is ... its compareTo implementation" names a class,
                // not an interface: zero implementations + location intent
                // must fall through to the location routes (round-5 fix)
                boolean zeroImpls = "implementations".equals(implResult.get("matched"))
                    && ((Integer) implResult.get("count")) == 0;
                if (!zeroImpls || !lower.contains("where")) {
                    return implResult;
                }
            }
            if (lower.contains("subclass") || lower.contains("extends")) {
                return classQuery(question, resolution, "subclasses",
                    "No class name found in the question");
            }
            if (lower.contains("call") || lower.contains("use") || lower.contains("uses")
                || lower.contains("read")) {
                // a named field? -> field-usage answer before method callers
                Map<String, Object> fieldResult = fieldUsageQuery(question, candidates);
                if (fieldResult != null) {
                    return fieldResult;
                }
                // "where is the separator pattern used to split a version
                // string defined": location intent + no named field - search
                // field NAMES before answering coincidental method callers
                // (round-5: 'version' resolved as a method name and hijacked
                // the question to a callers answer). Skipped when the
                // question carries a literal (value-question territory).
                if (lower.contains("where")
                    && (lower.contains("defined") || lower.contains("declared"))
                    && extractLiterals(question).isEmpty()) {
                    Map<String, Object> fieldLocation = fieldLocationQuery(question, candidates);
                    if (fieldLocation != null) {
                        return fieldLocation;
                    }
                }
                // callers only on call-intent: "which methods read and write
                // it" names no callable - answering callers of read() is a
                // mis-route (round-5 verification: 65KB answer)
                if (lower.contains("call")) {
                    return callersQuery(question, methodName);
                }
                return unmatched(question, candidates,
                    "No field named in the question - the referenced symbol may be a "
                        + "method parameter or local variable, which the graph does not model");
            }
            // flow/path questions: only handled when a path actually resolves,
            // otherwise fall through to the other routes
            if (lower.contains("path") || lower.contains("flow")
                || (lower.contains("from") && lower.contains("to"))) {
                Map<String, Object> pathResult = askCallPath(question, candidates);
                if (pathResult != null) {
                    return pathResult;
                }
            }
            if (TEST_QUESTION.matcher(lower).find()) {
                return testsQuery(question, candidates);
            }
            // value questions: "where is 9200 defined", 'where is "http.port" the default'
            List<String> literals = extractLiterals(question);
            if (!literals.isEmpty()
                && (lower.contains("where") || lower.contains("defined")
                    || lower.contains("declared") || lower.contains("default")
                    || lower.contains("constant") || lower.contains("value"))) {
                Map<String, Object> valueResult = valuesQuery(question, literals);
                if (valueResult != null) {
                    return valueResult;
                }
            }
            if (lower.contains("method")) {
                return classQuery(question, resolution, "methods",
                    "No class name found in the question");
            }
            if (lower.contains("field") || lower.contains("variable") || lower.contains("member")) {
                if (resolution.fqn() != null) {
                    return classQuery(question, resolution, "fields",
                        "No class name found in the question");
                }
                // no class in the question: the tokens may name a FIELD
                // ("where is the separator pattern defined") - answer its
                // usages or location instead of failing on class resolution
                Map<String, Object> fieldUsage = fieldUsageQuery(question, candidates);
                if (fieldUsage != null) {
                    return fieldUsage;
                }
                if (extractLiterals(question).isEmpty()) {
                    Map<String, Object> fieldLocation = fieldLocationQuery(question, candidates);
                    if (fieldLocation != null) {
                        return fieldLocation;
                    }
                }
                return classQuery(question, resolution, "fields",
                    "No class name found in the question");
            }
            if (lower.contains("where") || lower.contains("defined") || lower.contains("declared")
                || lower.contains("live") || lower.contains("located") || lower.contains("location")) {
                return locationQuery(question, resolution, methodName, fuzzyMethod);
            }

            // Fallback: name search
            if (classFqn != null) {
                return classQuery(question, resolution, "class-detail", null);
            }
            if (methodName != null) {
                return methodQuery(question, methodName);
            }
            if (fuzzyMethod != null) {
                return methodDetailQuery(question, fuzzyMethod);
            }
            return unmatched(question, candidates);
        }

        /**
         * Result of resolving a class from question identifiers: the fqn
         * when resolved, or the candidate list when ambiguous.
         */
        public record ClassResolution(String fqn, List<String> ambiguous) {}

        /**
         * Extracts identifier tokens from the question. Dotted sequences
         * ("org.elasticsearch.common.Strings") are kept whole so fully
         * qualified references resolve exactly instead of degenerating
         * into package-segment noise.
         */
        public static List<String> extractIdentifiers(String question) {
            List<String> tokens = new ArrayList<>();
            for (String part : question.split("[^A-Za-z0-9._$]+")) {
                if (part == null || part.isEmpty()) continue;
                String t = part.trim();
                // individual segments of dotted tokens are handled during
                // scoring; whole tokens resolve FQNs exactly
                if (t.length() >= 2 && !STOP_WORDS.contains(t.toLowerCase())) {
                    tokens.add(t);
                }
            }
            return tokens;
        }

        /**
         * Resolves a class from question identifiers. Order of preference:
         * exact fqn match, unique simple-name match, then scored match
         * (package-segment hints from other tokens, non-nested preferred).
         * Ambiguous names report their candidates instead of guessing.
         */
        public static ClassResolution resolveClass(List<String> candidates, GraphQueryEngine engine) {
            // fully qualified references win
            for (String c : candidates) {
                if (c.indexOf('.') >= 0
                    && engine.findClassByQualifiedName(c).isPresent()) {
                    return new ClassResolution(c, null);
                }
            }
            // longest simple names first (more specific)
            List<String> sorted = new ArrayList<>(candidates);
            sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
            for (String c : sorted) {
                if (c.indexOf('.') >= 0) continue;
                List<ClassNode> byName = engine.findClassesByName(c);
                if (byName.isEmpty()) continue;
                if (byName.size() == 1) {
                    return new ClassResolution(byName.get(0).getQualifiedName(), null);
                }
                // score candidates
                ClassNode best = null;
                int bestScore = Integer.MIN_VALUE;
                boolean tie = false;
                for (ClassNode cls : byName) {
                    int score = scoreClass(cls, candidates);
                    if (score > bestScore) {
                        best = cls;
                        bestScore = score;
                        tie = false;
                    } else if (score == bestScore) {
                        tie = true;
                    }
                }
                if (best != null && !tie) {
                    return new ClassResolution(best.getQualifiedName(), null);
                }
                List<String> alternatives = new ArrayList<>();
                for (ClassNode cls : byName) {
                    alternatives.add(cls.getQualifiedName());
                    if (alternatives.size() >= 5) break;
                }
                return new ClassResolution(null, alternatives);
            }
            return new ClassResolution(null, null);
        }

        /**
         * Scores a class candidate against the question's identifier
         * tokens: package-segment matches are strong hints, non-nested
         * classes are preferred over nested ones with the same simple name.
         */
        private static int scoreClass(ClassNode cls, List<String> candidates) {
            String fqn = cls.getQualifiedName();
            int score = 0;
            if (!fqn.contains("$")) {
                score += 2;
            }
            Set<String> segments = new HashSet<>();
            for (String seg : fqn.split("[.$]")) {
                segments.add(seg.toLowerCase());
            }
            for (String c : candidates) {
                String cl = c.toLowerCase();
                if (segments.contains(cl)) {
                    score += 3;
                } else if (c.indexOf('.') >= 0) {
                    // dotted candidate: its segments count as weak hints
                    for (String seg : c.split("\\.")) {
                        if (segments.contains(seg.toLowerCase())) {
                            score += 1;
                        }
                    }
                } else if (fqn.toLowerCase().contains(cl)) {
                    score += 1;
                }
            }
            return score;
        }

        /**
         * First candidate that resolves to a method name. Dotted candidates
         * ("$Gson$Types.resolve") contribute their last segment first, so
         * "who calls X.resolve" answers callers of resolve rather than
         * latching onto a coincidental plain word like "main" (source).
         */
        public static String resolveMethod(List<String> candidates, GraphQueryEngine engine) {
            for (String c : candidates) {
                if (c.indexOf('.') >= 0) {
                    String last = c.substring(c.lastIndexOf('.') + 1);
                    if (last.length() >= 2 && !engine.findMethodsByName(last).isEmpty()) {
                        return last;
                    }
                }
            }
            for (String c : candidates) {
                if (c.indexOf('.') >= 0) continue;
                if (!engine.findMethodsByName(c).isEmpty()) return c;
            }
            return null;
        }

        private Map<String, Object> classQuery(String question, ClassResolution resolution,
                                               String kind, String missingMsg) {
            String classFqn = resolution.fqn();
            if (classFqn == null) {
                if (resolution.ambiguous() != null && !resolution.ambiguous().isEmpty()) {
                    Map<String, Object> result = unmatched(question, null, null);
                    result.put("matched", "ambiguous-class");
                    result.put("candidates", resolution.ambiguous());
                    result.put("hint", "Multiple classes match. Re-ask with the fully "
                        + "qualified name, or query one of the candidates directly.");
                    return result;
                }
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
                    sb.append("  File: ").append(relativePath(c.getFile()));
                    if (c.getStartPosition() != null) {
                        sb.append(":").append(c.getStartPosition().line());
                    }
                    sb.append("\n");
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
            
            String classFqn = resolveClass(candidates, queryEngine).fqn();
            if (classFqn != null) {
                targetClasses.add(classFqn);
            } else {
                // fuzzy method resolution: match a candidate stem against
                // method names (e.g. "validation" -> validateIndexName)
                MethodNode best = resolveMethodFuzzy(candidates, queryEngine);
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
            int shown = 0;
            // per-test-method coverage is the bulk of the answer bytes
            // (round-4/5: 8KB raw vs ~2KB without) - include only on request
            boolean includeMethods = question.toLowerCase().contains("method");
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
                        total++;
                        // cap the listed tests like every other answer:
                        // big suites listed 314 classes / 59KB (dubbo
                        // round-6 spot check). The header count and the
                        // result count stay the true totals.
                        if (shown < MAX_TEST_ROWS) {
                            Map<?, ?> tm = (Map<?, ?>) t;
                            sb.append("  - ").append(tm.get("test_class"));
                            Object file = tm.get("file");
                            if (file != null) {
                                sb.append("  [").append(relativePath(file.toString())).append("]");
                            }
                            Object cov = tm.get("coverage");
                            if (cov != null) {
                                sb.append("  (").append(cov).append(")");
                            }
                            sb.append("\n");
                            if (includeMethods) {
                                Object methods = tm.get("test_methods");
                                if (methods instanceof Map && !((Map<?, ?>) methods).isEmpty()) {
                                    for (var e : ((Map<?, ?>) methods).entrySet()) {
                                        sb.append("      ").append(e.getKey()).append("\n");
                                    }
                                }
                            }
                            shown++;
                        }
                        total++;
                    }
                }
                if (tests.size() > shown) {
                    sb.append("  ... showing ").append(shown).append(" of ")
                      .append(tests.size()).append(" tests - narrow with the ")
                      .append("link=call-graph filter (strict coverage) or use ")
                      .append("/tests/{fqn}?methods=true for the full map\n");
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
        /**
         * Fuzzy method resolution. Candidate tokens are stemmed (plural/
         * gerund/-ion suffixes stripped), and methods are scored by how many
         * of the question's stems their name contains - a method covering
         * three tokens (validateIndexName for "index name validation")
         * must outrank an exact-but-generic single-token match (index()).
         */
        public static MethodNode resolveMethodFuzzy(List<String> candidates, GraphQueryEngine engine) {
            // stem every candidate once
            List<String> stems = new ArrayList<>();
            for (String candidate : candidates) {
                String stem = candidate.toLowerCase();
                for (String suffix : new String[]{"ation", "ion", "ing", "ed", "es", "s"}) {
                    if (stem.endsWith(suffix) && stem.length() > suffix.length()) {
                        stem = stem.substring(0, stem.length() - suffix.length());
                        break;
                    }
                }
                if (stem.length() >= 3 && !stems.contains(stem)) {
                    stems.add(stem);
                }
            }
            if (stems.isEmpty()) {
                return null;
            }
            
            MethodNode best = null;
            int bestScore = 0;
            int bestMatchedStems = 0;
            int bestLongestStem = 0;
            for (MethodNode m : engine.getAllMethods()) {
                String name = m.getName().toLowerCase();
                int matched = 0;
                int length = 0;
                int longestStem = 0;
                for (String stem : stems) {
                    if (name.contains(stem)) {
                        matched++;
                        length += stem.length();
                        longestStem = Math.max(longestStem, stem.length());
                    }
                }
                if (matched == 0) {
                    continue;
                }
                int score = matched * 40 + length;
                // Prefer production methods: "validateIndexName" vs the test
                // method "testValidateIndexName" have identical stems
                if (m.getClassName() != null && engine.findClassByQualifiedName(m.getClassName())
                        .filter(TestsHandler::isTestClass).isPresent()) {
                    score -= 15;
                }
                // exact name match adds a bonus only when the name is rare:
                // common names like get/index/build are not a signal
                boolean exactRare = false;
                for (String c : candidates) {
                    if (c.indexOf('.') < 0 && name.equals(c.toLowerCase())
                        && engine.findMethodsByName(m.getName()).size() <= 2) {
                        exactRare = true;
                        break;
                    }
                }
                if (exactRare) {
                    score += 20;
                }
                if (score > bestScore) {
                    best = m;
                    bestScore = score;
                    bestMatchedStems = matched;
                    bestLongestStem = longestStem;
                }
            }
            // Confidence gate (round-4 Q9 / round-5 findings): a single
            // short stem matching a method name is a spurious keyword hit,
            // not an answer - "where is the number 8094 defined" must not
            // resolve to findsQuotedLiteralAndPlainNumber. Serve the fuzzy
            // match only when it is backed by several question stems or one
            // long, specific stem; otherwise return null so the caller
            // reports matched=null (the honest, cheap failure mode).
            if (best != null && bestMatchedStems < 2 && bestLongestStem < 8) {
                return null;
            }
            return best;
        }

        /**
         * Handles "how does X flow/reach Y" questions via the call-path
         * index. Returns null when no path resolves so the caller can try
         * other routes.
         */
        private Map<String, Object> askCallPath(String question, List<String> candidates) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("from\\s+([\\w.$]+).*?\\bto\\s+([\\w.$]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
            
            String fromSpec = null;
            String toSpec = null;
            if (m.find()) {
                fromSpec = m.group(1);
                toSpec = m.group(2);
            } else if (candidates.size() >= 2) {
                // e.g. "path between RestController and TransportCreateIndexAction"
                fromSpec = candidates.get(0);
                toSpec = candidates.get(1);
            }
            if (fromSpec == null || toSpec == null) {
                return null;
            }
            
            Map<String, Object> result = callPaths(fromSpec, toSpec, 5, graph);
            int count = (int) result.get("path_count");
            if (count == 0) {
                return null;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("Call paths from ").append(fromSpec).append(" to ").append(toSpec)
              .append(" (").append(count).append("):\n");
            for (Object p : (List<?>) result.get("paths")) {
                sb.append("  - ").append(p).append("\n");
            }
            return matched(question, "call-path", sb.toString(), count);
        }

        /**
         * Extracts literal tokens from a question: quoted strings and
         * digit sequences not glued to an identifier (so "method2" and
         * package segments do not count).
         */
        private static final java.util.regex.Pattern LITERAL_PATTERN =
            java.util.regex.Pattern.compile("\"([^\"]{1,200})\"|(?<![\\w.$-])\\d+(?:[-.]\\d+)*");

        static List<String> extractLiterals(String question) {
            List<String> literals = new ArrayList<>();
            java.util.regex.Matcher m = LITERAL_PATTERN.matcher(question);
            while (m.find() && literals.size() < 3) {
                String quoted = m.group(1);
                literals.add(quoted != null ? quoted : m.group());
            }
            return literals;
        }

        /**
         * Answers value questions by searching the field-initializer
         * value index. Returns null when nothing matches so the caller
         * can fall through to other routes.
         */
        private Map<String, Object> valuesQuery(String question, List<String> literals) {
            StringBuilder sb = new StringBuilder();
            int total = 0;
            for (String literal : literals) {
                List<Indexes.ValueOccurrence> occurrences =
                    queryEngine.getIndexes().findValuesContaining(literal, 20);
                if (occurrences.isEmpty()) {
                    continue;
                }
                sb.append("Literal '").append(literal).append("' in field initializers (")
                  .append(occurrences.size()).append("):\n");
                for (Indexes.ValueOccurrence occ : occurrences) {
                    sb.append("  - ").append(occ.classFqn()).append(".").append(occ.fieldName())
                      .append(" = \"").append(occ.value()).append("\"");
                    if (occ.file() != null) {
                        sb.append("  [").append(relativePath(occ.file().toString()));
                        if (occ.line() > 0) {
                            sb.append(":").append(occ.line());
                        }
                        sb.append("]");
                    }
                    sb.append("\n");
                    total++;
                }
            }
            if (total == 0) {
                return null;
            }
            return matched(question, "value-location", sb.toString(), total);
        }

        /**
         * Answers "who uses <field>" by resolving a candidate token to a
         * field and listing the methods that reference it. Returns null
         * when no candidate names a known field.
         */
        private Map<String, Object> fieldUsageQuery(String question, List<String> candidates) {
            for (String c : candidates) {
                List<FieldNode> fields = queryEngine.getIndexes().getFieldsByName(c);
                if (fields.isEmpty()) {
                    continue;
                }
                // a candidate that also names a CLASS is a class reference,
                // not a field: 'who calls URLStrParser to turn raw URL
                // strings...' must not answer usages of a test field that
                // happens to be named URL (dubbo round-6 spot check)
                if (!queryEngine.findClassesByName(c).isEmpty()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                int total = 0;
                for (FieldNode f : fields) {
                    List<Indexes.FieldUsage> usages =
                        queryEngine.getIndexes().getFieldUsages(f.getQualifiedName());
                    if (usages.isEmpty()) {
                        continue;
                    }
                    sb.append("Usages of ").append(f.getQualifiedName())
                      .append(" (").append(usages.size()).append("):\n");
                    int shown = 0;
                    for (Indexes.FieldUsage u : usages) {
                        if (shown >= 50) {
                            sb.append("  ... and ").append(usages.size() - shown).append(" more\n");
                            break;
                        }
                        sb.append("  - ").append(u.methodQualifiedName());
                        if (u.file() != null) {
                            sb.append("  [").append(relativePath(u.file().toString()));
                            if (u.line() > 0) {
                                sb.append(":").append(u.line());
                            }
                            sb.append("]");
                        }
                        sb.append("\n");
                        shown++;
                        total++;
                    }
                }
                if (total > 0) {
                    return matched(question, "field-usage", sb.toString(), total);
                }
            }
            return null;
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
                // count-first, 10 rows: round 6 measured 50 rows x ~120B on
                // hub methods (URL.valueOf: 57KB answer); the count line is
                // often the whole answer the agent needs
                for (int i = 0; i < Math.min(callers.size(), 10); i++) {
                    MethodNode caller = callers.get(i);
                    sb.append("    - ").append(caller.getQualifiedName());
                    if (caller.getFile() != null) {
                        sb.append("  [").append(relativePath(caller.getFile()));
                        if (caller.getStartPosition() != null) {
                            sb.append(":").append(caller.getStartPosition().line());
                        }
                        sb.append("]");
                    }
                    sb.append("\n");
                }
                if (callers.size() > 10) {
                    sb.append("    ... and ").append(callers.size() - 10)
                      .append(" more caller(s) - ask about a more specific ")
                      .append("method signature or class to narrow\n");
                }
                total += callers.size();
            }
            if (methods.isEmpty()) {
                return unmatched(question, null, "No method found named: " + methodName);
            }
            return matched(question, "callers", sb.toString(), total);
        }

        private Map<String, Object> locationQuery(String question, ClassResolution resolution,
                                                  String methodName, MethodNode fuzzyMethod) {
            if (resolution.fqn() != null) {
                return classQuery(question, resolution, "class-location", null);
            }
            // An exact method-name match only wins when the name is rare:
            // "where is index name validation defined" must not match the
            // dozens of methods literally named index() over the fuzzy
            // resolution validateIndexName
            if (methodName != null
                && queryEngine.findMethodsByName(methodName).size() <= 3) {
                return methodQuery(question, methodName);
            }
            if (fuzzyMethod != null) {
                // "where is index name validation performed" -> validateIndexName
                return methodDetailQuery(question, fuzzyMethod);
            }
            if (methodName != null) {
                return methodQuery(question, methodName);
            }
            if (resolution.ambiguous() != null && !resolution.ambiguous().isEmpty()) {
                Map<String, Object> result = unmatched(question, null, null);
                result.put("matched", "ambiguous-class");
                result.put("candidates", resolution.ambiguous());
                result.put("hint", "Multiple classes match. Re-ask with the fully "
                    + "qualified name, or query one of the candidates directly.");
                return result;
            }
            // last resort before null: a field NAMED like the question's
            // tokens ("where is the separator pattern defined" -> V_SEP).
            // Never when the question carries a literal the value route
            // already failed on: "where is the number 8094 defined" is a
            // value question, and serving NUMBER_* fields is a mis-route.
            if (extractLiterals(question).isEmpty()) {
                Map<String, Object> fieldLocation =
                    fieldLocationQuery(question, extractIdentifiers(question));
                if (fieldLocation != null) {
                    return fieldLocation;
                }
            }
            return unmatched(question, null, "No class or method name found in the question");
        }

        /**
         * Splits a field name into lowercase segments (camel humps and
         * underscores): "V_SEP" -> ["v", "sep"], "snapshotInfo" ->
         * ["snapshot", "info"].
         */
        public static List<String> fieldNameSegments(String name) {
            List<String> segments = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (ch == '_' || ch == '$') {
                    if (cur.length() > 0) {
                        segments.add(cur.toString().toLowerCase());
                        cur.setLength(0);
                    }
                    continue;
                }
                // camel-case boundary: UPPER starts a new segment when it
                // follows a lowercase letter ("snapshotInfo") or when it
                // begins an UPPER-run that ends in lowercase ("parseURLValue")
                if (!cur.isEmpty() && Character.isUpperCase(ch)) {
                    boolean prevLower = Character.isLowerCase(name.charAt(i - 1));
                    boolean nextLower = i + 1 < name.length()
                        && Character.isLowerCase(name.charAt(i + 1));
                    if (prevLower || nextLower) {
                        segments.add(cur.toString().toLowerCase());
                        cur.setLength(0);
                    }
                }
                if (Character.isLetterOrDigit(ch)) {
                    cur.append(Character.toLowerCase(ch));
                } else if (cur.length() > 0) {
                    segments.add(cur.toString().toLowerCase());
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) {
                segments.add(cur.toString().toLowerCase());
            }
            return segments;
        }

        /**
         * Finds fields whose name matches a question token by segment
         * prefix (>= 3 chars): "separator" matches a field segment "sep",
         * "pattern" matches "pattern". Serves "where is the separator
         * pattern defined" without the agent knowing the field name
         * (round-5 jackson Q5: both gv arms needed this route).
         */
        public static List<FieldNode> findFieldsByStem(List<String> candidates, GraphQueryEngine engine) {
            List<String> stems = new ArrayList<>();
            for (String candidate : candidates) {
                if (candidate.indexOf('.') >= 0 || candidate.length() < 5) {
                    // 5-char floor keeps function words like "into" from
                    // matching every INT_* field (round-5 verification)
                    continue;
                }
                String stem = candidate.toLowerCase();
                for (String suffix : new String[]{"ation", "ion", "ing", "ed", "es", "s"}) {
                    if (stem.endsWith(suffix) && stem.length() > suffix.length() + 2) {
                        stem = stem.substring(0, stem.length() - suffix.length());
                        break;
                    }
                }
                if (stem.length() >= 6 && !stems.contains(stem)) {
                    // 6-char stem floor: short generic stems ("write",
                    // "number") drag in unrelated fields - the subject of a
                    // field-location question is a specific term
                    stems.add(stem);
                }
            }
            if (stems.isEmpty()) {
                return Collections.emptyList();
            }
            // rank: earlier question tokens are more central to the ask
            // ("separator" in "where is the separator pattern" outranks the
            // later "minor" of "major/minor/patch"); exact segment beats an
            // abbreviation, which beats a prefix; constants beat instances
            record Scored(FieldNode field, int score) {}
            List<Scored> scored = new ArrayList<>();
            for (FieldNode f : engine.getIndexes().getAllFields()) {
                List<String> segments = fieldNameSegments(f.getName());
                // bonus when the DECLARING CLASS also matches a question
                // token: V_SEP in VersionUtil for the "version string" ask
                int classBonus = 0;
                if (f.getClassName() != null) {
                    for (String stem : stems) {
                        for (String cseg : fieldNameSegments(f.getClassName())) {
                            if (cseg.length() >= 4
                                && (cseg.equals(stem) || cseg.startsWith(stem)
                                    || stem.startsWith(cseg) && stem.length() >= cseg.length() * 2)) {
                                classBonus = 15;
                                break;
                            }
                        }
                        if (classBonus > 0) {
                            break;
                        }
                    }
                }
                int best = Integer.MIN_VALUE;
                for (int si = 0; si < stems.size(); si++) {
                    if (si >= 3) {
                        // only the question's first content tokens can drive
                        // the match: they carry the subject ("where is
                        // VISITEDTYPEVARIABLES declared" - a later "write"
                        // token must not drag in numWrites/currentWrite;
                        // round-5 verification, the r4-Q9 failure mode)
                        break;
                    }
                    String stem = stems.get(si);
                    int kindBonus = 0;
                    boolean hit = false;
                    for (String seg : segments) {
                        if (seg.length() < 3) {
                            continue;
                        }
                        if (seg.equals(stem)) {
                            hit = true;
                            kindBonus = 3;
                            break;
                        }
                        // abbreviation: field segment "sep" abbreviated the
                        // question's "separator" - require the stem to be
                        // at least twice as long so "min" <- "minor" and
                        // "int" <- "into" cannot fire
                        if (stem.startsWith(seg) && stem.length() >= seg.length() * 2) {
                            hit = true;
                            kindBonus = 2;
                            break;
                        }
                        // stem is a specific prefix of the segment
                        if (seg.startsWith(stem) && stem.length() >= 4) {
                            hit = true;
                            kindBonus = 1;
                            break;
                        }
                    }
                    if (hit) {
                        int score = (stems.size() - si) * 10 + kindBonus + classBonus
                            + (f.isStatic() ? 2 : 0) + (f.isFinal() ? 1 : 0);
                        best = Math.max(best, score);
                    }
                }
                if (best != Integer.MIN_VALUE) {
                    scored.add(new Scored(f, best));
                }
            }
            scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
            List<FieldNode> matches = new ArrayList<>();
            for (Scored s : scored) {
                matches.add(s.field());
                if (matches.size() >= 20) {
                    break;
                }
            }
            return matches;
        }

        /**
         * Answers "where is the <thing> defined" by field-name search when
         * no class or method resolved: lists the matching fields with their
         * declaring class, type, initializer value (when indexed) and
         * file:line. Returns null when no field matches.
         */
        private Map<String, Object> fieldLocationQuery(String question, List<String> candidates) {
            List<FieldNode> fields = findFieldsByStem(candidates, queryEngine);
            if (fields.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            int total = 0;
            sb.append("Fields matching the question (").append(fields.size()).append("):\n");
            for (FieldNode f : fields) {
                sb.append("  - ").append(f.getQualifiedName()).append(": ").append(f.getDataType());
                String value = null;
                for (Indexes.ValueOccurrence occ : queryEngine.getIndexes().getAllValues()) {
                    if (occ.classFqn() != null && occ.classFqn().equals(f.getClassName())
                        && occ.fieldName() != null && occ.fieldName().equals(f.getName())) {
                        value = occ.value();
                        break;
                    }
                }
                if (value != null) {
                    sb.append("  = \"").append(value).append("\"");
                }
                if (f.getFile() != null) {
                    sb.append("  [").append(relativePath(f.getFile()));
                    if (f.getStartPosition() != null) {
                        sb.append(":").append(f.getStartPosition().line());
                    }
                    sb.append("]");
                }
                sb.append("\n");
                total++;
            }
            return matched(question, "field-location", sb.toString(), total);
        }

        /**
         * Answers with the location of one specific method node.
         */
        private Map<String, Object> methodDetailQuery(String question, MethodNode m) {
            StringBuilder sb = new StringBuilder();
            sb.append("Method ").append(m.getQualifiedName()).append("\n");
            sb.append("  Signature: ").append(m.getSignature()).append("\n");
            if (m.getFile() != null) {
                sb.append("  File: ").append(relativePath(m.getFile()));
                if (m.getStartPosition() != null) {
                    sb.append(":").append(m.getStartPosition().line());
                }
                sb.append("\n");
            }
            if (m.isStatic()) {
                sb.append("  Modifier: static\n");
            }
            if (m.isPublic()) {
                sb.append("  Modifier: public\n");
            }
            return matched(question, "method-location", sb.toString(), 1);
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
                    sb.append("  [").append(relativePath(c.getFile()));
                    if (c.getStartPosition() != null) {
                        sb.append(":").append(c.getStartPosition().line());
                    }
                    sb.append("]");
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
                    sb.append("  [").append(relativePath(m.getFile()));
                    if (m.getStartPosition() != null) {
                        sb.append(":").append(m.getStartPosition().line());
                    }
                    sb.append("]");
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
                    sb.append("  [").append(relativePath(f.getFile()));
                    if (f.getStartPosition() != null) {
                        sb.append(":").append(f.getStartPosition().line());
                    }
                    sb.append("]");
                }
                sb.append("\n");
            }
            if (fields.size() > count) {
                sb.append("  ... and ").append(fields.size() - count).append(" more\n");
            }
        }

        /**
         * Hard ceiling on any ask answer. Round 6 measured single asks at
         * 57KB (callers of URL.valueOf) and 9-12KB per tests ask, re-asked
         * 2-3 times per question - all result bytes enter the agent context
         * uncached, and this was the entire gv-vs-grep cost premium on
         * dubbo. Truncation happens at a line boundary with a pointer to
         * the raw endpoint.
         */
        private static final int MAX_ANSWER_BYTES = 4096;

        private Map<String, Object> matched(String question, String kind, String answer, int count) {
            if (answer != null && answer.length() > MAX_ANSWER_BYTES) {
                int cut = answer.lastIndexOf('\n', MAX_ANSWER_BYTES);
                if (cut <= 0) {
                    cut = MAX_ANSWER_BYTES;
                }
                int omitted = answer.length() - cut;
                answer = answer.substring(0, cut)
                    + "... [truncated " + omitted + " chars - narrow the question "
                    + "(more specific method/class) or use the raw endpoint for the full list]\n";
            }
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
                "/classes/{fqn}/fields, /classes/{fqn}/subclasses, " +
                "/classes/{fqn}/implementations, /methods, /packages, /search?q=NAME. " +
                "If a known class owns the symbol, ask 'what fields does <C> have' " +
                "(or class_members with relation=fields); method parameters and " +
                "local variables are not in the graph - grep them.");
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
                // the reparsed graph diverges from the cached one
                GraphCache.invalidate(Path.of(projectPath));
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

            // optional filters: ?link=call-graph (strict coverage semantics,
            // excludes 2-hop/exception/name-convention links) and
            // ?methods=true to opt into the per-test-method coverage map
            // (compact by default - the map is the dominant payload)
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            Set<String> linkTypes = null;
            String link = params.get("link");
            if (link != null && !link.isBlank()) {
                linkTypes = new HashSet<>();
                for (String part : link.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) {
                        linkTypes.add(t);
                    }
                }
            }
            boolean includeMethods = "true".equalsIgnoreCase(params.get("methods"));
            Map<String, Object> coverage =
                coverageFor(cls.get(), queryEngine, linkTypes, includeMethods);
            // ?limit=N: bound the raw payload for big suites (dubbo: 314
            // covering classes = ~60KB). test_count stays the true total.
            String limitRaw = params.get("limit");
            if (limitRaw != null && !limitRaw.isBlank()) {
                try {
                    int limit = Math.max(1, Integer.parseInt(limitRaw.trim()));
                    List<?> tests = (List<?>) coverage.get("tests");
                    if (tests != null && tests.size() > limit) {
                        coverage.put("tests", new ArrayList<>(tests.subList(0, limit)));
                        coverage.put("truncated", true);
                    }
                } catch (NumberFormatException ignored) {
                    // invalid limit: return unbounded
                }
            }
            return toJson(coverage);
        }
        
        /**
         * Computes test coverage for a production class.
         */
        public static Map<String, Object> coverageFor(ClassNode prodClass, GraphQueryEngine engine) {
            return coverageFor(prodClass, engine, null, true);
        }

        /**
         * Computes test coverage, optionally filtered by link type and without
         * the per-test-method coverage map (the map is the dominant payload:
         * round-4/5 measured 8KB raw vs ~2KB without). linkTypes null = all
         * link types; includeMethods false drops the per-test-method map.
         */
        public static Map<String, Object> coverageFor(ClassNode prodClass, GraphQueryEngine engine,
                                                    Set<String> linkTypes, boolean includeMethods) {
            String prodName = prodClass.getName();
            String prodFqn = prodClass.getQualifiedName();
            Set<String> prodMethodIds = new HashSet<>();
            for (MethodNode m : engine.getMethods(prodClass)) {
                prodMethodIds.add(m.getId());
            }

            // Exception names thrown by any production method: tests that
            // assert them verify behavior without calling the method directly
            Set<String> prodThrown = new HashSet<>();
            for (MethodNode m : engine.getMethods(prodClass)) {
                for (Indexes.ThrownException t : engine.getIndexes().getThrownByMethod(m.getQualifiedName())) {
                    if (!t.assertion()) {
                        prodThrown.add(t.exceptionName());
                    }
                }
            }

            // Map test-class fqn -> test method -> called prod methods
            Map<String, Map<String, Object>> coveringTests = new LinkedHashMap<>();

            for (ClassNode candidate : engine.getAllClasses()) {
                if (!isTestClass(candidate) || candidate.getQualifiedName().equals(prodFqn)) {
                    continue;
                }
                Map<String, List<String>> coveringMethods = new LinkedHashMap<>();
                boolean anyDirect = false;
                boolean anyIndirect = false;
                boolean anyExceptionLink = false;

                for (MethodNode testMethod : engine.getMethods(candidate)) {
                    java.util.Set<String> calledProd = new java.util.LinkedHashSet<>();
                    List<MethodNode> hop1 = engine.getCallees(testMethod);
                    int hop1Considered = 0;
                    for (MethodNode callee : hop1) {
                        if (prodMethodIds.contains(callee.getId())) {
                            calledProd.add(callee.getSignature());
                            anyDirect = true;
                        } else if (hop1Considered < 50) {
                            // two-hop: the test calls a helper that calls the prod class
                            hop1Considered++;
                            for (MethodNode hop2 : engine.getCallees(callee)) {
                                if (prodMethodIds.contains(hop2.getId())) {
                                    calledProd.add("[2hop] " + hop2.getSignature());
                                    anyIndirect = true;
                                }
                            }
                        }
                    }
                    for (Indexes.ThrownException t
                            : engine.getIndexes().getThrownByMethod(testMethod.getQualifiedName())) {
                        if (t.assertion() && prodThrown.contains(t.exceptionName())) {
                            calledProd.add("[throws " + t.exceptionName() + "]");
                            anyExceptionLink = true;
                        }
                    }
                    if (!calledProd.isEmpty()) {
                        coveringMethods.put(testMethod.getSignature(), new ArrayList<>(calledProd));
                    }
                }

                boolean nameMatch = candidate.getName().toLowerCase()
                    .contains(prodName.toLowerCase());
                if (!coveringMethods.isEmpty() || nameMatch) {
                    Map<String, Object> testEntry = new LinkedHashMap<>();
                    testEntry.put("test_class", candidate.getQualifiedName());
                    testEntry.put("file", relativePath(candidate.getFile()));
                    String coverage;
                    if (anyDirect) {
                        coverage = "call-graph";
                    } else if (anyIndirect) {
                        coverage = "call-graph-2hop";
                    } else if (anyExceptionLink) {
                        coverage = "exception-link";
                    } else {
                        coverage = "name-convention";
                    }
                    if (linkTypes != null && !linkTypes.contains(coverage)) {
                        continue;
                    }
                    testEntry.put("coverage", coverage);
                    if (includeMethods && !coveringMethods.isEmpty()) {
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
            String p = null;
            Path file = cls.getFile();
            if (file != null) {
                p = file.toString().replace('\\', '/');
            }
            // Name heuristics must not apply to production sources: a class
            // like "TestsHandler" in src/main is not a test.
            if (p != null && p.contains("/src/main/")) {
                return false;
            }
            String name = cls.getName();
            if (name.endsWith("Test") || name.endsWith("Tests")
                || name.endsWith("IT") || name.endsWith("TestCase")
                || name.startsWith("Test")) {
                return true;
            }
            if (p != null && (p.contains("/test/") || p.contains("/internalClusterTest/"))) {
                return true;
            }
            return false;
        }
    }

    /**
     * Call-path endpoint: how does execution flow from method A to
     * method B (transitive call chains, via the CallGraphIndex).
     *
     * GET /callpath?from=org.x.Foo.bar&to=org.x.Baz.qux&depth=5
     */
    static class CallPathHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            String from = params.get("from");
            String to = params.get("to");
            if (from == null || to == null || from.isEmpty() || to.isEmpty()) {
                return responseJson(400, Map.of("error",
                    "Missing 'from' and 'to' parameters (Class.method or method name)"));
            }
            int depth = 5;
            try {
                if (params.get("depth") != null) depth = Integer.parseInt(params.get("depth"));
            } catch (NumberFormatException ignored) {
                // keep default
            }
            return toJson(callPaths(from, to, depth, graph));
        }
    }

    /**
     * Finds call chains between two methods.
     *
     * Specs are "fqn.method" or a bare method name; ambiguous specs resolve
     * to up to 3 candidate methods each. Returns at most 10 paths.
     */
    /** Max call chains rendered per callpath answer. */
    private static final int MAX_CALL_PATHS = 10;

    public static Map<String, Object> callPaths(String fromSpec, String toSpec, int maxDepth, Graph targetGraph) {
        GraphQueryEngine engine = new GraphQueryEngine(targetGraph);
        CallGraphIndex callIndex = targetGraph.getIndexes().getCallGraphIndex();
        
        List<String> fromIds = resolveMethodSpec(fromSpec, engine, 3);
        List<String> toIds = resolveMethodSpec(toSpec, engine, 3);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", fromSpec);
        result.put("to", toSpec);
        
        if (fromIds.isEmpty() || toIds.isEmpty() || callIndex == null) {
            result.put("path_count", 0);
            result.put("paths", new ArrayList<>());
            if (callIndex == null) {
                result.put("hint", "Call graph index not enabled");
            } else {
                result.put("hint", "Could not resolve both method specs in the graph");
            }
            return result;
        }
        
        List<List<String>> allPaths = new ArrayList<>();
        // cap honored per path, not per batch: a single findCallPaths call
        // could return hundreds of chains and the old addAll-then-check
        // let 260 through into a list meant to hold 10 (round-5b: ~40KB
        // answer from one callpath question)
        outer:
        for (String fromId : fromIds) {
            for (String toId : toIds) {
                for (List<String> path : callIndex.findCallPaths(fromId, toId, maxDepth)) {
                    if (allPaths.size() >= MAX_CALL_PATHS) {
                        break outer;
                    }
                    allPaths.add(path);
                }
            }
        }
        
        List<String> rendered = new ArrayList<>();
        for (List<String> path : allPaths) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) sb.append(" -> ");
                sb.append(methodLabel(path.get(i), targetGraph));
            }
            rendered.add(sb.toString());
        }
        
        result.put("path_count", rendered.size());
        result.put("paths", rendered);
        return result;
    }
    
    /**
     * Resolves a method spec ("fqn.method" or bare name) to method node IDs,
     * capped at maxCandidates.
     */
    private static List<String> resolveMethodSpec(String spec, GraphQueryEngine engine, int maxCandidates) {
        String cls = null;
        String name = spec;
        int dot = spec.lastIndexOf('.');
        if (dot > 0) {
            cls = spec.substring(0, dot);
            name = spec.substring(dot + 1);
        }
        List<MethodNode> candidates = new ArrayList<>(engine.findMethodsByName(name));
        if (cls != null && !candidates.isEmpty()) {
            List<MethodNode> filtered = new ArrayList<>();
            for (MethodNode m : candidates) {
                String cn = m.getClassName();
                if (cn != null && (cn.equals(cls) || cn.endsWith("." + cls))) {
                    filtered.add(m);
                }
            }
            if (!filtered.isEmpty()) {
                candidates = filtered;
            }
        }
        List<String> ids = new ArrayList<>();
        for (MethodNode m : candidates) {
            if (ids.size() >= maxCandidates) break;
            ids.add(m.getId());
        }
        return ids;
    }
    
    /**
     * Renders a method node id as a readable label.
     */
    private static String methodLabel(String methodId, Graph targetGraph) {
        return targetGraph.getNode(methodId)
            .filter(n -> n instanceof MethodNode)
            .map(n -> ((MethodNode) n).getQualifiedName())
            .orElse(methodId);
    }

    /**
     * Background rebuild endpoint: POST /refresh rebuilds the whole graph
     * on a worker thread and swaps it in when done. The old graph keeps
     * serving queries during the rebuild.
     */
    static class RefreshHandler extends BaseHandler {
        @Override
        protected String handleRequest(HttpExchange exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                return responseJson(400, Map.of("error", "Use POST /refresh"));
            }
            if (!refreshing.compareAndSet(false, true)) {
                return toJson(Map.of("rebuilding", true, "detail", "A rebuild is already in progress"));
            }
            
            Thread t = new Thread(() -> {
                try {
                    System.out.println("Refresh: rebuilding graph for " + projectPath);
                    GausVibeBuilder newBuilder = new GausVibeBuilder(Path.of(projectPath));
                    newBuilder.setParallel(true);
                    newBuilder.setIncludeTestSources(true);
                    Graph newGraph = newBuilder.build();
                    newGraph.getIndexes().enableCallGraphIndex();
                    newGraph.getIndexes().getCallGraphIndex()
                        .indexCalls(newGraph.getIndexes().getEdgesByType(EdgeTypes.CALLS));
                    
                    // Swap atomically. Handlers may serve one request from a
                    // mixed old/new state during the swap itself; queries use
                    // queryEngine, which is assigned last.
                    synchronized (SWAP_LOCK) {
                        builder = newBuilder;
                        graph = newGraph;
                        queryEngine = new GraphQueryEngine(newGraph);
                    }
                    GraphCache.write(Path.of(projectPath), newGraph);
                    if (staleness != null) {
                        try {
                            staleness.captureBaseline();
                        } catch (java.io.IOException e) {
                            System.err.println("Warning: baseline recapture failed: " + e.getMessage());
                        }
                    }
                    System.out.println("Refresh complete: " + newGraph.getNodeCount()
                        + " nodes, " + newGraph.getEdgeCount() + " edges");
                } catch (Exception e) {
                    String cause = e.getCause() != null ? " (cause: " + e.getCause() + ")" : "";
                    System.err.println("Refresh failed: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage() + cause);
                } finally {
                    refreshing.set(false);
                }
            }, "gausvibe-refresh");
            t.setDaemon(true);
            t.start();
            
            return toJson(Map.of("rebuilding", true,
                "detail", "Rebuilding in the background; old graph serves until the swap"));
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
