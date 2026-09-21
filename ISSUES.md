# GausVibe Compilation Issues

## Current Status

After fixing several compilation errors, there are still multiple issues preventing a clean Maven build. This document tracks all known issues.

## Fixed Issues

1. ✅ **AddFieldOperation.java**: Renamed `getType()` to `getFieldType()` to resolve duplicate method with Operation interface
2. ✅ **ASTEditor.java line 179**: Changed `op.getType()` to `op.getFieldType()` for AddFieldOperation
3. ✅ **DeclarationVisitor.java**: Fixed ambiguous Node references by using fully qualified names
4. ✅ **NodeFactory.java**: Fixed ambiguous Node references in create() method and getParameterPosition()
5. ✅ **ASTSourceSerializer.java**: Fixed ambiguous Node references in List parameters
6. ✅ **ASTEditor.java**: Fixed NodeIdGenerator import (was using parser package instead of model package)

## Remaining Compilation Errors

### High Priority (Blocking Compilation)

#### 1. ASTEditor.java
- **Issue**: MethodNode constructor call with wrong number of arguments
- **Location**: Lines 104-112 (createMethodNode) and 185-193 (createFieldNode)
- **Error**: MethodNode constructor expects 12 arguments but 13 are provided
- **Details**: The constructor signature doesn't match. Need to check MethodNode and FieldNode constructors

#### 2. ASTEditor.java
- **Issue**: Missing methods in Indexes class
- **Location**: Lines 127, 147
- **Error**: `getMethodByQualifiedName(String)` not found in Indexes class
- **Action**: Need to add this method to Indexes class or use alternative approach

#### 3. NodeFactory.java
- **Issue**: Ambiguous Node reference in nodeCache
- **Location**: Line 27 (field declaration), Line 438 (getNode method)
- **Error**: Both com.github.javaparser.ast.Node and dk.gausdalfind.model.Node match
- **Action**: Use fully qualified names throughout

#### 4. GausVibeBuilder.java
- **Issue**: TypeDeclaration import
- **Location**: Line 274
- **Error**: TypeDeclaration not found in com.github.javaparser.ast
- **Action**: TypeDeclaration was removed or moved in JavaParser 3.25.9. Need to update to use specific type declarations

#### 5. StatementVisitor.java
- **Issue**: Missing Scope class
- **Location**: Line 37
- **Error**: Scope class not found
- **Action**: Need to import or implement Scope class

#### 6. JsonSerializer.java
- **Issue**: JsonReader class not found
- **Location**: Lines 592, 635
- **Error**: JsonReader not found (Gson library issue)
- **Action**: Need to use correct Gson API for reading JSON

#### 7. JsonSerializer.java
- **Issue**: Syntax error in lambda
- **Location**: Line 647
- **Error**: Interface expected, type does not take parameters
- **Action**: Fix lambda syntax

#### 8. IncrementalGraphBuilder.java
- **Issue**: Missing Scope class
- **Location**: Line 244
- **Error**: Scope not found
- **Action**: Need to import com.github.javaparser.symbolsolver.javaparseravestructure.Scope or similar

#### 9. ParallelGraphBuilder.java
- **Issue**: Missing Scope class
- **Location**: Line 202
- **Error**: Scope not found
- **Action**: Same as above

#### 10. SymbolTable.java
- **Issue**: Type mismatch in stream operations
- **Location**: Lines 673-674
- **Error**: Map.Entry mismatch and lambda return type
- **Action**: Fix stream operations to handle Map<String, MethodNode> correctly

#### 11. TextSearchIndex.java
- **Issue**: Node interface methods not found
- **Location**: Lines 224-236
- **Error**: getName(), getQualifiedName(), getPackageName(), getDescription() not found in Node interface
- **Action**: These methods don't exist in Node interface. Need to cast to specific node types or add methods to Node interface

#### 12. GraphQueryEngine.java
- **Issue**: Missing getClassNode() method
- **Location**: Line 223
- **Error**: getClassNode() not found in MethodNode
- **Action**: Add getClassNode() to MethodNode or use alternative approach

#### 13. JavaParserConfig.java
- **Issue**: ReflectionTypeSolver constructor
- **Location**: Line 44
- **Error**: No suitable constructor found
- **Action**: Update to use correct ReflectionTypeSolver constructor

#### 14. JavaParserConfig.java
- **Issue**: setSymbolSolver method
- **Location**: Lines 56, 85
- **Error**: setSymbolSolver not found in ParserConfiguration
- **Action**: Check JavaParser 3.25.9 API for correct method name

#### 15. ASTSourceSerializer.java
- **Issue**: JavaParser parseBlock is non-static
- **Location**: Line 363
- **Error**: Cannot reference parseBlock from static context
- **Action**: Use JavaParser.parseBlock() correctly or fix the usage

#### 16. ASTSourceSerializer.java
- **Issue**: Path vs String type mismatch
- **Location**: Line 422
- **Error**: Path cannot be converted to String
- **Action**: Convert Path to String or fix method signature

#### 17. EditCommand.java
- **Issue**: toJson method not found
- **Location**: Line 114
- **Error**: toJson(Map) not found in JsonSerializer
- **Action**: Add toJson method to JsonSerializer or use correct method

### Medium Priority (API Changes)

#### 18. ASTSourceSerializer.java - Modifier API
- **Issue**: addModifier expects Modifier.Keyword[] not Modifier
- **Status**: Partially fixed - need to verify all usages
- **Action**: Convert Modifier objects to Modifier.Keyword arrays

### Low Priority (Can be addressed later)

#### 19. Various files
- **Issue**: Deprecated API warnings
- **Action**: Update to use non-deprecated APIs

#### 20. CachingQueryEngine.java
- **Issue**: Unchecked/unsafe operations warnings
- **Action**: Add proper type safety

## Recommendations

1. **Fix Node interface**: Add missing methods (getName, getQualifiedName, etc.) to the Node interface or ensure all usages cast to specific node types
2. **Update JavaParser dependencies**: Verify all JavaParser API usages match version 3.25.9
3. **Add missing methods**: Add getMethodByQualifiedName to Indexes class
4. **Fix Scope imports**: Add proper imports for Scope class in graph builders
5. **Fix Gson usage**: Update JsonSerializer to use correct Gson API
6. **Test incrementally**: After fixing each issue, run `mvn compile` to verify progress

## Build Command

```bash
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean compile
```

## Files with Known Issues

- src/main/java/dk/gausdalfind/editing/ASTEditor.java
- src/main/java/dk/gausdalfind/graph/GausVibeBuilder.java
- src/main/java/dk/gausdalfind/graph/IncrementalGraphBuilder.java
- src/main/java/dk/gausdalfind/graph/ParallelGraphBuilder.java
- src/main/java/dk/gausdalfind/model/Indexes.java
- src/main/java/dk/gausdalfind/model/Node.java
- src/main/java/dk/gausdalfind/model/declaration/MethodNode.java
- src/main/java/dk/gausdalfind/parser/JavaParserConfig.java
- src/main/java/dk/gausdalfind/parser/NodeFactory.java
- src/main/java/dk/gausdalfind/parser/StatementVisitor.java
- src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java
- src/main/java/dk/gausdalfind/queries/TextSearchIndex.java
- src/main/java/dk/gausdalfind/serializer/ASTSourceSerializer.java
- src/main/java/dk/gausdalfind/serializer/JsonSerializer.java
- src/main/java/dk/gausdalfind/symbols/SymbolTable.java
- src/main/java/dk/gausdalfind/cli/EditCommand.java
