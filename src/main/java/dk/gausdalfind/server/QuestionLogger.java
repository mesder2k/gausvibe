package dk.gausdalfind.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Appends one JSON line per /ask request to a log file.
 *
 * The log is the data source for the question-harvesting experiment:
 * it records every natural-language question the harness asks GausVibe,
 * whether the server could answer it, and how big the answer was.
 *
 * Default location: <project>/.gausvibe/ask-log.jsonl
 * Override with: --ask-log /path/to/file.jsonl
 */
public class QuestionLogger {

    private final Path logFile;
    private final Path feedbackFile;
    private final Object lock = new Object();

    public QuestionLogger(Path logFile, Path feedbackFile) {
        this.logFile = logFile;
        this.feedbackFile = feedbackFile;
    }

    /**
     * Creates a logger, ensuring the parent directory and files exist.
     *
     * @param projectPath the analyzed project (default logs live under it)
     * @param explicitPath user-supplied ask-log path, or null for the default
     * @return a ready logger, or null if the log files cannot be created
     */
    public static QuestionLogger create(String projectPath, String explicitPath) {
        Path path = explicitPath != null
            ? Path.of(explicitPath)
            : Path.of(projectPath, ".gausvibe", "ask-log.jsonl");
        Path feedback = path.getParent() == null
            ? Path.of("feedback-log.jsonl")
            : path.getParent().resolve("feedback-log.jsonl");
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            for (Path p : new Path[]{path, feedback}) {
                if (!Files.exists(p)) {
                    Files.writeString(p, "", StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: cannot create ask log " + path + ": " + e.getMessage());
            return null;
        }
        return new QuestionLogger(path, feedback);
    }

    /**
     * Appends one entry. Never throws - logging must not break request handling.
     *
     * @param question the natural-language question (URL-decoded)
     * @param matched the endpoint/question-type that answered it, or null
     * @param answerBytes size of the answer payload in bytes
     * @param ok true if the request succeeded
     */
    public void log(String question, String matched, int answerBytes, boolean ok) {
        StringBuilder sb = new StringBuilder(128 + question.length());
        sb.append("{\"ts\":\"").append(Instant.now()).append('"');
        sb.append(",\"project\":\"").append(escape(projectName())).append('"');
        sb.append(",\"question\":").append(question == null ? "null" : quote(question));
        sb.append(",\"matched\":").append(matched == null ? "null" : quote(matched));
        sb.append(",\"answer_bytes\":").append(Math.max(0, answerBytes));
        sb.append(",\"ok\":").append(ok);
        sb.append("}\n");
        synchronized (lock) {
            try {
                Files.writeString(logFile, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Warning: ask log write failed: " + e.getMessage());
            }
        }
    }

    /**
     * Appends feedback about a previous answer. Never throws.
     *
     * @param question the question the feedback refers to
     * @param matched the query type that answered it, or null
     * @param rating one of: helpful, wrong, incomplete, too-big, other
     * @param comment free-text explanation, may be null
     */
    public void logFeedback(String question, String matched, String rating, String comment) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"ts\":\"").append(Instant.now()).append('"');
        sb.append(",\"project\":\"").append(escape(projectName())).append('"');
        sb.append(",\"question\":").append(question == null ? "null" : quote(question));
        sb.append(",\"matched\":").append(matched == null ? "null" : quote(matched));
        sb.append(",\"rating\":").append(rating == null ? "null" : quote(rating));
        sb.append(",\"comment\":").append(comment == null ? "null" : quote(comment));
        sb.append("}\n");
        synchronized (lock) {
            try {
                Files.writeString(feedbackFile, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Warning: feedback log write failed: " + e.getMessage());
            }
        }
    }

    public Path getLogFile() {
        return logFile;
    }

    public Path getFeedbackFile() {
        return feedbackFile;
    }

    private String projectName() {
        String p = GausVibeServer.getProjectPath();
        if (p == null) return "unknown";
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(idx + 1) : p;
    }

    private String quote(String s) {
        return "\"" + escape(s) + "\"";
    }

    private String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
