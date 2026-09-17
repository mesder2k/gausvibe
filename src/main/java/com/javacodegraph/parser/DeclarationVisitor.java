package com.javacodegraph.parser;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.javacodegraph.model.*;
import com.javacodegraph.model.declaration.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Visitor for processing declaration nodes in the JavaParser AST.
 * 
 * This visitor handles:
 * - Package declarations
 * - Class/interface declarations
 * - Method declarations
 * - Field declarations
 * - Parameter declarations
 * - Variable declarations
 * 
 * It creates nodes for each declaration and establishes structural relationships
 * between them (e.g., class has method, method has parameter).
 */
public class DeclarationVisitor extends VoidVisitorAdapter<VisitorContext> {
    
    private final NodeFactory nodeFactory;
    private final EdgeFactory edgeFactory;
    
    // Stack to track parent nodes for establishing relationships
    private final Deque<Node> parentStack = new ArrayDeque<>();
    
    /**
     * Creates a new declaration visitor.
     * 
     * @param graph the graph to add nodes to
     * @param context the visitor context
     */
    public DeclarationVisitor(Graph graph, VisitorContext context) {
        this.nodeFactory = new NodeFactory(graph, context);
        this.edgeFactory = new EdgeFactory(graph, context);
    }
    
    /**
     * Visits a CompilationUnit (root of a Java file).
     */
    @Override
    public void visit(CompilationUnit cu, VisitorContext context) {
        super.visit(cu, context);
        
        // Process package declaration
        cu.getPackageDeclaration().ifPresent(pkg -> {
            PackageNode pkgNode = nodeFactory.createPackage(pkg);
            if (pkgNode != null) {
                parentStack.push(pkgNode);
            }
        });
        
        // Process imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            context.addImport(importDecl.getName().toString());
        }
        
        // Process types (classes, interfaces, enums)
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            typeDecl.accept(this, context);
        }
        
        parentStack.clear();
    }
    
    /**
     * Visits a PackageDeclaration.
     */
    @Override
    public void visit(PackageDeclaration pkg, VisitorContext context) {
        super.visit(pkg, context);
        
        PackageNode pkgNode = nodeFactory.createPackage(pkg);
        if (pkgNode != null) {
            parentStack.push(pkgNode);
        }
    }
    
    /**
     * Visits a ClassOrInterfaceDeclaration.
     */
    @Override
    public void visit(ClassOrInterfaceDeclaration classDecl, VisitorContext context) {
        super.visit(classDecl, context);
        
        // Create the class node
        ClassNode classNode = nodeFactory.createClass(classDecl);
        if (classNode == null) {
            return;
        }
        
        // Push class onto parent stack
        parentStack.push(classNode);
        
        // Process class members (fields, methods, nested classes)
        for (BodyDeclaration<?> member : classDecl.getMembers()) {
            member.accept(this, context);
        }
        
        // Create INHERITS edges for superclass
        if (classNode.hasSuperclass()) {
            String superclassName = classNode.getSuperclass();
            String superclassId = NodeIdGenerator.forDeclaration(
                NodeIdGenerator.NodeType.CLASS, superclassName
            );
            // Note: The superclass node may not exist yet, but we'll create the edge anyway
            // The edge will be validated when we try to resolve symbols in Phase 3
        }
        
        // Create IMPLEMENTS edges for interfaces
        for (String ifaceName : classNode.getInterfaces()) {
            String ifaceId = NodeIdGenerator.forDeclaration(
                NodeIdGenerator.NodeType.CLASS, ifaceName
            );
            edgeFactory.createImplements(classNode.getId(), ifaceId);
        }
        
        // Pop class from parent stack
        parentStack.pop();
    }
    
    /**
     * Visits a MethodDeclaration.
     */
    @Override
    public void visit(MethodDeclaration methodDecl, VisitorContext context) {
        super.visit(methodDecl, context);
        
        // Create the method node
        MethodNode methodNode = nodeFactory.createMethod(methodDecl);
        if (methodNode == null) {
            return;
        }
        
        // Push method onto parent stack
        parentStack.push(methodNode);
        
        // Create HAS_METHOD edge from current class to this method
        Node parent = getCurrentClass();
        if (parent instanceof ClassNode) {
            edgeFactory.createHasMethod(parent.getId(), methodNode.getId());
        }
        
        // Process parameters
        int paramIndex = 0;
        for (Parameter param : methodDecl.getParameters()) {
            ParameterNode paramNode = nodeFactory.createParameter(param);
            if (paramNode != null) {
                // Create HAS_PARAMETER edge
                edgeFactory.createHasParameter(
                    methodNode.getId(), paramNode.getId(), paramIndex
                );
                paramIndex++;
            }
            param.accept(this, context);
        }
        
        // Process method body (will be handled by StatementVisitor)
        methodDecl.getBody().ifPresent(body -> {
            body.accept(this, context);
        });
        
        // Pop method from parent stack
        parentStack.pop();
    }
    
    /**
     * Visits a FieldDeclaration.
     */
    @Override
    public void visit(FieldDeclaration fieldDecl, VisitorContext context) {
        super.visit(fieldDecl, context);
        
        // Create field node(s) - one for each variable declarator
        for (VariableDeclarator var : fieldDecl.getVariables()) {
            FieldNode fieldNode = (FieldNode) nodeFactory.createField(fieldDecl);
            if (fieldNode != null) {
                // Create HAS_FIELD edge from current class to this field
                Node parent = getCurrentClass();
                if (parent instanceof ClassNode) {
                    edgeFactory.createHasField(parent.getId(), fieldNode.getId());
                }
            }
        }
    }
    
    /**
     * Visits a Parameter.
     */
    @Override
    public void visit(Parameter param, VisitorContext context) {
        super.visit(param, context);
        
        // Parameter is handled by MethodDeclaration visitor
    }
    
    /**
     * Visits a VariableDeclarator (for local variables).
     */
    @Override
    public void visit(VariableDeclarator varDecl, VisitorContext context) {
        super.visit(varDecl, context);
        
        // Check if this is a field declaration (already handled)
        Node parent = varDecl.getParentNode().orElse(null);
        if (parent instanceof FieldDeclaration) {
            return; // Already handled by FieldDeclaration visitor
        }
        
        // Create local variable node
        VariableNode varNode = nodeFactory.createVariable(varDecl);
        if (varNode != null && context.inMethod()) {
            // Local variable is in a method
            Node methodNode = getCurrentMethod();
            if (methodNode != null) {
                // Could create a DECLARES edge
            }
        }
    }
    
    /**
     * Visits an EnumDeclaration.
     */
    @Override
    public void visit(EnumDeclaration enumDecl, VisitorContext context) {
        super.visit(enumDecl, context);
        
        // For now, treat enums as classes
        // Would need to create a dedicated EnumNode class in the future
    }
    
    /**
     * Visits an AnnotationDeclaration.
     */
    @Override
    public void visit(AnnotationDeclaration annotationDecl, VisitorContext context) {
        super.visit(annotationDecl, context);
        
        // For now, skip annotation declarations
        // Would need to create AnnotationNode class in the future
    }
    
    /**
     * Returns the current class from the parent stack.
     */
    private Node getCurrentClass() {
        for (Node node : parentStack) {
            if (node instanceof ClassNode) {
                return node;
            }
        }
        return null;
    }
    
    /**
     * Returns the current method from the parent stack.
     */
    private Node getCurrentMethod() {
        for (Node node : parentStack) {
            if (node instanceof MethodNode) {
                return node;
            }
        }
        return null;
    }
    
    /**
     * Returns the node factory.
     */
    public NodeFactory getNodeFactory() {
        return nodeFactory;
    }
    
    /**
     * Returns the edge factory.
     */
    public EdgeFactory getEdgeFactory() {
        return edgeFactory;
    }
}
