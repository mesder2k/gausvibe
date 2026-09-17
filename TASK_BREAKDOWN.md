# Java Project Graph Model: Complete Implementation Guide

**Project:** JavaCodeGraph  
**Goal:** Structured graph of Java code for Vibe to understand without grep  
**Version:** 1.0 - 2026-09-17  
**Status:** Self-contained with ALL implementation details

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
- [ ] Create Maven project
- [ ] Add dependencies
- [ ] Create Main.java
- [ ] Verify build

**Phase 1: Core Model**
- [ ] Position record
- [ ] Node interface
- [ ] Edge class
- [ ] EdgeTypes constants
- [ ] All node classes
- [ ] Graph + Indexes
- [ ] NodeIdGenerator

**Phase 2: Parsing**
- [ ] JavaParserConfig
- [ ] JavaFileCollector
- [ ] VisitorContext
- [ ] NodeFactory
- [ ] EdgeFactory
- [ ] DeclarationVisitor
- [ ] StatementVisitor
- [ ] ExpressionVisitor

**Phase 3: Resolution**
- [ ] SymbolTable
- [ ] SymbolResolver
- [ ] Resolve variables
- [ ] Resolve methods
- [ ] Resolve fields
- [ ] Resolve types
- [ ] Handle inheritance

**Phase 4: Construction**
- [ ] JavaCodeGraphBuilder
- [ ] Parse all files
- [ ] Resolve symbols
- [ ] Add derived edges

**Phase 5: Serialization**
- [ ] JsonSerializer
- [ ] Serialize/deserialize
- [ ] Round-trip test

**Phase 6: Query API**
- [ ] JavaGraphQuery interface
- [ ] GraphQueryEngine
- [ ] All query methods

**Phase 7: Vibe Integration**
- [ ] CLI query mode
- [ ] Query DSL
- [ ] Fallback to grep
- [ ] Documentation

**Phase 8: Optimization**
- [ ] CachingQueryEngine
- [ ] Parallel parsing
- [ ] Incremental updates

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

- [ ] Phase 0: Setup
- [ ] Phase 1: Core Model
- [ ] Phase 2: Parsing
- [ ] Phase 3: Resolution
- [ ] Phase 4: Construction
- [ ] Phase 5: Serialization
- [ ] Phase 6: Query API
- [ ] Phase 7: Vibe Integration
- [ ] Phase 8: Optimization
- [ ] Phase 9: Testing

---

**This document contains ALL information needed to implement the project.**

**Start with Phase 0 and work through the checklist!**
