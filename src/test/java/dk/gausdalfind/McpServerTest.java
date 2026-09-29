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
        assertTrue(tools.size() >= 7, "all tools advertised: " + tools.size());
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
