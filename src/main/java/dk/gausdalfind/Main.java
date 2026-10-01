package dk.gausdalfind;

import dk.gausdalfind.cli.CommandLineInterface;
import dk.gausdalfind.server.GausVibeMcpServer;
import dk.gausdalfind.server.GausVibeServer;

/**
 * Main entry point for GausVibe.
 *
 * Dispatches subcommands to the matching entry point so the shaded jar is
 * self-describing:
 *
 *   server --project PATH [--port NUM]   HTTP graph server (owns the graph)
 *   mcp --url URL                       MCP stdio proxy to a running server
 *   build | query | edit | ...          command-line interface
 *   (no args)                           interactive CLI
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            CommandLineInterface.main(args);
            return;
        }

        switch (args[0]) {
            case "server":
                GausVibeServer.main(tail(args));
                break;
            case "mcp":
                GausVibeMcpServer.main(tail(args));
                break;
            case "help":
            case "--help":
            case "-h":
                printHelp();
                break;
            default:
                // build, query, edit, interactive, version, ...
                CommandLineInterface.main(args);
        }
    }

    private static String[] tail(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }

    private static void printHelp() {
        System.out.println("GausVibe 1.0.0 - Structured graph of Java code");
        System.out.println();
        System.out.println("Usage: java -jar gausvibe.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  server --project PATH [--port NUM]  Start the HTTP graph server");
        System.out.println("  mcp --url URL                       Start the MCP stdio proxy");
        System.out.println("  build [options]                     Build the graph (CLI)");
        System.out.println("  query [options]                     Query the graph (CLI)");
        System.out.println("  edit [options]                      Mark files edited (CLI)");
        System.out.println("  interactive | shell                 Interactive CLI");
        System.out.println("  version                             Print version");
        System.out.println("  help                                Show this help");
    }
}
