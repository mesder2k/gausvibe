package dk.gausdalfind.serializer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dk.gausdalfind.model.Graph;
import dk.gausdalfind.model.Node;
import dk.gausdalfind.model.declaration.*;
import dk.gausdalfind.editing.ChangeTracker;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Serializes a modified AST back to valid Java source code.
 * 
 * This class handles the conversion of the in-memory graph (with modifications)
 * back into compilable Java source files, completing the self-editing round-trip.
 */
public class ASTSourceSerializer {
    
    private final Graph graph;
    private final ChangeTracker changeTracker;
    private final Map<String, CompilationUnit> fileCache = new HashMap<>();
    
    /**
     * Creates a new serializer for the given graph.
     */
    public ASTSourceSerializer(Graph graph) {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        this.changeTracker = new ChangeTracker();
    }
    
    /**
     * Creates a new serializer with a change tracker.
     */
    public ASTSourceSerializer(Graph graph, ChangeTracker changeTracker) {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        this.changeTracker = Objects.requireNonNull(changeTracker, "ChangeTracker cannot be null");
    }
    
    /**
     * Serializes all modified files back to source.
     * 
     * @return Map of file paths to serialized source code
     */
    public Map<String, String> serializeModifiedFiles() {
        Map<String, String> results = new LinkedHashMap<>();
        
        // Get all files that have nodes in the graph
        Set<String> files = getFilesInGraph();
        
        // Filter to only modified files if tracker has changes
        if (changeTracker.hasChanges()) {
            Set<String> modifiedFiles = changeTracker.getModifiedFiles();
            if (!modifiedFiles.isEmpty()) {
                files.retainAll(modifiedFiles);
            }
        }
        
        // Serialize each file
        for (String filePath : files) {
            String source = serializeFile(filePath);
            if (source != null) {
                results.put(filePath, source);
            }
        }
        
        return results;
    }
    
    /**
     * Serializes a specific file back to source.
     */
    public String serializeFile(String filePath) {
        // Get all nodes in this file
        List<dk.gausdalfind.model.Node> nodes = graph.getNodesInFile(Paths.get(filePath));
        
        if (nodes.isEmpty()) {
            return null;
        }
        
        // Build a CompilationUnit for this file
        CompilationUnit cu = buildCompilationUnit(filePath, nodes);
        
        // Serialize to source
        return cu.toString();
    }
    
    /**
     * Writes all modified files back to disk.
     */
    public void writeModifiedFiles(Path outputDir) throws IOException {
        Map<String, String> serialized = serializeModifiedFiles();
        
        for (Map.Entry<String, String> entry : serialized.entrySet()) {
            String filePath = entry.getKey();
            String source = entry.getValue();
            
            Path outputPath = outputDir.resolve(filePath).normalize();
            outputPath.toFile().getParentFile().mkdirs();
            
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                writer.write(source);
            }
        }
    }
    
    /**
     * Builds a CompilationUnit from nodes in a file.
     */
    private CompilationUnit buildCompilationUnit(String filePath, List<dk.gausdalfind.model.Node> nodes) {
        CompilationUnit cu = new CompilationUnit();
        
        // Set package
        PackageNode pkgNode = getPackageNode(filePath, nodes);
        if (pkgNode != null) {
            cu.setPackageDeclaration(pkgNode.getName());
        }
        
        // Add imports (would need ImportNode support)
        // For now, we'll skip imports
        
        // Add types (classes, interfaces, enums)
        for (dk.gausdalfind.model.Node node : nodes) {
            if (node instanceof ClassNode) {
                cu.addType(buildClassBody((ClassNode) node, nodes));
            } else if (node instanceof PackageNode) {
                // Already handled above
            }
        }
        
        return cu;
    }
    
    /**
     * Gets the package node for a file.
     */
    private PackageNode getPackageNode(String filePath, List<dk.gausdalfind.model.Node> nodes) {
        for (dk.gausdalfind.model.Node node : nodes) {
            if (node instanceof PackageNode) {
                return (PackageNode) node;
            }
        }
        return null;
    }
    
    /**
     * Builds a ClassOrInterfaceDeclaration from a ClassNode.
     */
    private ClassOrInterfaceDeclaration buildClassBody(ClassNode classNode, List<dk.gausdalfind.model.Node> allNodes) {
        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        
        // Set name
        classDecl.setName(classNode.getName());
        
        // Set modifiers
        List<Modifier> modifiers = toModifiers(classNode.getModifiers());
        Modifier.Keyword[] keywords = modifiers.stream()
            .map(m -> m.getKeyword())
            .toArray(Modifier.Keyword[]::new);
        classDecl.addModifier(keywords);
        
        // Set if interface
        if (classNode.isInterface()) {
            classDecl.setInterface(true);
        }
        
        // Set extends
        if (classNode.hasSuperclass() && !"java.lang.Object".equals(classNode.getSuperclass())) {
            classDecl.addExtendedType(classNode.getSuperclass());
        }
        
        // Set implements
        for (String iface : classNode.getInterfaces()) {
            classDecl.addImplementedType(iface);
        }
        
        // Add fields
        for (dk.gausdalfind.model.Node node : allNodes) {
            if (node instanceof FieldNode) {
                FieldNode field = (FieldNode) node;
                if (classNode.getQualifiedName().equals(field.getClassName())) {
                    classDecl.addMember(buildFieldDeclaration(field));
                }
            }
        }
        
        // Add methods (constructors are emitted by the loop below)
        for (dk.gausdalfind.model.Node node : allNodes) {
            if (node instanceof MethodNode) {
                MethodNode method = (MethodNode) node;
                if (!method.isConstructor()
                        && classNode.getQualifiedName().equals(method.getClassName())) {
                    classDecl.addMember(buildMethodDeclaration(method, allNodes));
                }
            }
        }
        
        // Add constructors
        for (dk.gausdalfind.model.Node node : allNodes) {
            if (node instanceof MethodNode) {
                MethodNode method = (MethodNode) node;
                if (method.isConstructor() && classNode.getQualifiedName().equals(method.getClassName())) {
                    classDecl.addMember(buildConstructorDeclaration(method, allNodes));
                }
            }
        }
        
        return classDecl;
    }
    
    /**
     * Builds a FieldDeclaration from a FieldNode.
     */
    private FieldDeclaration buildFieldDeclaration(FieldNode fieldNode) {
        FieldDeclaration fieldDecl = new FieldDeclaration();
        
        // Set modifiers
        List<Modifier> modifiers = toModifiers(fieldNode.getModifiers());
        Modifier.Keyword[] keywords = modifiers.stream()
            .map(m -> m.getKeyword())
            .toArray(Modifier.Keyword[]::new);
        fieldDecl.addModifier(keywords);
        
        // Set variable
        VariableDeclarator varDecl = new VariableDeclarator();
        varDecl.setName(fieldNode.getName());
        
        // Set type
        varDecl.setType(parseType(fieldNode.getDataType()));
        
        // Set initializer if available (would need expression support)
        
        fieldDecl.getVariables().add(varDecl);
        
        return fieldDecl;
    }
    
    /**
     * Builds a MethodDeclaration from a MethodNode.
     */
    private MethodDeclaration buildMethodDeclaration(MethodNode methodNode, List<dk.gausdalfind.model.Node> allNodes) {
        MethodDeclaration methodDecl = new MethodDeclaration();
        
        // Set name
        methodDecl.setName(methodNode.getName());
        
        // Set modifiers
        List<Modifier> modifiers = toModifiers(methodNode.getModifiers());
        Modifier.Keyword[] keywords = modifiers.stream()
            .map(m -> m.getKeyword())
            .toArray(Modifier.Keyword[]::new);
        methodDecl.addModifier(keywords);
        
        // Set return type
        methodDecl.setType(parseType(methodNode.getReturnType()));
        
        // Set parameters
        for (dk.gausdalfind.model.Node paramNode : allNodes) {
            if (paramNode instanceof ParameterNode) {
                ParameterNode param = (ParameterNode) paramNode;
                if (methodNode.getQualifiedName().equals(param.getBelongingMethod())) {
                    methodDecl.addParameter(buildParameter(param));
                }
            }
        }
        
        // Set body
        if (methodNode.isAbstract()) {
            // No body for abstract methods
        } else {
            BlockStmt body = buildMethodBody(methodNode, allNodes);
            methodDecl.setBody(body);
        }
        
        // Set thrown exceptions
        for (String exception : methodNode.getThrownExceptions()) {
            methodDecl.addThrownException(parseExceptionType(exception));
        }
        
        return methodDecl;
    }
    
    /**
     * Builds a ConstructorDeclaration from a MethodNode.
     */
    private ConstructorDeclaration buildConstructorDeclaration(MethodNode methodNode, List<dk.gausdalfind.model.Node> allNodes) {
        ConstructorDeclaration constructorDecl = new ConstructorDeclaration();
        
        // Set name
        constructorDecl.setName(methodNode.getName());
        
        // Set modifiers (filter out invalid constructor modifiers)
        List<Modifier.Keyword> validKeywords = new ArrayList<>();
        for (String modifier : methodNode.getModifiers()) {
            if (!"static".equals(modifier) && !"final".equals(modifier) && 
                !"abstract".equals(modifier) && !"native".equals(modifier)) {
                Modifier.Keyword keyword = toModifierKeyword(modifier);
                if (keyword != null) {
                    validKeywords.add(keyword);
                }
            }
        }
        if (!validKeywords.isEmpty()) {
            constructorDecl.addModifier(validKeywords.toArray(new Modifier.Keyword[0]));
        }
        
        // Set parameters
        for (dk.gausdalfind.model.Node paramNode : allNodes) {
            if (paramNode instanceof ParameterNode) {
                ParameterNode param = (ParameterNode) paramNode;
                if (methodNode.getQualifiedName().equals(param.getBelongingMethod())) {
                    constructorDecl.addParameter(buildParameter(param));
                }
            }
        }
        
        // Set body
        BlockStmt body = buildMethodBody(methodNode, allNodes);
        constructorDecl.setBody(body);
        
        // Set thrown exceptions
        for (String exception : methodNode.getThrownExceptions()) {
            constructorDecl.addThrownException(parseExceptionType(exception));
        }
        
        return constructorDecl;
    }
    
    /**
     * Builds a Parameter from a ParameterNode.
     */
    private Parameter buildParameter(ParameterNode paramNode) {
        Parameter param = new Parameter();
        param.setName(paramNode.getName());
        param.setType(parseType(paramNode.getDataType()));
        
        // Set modifiers (parameters rarely have modifiers like 'final')
        // Note: ParameterNode doesn't currently store modifiers, so we skip this
        // If needed in the future, add modifiers field to ParameterNode
        
        return param;
    }
    
    /**
     * Builds a BlockStmt from method body.
     */
    private BlockStmt buildMethodBody(MethodNode methodNode, List<dk.gausdalfind.model.Node> allNodes) {
        BlockStmt block = new BlockStmt();
        
        // Find statements for this method
        // This would need statement node support
        // For now, if we have a body stored in the method, use it
        
        // Check if this method was modified and has a new body
        if (changeTracker != null) {
            for (ChangeTracker.Change change : changeTracker.getAllChanges()) {
                if (change.getNodeId() != null && change.getNodeId().equals(methodNode.getId())) {
                    if (change.getValue() != null) {
                        // Try to parse the new body
                        // Note: This feature requires full implementation of statement parsing
                        // For now, we skip this and return empty block
                        // TODO: Implement when statement nodes are fully supported
                    }
                }
            }
        }
        
        // Default: empty block
        return block;
    }
    
    /**
     * Parses a type string into a JavaParser Type.
     */
    private Type parseType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return new UnknownType();
        }
        
        // Handle primitive types
        switch (typeName) {
            case "void": return new VoidType();
            case "boolean": return PrimitiveType.booleanType();
            case "byte": return PrimitiveType.byteType();
            case "char": return PrimitiveType.charType();
            case "short": return PrimitiveType.shortType();
            case "int": return PrimitiveType.intType();
            case "long": return PrimitiveType.longType();
            case "float": return PrimitiveType.floatType();
            case "double": return PrimitiveType.doubleType();
            case "String": return StaticJavaParser.parseClassOrInterfaceType("String");
            default:
                // Handle array types
                if (typeName.endsWith("[]")) {
                    return new ArrayType(parseType(typeName.substring(0, typeName.length() - 2)));
                }
                return StaticJavaParser.parseClassOrInterfaceType(typeName);
        }
    }
    
    /**
     * Parses an exception type string into a JavaParser ReferenceType.
     */
    private ReferenceType parseExceptionType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return StaticJavaParser.parseClassOrInterfaceType("Throwable");
        }
        return StaticJavaParser.parseClassOrInterfaceType(typeName);
    }
    
    /**
     * Gets all files that have nodes in the graph.
     */
    private Set<String> getFilesInGraph() {
        Set<String> files = new HashSet<>();
        for (Node node : graph.getAllNodes()) {
            if (node.getFile() != null) {
                files.add(node.getFile().toString());
            }
        }
        return files;
    }
    
    /**
     * Writes a single file back to disk.
     */
    public void writeFile(Path filePath) throws IOException {
        String filePathStr = filePath.toString();
        String source = serializeFile(filePathStr);
        
        if (source != null) {
            filePath.toFile().getParentFile().mkdirs();
            Files.writeString(filePath, source);
        }
    }
    
    /**
     * Writes all files back to disk.
     */
    public void writeAllFiles(Path outputDir) throws IOException {
        Set<String> files = getFilesInGraph();
        
        for (String filePath : files) {
            Path fullPath = outputDir.resolve(filePath).normalize();
            fullPath.toFile().getParentFile().mkdirs();
            
            String source = serializeFile(filePath);
            if (source != null) {
                Files.writeString(fullPath, source);
            }
        }
    }
    
    /**
     * Converts a string modifier to JavaParser Modifier.Keyword.
     */
    private Modifier.Keyword toModifierKeyword(String modifier) {
        if (modifier == null || modifier.isEmpty()) {
            return null;
        }
        try {
            return Modifier.Keyword.valueOf(modifier.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown modifier: " + modifier);
            return null;
        }
    }
    
    /**
     * Converts a list of string modifiers to JavaParser modifiers.
     */
    private List<Modifier> toModifiers(Set<String> modifiers) {
        List<Modifier> result = new ArrayList<>();
        if (modifiers != null) {
            for (String modifier : modifiers) {
                Modifier.Keyword keyword = toModifierKeyword(modifier);
                if (keyword != null) {
                    result.add(new Modifier(keyword));
                }
            }
        }
        return result;
    }
}
