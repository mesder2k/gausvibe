package dk.gausdalfind.model;

import java.nio.file.Path;
import java.util.Map;

/**
 * Base interface for all nodes in the Java code graph.
 * 
 * Nodes represent various elements in Java source code:
 * - Declarations (classes, methods, fields, parameters, variables, packages)
 * - Statements (if, for, while, return, etc.)
 * - Expressions (method calls, literals, binary operations, etc.)
 */
public interface Node {
    
    /**
     * Returns the unique identifier for this node.
     * ID format varies by node type (see NodeIdGenerator).
     */
    String getId();
    
    /**
     * Returns the type of this node (e.g., "CLASS", "METHOD", "FIELD").
     * Type names match the constants defined in this interface.
     */
    String getType();
    
    /**
     * Returns the file path where this node is defined.
     */
    Path getFile();
    
    /**
     * Returns the start position of this node in the source file.
     */
    Position getStartPosition();
    
    /**
     * Returns the end position of this node in the source file.
     */
    Position getEndPosition();
    
    /**
     * Returns additional properties of this node as a map.
     * Properties vary by node type.
     */
    Map<String, Object> getProperties();
    
    /**
     * Node type constants for declarations.
     */
    String TYPE_PACKAGE = "PACKAGE";
    String TYPE_CLASS = "CLASS";
    String TYPE_METHOD = "METHOD";
    String TYPE_FIELD = "FIELD";
    String TYPE_PARAMETER = "PARAMETER";
    String TYPE_VARIABLE = "VARIABLE";
    
    /**
     * Node type constants for statements.
     */
    String TYPE_BLOCK = "BLOCK";
    String TYPE_IF = "IF";
    String TYPE_FOR = "FOR";
    String TYPE_WHILE = "WHILE";
    String TYPE_DO = "DO";
    String TYPE_SWITCH = "SWITCH";
    String TYPE_TRY = "TRY";
    String TYPE_CATCH_CLAUSE = "CATCH_CLAUSE";
    String TYPE_SYNCHRONIZED = "SYNCHRONIZED";
    String TYPE_RETURN = "RETURN";
    String TYPE_THROW = "THROW";
    String TYPE_BREAK = "BREAK";
    String TYPE_CONTINUE = "CONTINUE";
    String TYPE_LABELLED = "LABELED";
    String TYPE_EXPRESSION_STMT = "EXPRESSION_STMT";
    String TYPE_VARIABLE_DECL = "VARIABLE_DECL";
    
    /**
     * Node type constants for expressions.
     */
    String TYPE_LITERAL = "LITERAL";
    String TYPE_VARIABLE_REF = "VARIABLE_REF";
    String TYPE_FIELD_ACCESS = "FIELD_ACCESS";
    String TYPE_METHOD_CALL = "METHOD_CALL";
    String TYPE_NEW_CLASS = "NEW_CLASS";
    String TYPE_NEW_ARRAY = "NEW_ARRAY";
    String TYPE_ARRAY_ACCESS = "ARRAY_ACCESS";
    String TYPE_BINARY_OP = "BINARY_OP";
    String TYPE_UNARY_OP = "UNARY_OP";
    String TYPE_TERNARY = "TERNARY";
    String TYPE_CAST = "CAST";
    String TYPE_INSTANCEOF = "INSTANCEOF";
    String TYPE_LAMBDA = "LAMBDA";
    String TYPE_METHOD_REF = "METHOD_REF";
    String TYPE_THIS = "THIS";
    String TYPE_SUPER = "SUPER";
    String TYPE_PARENTHESES = "PARENTHESES";
    String TYPE_CLASS_EXPR = "CLASS_EXPR";
}
