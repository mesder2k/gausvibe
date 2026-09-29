package dk.gausdalfind.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP (Model Context Protocol) stdio server for GausVibe.
 *
 * Proxies tool calls to a running GausVibe HTTP server. It deliberately
 * does NOT build a graph itself: building takes minutes on large projects
 * and would block session startup. The HTTP daemon owns the graph; this
 * process is a stateless, instantly-available adapter.
 *
 * Protocol: JSON-RPC 2.0, one message per line, stdin/stdout.
 * Diagnostics go to stderr ONLY (stdout is the protocol channel).
 *
 * Usage:
 *   java -cp <classpath> dk.gausdalfind.server.GausVibeMcpServer \
 *     --url http://localhost:8080
 */
public class GausVibeMcpServer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public GausVibeMcpServer(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static void main(String[] args) throws Exception {
        String url = "http://localhost:8080";
        for (int i = 0; i < args.length; i++) {
            if ("--url".equals(args[i]) && i + 1 < args.length) {
                url = args[++i];
            }
        }
        GausVibeMcpServer server = new GausVibeMcpServer(url);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String response = server.handleLine(line);
            if (response != null) {
                System.out.println(response);
                System.out.flush();
            }
        }
    }

    /**
     * Handles one protocol line; returns the response line or null for
     * notifications (which take no response).
     */
    public String handleLine(String line) {
        JsonObject request;
        try {
            request = JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
        }
        String method = request.has("method") ? request.get("method").getAsString() : "";
        boolean hasId = request.has("id") && !request.get("id").isJsonNull();
        JsonElement id = hasId ? request.get("id") : null;

        try {
            JsonElement result = dispatch(method, request);
            if (result == null) {
                return null; // notification: no response
            }
            if (!hasId) {
                return null; // response to a request without id: nothing to send
            }
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            response.add("result", result);
            return GSON.toJson(response);
        } catch (MethodNotFound e) {
            if (!hasId) {
                return null;
            }
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            JsonObject error = new JsonObject();
            error.addProperty("code", -32601);
            error.addProperty("message", "Method not found: " + method);
            response.add("error", error);
            return GSON.toJson(response);
        }
    }

    private static final class MethodNotFound extends Exception {}

    private JsonElement dispatch(String method, JsonObject request) throws MethodNotFound {
        switch (method) {
            case "initialize": {
                JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
                String protocolVersion = params.has("protocolVersion")
                    ? params.get("protocolVersion").getAsString() : "2024-11-05";
                JsonObject result = new JsonObject();
                result.addProperty("protocolVersion", protocolVersion);
                JsonObject capabilities = new JsonObject();
                capabilities.add("tools", new JsonObject());
                result.add("capabilities", capabilities);
                JsonObject serverInfo = new JsonObject();
                serverInfo.addProperty("name", "gausvibe");
                serverInfo.addProperty("version", "1.0.0");
                result.add("serverInfo", serverInfo);
                return result;
            }
            case "notifications/initialized":
            case "notifications/cancelled":
                return null;
            case "ping":
                return new JsonObject();
            case "tools/list":
                return toolsList();
            case "tools/call":
                return toolsCall(request);
            case "resources/list":
            case "prompts/list":
                return JsonParser.parseString("{\"resources\":[],\"prompts\":[]}");
            default:
                throw new MethodNotFound();
        }
    }

    private JsonElement toolsList() {
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();
        for (ToolDef def : TOOL_DEFS.values()) {
            tools.add(def.toJson());
        }
        result.add("tools", tools);
        return result;
    }

    private JsonElement toolsCall(JsonObject request) throws MethodNotFound {
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        String name = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
            ? params.getAsJsonObject("arguments") : new JsonObject();

        ToolDef def = TOOL_DEFS.get(name);
        if (def == null) {
            throw new MethodNotFound();
        }

        String text;
        boolean isError = false;
        try {
            text = def.invoke(arguments, http, baseUrl);
        } catch (IllegalArgumentException e) {
            text = e.getMessage();
            isError = true;
        } catch (Exception e) {
            text = "GausVibe server not reachable at " + baseUrl + ". "
                + "Start it with: java -cp <classpath> dk.gausdalfind.server.GausVibeServer "
                + "--project <java-project> --port <port>. Falling back to grep/read is fine "
                + "until it is running.";
            isError = true;
        }

        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        JsonArray contentArr = new JsonArray();
        contentArr.add(content);
        JsonObject result = new JsonObject();
        result.add("content", contentArr);
        if (isError) {
            result.addProperty("isError", true);
        }
        return result;
    }

    // ==================== Tool definitions ====================

    private interface Invoker {
        String invoke(JsonObject args, HttpClient http, String baseUrl) throws Exception;
    }

    private static final class ToolDef {
        final String name;
        final String description;
        final Map<String, Object> schema; // property name -> {"type", "description"}
        final java.util.Set<String> required;
        final Invoker invoker;

        ToolDef(String name, String description, Map<String, Object> schema,
                java.util.Set<String> required, Invoker invoker) {
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.required = required;
            this.invoker = invoker;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("description", description);
            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            for (Map.Entry<String, Object> e : schema.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> prop = (Map<String, Object>) e.getValue();
                properties.add(e.getKey(), GSON.toJsonTree(prop));
            }
            inputSchema.add("properties", properties);
            JsonArray req = new JsonArray();
            for (String r : required) {
                req.add(r);
            }
            if (required.isEmpty()) {
                // no required key
            } else {
                inputSchema.add("required", req);
            }
            o.add("inputSchema", inputSchema);
            return o;
        }

        String invoke(JsonObject args, HttpClient http, String baseUrl) throws Exception {
            for (String r : required) {
                if (!args.has(r) || args.get(r).isJsonNull()) {
                    throw new IllegalArgumentException("Missing required argument '" + r + "' for tool " + name);
                }
            }
            return invoker.invoke(args, http, baseUrl);
        }
    }

    private static String getString(JsonObject args, String key) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString() : null;
    }

    private static String httpGet(HttpClient http, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body();
    }

    private static String httpPost(HttpClient http, String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static final Map<String, ToolDef> TOOL_DEFS = new LinkedHashMap<>();

    static {
        put(new ToolDef("ask",
            "Answer a structural question about a Java symbol you can NAME exactly. "
                + "Supported patterns ONLY: 'where is <X> defined' (class or method location), "
                + "'what methods does <X> have', 'what fields does <X> have', "
                + "'what implements <I>' / 'what subclasses <C>', 'who calls <M>' (method), "
                + "'which tests verify <C>' (class), and 'where is <literal> defined' for "
                + "numbers or quoted string literals (value index, e.g. 'where is 9200 "
                + "defined'). <X> must be an exact class or method "
                + "name from the indexed codebase. NOT free-form: no listing, enumeration, "
                + "or partial-name search - use the search, classes, packages, or "
                + "class_detail tools for those, and the callpath tool for execution flow. "
                + "The response reports the matched query type; matched=null means GausVibe "
                + "could not answer - then fall back to grep for that question.",
            Map.of("question", Map.of("type", "string",
                    "description", "The question, e.g. 'who calls registerHandler' or 'which tests verify MetadataCreateIndexService'")),
            java.util.Set.of("question"),
            (args, http, base) -> httpGet(http, base + "/ask?q=" + urlEncode(getString(args, "question")))));

        put(new ToolDef("tests",
            "List the tests covering a Java class, with method-level coverage (which test "
                + "methods call which of the class's methods) and file paths. Strongest way "
                + "to answer 'which tests verify/check X' or 'what happens if I change X'.",
            Map.of("class_fqn", Map.of("type", "string",
                    "description", "Fully qualified class name, e.g. org.elasticsearch.cluster.metadata.MetadataCreateIndexService")),
            java.util.Set.of("class_fqn"),
            (args, http, base) -> httpGet(http, base + "/tests/" + urlEncode(getString(args, "class_fqn")))));

        put(new ToolDef("callpath",
            "Find transitive call chains between two methods (how execution can get from A "
                + "to B). Specs are 'Class.method' or bare method names. Cannot cross "
                + "interface dispatch yet; if no path returns, chain one-hop callers via the "
                + "ask tool instead.",
            Map.of(
                "from", Map.of("type", "string", "description", "Starting method, e.g. RestController.dispatchRequest"),
                "to", Map.of("type", "string", "description", "Target method"),
                "depth", Map.of("type", "integer", "description", "Max hops (default 5)")),
            java.util.Set.of("from", "to"),
            (args, http, base) -> {
                StringBuilder url = new StringBuilder(base + "/callpath?from=")
                    .append(urlEncode(getString(args, "from")))
                    .append("&to=")
                    .append(urlEncode(getString(args, "to")));
                if (args.has("depth") && !args.get("depth").isJsonNull()) {
                    url.append("&depth=").append(args.get("depth").getAsInt());
                }
                return httpGet(http, url.toString());
            }));

        put(new ToolDef("search",
            "Find a class or method by exact simple NAME (a symbol name, not a sentence). "
                + "Deterministic lookup, no question parsing: classes first, then methods. "
                + "Use this instead of ask whenever you only have a name - and before "
                + "grepping for a symbol.",
            Map.of("name", Map.of("type", "string",
                "description", "Exact symbol name, e.g. MetadataCreateIndexService")),
            java.util.Set.of("name"),
            (args, http, base) -> httpGet(http, base + "/search?q=" + urlEncode(getString(args, "name")))));

        put(new ToolDef("class_detail",
            "Get a class's details by fully qualified name: file path, method and field "
                + "counts, superclass, interfaces.",
            Map.of("class_fqn", Map.of("type", "string",
                "description", "Fully qualified class name, e.g. org.elasticsearch.cluster.metadata.MetadataCreateIndexService")),
            java.util.Set.of("class_fqn"),
            (args, http, base) -> httpGet(http, base + "/classes/" + urlEncode(getString(args, "class_fqn")))));

        put(new ToolDef("class_members",
            "List a class's members or relations by fully qualified name: its methods "
                + "(default), its subclasses, or the implementations of an interface.",
            Map.of(
                "class_fqn", Map.of("type", "string", "description", "Fully qualified class or interface name"),
                "relation", Map.of("type", "string",
                    "description", "methods | subclasses | implementations (default: methods)")),
            java.util.Set.of("class_fqn"),
            (args, http, base) -> {
                String relation = getString(args, "relation");
                if (relation == null || relation.isEmpty()) {
                    relation = "methods";
                }
                if (!relation.equals("methods") && !relation.equals("subclasses")
                        && !relation.equals("implementations")) {
                    throw new IllegalArgumentException(
                        "relation must be methods, subclasses, or implementations, got: " + relation);
                }
                return httpGet(http, base + "/classes/" + urlEncode(getString(args, "class_fqn"))
                    + "/" + relation);
            }));

        put(new ToolDef("classes",
            "List classes in the indexed codebase filtered by Java package (e.g. "
                + "'org.elasticsearch.cluster.metadata') or fully-qualified-name prefix. "
                + "One of package/prefix is REQUIRED - there is no unfiltered class listing. "
                + "Returns at most 'limit' names plus the total match count.",
            Map.of(
                "package", Map.of("type", "string", "description", "Package filter, e.g. org.elasticsearch.cluster"),
                "prefix", Map.of("type", "string", "description", "Fully-qualified-name prefix filter"),
                "limit", Map.of("type", "integer", "description", "Max names to return (default 500, cap 1000)")),
            java.util.Set.of(),
            (args, http, base) -> {
                String pkg = getString(args, "package");
                String prefix = getString(args, "prefix");
                if ((pkg == null || pkg.isEmpty()) && (prefix == null || prefix.isEmpty())) {
                    throw new IllegalArgumentException(
                        "Provide 'package' or 'prefix' - unfiltered class listing is not supported");
                }
                int limit = 500;
                if (args.has("limit") && !args.get("limit").isJsonNull()) {
                    limit = Math.min(1000, Math.max(1, args.get("limit").getAsInt()));
                }
                StringBuilder url = new StringBuilder(base).append("/classes?");
                if (pkg != null && !pkg.isEmpty()) {
                    url.append("package=").append(urlEncode(pkg)).append("&");
                }
                if (prefix != null && !prefix.isEmpty()) {
                    url.append("prefix=").append(urlEncode(prefix)).append("&");
                }
                url.append("limit=").append(limit);
                return httpGet(http, url.toString());
            }));

        put(new ToolDef("packages",
            "List all Java packages in the indexed codebase.",
            Map.of(),
            java.util.Set.of(),
            (args, http, base) -> httpGet(http, base + "/packages")));

        put(new ToolDef("edited",
            "Report that you edited a Java file in the indexed project. GausVibe reparses "
                + "just that file and re-links cross-file references, so subsequent ask/tests "
                + "answers stay accurate. Call this after EVERY edit to a Java file in the "
                + "indexed project, one call per file. Also reports the change type so the "
                + "most valuable edit operations can be tracked.",
            Map.of(
                "path", Map.of("type", "string", "description", "Path of the edited Java file"),
                "kind", Map.of("type", "string",
                    "description", "Change type: add-method, remove-method, modify-method-body, replace-method-body, modify-signature, add-field, remove-field, add-class, remove-class, rename, move, add-import, remove-import, javadoc, annotation, other"),
                "target", Map.of("type", "string", "description", "Affected class or method fqn"),
                "note", Map.of("type", "string", "description", "One line of context")),
            java.util.Set.of("path"),
            (args, http, base) -> {
                JsonObject body = new JsonObject();
                for (String key : new String[]{"path", "kind", "target", "note"}) {
                    String v = getString(args, key);
                    if (v != null) {
                        body.addProperty(key, v);
                    }
                }
                return httpPost(http, base + "/edited", GSON.toJson(body));
            }));

        put(new ToolDef("feedback",
            "Rate a GausVibe answer so the index can be improved. Use after any notably "
                + "helpful, wrong, incomplete, or too-large answer. Cheap to call.",
            Map.of(
                "question", Map.of("type", "string", "description", "The original question"),
                "rating", Map.of("type", "string", "description", "helpful | wrong | incomplete | too-big | other"),
                "matched", Map.of("type", "string", "description", "The matched value from the answer"),
                "comment", Map.of("type", "string", "description", "What was wrong or helpful")),
            java.util.Set.of("question", "rating"),
            (args, http, base) -> {
                JsonObject body = new JsonObject();
                for (String key : new String[]{"question", "matched", "rating", "comment"}) {
                    String v = getString(args, key);
                    if (v != null) {
                        body.addProperty(key, v);
                    }
                }
                return httpPost(http, base + "/feedback", GSON.toJson(body));
            }));

        put(new ToolDef("changes",
            "List recent file edits reported through the edited tool, with change kinds "
                + "and per-file node/edge counts.",
            Map.of(),
            java.util.Set.of(),
            (args, http, base) -> httpGet(http, base + "/changes")));

        put(new ToolDef("stats",
            "Graph statistics for the indexed project: node and edge counts, staleness "
                + "(whether files changed on disk since the graph was built), and whether "
                + "a rebuild is running.",
            Map.of(),
            java.util.Set.of(),
            (args, http, base) -> httpGet(http, base + "/stats")));
    }

    private static void put(ToolDef def) {
        TOOL_DEFS.put(def.name, def);
    }
}
