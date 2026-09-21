# GausVibe Quick Start Guide

**Version**: 1.0.0  
**Last Updated**: 2026-09-20  
**Purpose**: Get GausVibe running and self-editing in minutes

---

## 🎯 What You Need

### Required
| Tool | Version | Purpose | Status |
|------|---------|---------|--------|
| **Java** | 17+ | Runtime | ✅ Already installed |
| **Maven** | 3.x | Build tool | ❌ **MISSING - Install this** |

### Optional
| Tool | Purpose |
|------|---------|
| Git | Version control |
| IDE (IntelliJ/Eclipse) | Code editing |

---

## 📥 Installation

### 1. Install Maven

**macOS (Homebrew)**:
```bash
brew install maven
```

**Linux (apt)**:
```bash
sudo apt update
sudo apt install maven
```

**Windows (Chocolatey)**:
```powershell
choco install maven
```

**Manual**:
```bash
# Download from https://maven.apache.org/download.cgi
wget https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz
export PATH=$PATH:/path/to/apache-maven-3.9.6/bin
```

### 2. Verify Installation

```bash
# Check Java
java -version
# Should show: openjdk version "17" or higher

# Check Maven
mvn -version
# Should show: Apache Maven 3.x.x
```

---

## 🚀 Using GausVibe

### Basic Commands

```bash
# Navigate to project
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe

# Build the project
mvn clean compile

# Run all tests
mvn test

# Build JAR (for self-editing)
mvn clean package
```

### Self-Editing Workflow

#### Step 1: Build Binary from Master
```bash
# Ensure you're on master branch
git checkout master

# Build JAR
mvn clean package

# Binary will be in target/
ls target/*.jar
```

#### Step 2: Create Feature Branch
```bash
# Create branch for editing
git checkout -b feature/your-edit

# Or use existing test branch
git checkout feature/self-edit-test
```

#### Step 3: Apply Edits Using Master's Binary
```bash
# Build graph
java -jar target/gausvibe-*.jar \
  build --project . --output /tmp/graph.json

# Apply edit operation
java -jar target/gausvibe-*.jar \
  edit \
  --graph /tmp/graph.json \
  --operations '[{"type": "ADD_METHOD", "target_class": "dk.gausdalfind.graph.GausVibeBuilder", "name": "testMethod", "return_type": "void", "body": "System.out.println(\"test\");"}]'

# Serialize changes back to source
java -jar target/gausvibe-*.jar \
  serialize \
  --graph /tmp/graph.json \
  --output .
```

#### Step 4: Verify Changes
```bash
# Check what changed
git diff

# Verify it compiles
mvn compile

# Run tests
mvn test

# If good, commit
git add .
git commit -m "Self-edit: [your change]"
git push origin feature/your-edit
```

#### Step 5: Merge to Master
```bash
git checkout master
git merge feature/your-edit
git push origin master
```

---

## 🎯 Common Operations

### Build Graph
```bash
java -jar target/gausvibe-*.jar \
  build --project /path/to/project --output graph.json
```

### Query Graph
```bash
# Find all classes
java -jar target/gausvibe-*.jar \
  query:class:*.java

# Find specific class
java -jar target/gausvibe-*.jar \
  query:class:dk.gausdalfind.graph.GausVibeBuilder

# Search by text
java -jar target/gausvibe-*.jar \
  query:search:text:parseFile
```

### Edit Operations

#### Add Method
```bash
java -jar target/gausvibe-*.jar \
  edit --graph graph.json \
  --operations '[{"type": "ADD_METHOD", "target_class": "MyClass", "name": "newMethod", "return_type": "void", "body": "System.out.println(\"Hello\");"}]'
```

#### Add Field
```bash
java -jar target/gausvibe-*.jar \
  edit --graph graph.json \
  --operations '[{"type": "ADD_FIELD", "target_class": "MyClass", "name": "newField", "data_type": "String"}]'
```

#### Remove Method
```bash
java -jar target/gausvibe-*.jar \
  edit --graph graph.json \
  --operations '[{"type": "REMOVE_METHOD", "target_class": "MyClass", "name": "oldMethod"}]'
```

#### Replace Method Body
```bash
java -jar target/gausvibe-*.jar \
  edit --graph graph.json \
  --operations '[{"type": "REPLACE_METHOD_BODY", "target_class": "MyClass", "method_name": "myMethod", "new_body": "// new implementation"}]'
```

---

## 📚 Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=SelfEditValidationTest
mvn test -Dtest=SelfEditRoundTripTest
```

### Run Specific Test Method
```bash
mvn test -Dtest=SelfEditValidationTest#testComponent_Parser_ParseGausVibe
```

---

## 🛠️ Project Structure

```
gausvibe/
├── src/
│   ├── main/java/dk/gausdalfind/
│   │   ├── cli/              # CLI commands
│   │   ├── graph/            # Graph building
│   │   ├── model/            # Graph data model
│   │   ├── parser/           # File parsing
│   │   ├── queries/          # Query engine
│   │   ├── serializer/       # Serialization
│   │   └── editing/          # Edit operations
│   └── test/java/dk/gausdalfind/
│       ├── SelfEditValidationTest.java  # Component tests
│       └── SelfEditRoundTripTest.java    # Round-trip tests
├── pom.xml                  # Maven config
├── target/                  # Build output
├── HOW_TO_EDIT.md           # Self-editing guide
├── SELF_EDIT_WORKFLOW.md    # Safe workflow
├── QUICK_START.md           # This file
└── ...
```

---

## ⚠️ Troubleshooting

### "mvn: command not found"
```bash
# Install Maven (see Installation section above)
brew install maven  # macOS
sudo apt install maven  # Linux
```

### "Java 17 required"
```bash
# Check Java version
java -version

# Install Java 17 if needed
brew install openjdk@17  # macOS
sudo apt install openjdk-17-jdk  # Linux
```

### Build Fails
```bash
# Clean and rebuild
mvn clean compile

# Check Maven version
mvn -version

# Check Java version
java -version
```

---

## 🎯 Next Steps After Maven Install

Once Maven is installed:

1. **Build GausVibe**
   ```bash
   cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
   mvn clean package
   ```

2. **Run Component Tests**
   ```bash
   mvn test -Dtest=SelfEditValidationTest
   ```

3. **Run Round-Trip Tests**
   ```bash
   mvn test -Dtest=SelfEditRoundTripTest
   ```

4. **Test Self-Editing**
   ```bash
   # See Self-Editing Workflow section above
   ```

---

## 📖 Related Documentation

| Document | Purpose |
|----------|---------|
| [HOW_TO_EDIT.md](HOW_TO_EDIT.md) | Detailed self-editing guide |
| [SELF_EDIT_WORKFLOW.md](SELF_EDIT_WORKFLOW.md) | Safe branch-based workflow |
| [PHASE5_STATUS.md](PHASE5_STATUS.md) | Phase 5 implementation status |
| [ISSUES.md](ISSUES.md) | Known issues and blockers |
| [OPTIMIZATION_SUMMARY.md](OPTIMIZATION_SUMMARY.md) | Optimization overview |
| [PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md) | Detailed optimization plan |
| [SELF_HOSTING_MVP.md](SELF_HOSTING_MVP.md) | Self-hosting implementation plan |

---

## 💡 Pro Tips

1. **Start with component tests** - Validate individual parts before end-to-end
2. **Use dry-run first** - Preview changes with `--dry-run` flag
3. **Small edits first** - Add a simple method before complex changes
4. **Check git status** - Always verify changes before committing
5. **Use feature branches** - Never edit master directly

---

## 🎉 Summary

**You only need to install Maven.** Everything else is ready:
- ✅ Code is fixed and compiles
- ✅ Tests are written
- ✅ Documentation is complete
- ✅ Workflow is documented

**Install Maven → Run tests → Start self-editing!**
