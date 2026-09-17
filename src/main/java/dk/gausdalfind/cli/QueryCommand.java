package dk.gausdalfind.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a parsed query command.
 * 
 * A query command consists of:
 * - type: The query type (class, method, field, package, file, search, stats, build)
 * - arguments: List of arguments for the query
 * 
 * Examples:
 *   - "class:com.example.MyClass" -> type="class", arguments=["com.example.MyClass"]
 *   - "method:class:com.example.MyClass" -> type="method", arguments=["class", "com.example.MyClass"]
 *   - "search:nodes:CLASS" -> type="search", arguments=["nodes", "CLASS"]
 */
public class QueryCommand {
    
    private final String type;
    private final List<String> arguments;
    
    public QueryCommand(String type, List<String> arguments) {
        this.type = Objects.requireNonNull(type, "Query type cannot be null");
        this.arguments = arguments != null ? 
            Collections.unmodifiableList(new ArrayList<>(arguments)) : 
            Collections.emptyList();
    }
    
    public QueryCommand(String type) {
        this(type, Collections.emptyList());
    }
    
    public String getType() {
        return type;
    }
    
    public List<String> getArguments() {
        return arguments;
    }
    
    public boolean hasArguments() {
        return !arguments.isEmpty();
    }
    
    public int getArgumentCount() {
        return arguments.size();
    }
    
    public String getArgument(int index) {
        if (index < 0 || index >= arguments.size()) {
            throw new IndexOutOfBoundsException("Argument index " + index + " out of bounds for " + arguments.size() + " arguments");
        }
        return arguments.get(index);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryCommand that = (QueryCommand) o;
        return Objects.equals(type, that.type) && 
               Objects.equals(arguments, that.arguments);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, arguments);
    }
    
    @Override
    public String toString() {
        return "QueryCommand{type='" + type + "', arguments=" + arguments + "}";
    }
}
