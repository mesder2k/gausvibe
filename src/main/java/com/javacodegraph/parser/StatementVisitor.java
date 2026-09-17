package com.javacodegraph.parser;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.javacodegraph.model.*;

import java.util.*;

/**
 * Visitor for processing statement nodes in the JavaParser AST.
 * 
 * This visitor handles:
 * - Block statements
 * - If statements
 * - For/While/Do loops
 * - Try-Catch-Finally
 * - Return statements
 * - Throw statements
 * - Break/Continue statements
 * - Switch statements
 * - Synchronized statements
 * - Expression statements
 * - Variable declaration statements
 * 
 * It creates nodes for each statement and establishes structural relationships
 * (e.g., block contains statement, if has condition/then/else branches).
 */
public class StatementVisitor extends VoidVisitorAdapter<VisitorContext> {
    
    private final Graph graph;
    private final NodeFactory nodeFactory;
    private final EdgeFactory edgeFactory;
    
    // Stack to track block/parent nodes
    private final Deque<Node> parentStack = new ArrayDeque<>();
    
    // Counter for statement indexing within blocks
    private final Deque<Integer> statementCounter = new ArrayDeque<>();
    
    /**
     * Creates a new statement visitor.
     * 
     * @param graph the graph to add nodes to
     * @param context the visitor context
     */
    public StatementVisitor(Graph graph, VisitorContext context) {
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
     * Visits a BlockStmt.
     */
    @Override
    public void visit(BlockStmt block, VisitorContext context) {
        super.visit(block, context);
        
        // Create block node (will be implemented in future phases)
        // For now, just track the block and process its statements
        
        parentStack.push(null); // Push null as placeholder for block
        statementCounter.push(0);
        
        // Process all statements in the block
        for (Statement stmt : block.getStatements()) {
            stmt.accept(this, context);
            statementCounter.push(statementCounter.pop() + 1);
        }
        
        statementCounter.pop();
        parentStack.pop();
    }
    
    /**
     * Visits an IfStmt.
     */
    @Override
    public void visit(IfStmt ifStmt, VisitorContext context) {
        super.visit(ifStmt, context);
        
        // Process condition
        ifStmt.getCondition().ifPresent(condition -> {
            condition.accept(new ExpressionVisitor(graph, context), context);
        });
        
        // Process then branch
        ifStmt.getThenStmt().ifPresent(thenStmt -> {
            thenStmt.accept(this, context);
        });
        
        // Process else branch
        ifStmt.getElseStmt().ifPresent(elseStmt -> {
            elseStmt.accept(this, context);
        });
    }
    
    /**
     * Visits a ForStmt.
     */
    @Override
    public void visit(ForStmt forStmt, VisitorContext context) {
        super.visit(forStmt, context);
        
        // Process initialization
        forStmt.getInitialization().forEach(initialization -> {
            initialization.accept(this, context);
        });
        
        // Process condition
        forStmt.getCompare().ifPresent(condition -> {
            condition.accept(new ExpressionVisitor(graph, context), context);
        });
        
        // Process update
        forStmt.getUpdate().forEach(updateExpression -> {
            updateExpression.accept(this, context);
        });
        
        // Process body
        forStmt.getBody().ifPresent(body -> {
            body.accept(this, context);
        });
    }
    
    /**
     * Visits a WhileStmt.
     */
    @Override
    public void visit(WhileStmt whileStmt, VisitorContext context) {
        super.visit(whileStmt, context);
        
        // Process condition
        whileStmt.getCondition().ifPresent(condition -> {
            condition.accept(new ExpressionVisitor(graph, context), context);
        });
        
        // Process body
        whileStmt.getBody().ifPresent(body -> {
            body.accept(this, context);
        });
    }
    
    /**
     * Visits a DoStmt.
     */
    @Override
    public void visit(DoStmt doStmt, VisitorContext context) {
        super.visit(doStmt, context);
        
        // Process body
        doStmt.getBody().ifPresent(body -> {
            body.accept(this, context);
        });
        
        // Process condition
        doStmt.getCondition().ifPresent(condition -> {
            condition.accept(new ExpressionVisitor(graph, context), context);
        });
    }
    
    /**
     * Visits a TryStmt.
     */
    @Override
    public void visit(TryStmt tryStmt, VisitorContext context) {
        super.visit(tryStmt, context);
        
        // Process try block
        tryStmt.getTryBlock().ifPresent(block -> {
            block.accept(this, context);
        });
        
        // Process catch clauses
        for (CatchClause catchClause : tryStmt.getCatchClauses()) {
            catchClause.accept(this, context);
        }
        
        // Process finally block
        tryStmt.getFinallyBlock().ifPresent(block -> {
            block.accept(this, context);
        });
    }
    
    /**
     * Visits a CatchClause.
     */
    @Override
    public void visit(CatchClause catchClause, VisitorContext context) {
        super.visit(catchClause, context);
        
        // Process catch block
        catchClause.getBody().accept(this, context);
    }
    
    /**
     * Visits a SwitchStmt.
     */
    @Override
    public void visit(SwitchStmt switchStmt, VisitorContext context) {
        super.visit(switchStmt, context);
        
        // Process selector (expression)
        switchStmt.getSelector().ifPresent(selector -> {
            selector.accept(new ExpressionVisitor(graph, context), context);
        });
        
        // Process entries (cases)
        for (SwitchEntry entry : switchStmt.getEntries()) {
            entry.accept(this, context);
        }
    }
    
    /**
     * Visits a SwitchEntry.
     */
    @Override
    public void visit(SwitchEntry switchEntry, VisitorContext context) {
        super.visit(switchEntry, context);
        
        // Process statements in the case
        for (Statement stmt : switchEntry.getStatements()) {
            stmt.accept(this, context);
        }
    }
    
    /**
     * Visits a SynchronizedStmt.
     */
    @Override
    public void visit(SynchronizedStmt syncStmt, VisitorContext context) {
        super.visit(syncStmt, context);
        
        // Process expression (lock object)
        syncStmt.getExpression().ifPresent(expression -> {
            expression.accept(new ExpressionVisitor(graph, context), context);
        });
        
        // Process body
        syncStmt.getBody().ifPresent(body -> {
            body.accept(this, context);
        });
    }
    
    /**
     * Visits a ReturnStmt.
     */
    @Override
    public void visit(ReturnStmt returnStmt, VisitorContext context) {
        super.visit(returnStmt, context);
        
        // Process return expression
        returnStmt.getExpression().ifPresent(expression -> {
            expression.accept(new ExpressionVisitor(graph, context), context);
        });
    }
    
    /**
     * Visits a ThrowStmt.
     */
    @Override
    public void visit(ThrowStmt throwStmt, VisitorContext context) {
        super.visit(throwStmt, context);
        
        // Process thrown expression
        throwStmt.getExpression().ifPresent(expression -> {
            expression.accept(new ExpressionVisitor(graph, context), context);
        });
    }
    
    /**
     * Visits a BreakStmt.
     */
    @Override
    public void visit(BreakStmt breakStmt, VisitorContext context) {
        super.visit(breakStmt, context);
    }
    
    /**
     * Visits a ContinueStmt.
     */
    @Override
    public void visit(ContinueStmt continueStmt, VisitorContext context) {
        super.visit(continueStmt, context);
    }
    
    /**
     * Visits a LabeledStmt.
     */
    @Override
    public void visit(LabeledStmt labeledStmt, VisitorContext context) {
        super.visit(labeledStmt, context);
        
        // Process statement
        labeledStmt.getStatement().accept(this, context);
    }
    
    /**
     * Visits an ExpressionStmt.
     */
    @Override
    public void visit(ExpressionStmt exprStmt, VisitorContext context) {
        super.visit(exprStmt, context);
        
        // Process expression
        exprStmt.getExpression().ifPresent(expression -> {
            expression.accept(new ExpressionVisitor(graph, context), context);
        });
    }
    
    /**
     * Visits a VariableDeclarationExpr (local variable declaration).
     */
    @Override
    public void visit(VariableDeclarationExpr varDeclExpr, VisitorContext context) {
        super.visit(varDeclExpr, context);
        
        // Process each variable declarator
        for (VariableDeclarator var : varDeclExpr.getVariables()) {
            var.accept(new DeclarationVisitor(graph, context), context);
        }
    }
    
    /**
     * Returns the graph being built.
     */
    public Graph getGraph() {
        return graph;
    }
}
