package com.javacodegraph.parser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.javacodegraph.model.*;

import java.util.*;

/**
 * Factory for creating edges between nodes in the graph.
 * 
 * This factory creates edges based on relationships discovered during
 * AST traversal.
 */
public class EdgeFactory {
    
    private final Graph graph;
    private final VisitorContext context;
    
    // Cache for edges to avoid duplicates
    private final Set<String> edgeCache = new HashSet<>();
    
    /**
     * Creates a new edge factory.
     * 
     * @param graph the graph to add edges to
     * @param context the visitor context
     */
    public EdgeFactory(Graph graph, VisitorContext context) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.graph = graph;
        this.context = context;
    }
    
    /**
     * Creates an edge between two nodes.
     * 
     * @param fromId the source node ID
     * @param toId the target node ID
     * @param type the edge type
     * @return the created edge, or null if invalid
     */
    public Edge createEdge(String fromId, String toId, String type) {
        return createEdge(fromId, toId, type, Map.of());
    }
    
    /**
     * Creates an edge between two nodes with properties.
     * 
     * @param fromId the source node ID
     * @param toId the target node ID
     * @param type the edge type
     * @param properties edge properties
     * @return the created edge, or null if invalid
     */
    public Edge createEdge(String fromId, String toId, String type, Map<String, Object> properties) {
        if (fromId == null || toId == null || type == null) {
            return null;
        }
        
        // Check if edge already exists
        String edgeKey = fromId + "|" + toId + "|" + type;
        if (edgeCache.contains(edgeKey)) {
            return null; // Edge already exists
        }
        
        // Verify nodes exist
        if (!graph.getNode(fromId).isPresent() || !graph.getNode(toId).isPresent()) {
            return null;
        }
        
        Edge edge = new Edge(fromId, toId, type, properties);
        
        if (graph.addEdge(edge)) {
            edgeCache.add(edgeKey);
        }
        
        return edge;
    }
    
    /**
     * Creates a CONTAINS edge (block contains statement).
     */
    public Edge createContains(String blockId, String statementId, int index) {
        return createEdge(blockId, statementId, EdgeTypes.CONTAINS, 
                         Map.of("index", index));
    }
    
    /**
     * Creates a BODY edge (method/control structure has body block).
     */
    public Edge createBody(String methodId, String blockId) {
        return createEdge(methodId, blockId, EdgeTypes.BODY);
    }
    
    /**
     * Creates a CONDITION edge (if/while/for/switch has condition).
     */
    public Edge createCondition(String controlId, String expressionId) {
        return createEdge(controlId, expressionId, EdgeTypes.CONDITION);
    }
    
    /**
     * Creates a THEN_BRANCH edge (if has then branch).
     */
    public Edge createThenBranch(String ifId, String thenId) {
        return createEdge(ifId, thenId, EdgeTypes.THEN_BRANCH);
    }
    
    /**
     * Creates an ELSE_BRANCH edge (if has else branch).
     */
    public Edge createElseBranch(String ifId, String elseId) {
        return createEdge(ifId, elseId, EdgeTypes.ELSE_BRANCH);
    }
    
    /**
     * Creates HAS_METHOD edges (class has method).
     */
    public Edge createHasMethod(String classId, String methodId) {
        return createEdge(classId, methodId, EdgeTypes.HAS_METHOD);
    }
    
    /**
     * Creates HAS_FIELD edges (class has field).
     */
    public Edge createHasField(String classId, String fieldId) {
        return createEdge(classId, fieldId, EdgeTypes.HAS_FIELD);
    }
    
    /**
     * Creates HAS_PARAMETER edges (method has parameter).
     */
    public Edge createHasParameter(String methodId, String paramId, int index) {
        return createEdge(methodId, paramId, EdgeTypes.HAS_PARAMETER,
                         Map.of("index", index));
    }
    
    /**
     * Creates RETURNS edge (method returns type).
     */
    public Edge createReturns(String methodId, String typeId) {
        return createEdge(methodId, typeId, EdgeTypes.RETURNS);
    }
    
    /**
     * Creates THROWS edge (method throws exception).
     */
    public Edge createThrows(String methodId, String exceptionId, int index) {
        return createEdge(methodId, exceptionId, EdgeTypes.THROWS,
                         Map.of("index", index));
    }
    
    /**
     * Creates INHERITS edge (class inherits from superclass).
     */
    public Edge createInherits(String subclassId, String superclassId) {
        return createEdge(subclassId, superclassId, EdgeTypes.INHERITS);
    }
    
    /**
     * Creates IMPLEMENTS edge (class implements interface).
     */
    public Edge createImplements(String classId, String interfaceId) {
        return createEdge(classId, interfaceId, EdgeTypes.IMPLEMENTS);
    }
    
    /**
     * Creates EXTENDS edge (interface extends interface).
     */
    public Edge createExtends(String childId, String parentId) {
        return createEdge(childId, parentId, EdgeTypes.EXTENDS);
    }
    
    // ==================== Expression Edges ====================
    
    /**
     * Creates RECEIVER edge (method call/field access has receiver).
     */
    public Edge createReceiver(String callId, String receiverId) {
        return createEdge(callId, receiverId, EdgeTypes.RECEIVER);
    }
    
    /**
     * Creates METHOD_NAME edge (method call has method name string).
     */
    public Edge createMethodName(String callId, String name) {
        // For string literals, we'd need a string node
        return null; // TODO: Implement when StringNode exists
    }
    
    /**
     * Creates ARGUMENT edge (method call/new class has argument).
     */
    public Edge createArgument(String callId, String argId, int index) {
        return createEdge(callId, argId, EdgeTypes.ARGUMENT,
                         Map.of("index", index));
    }
    
    /**
     * Creates LEFT_OPERAND edge (binary operation has left operand).
     */
    public Edge createLeftOperand(String opId, String leftId) {
        return createEdge(opId, leftId, EdgeTypes.LEFT_OPERAND);
    }
    
    /**
     * Creates RIGHT_OPERAND edge (binary operation has right operand).
     */
    public Edge createRightOperand(String opId, String rightId) {
        return createEdge(opId, rightId, EdgeTypes.RIGHT_OPERAND);
    }
    
    /**
     * Creates OPERATOR edge (binary/unary operation has operator).
     */
    public Edge createOperator(String opId, String operator) {
        return createEdge(opId, operator, EdgeTypes.OPERATOR);
    }
    
    // ==================== Semantic Edges ====================
    
    /**
     * Creates CALLS edge (method call calls method).
     */
    public Edge createCalls(String callId, String methodId) {
        return createEdge(callId, methodId, EdgeTypes.CALLS);
    }
    
    /**
     * Creates CALLS_CONSTRUCTOR edge (new class calls constructor).
     */
    public Edge createCallsConstructor(String newId, String constructorId) {
        return createEdge(newId, constructorId, EdgeTypes.CALLS_CONSTRUCTOR);
    }
    
    /**
     * Creates ACCESSES edge (field access accesses field).
     */
    public Edge createAccesses(String accessId, String fieldId) {
        return createEdge(accessId, fieldId, EdgeTypes.ACCESSES);
    }
    
    /**
     * Creates CREATES edge (new class creates instance of class).
     */
    public Edge createCreates(String newId, String classId) {
        return createEdge(newId, classId, EdgeTypes.CREATES);
    }
    
    /**
     * Creates REFERENCES edge (expression references node).
     */
    public Edge createReferences(String fromId, String toId) {
        return createEdge(fromId, toId, EdgeTypes.REFERENCES);
    }
    
    /**
     * Creates REFERENCES_TYPE edge (node references type).
     */
    public Edge createReferencesType(String fromId, String typeId) {
        return createEdge(fromId, typeId, EdgeTypes.REFERENCES_TYPE);
    }
    
    /**
     * Creates OVERRIDES edge (method overrides method).
     */
    public Edge createOverrides(String overridingId, String overriddenId) {
        return createEdge(overridingId, overriddenId, EdgeTypes.OVERRIDES);
    }
    
    /**
     * Creates IMPORTS edge (file imports type).
     */
    public Edge createImports(String fileId, String typeId) {
        return createEdge(fileId, typeId, EdgeTypes.IMPORTS);
    }
    
    /**
     * Creates ANNOTATED_WITH edge (node has annotation).
     */
    public Edge createAnnotatedWith(String nodeId, String annotationId) {
        return createEdge(nodeId, annotationId, EdgeTypes.ANNOTATED_WITH);
    }
    
    /**
     * Clears the edge cache.
     */
    public void clearCache() {
        edgeCache.clear();
    }
    
    /**
     * Returns the number of edges created.
     */
    public int getEdgeCount() {
        return edgeCache.size();
    }
}
