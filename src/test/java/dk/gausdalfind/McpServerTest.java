package dk.gausdalfind;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.gausdalfind.server.GausVibeMcpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the MCP stdio protocol: handshake, tool listing, proxied tool
 * calls against a fake GausVibe HTTP endpoint, and the daemon-unreachable
 * error path.
 */
class McpServerTest {

    private static com.sun.net.httpserver.HttpServer fakeDaemon;
    private static String fakeUrl;

    @BeforeAll
    static void startFakeDaemon() throws IOException {
        fakeDaemon = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        fakeDaemon.createContext("/ask", exchange -> {
            String q = exchange.getRequestURI().getQuery();
            String question = java.net.URLDecoder.decode(
                q == null ? "" : q.substring(2), StandardCharsets.UTF_8);
            byte[] body = ("{\"question\":\"" + question + "\",\"matched\":\"class-location\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeDaemon.createContext("/classes", exchange -> {
            String echo = exchange.getRequestURI().getPath() + "?"
                + exchange.getRequestURI().getQuery();
            byte[] body = ("{\"echo\":\"" + echo + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeDaemon.createContext("/packages", exchange -> {
            byte[] body = "{\"count\":1,\"packages\":[\"com.example\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeDaemon.createContext("/search", exchange -> {
            String q = exchange.getRequestURI().getQuery();
            String name = java.net.URLDecoder.decode(
                q == null ? "" : q.substring(2), StandardCharsets.UTF_8);
            byte[] body = ("{\"type\":\"class\",\"query\":\"" + name + "\",\"count\":1,"
                + "\"results\":[\"com.example." + name + "\"]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeDaemon.start();
        fakeUrl = "http://localhost:" + fakeDaemon.getAddress().getPort();
    }

    @AfterAll
    static void stopFakeDaemon() {
        if (fakeDaemon != null) {
            fakeDaemon.stop(0);
        }
    }

    @Test
    void initializeHandshake() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"test\"}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        assertEquals("2.0", o.get("jsonrpc").getAsString());
        assertEquals(1, o.get("id").getAsInt());
        assertEquals("2024-11-05", o.getAsJsonObject("result").get("protocolVersion").getAsString());
        assertEquals("gausvibe",
            o.getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString());
        assertTrue(o.getAsJsonObject("result").has("capabilities"));
    }

    @Test
    void notificationsProduceNoResponse() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        assertNull(server.handleLine(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    void toolsListIncludesAskWithSchema() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        var tools = o.getAsJsonObject("result").getAsJsonArray("tools");
        boolean hasAsk = false;
        for (var t : tools) {
            if (t.getAsJsonObject().get("name").getAsString().equals("ask")) {
                hasAsk = true;
                var schema = t.getAsJsonObject().get("inputSchema").getAsJsonObject();
                assertTrue(schema.get("properties").getAsJsonObject().has("question"));
            }
        }
        assertTrue(hasAsk, "ask tool advertised");
        assertTrue(tools.size() >= 12, "all tools advertised: " + tools.size());
    }

    @Test
    void deterministicToolsAdvertised() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        var tools = o.getAsJsonObject("result").getAsJsonArray("tools");
        for (String name : new String[]{"search", "class_detail", "class_members", "classes", "packages"}) {
            boolean found = false;
            for (var t : tools) {
                if (t.getAsJsonObject().get("name").getAsString().equals(name)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, name + " tool advertised");
        }
    }

    @Test
    void askDescriptionDiscouragesFreeForm() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        var tools = o.getAsJsonObject("result").getAsJsonArray("tools");
        String askDescription = null;
        for (var t : tools) {
            if (t.getAsJsonObject().get("name").getAsString().equals("ask")) {
                askDescription = t.getAsJsonObject().get("description").getAsString();
            }
        }
        assertNotNull(askDescription);
        assertFalse(askDescription.contains("plain-language"), "no plain-language framing");
        assertTrue(askDescription.contains("NOT free-form"));
        assertTrue(askDescription.contains("search"), "redirects to deterministic tools");
    }

    @Test
    void classesRequiresPackageOrPrefix() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"classes\",\"arguments\":{}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        assertTrue(o.getAsJsonObject("result").get("isError").getAsBoolean(),
            "unfiltered listing rejected");
        String text = o.getAsJsonObject("result").getAsJsonArray("content")
            .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(text.contains("package") && text.contains("prefix"),
            "error names the required filters: " + text);
    }

    @Test
    void classesProxyPassesFilters() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"classes\",\"arguments\":{\"package\":\"dk.gausdalfind.server\",\"limit\":10}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        assertFalse(o.getAsJsonObject("result").has("isError"));
        String text = o.getAsJsonObject("result").getAsJsonArray("content")
            .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(text.contains("package=dk.gausdalfind.server"),
            "package filter forwarded: " + text);
        assertTrue(text.contains("limit=10"), "limit forwarded: " + text);
    }

    @Test
    void classMembersDefaultsToMethodsAndValidatesRelation() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);

        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"class_members\",\"arguments\":{\"class_fqn\":\"com.example.Foo\"}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        String text = o.getAsJsonObject("result").getAsJsonArray("content")
            .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(text.contains("com.example.Foo/methods"), "defaults to methods: " + text);

        resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"class_members\",\"arguments\":"
            + "{\"class_fqn\":\"com.example.Foo\",\"relation\":\"bogus\"}}}");
        o = JsonParser.parseString(resp).getAsJsonObject();
        assertTrue(o.getAsJsonObject("result").get("isError").getAsBoolean(),
            "invalid relation rejected");
    }

    @Test
    void searchProxiesName() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"search\",\"arguments\":{\"name\":\"MetadataCreateIndexService\"}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        String text = o.getAsJsonObject("result").getAsJsonArray("content")
            .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(text.contains("MetadataCreateIndexService"), "name round-trips: " + text);
    }

    @Test
    void askProxiesQuestion() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"ask\",\"arguments\":{\"question\":\"where is Calculator defined\"}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        var content = o.getAsJsonObject("result").getAsJsonArray("content");
        String text = content.get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(text.contains("where is Calculator defined"),
            "question round-trips through the daemon: " + text);
        assertTrue(text.contains("class-location"));
        assertFalse(o.getAsJsonObject("result").has("isError"));
    }

    @Test
    void unreachableDaemonIsToolErrorNotProtocolError() {
        GausVibeMcpServer server = new GausVibeMcpServer("http://localhost:1");
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"ask\",\"arguments\":{\"question\":\"anything\"}}}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        // protocol-level success, tool-level error
        assertFalse(o.has("error"), "no protocol error: " + resp);
        assertTrue(o.getAsJsonObject("result").get("isError").getAsBoolean());
        String text = o.getAsJsonObject("result").getAsJsonArray("content")
            .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(text.contains("not reachable"));
    }

    @Test
    void unknownMethodIsError() {
        GausVibeMcpServer server = new GausVibeMcpServer(fakeUrl);
        String resp = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"bogus/method\"}");
        JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
        assertEquals(-32601, o.getAsJsonObject("error").get("code").getAsInt());
    }
}
