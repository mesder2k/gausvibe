# GausVibe Bake-Off Plan

## Objective
Compare token usage and output quality between GausVibe and alternative approaches for understanding and modifying Java codebases.

## Test Subject: Simple Java Application

### Sample Application Specification
Create a small but realistic Java application that demonstrates common patterns:

**Application: Simple Calculator Service**

```java
package com.example.calculator;

public interface Calculator {
    double add(double a, double b);
    double subtract(double a, double b);
    double multiply(double a, double b);
    double divide(double a, double b);
}

package com.example.calculator;

public class StandardCalculator implements Calculator {
    @Override
    public double add(double a, double b) {
        return a + b;
    }
    
    @Override
    public double subtract(double a, double b) {
        return a - b;
    }
    
    @Override
    public double multiply(double a, double b) {
        return a * b;
    }
    
    @Override
    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }
}

package com.example.calculator;

public class ScientificCalculator extends StandardCalculator {
    public double sqrt(double a) {
        return Math.sqrt(a);
    }
    
    public double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }
}

package com.example;

public class Main {
    public static void main(String[] args) {
        Calculator calculator = new StandardCalculator();
        System.out.println("2 + 3 = " + calculator.add(2, 3));
        
        ScientificCalculator sciCalc = new ScientificCalculator();
        System.out.println("Square root of 16 = " + sciCalc.sqrt(16));
    }
}
```

### Application Characteristics
- **Files**: 4 Java files
- **Lines of Code**: ~60 lines
- **Classes**: 1 interface, 2 concrete classes, 1 main class
- **Methods**: 7 methods (4 interface, 4 implementation, 2 extended)
- **Complexity**: Low to medium (inheritance, method overrides, basic error handling)

---

## Comparison Methodology

### Approach 1: Direct LLM Prompting (Baseline)

**Prompt**: "Here is a Java codebase. [paste all files]. Please explain the structure and relationships between classes."

**Metrics to Track**:
- Token count (input + output)
- Accuracy of structural analysis
- Completeness of relationship mapping

### Approach 2: GausVibe Analysis

**Steps**:
1. Run GausVibe on the codebase to build a graph
2. Query the graph for structural information
3. Export relevant information
4. Feed structured data to LLM

**Commands**:
```bash
# Build graph
java -jar gausvibe.jar build --project ./calculator-app --output graph.json

# Query for class hierarchy
java -jar gausvibe.jar query --graph graph.json "class:all"
java -jar gausvibe.jar query --graph graph.json "class:subclasses:com.example.calculator.StandardCalculator"
java -jar gausvibe.jar query --graph graph.json "class:implementations:com.example.calculator.Calculator"

# Query for methods
java -jar gausvibe.jar query --graph graph.json "method:class:com.example.calculator.StandardCalculator"

# Get full structure
java -jar gausvibe.jar query --graph graph.json "stats"
```

**Metrics to Track**:
- Token count for building graph (serialization tokens)
- Token count for queries
- Token count for structured output fed to LLM
- Total token efficiency

---

## Bake-Off Execution Plan

### Phase 1: Prepare Test Environment

1. **Create Sample Application**
   - Create directory: `bakeoff-test/calculator-app/`
   - Add the 4 Java files listed above
   - Verify it compiles: `javac com/example/*.java com/example/calculator/*.java`

2. **Measure Baseline (Direct LLM)**
   - Count input tokens (all Java files concatenated)
   - Prepare prompt template
   - Run with LLM, count output tokens
   - Document: `BAKEOFF_RESULTS.md`

3. **Prepare GausVibe**
   - Build GausVibe: `mvn clean package -DskipTests`
   - Create jar file for easy execution
   - Verify it works on sample app

### Phase 2: Run GausVibe Analysis

1. **Build Graph**
   ```bash
   cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
   java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
     build --project ../bakeoff-test/calculator-app --output /tmp/calculator-graph.json --serialize
   ```

2. **Query Graph**
   ```bash
   # Get all classes
   java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
     query --graph /tmp/calculator-graph.json "class:all"
   
   # Get class hierarchy
   java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
     query --graph /tmp/calculator-graph.json "class:subclasses:com.example.calculator.StandardCalculator"
   
   # Get all methods
   java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
     query --graph /tmp/calculator-graph.json "method:all"
   ```

3. **Calculate Token Counts**
   - Token count for original source files
   - Token count for graph JSON
   - Token count for query outputs
   - Total token overhead

### Phase 3: Compare Results

Create comparison table:

| Metric | Direct LLM | GausVibe + LLM | Improvement |
|--------|------------|----------------|-------------|
| Input Tokens | X | Y | (X-Y)/X % |
| Output Tokens | A | B | (A-B)/A % |
| Total Tokens | X+A | Y+B | Overall % |
| Accuracy | % | % | Δ% |
| Completeness | % | % | Δ% |

### Phase 4: Analysis Tasks

Define specific tasks for both approaches:

**Task 1: Class Discovery**
- Direct: "List all classes in this codebase"
- GausVibe: `query "class:all"`

**Task 2: Interface Implementations**
- Direct: "Which classes implement the Calculator interface?"
- GausVibe: `query "class:implementations:com.example.calculator.Calculator"`

**Task 3: Inheritance Hierarchy**
- Direct: "Show the inheritance tree for ScientificCalculator"
- GausVibe: `query "class:subclasses:com.example.calculator.StandardCalculator"`

**Task 4: Method Inventory**
- Direct: "List all methods in StandardCalculator"
- GausVibe: `query "method:class:com.example.calculator.StandardCalculator"`

**Task 5: Method Overrides**
- Direct: "Which methods in ScientificCalculator override methods from StandardCalculator?"
- GausVibe: Query for methods, then filter by class

### Phase 5: Token Calculation Methodology

**For Direct LLM Approach**:
```
Input Tokens = count_tokens(all_java_files + prompt)
Output Tokens = count_tokens(llm_response)
Total Tokens = Input Tokens + Output Tokens
```

**For GausVibe Approach**:
```
Graph Build Tokens = count_tokens(graph_json_serialization)  
Query Tokens = sum(count_tokens(query_output) for all queries)
LLM Input Tokens = count_tokens(structured_data + prompt)
LLM Output Tokens = count_tokens(llm_response)
Total Tokens = Graph Build Tokens + Query Tokens + LLM Input Tokens + LLM Output Tokens
```

**Efficiency Ratio**:
```
Efficiency = (Direct Total Tokens - GausVibe Total Tokens) / Direct Total Tokens * 100
```

---

## Expected Outcomes

### Hypothesis
- GausVibe will use MORE tokens for graph building but FEWER tokens for queries
- Net result: Lower total token usage for complex analytical tasks
- Better accuracy and completeness for structural queries

### Success Criteria
1. **Token Efficiency**: GausVibe approach uses ≤ 80% of direct LLM tokens for the test scenario
2. **Accuracy**: GausVibe + LLM achieves ≥ 95% accuracy on structural queries
3. **Completeness**: GausVibe + LLM finds ≥ 90% of all structural relationships

---

## Test Execution Script

```bash
#!/bin/bash

# Bake-Off Test Script
TEST_DIR="/tmp/gausvibe-bakeoff"
APP_DIR="$TEST_DIR/calculator-app"
RESULTS_DIR="$TEST_DIR/results"

# Create directories
mkdir -p "$APP_DIR/com/example/calculator" "$APP_DIR/com/example" "$RESULTS_DIR"

# Create Calculator.java
cat > "$APP_DIR/com/example/calculator/Calculator.java" << 'EOF'
package com.example.calculator;

public interface Calculator {
    double add(double a, double b);
    double subtract(double a, double b);
    double multiply(double a, double b);
    double divide(double a, double b);
}
EOF

# Create StandardCalculator.java
cat > "$APP_DIR/com/example/calculator/StandardCalculator.java" << 'EOF'
package com.example.calculator;

public class StandardCalculator implements Calculator {
    @Override
    public double add(double a, double b) {
        return a + b;
    }
    
    @Override
    public double subtract(double a, double b) {
        return a - b;
    }
    
    @Override
    public double multiply(double a, double b) {
        return a * b;
    }
    
    @Override
    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }
}
EOF

# Create ScientificCalculator.java
cat > "$APP_DIR/com/example/calculator/ScientificCalculator.java" << 'EOF'
package com.example.calculator;

public class ScientificCalculator extends StandardCalculator {
    public double sqrt(double a) {
        return Math.sqrt(a);
    }
    
    public double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }
}
EOF

# Create Main.java
cat > "$APP_DIR/com/example/Main.java" << 'EOF'
package com.example;

public class Main {
    public static void main(String[] args) {
        Calculator calculator = new StandardCalculator();
        System.out.println("2 + 3 = " + calculator.add(2, 3));
        
        ScientificCalculator sciCalc = new ScientificCalculator();
        System.out.println("Square root of 16 = " + sciCalc.sqrt(16));
    }
}
EOF

# Compile the test app
cd "$APP_DIR"
javac com/example/*.java com/example/calculator/*.java

# Measure file sizes and token counts
echo "=== Direct LLM Approach ==="
cat com/example/calculator/*.java com/example/*.java > "$RESULTS_DIR/all_source.txt"
wc -c "$RESULTS_DIR/all_source.txt"

# Run GausVibe (assuming jar is built)
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
  build --project "$APP_DIR" --output "$RESULTS_DIR/graph.json" --serialize

# Measure graph size
echo "=== GausVibe Graph ==="
wc -c "$RESULTS_DIR/graph.json"

# Run queries
java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
  query --graph "$RESULTS_DIR/graph.json" "class:all" > "$RESULTS_DIR/classes.txt"

java -cp target/gausvibe-1.0.0.jar:target/classes dk.gausdalfind.Main \
  query --graph "$RESULTS_DIR/graph.json" "method:all" > "$RESULTS_DIR/methods.txt"

echo "=== Query Outputs ==="
wc -c "$RESULTS_DIR/classes.txt" "$RESULTS_DIR/methods.txt"

echo "Bake-off test completed. Results in $RESULTS_DIR"
```

---

## Token Counting Tools

### Option 1: Use tiktoken (Python)
```python
from tiktoken import encode

def count_tokens(text):
    return len(encode(text, model="claud-3-sonnet-20250219"))

# Count source files
with open("all_source.txt") as f:
    source_tokens = count_tokens(f.read())

# Count graph
with open("graph.json") as f:
    graph_tokens = count_tokens(f.read())

# Count queries
query_tokens = 0
for query_file in ["classes.txt", "methods.txt"]:
    with open(query_file) as f:
        query_tokens += count_tokens(f.read())

print(f"Source tokens: {source_tokens}")
print(f"Graph tokens: {graph_tokens}")
print(f"Query tokens: {query_tokens}")
print(f"Total GausVibe tokens: {graph_tokens + query_tokens}")
```

### Option 2: Use LLM API Token Count
Many LLM providers return token usage in API responses.

### Option 3: Approximate Counting
- 1 token ≈ 4 characters (for English text)
- 1 token ≈ 2-3 characters (for code)
- JSON structures typically more verbose

---

## Deliverables

1. **BAKEOFF_PLAN.md** - This document
2. **bakeoff-test/** - Test application directory
3. **bakeoff-script.sh** - Automated test execution script
4. **BAKEOFF_RESULTS.md** - Results documentation with:
   - Token counts for each approach
   - Accuracy comparisons
   - Completeness analysis
   - Efficiency metrics

---

## Timeline

| Phase | Task | Estimated Time |
|-------|------|----------------|
| 1 | Prepare test environment | 15 min |
| 2 | Create sample application | 10 min |
| 3 | Run baseline (Direct LLM) | 10 min |
| 4 | Build GausVibe graph | 5 min |
| 5 | Run GausVibe queries | 10 min |
| 6 | Count tokens | 15 min |
| 7 | Compare results | 20 min |
| 8 | Document findings | 15 min |
| **Total** | | **~1.5-2 hours** |

---

## Next Steps

1. ✅ **DONE**: Fix all compilation errors
2. ⏳ **NEXT**: Build GausVibe jar
3. ⏳ **NEXT**: Create test application
4. ⏳ **NEXT**: Execute bake-off
5. ⏳ **NEXT**: Document results

**Ready to execute?**
