package com.javacodegraph.model;

import java.nio.file.Path;

/**
 * Generates unique IDs for nodes in the Java code graph.
 * 
 * ID formats:
 * - Declarations: {prefix}:{qualified_name}
 * - AST nodes: {prefix}:{file}:{line}:{column}
 * 
 * This ensures globally unique identifiers across the entire codebase.
 */
public final class NodeIdGenerator {
    
    private NodeIdGenerator() {
        // Prevent instantiation
    }
    
    /**
     * Node type prefixes for declaration nodes.
     */
    public enum NodeType {
        PACKAGE("pkg"),
        CLASS("cls"),
        METHOD("mth"),
        FIELD("fld"),
        PARAMETER("par"),
        VARIABLE("var"),
        
        // Statement types
        BLOCK("blk"),
        IF("if"),
        FOR("for"),
        WHILE("while"),
        DO("do"),
        SWITCH("switch"),
        TRY("try"),
        CATCH_CLAUSE("catch"),
        SYNCHRONIZED("sync"),
        RETURN("return"),
        THROW("throw"),
        BREAK("break"),
        CONTINUE("continue"),
        LABELED("labeled"),
        EXPRESSION_STMT("expr_stmt"),
        VARIABLE_DECL("var_decl"),
        
        // Expression types
        LITERAL("lit"),
        VARIABLE_REF("var_ref"),
        FIELD_ACCESS("field_acc"),
        METHOD_CALL("call"),
        NEW_CLASS("new"),
        NEW_ARRAY("new_arr"),
        ARRAY_ACCESS("arr_acc"),
        BINARY_OP("bin_op"),
        UNARY_OP("un_op"),
        TERNARY("ternary"),
        CAST("cast"),
        INSTANCEOF("instanceof"),
        LAMBDA("lambda"),
        METHOD_REF("mref"),
        THIS("this"),
        SUPER("super"),
        PARENTHESES("parens"),
        CLASS_EXPR("class_expr");
        
        private final String prefix;
        
        NodeType(String prefix) {
            this.prefix = prefix;
        }
        
        public String getPrefix() {
            return prefix;
        }
    }
    
    /**
     * Generates an ID for a declaration node.
     * 
     * Format: {prefix}:{qualified_name_with_slashes}
     * 
     * @param type the node type
     * @param qualifiedName the fully qualified name (e.g., "com.example.Calculator")
     * @return a unique ID string
     */
    public static String forDeclaration(NodeType type, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new IllegalArgumentException("Qualified name cannot be null or blank");
        }
        // Replace dots with slashes for consistency
        String normalized = qualifiedName.replace('.', '/');
        return type.prefix + ":" + normalized;
    }
    
    /**
     * Generates an ID for an AST node (statement or expression).
     * 
     * Format: {prefix}:{file_path}:{line}:{column}
     * 
     * @param type the node type
     * @param file the source file path
     * @param position the position in the file
     * @return a unique ID string
     */
    public static String forAst(NodeType type, Path file, Position position) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        // Normalize file path: replace separators and special characters
        String fileKey = normalizeFilePath(file);
        return type.prefix + ":" + fileKey + ":" + position.line() + ":" + position.column();
    }
    
    /**
     * Generates an ID for an AST node with a signature.
     * Used for method calls and other nodes that need a signature component.
     * 
     * @param type the node type
     * @param file the source file path
     * @param position the position in the file
     * @param signature additional signature information
     * @return a unique ID string
     */
    public static String forAstWithSignature(NodeType type, Path file, Position position, String signature) {
        String baseId = forAst(type, file, position);
        if (signature != null && !signature.isBlank()) {
            return baseId + ":" + signature;
        }
        return baseId;
    }
    
    /**
     * Normalizes a file path for use in IDs.
     * Replaces path separators and special characters with underscores.
     */
    private static String normalizeFilePath(Path file) {
        String pathStr = file.toString();
        // Replace both Unix and Windows path separators
        pathStr = pathStr.replace('/', '_').replace('\\', '_');
        // Replace other problematic characters
        pathStr = pathStr.replace(':', '_').replace(' ', '_');
        return pathStr;
    }
    
    /**
     * Generates an ID for a parameter node.
     * 
     * @param methodQualifiedName the qualified name of the method
     * @param parameterName the name of the parameter
     * @param position the parameter position (0-indexed)
     * @return a unique ID string
     */
    public static String forParameter(String methodQualifiedName, String parameterName, int position) {
        String methodId = forDeclaration(NodeType.METHOD, methodQualifiedName);
        return NodeType.PARAMETER.prefix + ":" + methodQualifiedName.replace('.', '/') + ":" + position;
    }
    
    /**
     * Generates an ID for a variable node within a method.
     * 
     * @param file the source file path
     * @param methodName the method name
     * @param variableName the variable name
     * @return a unique ID string
     */
    public static String forVariable(Path file, String methodName, String variableName) {
        String fileKey = normalizeFilePath(file);
        return NodeType.VARIABLE.prefix + ":" + fileKey + ":" + methodName + ":" + variableName;
    }
}
