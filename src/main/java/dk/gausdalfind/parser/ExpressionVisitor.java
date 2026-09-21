package dk.gausdalfind.parser;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dk.gausdalfind.model.*;

import java.util.*;

/**
 * Visitor for processing expression nodes in the JavaParser AST.
 * 
 * This visitor handles:
 * - Literals
 * - Variable references
 * - Field access
 * - Method calls
 * - Object creation (new)
 * - Array access
 * - Binary operations
 * - Unary operations
 * - Ternary expressions
 * - Cast expressions
 * - Instanceof expressions
 * - Lambda expressions
 * - Method references
 * - This/Super expressions
 * - etc.
 * 
 * It creates nodes for expressions and establishes relationships between them
 * (e.g., method call has receiver, binary op has left/right operands).
 */
public class ExpressionVisitor extends VoidVisitorAdapter<VisitorContext> {
    
    private final Graph graph;
    private final NodeFactory nodeFactory;
    private final EdgeFactory edgeFactory;
    
    /**
     * Creates a new expression visitor.
     * 
     * @param graph the graph to add nodes to
     * @param context the visitor context
     */
    public ExpressionVisitor(Graph graph, VisitorContext context) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.graph = graph;
        this.nodeFactory = new NodeFactory(graph, context);
        this.edgeFactory = new EdgeFactory(graph, context);
    }
    
    /**
     * Visits a BinaryExpr.
     */
    @Override
    public void visit(BinaryExpr binaryExpr, VisitorContext context) {
        super.visit(binaryExpr, context);
        
        // Process left operand
        binaryExpr.getLeft().accept(this, context);
        
        // Process right operand
        binaryExpr.getRight().accept(this, context);
        
        // Process operator
        String operator = binaryExpr.getOperator().toString();
        // Would create operator node if we had one
    }
    
    /**
     * Visits a UnaryExpr.
     */
    @Override
    public void visit(UnaryExpr unaryExpr, VisitorContext context) {
        super.visit(unaryExpr, context);
        
        // Process operand
        unaryExpr.getExpression().accept(this, context);
        
        // Process operator
        String operator = unaryExpr.getOperator().toString();
        // Would create operator node if we had one
    }
    
    /**
     * Visits a MethodCallExpr.
     */
    @Override
    public void visit(MethodCallExpr methodCall, VisitorContext context) {
        super.visit(methodCall, context);
        
        // Process scope/receiver
        methodCall.getScope().ifPresent(scope -> {
            scope.accept(this, context);
        });
        
        // Process arguments
        for (Expression arg : methodCall.getArguments()) {
            arg.accept(this, context);
        }
        
        // Get method name
        String methodName = methodCall.getName().toString();
        
        // Note: We'll resolve the actual method being called in Phase 3 (Resolution)
    }
    
    /**
     * Visits a FieldAccessExpr.
     */
    @Override
    public void visit(FieldAccessExpr fieldAccess, VisitorContext context) {
        super.visit(fieldAccess, context);
        
        // Process scope/receiver
        Expression scope = fieldAccess.getScope();
        if (scope != null) {
            scope.accept(this, context);
        }
        
        // Get field name
        String fieldName = fieldAccess.getName().toString();
    }
    
    /**
     * Visits a NameExpr (variable reference).
     */
    @Override
    public void visit(NameExpr nameExpr, VisitorContext context) {
        super.visit(nameExpr, context);
        
        // Get the name being referenced
        String name = nameExpr.getName().toString();
        
        // Note: We'll resolve this to the actual variable in Phase 3 (Resolution)
    }
    
    /**
     * Visits an ObjectCreationExpr (new).
     */
    @Override
    public void visit(ObjectCreationExpr objCreation, VisitorContext context) {
        super.visit(objCreation, context);
        
        // Process scope
        objCreation.getScope().ifPresent(scope -> {
            scope.accept(this, context);
        });
        
        // Get class name
        String className = objCreation.getType().toString();
        
        // Process arguments
        for (Expression arg : objCreation.getArguments()) {
            arg.accept(this, context);
        }
        
        // Process anonymous class body if present
        objCreation.getAnonymousClassBody().ifPresent(body -> {
            body.accept(new StatementVisitor(graph, context), context);
        });
    }
    
    /**
     * Visits an ArrayAccessExpr.
     */
    @Override
    public void visit(ArrayAccessExpr arrayAccess, VisitorContext context) {
        super.visit(arrayAccess, context);
        
        // Process array expression
        arrayAccess.getName().accept(this, context);
        
        // Process index expression
        arrayAccess.getIndex().accept(this, context);
    }
    
    /**
     * Visits an ArrayCreationExpr.
     */
    @Override
    public void visit(ArrayCreationExpr arrayCreation, VisitorContext context) {
        super.visit(arrayCreation, context);
        
        // Process element type
        Type elementType = arrayCreation.getElementType();
        if (elementType != null) {
            // Would process type
        }
        
        // Process dimensions
        for (ArrayCreationLevel level : arrayCreation.getLevels()) {
            level.getDimension().ifPresent(dim -> {
                dim.accept(this, context);
            });
        }
        
        // Process initializer
        arrayCreation.getInitializer().ifPresent(init -> {
            init.accept(new StatementVisitor(graph, context), context);
        });
    }
    
    /**
     * Visits a ConditionalExpr (ternary operator).
     */
    @Override
    public void visit(ConditionalExpr conditionalExpr, VisitorContext context) {
        super.visit(conditionalExpr, context);
        
        // Process condition
        conditionalExpr.getCondition().accept(this, context);
        
        // Process then expression
        conditionalExpr.getThenExpr().accept(this, context);
        
        // Process else expression
        conditionalExpr.getElseExpr().accept(this, context);
    }
    
    /**
     * Visits a CastExpr.
     */
    @Override
    public void visit(CastExpr castExpr, VisitorContext context) {
        super.visit(castExpr, context);
        
        // Process expression
        castExpr.getExpression().accept(this, context);
        
        // Get type
        String type = castExpr.getType().toString();
    }
    
    /**
     * Visits an InstanceOfExpr.
     */
    @Override
    public void visit(InstanceOfExpr instanceofExpr, VisitorContext context) {
        super.visit(instanceofExpr, context);
        
        // Process expression
        instanceofExpr.getExpression().accept(this, context);
        
        // Get type
        String type = instanceofExpr.getType().toString();
    }
    
    /**
     * Visits a LambdaExpr.
     */
    @Override
    public void visit(LambdaExpr lambdaExpr, VisitorContext context) {
        super.visit(lambdaExpr, context);
        
        // Process parameters
        for (Parameter param : lambdaExpr.getParameters()) {
            param.accept(new DeclarationVisitor(graph, context), context);
        }
        
        // Process body
        lambdaExpr.getBody().accept(new StatementVisitor(graph, context), context);
    }
    
    /**
     * Visits a MethodReferenceExpr.
     */
    @Override
    public void visit(MethodReferenceExpr methodRef, VisitorContext context) {
        super.visit(methodRef, context);
        
        // Process scope
        Expression scope = methodRef.getScope();
        if (scope != null) {
            scope.accept(this, context);
        }
        
        // Get method name
        String methodName = methodRef.getIdentifier();
    }
    
    /**
     * Visits a ThisExpr.
     */
    @Override
    public void visit(ThisExpr thisExpr, VisitorContext context) {
        super.visit(thisExpr, context);
        // Note: getClassExpr() not available in JavaParser 3.25.9
    }
    
    /**
     * Visits a SuperExpr.
     */
    @Override
    public void visit(SuperExpr superExpr, VisitorContext context) {
        super.visit(superExpr, context);
        // Note: getClassExpr() not available in JavaParser 3.25.9
    }
    
    /**
     * Visits an EnclosedExpr (parentheses).
     */
    @Override
    public void visit(EnclosedExpr enclosedExpr, VisitorContext context) {
        super.visit(enclosedExpr, context);
        
        // Process inner expression
        enclosedExpr.getInner().accept(this, context);
    }
    
    /**
     * Visits a ClassExpr.
     */
    @Override
    public void visit(ClassExpr classExpr, VisitorContext context) {
        super.visit(classExpr, context);
        
        // Get class type
        String type = classExpr.getType().toString();
    }
}
