package dk.gausdalfind.parser;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Factory for creating nodes from JavaParser AST nodes.
 * 
 * This factory creates declaration nodes (Package, Class, Method, Field, Parameter, Variable)
 * from JavaParser's AST node types.
 */
public class NodeFactory {
    
    private final Graph graph;
    private final VisitorContext context;
    
    // Cache for already created nodes to avoid duplicates
    private final Map<String, dk.gausdalfind.model.Node> nodeCache = new HashMap<>();
    
    /**
     * Creates a new node factory.
     * 
     * @param graph the graph to add nodes to
     * @param context the visitor context
     */
    public NodeFactory(Graph graph, VisitorContext context) {
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
     * Creates a node from a JavaParser AST node.
     * 
     * @param astNode the JavaParser AST node
     * @return the created node, or null if the node type is not supported
     */
    public dk.gausdalfind.model.Node create(com.github.javaparser.ast.Node astNode) {
        if (astNode == null) {
            return null;
        }
        
        try {
            if (astNode instanceof PackageDeclaration) {
                return createPackage((PackageDeclaration) astNode);
            } else if (astNode instanceof ClassOrInterfaceDeclaration) {
                return createClass((ClassOrInterfaceDeclaration) astNode);
            } else if (astNode instanceof EnumDeclaration) {
                return createClass((ClassOrInterfaceDeclaration) null);
            } else if (astNode instanceof MethodDeclaration) {
                return createMethod((MethodDeclaration) astNode);
            } else if (astNode instanceof FieldDeclaration) {
                return createField((FieldDeclaration) astNode);
            } else if (astNode instanceof Parameter) {
                return createParameter((Parameter) astNode);
            } else if (astNode instanceof VariableDeclarator) {
                return createVariable((VariableDeclarator) astNode);
            }
            // TODO: Add support for other AST node types (statements, expressions)
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Error creating node from AST node: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Creates a package node from a PackageDeclaration.
     */
    public PackageNode createPackage(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return null;
        }
        
        String name = pkgDecl.getName().toString();
        Path file = context.getCurrentFile();
        
        Position startPos = toPosition(pkgDecl.getBegin().orElse(null));
        Position endPos = toPosition(pkgDecl.getEnd().orElse(null));
        
        PackageNode node = new PackageNode(name, file, startPos, endPos);
        
        // Store in context
        context.setCurrentPackage(name);
        context.addSymbol(name, node);
        
        // Add to graph
        if (graph.addNode(node)) {
            nodeCache.put(node.getId(), node);
        }
        
        return node;
    }
    
    /**
     * Creates a class node from a ClassOrInterfaceDeclaration.
     */
    public ClassNode createClass(ClassOrInterfaceDeclaration classDecl) {
        if (classDecl == null) {
            return null;
        }
        
        String name = classDecl.getName().toString();
        String qualifiedName = classDecl.getFullyQualifiedName().orElse(name);
        
        // Handle nested classes
        if (context.getCurrentClass() != null && !context.getCurrentClass().isBlank()) {
            qualifiedName = context.getCurrentClass() + "$" + name;
        } else if (context.getCurrentPackage() != null && !context.getCurrentPackage().isBlank()) {
            qualifiedName = context.getCurrentPackage() + "." + name;
        }
        
        // Get modifiers
        Set<String> modifiers = new HashSet<>();
        classDecl.getModifiers().forEach(m -> modifiers.add(m.toString()));
        
        // Get superclass
        String superclass = null;
        if (!classDecl.getExtendedTypes().isEmpty()) {
            superclass = context.resolveType(stripTypeArguments(
                classDecl.getExtendedTypes().get(0).toString()));
        }
        
        // Get interfaces
        List<String> interfaces = new ArrayList<>();
        for (ClassOrInterfaceType iface : classDecl.getImplementedTypes()) {
            interfaces.add(context.resolveType(stripTypeArguments(iface.toString())));
        }
        
        Path file = context.getCurrentFile();
        Position startPos = toPosition(classDecl.getBegin().orElse(null));
        Position endPos = toPosition(classDecl.getEnd().orElse(null));
        
        boolean isInterface = classDecl.isInterface();
        boolean isEnum = false; // Enum handling would be separate
        
        String id = NodeIdGenerator.forDeclaration(
            NodeIdGenerator.NodeType.CLASS, qualifiedName
        );
        
        ClassNode node = new ClassNode(
            id, name, qualifiedName, modifiers, superclass, interfaces,
            isInterface, isEnum, file, startPos, endPos
        );
        
        // Store in context
        context.setCurrentClass(qualifiedName);
        context.addSymbol(name, node);
        
        // Add to graph
        if (graph.addNode(node)) {
            nodeCache.put(node.getId(), node);
        }
        
        return node;
    }
    
    /**
     * Creates a method node from a MethodDeclaration.
     */
    public MethodNode createMethod(MethodDeclaration methodDecl) {
        if (methodDecl == null) {
            return null;
        }
        
        String name = methodDecl.getName().toString();
        String returnType = methodDecl.getType().toString();
        
        // Build qualified name
        String qualifiedName;
        if (context.getCurrentClass() != null && !context.getCurrentClass().isBlank()) {
            qualifiedName = context.getCurrentClass() + "." + name;
        } else {
            qualifiedName = name;
        }
        
        // Build signature
        StringBuilder sigBuilder = new StringBuilder();
        sigBuilder.append(name).append("(");
        boolean first = true;
        for (Parameter param : methodDecl.getParameters()) {
            if (!first) {
                sigBuilder.append(",");
            }
            sigBuilder.append(param.getType().toString());
            first = false;
        }
        sigBuilder.append(")");
        String signature = sigBuilder.toString();
        
        // Get modifiers
        Set<String> modifiers = new HashSet<>();
        methodDecl.getModifiers().forEach(m -> modifiers.add(m.toString()));
        
        // Get thrown exceptions
        List<String> thrownExceptions = new ArrayList<>();
        for (ReferenceType ref : methodDecl.getThrownExceptions()) {
            thrownExceptions.add(ref.toString());
        }
        
        // Check if this is a constructor (name matches class name)
        boolean isConstructor = context.getCurrentClass() != null && 
            methodDecl.getName().toString().equals(
                context.getCurrentClass().substring(context.getCurrentClass().lastIndexOf('.') + 1)
            );
        boolean isStatic = modifiers.contains("static");
        
        Path file = context.getCurrentFile();
        Position startPos = toPosition(methodDecl.getBegin().orElse(null));
        Position endPos = toPosition(methodDecl.getEnd().orElse(null));
        
        // Method IDs include the signature so that overloads do not collide
        // (two methods named "serialize" in the same class must both exist
        // in the graph). Matches the documented ID format
        // "mth:com/example/MyClass#method()".
        String id = NodeIdGenerator.forDeclaration(
            NodeIdGenerator.NodeType.METHOD, qualifiedName + "#" + signature
        );
        
        MethodNode node = new MethodNode(
            id, name, signature, qualifiedName, returnType, modifiers,
            isConstructor, isStatic, thrownExceptions, file, startPos, endPos
        );
        
        // Store in context
        context.setCurrentMethod(qualifiedName + "#" + signature);
        context.setCurrentMethodId(node.getId());
        context.addSymbol(name, node);
        
        // Add to graph
        if (graph.addNode(node)) {
            nodeCache.put(node.getId(), node);
        }
        
        return node;
    }
    
    /**
     * Creates a field node from a FieldDeclaration.
     */
    public FieldNode createField(FieldDeclaration fieldDecl) {
        if (fieldDecl == null) {
            return null;
        }
        
        // FieldDeclaration can have multiple variables
        for (VariableDeclarator var : fieldDecl.getVariables()) {
            String name = var.getName().toString();
            String type = fieldDecl.getElementType().toString();
            
            // Build qualified name
            String qualifiedName;
            if (context.getCurrentClass() != null && !context.getCurrentClass().isBlank()) {
                qualifiedName = context.getCurrentClass() + "." + name;
            } else {
                qualifiedName = name;
            }
            
            // Get modifiers
            Set<String> modifiers = new HashSet<>();
            fieldDecl.getModifiers().forEach(m -> modifiers.add(m.toString()));
            
            boolean isStatic = modifiers.contains("static");
            boolean isFinal = modifiers.contains("final");
            
            Path file = context.getCurrentFile();
            Position startPos = toPosition(fieldDecl.getBegin().orElse(null));
            Position endPos = toPosition(fieldDecl.getEnd().orElse(null));
            
            String id = NodeIdGenerator.forDeclaration(
                NodeIdGenerator.NodeType.FIELD, qualifiedName
            );
            
            FieldNode node = new FieldNode(
                id, name, qualifiedName, type, modifiers, isStatic, isFinal,
                file, startPos, endPos
            );
            
            // Store in context
            context.addSymbol(name, node);
            
            // Add to graph
            if (graph.addNode(node)) {
                nodeCache.put(node.getId(), node);
            }
            
            return node;
        }
        
        return null;
    }
    
    /**
     * Creates a parameter node from a Parameter.
     */
    public ParameterNode createParameter(Parameter param) {
        if (param == null) {
            return null;
        }
        
        String name = param.getName().toString();
        String type = param.getType().toString();
        
        // Build qualified name
        String qualifiedName;
        if (context.getCurrentMethod() != null && !context.getCurrentMethod().isBlank()) {
            qualifiedName = context.getCurrentMethod() + ":" + name;
        } else {
            qualifiedName = name;
        }
        
        // Get position
        int position = context.getCurrentMethod() != null ? 
            getParameterPosition(param) : 0;
        
        String belongingMethod = context.getCurrentMethod();
        
        Path file = context.getCurrentFile();
        Position startPos = toPosition(param.getBegin().orElse(null));
        Position endPos = toPosition(param.getEnd().orElse(null));
        
        String id = NodeIdGenerator.forParameter(
            context.getCurrentMethod(), name, position
        );
        
        ParameterNode node = new ParameterNode(
            id, name, qualifiedName, type, position, belongingMethod,
            file, startPos, endPos
        );
        
        // Store in context
        context.addSymbol(name, node);
        
        // Add to graph
        if (graph.addNode(node)) {
            nodeCache.put(node.getId(), node);
        }
        
        return node;
    }
    
    /**
     * Creates a variable node from a VariableDeclarator.
     */
    public VariableNode createVariable(VariableDeclarator varDecl) {
        if (varDecl == null) {
            return null;
        }
        
        String name = varDecl.getName().toString();
        String type = ""; // Type would come from parent declaration
        
        // Try to get type from parent
        com.github.javaparser.ast.Node parent = varDecl.getParentNode().orElse(null);
        if (parent instanceof FieldDeclaration) {
            type = ((FieldDeclaration) parent).getElementType().toString();
        } else if (parent instanceof VariableDeclarationExpr) {
            type = ((VariableDeclarationExpr) parent).getElementType().toString();
        }
        
        // Build qualified name
        String qualifiedName;
        if (context.getCurrentMethod() != null && !context.getCurrentMethod().isBlank()) {
            qualifiedName = context.getCurrentMethod() + ":" + name;
        } else if (context.getCurrentClass() != null && !context.getCurrentClass().isBlank()) {
            qualifiedName = context.getCurrentClass() + "." + name;
        } else {
            qualifiedName = name;
        }
        
        boolean isFinal = false; // Would check modifiers if available
        String scopeMethod = context.getCurrentMethod();
        
        Path file = context.getCurrentFile();
        Position startPos = toPosition(varDecl.getBegin().orElse(null));
        Position endPos = toPosition(varDecl.getEnd().orElse(null));
        
        String id = NodeIdGenerator.forVariable(file, context.getCurrentMethod(), name);
        
        VariableNode node = new VariableNode(
            id, name, qualifiedName, type, scopeMethod, isFinal,
            file, startPos, endPos
        );
        
        // Store in context
        context.addSymbol(name, node);
        
        // Add to graph
        if (graph.addNode(node)) {
            nodeCache.put(node.getId(), node);
        }
        
        return node;
    }
    
    /**
     * Gets the position of a parameter within its method.
     */
    private int getParameterPosition(Parameter param) {
        com.github.javaparser.ast.Node parent = param.getParentNode().orElse(null);
        if (parent instanceof MethodDeclaration) {
            MethodDeclaration method = (MethodDeclaration) parent;
            return method.getParameters().indexOf(param);
        }
        return 0;
    }
    
    /**
     * Converts a JavaParser Position to our Position record.
     */
    /**
     * Removes generic type arguments and array markers from a type string,
     * e.g. "List<String>[]" -> "List".
     */
    private String stripTypeArguments(String type) {
        if (type == null) return null;
        return type.replaceAll("<[^<>]*>", "")
                   .replaceAll("<[^<>]*>", "")
                   .replaceAll("\\[\\]", "")
                   .replaceAll("\\.\\.\\.", "")
                   .trim();
    }

    private Position toPosition(com.github.javaparser.Position pos) {
        if (pos == null) {
            return null;
        }
        return new Position(pos.line, pos.column);
    }
    
    /**
     * Returns a node from the cache if it exists.
     */
    public dk.gausdalfind.model.Node getNode(String id) {
        return nodeCache.get(id);
    }
    
    /**
     * Clears the node cache.
     */
    public void clearCache() {
        nodeCache.clear();
    }
}
