package dk.gausdalfind.parser;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Node;

/**
 * Visitor for processing expression nodes in the JavaParser AST.
 *
 * Traversal contract: super.visit performs the single traversal of children;
 * overridden methods only ADD work (e.g. recording call sites). Overridden
 * methods must not manually re-accept children that super.visit already
 * visits - re-accepting children made nested statements traverse
 * exponentially (2^depth) and made large builds effectively hang.
 */
public class ExpressionVisitor extends VoidVisitorAdapter<VisitorContext> {

    protected final Graph graph;
    protected final NodeFactory nodeFactory;
    protected final EdgeFactory edgeFactory;

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
     * Visits a MethodCallExpr: records the call site for post-build
     * CALLS edge resolution.
     */
    @Override
    public void visit(MethodCallExpr methodCall, VisitorContext context) {
        super.visit(methodCall, context);
        recordCallSite(methodCall, context);
    }

    /**
     * Records a method call site so a CALLS edge can be created once the
     * whole graph is built (the callee's class may live in another file).
     */
    private void recordCallSite(MethodCallExpr methodCall, VisitorContext context) {
        String callerMethodId = context.getCurrentMethodId();
        if (callerMethodId == null || callerMethodId.isBlank()) {
            return; // call outside any method body (field initializer, static block)
        }
        String methodName = methodCall.getName().toString();
        String receiverType = resolveReceiverType(methodCall.getScope().orElse(null), context);
        context.recordCall(new VisitorContext.CallRecord(
            callerMethodId, context.getCurrentClass(), receiverType,
            methodName, false, context.getCurrentFile()));
    }

    /**
     * Visits an ObjectCreationExpr (new): records the constructor call site.
     */
    @Override
    public void visit(ObjectCreationExpr objCreation, VisitorContext context) {
        super.visit(objCreation, context);

        String callerMethodId = context.getCurrentMethodId();
        if (callerMethodId != null && !callerMethodId.isBlank()) {
            String typeName = objCreation.getType().getName().toString();
            String simpleName = typeName.contains(".")
                ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
            context.recordCall(new VisitorContext.CallRecord(
                callerMethodId, context.getCurrentClass(), typeName,
                simpleName, true, context.getCurrentFile()));
        }
    }

    /**
     * Visits a LambdaExpr: parameters are dispatched to the
     * DeclarationVisitor so ParameterNodes are created; the body continues
     * in this visitor. super.visit is NOT called to avoid traversing
     * parameters and body twice.
     */
    @Override
    public void visit(LambdaExpr lambdaExpr, VisitorContext context) {
        for (Parameter param : lambdaExpr.getParameters()) {
            param.accept(new DeclarationVisitor(graph, context), context);
        }
        lambdaExpr.getBody().accept(this, context);
    }

    /**
     * Visits a VariableDeclarationExpr (local variable declaration):
     * creates the VariableNode first (so later receiver lookups resolve),
     * then visits initializer expressions for call recording.
     * super.visit is NOT called to avoid traversing declarators twice.
     */
    @Override
    public void visit(VariableDeclarationExpr varDeclExpr, VisitorContext context) {
        for (VariableDeclarator var : varDeclExpr.getVariables()) {
            var.accept(new DeclarationVisitor(graph, context), context);
            var.getInitializer().ifPresent(init -> init.accept(this, context));
        }
    }

    /**
     * Best-effort resolution of the receiver's declared type name.
     * Returns null when the receiver cannot be resolved statically.
     */
    private String resolveReceiverType(Expression scope, VisitorContext context) {
        if (scope == null) {
            return null; // unqualified call: try own class, then unique name match
        }
        if (scope instanceof ThisExpr) {
            return context.getCurrentClass();
        }
        if (scope instanceof NameExpr) {
            String name = ((NameExpr) scope).getName().toString();
            String type = symbolType(lookupSymbol(name, context));
            if (type != null) return type;
            // no local symbol: could be a class name (static call)
            return name;
        }
        if (scope instanceof FieldAccessExpr) {
            String name = ((FieldAccessExpr) scope).getName().toString();
            return symbolType(lookupSymbol(name, context));
        }
        if (scope instanceof SuperExpr) {
            return null;
        }
        // chained calls, casts, etc.: unresolved
        return null;
    }

    /**
     * Looks up a symbol by name, in the lexical scope first, then in the
     * file-level symbol table (where variables, parameters and fields are
     * registered by NodeFactory).
     */
    private Node lookupSymbol(String name, VisitorContext context) {
        Node symbol = context.getCurrentScope().getSymbol(name);
        if (symbol != null) {
            return symbol;
        }
        return context.getLocalSymbols().get(name);
    }

    /**
     * Returns the declared type name of a variable/field symbol.
     */
    private String symbolType(Node symbol) {
        if (symbol instanceof dk.gausdalfind.model.declaration.VariableNode) {
            return ((dk.gausdalfind.model.declaration.VariableNode) symbol).getDataType();
        }
        if (symbol instanceof dk.gausdalfind.model.declaration.FieldNode) {
            return ((dk.gausdalfind.model.declaration.FieldNode) symbol).getDataType();
        }
        if (symbol instanceof dk.gausdalfind.model.declaration.ParameterNode) {
            return ((dk.gausdalfind.model.declaration.ParameterNode) symbol).getDataType();
        }
        return null;
    }
}
