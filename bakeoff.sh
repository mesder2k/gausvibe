#!/bin/bash
# GausVibe Bake-Off Execution Script
# Extracted from BAKEOFF_EXECUTION.md so it can actually be run by hand.

set -e

echo "=========================================="
echo "GausVibe Bake-Off Test"
echo "=========================================="

# Setup
PROJECT_DIR="/Users/magnusfind/Documents/find-shadow-model/gausvibe"
TEST_APP_DIR="/tmp/bakeoff-test"
RESULTS_DIR="/tmp/bakeoff-results"

# Clean and create directories
rm -rf "$TEST_APP_DIR" "$RESULTS_DIR"
mkdir -p "$TEST_APP_DIR/com/example/calculator" "$TEST_APP_DIR/com/example" "$RESULTS_DIR"

# Get Maven classpath
cd "$PROJECT_DIR"
if [ ! -f /tmp/classpath.txt ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/classpath.txt
fi
CP=$(cat /tmp/classpath.txt)
JAVA="java"
if [ -x /usr/libexec/java_home ]; then
    JAVA="/usr/libexec/java_home -v 17 --exec java"
fi

echo ""
echo "Step 1: Creating test application..."

# Create Calculator.java
cat > "$TEST_APP_DIR/com/example/calculator/Calculator.java" << 'EOF'
package com.example.calculator;

public interface Calculator {
    double add(double a, double b);
    double subtract(double a, double b);
    double multiply(double a, double b);
    double divide(double a, double b);
}
EOF

# Create StandardCalculator.java
cat > "$TEST_APP_DIR/com/example/calculator/StandardCalculator.java" << 'EOF'
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
cat > "$TEST_APP_DIR/com/example/calculator/ScientificCalculator.java" << 'EOF'
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
cat > "$TEST_APP_DIR/com/example/Main.java" << 'EOF'
package com.example;

import com.example.calculator.*;

public class Main {
    public static void main(String[] args) {
        Calculator calculator = new StandardCalculator();
        System.out.println("2 + 3 = " + calculator.add(2, 3));

        ScientificCalculator sciCalc = new ScientificCalculator();
        System.out.println("Square root of 16 = " + sciCalc.sqrt(16));
    }
}
EOF

echo "Test application created at $TEST_APP_DIR"

# Verify it compiles
cd "$TEST_APP_DIR"
javac com/example/*.java com/example/calculator/*.java
echo "Test application compiles"

echo ""
echo "Step 2: Running Direct LLM Approach (Baseline)..."

# Concatenate all source files
cat com/example/calculator/*.java com/example/*.java > "$RESULTS_DIR/direct_input.txt"

# Count characters (for token estimation)
DIRECT_CHARS=$(wc -c < "$RESULTS_DIR/direct_input.txt" | tr -d ' ')
DIRECT_TOKENS=$((DIRECT_CHARS / 4))  # Approximate: 4 chars per token

echo "  Direct input: $DIRECT_CHARS characters (~$DIRECT_TOKENS tokens)"
echo "  -> Stored in: $RESULTS_DIR/direct_input.txt"

echo ""
echo "Step 3: Running GausVibe Approach..."

cd "$PROJECT_DIR"

# Build GausVibe graph
echo "  Building graph..."
$JAVA -cp "target/classes:$CP" dk.gausdalfind.Main \
  build --project "$TEST_APP_DIR" --output "$RESULTS_DIR/graph.json" --serialize 2>&1 | grep -E "Nodes:|Edges:|Graph serialized" | tail -1

# Count graph size
GRAPH_CHARS=$(wc -c < "$RESULTS_DIR/graph.json" | tr -d ' ')
GRAPH_TOKENS=$((GRAPH_CHARS / 4))
echo "  Graph JSON: $GRAPH_CHARS characters (~$GRAPH_TOKENS tokens)"

# Run queries
echo "  Running queries..."

$JAVA -cp "target/classes:$CP" dk.gausdalfind.Main \
  query --graph "$RESULTS_DIR/graph.json" "class:all" > "$RESULTS_DIR/classes.txt"

$JAVA -cp "target/classes:$CP" dk.gausdalfind.Main \
  query --graph "$RESULTS_DIR/graph.json" "method:all" > "$RESULTS_DIR/methods.txt"

$JAVA -cp "target/classes:$CP" dk.gausdalfind.Main \
  query --graph "$RESULTS_DIR/graph.json" "class:implementations:com.example.calculator.Calculator" > "$RESULTS_DIR/implementations.txt"

$JAVA -cp "target/classes:$CP" dk.gausdalfind.Main \
  query --graph "$RESULTS_DIR/graph.json" "class:subclasses:com.example.calculator.StandardCalculator" > "$RESULTS_DIR/subclasses.txt"

# Count query output sizes
QUERY_CHARS=0
for f in classes.txt methods.txt implementations.txt subclasses.txt; do
    QUERY_CHARS=$((QUERY_CHARS + $(wc -c < "$RESULTS_DIR/$f" | tr -d ' ')))
done
QUERY_TOKENS=$((QUERY_CHARS / 4))
echo "  Query outputs: $QUERY_CHARS characters (~$QUERY_TOKENS tokens)"

GAUSVIBE_TOKENS=$((GRAPH_TOKENS + QUERY_TOKENS))

echo ""
echo "Step 4: Calculating Results..."

echo ""
echo "=========================================="
echo "          BAKE-OFF RESULTS"
echo "=========================================="
echo ""
echo "Direct LLM Approach:"
echo "  Input Tokens:      ~$DIRECT_TOKENS"
echo "  (Estimate based on character count / 4)"
echo ""
echo "GausVibe Approach:"
echo "  Graph Tokens:     ~$GRAPH_TOKENS"
echo "  Query Tokens:     ~$QUERY_TOKENS"
echo "  Total Tokens:     ~$GAUSVIBE_TOKENS"
echo ""

# Calculate efficiency
if [ $DIRECT_TOKENS -gt 0 ]; then
    if [ $GAUSVIBE_TOKENS -lt $DIRECT_TOKENS ]; then
        SAVINGS=$((100 * (DIRECT_TOKENS - GAUSVIBE_TOKENS) / DIRECT_TOKENS))
        echo "Token Savings:     $SAVINGS% MORE EFFICIENT"
    else
        OVERHEAD=$((100 * (GAUSVIBE_TOKENS - DIRECT_TOKENS) / DIRECT_TOKENS))
        echo "Token Overhead:    $OVERHEAD% MORE TOKENS"
    fi
fi

echo ""
echo "=========================================="
echo "Test Results Saved To:"
echo "  $RESULTS_DIR/"
echo ""
echo "Files:"
echo "  - direct_input.txt (raw source)"
echo "  - graph.json (GausVibe graph)"
echo "  - classes.txt (query: class:all)"
echo "  - methods.txt (query: method:all)"
echo "  - implementations.txt (query: class:implementations)"
echo "  - subclasses.txt (query: class:subclasses)"
echo ""
echo "=========================================="
echo "Bake-off execution complete!"
echo "=========================================="

# Print file sizes for reference
echo ""
echo "File Sizes:"
ls -lh "$RESULTS_DIR/"
