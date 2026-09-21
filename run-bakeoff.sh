#!/bin/bash
# GausVibe Bake-Off Execution Script
# Run this to execute the full bake-off test

PROJECT_DIR="/Users/magnusfind/Documents/find-shadow-model/gausvibe"
cd "$PROJECT_DIR"

# Ensure we have the classpath
if [ ! -f /tmp/classpath.txt ]; then
    echo "Getting Maven classpath..."
    JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/classpath.txt
fi

# Run the execution script
bash "$PROJECT_DIR/bakeoff.sh"
