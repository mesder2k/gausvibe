package dk.gausdalfind.queries;

import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Interface for querying the Java code graph.
 * 
 * This interface provides methods for querying the graph to answer questions
 * about the Java code structure without using grep.
 * 
 * Query categories:
 * - Class queries
 * - Method queries
 * - Field queries
 * - Variable queries
 * - AST queries
 * - File queries
 * - Control flow queries
 */
public interface JavaGraphQuery {
    
    // ==================== CLASS QUERIES ====================
    
    /**
     * Finds a class by its fully qualified name.
     * 
     * @param qn the fully qualified class name
     * @return the class node, or empty if not found
     */
    Optional<ClassNode> findClassByQualifiedName(String qn);
    
    /**
     * Finds all classes with a given simple name.
     * Useful when you don't know the package.
     * 
     * @param name the simple class name
     * @return list of matching class nodes
     */
    List<ClassNode> findClassesByName(String name);
    
    /**
     * Returns all classes in the graph.
     */
    List<ClassNode> getAllClasses();
    
    /**
     * Returns all methods in the graph.
     */
    List<MethodNode> getAllMethods();
    
    /**
     * Returns all fields in the graph.
     */
    List<FieldNode> getAllFields();
    
    /**
     * Returns all direct subclasses of a class.
     */
    List<ClassNode> getSubclasses(ClassNode clazz);
    
    /**
     * Returns all implementations of an interface.
     */
    List<ClassNode> getImplementations(ClassNode iface);
    
    /**
     * Returns the superclass of a class, if any.
     */
    Optional<ClassNode> getSuperclass(ClassNode clazz);
    
    /**
     * Returns all interfaces that a class implements.
     */
    List<ClassNode> getInterfaces(ClassNode clazz);
    
    // ==================== METHOD QUERIES ====================
    
    /**
     * Finds a method by its fully qualified signature.
     * Signature format: "com.example.MyClass#method(int,java.lang.String)"
     * 
     * @param signature the fully qualified method signature
     * @return the method node, or empty if not found
     */
    Optional<MethodNode> findMethodBySignature(String signature);
    
    /**
     * Finds all methods with a given name.
     * 
     * @param name the method name
     * @return list of matching method nodes
     */
    List<MethodNode> findMethodsByName(String name);
    
    /**
     * Returns all methods in a class.
     */
    List<MethodNode> getMethods(ClassNode clazz);
    
    /**
     * Returns all methods that call the given method.
     */
    List<MethodNode> getCallers(MethodNode method);
    
    /**
     * Returns all methods called by the given method.
     */
    List<MethodNode> getCallees(MethodNode method);
    
    /**
     * Returns the method that this method overrides, if any.
     */
    Optional<MethodNode> getOverriddenMethod(MethodNode method);
    
    /**
     * Returns all methods that override this method.
     */
    List<MethodNode> getOverridingMethods(MethodNode method);
    
    /**
     * Returns all constructors for a class.
     */
    List<MethodNode> getConstructors(ClassNode clazz);
    
    /**
     * Returns all static methods in a class.
     */
    List<MethodNode> getStaticMethods(ClassNode clazz);
    
    /**
     * Returns all public methods in a class.
     */
    List<MethodNode> getPublicMethods(ClassNode clazz);
    
    // ==================== FIELD QUERIES ====================
    
    /**
     * Finds a field by its fully qualified name.
     * 
     * @param qn the fully qualified field name
     * @return the field node, or empty if not found
     */
    Optional<FieldNode> findFieldByQualifiedName(String qn);
    
    /**
     * Returns all fields in a class.
     */
    List<FieldNode> getFields(ClassNode clazz);
    
    /**
     * Returns all field accesses for a given field.
     * (Requires expression nodes to be fully implemented)
     */
    List<Node> getFieldAccesses(FieldNode field);
    
    /**
     * Returns all static fields in a class.
     */
    List<FieldNode> getStaticFields(ClassNode clazz);
    
    /**
     * Returns all final fields in a class.
     */
    List<FieldNode> getFinalFields(ClassNode clazz);
    
    // ==================== VARIABLE QUERIES ====================
    
    /**
     * Returns all local variables in a method.
     */
    List<VariableNode> getVariables(MethodNode method);
    
    /**
     * Returns all uses of a variable in a method.
     * (Requires expression nodes to be fully implemented)
     */
    List<Node> getVariableUses(VariableNode var);
    
    // ==================== AST QUERIES ====================
    
    /**
     * Returns all statement nodes in a method.
     * (Requires statement nodes to be fully implemented)
     */
    List<Node> getStatements(MethodNode method);
    
    /**
     * Returns the statement at a specific line in a method.
     * (Requires statement nodes to be fully implemented)
     */
    Optional<Node> getStatementAt(MethodNode method, int line);
    
    // ==================== FILE QUERIES ====================
    
    /**
     * Returns all nodes in a specific file.
     */
    List<Node> getNodesInFile(Path file);
    
    /**
     * Returns all classes in a specific file.
     */
    List<ClassNode> getClassesInFile(Path file);
    
    /**
     * Returns all methods in a specific file.
     */
    List<MethodNode> getMethodsInFile(Path file);
    
    // ==================== PACKAGE QUERIES ====================
    
    /**
     * Finds a package by name.
     */
    Optional<PackageNode> findPackageByName(String name);
    
    /**
     * Returns all packages in the graph.
     */
    List<PackageNode> getAllPackages();
    
    /**
     * Returns all classes in a package.
     */
    List<ClassNode> getClassesInPackage(String packageName);
    
    // ==================== CONTROL FLOW QUERIES ====================
    
    /**
     * Builds a control flow graph for a method.
     * (Will be fully implemented in future phases)
     */
    Graph buildCFG(MethodNode method);
    
    /**
     * Checks if a statement is reachable from another statement.
     * (Requires CFG to be built)
     */
    boolean isReachable(Node from, Node to);
    
    // ==================== SEARCH QUERIES ====================
    
    /**
     * Searches for nodes by type.
     */
    List<Node> getNodesByType(String type);
    
    /**
     * Searches for edges by type.
     */
    List<Edge> getEdgesByType(String type);
    
    /**
     * Returns all nodes with a specific modifier.
     */
    List<Node> getNodesWithModifier(String modifier);
    
    // ==================== STATISTICS QUERIES ====================
    
    /**
     * Returns the total number of nodes in the graph.
     */
    int getTotalNodeCount();
    
    /**
     * Returns the total number of edges in the graph.
     */
    int getTotalEdgeCount();
    
    /**
     * Returns the number of nodes of a specific type.
     */
    int getNodeCountByType(String type);
    
    /**
     * Returns the number of edges of a specific type.
     */
    int getEdgeCountByType(String type);
}
