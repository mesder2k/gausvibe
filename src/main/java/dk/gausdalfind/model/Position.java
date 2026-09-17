package dk.gausdalfind.model;

import java.util.Objects;

/**
 * Represents a position in source code with line and column numbers.
 * Line and column are 1-indexed.
 */
public record Position(int line, int column) {
    
    public Position {
        if (line < 1) {
            throw new IllegalArgumentException("Line must be >= 1, got: " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("Column must be >= 1, got: " + column);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return line == position.line && column == position.column;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(line, column);
    }
    
    @Override
    public String toString() {
        return "Position{line=" + line + ", column=" + column + "}";
    }
    
    /**
     * Creates a Position from JavaParser's Position.
     * Requires JavaParser to be on the classpath.
     */
    // public static Position fromJavaParser(com.github.javaparser.Position pos) {
    //     if (pos == null) {
    //         return null;
    //     }
    //     return new Position(pos.line, pos.column);
    // }
}
