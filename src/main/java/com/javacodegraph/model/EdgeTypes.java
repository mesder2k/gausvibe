package com.javacodegraph.model;

/**
 * Constants for edge types in the Java code graph.
 * 
 * Edge types are categorized into:
 * - Structural (AST hierarchy)
 * - Expression edges
 * - Declaration edges
 * - Semantic edges (cross-references)
 */
public final class EdgeTypes {
    
    private EdgeTypes() {
        // Prevent instantiation
    }
    
    // ==================== STRUCTURAL EDGES (AST Hierarchy) ====================
    
    /** Statement is contained in a block */
    public static final String CONTAINS = "CONTAINS";
    
    /** Method/control structure has a body block */
    public static final String BODY = "BODY";
    
    /** Condition expression for if/while/for/switch */
    public static final String CONDITION = "CONDITION";
    
    /** Then branch of an if statement */
    public static final String THEN_BRANCH = "THEN_BRANCH";
    
    /** Else branch of an if statement */
    public static final String ELSE_BRANCH = "ELSE_BRANCH";
    
    /** For loop initialization */
    public static final String INIT = "INIT";
    
    /** For loop update expression */
    public static final String UPDATE = "UPDATE";
    
    /** Try block */
    public static final String TRY_BLOCK = "TRY_BLOCK";
    
    /** Catch clause in try */
    public static final String CATCH = "CATCH";
    
    /** Finally block in try */
    public static final String FINALLY_BLOCK = "FINALLY_BLOCK";
    
    /** Return value expression */
    public static final String RETURN_VALUE = "RETURN_VALUE";
    
    /** Thrown value expression */
    public static final String THROW_VALUE = "THROW_VALUE";
    
    /** Target statement for labeled statement */
    public static final String TARGET = "TARGET";
    
    /** Label name string */
    public static final String LABEL = "LABEL";
    
    /** Variable declaration declares a variable */
    public static final String DECLARES = "DECLARES";
    
    /** Variable initializer expression */
    public static final String INITIALIZER = "INITIALIZER";
    
    // ==================== EXPRESSION EDGES ====================
    
    /** Receiver of method call or field access */
    public static final String RECEIVER = "RECEIVER";
    
    /** Method name string for method call */
    public static final String METHOD_NAME = "METHOD_NAME";
    
    /** Field name string for field access */
    public static final String FIELD_NAME = "FIELD_NAME";
    
    /** Class name string for new class */
    public static final String CLASS_NAME = "CLASS_NAME";
    
    /** Argument in method call or constructor */
    public static final String ARGUMENT = "ARGUMENT";
    
    /** Array expression in array access */
    public static final String ARRAY = "ARRAY";
    
    /** Index expression in array access */
    public static final String INDEX = "INDEX";
    
    /** Left operand of binary operation */
    public static final String LEFT_OPERAND = "LEFT_OPERAND";
    
    /** Right operand of binary operation */
    public static final String RIGHT_OPERAND = "RIGHT_OPERAND";
    
    /** Operator string for binary/unary operation */
    public static final String OPERATOR = "OPERATOR";
    
    /** Operand for unary/cast/instanceof */
    public static final String OPERAND = "OPERAND";
    
    /** Type string for cast/instanceof */
    public static final String TYPE = "TYPE";
    
    /** Test expression in ternary */
    public static final String TEST = "TEST";
    
    /** Consequent expression in ternary */
    public static final String CONSEQUENT = "CONSEQUENT";
    
    /** Alternative expression in ternary */
    public static final String ALTERNATIVE = "ALTERNATIVE";
    
    // ==================== DECLARATION EDGES ====================
    
    /** Class has method */
    public static final String HAS_METHOD = "HAS_METHOD";
    
    /** Class has field */
    public static final String HAS_FIELD = "HAS_FIELD";
    
    /** Method has parameter */
    public static final String HAS_PARAMETER = "HAS_PARAMETER";
    
    /** Method returns type */
    public static final String RETURNS = "RETURNS";
    
    /** Method throws exception type */
    public static final String THROWS = "THROWS";
    
    /** Class inherits from superclass */
    public static final String INHERITS = "INHERITS";
    
    /** Class implements interface */
    public static final String IMPLEMENTS = "IMPLEMENTS";
    
    /** Interface extends interface */
    public static final String EXTENDS = "EXTENDS";
    
    // ==================== SEMANTIC EDGES (Cross-References) ====================
    
    /** Generic reference from expression to node */
    public static final String REFERENCES = "REFERENCES";
    
    /** Method call calls method */
    public static final String CALLS = "CALLS";
    
    /** New class calls constructor */
    public static final String CALLS_CONSTRUCTOR = "CALLS_CONSTRUCTOR";
    
    /** Field access accesses field */
    public static final String ACCESSES = "ACCESSES";
    
    /** New class creates instance of class */
    public static final String CREATES = "CREATES";
    
    /** Node references type */
    public static final String REFERENCES_TYPE = "REFERENCES_TYPE";
    
    /** Method overrides method */
    public static final String OVERRIDES = "OVERRIDES";
    
    /** Expression reads field */
    public static final String READS = "READS";
    
    /** Expression writes field */
    public static final String WRITES = "WRITES";
    
    /** File imports type */
    public static final String IMPORTS = "IMPORTS";
    
    /** Node is annotated with annotation */
    public static final String ANNOTATED_WITH = "ANNOTATED_WITH";
}
