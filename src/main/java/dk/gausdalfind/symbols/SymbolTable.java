package dk.gausdalfind.symbols;

import dk.gausdalfind.model.*;
import dk.gausdalfind.model.declaration.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global symbol table for resolving references in the Java code graph.
 * 
 * The symbol table maintains mappings from names to nodes, enabling efficient
 * lookup of classes, methods, fields, variables, and other symbols.
 * 
 * Resolution strategy:
 * 1. Check local symbols (in current method/block)
 * 2. Check class members (fields, methods)
 * 3. Check imported types
 * 4. Check current package
 * 5. Check java.lang package
 * 6. Check all loaded classes
 */
public class SymbolTable {
    
    // ==================== Class/Interface Symbols ====================
    
    /** Maps fully qualified class name to ClassNode */
    private final Map<String, ClassNode> classesByFqn = new ConcurrentHashMap<>();
    
    /** Maps simple class name to list of ClassNodes (for overload resolution) */
    private final Map<String, List<ClassNode>> classesByName = new ConcurrentHashMap<>();
    
    // ==================== Method Symbols ====================
    
    /** Maps method signature (FQN + signature) to MethodNode */
    private final Map<String, MethodNode> methodsBySignature = new ConcurrentHashMap<>();
    
    /** Maps method name to list of MethodNodes (for overload resolution) */
    private final Map<String, List<MethodNode>> methodsByName = new ConcurrentHashMap<>();
    
    // ==================== Field Symbols ====================
    
    /** Maps fully qualified field name to FieldNode */
    private final Map<String, FieldNode> fieldsByFqn = new ConcurrentHashMap<>();
    
    /** Maps field name to list of FieldNodes */
    private final Map<String, List<FieldNode>> fieldsByName = new ConcurrentHashMap<>();
    
    // ==================== Package Symbols ====================
    
    /** Maps package name to PackageNode */
    private final Map<String, PackageNode> packagesByName = new ConcurrentHashMap<>();
    
    // ==================== Variable Symbols ====================
    
    /** Maps variable name to VariableNode (local to current method) */
    private final Map<String, VariableNode> variablesByName = new ConcurrentHashMap<>();
    
    // ==================== File Information ====================
    
    /** Maps file path to its package name */
    private final Map<Path, String> packageByFile = new ConcurrentHashMap<>();
    
    /** Maps file path to its import statements */
    private final Map<Path, Set<String>> importsByFile = new ConcurrentHashMap<>();
    
    /** Maps file path to its class declarations */
    private final Map<Path, List<ClassNode>> classesByFile = new ConcurrentHashMap<>();
    
    // ==================== Inheritance Information ====================
    
    /** Maps class FQN to list of its subclasses */
    private final Map<String, List<ClassNode>> subclassesByClass = new ConcurrentHashMap<>();
    
    /** Maps class FQN to list of its direct subclasses */
    private final Map<String, List<ClassNode>> directSubclassesByClass = new ConcurrentHashMap<>();
    
    /** Maps interface FQN to list of implementing classes */
    private final Map<String, List<ClassNode>> implementationsByInterface = new ConcurrentHashMap<>();
    
    // ==================== Method Override Information ====================
    
    /** Maps method signature to list of overriding methods */
    private final Map<String, List<MethodNode>> overridesByMethod = new ConcurrentHashMap<>();
    
    /** Maps method signature to the method it overrides (if any) */
    private final Map<String, MethodNode> overriddenByMethod = new ConcurrentHashMap<>();
    
    // ==================== Primitive Types ====================
    
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "byte", "short", "int", "long", "float", "double", "char", "boolean", "void"
    );
    
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
        "java.lang.String", "java.lang.Object", "java.lang.Integer", "java.lang.Long",
        "java.lang.Double", "java.lang.Float", "java.lang.Character", "java.lang.Boolean",
        "java.lang.Byte", "java.lang.Short", "java.lang.Void"
    );
    
    // ==================== Registration Methods ====================
    
    /**
     * Registers a package in the symbol table.
     */
    public void register(PackageNode pkg) {
        if (pkg == null) {
            return;
        }
        packagesByName.put(pkg.getName(), pkg);
    }
    
    /**
     * Registers a class in the symbol table.
     */
    public void register(ClassNode cls) {
        if (cls == null) {
            return;
        }
        
        String fqn = cls.getQualifiedName();
        String name = cls.getName();
        
        classesByFqn.put(fqn, cls);
        classesByName.computeIfAbsent(name, k -> new ArrayList<>()).add(cls);
        
        // Update inheritance indexes
        if (cls.hasSuperclass()) {
            String superclass = cls.getSuperclass();
            subclassesByClass.computeIfAbsent(superclass, k -> new ArrayList<>()).add(cls);
            directSubclassesByClass.computeIfAbsent(superclass, k -> new ArrayList<>()).add(cls);
        }
        
        for (String iface : cls.getInterfaces()) {
            implementationsByInterface.computeIfAbsent(iface, k -> new ArrayList<>()).add(cls);
        }
        
        // Update file index
        Path file = cls.getFile();
        if (file != null) {
            classesByFile.computeIfAbsent(file, k -> new ArrayList<>()).add(cls);
        }
    }
    
    /**
     * Registers a method in the symbol table.
     */
    public void register(MethodNode method) {
        if (method == null) {
            return;
        }
        
        String signature = method.getSignature();
        String name = method.getName();
        String fullSig = method.getQualifiedName() + "#" + signature;
        
        methodsBySignature.put(fullSig, method);
        methodsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
    }
    
    /**
     * Registers a field in the symbol table.
     */
    public void register(FieldNode field) {
        if (field == null) {
            return;
        }
        
        String fqn = field.getQualifiedName();
        String name = field.getName();
        
        fieldsByFqn.put(fqn, field);
        fieldsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(field);
    }
    
    /**
     * Registers a variable in the symbol table.
     */
    public void register(VariableNode variable) {
        if (variable == null) {
            return;
        }
        
        String name = variable.getName();
        variablesByName.put(name, variable);
    }
    
    /**
     * Registers import statements for a file.
     */
    public void registerImports(Path file, Set<String> imports) {
        if (file == null || imports == null) {
            return;
        }
        importsByFile.put(file, new HashSet<>(imports));
    }
    
    /**
     * Registers the package for a file.
     */
    public void registerPackage(Path file, String packageName) {
        if (file == null || packageName == null) {
            return;
        }
        packageByFile.put(file, packageName);
    }
    
    /**
     * Registers an override relationship.
     */
    public void registerOverride(MethodNode overriding, MethodNode overridden) {
        if (overriding == null || overridden == null) {
            return;
        }
        
        String overriddenSig = overridden.getQualifiedName() + "#" + overridden.getSignature();
        overridesByMethod.computeIfAbsent(overriddenSig, k -> new ArrayList<>()).add(overriding);
        overriddenByMethod.put(overriding.getQualifiedName() + "#" + overriding.getSignature(), overridden);
    }
    
    // ==================== Resolution Methods ====================
    
    /**
     * Resolves a class by its fully qualified name.
     */
    public Optional<ClassNode> resolveClass(String name, Path file) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        
        // Already fully qualified?
        if (name.contains(".") || name.contains("/")) {
            return Optional.ofNullable(classesByFqn.get(name));
        }
        
        // Check primitive types
        if (isPrimitive(name)) {
            return Optional.empty(); // Primitives don't have ClassNode
        }
        
        // Try to resolve using imports
        Set<String> imports = importsByFile.getOrDefault(file, Set.of());
        for (String importStmt : imports) {
            if (importStmt.endsWith("." + name)) {
                return Optional.ofNullable(classesByFqn.get(importStmt));
            }
            if (importStmt.equals(name)) {
                return Optional.ofNullable(classesByFqn.get(importStmt));
            }
        }
        
        // Try current package
        String pkg = packageByFile.get(file);
        if (pkg != null && !pkg.isBlank()) {
            String fqn = pkg + "." + name;
            ClassNode cls = classesByFqn.get(fqn);
            if (cls != null) {
                return Optional.of(cls);
            }
        }
        
        // Try java.lang
        String javaLang = "java.lang." + name;
        ClassNode javaLangClass = classesByFqn.get(javaLang);
        if (javaLangClass != null) {
            return Optional.of(javaLangClass);
        }
        
        // Try all classes with this name
        List<ClassNode> candidates = classesByName.get(name);
        if (candidates != null && !candidates.isEmpty()) {
            // If there's only one, return it
            if (candidates.size() == 1) {
                return Optional.of(candidates.get(0));
            }
            // TODO: More sophisticated disambiguation based on imports/context
        }
        
        return Optional.empty();
    }
    
    /**
     * Resolves a method by its name and parameter types.
     */
    public Optional<MethodNode> resolveMethod(String name, List<String> paramTypes, ClassNode contextClass) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        
        // Build signature
        StringBuilder sigBuilder = new StringBuilder();
        sigBuilder.append(name).append("(");
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) {
                sigBuilder.append(",");
            }
            sigBuilder.append(paramTypes.get(i));
        }
        sigBuilder.append(")");
        String signature = sigBuilder.toString();
        
        // Try exact match with context class
        if (contextClass != null) {
            String fullSig = contextClass.getQualifiedName() + "." + name + "#" + signature;
            MethodNode method = methodsBySignature.get(fullSig);
            if (method != null) {
                return Optional.of(method);
            }
        }
        
        // Try by name only
        List<MethodNode> candidates = methodsByName.get(name);
        if (candidates != null) {
            // Filter by parameter count
            List<MethodNode> byParamCount = new ArrayList<>();
            for (MethodNode method : candidates) {
                // Count parameters from signature
                int paramCount = countParameters(method.getSignature());
                if (paramCount == paramTypes.size()) {
                    byParamCount.add(method);
                }
            }
            
            if (byParamCount.size() == 1) {
                return Optional.of(byParamCount.get(0));
            }
            
            // TODO: More sophisticated overload resolution
        }
        
        return Optional.empty();
    }
    
    /**
     * Resolves a field by its name within a class context.
     */
    public Optional<FieldNode> resolveField(String name, ClassNode contextClass) {
        if (name == null || name.isBlank() || contextClass == null) {
            return Optional.empty();
        }
        
        // Try exact match with context class
        String fqn = contextClass.getQualifiedName() + "." + name;
        FieldNode field = fieldsByFqn.get(fqn);
        if (field != null) {
            return Optional.of(field);
        }
        
        // Check all fields with this name
        List<FieldNode> candidates = fieldsByName.get(name);
        if (candidates != null) {
            // Filter by class
            for (FieldNode f : candidates) {
                if (contextClass.getQualifiedName().equals(f.getClassName())) {
                    return Optional.of(f);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Resolves a variable by its name within a method context.
     */
    public Optional<VariableNode> resolveVariable(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(variablesByName.get(name));
    }
    
    /**
     * Resolves a type name to a ClassNode.
     */
    public Optional<ClassNode> resolveType(String typeName, Path file) {
        return resolveClass(typeName, file);
    }
    
    // ==================== Inheritance Methods ====================
    
    /**
     * Returns all subclasses of a class.
     */
    public List<ClassNode> getSubclasses(String classFqn) {
        return Collections.unmodifiableList(
            subclassesByClass.getOrDefault(classFqn, Collections.emptyList())
        );
    }
    
    /**
     * Returns all direct subclasses of a class.
     */
    public List<ClassNode> getDirectSubclasses(String classFqn) {
        return Collections.unmodifiableList(
            directSubclassesByClass.getOrDefault(classFqn, Collections.emptyList())
        );
    }
    
    /**
     * Returns all implementations of an interface.
     */
    public List<ClassNode> getImplementations(String interfaceFqn) {
        return Collections.unmodifiableList(
            implementationsByInterface.getOrDefault(interfaceFqn, Collections.emptyList())
        );
    }
    
    /**
     * Returns the superclass of a class.
     */
    public Optional<ClassNode> getSuperclass(ClassNode cls) {
        if (cls == null || !cls.hasSuperclass()) {
            return Optional.empty();
        }
        return Optional.ofNullable(classesByFqn.get(cls.getSuperclass()));
    }
    
    /**
     * Returns all interfaces implemented by a class.
     */
    public List<ClassNode> getInterfaces(ClassNode cls) {
        if (cls == null) {
            return Collections.emptyList();
        }
        List<ClassNode> interfaces = new ArrayList<>();
        for (String ifaceFqn : cls.getInterfaces()) {
            ClassNode iface = classesByFqn.get(ifaceFqn);
            if (iface != null) {
                interfaces.add(iface);
            }
        }
        return Collections.unmodifiableList(interfaces);
    }
    
    /**
     * Returns the overridden method for a method, if any.
     */
    public Optional<MethodNode> getOverriddenMethod(MethodNode method) {
        if (method == null) {
            return Optional.empty();
        }
        String sig = method.getQualifiedName() + "#" + method.getSignature();
        return Optional.ofNullable(overriddenByMethod.get(sig));
    }
    
    /**
     * Returns all methods that override a given method.
     */
    public List<MethodNode> getOverridingMethods(MethodNode method) {
        if (method == null) {
            return Collections.emptyList();
        }
        String sig = method.getQualifiedName() + "#" + method.getSignature();
        return Collections.unmodifiableList(
            overridesByMethod.getOrDefault(sig, Collections.emptyList())
        );
    }
    
    // ==================== Lookup Methods ====================
    
    /**
     * Returns all classes in the symbol table.
     */
    public Collection<ClassNode> getAllClasses() {
        return Collections.unmodifiableCollection(classesByFqn.values());
    }
    
    /**
     * Returns all methods in the symbol table.
     */
    public Collection<MethodNode> getAllMethods() {
        return Collections.unmodifiableCollection(methodsBySignature.values());
    }
    
    /**
     * Returns all fields in the symbol table.
     */
    public Collection<FieldNode> getAllFields() {
        return Collections.unmodifiableCollection(fieldsByFqn.values());
    }
    
    /**
     * Returns all packages in the symbol table.
     */
    public Collection<PackageNode> getAllPackages() {
        return Collections.unmodifiableCollection(packagesByName.values());
    }
    
    /**
     * Returns all classes in a file.
     */
    public List<ClassNode> getClassesByFile(Path file) {
        return Collections.unmodifiableList(
            classesByFile.getOrDefault(file, Collections.emptyList())
        );
    }
    
    /**
     * Returns the package for a file.
     */
    public Optional<String> getPackageByFile(Path file) {
        return Optional.ofNullable(packageByFile.get(file));
    }
    
    /**
     * Returns the imports for a file.
     */
    public Set<String> getImportsByFile(Path file) {
        return Collections.unmodifiableSet(
            importsByFile.getOrDefault(file, Collections.emptySet())
        );
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Returns true if the type name is a primitive type.
     */
    public boolean isPrimitive(String typeName) {
        return PRIMITIVE_TYPES.contains(typeName);
    }
    
    /**
     * Returns true if the type name is a java.lang type.
     */
    public boolean isJavaLangType(String typeName) {
        return JAVA_LANG_TYPES.contains(typeName);
    }
    
    /**
     * Counts the number of parameters in a method signature.
     */
    private int countParameters(String signature) {
        if (signature == null || signature.isBlank()) {
            return 0;
        }
        int openParen = signature.indexOf('(');
        int closeParen = signature.lastIndexOf(')');
        if (openParen < 0 || closeParen < 0) {
            return 0;
        }
        String params = signature.substring(openParen + 1, closeParen);
        if (params.isBlank()) {
            return 0;
        }
        return params.split(",").length;
    }
    
    /**
     * Clears all entries from the symbol table.
     */
    public void clear() {
        classesByFqn.clear();
        classesByName.clear();
        methodsBySignature.clear();
        methodsByName.clear();
        fieldsByFqn.clear();
        fieldsByName.clear();
        packagesByName.clear();
        variablesByName.clear();
        packageByFile.clear();
        importsByFile.clear();
        classesByFile.clear();
        subclassesByClass.clear();
        directSubclassesByClass.clear();
        implementationsByInterface.clear();
        overridesByMethod.clear();
        overriddenByMethod.clear();
    }
    
    /**
     * Returns the number of classes in the symbol table.
     */
    public int getClassCount() {
        return classesByFqn.size();
    }
    
    /**
     * Returns the number of methods in the symbol table.
     */
    public int getMethodCount() {
        return methodsBySignature.size();
    }
    
    /**
     * Returns the number of fields in the symbol table.
     */
    public int getFieldCount() {
        return fieldsByFqn.size();
    }
    
    /**
     * Merges another symbol table into this one.
     * All symbols from the other table are added to this table.
     * If a symbol already exists, it is not replaced.
     * 
     * @param other the symbol table to merge into this one
     */
    public void merge(SymbolTable other) {
        if (other == null) {
            return;
        }
        
        // Merge classes
        for (Map.Entry<String, ClassNode> entry : other.classesByFqn.entrySet()) {
            classesByFqn.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<ClassNode>> entry : other.classesByName.entrySet()) {
            classesByName.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        
        // Merge methods
        for (Map.Entry<String, MethodNode> entry : other.methodsBySignature.entrySet()) {
            methodsBySignature.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<MethodNode>> entry : other.methodsByName.entrySet()) {
            methodsByName.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        
        // Merge fields
        for (Map.Entry<String, FieldNode> entry : other.fieldsByFqn.entrySet()) {
            fieldsByFqn.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<FieldNode>> entry : other.fieldsByName.entrySet()) {
            fieldsByName.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        
        // Merge packages
        for (Map.Entry<String, PackageNode> entry : other.packagesByName.entrySet()) {
            packagesByName.putIfAbsent(entry.getKey(), entry.getValue());
        }
        
        // Merge variables
        for (Map.Entry<String, VariableNode> entry : other.variablesByName.entrySet()) {
            variablesByName.putIfAbsent(entry.getKey(), entry.getValue());
        }
        
        // Merge file information
        for (Map.Entry<Path, String> entry : other.packageByFile.entrySet()) {
            packageByFile.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Path, Set<String>> entry : other.importsByFile.entrySet()) {
            importsByFile.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                .addAll(entry.getValue());
        }
        for (Map.Entry<Path, List<ClassNode>> entry : other.classesByFile.entrySet()) {
            classesByFile.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        
        // Merge inheritance information
        for (Map.Entry<String, List<ClassNode>> entry : other.subclassesByClass.entrySet()) {
            subclassesByClass.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        for (Map.Entry<String, List<ClassNode>> entry : other.directSubclassesByClass.entrySet()) {
            directSubclassesByClass.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        for (Map.Entry<String, List<ClassNode>> entry : other.implementationsByInterface.entrySet()) {
            implementationsByInterface.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        for (Map.Entry<String, List<MethodNode>> entry : other.overridesByMethod.entrySet()) {
            overridesByMethod.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
        for (Map.Entry<String, List<MethodNode>> entry : other.overriddenByMethod.entrySet()) {
            overriddenByMethod.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(entry.getValue());
        }
    }
}
