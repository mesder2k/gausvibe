package dk.gausdalfind;

import dk.gausdalfind.cli.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI components (QueryParser and QueryCommand).
 */
public class TestCLI {
    
    private QueryParser parser;
    
    @BeforeEach
    void setup() {
        parser = new QueryParser();
    }
    
    // ==================== QueryParser Tests ====================
    
    @Test
    void testParseNullQuery() {
        QueryCommand result = parser.parse(null);
        assertNull(result);
    }
    
    @Test
    void testParseEmptyQuery() {
        QueryCommand result = parser.parse("");
        assertNull(result);
    }
    
    @Test
    void testParseBlankQuery() {
        QueryCommand result = parser.parse("   ");
        assertNull(result);
    }
    
    @Test
    void testParseSimpleQuery() {
        QueryCommand result = parser.parse("class:com.example.MyClass");
        assertNotNull(result);
        assertEquals("class", result.getType());
        assertEquals(1, result.getArgumentCount());
        assertEquals("com.example.MyClass", result.getArgument(0));
    }
    
    @Test
    void testParseQueryWithMultipleArgs() {
        QueryCommand result = parser.parse("method:class:com.example.MyClass");
        assertNotNull(result);
        assertEquals("method", result.getType());
        assertEquals(2, result.getArgumentCount());
        assertEquals("class", result.getArgument(0));
        assertEquals("com.example.MyClass", result.getArgument(1));
    }
    
    @Test
    void testParseQueryWithSpaces() {
        QueryCommand result = parser.parse("  class:  com.example.MyClass  ");
        assertNotNull(result);
        assertEquals("class", result.getType());
        assertEquals("com.example.MyClass", result.getArgument(0));
    }
    
    @Test
    void testParseBuildCommand() {
        QueryCommand result = parser.parse("build");
        assertNotNull(result);
        assertEquals("build", result.getType());
        assertEquals(0, result.getArgumentCount());
    }
    
    @Test
    void testParseHelpCommand() {
        QueryCommand result = parser.parse("help");
        assertNotNull(result);
        assertEquals("help", result.getType());
    }
    
    @Test
    void testParseVersionCommand() {
        QueryCommand result = parser.parse("version");
        assertNotNull(result);
        assertEquals("version", result.getType());
    }
    
    @Test
    void testParseExitCommand() {
        QueryCommand result = parser.parse("exit");
        assertNotNull(result);
        assertEquals("exit", result.getType());
    }
    
    @Test
    void testParseClearCommand() {
        QueryCommand result = parser.parse("clear");
        assertNotNull(result);
        assertEquals("clear", result.getType());
    }
    
    @Test
    void testParseStatsQuery() {
        QueryCommand result = parser.parse("stats");
        assertNotNull(result);
        assertEquals("stats", result.getType());
        assertEquals(0, result.getArgumentCount());
    }
    
    @Test
    void testParseSearchQuery() {
        QueryCommand result = parser.parse("search:nodes:CLASS");
        assertNotNull(result);
        assertEquals("search", result.getType());
        assertEquals(2, result.getArgumentCount());
        assertEquals("nodes", result.getArgument(0));
        assertEquals("CLASS", result.getArgument(1));
    }
    
    @Test
    void testParseQueryWithEmptyArgs() {
        QueryCommand result = parser.parse("type::arg");
        assertNotNull(result);
        assertEquals("type", result.getType());
        assertEquals(2, result.getArgumentCount());
        assertEquals("", result.getArgument(0));
        assertEquals("arg", result.getArgument(1));
    }
    
    @Test
    void testIsBuildCommand() {
        assertTrue(parser.isBuildCommand("build"));
        assertTrue(parser.isBuildCommand("build --project /path"));
        assertTrue(parser.isBuildCommand("build:"));
        assertFalse(parser.isBuildCommand("query"));
        assertFalse(parser.isBuildCommand(null));
        assertFalse(parser.isBuildCommand(""));
    }
    
    @Test
    void testIsHelpCommand() {
        assertTrue(parser.isHelpCommand("help"));
        assertTrue(parser.isHelpCommand("?"));
        assertTrue(parser.isHelpCommand("--help"));
        assertTrue(parser.isHelpCommand("-h"));
        assertFalse(parser.isHelpCommand("query"));
    }
    
    @Test
    void testIsExitCommand() {
        assertTrue(parser.isExitCommand("exit"));
        assertTrue(parser.isExitCommand("quit"));
        assertTrue(parser.isExitCommand("q"));
        assertFalse(parser.isExitCommand("query"));
    }
    
    // ==================== QueryCommand Tests ====================
    
    @Test
    void testQueryCommandNullType() {
        assertThrows(NullPointerException.class, () -> {
            new QueryCommand(null);
        });
    }
    
    @Test
    void testQueryCommandWithNullArguments() {
        QueryCommand cmd = new QueryCommand("test", null);
        assertEquals("test", cmd.getType());
        assertEquals(0, cmd.getArgumentCount());
    }
    
    @Test
    void testQueryCommandWithEmptyList() {
        QueryCommand cmd = new QueryCommand("test", new ArrayList<>());
        assertEquals("test", cmd.getType());
        assertEquals(0, cmd.getArgumentCount());
    }
    
    @Test
    void testQueryCommandHasArguments() {
        QueryCommand cmd = new QueryCommand("test", Arrays.asList("arg1", "arg2"));
        assertTrue(cmd.hasArguments());
    }
    
    @Test
    void testQueryCommandNoArguments() {
        QueryCommand cmd = new QueryCommand("test");
        assertFalse(cmd.hasArguments());
    }
    
    @Test
    void testQueryCommandGetArgumentOutOfBounds() {
        QueryCommand cmd = new QueryCommand("test", Arrays.asList("arg1"));
        assertThrows(IndexOutOfBoundsException.class, () -> {
            cmd.getArgument(1);
        });
    }
    
    @Test
    void testQueryCommandGetArgumentNegativeIndex() {
        QueryCommand cmd = new QueryCommand("test", Arrays.asList("arg1"));
        assertThrows(IndexOutOfBoundsException.class, () -> {
            cmd.getArgument(-1);
        });
    }
    
    @Test
    void testQueryCommandEquality() {
        QueryCommand cmd1 = new QueryCommand("test", Arrays.asList("arg1"));
        QueryCommand cmd2 = new QueryCommand("test", Arrays.asList("arg1"));
        QueryCommand cmd3 = new QueryCommand("test", Arrays.asList("arg2"));
        
        assertEquals(cmd1, cmd2);
        assertNotEquals(cmd1, cmd3);
        assertNotEquals(cmd1, null);
    }
    
    @Test
    void testQueryCommandHashCode() {
        QueryCommand cmd1 = new QueryCommand("test", Arrays.asList("arg1"));
        QueryCommand cmd2 = new QueryCommand("test", Arrays.asList("arg1"));
        
        assertEquals(cmd1.hashCode(), cmd2.hashCode());
    }
    
    @Test
    void testQueryCommandToString() {
        QueryCommand cmd = new QueryCommand("test", Arrays.asList("arg1", "arg2"));
        String str = cmd.toString();
        assertTrue(str.contains("test"));
        assertTrue(str.contains("arg1"));
        assertTrue(str.contains("arg2"));
    }
}
