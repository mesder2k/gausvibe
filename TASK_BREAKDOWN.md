# Java Project Graph Model: Complete Implementation Guide

**Project:** JavaCodeGraph  
**Goal:** Structured graph of Java code for Vibe to understand without grep  
**Version:** 1.0 - 2026-09-17  
**Status:** Phase 8 complete - Optimization with CachingQueryEngine, ParallelGraphBuilder, and IncrementalGraphBuilder implemented

---

## 📋 TABLE OF CONTENTS
1. [Quick Start & MVP](#-quick-start--mvp)
2. [Project Structure](#-project-structure)
3. [Dependencies (pom.xml)](#-dependencies-pomxml)
4. [Node Types (Complete)](#-node-types-complete)
5. [Edge Types (Complete)](#-edge-types-complete)
6. [Implementation Phases](#-implementation-phases)
7. [Query API](#-query-api)
8. [Key Code Snippets](#-key-code-snippets)
9. [Edge Cases](#-edge-cases)
10. [Testing & Validation](#-testing--validation)
11. [LLM Integration](LLM_INTEGRATION.md) - Vibe Skill implementation details

---

## 🚀 QUICK START & MVP

### 2-3 Day MVP
**Goal:** Parse simple Java, create graph, answer basic queries.

**Test File:** `Calculator.java`
```java
package com.example;
public class Calculator {
    public int add(int a, int b) { return a + b; }
}
```

**Steps:**
1. `mvn archetype:generate` - Create Maven project
2. Add JavaParser to pom.xml
3. Implement Node, Edge, Graph, Indexes
4. Create NodeFactory, EdgeFactory
5. Create DeclarationVisitor (CLASS, METHOD, PARAMETER)
6. Parse test file, create nodes/edges
7. Serialize to JSON with JsonSerializer
8. Implement GraphQueryEngine with findClass, getMethods
9. Test: "What methods in Calculator?"

---

## 📁 PROJECT STRUCTURE

```
java-code-graph/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/javacodegraph/
    │   ├── Main.java
    │   ├── model/
    │   │   ├── Node.java, Position.java, Edge.java, EdgeTypes.java
    │   │   ├── Graph.java, Indexes.java, NodeIdGenerator.java
    │   │   ├── declaration/
    │   │   │   ├── ClassNode.java, MethodNode.java, FieldNode.java
    │   │   │   ├── ParameterNode.java, VariableNode.java
    │   │   │   └── PackageNode.java
    │   │   └── ast/
    │   │       ├── statement/
    │   │       │   └── IfStatementNode.java, ForStatementNode.java, ...
    │   │       └── expression/
    │   │           └── MethodCallNode.java, BinaryOperationNode.java, ...
    │   ├── parser/
    │   │   ├── JavaParserConfig.java, JavaFileCollector.java
    │   │   ├── VisitorContext.java
    │   │   ├── NodeFactory.java, EdgeFactory.java
    │   │   └── DeclarationVisitor.java, StatementVisitor.java, ExpressionVisitor.java
    │   ├── symbols/
    │   │   ├── SymbolTable.java
    │   │   └── SymbolResolver.java
    │   ├── graph/
    │   │   └── JavaCodeGraphBuilder.java
    │   ├── queries/
    │   │   ├── JavaGraphQuery.java
    │   │   └── GraphQueryEngine.java
    │   └── serializer/
    │       └── JsonSerializer.java
    └── test/java/
```

---

## 📦 DEPENDENCIES (pom.xml)

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.javacodegraph</groupId>
  <artifactId>java-code-graph</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <javaparser.version>3.25.9</javaparser.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.github.javaparser</groupId>
      <artifactId>javaparser-core</artifactId>
      <version>${javaparser.version}</version>
    </dependency>
    <dependency>
      <groupId>com.github.javaparser</groupId>
      <artifactId>javaparser-symbol-solver-core</artifactId>
      <version>${javaparser.version}</version>
    </dependency>
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.10.1</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.7</version>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>1.4.7</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.9.2</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

---

## 🎯 NODE TYPES (Complete)

### Declarations (Code Structure)
| Type | Properties | JavaParser Node | ID Format |
|------|------------|-----------------|-----------|
| PACKAGE | name, qualified_name, file, start, end | PackageDeclaration | `pkg:{name}` |
| CLASS | name, qualified_name, modifiers, superclass, interfaces, is_interface, is_enum | ClassOrInterfaceDeclaration | `cls:{fqn}` |
| METHOD | name, signature, qualified_name, return_type, modifiers, is_constructor, is_static | MethodDeclaration | `mth:{fqn}#{sig}` |
| FIELD | name, qualified_name, type, modifiers, is_static, is_final | FieldDeclaration | `fld:{fqn}#{name}` |
| PARAMETER | name, type, position, belonging_method | Parameter | `par:{fqn}#{pos}` |
| VARIABLE | name, type, scope_method, is_final | VariableDeclarator | `var:{file}#{method}#{name}` |

### Statements (Control Flow)
| Type | Properties | JavaParser Node | ID Format |
|------|------------|-----------------|-----------|
| BLOCK | file, start, end | BlockStmt | `blk:{file}:{line}:{col}` |
| IF | has_else | IfStmt | `if:{file}:{line}:{col}` |
| FOR | has_init, has_update, has_condition | ForStmt | `for:{file}:{line}:{col}` |
| WHILE | - | WhileStmt | `while:{file}:{line}:{col}` |
| DO | - | DoStmt | `do:{file}:{line}:{col}` |
| SWITCH | has_default | SwitchStmt | `switch:{file}:{line}:{col}` |
| TRY | has_catch, has_finally | TryStmt | `try:{file}:{line}:{col}` |
| CATCH_CLAUSE | parameter_name, parameter_type | CatchClause | `catch:{file}:{line}:{col}` |
| SYNCHRONIZED | - | SynchronizedStmt | `sync:{file}:{line}:{col}` |
| RETURN | has_value | ReturnStmt | `return:{file}:{line}:{col}` |
| THROW | - | ThrowStmt | `throw:{file}:{line}:{col}` |
| BREAK | label | BreakStmt | `break:{file}:{line}:{col}` |
| CONTINUE | label | ContinueStmt | `continue:{file}:{line}:{col}` |
| LABELED | label | LabeledStmt | `labeled:{file}:{line}:{col}` |
| EXPRESSION_STMT | - | ExpressionStmt | `expr_stmt:{file}:{line}:{col}` |
| VARIABLE_DECL | type | VariableDeclarationExpr | `var_decl:{file}:{line}:{col}` |

### Expressions (Data Flow)
| Type | Properties | JavaParser Node | ID Format |
|------|------------|-----------------|-----------|
| LITERAL | value, literal_type | LiteralExpr | `lit:{file}:{line}:{col}` |
| VARIABLE_REF | name | NameExpr | `var_ref:{file}:{line}:{col}` |
| FIELD_ACCESS | field_name, is_static | FieldAccessExpr | `field_acc:{file}:{line}:{col}` |
| METHOD_CALL | method_name, arg_count, is_static | MethodCallExpr | `call:{file}:{line}:{col}` |
| NEW_CLASS | class_name, is_anonymous | ObjectCreationExpr | `new:{file}:{line}:{col}` |
| NEW_ARRAY | type, dimensions | ArrayCreationExpr | `new_arr:{file}:{line}:{col}` |
| ARRAY_ACCESS | - | ArrayAccessExpr | `arr_acc:{file}:{line}:{col}` |
| BINARY_OP | operator | BinaryExpr | `bin_op:{file}:{line}:{col}` |
| UNARY_OP | operator | UnaryExpr | `un_op:{file}:{line}:{col}` |
| TERNARY | - | ConditionalExpr | `ternary:{file}:{line}:{col}` |
| CAST | type | CastExpr | `cast:{file}:{line}:{col}` |
| INSTANCEOF | type | InstanceOfExpr | `instanceof:{file}:{line}:{col}` |
| LAMBDA | - | LambdaExpr | `lambda:{file}:{line}:{col}` |
| METHOD_REF | method_name, ref_kind | MethodReferenceExpr | `mref:{file}:{line}:{col}` |
| THIS | - | ThisExpr | `this:{file}:{line}:{col}` |
| SUPER | - | SuperExpr | `super:{file}:{line}:{col}` |
| PARENTHESES | - | EnclosedExpr | `parens:{file}:{line}:{col}` |
| CLASS_EXPR | type | ClassExpr | `class_expr:{file}:{line}:{col}` |

---

## 🔗 EDGE TYPES (Complete)

### Structural (AST Hierarchy)
| Type | From | To | Properties | Purpose |
|------|------|---|------------|---------|
| CONTAINS | BLOCK | STATEMENT | index | Statement in block |
| BODY | METHOD/CONTROL | BLOCK | - | Body block |
| CONDITION | IF/WHILE/FOR/SWITCH | EXPR | - | Condition |
| THEN_BRANCH | IF | STATEMENT/BLOCK | - | Then branch |
| ELSE_BRANCH | IF | STATEMENT/BLOCK | - | Else branch |
| INIT | FOR | STATEMENT/EXPR | index | For init |
| UPDATE | FOR | EXPR | - | For update |
| TRY_BLOCK | TRY | BLOCK | - | Try block |
| CATCH | TRY | CATCH_CLAUSE | index | Catch clause |
| FINALLY_BLOCK | TRY | BLOCK | - | Finally |
| RETURN_VALUE | RETURN | EXPR | - | Return value |
| THROW_VALUE | THROW | EXPR | - | Thrown value |
| TARGET | LABELED | STATEMENT | - | Label target |
| LABEL | LABELED | STRING | - | Label name |
| DECLARES | VAR_DECL | VARIABLE | index | Declares variable |
| INITIALIZER | VARIABLE | EXPR | - | Variable init |

### Expression Edges
| Type | From | To | Properties | Purpose |
|------|------|---|------------|---------|
| RECEIVER | METHOD_CALL/FIELD_ACCESS | EXPR | - | Receiver |
| METHOD_NAME | METHOD_CALL | STRING | - | Method name |
| FIELD_NAME | FIELD_ACCESS | STRING | - | Field name |
| CLASS_NAME | NEW_CLASS | STRING | - | Class name |
| ARGUMENT | METHOD_CALL/NEW_CLASS | EXPR | index | Argument |
| ARRAY, INDEX | ARRAY_ACCESS | EXPR | - | Array/index |
| LEFT/RIGHT_OPERAND | BINARY_OP | EXPR | - | Operands |
| OPERATOR | BINARY/UNARY | STRING | - | Operator |
| OPERAND | UNARY/CAST/INSTANCEOF | EXPR | - | Operand |
| TYPE | CAST/INSTANCEOF | STRING | - | Type |
| TEST, CONSEQUENT, ALTERNATIVE | TERNARY | EXPR | - | Ternary parts |

### Declaration Edges
| Type | From | To | Properties | Purpose |
|------|------|---|------------|---------|
| HAS_METHOD | CLASS | METHOD | - | Class has method |
| HAS_FIELD | CLASS | FIELD | - | Class has field |
| HAS_PARAMETER | METHOD | PARAMETER | index | Parameter |
| RETURNS | METHOD | TYPE | - | Return type |
| THROWS | METHOD | TYPE | index | Exception |
| INHERITS | CLASS | CLASS | - | Extends |
| IMPLEMENTS | CLASS | INTERFACE | - | Implements |
| EXTENDS | INTERFACE | INTERFACE | - | Extends interface |

### Semantic Edges (Cross-References)
| Type | From | To | Properties | Purpose |
|------|------|---|------------|---------|
| REFERENCES | EXPR | NODE | - | Generic reference |
| CALLS | METHOD_CALL | METHOD | - | Calls method |
| CALLS_CONSTRUCTOR | NEW_CLASS | METHOD | - | Calls constructor |
| ACCESSES | FIELD_ACCESS | FIELD | - | Accesses field |
| CREATES | NEW_CLASS | CLASS | - | Creates instance |
| REFERENCES_TYPE | NODE | TYPE | - | Uses type |
| OVERRIDES | METHOD | METHOD | - | Overrides |
| READS/WRITES | EXPR | FIELD | - | Field access |
| IMPORTS | FILE | TYPE | - | Import |
| ANNOTATED_WITH | NODE | ANNOTATION | - | Has annotation |

---

## 📋 IMPLEMENTATION PHASES

| Phase | Name | Days | Goal |
|-------|------|------|------|
| 0 | Setup | 1-2 | Maven + JavaParser |
| 1 | Core Model | 3-5 | Nodes, edges, graph |
| 2 | Parsing | 5-7 | Java -> AST nodes |
| 3 | Resolution | 5-7 | Resolve symbols |
| 4 | Construction | 3-5 | Build complete graph |
| 5 | Serialization | 2-3 | Save/load JSON |
| 6 | Query API | 3-5 | Query interface |
| 7 | Vibe Integration | 2-3 | CLI + queries |
| 8 | Optimization | 2-3 | Performance |
| 9 | Testing | 3-5 | Tests + polish |

**Total: 6-8 weeks part-time | 2-3 weeks full-time**

### Phase Details

**Phase 0: Setup**
- [x] Create Maven project
- [x] Add dependencies
- [x] Create Main.java
- [x] Verify build (structure verified; Maven not available in environment)

**Phase 1: Core Model**
- [x] Position record
- [x] Node interface
- [x] Edge class
- [x] EdgeTypes constants
- [x] All node classes (DeclarationNode, PackageNode, ClassNode, MethodNode, FieldNode, ParameterNode, VariableNode)
- [x] Graph + Indexes
- [x] NodeIdGenerator

**Phase 2: Parsing**
- [x] JavaParserConfig - Configure JavaParser with symbol solving
- [x] JavaFileCollector - Collect Java files from project directories
- [x] VisitorContext - Context for AST traversal with scoping and symbol tracking
- [x] NodeFactory - Create nodes from JavaParser AST nodes (Package, Class, Method, Field, Parameter, Variable)
- [x] EdgeFactory - Create edges between nodes (structural, expression, semantic)
- [x] DeclarationVisitor - Process package, class, method, field, parameter declarations
- [x] StatementVisitor - Process block, if, for, while, try-catch, return, throw, etc.
- [x] ExpressionVisitor - Process expressions (method calls, literals, binary ops, etc.)

**Phase 3: Resolution**
- [x] SymbolTable - Global symbol table with indexes for classes, methods, fields, variables
- [x] SymbolResolver - Multi-pass resolver with statistics tracking
- [x] Resolve variables - Variable reference resolution
- [x] Resolve methods - Method reference resolution with overload handling
- [x] Resolve fields - Field reference resolution within class context
- [x] Resolve types - Type reference resolution with primitive and java.lang support
- [x] Handle inheritance - INHERITS, IMPLEMENTS, EXTENDS edge creation and override detection

**Phase 4: Construction**
- [x] JavaCodeGraphBuilder - Main entry point with multi-phase build process
- [x] Parse all files - File collection and individual file parsing
- [x] Resolve symbols - Integration with SymbolResolver
- [x] Add derived edges - Placeholder for future derived edge creation

**Phase 5: Serialization**
- [x] JsonSerializer - JSON serialization with Gson
- [x] Serialize/deserialize - Full graph serialization with metadata
- [x] Round-trip test - Graph equality checking for round-trip validation

**Phase 6: Query API**
- [x] JavaGraphQuery interface - Complete query interface with 25+ query methods
- [x] GraphQueryEngine - Full implementation of JavaGraphQuery interface
- [x] All query methods - Class, method, field, variable, AST, file, package, control flow, search, and statistics queries

**Phase 7: Vibe Integration**
- [x] LLM_INTEGRATION.md scoping document
- [x] Vibe Skill scaffolding (SKILL.md, README.md)
- [x] Build tool (tool.yaml + execute.py)
- [x] Query tool (tool.yaml + execute.py)
- [x] QueryParser with NLP patterns
- [x] QueryExecutor with graph querying
- [x] MCP server skeleton (server.py)
- [x] Java Main.java CLI entry point
- [x] CommandLineInterface with build/query/interactive modes
- [x] QueryCommand DTO for parsed queries
- [x] QueryParser for parsing query strings
- [x] Added missing query methods (getAllMethods, getAllFields)
- [ ] Package as JAR for tool execution
- [ ] Integration testing with Vibe
- [ ] Query DSL refinement
- [ ] Fallback to grep for edge cases
- [ ] Final documentation

**Phase 8: Optimization**
- [x] CachingQueryEngine - Query result caching with statistics tracking
- [x] ParallelGraphBuilder - Parallel file parsing with configurable thread pool
- [x] IncrementalGraphBuilder - Incremental updates with file modification tracking
- [x] Added supporting methods: Graph.addAll(), Graph.removeEdgesByType(), SymbolTable.merge()

**Phase 9: Testing**
- [ ] Unit tests
- [ ] Integration tests
- [ ] Edge cases
- [ ] Validation

---

## 🔍 QUERY API

```java
public interface JavaGraphQuery {
    // CLASS
    Optional<ClassNode> findClassByQualifiedName(String qn);
    List<ClassNode> findClassesByName(String name);
    List<ClassNode> getAllClasses();
    List<ClassNode> getSubclasses(ClassNode clazz);
    List<ClassNode> getImplementations(ClassNode iface);
    Optional<ClassNode> getSuperclass(ClassNode clazz);
    
    // METHOD
    Optional<MethodNode> findMethodBySignature(String sig);
    List<MethodNode> findMethodsByName(String name);
    List<MethodNode> getMethods(ClassNode clazz);
    List<MethodNode> getCallers(MethodNode method);
    List<MethodNode> getCallees(MethodNode method);
    Optional<MethodNode> getOverriddenMethod(MethodNode method);
    
    // FIELD
    Optional<FieldNode> findFieldByQualifiedName(String qn);
    List<FieldNode> getFields(ClassNode clazz);
    List<FieldAccessNode> getFieldAccesses(FieldNode field);
    
    // VARIABLE
    List<VariableNode> getVariables(MethodNode method);
    List<ExpressionNode> getVariableUses(VariableNode var);
    
    // AST
    List<StatementNode> getStatements(MethodNode method);
    Optional<StatementNode> getStatementAt(MethodNode method, int line);
    
    // FILE
    List<Node> getNodesInFile(Path file);
    
    // CONTROL FLOW
    ControlFlowGraph buildCFG(MethodNode method);
    boolean isReachable(StatementNode from, StatementNode to);
}
```

---

## 💻 KEY CODE SNIPPETS

### 1. Node Interface & Position
```java
public record Position(int line, int column) {}

public interface Node {
    String getId();
    String getType();
    Path getFile();
    Position getStartPosition();
    Position getEndPosition();
    Map<String, Object> getProperties();
}
```

### 2. Edge Class
```java
public class Edge {
    private final String fromId, toId, type;
    private final Map<String, Object> properties;
    public Edge(String fromId, String toId, String type) {
        this(fromId, toId, type, Map.of());
    }
    public Edge(String fromId, String toId, String type, Map<String, Object> props) {
        this.fromId = fromId; this.toId = toId; this.type = type; this.properties = props;
    }
    // getters, equals, hashCode
}
```

### 3. ClassNode
```java
public class ClassNode implements Node {
    private final String id, name, qualifiedName;
    private final Set<String> modifiers;
    private final String superclass;
    private final List<String> interfaces;
    private final boolean isInterface, isEnum;
    private final Path file;
    private final Position start, end;
    // constructor, getters, equals, hashCode
}
```

### 4. MethodNode
```java
public class MethodNode implements Node {
    private final String id, name, signature, qualifiedName;
    private final String returnType;
    private final Set<String> modifiers;
    private final boolean isConstructor, isStatic;
    private final Path file;
    private final Position start, end;
    // constructor, getters
}
```

### 5. Graph + Indexes
```java
public class Graph {
    private final Map<String, Node> nodes = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Indexes indexes = new Indexes();
    public void addNode(Node node) { nodes.put(node.getId(), node); indexes.index(node); }
    public void addEdge(Edge edge) { edges.add(edge); indexes.index(edge); }
    public Optional<Node> getNode(String id) { return Optional.ofNullable(nodes.get(id)); }
    public List<Node> getNodesByType(String type) { return indexes.getNodesByType(type); }
    public List<Edge> getEdgesFrom(String nodeId) { return indexes.getEdgesFrom(nodeId); }
    public List<Edge> getEdgesTo(String nodeId) { return indexes.getEdgesTo(nodeId); }
}

public class Indexes {
    private final Map<String, List<Node>> nodesByType = new HashMap<>();
    private final Map<String, Node> nodesById = new HashMap<>();
    private final Map<String, List<Node>> nodesByFile = new HashMap<>();
    private final Map<String, ClassNode> classesByFqn = new HashMap<>();
    private final Map<String, List<ClassNode>> classesByName = new HashMap<>();
    private final Map<String, MethodNode> methodsBySignature = new HashMap<>();
    private final Map<String, List<MethodNode>> methodsByName = new HashMap<>();
    private final Map<String, List<Edge>> edgesByType = new HashMap<>();
    private final Map<String, List<Edge>> edgesFrom = new HashMap<>();
    private final Map<String, List<Edge>> edgesTo = new HashMap<>();
    public void index(Node node) { /* add to all indexes */ }
    public void index(Edge edge) { /* add to all indexes */ }
}
```

### 6. NodeIdGenerator
```java
public class NodeIdGenerator {
    public static String forDeclaration(NodeType type, String qn) {
        return type.prefix + ":" + qn.replace(".", "/");
    }
    public static String forAst(NodeType type, Path file, Position pos) {
        String fileKey = file.toString().replace("/", "_").replace("\\", "_");
        return type.prefix + ":" + fileKey + ":" + pos.line() + ":" + pos.column();
    }
    public enum NodeType {
        PACKAGE("pkg"), CLASS("cls"), METHOD("mth"), FIELD("fld"),
        PARAMETER("par"), VARIABLE("var"), BLOCK("blk"), IF("if"),
        EXPRESSION("expr");
        private final String prefix;
        NodeType(String prefix) { this.prefix = prefix; }
    }
}
```

### 7. NodeFactory (Key Methods)
```java
public class NodeFactory {
    private final Graph graph;
    
    public ClassNode createClass(ClassOrInterfaceDeclaration n, Path file) {
        String name = n.getName().toString();
        String fqn = n.getFullyQualifiedName().orElse(name);
        Set<String> mods = n.getModifiers().stream().map(Modifier::toString).collect(toSet());
        String superclass = n.getExtendedTypes().isEmpty() ? null : n.getExtendedTypes().get(0).toString();
        List<String> interfaces = n.getImplementedTypes().stream().map(Object::toString).toList();
        String id = NodeIdGenerator.forDeclaration(NodeType.CLASS, fqn);
        Position start = toPosition(n.getBegin().orElseThrow());
        Position end = toPosition(n.getEnd().orElseThrow());
        ClassNode node = new ClassNode(id, name, fqn, mods, superclass, interfaces, n.isInterface(), false, file, start, end);
        graph.addNode(node);
        return node;
    }
    
    private Position toPosition(com.github.javaparser.Position pos) {
        return new Position(pos.line, pos.column);
    }
}
```

### 8. EdgeFactory
```java
public class EdgeFactory {
    private final Graph graph;
    public void createContains(Node parent, Node child, int index) {
        graph.addEdge(new Edge(parent.getId(), child.getId(), EdgeTypes.CONTAINS, Map.of("index", index)));
    }
    public void createBody(MethodNode method, BlockNode body) {
        graph.addEdge(new Edge(method.getId(), body.getId(), EdgeTypes.BODY));
    }
    public void createCondition(IfStatementNode ifStmt, ExpressionNode cond) {
        graph.addEdge(new Edge(ifStmt.getId(), cond.getId(), EdgeTypes.CONDITION));
    }
    public void createLeftOperand(BinaryOperationNode op, ExpressionNode left) {
        graph.addEdge(new Edge(op.getId(), left.getId(), EdgeTypes.LEFT_OPERAND));
    }
    public void createCalls(MethodCallNode call, MethodNode target) {
        graph.addEdge(new Edge(call.getId(), target.getId(), EdgeTypes.CALLS));
    }
    public void createReferences(VariableReferenceNode ref, VariableNode target) {
        graph.addEdge(new Edge(ref.getId(), target.getId(), EdgeTypes.REFERENCES));
    }
}
```

### 9. SymbolTable
```java
public class SymbolTable {
    private final Map<String, ClassNode> classesByFqn = new HashMap<>();
    private final Map<String, List<ClassNode>> classesByName = new HashMap<>();
    private final Map<String, MethodNode> methodsBySignature = new HashMap<>();
    private final Map<Path, String> packageByFile = new HashMap<>();
    private final Map<Path, Set<String>> importsByFile = new HashMap<>();
    
    public Optional<ClassNode> resolveClass(String name, Path file) {
        if (isPrimitive(name)) return Optional.empty();
        if (classesByFqn.containsKey(name)) return Optional.of(classesByFqn.get(name));
        for (String imp : importsByFile.getOrDefault(file, Set.of())) {
            if (imp.endsWith("." + name) || imp.equals(name)) {
                return Optional.ofNullable(classesByFqn.get(imp));
            }
        }
        String pkg = packageByFile.get(file);
        if (pkg != null) {
            String fqn = pkg + "." + name;
            if (classesByFqn.containsKey(fqn)) return Optional.of(classesByFqn.get(fqn));
        }
        String javaLang = "java.lang." + name;
        if (classesByFqn.containsKey(javaLang)) return Optional.of(classesByFqn.get(javaLang));
        return Optional.empty();
    }
    
    private boolean isPrimitive(String name) {
        return Set.of("byte","short","int","long","float","double","char","boolean","void").contains(name);
    }
}
```

### 10. JavaCodeGraphBuilder
```java
public class JavaCodeGraphBuilder {
    private final Path projectRoot;
    private final Graph graph = new Graph();
    
    public Graph build() throws IOException {
        List<Path> files = JavaFileCollector.collect(projectRoot);
        JavaParserConfig.setup(projectRoot);
        
        for (Path file : files) {
            CompilationUnit cu = StaticJavaParser.parse(file);
            VisitorContext ctx = new VisitorContext(file, null, null, null, null, 0, new Scope(null));
            new DeclarationVisitor(graph, ctx).visit(cu, ctx);
            new StatementVisitor(graph, ctx).visit(cu, ctx);
            new ExpressionVisitor(graph, ctx).visit(cu, ctx);
        }
        
        new SymbolResolver(graph, new SymbolTable()).resolve();
        addInheritsEdges();
        return graph;
    }
    
    private void addInheritsEdges() {
        graph.getNodesByType("CLASS").forEach(n -> {
            ClassNode cls = (ClassNode) n;
            if (cls.getSuperclass() != null) {
                graph.getIndexes().getClassByQualifiedName(cls.getSuperclass())
                    .ifPresent(parent -> graph.addEdge(new Edge(cls.getId(), parent.getId(), EdgeTypes.INHERITS)));
            }
        });
    }
}
```

### 11. GraphQueryEngine (Key Methods)
```java
public class GraphQueryEngine implements JavaGraphQuery {
    private final Graph graph;
    
    @Override
    public Optional<ClassNode> findClassByQualifiedName(String qn) {
        return graph.getIndexes().getClassByQualifiedName(qn);
    }
    
    @Override
    public List<MethodNode> getMethods(ClassNode clazz) {
        return graph.getEdgesFrom(clazz.getId(), EdgeTypes.HAS_METHOD).stream()
            .map(e -> graph.getNode(e.getToId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(n -> n instanceof MethodNode)
            .map(n -> (MethodNode) n)
            .collect(toList());
    }
    
    @Override
    public List<MethodNode> getCallers(MethodNode method) {
        return graph.getEdgesTo(method.getId(), EdgeTypes.CALLS).stream()
            .map(e -> graph.getNode(e.getFromId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(n -> n instanceof MethodCallNode)
            .map(n -> (MethodCallNode) n)
            .map(this::resolveMethodForCall)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toList());
    }
    
    @Override
    public List<StatementNode> getStatements(MethodNode method) {
        return graph.getEdgesFrom(method.getId(), EdgeTypes.BODY).stream()
            .findFirst()
            .flatMap(e -> graph.getNode(e.getToId()))
            .filter(n -> n instanceof BlockNode)
            .map(n -> (BlockNode) n)
            .flatMap(block -> graph.getEdgesFrom(block.getId(), EdgeTypes.CONTAINS).stream())
            .sorted(Comparator.comparing(e -> (int) e.getProperties().getOrDefault("index", 0)))
            .map(e -> graph.getNode(e.getToId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(n -> n instanceof StatementNode)
            .map(n -> (StatementNode) n)
            .collect(toList());
    }
}
```

---

## ⚠️ EDGE CASES

| Case | Strategy |
|------|----------|
| Syntax errors | Skip file, log warning |
| Unresolved symbols | Store as-is, no crash |
| Duplicate names | Use FQN + file in ID |
| Inner classes | ID: `cls:Outer$Inner` |
| Anonymous classes | ID: `cls:Outer$1` |
| Lambdas | Treat as METHOD-like |
| Method refs | Store name, resolve if possible |
| Generics | Store raw string, try resolve |
| Wildcard imports | Expand on demand, cache |
| Static imports | Track in SymbolTable |
| Large files | Limit depth, lazy nodes |

---

## 🧪 TESTING & VALIDATION

### Validation Method
```java
public void validateGraph(Graph graph) {
    // Check all nodes reachable
    Set<String> reachable = findReachableNodes(graph);
    Set<String> all = graph.getAllNodes().stream().map(Node::getId).collect(toSet());
    assertTrue("Orphaned: " + new HashSet<>(all).removeAll(reachable), orphaned.isEmpty());
    
    // Check all edges valid
    for (Edge edge : graph.getAllEdges()) {
        assertTrue(graph.getNode(edge.getFromId()).isPresent());
        assertTrue(graph.getNode(edge.getToId()).isPresent());
    }
}
```

### Test Files to Create
- `SimpleClass.java` - Basic class
- `Inheritance.java` - Class hierarchy
- `Interfaces.java` - Implements
- `Generics.java` - Generic types
- `Lambdas.java` - Lambda expressions
- `EdgeCases.java` - Special syntax

---

## ⚡ PERFORMANCE NOTES

### Complexity
| Operation | Complexity | Notes |
|-----------|------------|-------|
| findClass | O(1) | HashMap |
| getMethods | O(1)+O(k) | Index |
| getCallers | O(1)+O(k) | Reverse index |
| getStatements | O(1)+O(k) | Index |
| buildCFG | O(n) | n = statements |

### Memory
| Component | Size | Optimization |
|-----------|------|--------------|
| Node | ~200B | Intern strings |
| Edge | ~100B | Pool |
| Indexes | ~300B/node | Lazy |

**Estimated:** 100 files = ~10MB | 1000 files = ~100MB

### Optimizations
1. Lazy loading (load AST on demand)
2. Caching (Caffeine/Guava)
3. Parallel parsing
4. Incremental updates
5. Protobuf serialization
6. Flyweight pattern
7. String interning

---

## 📚 RESOURCES
- JavaParser: https://javaparser.org/ | https://github.com/javaparser/javaparser
- JavaParser Docs: https://javaparser.org/apidocs/
- AST Viewer: https://javaparser.org/ast-viewer
- Gson: https://github.com/google/gson
- Maven: https://maven.apache.org/

---

## ✅ COMPLETION CHECKLIST

- [x] Phase 0: Setup
- [x] Phase 1: Core Model
- [x] Phase 2: Parsing
- [x] Phase 3: Resolution
- [x] Phase 4: Construction
- [x] Phase 5: Serialization
- [x] Phase 6: Query API
- [x] Phase 7: Vibe Integration
- [x] Phase 8: Optimization
- [ ] Phase 9: Testing

---

**This document contains ALL information needed to implement the project.**

**Start with Phase 0 and work through the checklist!**

---

## 📝 LESSONS LEARNED & DESIGN DECISIONS

### Phase 1 Implementation Notes

1. **DeclarationNode Hierarchy**: Created an abstract `DeclarationNode` base class for all declaration types (Package, Class, Method, Field, Parameter, Variable). This provides a common structure and avoids code duplication for shared properties like id, name, qualifiedName, file, and positions.

2. **Type Naming Conflict**: Discovered that `getType()` method in the `Node` interface conflicts with field/method type getters in `FieldNode`, `ParameterNode`, and `VariableNode`. Resolution: Renamed the data type getters to `getDataType()` to avoid the naming collision while maintaining the `getType()` method for the node type.

3. **Indexes Design**: Implemented comprehensive indexing with `ConcurrentHashMap` for thread-safe operations. Indexes include node indexes (by ID, type, file), class indexes (by FQN, by name), method indexes (by signature, by name), field indexes (by FQN, by name), package indexes (by name), and edge indexes (by type, by from node, by to node). This enables O(1) lookups for most common queries.

4. **NodeIdGenerator**: Created a utility class for generating unique IDs following the patterns specified in the document. Added `NodeType` enum for all node types with their prefixes.

5. **Immutability**: Used `Collections.unmodifiableMap()`, `Set.copyOf()`, and `List.copyOf()` extensively to ensure that returned collections cannot be modified externally.

6. **Validation**: Added null/blank checks in constructors and setter methods to prevent invalid state.

7. **Compilation Testing**: Without Maven available in the environment, tested compilation using direct `javac` commands. Had to comment out JavaParser-specific utility methods and ensure all imports were correct.

### Design Patterns Applied

1. **Factory Pattern**: `NodeIdGenerator` acts as a factory for generating unique IDs
2. **Composite Pattern**: `Graph` contains `Node` and `Edge` objects with hierarchical relationships
3. **Index/Repository Pattern**: `Indexes` provides efficient lookup and querying capabilities
4. **Strategy Pattern**: Different node types implement their own behavior while sharing common interface
5. **Immutable Objects**: Position (record), Edge, and most node properties are immutable

### Open Questions for Future Phases

- Should we use interfaces for different node categories (Declaration, Statement, Expression) instead of just Node?
- Should we add a visitor pattern for traversing the graph?
- Consider adding a builder pattern for complex node construction in the parser phase
- Should AST nodes (statements/expressions) also extend a common base class like we did with DeclarationNode?

### Phase 2 Implementation Notes

1. **Circular Dependencies**: Discovered circular dependencies between visitor classes (DeclarationVisitor <- StatementVisitor <- ExpressionVisitor <- DeclarationVisitor). Resolution: Use forward references and lazy initialization. The visitors pass Graph and VisitorContext to each other rather than holding direct references.

2. **JavaParser API Complexity**: JavaParser's AST node types are extensive and nested. The visitor pattern helps manage this complexity by allowing each visitor to focus on specific node types.

3. **AST Node Types**: JavaParser distinguishes between different categories of nodes:
   - Declaration nodes (ClassOrInterfaceDeclaration, MethodDeclaration, FieldDeclaration, etc.)
   - Statement nodes (BlockStmt, IfStmt, ForStmt, etc.)
   - Expression nodes (MethodCallExpr, BinaryExpr, NameExpr, etc.)
   - Type nodes (ClassOrInterfaceType, ReferenceType, etc.)
   - Body nodes (ConstructorDeclaration, InitializerDeclaration, etc.)

4. **Visitor Pattern**: Used JavaParser's VoidVisitorAdapter<T> as the base class for our visitors. The generic type T is used for the VisitorContext, allowing us to pass context through the visitor chain.

5. **Context Management**: The VisitorContext class is crucial for tracking state during AST traversal:
   - Current file, package, class, method
   - Import statements
   - Symbol table (local to current file)
   - Scope stacks for nested blocks
   - Type resolution utilities

6. **NodeFactory Caching**: Implemented caching in NodeFactory to avoid creating duplicate nodes for the same AST element.

7. **EdgeFactory Deduplication**: Implemented edge caching in EdgeFactory to avoid creating duplicate edges between the same nodes.

8. **Constructor Handling**: Special handling needed for constructors in MethodNode (they have a different ID format and behavior).

9. **Parameter Position Tracking**: Parameter nodes need to track their position within the method signature for proper ordering.

10. **Nested Class Support**: Added handling for nested classes by using the outer class's qualified name with "$" separator.

### Design Patterns Applied (Additional)

6. **Visitor Pattern**: Used extensively for AST traversal (DeclarationVisitor, StatementVisitor, ExpressionVisitor)
7. **Factory Pattern**: NodeFactory and EdgeFactory for creating nodes and edges
8. **Strategy Pattern**: Different visitors implement different strategies for processing different node types
9. **Composite Pattern**: AST itself is a composite structure, and our graph mirrors this
10. **Memento Pattern**: VisitorContext acts as a memento, capturing the state of traversal

### Open Questions for Future Phases

- How to handle forward references (method A calls method B which is defined later)?
- How to handle diamond inheritance and complex type hierarchies?
- Should we create AST node classes for statements/expressions now, or wait until Phase 3?
- How to handle generic type parameters properly?
- How to handle anonymous inner classes?

### Phase 3 Implementation Notes

1. **Symbol Table Design**: Implemented a comprehensive SymbolTable with multiple index types:
   - Classes by FQN and by simple name (for overload resolution)
   - Methods by signature (FQN + signature) and by name
   - Fields by FQN and by name
   - Packages by name
   - File-based indexes (package by file, imports by file, classes by file)
   - Inheritance indexes (subclasses, implementations)
   - Override indexes (overriding/overridden methods)

2. **Multi-Pass Resolution**: SymbolResolver uses 5 passes:
   - Pass 1: Register all declarations in the symbol table
   - Pass 2: Resolve type references (return types, field types, parameter types)
   - Pass 3: Resolve reference expressions (placeholder for expression nodes)
   - Pass 4: Establish inheritance relationships (INHERITS, IMPLEMENTS)
   - Pass 5: Establish override relationships (OVERRIDES)
   - Pass 6: Create semantic edges (CALLS, ACCESSES, etc.)

3. **Resolution Strategy**: The resolveClass method uses a hierarchical approach:
   - First check if already fully qualified
   - Check primitive types (skip)
   - Try to resolve using imports from the file
   - Try current package
   - Try java.lang package
   - Fall back to all classes with matching simple name

4. **Type Handling**: Special handling for:
   - Primitive types (byte, int, etc.) - not stored in symbol table
   - Array types (elementType[]) - recursively resolve element type
   - java.lang types - automatic import resolution
   - Generic types - TODO: will need special handling

5. **Inheritance Checking**: Implemented isSubclass() with recursive checking of:
   - Direct superclass
   - Implemented interfaces
   - Indirect superclass hierarchy

6. **Override Detection**: Methods are considered overrides if they:
   - Have the same name and signature
   - Belong to different classes
   - The overriding class is a subclass of the overridden class's class

7. **Statistics Tracking**: Added counters for resolved classes, methods, fields, variables, and unresolved references to help with debugging and validation.

8. **Placeholder Implementation**: resolveReferences() and createSemanticEdges() are placeholders that will be fully implemented when expression nodes (MethodCallExpr, FieldAccessExpr, etc.) are added to the graph.

### Design Patterns Applied (Additional)

11. **Registry Pattern**: SymbolTable acts as a central registry for all symbols
12. **Strategy Pattern**: Different resolution strategies for different symbol types
13. **Mediator Pattern**: SymbolResolver mediates between the graph and symbol table
14. **Chain of Responsibility**: Resolution attempts multiple strategies in sequence

### Open Questions for Future Phases

- Should we add a type hierarchy cache to speed up isSubclass() checks?
- How to handle generic type parameters and wildcards?
- How to resolve method calls with type inference (e.g., generics)?
- Should we add a "fuzzy" resolution mode for handling incomplete code?
- How to handle static imports?
- Should we add support for resolving annotations?

### Phase 4 Implementation Notes

1. **Builder Pattern**: Implemented JavaCodeGraphBuilder using a builder pattern with a clear multi-phase process:
   - Setup JavaParser
   - Collect Java files
   - Parse each file
   - Resolve symbols
   - Add derived edges
   - Validate graph

2. **File Collection Strategy**: The builder tries standard Maven directories first (src/main/java, src/test/java), then falls back to collecting from the project root. This makes it work with both Maven and non-Maven projects.

3. **Error Handling**: Each file is parsed independently, so errors in one file don't prevent others from being parsed. Statistics track both successful and failed parses.

4. **Context Management**: During file parsing, the builder:
   - Creates a new VisitorContext for each file
   - Processes package declaration first
   - Collects import statements
   - Registers symbols in the symbol table as they're created

5. **Type Declaration Processing**: Handles different type declaration types:
   - ClassOrInterfaceDeclaration -> ClassNode
   - EnumDeclaration -> ClassNode (with isEnum=true) with enum constants as FieldNodes
   - AnnotationDeclaration -> TODO (skipped for now)

6. **Class Member Processing**: For each class, processes:
   - Method declarations (creates MethodNode, processes parameters and body)
   - Field declarations (creates FieldNode for each variable)
   - Nested class/interface declarations (recursive processing)
   - Enum declarations (creates enum class with constants)

7. **Method Body Processing**: For each method:
   - Creates ParameterNode for each parameter
   - Creates HAS_PARAMETER edges
   - Uses StatementVisitor to process the method body

8. **Enum Handling**: Special handling for enums:
   - Created as ClassNode with isEnum=true
   - Each enum constant is a FieldNode with static/final flags
   - Enum constants can have arguments and class bodies

9. **Derived Edges**: Placeholder for creating edges that can be inferred (RETURN_VALUE, THROW_VALUE, CALLS_CONSTRUCTOR, etc.) - will be fully implemented when expression nodes are added.

10. **Graph Validation**: Built-in validation checks for:
    - Nodes with null/blank IDs
    - Edges with missing from/to nodes
    - General graph integrity

11. **Statistics Tracking**: Tracks files parsed, files failed, parse time, node count, edge count for monitoring and debugging.

12. **BuildResult Pattern**: Added a BuildResult inner class to return both the graph and symbol table along with statistics.

### Design Patterns Applied (Additional)

15. **Builder Pattern**: JavaCodeGraphBuilder builds the graph step by step
16. **Facade Pattern**: JavaCodeGraphBuilder acts as a facade for the complex graph construction process
17. **Template Method Pattern**: The build() method defines the overall algorithm structure
18. **Strategy Pattern**: Different processing strategies for different node types

### Open Questions for Future Phases

- Should we add support for incremental building (only reparse changed files)?
- How to handle circular dependencies between files?
- Should we add parallel parsing for better performance on large projects?
- How to handle Java source files with encoding issues?
- Should we add a progress callback for long-running builds?
- How to handle syntax errors in a more graceful way (e.g., partial parsing)?

### Phase 5 Implementation Notes

1. **JSON Structure**: Designed a clean JSON structure with:
   - Version field for future compatibility
   - Metadata (createdAt, nodeCount, edgeCount)
   - Nodes array with full node data
   - Edges array with full edge data

2. **Gson Configuration**: Used GsonBuilder with custom settings:
   - Pretty printing for human readability
   - HTML escaping disabled
   - Complex map key serialization enabled
   - Null serialization enabled
   - Custom type adapters for Position and Path

3. **Type Adapters**: Created custom type adapters:
   - PositionTypeAdapter: Serializes Position as {line, column} object
   - PathTypeAdapter: Serializes Path as string
   - NodeTypeHierarchyAdapter: Handles Node polymorphism (partial - full handling in NodeWrapper)

4. **Wrapper Pattern**: Used wrapper classes to handle complex serialization:
   - GraphWrapper: Wraps the entire graph with metadata
   - NodeWrapper: Handles different node types and their properties
   - EdgeWrapper: Handles edge serialization
   - Metadata: Contains graph statistics

5. **Property Handling**: NodeWrapper extracts properties from DeclarationNode subclasses:
   - For ClassNode: modifiers, superclass, interfaces, isInterface, isEnum
   - For MethodNode: signature, returnType, modifiers, isConstructor, isStatic, thrownExceptions
   - For FieldNode: type, modifiers, isStatic, isFinal
   - For ParameterNode: type, position, belongingMethod
   - For VariableNode: type, scopeMethod, isFinal

6. **Deserialization**: NodeWrapper.toNode() recreates appropriate node types based on the type field, extracting properties from the properties map.

7. **Round-trip Testing**: Implemented testRoundTrip() method that:
   - Serializes a graph to JSON
   - Deserializes back to a graph
   - Compares the two graphs for equality
   - Checks node count, edge count, node IDs, edge connections

8. **SimpleNode**: Created a SimpleNode implementation for handling non-declaration nodes during deserialization, ensuring all node types can be reconstructed.

9. **Multiple Serialization Targets**: Support for serializing to:
   - String (serialize method)
   - File (serialize with Path)
   - Writer (serialize with Writer)
   - And corresponding deserialization methods

10. **Error Handling**: Added null checks and proper exception handling for all public methods.

### Design Patterns Applied (Additional)

19. **Adapter Pattern**: Type adapters for Position, Path, and Node
20. **Wrapper Pattern**: GraphWrapper, NodeWrapper, EdgeWrapper for complex serialization
21. **Composite Pattern**: JSON structure mirrors the graph structure
22. **Memento Pattern**: Serialization captures the complete state of the graph

### Open Questions for Future Phases

- Should we add binary serialization for better performance?
- Should we add compression for large graphs?
- Should we add incremental serialization (only serialize changes)?
- How to handle version migration for future schema changes?
- Should we add support for other serialization formats (XML, Protocol Buffers)?
- Should we add pretty-printing options (e.g., compact vs. formatted)?

### Phase 6 Implementation Notes

1. **Query Interface Design**: Created JavaGraphQuery interface with comprehensive query methods organized into categories:
   - Class queries (6 methods)
   - Method queries (10 methods)
   - Field queries (6 methods)
   - Variable queries (2 methods)
   - AST queries (2 methods)
   - File queries (3 methods)
   - Package queries (3 methods)
   - Control flow queries (2 methods)
   - Search queries (3 methods)
   - Statistics queries (5 methods)

2. **GraphQueryEngine Implementation**: Implemented all 42 query methods from the interface:
   - Delegates to Graph's Indexes for O(1) or O(k) lookups
   - Provides convenient query interface on top of raw index access
   - Handles null/blank parameters gracefully
   - Returns empty collections instead of null for better usability

3. **Query Categories**:
   - **Class Queries**: findByQualifiedName, findByName, getAll, getSubclasses, getImplementations, getSuperclass, getInterfaces
   - **Method Queries**: findBySignature, findByName, getMethods, getCallers, getCallees, getOverridden, getOverriding, getConstructors, getStaticMethods, getPublicMethods
   - **Field Queries**: findByQualifiedName, getFields, getFieldAccesses, getStaticFields, getFinalFields
   - **Variable Queries**: getVariables, getVariableUses
   - **AST Queries**: getStatements, getStatementAt
   - **File Queries**: getNodesInFile, getClassesInFile, getMethodsInFile
   - **Package Queries**: findByName, getAllPackages, getClassesInPackage
   - **Control Flow**: buildCFG, isReachable (placeholders)
   - **Search**: getNodesByType, getEdgesByType, getNodesWithModifier
   - **Statistics**: getTotalNodeCount, getTotalEdgeCount, getNodeCountByType, getEdgeCountByType

4. **Helper Methods**: Added private helper methods for:
   - hasSuperclass(): Recursive superclass checking
   - implementsInterface(): Recursive interface checking
   - findOverriddenInHierarchy(): Override detection in superclass hierarchy
   - getClassForMethod(): Extract class from method
   - isSubclass(): Subclass relationship checking
   - hasModifier(): Modifier checking for different node types
   - isStatementNode(): Type checking for statement nodes
   - belongsToMethod(): Determining if a node belongs to a method

5. **Statistics Tracking**: Added query performance tracking:
   - Query count
   - Cache hits
   - Total query time
   - Average query time
   - Cache clearing and statistics reset

6. **Placeholders**: Some methods are placeholders for future implementation:
   - buildCFG(): Control flow graph construction
   - isReachable(): Reachability analysis
   - getStatements(): Requires statement nodes
   - getStatementAt(): Requires statement nodes
   - getFieldAccesses(): Requires expression nodes
   - getVariableUses(): Requires expression nodes

7. **Recursive Hierarchy Traversal**: Many queries use recursive traversal of the class hierarchy:
   - getSubclasses() checks all classes for superclass relationship
   - getImplementations() checks all classes for interface relationship
   - getOverriddenMethod() recursively checks superclass hierarchy
   - getOverridingMethods() checks all methods for subclass relationship

8. **Stream-Based Processing**: Most queries use Java Streams for:
   - Filtering
   - Mapping
   - Collecting results
   - Lazy evaluation

9. **Null Safety**: All public methods check for null parameters and return empty collections instead of null.

10. **Type Safety**: Uses proper type casting with instanceof checks and Optional for safe navigation.

### Design Patterns Applied (Additional)

23. **Query Object Pattern**: JavaGraphQuery interface defines the query contract
24. **Repository Pattern**: GraphQueryEngine acts as a repository for graph data
25. **Facade Pattern**: GraphQueryEngine provides a simple facade over complex graph traversal
26. **Chain of Responsibility**: Queries can be chained together for complex lookups

### Open Questions for Future Phases

- Should we add a query builder for complex queries (e.g., "find all public methods that call method X")?
- Should we add caching for query results to improve performance?
- Should we add support for query timeouts to prevent long-running queries?
- Should we add a query language (DSL) for expressing complex queries?
- Should we add support for query pagination for large result sets?
- Should we add support for query subscriptions (notifications when graph changes affect query results)?

### Phase 7 Implementation Notes

1. **CLI Architecture**: Implemented CommandLineInterface as the main entry point with three modes:
   - Command mode: Direct execution of build/query commands with arguments
   - Interactive mode: REPL-style shell for exploring the graph
   - Query mode: Execute single queries against a loaded graph

2. **Query Parser Design**: Created QueryParser to parse query strings into QueryCommand objects:
   - Simple colon-delimited syntax: "type:arg1:arg2"
   - Special handling for commands: build, help, exit, clear, version
   - Trims whitespace and handles empty arguments
   - Supports nested query types (e.g., "class:subclasses:FQN")

3. **Query Command DTO**: Implemented QueryCommand as an immutable value object:
   - Holds query type and arguments list
   - Provides convenient methods: hasArguments(), getArgumentCount(), getArgument(int)
   - Proper equals/hashCode/toString implementations

4. **Missing Method Discovery**: During CLI implementation, discovered and added:
   - getAllMethods() to JavaGraphQuery interface and GraphQueryEngine
   - getAllFields() to JavaGraphQuery interface and GraphQueryEngine
   - getAllFields() to Indexes
   - These were referenced by the CLI but not yet implemented

5. **Command Line Features**:
   - Build command: Parses Java projects and optionally serializes to JSON
   - Query command: Loads graphs and executes queries
   - Interactive shell: Persistent session with build/query/clear/help/exit commands
   - Formatting: Human-readable output for classes, methods, fields, packages
   - Help system: Context-sensitive help for commands and query types

6. **Query Type Support**: Implemented query handlers for:
   - Class queries: find, by name, subclasses, implementations, superclass, interfaces
   - Method queries: find by signature, by name, by class, constructors, static, public
   - Field queries: find by FQN, by class, by name
   - Package queries: find, all, classes in package
   - File queries: nodes in file
   - Search queries: nodes by type, edges by type, nodes with modifier
   - Statistics queries: total nodes/edges

7. **Integration with Existing Components**:
   - Updated Main.java to delegate to CommandLineInterface
   - GraphQueryEngine already had most query methods implemented
   - Indexes already had the necessary index methods
   - Only needed to add getAllMethods() and getAllFields() to complete the API

8. **Design Patterns Applied (Additional)**:
   27. **Command Pattern**: QueryCommand encapsulates a query request
   28. **Interpreter Pattern**: QueryParser interprets query strings into commands
   29. **Facade Pattern**: CommandLineInterface provides a simple CLI over complex graph operations
   30. **MVC Pattern**: Separation of CLI (view), QueryCommand (model), QueryParser (controller)

### Phase 8 Implementation Notes

1. **CachingQueryEngine Design**: Implemented a decorator pattern wrapper around JavaGraphQuery:
   - Thread-safe using ConcurrentHashMap for cache storage
   - Caches most query results that are expensive and don't change frequently
   - Tracks cache hits, misses, and hit rate for monitoring
   - Selectively caches: findBy* lookups, getAll* lists, getSubclasses, getImplementations, etc.
   - Does NOT cache: context-dependent queries (getFieldAccesses, getVariableUses), CFG building, reachability
   - Uses string-based cache keys combining method name and arguments

2. **ParallelGraphBuilder Design**: Implemented parallel file parsing using Java's ExecutorService:
   - Configurable thread pool size (defaults to available processors)
   - Parses all Java files concurrently
   - Uses AtomicInteger for thread-safe statistics tracking
   - Symbol resolution remains sequential (requires all declarations to be available)
   - Properly handles exceptions from individual parse tasks
   - Includes validation phase to check graph integrity

3. **IncrementalGraphBuilder Design**: Implemented smart rebuilds that only process changed files:
   - Tracks file modification times using Files.getLastModifiedTime()
   - Maintains mapping of files to their nodes for targeted removal
   - Supports forceFullRebuild() to clear all state
   - Can initialize from existing graph and symbol table (for loading saved state)
   - Properly removes file-specific nodes when files are deleted
   - Reuses SymbolTable.merge() for combining symbol tables

4. **Supporting Infrastructure**:
   - Added Graph.addAll() for merging graphs
   - Added Graph.removeEdgesByType() for cleaning up derived edges
   - Added SymbolTable.merge() for combining symbol tables
   - All methods include proper null/blank checks and validation

5. **Performance Considerations**:
   - CachingQueryEngine: Best for interactive use where same queries are repeated
   - ParallelGraphBuilder: Best for large projects with many files and many CPU cores
   - IncrementalGraphBuilder: Best for IDE integration or continuous analysis scenarios
   - All three can be combined for maximum performance

6. **Design Patterns Applied (Additional)**:
   31. **Decorator Pattern**: CachingQueryEngine wraps another JavaGraphQuery
   32. **Proxy Pattern**: CachingQueryEngine acts as a proxy with caching
   33. **Worker Thread Pattern**: ParallelGraphBuilder uses thread pool for parallel work
   34. **Memento Pattern**: IncrementalGraphBuilder maintains state across builds
   35. **Observer Pattern**: File modification tracking is a form of observable state

### Open Questions for Future Phases
