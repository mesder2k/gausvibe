package com.javacodegraph.model.declaration;

import com.javacodegraph.model.Node;
import com.javacodegraph.model.NodeIdGenerator;
import com.javacodegraph.model.Position;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a method declaration in Java source code.
 * 
 * Properties:
 * - name: The method name (e.g., "add")
 * - signature: The full method signature (e.g., "add(int,int)")
 * - qualified_name: The fully qualified method name (e.g., "com.example.Calculator.add")
 * - return_type: The fully qualified return type name
 * - modifiers: Set of modifiers (public, private, static, final, etc.)
 * - is_constructor: true if this is a constructor
 * - is_static: true if this is a static method
 * - file: The source file path
 * - start/end: Position in source file
 */
public class MethodNode extends DeclarationNode {
    
    private final String signature;
    private final String returnType;
    private final Set<String> modifiers;
    private final boolean isConstructor;
    private final boolean isStatic;
    private final List<String> thrownExceptions;
    
    /**
     * Creates a new method node.
     * 
     * @param id the unique ID
     * @param name the method name
     * @param signature the full method signature
     * @param qualifiedName the fully qualified method name
     * @param returnType the fully qualified return type name
     * @param modifiers the set of modifiers
     * @param isConstructor true if this is a constructor
     * @param isStatic true if this is a static method
     * @param thrownExceptions list of fully qualified exception type names
     * @param file the source file path
     * @param startPosition the start position in the file
     * @param endPosition the end position in the file
     */
    public MethodNode(String id, String name, String signature, String qualifiedName,
                      String returnType, Set<String> modifiers, 
                      boolean isConstructor, boolean isStatic, List<String> thrownExceptions,
                      Path file, Position startPosition, Position endPosition) {
        super(id, name, qualifiedName, file, startPosition, endPosition);
        this.signature = signature != null ? signature : name + "()";
        this.returnType = returnType != null ? returnType : "void";
        this.modifiers = modifiers != null ? Set.copyOf(modifiers) : Set.of();
        this.isConstructor = isConstructor;
        this.isStatic = isStatic;
        this.thrownExceptions = thrownExceptions != null ? List.copyOf(thrownExceptions) : List.of();
    }
    
    @Override
    public String getType() {
        return Node.TYPE_METHOD;
    }
    
    /**
     * Returns the method signature.
     * Format: name(paramType1,paramType2,...)
     */
    public String getSignature() {
        return signature;
    }
    
    /**
     * Returns the fully qualified return type name.
     */
    public String getReturnType() {
        return returnType;
    }
    
    /**
     * Returns the set of modifiers for this method.
     */
    public Set<String> getModifiers() {
        return Collections.unmodifiableSet(modifiers);
    }
    
    /**
     * Returns true if this method has the specified modifier.
     */
    public boolean hasModifier(String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return false;
        }
        return modifiers.contains(modifier);
    }
    
    /**
     * Returns true if this is a constructor.
     */
    public boolean isConstructor() {
        return isConstructor;
    }
    
    /**
     * Returns true if this is a static method.
     */
    public boolean isStatic() {
        return isStatic;
    }
    
    /**
     * Returns true if this method is abstract.
     */
    public boolean isAbstract() {
        return modifiers.contains("abstract");
    }
    
    /**
     * Returns true if this method is final.
     */
    public boolean isFinal() {
        return modifiers.contains("final");
    }
    
    /**
     * Returns true if this method is public.
     */
    public boolean isPublic() {
        return modifiers.contains("public");
    }
    
    /**
     * Returns true if this method is private.
     */
    public boolean isPrivate() {
        return modifiers.contains("private");
    }
    
    /**
     * Returns true if this method is protected.
     */
    public boolean isProtected() {
        return modifiers.contains("protected");
    }
    
    /**
     * Returns the list of fully qualified exception type names that this method throws.
     */
    public List<String> getThrownExceptions() {
        return Collections.unmodifiableList(thrownExceptions);
    }
    
    /**
     * Returns true if this method throws any exceptions.
     */
    public boolean throwsExceptions() {
        return !thrownExceptions.isEmpty();
    }
    
    /**
     * Returns the class name that this method belongs to.
     */
    public String getClassName() {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return qualifiedName.substring(0, lastDot);
    }
    
    /**
     * Returns the package name of this method's class.
     */
    public String getPackageName() {
        String className = getClassName();
        if (className == null || className.isBlank()) {
            return null;
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return ""; // Default package
        }
        return className.substring(0, lastDot);
    }
}
