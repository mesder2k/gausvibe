package dk.gausdalfind.serializer;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Serializes and deserializes the Java code graph to/from JSON format.
 * 
 * The JSON structure is:
 * {
 *   "version": "1.0",
 *   "metadata": {
 *     "createdAt": "...",
 *     "projectName": "...",
 *     "nodeCount": 123,
 *     "edgeCount": 456
 *   },
 *   "nodes": [
 *     {
 *       "id": "cls:com/example/MyClass",
 *       "type": "CLASS",
 *       "name": "MyClass",
 *       "qualifiedName": "com.example.MyClass",
 *       "file": "src/main/java/com/example/MyClass.java",
 *       "start": {"line": 10, "column": 5},
 *       "end": {"line": 50, "column": 10},
 *       "properties": {
 *         "modifiers": ["public"],
 *         "superclass": "java.lang.Object",
 *         "interfaces": [],
 *         "isInterface": false,
 *         "isEnum": false
 *       }
 *     },
 *     ...
 *   ],
 *   "edges": [
 *     {
 *       "from": "cls:com/example/MyClass",
 *       "to": "mth:com/example/MyClass#method()",
 *       "type": "HAS_METHOD",
 *       "properties": {}
 *     },
 *     ...
 *   ]
 * }
 */
public class JsonSerializer {
    
    private static final String VERSION = "1.0";
    
    private final Gson gson;
    
    /**
     * Creates a new JSON serializer with default settings.
     */
    public JsonSerializer() {
        this(createDefaultGson());
    }
    
    /**
     * Creates a new JSON serializer with a custom Gson instance.
     */
    public JsonSerializer(Gson gson) {
        this.gson = gson;
    }
    
    /**
     * Creates a Gson instance with custom settings for graph serialization.
     */
    private static Gson createDefaultGson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .enableComplexMapKeySerialization()
            .serializeNulls()
            .registerTypeAdapter(Position.class, new PositionTypeAdapter())
            .registerTypeAdapter(Path.class, new PathTypeAdapter())
            .registerTypeHierarchyAdapter(Node.class, new NodeTypeHierarchyAdapter())
            .create();
    }
    
    // ==================== Serialization Methods ====================
    
    /**
     * Serializes a graph to a JSON string.
     * 
     * @param graph the graph to serialize
     * @return the JSON string representation
     */
    public String serialize(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        GraphWrapper wrapper = new GraphWrapper(graph);
        return gson.toJson(wrapper);
    }
    
    /**
     * Serializes a graph to a file.
     * 
     * @param graph the graph to serialize
     * @param outputFile the output file path
     * @throws IOException if writing fails
     */
    public void serialize(Graph graph, Path outputFile) throws IOException {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        
        String json = serialize(graph);
        Files.writeString(outputFile, json, StandardCharsets.UTF_8);
    }
    
    /**
     * Serializes a graph to a Writer.
     * 
     * @param graph the graph to serialize
     * @param writer the writer to write to
     */
    public void serialize(Graph graph, Writer writer) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (writer == null) {
            throw new IllegalArgumentException("Writer cannot be null");
        }
        
        String json = serialize(graph);
        try {
            writer.write(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON", e);
        }
    }
    
    // ==================== Deserialization Methods ====================
    
    /**
     * Deserializes a graph from a JSON string.
     * 
     * @param json the JSON string to deserialize
     * @return the deserialized graph
     */
    public Graph deserialize(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON cannot be null or blank");
        }
        
        GraphWrapper wrapper = gson.fromJson(json, GraphWrapper.class);
        return wrapper.toGraph();
    }
    
    /**
     * Deserializes a graph from a file.
     * 
     * @param inputFile the file to read
     * @return the deserialized graph
     * @throws IOException if reading fails
     */
    public Graph deserialize(Path inputFile) throws IOException {
        if (inputFile == null) {
            throw new IllegalArgumentException("Input file cannot be null");
        }
        
        String json = Files.readString(inputFile, StandardCharsets.UTF_8);
        return deserialize(json);
    }
    
    /**
     * Deserializes a graph from a Reader.
     * 
     * @param reader the reader to read from
     * @return the deserialized graph
     */
    public Graph deserialize(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Reader cannot be null");
        }
        
        GraphWrapper wrapper = gson.fromJson(reader, GraphWrapper.class);
        return wrapper.toGraph();
    }
    
    // ==================== Round-trip Methods ====================
    
    /**
     * Tests that a graph can be serialized and deserialized correctly.
     * 
     * @param graph the graph to test
     * @return true if the round-trip succeeds and graphs are equal
     */
    public boolean testRoundTrip(Graph graph) {
        if (graph == null) {
            return false;
        }
        
        try {
            String json = serialize(graph);
            Graph deserialized = deserialize(json);
            return graphsAreEqual(graph, deserialized);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if two graphs are equal (same nodes and edges).
     */
    private boolean graphsAreEqual(Graph g1, Graph g2) {
        if (g1 == null || g2 == null) {
            return g1 == g2;
        }
        
        // Check node count
        if (g1.getNodeCount() != g2.getNodeCount()) {
            return false;
        }
        
        // Check edge count
        if (g1.getEdgeCount() != g2.getEdgeCount()) {
            return false;
        }
        
        // Check all nodes exist in both graphs
        for (Node node : g1.getAllNodes()) {
            if (!g2.getNode(node.getId()).isPresent()) {
                return false;
            }
        }
        
        // Check all edges exist in both graphs
        for (Edge edge : g1.getAllEdges()) {
            boolean found = false;
            for (Edge e2 : g2.getAllEdges()) {
                if (edgesAreEqual(edge, e2)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if two edges are equal.
     */
    private boolean edgesAreEqual(Edge e1, Edge e2) {
        if (e1 == null || e2 == null) {
            return e1 == e2;
        }
        return e1.getFromId().equals(e2.getFromId()) &&
               e1.getToId().equals(e2.getToId()) &&
               e1.getType().equals(e2.getType());
    }
    
    // ==================== Wrapper Classes ====================
    
    /**
     * Wrapper class for serializing the graph with metadata.
     */
    private static class GraphWrapper {
        private final String version;
        private final Metadata metadata;
        private final List<NodeWrapper> nodes;
        private final List<EdgeWrapper> edges;
        
        public GraphWrapper(Graph graph) {
            this.version = VERSION;
            this.metadata = new Metadata(graph);
            this.nodes = new ArrayList<>();
            this.edges = new ArrayList<>();
            
            // Convert nodes
            for (Node node : graph.getAllNodes()) {
                nodes.add(NodeWrapper.fromNode(node));
            }
            
            // Convert edges
            for (Edge edge : graph.getAllEdges()) {
                edges.add(EdgeWrapper.fromEdge(edge));
            }
        }
        
        public Graph toGraph() {
            Graph graph = new Graph();
            Map<String, Node> nodeMap = new HashMap<>();
            
            // Recreate nodes
            for (NodeWrapper nw : nodes) {
                Node node = nw.toNode();
                graph.addNode(node);
                nodeMap.put(node.getId(), node);
            }
            
            // Recreate edges
            for (EdgeWrapper ew : edges) {
                Edge edge = ew.toEdge();
                // Verify both nodes exist
                if (nodeMap.containsKey(edge.getFromId()) && nodeMap.containsKey(edge.getToId())) {
                    graph.addEdge(edge);
                }
            }
            
            return graph;
        }
    }
    
    /**
     * Metadata about the graph.
     */
    private static class Metadata {
        private final String createdAt;
        private final int nodeCount;
        private final int edgeCount;
        
        public Metadata(Graph graph) {
            this.createdAt = new Date().toString();
            this.nodeCount = graph.getNodeCount();
            this.edgeCount = graph.getEdgeCount();
        }
        
        // For Gson deserialization
        @SuppressWarnings("unused")
        private Metadata() {
            this.createdAt = null;
            this.nodeCount = 0;
            this.edgeCount = 0;
        }
        
        public String getCreatedAt() {
            return createdAt;
        }
        
        public int getNodeCount() {
            return nodeCount;
        }
        
        public int getEdgeCount() {
            return edgeCount;
        }
    }
    
    /**
     * Wrapper for Node serialization.
     */
    private static class NodeWrapper {
        private final String id;
        private final String type;
        private final String name;
        private final String qualifiedName;
        private final String file;
        private final Position start;
        private final Position end;
        private final Map<String, Object> properties;
        
        public NodeWrapper(String id, String type, String name, String qualifiedName,
                          String file, Position start, Position end,
                          Map<String, Object> properties) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.qualifiedName = qualifiedName;
            this.file = file;
            this.start = start;
            this.end = end;
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        }
        
        public static NodeWrapper fromNode(Node node) {
            String name = null;
            String qualifiedName = null;
            
            if (node instanceof DeclarationNode) {
                DeclarationNode decl = (DeclarationNode) node;
                name = decl.getName();
                qualifiedName = decl.getQualifiedName();
            }
            
            return new NodeWrapper(
                node.getId(),
                node.getType(),
                name,
                qualifiedName,
                node.getFile() != null ? node.getFile().toString() : null,
                node.getStartPosition(),
                node.getEndPosition(),
                node.getProperties()
            );
        }
        
        public Node toNode() {
            Path filePath = file != null ? Path.of(file) : null;
            
            // Create appropriate node type based on type field
            switch (type) {
                case Node.TYPE_PACKAGE:
                    return new PackageNode(name, filePath, start, end);
                case Node.TYPE_CLASS:
                    return createClassNode();
                case Node.TYPE_METHOD:
                    return createMethodNode();
                case Node.TYPE_FIELD:
                    return createFieldNode();
                case Node.TYPE_PARAMETER:
                    return createParameterNode();
                case Node.TYPE_VARIABLE:
                    return createVariableNode();
                default:
                    // For non-declaration nodes, create a simple node
                    return createSimpleNode();
            }
        }
        
        private ClassNode createClassNode() {
            Set<String> modifiers = getPropertyAsSet("modifiers");
            String superclass = getProperty("superclass");
            List<String> interfaces = getPropertyAsList("interfaces");
            boolean isInterface = getPropertyAsBoolean("isInterface", false);
            boolean isEnum = getPropertyAsBoolean("isEnum", false);
            
            return new ClassNode(
                id, name, qualifiedName, modifiers, superclass, interfaces,
                isInterface, isEnum, Path.of(file), start, end
            );
        }
        
        private MethodNode createMethodNode() {
            String signature = getProperty("signature");
            String returnType = getProperty("returnType");
            Set<String> modifiers = getPropertyAsSet("modifiers");
            boolean isConstructor = getPropertyAsBoolean("isConstructor", false);
            boolean isStatic = getPropertyAsBoolean("isStatic", false);
            List<String> thrownExceptions = getPropertyAsList("thrownExceptions");
            
            return new MethodNode(
                id, name, signature, qualifiedName, returnType, modifiers,
                isConstructor, isStatic, thrownExceptions, Path.of(file), start, end
            );
        }
        
        private FieldNode createFieldNode() {
            String type = getProperty("type");
            Set<String> modifiers = getPropertyAsSet("modifiers");
            boolean isStatic = getPropertyAsBoolean("isStatic", false);
            boolean isFinal = getPropertyAsBoolean("isFinal", false);
            
            return new FieldNode(
                id, name, qualifiedName, type, modifiers, isStatic, isFinal,
                Path.of(file), start, end
            );
        }
        
        private ParameterNode createParameterNode() {
            String type = getProperty("type");
            int position = getPropertyAsInt("position", 0);
            String belongingMethod = getProperty("belongingMethod");
            
            return new ParameterNode(
                id, name, qualifiedName, type, position, belongingMethod,
                Path.of(file), start, end
            );
        }
        
        private VariableNode createVariableNode() {
            String type = getProperty("type");
            String scopeMethod = getProperty("scopeMethod");
            boolean isFinal = getPropertyAsBoolean("isFinal", false);
            
            return new VariableNode(
                id, name, qualifiedName, type, scopeMethod, isFinal,
                Path.of(file), start, end
            );
        }
        
        private Node createSimpleNode() {
            // For non-declaration nodes, return a simple implementation
            return new SimpleNode(id, type, Path.of(file), start, end, properties);
        }
        
        private String getProperty(String key) {
            Object value = properties.get(key);
            return value != null ? value.toString() : null;
        }
        
        private int getPropertyAsInt(String key, int defaultValue) {
            Object value = properties.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return defaultValue;
        }
        
        private boolean getPropertyAsBoolean(String key, boolean defaultValue) {
            Object value = properties.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return defaultValue;
        }
        
        private Set<String> getPropertyAsSet(String key) {
            Object value = properties.get(key);
            if (value instanceof Collection) {
                Set<String> set = new HashSet<>();
                for (Object item : (Collection<?>) value) {
                    set.add(item.toString());
                }
                return set;
            }
            return Set.of();
        }
        
        private List<String> getPropertyAsList(String key) {
            Object value = properties.get(key);
            if (value instanceof Collection) {
                List<String> list = new ArrayList<>();
                for (Object item : (Collection<?>) value) {
                    list.add(item.toString());
                }
                return list;
            }
            return List.of();
        }
    }
    
    /**
     * Wrapper for Edge serialization.
     */
    private static class EdgeWrapper {
        private final String from;
        private final String to;
        private final String type;
        private final Map<String, Object> properties;
        
        public EdgeWrapper(String from, String to, String type, Map<String, Object> properties) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        }
        
        public static EdgeWrapper fromEdge(Edge edge) {
            return new EdgeWrapper(
                edge.getFromId(),
                edge.getToId(),
                edge.getType(),
                edge.getProperties()
            );
        }
        
        public Edge toEdge() {
            return new Edge(from, to, type, properties);
        }
    }
    
    // ==================== Type Adapters ====================
    
    /**
     * Type adapter for Position record.
     */
    private static class PositionTypeAdapter extends TypeAdapter<Position> {
        @Override
        public void write(JsonWriter out, Position value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.beginObject();
                out.name("line").value(value.line());
                out.name("column").value(value.column());
                out.endObject();
            }
        }
        
        @Override
        public Position read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            
            int line = 0;
            int column = 0;
            
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "line":
                        line = in.nextInt();
                        break;
                    case "column":
                        column = in.nextInt();
                        break;
                    default:
                        in.skipValue();
                }
            }
            in.endObject();
            
            return new Position(line, column);
        }
    }
    
    /**
     * Type adapter for Path.
     */
    private static class PathTypeAdapter extends TypeAdapter<Path> {
        @Override
        public void write(JsonWriter out, Path value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }
        
        @Override
        public Path read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Path.of(in.nextString());
        }
    }
    
    /**
     * Type hierarchy adapter for Node.
     */
    private static class NodeTypeHierarchyAdapter implements com.google.gson.JsonSerializer<Node>, com.google.gson.JsonDeserializer<Node> {
        @Override
        public JsonElement serialize(Node src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("type", src.getType());
            obj.addProperty("file", src.getFile() != null ? src.getFile().toString() : null);
            
            if (src.getStartPosition() != null) {
                obj.add("start", context.serialize(src.getStartPosition()));
            }
            if (src.getEndPosition() != null) {
                obj.add("end", context.serialize(src.getEndPosition()));
            }
            
            if (src instanceof DeclarationNode) {
                DeclarationNode decl = (DeclarationNode) src;
                obj.addProperty("name", decl.getName());
                obj.addProperty("qualifiedName", decl.getQualifiedName());
            }
            
            obj.add("properties", context.serialize(src.getProperties()));
            
            return obj;
        }
        
        @Override
        public Node deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get("type").getAsString();
            
            // This is a simplified deserialization - full deserialization is handled by NodeWrapper
            throw new UnsupportedOperationException("Use GraphWrapper for full deserialization");
        }
    }
    
    // ==================== Simple Node Implementation ====================
    
    /**
     * Simple node implementation for non-declaration nodes during deserialization.
     */
    private static class SimpleNode implements Node {
        private final String id;
        private final String type;
        private final Path file;
        private final Position start;
        private final Position end;
        private final Map<String, Object> properties;
        
        public SimpleNode(String id, String type, Path file, Position start, Position end, Map<String, Object> properties) {
            this.id = id;
            this.type = type;
            this.file = file;
            this.start = start;
            this.end = end;
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public Path getFile() {
            return file;
        }
        
        @Override
        public Position getStartPosition() {
            return start;
        }
        
        @Override
        public Position getEndPosition() {
            return end;
        }
        
        @Override
        public Map<String, Object> getProperties() {
            return Collections.unmodifiableMap(properties);
        }
        
        @Override
        public String toString() {
            return "SimpleNode{id='" + id + "', type='" + type + "'}";
        }
    }
}
