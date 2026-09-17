package com.javacodegraph.model.declaration;

import com.javacodegraph.model.Node;
import com.javacodegraph.model.NodeIdGenerator;
import com.javacodegraph.model.Position;

import java.nio.file.Path;

/**
 * Represents a package declaration in Java source code.
 * 
 * Properties:
 * - name: The package name (e.g., "com.example")
 * - qualified_name: Same as name for packages
 * - file: The file containing this package declaration
 * - start/end: Position in source file
 */
public class PackageNode extends DeclarationNode {
    
    /**
     * Creates a new package node.
     * 
     * @param name the package name
     * @param file the source file path
     * @param startPosition the start position in the file
     * @param endPosition the end position in the file
     */
    public PackageNode(String name, Path file, Position startPosition, Position endPosition) {
        super(
            NodeIdGenerator.forDeclaration(
                NodeIdGenerator.NodeType.PACKAGE, 
                name
            ),
            name,
            name,
            file,
            startPosition,
            endPosition
        );
    }
    
    @Override
    public String getType() {
        return Node.TYPE_PACKAGE;
    }
    
    /**
     * Returns true if this is the default (unnamed) package.
     */
    public boolean isDefaultPackage() {
        return "".equals(name) || name.isBlank();
    }
    
    /**
     * Returns the parent package name, or null if this is a top-level package.
     */
    public String getParentPackageName() {
        if (name == null || name.isBlank()) {
            return null;
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) {
            return null; // Top-level package
        }
        return name.substring(0, lastDot);
    }
}
