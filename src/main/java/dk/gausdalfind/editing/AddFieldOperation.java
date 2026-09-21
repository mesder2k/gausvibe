package dk.gausdalfind.editing;

import java.util.*;

/**
 * Operation to add a new field to a class.
 */
public class AddFieldOperation implements Operation {
    
    private final String targetClass;
    private final String name;
    private final String type;
    private final Set<String> modifiers;
    private final boolean isStatic;
    private final boolean isFinal;
    
    private AddFieldOperation(Builder builder) {
        this.targetClass = builder.targetClass;
        this.name = builder.name;
        this.type = builder.type;
        this.modifiers = Set.copyOf(builder.modifiers);
        this.isStatic = builder.isStatic;
        this.isFinal = builder.isFinal;
    }
    
    @Override
    public OperationType getType() {
        return OperationType.ADD_FIELD;
    }
    
    @Override
    public String getDescription() {
        return "Add field " + name + " to " + targetClass;
    }
    
    public String getTargetClass() {
        return targetClass;
    }
    
    public String getName() {
        return name;
    }
    
    public String getFieldType() {
        return type;
    }
    
    public Set<String> getModifiers() {
        return modifiers;
    }
    
    public boolean isStatic() {
        return isStatic;
    }
    
    public boolean isFinal() {
        return isFinal;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String targetClass;
        private String name;
        private String type;
        private Set<String> modifiers = new HashSet<>();
        private boolean isStatic = false;
        private boolean isFinal = false;
        
        public Builder targetClass(String targetClass) {
            this.targetClass = Objects.requireNonNull(targetClass);
            return this;
        }
        
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }
        
        public Builder type(String type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }
        
        public Builder modifiers(Set<String> modifiers) {
            this.modifiers = new HashSet<>(modifiers);
            return this;
        }
        
        public Builder addModifier(String modifier) {
            this.modifiers.add(modifier);
            return this;
        }
        
        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }
        
        public Builder isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return this;
        }
        
        public AddFieldOperation build() {
            Objects.requireNonNull(targetClass, "targetClass is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(type, "type is required");
            return new AddFieldOperation(this);
        }
    }
}
