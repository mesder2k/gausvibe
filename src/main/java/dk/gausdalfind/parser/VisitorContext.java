package dk.gausdalfind.parser;

import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Node;

import java.nio.file.Path;
import java.util.*;

/**
 * Context object passed to visitors during AST traversal.
 * 
 * Holds state that is shared across all visitors, including:
 * - The graph being built
 * - The current file being processed
 * - The current class/interface/method scope
 * - The current package
 * - Stacks for tracking nested scopes
 * - Symbol resolution state
 */
public class VisitorContext {
    
    private final Graph graph;
    private final Path currentFile;
    private final Deque<String> packageStack;
    private final Deque<String> classStack;
    private final Deque<String> methodStack;
    private final Deque<Scope> scopeStack;
    private String currentPackage;
    private String currentClass;
    private String currentMethod;
    private int currentDepth;
    
    // Symbol table for the current file (will be merged into global table later)
    private final Map<String, Node> localSymbols;
    
    // Import statements for the current file
    private final Set<String> imports;
    
    /**
     * Creates a new visitor context.
     * 
     * @param file the current file being processed
     * @param graph the graph being built
     */
    public VisitorContext(Path file, Graph graph) {
        this(file, graph, null, null, null, 0, new Scope(null));
    }
    
    /**
     * Creates a new visitor context with full state.
     */
    public VisitorContext(Path file, Graph graph, String currentPackage, 
                          String currentClass, String currentMethod, 
                          int currentDepth, Scope initialScope) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        this.currentFile = file;
        this.graph = graph;
        this.currentPackage = currentPackage;
        this.currentClass = currentClass;
        this.currentMethod = currentMethod;
        this.currentDepth = currentDepth;
        
        this.packageStack = new ArrayDeque<>();
        this.classStack = new ArrayDeque<>();
        this.methodStack = new ArrayDeque<>();
        this.scopeStack = new ArrayDeque<>();
        this.scopeStack.push(initialScope);
        
        this.localSymbols = new HashMap<>();
        this.imports = new HashSet<>();
    }
    
    /**
     * Returns the graph being built.
     */
    public Graph getGraph() {
        return graph;
    }
    
    /**
     * Returns the current file being processed.
     */
    public Path getCurrentFile() {
        return currentFile;
    }
    
    /**
     * Returns the current package.
     */
    public String getCurrentPackage() {
        return currentPackage;
    }
    
    /**
     * Sets the current package.
     */
    public void setCurrentPackage(String currentPackage) {
        this.currentPackage = currentPackage;
    }
    
    /**
     * Returns the current class/interface.
     */
    public String getCurrentClass() {
        return currentClass;
    }
    
    /**
     * Sets the current class/interface.
     */
    public void setCurrentClass(String currentClass) {
        this.currentClass = currentClass;
    }
    
    /**
     * Returns the current method.
     */
    public String getCurrentMethod() {
        return currentMethod;
    }
    
    /**
     * Sets the current method.
     */
    public void setCurrentMethod(String currentMethod) {
        this.currentMethod = currentMethod;
    }
    
    /**
     * Returns the current depth in the AST.
     */
    public int getCurrentDepth() {
        return currentDepth;
    }
    
    /**
     * Increments the current depth.
     */
    public void incrementDepth() {
        currentDepth++;
    }
    
    /**
     * Decrements the current depth.
     */
    public void decrementDepth() {
        if (currentDepth > 0) {
            currentDepth--;
        }
    }
    
    /**
     * Returns the local symbols for the current file.
     */
    public Map<String, Node> getLocalSymbols() {
        return Collections.unmodifiableMap(localSymbols);
    }
    
    /**
     * Adds a symbol to the local symbol table.
     */
    public void addSymbol(String name, Node node) {
        if (name != null && !name.isBlank() && node != null) {
            localSymbols.put(name, node);
        }
    }
    
    /**
     * Returns the imports for the current file.
     */
    public Set<String> getImports() {
        return Collections.unmodifiableSet(imports);
    }
    
    /**
     * Adds an import statement.
     */
    public void addImport(String importStmt) {
        if (importStmt != null && !importStmt.isBlank()) {
            imports.add(importStmt);
        }
    }
    
    /**
     * Pushes a package onto the stack.
     */
    public void pushPackage(String pkg) {
        if (pkg != null && !pkg.isBlank()) {
            packageStack.push(pkg);
        }
    }
    
    /**
     * Pops a package from the stack.
     */
    public String popPackage() {
        return packageStack.poll();
    }
    
    /**
     * Pushes a class onto the stack.
     */
    public void pushClass(String cls) {
        if (cls != null && !cls.isBlank()) {
            classStack.push(cls);
        }
    }
    
    /**
     * Pops a class from the stack.
     */
    public String popClass() {
        return classStack.poll();
    }
    
    /**
     * Pushes a method onto the stack.
     */
    public void pushMethod(String method) {
        if (method != null && !method.isBlank()) {
            methodStack.push(method);
        }
    }
    
    /**
     * Pops a method from the stack.
     */
    public String popMethod() {
        return methodStack.poll();
    }
    
    /**
     * Pushes a scope onto the stack.
     */
    public void pushScope(Scope scope) {
        if (scope != null) {
            scopeStack.push(scope);
        }
    }
    
    /**
     * Pops a scope from the stack.
     */
    public Scope popScope() {
        Scope popped = scopeStack.poll();
        if (popped == null && !scopeStack.isEmpty()) {
            // Keep at least one scope
            return scopeStack.peek();
        }
        return popped;
    }
    
    /**
     * Returns the current scope.
     */
    public Scope getCurrentScope() {
        return scopeStack.peek();
    }
    
    /**
     * Returns true if currently inside a class.
     */
    public boolean inClass() {
        return currentClass != null && !currentClass.isBlank();
    }
    
    /**
     * Returns true if currently inside a method.
     */
    public boolean inMethod() {
        return currentMethod != null && !currentMethod.isBlank();
    }
    
    /**
     * Resolves a type name to a fully qualified name using imports and context.
     */
    public String resolveType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "";
        }
        
        // Handle primitive types and java.lang types
        if (isPrimitive(typeName) || typeName.startsWith("java.lang.")) {
            return typeName;
        }
        
        // Check if already fully qualified
        if (typeName.contains(".")) {
            return typeName;
        }
        
        // Try to resolve using imports
        for (String importStmt : imports) {
            if (importStmt.endsWith("." + typeName)) {
                return importStmt;
            }
        }
        
        // Use current package
        if (currentPackage != null && !currentPackage.isBlank()) {
            return currentPackage + "." + typeName;
        }
        
        return typeName;
    }
    
    /**
     * Returns true if the type name is a primitive type.
     */
    public boolean isPrimitive(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        return Set.of("byte", "short", "int", "long", "float", "double", 
                      "char", "boolean", "void").contains(typeName);
    }
    
    /**
     * Clears the context for reuse.
     */
    public void clear() {
        currentPackage = null;
        currentClass = null;
        currentMethod = null;
        currentDepth = 0;
        packageStack.clear();
        classStack.clear();
        methodStack.clear();
        scopeStack.clear();
        scopeStack.push(new Scope(null));
        localSymbols.clear();
        imports.clear();
    }
    
    /**
     * Represents a lexical scope (block, method, class, etc.).
     */
    public static class Scope {
        private final Scope parent;
        private final Map<String, Node> symbols;
        
        public Scope(Scope parent) {
            this.parent = parent;
            this.symbols = new HashMap<>();
        }
        
        public Scope getParent() {
            return parent;
        }
        
        public void addSymbol(String name, Node node) {
            if (name != null && !name.isBlank() && node != null) {
                symbols.put(name, node);
            }
        }
        
        public Node getSymbol(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            Node node = symbols.get(name);
            if (node != null) {
                return node;
            }
            if (parent != null) {
                return parent.getSymbol(name);
            }
            return null;
        }
        
        public boolean contains(String name) {
            return symbols.containsKey(name) || 
                   (parent != null && parent.contains(name));
        }
    }
}
