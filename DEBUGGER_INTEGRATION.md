# Debugger Integration: Parallel Differential Debugging

**Project:** GausVibe - Debugger Extension  
**Goal:** Run two program versions in parallel with a debugger, find first divergence point when method output invariant is violated  
**Date:** 2026-09-17  
**Status:** Design Document - Not Yet Implemented

---

## 🎯 USE CASE: Invariant Violation Detection

### The Problem
You have:
- **Version A** - A working program (commit `abc123`)
- **Version B** - A modified program (commit `def456`)
- **Invariant** - Method `com.example.Calculator#add(int,int)` must return the same output for the same input
- **Symptom** - Invariant is violated: `add(2, 3)` returns `5` in A but `6` in B

**Traditional approach:** 
- Manually trace both versions
- Add print statements everywhere
- Use `git bisect` (but only for commit history, not runtime behavior)
- **Problem:** The divergence point might be deep in the call chain, far from the invariant method

**Our solution:** 
- Attach debuggers to both versions
- Run them in lockstep (same inputs)
- Compare state at every step
- **Stop at the first divergence** - the root cause

---

## 🏗️ ARCHITECTURE

### Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        DIFFERENTIAL DEBUGGER                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐     ┌─────────────────────┐                     │
│  │    VERSION A         │     │    VERSION B         │                     │
│  │  (commit abc123)     │     │  (commit def456)     │                     │
│  │                     │     │                     │                     │
│  │  ┌─────────────────┐│     │  ┌─────────────────┐│                     │
│  │  │   JVM A          ││     │  │   JVM B          ││                     │
│  │  │                 ││     │  │                 ││                     │
│  │  └────────┬────────┘│     │  └────────┬────────┘│                     │
│  │           │          │     │           │          │                     │
│  │  ┌────────▼────────┐│     │  ┌────────▼────────┐│                     │
│  │  │  AST Graph A    ││     │  │  AST Graph B    ││                     │
│  │  │  (Static)       ││     │  │  (Static)       ││                     │
│  │  └────────┬────────┘│     │  └────────┬────────┘│                     │
│  │           │          │     │           │          │                     │
│  └───────────┼──────────┘     └───────────┼──────────┘                     │
│              │                              │                            │
│              ▼                              ▼                            │
│  ┌─────────────────────────────────────────────────────┐              │
│  │                    COMPARISON ENGINE                      │              │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │              │
│  │  │  State       │  │  Diff        │  │  Div.        │  │              │
│  │  │  Snapshot    │  │  Detector    │  │  Point      │  │              │
│  │  │  Manager     │  │              │  │  Tracker    │  │              │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  │              │
│  └─────────────────────────────────────────────────────┘              │
│                           │                                               │
│                           ▼                                               │
│  ┌─────────────────────────────────────────────────────┐              │
│  │                    LLM INTERFACE                         │              │
│  │  "Why did add(2,3) return different values?"           │              │
│  │  "Show me the first point where A and B diverged"      │              │
│  │  "What was the value of variable x at that point?"    │              │
│  └─────────────────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Details

#### 1. Parallel Debug Session

```java
public class ParallelDebugSession {
    private final DebugSession sessionA;  // Version A
    private final DebugSession sessionB;  // Version B
    private final ComparisonEngine comparator;
    private final Invariant invariant;    // The invariant to check
    
    // Both sessions are synchronized on the same inputs
    public void runWithInputs(Object... inputs) {
        StepResult resultA = sessionA.stepWithInputs(inputs);
        StepResult resultB = sessionB.stepWithInputs(inputs);
        
        ComparisonResult comparison = comparator.compare(resultA, resultB);
        
        if (comparison.hasDivergence()) {
            // Found the first point where things differ!
            reportDivergence(comparison);
            pauseBothSessions();
        }
        
        if (invariant.isViolated(resultA, resultB)) {
            // Invariant check failed
            reportInvariantViolation();
        }
    }
}
```

#### 2. Debug Session (Single Version)

```java
public class DebugSession {
    private final String versionId;           // "A" or "B"
    private final String commitHash;          // "abc123" or "def456"
    private final Path sourceRoot;           // Path to source code
    private final VirtualMachine vm;         // JDI VirtualMachine
    private final Graph astGraph;            // Your existing AST graph
    private final DebugLinker linker;        // Maps AST <-> Runtime
    private final BreakpointManager breakpoints;
    
    // Current execution context
    private ThreadReference currentThread;
    private List<StackFrame> callStack;
    
    public DebugSession(String versionId, String commitHash, Path sourceRoot) {
        this.versionId = versionId;
        this.commitHash = commitHash;
        this.sourceRoot = sourceRoot;
        this.astGraph = buildGraphForCommit(commitHash);  // Your existing GausVibeBuilder
        this.vm = attachToJVM();
        this.linker = new DebugLinker(astGraph, vm);
        this.breakpoints = new BreakpointManager(this);
    }
    
    public void attachToJVM() {
        // Launch or attach to JVM for this version
        // Use JDI: VirtualMachine.attach() or launch
    }
    
    public StepResult stepWithInputs(Object... inputs) {
        // Execute one step (line, method, or instruction)
        // Capture all state
        // Return snapshot for comparison
    }
}
```

#### 3. Debug Linker (AST <-> Runtime Bridge)

```java
public class DebugLinker {
    private final Graph astGraph;
    private final VirtualMachine vm;
    private final Map<String, Location> nodeToLocationCache = new HashMap<>();
    
    public DebugLinker(Graph astGraph, VirtualMachine vm) {
        this.astGraph = astGraph;
        this.vm = vm;
    }
    
    // Map AST MethodNode to JVM Location
    public Location toLocation(MethodNode method) {
        String cacheKey = method.getId();
        if (nodeToLocationCache.containsKey(cacheKey)) {
            return nodeToLocationCache.get(cacheKey);
        }
        
        // Find the class in JVM
        ReferenceType classType = findClassType(method.getQualifiedName());
        if (classType == null) return null;
        
        // Find the method in JVM
        Method jdiMethod = findMethod(classType, method.getName(), method.getSignature());
        if (jdiMethod == null) return null;
        
        // Get all locations for this method
        List<Location> locations = jdiMethod.allLineLocations();
        
        // Match by line number from AST
        Position start = method.getStartPosition();
        Location location = locations.stream()
            .filter(l -> l.lineNumber() == start.line())
            .findFirst()
            .orElse(locations.get(0));
        
        nodeToLocationCache.put(cacheKey, location);
        return location;
    }
    
    // Map AST VariableNode to JVM LocalVariable
    public LocalVariable toLocalVariable(VariableNode varNode, StackFrame frame) {
        Method method = frame.location().method();
        return method.variables().stream()
            .filter(v -> v.name().equals(varNode.getName()))
            .findFirst()
            .orElse(null);
    }
    
    // Get runtime value for AST VariableNode
    public Value getValue(VariableNode varNode, StackFrame frame) {
        LocalVariable jdiVar = toLocalVariable(varNode, frame);
        if (jdiVar == null) return null;
        return frame.getValue(jdiVar);
    }
    
    // Map current location back to AST node
    public Node toAstNode(Location location) {
        String filePath = location.sourcePath();
        int line = location.lineNumber();
        
        // Query the graph for nodes at this file:line
        return astGraph.getNodesByFile(Paths.get(filePath)).stream()
            .filter(n -> n.getStartPosition().line() == line)
            .findFirst()
            .orElse(null);
    }
}
```

#### 4. Comparison Engine

```java
public class ComparisonEngine {
    private final ParallelDebugSession session;
    
    public ComparisonResult compare(StepResult resultA, StepResult resultB) {
        ComparisonResult result = new ComparisonResult();
        
        // Compare call stacks
        result.addCallStackComparison(
            compareCallStacks(resultA.getCallStack(), resultB.getCallStack())
        );
        
        // Compare local variables at current frame
        result.addVariablesComparison(
            compareVariables(resultA.getCurrentFrame(), resultB.getCurrentFrame())
        );
        
        // Compare field values for objects
        result.addFieldsComparison(
            compareFields(resultA.getAllObjects(), resultB.getAllObjects())
        );
        
        // Check invariant if applicable
        if (session.getInvariant() != null) {
            result.setInvariantViolated(
                session.getInvariant().isViolated(resultA, resultB)
            );
        }
        
        return result;
    }
    
    private CallStackComparison compareCallStacks(
            List<StackFrame> stackA, List<StackFrame> stackB) {
        
        CallStackComparison comparison = new CallStackComparison();
        
        // Frame by frame comparison
        int maxFrames = Math.max(stackA.size(), stackB.size());
        for (int i = 0; i < maxFrames; i++) {
            StackFrame frameA = i < stackA.size() ? stackA.get(i) : null;
            StackFrame frameB = i < stackB.size() ? stackB.get(i) : null;
            
            FrameComparison frameComp = compareFrames(frameA, frameB);
            comparison.addFrameComparison(i, frameComp);
            
            if (frameComp.hasDifference()) {
                // Divergence found!
                comparison.setFirstDivergenceFrame(i);
                break;
            }
        }
        
        return comparison;
    }
    
    private FrameComparison compareFrames(StackFrame frameA, StackFrame frameB) {
        FrameComparison comp = new FrameComparison();
        
        if (frameA == null || frameB == null) {
            comp.setDifferent(true);
            comp.setReason("Frame count mismatch");
            return comp;
        }
        
        // Compare method
        if (!frameA.location().method().name().equals(frameB.location().method().name())) {
            comp.setDifferent(true);
            comp.setReason("Different method");
            return comp;
        }
        
        // Compare line numbers
        if (frameA.location().lineNumber() != frameB.location().lineNumber()) {
            comp.setDifferent(true);
            comp.setReason("Different line number");
            return comp;
        }
        
        // Compare local variables
        Map<String, Value> varsA = getVariableMap(frameA);
        Map<String, Value> varsB = getVariableMap(frameB);
        
        for (String varName : Sets.union(varsA.keySet(), varsB.keySet())) {
            Value valA = varsA.get(varName);
            Value valB = varsB.get(varName);
            
            if (!valuesEqual(valA, valB)) {
                comp.addVariableDifference(varName, valA, valB);
            }
        }
        
        comp.setDifferent(!comp.getVariableDifferences().isEmpty());
        return comp;
    }
    
    private boolean valuesEqual(Value valA, Value valB) {
        if (valA == null && valB == null) return true;
        if (valA == null || valB == null) return false;
        if (!valA.type().name().equals(valB.type().name())) return false;
        
        // Primitive comparison
        if (valA instanceof PrimitiveValue && valB instanceof PrimitiveValue) {
            return ((PrimitiveValue) valA).value().equals(((PrimitiveValue) valB).value());
        }
        
        // Object comparison - check reference or use equals()
        if (valA instanceof ObjectReference && valB instanceof ObjectReference) {
            ObjectReference refA = (ObjectReference) valA;
            ObjectReference refB = (ObjectReference) valB;
            
            // Same reference
            if (refA.uniqueID() == refB.uniqueID()) return true;
            
            // Compare field values recursively
            return fieldsEqual(refA, refB);
        }
        
        return false;
    }
}
```

#### 5. Invariant System

```java
public interface Invariant {
    String getDescription();
    boolean isViolated(StepResult resultA, StepResult resultB);
}

// Method Output Invariant (your use case)
public class MethodOutputInvariant implements Invariant {
    private final String methodSignature;  // e.g., "com.example.Calculator#add(int,int)"
    private final Object[] inputArgs;
    private final Object expectedOutput;   // Optional: expected from version A
    
    public MethodOutputInvariant(String methodSignature, Object[] inputArgs) {
        this.methodSignature = methodSignature;
        this.inputArgs = inputArgs;
    }
    
    @Override
    public boolean isViolated(StepResult resultA, StepResult resultB) {
        // Check if we just executed the target method
        if (!isAtMethodExit(resultA) || !isAtMethodExit(resultB)) {
            return false;
        }
        
        // Get return values
        Value returnA = resultA.getCurrentFrame().getReturnValue();
        Value returnB = resultB.getCurrentFrame().getReturnValue();
        
        // If we have expected output, check against it
        if (expectedOutput != null) {
            return !expectedOutput.equals(extractValue(returnA));
        }
        
        // Compare A and B outputs
        return !valuesEqual(returnA, returnB);
    }
    
    private boolean isAtMethodExit(StepResult result) {
        // Check if we're at the return statement of the target method
        StackFrame frame = result.getCurrentFrame();
        Location loc = frame.location();
        Method method = loc.method();
        
        return method.name().equals(methodSignature) &&
               isReturnLocation(loc, result.getAstGraph());
    }
}

// Custom invariant: field value should remain constant
public class FieldValueInvariant implements Invariant {
    private final String fieldFQN;  // e.g., "com.example.Counter#count"
    private final Object expectedValue;
    
    @Override
    public boolean isViolated(StepResult resultA, StepResult resultB) {
        ObjectReference objA = findObjectWithField(resultA);
        ObjectReference objB = findObjectWithField(resultB);
        
        if (objA == null || objB == null) return false;
        
        Value valA = objA.getValue(objA.referenceType().fieldByName(fieldFQN));
        Value valB = objB.getValue(objB.referenceType().fieldByName(fieldFQN));
        
        return !valuesEqual(valA, valB);
    }
}
```

#### 6. Step Result (Execution Snapshot)

```java
public class StepResult {
    private final String versionId;
    private final long stepNumber;
    private final DebugSession session;
    private final ThreadReference thread;
    private final List<StackFrame> callStack;
    private final Map<String, Value> localVariables;
    private final Map<Long, ObjectSnapshot> objects;  // objectId -> snapshot
    private final Location currentLocation;
    private final Node currentAstNode;  // Linked AST node
    private final StepType stepType;    // LINE, METHOD_ENTRY, METHOD_EXIT, INSTRUCTION
    
    // Get the current frame (top of stack)
    public StackFrame getCurrentFrame() {
        return callStack.isEmpty() ? null : callStack.get(0);
    }
    
    // Get AST node for current location
    public Node getCurrentAstNode() {
        return currentAstNode;
    }
}

public class ObjectSnapshot {
    private final long objectId;
    private final ReferenceType type;
    private final Map<String, Value> fieldValues;
    
    public ObjectSnapshot(ObjectReference obj) {
        this.objectId = obj.uniqueID();
        this.type = obj.referenceType();
        this.fieldValues = captureFieldValues(obj);
    }
}
```

#### 7. Divergence Point Tracking

```java
public class DivergenceTracker {
    private final ParallelDebugSession session;
    private DivergencePoint firstDivergence;
    private List<DivergencePoint> allDivergences = new ArrayList<>();
    
    public void recordDivergence(ComparisonResult comparison) {
        if (firstDivergence == null) {
            firstDivergence = createDivergencePoint(comparison);
        }
        allDivergences.add(createDivergencePoint(comparison));
    }
    
    private DivergencePoint createDivergencePoint(ComparisonResult comparison) {
        DivergencePoint point = new DivergencePoint();
        
        point.setStepNumber(session.getCurrentStep());
        point.setComparisonResult(comparison);
        
        // Capture the AST nodes at divergence
        if (comparison.getCallStackComparison().getFirstDivergenceFrame() != null) {
            int frameIndex = comparison.getCallStackComparison().getFirstDivergenceFrame();
            StackFrame frameA = session.getSessionA().getCallStack().get(frameIndex);
            StackFrame frameB = session.getSessionB().getCallStack().get(frameIndex);
            
            point.setAstNodeA(session.getSessionA().getLinker().toAstNode(frameA.location()));
            point.setAstNodeB(session.getSessionB().getLinker().toAstNode(frameB.location()));
        }
        
        // Capture variable differences
        for (VariableDifference diff : comparison.getVariablesComparison().getDifferences()) {
            point.addVariableDifference(diff);
        }
        
        return point;
    }
    
    public DivergencePoint getFirstDivergence() {
        return firstDivergence;
    }
    
    public List<DivergencePoint> getAllDivergences() {
        return Collections.unmodifiableList(allDivergences);
    }
}

public class DivergencePoint {
    private long stepNumber;
    private Node astNodeA;
    private Node astNodeB;
    private List<VariableDifference> variableDifferences = new ArrayList<>();
    private ComparisonResult comparisonResult;
    
    // Getters
    public long getStepNumber() { return stepNumber; }
    public Node getAstNodeA() { return astNodeA; }
    public Node getAstNodeB() { return astNodeB; }
    public List<VariableDifference> getVariableDifferences() { 
        return Collections.unmodifiableList(variableDifferences); 
    }
    
    public void addVariableDifference(VariableDifference diff) {
        variableDifferences.add(diff);
    }
}

public class VariableDifference {
    private final String variableName;
    private final Value valueA;
    private final Value valueB;
    private final String astNodeId;  // Which AST variable node this corresponds to
    
    public VariableDifference(String variableName, Value valueA, Value valueB, String astNodeId) {
        this.variableName = variableName;
        this.valueA = valueA;
        this.valueB = valueB;
        this.astNodeId = astNodeId;
    }
}
```

---

## 🎯 EXECUTION FLOW

### Workflow: Finding Invariant Violation Root Cause

```
1. SETUP
   ├─ Load Version A source (commit abc123)
   ├─ Build AST Graph A using GausVibeBuilder
   ├─ Launch/Attach JVM A
   │
   ├─ Load Version B source (commit def456)
   ├─ Build AST Graph B using GausVibeBuilder
   └─ Launch/Attach JVM B

2. CONFIGURE
   ├─ Define Invariant: MethodOutputInvariant("com.example.Calculator#add(int,int)", [2, 3])
   ├─ Set up DebugLinker for both versions
   └─ Initialize ComparisonEngine

3. EXECUTE IN PARALLEL
   ├─ Set breakpoints at method entry points
   ├─ Start both JVMs with same inputs
   └─ Step through execution in lockstep

4. AT EACH STEP
   ├─ sessionA.step() → StepResult A
   ├─ sessionB.step() → StepResult B
   ├─ comparator.compare(A, B)
   │  ├─ Compare call stacks
   │  ├─ Compare local variables
   │  ├─ Compare object fields
   │  └─ Check invariant
   │
   └─ IF divergence detected OR invariant violated:
      ├─ divergenceTracker.recordDivergence()
      ├─ Generate report
      └─ Pause execution

5. ANALYSIS
   ├─ Display first divergence point
   ├─ Show AST nodes where divergence occurred
   ├─ Show variable values in both versions
   └─ Provide explanations to LLM

6. LLM INTERACTION
   ├─ "What was the first difference between A and B?"
   ├─ "Show me the call stack when they diverged"
   ├─ "What was the value of variable 'x' at step 42?"
   └─ "Why did the invariant fail?"
```

### Example: Calculator Invariant Violation

**Version A (commit abc123):**
```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;  // Returns 5 for add(2,3)
    }
}
```

**Version B (commit def456):**
```java
public class Calculator {
    public int add(int a, int b) {
        int helper = a + 1;  // NEW LINE: This changes behavior
        return helper + b;   // Returns 6 for add(2,3)
    }
}
```

**Execution Trace:**

| Step | Version A | Version B | Comparison | Divergence? |
|------|-----------|-----------|------------|-------------|
| 1 | Method entry: Calculator.add | Method entry: Calculator.add | Same | No |
| 2 | Line 3: `return a + b` | Line 3: `int helper = a + 1` | Different line | **YES!** |
| 3 | - | Line 4: `return helper + b` | N/A | - |

**First Divergence Detected at Step 2:**
- **AST Node A:** MethodNode `mth:com/example/Calculator#add(int,int)` at line 3
- **AST Node B:** VariableNode `var:Calculator.java:add:3:5:helper` (declaration)
- **Variable Difference:** None yet, but execution path diverged

**At Step 3:**
- **Variable Difference:** 
  - Variable `helper` exists in B but not in A
  - Return value: A=5, B=6

---

## 📊 DATA STRUCTURES FOR YOUR EXISTING GRAPH

### Extending Your Node Types

Add these node types to support debugging:

```java
// In NodeIdGenerator.NodeType enum:
BREAKPOINT("bp"),       // Breakpoint at a location
STACK_FRAME("frame"),   // Stack frame snapshot
THREAD("thread"),       // Thread context
RUNTIME_VALUE("val"),  // Runtime value (primitive or object)
DIVERGENCE("div"),     // Divergence point marker
INVARIANT("inv")       // Invariant definition
```

### New Node Classes

```java
// BreakpointNode.java
public class BreakpointNode implements Node {
    private final String id;
    private final String versionId;      // "A" or "B"
    private final String locationId;     // The node ID this breakpoint is at
    private final boolean enabled;
    private final BreakpointType type;  // LINE, METHOD_ENTRY, METHOD_EXIT, FIELD_ACCESS
    
    // Implements Node interface methods
}

// StackFrameNode.java
public class StackFrameNode implements Node {
    private final String id;
    private final String versionId;
    private final long stepNumber;
    private final String methodId;       // Link to MethodNode
    private final int lineNumber;
    private final String threadId;       // Link to ThreadNode
    private final int frameIndex;        // 0 = top of stack
    
    // Additional frame-specific data
    private final Map<String, String> localVariableIds;  // var name -> VariableNode ID
}

// ThreadNode.java
public class ThreadNode implements Node {
    private final String id;
    private final String versionId;
    private final String threadName;
    private final long threadId;         // JVM thread ID
    private final ThreadState state;     // RUNNING, BLOCKED, WAITING, etc.
}

// RuntimeValueNode.java
public class RuntimeValueNode implements Node {
    private final String id;
    private final String versionId;
    private final long stepNumber;
    private final String variableId;     // Link to VariableNode or FieldNode
    private final String typeName;       // int, java.lang.String, etc.
    private final String valueRepresentation;  // String representation of value
    private final boolean isPrimitive;
    
    // For objects
    private final String objectId;       // JVM object ID (if not primitive)
    private final Map<String, String> fieldValueIds;  // field name -> RuntimeValueNode ID
}

// DivergenceNode.java
public class DivergenceNode implements Node {
    private final String id;
    private final long stepNumber;
    private final String versionA_NodeId;  // Node in version A
    private final String versionB_NodeId;  // Node in version B
    private final DivergenceType type;    // CALL_STACK, VARIABLE, FIELD, RETURN_VALUE
    private final String description;
}

// InvariantNode.java
public class InvariantNode implements Node {
    private final String id;
    private final String invariantType;   // METHOD_OUTPUT, FIELD_VALUE, etc.
    private final String targetNodeId;    // The node this invariant applies to
    private final String description;
    private final boolean isViolated;
}
```

### Extending EdgeTypes

Add these to your existing `EdgeTypes` class:

```java
// Breakpoint edges
public static final String HAS_BREAKPOINT = "HAS_BREAKPOINT";
public static final String BREAKPOINT_AT = "BREAKPOINT_AT";

// Stack frame edges
public static final String HAS_FRAME = "HAS_FRAME";
public static final String FRAME_METHOD = "FRAME_METHOD";
public static final String FRAME_VARIABLE = "FRAME_VARIABLE";
public static final String CALLER_FRAME = "CALLER_FRAME";
public static final String CALLEE_FRAME = "CALLEE_FRAME";

// Thread edges
public static final String THREAD_FRAME = "THREAD_FRAME";
public static final String THREAD_STATE = "THREAD_STATE";

// Runtime value edges
public static final String HAS_VALUE = "HAS_VALUE";
public static final String VALUE_OF = "VALUE_OF";
public static final String FIELD_VALUE = "FIELD_VALUE";

// Divergence edges
public static final String DIVERGENCE_AT = "DIVERGENCE_AT";
public static final String DIVERGENCE_BETWEEN = "DIVERGENCE_BETWEEN";
public static final String DIVERGENCE_CAUSE = "DIVERGENCE_CAUSE";

// Invariant edges
public static final String INVARIANT_ON = "INVARIANT_ON";
public static final String INVARIANT_VIOLATED = "INVARIANT_VIOLATED";
```

---

## 🔧 IMPLEMENTATION PHASES

### Phase 10: Debugger Foundation (3-5 days)
- [ ] Add JDI dependencies to pom.xml
- [ ] Implement `DebugSession` (single version debugging)
- [ ] Implement `DebugLinker` (AST <-> Runtime mapping)
- [ ] Implement `StepResult` and related snapshot classes
- [ ] Test: Single version debugging with breakpoints

### Phase 11: Parallel Execution (3-5 days)
- [ ] Implement `ParallelDebugSession`
- [ ] Implement synchronization between two sessions
- [ ] Implement stepping in lockstep
- [ ] Test: Run two versions with same inputs

### Phase 12: Comparison Engine (5-7 days)
- [ ] Implement `ComparisonEngine`
- [ ] Implement `CallStackComparison`, `FrameComparison`, `VariableComparison`
- [ ] Implement `Value` equality comparison (primitives, objects, arrays)
- [ ] Test: Detect differences at various levels

### Phase 13: Divergence Tracking (3-5 days)
- [ ] Implement `DivergenceTracker`
- [ ] Implement `DivergencePoint`
- [ ] Implement `VariableDifference`
- [ ] Add divergence nodes to graph
- [ ] Test: Record and query divergence points

### Phase 14: Invariant System (3-5 days)
- [ ] Implement `Invariant` interface
- [ ] Implement `MethodOutputInvariant`
- [ ] Implement `FieldValueInvariant`
- [ ] Implement invariant checking in comparison engine
- [ ] Test: Detect invariant violations

### Phase 15: Graph Integration (3-5 days)
- [ ] Add new node types to `NodeIdGenerator`
- [ ] Implement new node classes (BreakpointNode, StackFrameNode, etc.)
- [ ] Add new edge types to `EdgeTypes`
- [ ] Extend `Graph` to support runtime data
- [ ] Extend `Indexes` for fast divergence queries
- [ ] Test: Store and query debugging information from graph

### Phase 16: LLM Integration (2-3 days)
- [ ] Extend `JavaGraphQuery` interface for debugging queries
- [ ] Implement `DebugGraphQuery`
- [ ] Add query methods for:
  - Get divergence points
  - Get variable values at step
  - Get call stack at step
  - Explain invariant violation
- [ ] Test: LLM can query debugging information

### Phase 17: CLI & Testing (3-5 days)
- [ ] Create CLI for differential debugging
- [ ] Implement test cases:
  - Calculator example (add method)
  - Field value invariant
  - Complex call chain divergence
  - Multi-threaded scenarios
- [ ] Performance testing
- [ ] Documentation

---

## 💡 EXAMPLE QUERIES FOR LLM

### After Running Differential Debug Session

```java
// LLM can ask:
JavaGraphQuery query = new DebugGraphQuery(graph);

// 1. Find the first divergence point
Optional<DivergenceNode> firstDiv = query.findFirstDivergence();
if (firstDiv.isPresent()) {
    DivergenceNode div = firstDiv.get();
    System.out.println("First divergence at step: " + div.getStepNumber());
    System.out.println("Between: " + div.getVersionA_NodeId() + " and " + div.getVersionB_NodeId());
}

// 2. Get the AST nodes where divergence occurred
List<Node> divNodes = query.getDivergenceNodes();
for (Node node : divNodes) {
    System.out.println("Divergence at: " + node.getType() + " " + node.getId());
}

// 3. Get variable differences at a divergence point
List<VariableDifference> varDiffs = query.getVariableDifferences(divergenceNode);
for (VariableDifference diff : varDiffs) {
    System.out.println(String.format(
        "Variable '%s' differs: A=%s, B=%s",
        diff.getVariableName(), 
        diff.getValueA(), 
        diff.getValueB()
    ));
}

// 4. Get the call stack at a specific step
List<StackFrameNode> callStack = query.getCallStackAtStep(42);
for (StackFrameNode frame : callStack) {
    MethodNode method = query.getMethod(frame.getMethodId());
    System.out.println("Frame: " + method.getQualifiedName() + " at line " + frame.getLineNumber());
}

// 5. Check if an invariant was violated
List<InvariantNode> violated = query.getViolatedInvariants();
for (InvariantNode inv : violated) {
    System.out.println("Invariant violated: " + inv.getDescription());
}

// 6. Trace back from divergence to understand cause
List<Node> causeChain = query.getDivergenceCauseChain(divergenceNode);
// Returns: [divergence node, causing statement A, causing statement B, ...]
```

### LLM Conversation Example

**User:** "The add method returns different values in commit abc123 vs def456. Why?"

**LLM (using DebugGraphQuery):**
1. Finds `MethodOutputInvariant` for `Calculator.add` was violated
2. Queries `findFirstDivergence()` → returns divergence at step 42
3. Queries `getDivergenceNodes()` → finds it's at a variable declaration
4. Queries variable differences → `helper` variable exists in B but not A
5. Queries AST for version B → finds new line `int helper = a + 1;`
6. **Answer:** "In commit def456, a new line was added at the start of the add method: `int helper = a + 1;`. This increments the first argument before adding, causing add(2,3) to return 6 instead of 5. The first divergence occurred when version B executed this new line, while version A went directly to the return statement."

---

## ⚡ PERFORMANCE CONSIDERATIONS

### Memory Usage
| Component | Estimated Size | Notes |
|-----------|---------------|-------|
| AST Graph | ~100KB per 1000 lines | Your existing implementation |
| Breakpoints | ~1KB per breakpoint | Small overhead |
| Stack Frames | ~500B per frame | Per step |
| Local Variables | ~100B per variable | Per frame |
| Object Snapshots | ~1KB per object | Can be large |
| Divergence Points | ~500B per divergence | Only stored when found |

**Estimated for 1000-step session:** ~1-5MB (manageable)

### Optimization Strategies

1. **Lazy Object Snapshot Capture**
   ```java
   public class ObjectSnapshotPolicy {
       // Only snapshot objects that are:
       // 1. Referenced by tracked variables
       // 2. Part of the invariant check
       // 3. In the current call stack
       public boolean shouldSnapshot(ObjectReference obj, StepContext ctx) {
           return isRelevantToInvariant(obj, ctx) || 
                  isInCallStack(obj, ctx) ||
                  isReferencedByVariable(obj, ctx);
       }
   }
   ```

2. **Value Diffing Optimization**
   ```java
   public class OptimizedValueComparator {
       // Cache equality checks
       private final Map<Long, Boolean> equalityCache = new HashMap<>();
       
       public boolean valuesEqual(Value valA, Value valB) {
           long cacheKey = hashPair(valA, valB);
           if (equalityCache.containsKey(cacheKey)) {
               return equalityCache.get(cacheKey);
           }
           boolean result = deepCompare(valA, valB);
           equalityCache.put(cacheKey, result);
           return result;
       }
   }
   ```

3. **Incremental Comparison**
   ```java
   public class IncrementalComparator {
       private ComparisonResult lastResult;
       
       public ComparisonResult compare(StepResult currA, StepResult currB) {
           if (lastResult == null) {
               // Full comparison on first step
               lastResult = fullCompare(currA, currB);
               return lastResult;
           }
           
           // Only compare what changed since last step
           ComparisonResult result = new ComparisonResult();
           
           // If call stacks are same length, only compare changed frames
           if (currA.getCallStack().size() == currB.getCallStack().size() &&
               lastResult.getCallStackComparison().getFrameCount() == currA.getCallStack().size()) {
               
               // Compare only top frame (most likely to change)
               FrameComparison frameComp = compareFrames(
                   currA.getCurrentFrame(), 
                   currB.getCurrentFrame()
               );
               result.addFrameComparison(0, frameComp);
           } else {
               // Call stack changed, do full comparison
               result = fullCompare(currA, currB);
           }
           
           lastResult = result;
           return result;
       }
   }
   ```

4. **Step Skipping**
   ```java
   public class AdaptiveStepper {
       // Skip steps when both versions are in the same method
       // and not near a potential divergence
       public boolean shouldStepIn detail(ParallelDebugSession session) {
           if (session.hasActiveInvariants()) {
               return true; // Need detailed stepping near invariants
           }
           if (session.getDivergenceTracker().getFirstDivergence() != null) {
               return true; // Already found divergence, step carefully
           }
           // Skip if both are in standard library code
           return !session.getSessionA().isInLibraryCode() &&
                  !session.getSessionB().isInLibraryCode();
       }
   }
   ```

---

## 🐛 EDGE CASES & SOLUTIONS

| Case | Solution |
|------|----------|
| **Different line numbers** | Use method name + bytecode index as fallback |
| **JIT optimization** | Disable JIT with `-Xint` flag for deterministic stepping |
| **Inlined methods** | Track bytecode positions, not just source lines |
| **Lambda expressions** | Use synthetic names from JVM (e.g., `lambda$0`) |
| **Anonymous classes** | Use generated names (e.g., `Calculator$1`) |
| **Native methods** | Skip stepping into native code |
| **Exceptions thrown** | Catch and compare exception objects |
| **Deadlocks** | Add timeout to stepping, detect hung threads |
| **Different thread counts** | Compare threads by name/role, not by ID |
| **Garbage collection** | Use weak references for object tracking |
| **Large object graphs** | Limit snapshot depth, use lazy loading |

---

## 📋 DEPENDENCIES TO ADD

```xml
<!-- pom.xml additions -->

<!-- Java Debug Interface (JDI) - comes with JDK -->
<dependency>
    <groupId>com.sun</groupId>
    <artifactId>tools</artifactId>
    <version>1.8</version>
    <scope>system</scope>
    <systemPath>${java.home}/../lib/tools.jar</systemPath>
</dependency>

<!-- Or for modern JDKs (9+), use jdk.jdi module -->
<!-- No explicit dependency needed, it's part of the JDK -->

<!-- For easier JDI usage -->
<dependency>
    <groupId>com.github.alexandreroman</groupId>
    <artifactId>jdi-utils</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- For testing with embedded JVM -->
<dependency>
    <groupId>com.sun</groupId>
    <artifactId>jdi</artifactId>
    <version>1.8</version>
    <scope>system</scope>
    <systemPath>${java.home}/../lib/tools.jar</systemPath>
</dependency>
```

---

## 🚀 GETTING STARTED

### Minimal Proof of Concept

To test the concept before full implementation:

```java
// 1. Create a simple test case
public class DebugTest {
    public static void main(String[] args) {
        // Version A: returns 5
        int resultA = new CalculatorV1().add(2, 3);
        
        // Version B: returns 6  
        int resultB = new CalculatorV2().add(2, 3);
        
        System.out.println("A: " + resultA + ", B: " + resultB);
    }
}

// 2. Attach debugger to this test
// 3. Set breakpoints at CalculatorV1.add and CalculatorV2.add
// 4. Step through and compare

// 3. Implement minimal DebugSession
public class MinimalDebugSession {
    public static void main(String[] args) throws Exception {
        // Launch target JVM
        LaunchingConnector connector = 
            VirtualMachineManager.virtualMachineManager()
                .defaultConnectors()
                .stream()
                .filter(c -> c.name().equals("com.sun.jdi.CommandLineLaunch"))
                .findFirst()
                .orElseThrow();
        
        Map<String, Argument> args = new HashMap<>();
        args.put("main", new ArgumentImpl("com.example.DebugTest"));
        VirtualMachine vm = connector.launch(args);
        
        // Set breakpoint
        ClassType calculatorClass = (ClassType) vm.classesByName("com.example.CalculatorV1").get(0);
        Method addMethod = calculatorClass.methodsByName("add", "(II)I").get(0);
        Location location = addMethod.allLineLocations().get(0);
        
        BreakpointRequest bp = vm.eventRequestManager()
            .createBreakpointRequest(location);
        bp.enable();
        
        // Resume and wait for breakpoint
        vm.resume();
        EventQueue queue = vm.eventQueue();
        BreakpointEvent event = (BreakpointEvent) queue.remove();
        
        System.out.println("Breakpoint hit at: " + event.location());
        
        // Get local variables
        StackFrame frame = event.thread().frame(0);
        LocalVariable aVar = addMethod.variablesByName("a").get(0);
        Value aValue = frame.getValue(aVar);
        System.out.println("a = " + aValue);
        
        // Continue
        vm.resume();
    }
}
```

---

## ✅ COMPLETION CHECKLIST

- [ ] Design document created (this file)
- [ ] JDI dependencies added
- [ ] DebugSession implemented
- [ ] DebugLinker implemented
- [ ] ParallelDebugSession implemented
- [ ] ComparisonEngine implemented
- [ ] DivergenceTracker implemented
- [ ] Invariant system implemented
- [ ] New node types added to graph
- [ ] New edge types added
- [ ] LLM query interface extended
- [ ] Test cases created
- [ ] Documentation updated
- [ ] Performance optimized

---

## 📚 RESOURCES

- **JDI Documentation**: https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/
- **JDI Tutorial**: https://www.baeldung.com/java-debug-interface
- **Java Debugging**: https://developer.ibm.com/articles/j-jdi/
- **JDI Examples**: https://github.com/alexandreroman/jdi-utils
- **Differential Testing**: https://en.wikipedia.org/wiki/Differential_testing
- **Delta Debugging**: https://en.wikipedia.org/wiki/Delta_debugging

---

## 🎉 NEXT STEPS

1. **Start with Phase 10** - Implement basic `DebugSession` and `DebugLinker`
2. **Test with simple example** - Verify JDI connection and stepping works
3. **Build incrementally** - Add parallel execution, then comparison, then invariants
4. **Integrate with existing graph** - Extend your GausVibe to support runtime data
5. **Add LLM queries** - Make it accessible to Vibe

This differential debugging approach will be **extremely powerful** for:
- Finding regression bugs quickly
- Understanding behavioral differences between versions
- Automating bug diagnosis
- Teaching LLMs to debug effectively

The key insight is that by running both versions in parallel and comparing at every step, you can **automatically find the root cause** of any invariant violation, even deep in the call chain.
