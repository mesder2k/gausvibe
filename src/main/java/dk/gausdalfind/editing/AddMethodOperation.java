package dk.gausdalfind.editing;

import java.util.*;

/**
 * Operation to add a new method to a class.
 */
public class AddMethodOperation implements Operation {
    
    private final String targetClass;
    private final String name;
    private final String returnType;
    private final String signature;
    private final Set<String> modifiers;
    private final boolean isConstructor;
    private final boolean isStatic;
    private final boolean isAbstract;
    private final List<String> thrownExceptions;
    private final String body;
    
    private AddMethodOperation(Builder builder) {
        this.targetClass = builder.targetClass;
        this.name = builder.name;
        this.returnType = builder.returnType;
        this.signature = builder.signature;
        this.modifiers = Set.copyOf(builder.modifiers);
        this.isConstructor = builder.isConstructor;
        this.isStatic = builder.isStatic;
        this.isAbstract = builder.isAbstract;
        this.thrownExceptions = List.copyOf(builder.thrownExceptions);
        this.body = builder.body;
    }
    
    @Override
    public OperationType getType() {
        return OperationType.ADD_METHOD;
    }
    
    @Override
    public String getDescription() {
        return "Add method " + name + " to " + targetClass;
    }
    
    // ==================== Getters ====================
    
    public String getTargetClass() {
        return targetClass;
    }
    
    public String getName() {
        return name;
    }
    
    public String getReturnType() {
        return returnType;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public Set<String> getModifiers() {
        return modifiers;
    }
    
    public boolean isConstructor() {
        return isConstructor;
    }
    
    public boolean isStatic() {
        return isStatic;
    }
    
    public boolean isAbstract() {
        return isAbstract;
    }
    
    public List<String> getThrownExceptions() {
        return thrownExceptions;
    }
    
    public String getBody() {
        return body;
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String targetClass;
        private String name;
        private String returnType = "void";
        private String signature;
        private Set<String> modifiers = new HashSet<>();
        private boolean isConstructor = false;
        private boolean isStatic = false;
        private boolean isAbstract = false;
        private List<String> thrownExceptions = new ArrayList<>();
        private String body = "";
        
        public Builder targetClass(String targetClass) {
            this.targetClass = Objects.requireNonNull(targetClass);
            return this;
        }
        
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }
        
        public Builder returnType(String returnType) {
            this.returnType = Objects.requireNonNull(returnType);
            return this;
        }
        
        public Builder signature(String signature) {
            this.signature = Objects.requireNonNull(signature);
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
        
        public Builder isConstructor(boolean isConstructor) {
            this.isConstructor = isConstructor;
            return this;
        }
        
        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }
        
        public Builder isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return this;
        }
        
        public Builder thrownExceptions(List<String> thrownExceptions) {
            this.thrownExceptions = new ArrayList<>(thrownExceptions);
            return this;
        }
        
        public Builder body(String body) {
            this.body = body;
            return this;
        }
        
        public AddMethodOperation build() {
            Objects.requireNonNull(targetClass, "targetClass is required");
            Objects.requireNonNull(name, "name is required");
            if (signature == null) {
                // Build signature from name and return type
                signature = name + "()";
            }
            return new AddMethodOperation(this);
        }
    }
}
