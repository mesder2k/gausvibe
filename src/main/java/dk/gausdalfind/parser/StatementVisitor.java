package dk.gausdalfind.parser;

import dk.gausdalfind.model.Graph;

/**
 * Visitor for processing statement nodes in the JavaParser AST.
 *
 * StatementVisitor extends ExpressionVisitor so that a single default
 * traversal covers statements AND the expressions inside them: each node
 * is visited exactly once, and ExpressionVisitor's overrides (call-site
 * recording) fire for every method call in a method body.
 *
 * This class exists to keep the builder's construction sites unchanged;
 * it adds no overrides of its own. Previously every statement override
 * called super.visit AND manually re-accepted its children, which made
 * nested statements traverse exponentially (2^depth) and caused large
 * projects to effectively hang during parsing.
 */
public class StatementVisitor extends ExpressionVisitor {

    /**
     * Creates a new statement visitor.
     *
     * @param graph the graph to add nodes to
     * @param context the visitor context
     */
    public StatementVisitor(Graph graph, VisitorContext context) {
        super(graph, context);
    }
}
