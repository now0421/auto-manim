package com.mathvision.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utilities for error summarization, traceback handling, and error classification.
 *
 * Consolidates patterns previously in RenderNode for reuse across fix-related nodes.
 */
public final class ErrorSummarizer {

    private ErrorSummarizer() {}

    private static final int MAX_TRACEBACK_LINES = 30;
    private static final int TRACEBACK_CONTEXT_RADIUS = 4;
    private static final int MAX_STDOUT_ERROR_LINES = 12;
    private static final int LATEX_LOG_CONTEXT_RADIUS = 3;
    private static final String TRACEBACK_MARKER = "Traceback (most recent call last)";

    /** Patterns for detecting user-code frames in Manim Rich tracebacks. */
    private static final List<Pattern> USER_CODE_FRAME_PATTERNS = List.of(
            Pattern.compile("scene_render\\.py", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mathvision_geometry_export\\.py", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\\\\/]output[\\\\/]", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern ERROR_SIGNATURE_PATTERN = Pattern.compile(
            "\\b(?:[A-Za-z_][A-Za-z0-9_]*Error|[A-Za-z_][A-Za-z0-9_]*Exception)\\s*:\\s*.+");
    private static final Pattern WINDOWS_LOG_PATH_PATTERN = Pattern.compile(
            "([A-Za-z]:\\\\(?:[^\\\\\\s]+\\\\)*[^\\\\\\s]+\\.log)",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> NON_CODE_ERROR_PATTERNS = List.of(
            Pattern.compile("(?i)no module named"),
            Pattern.compile("(?i)command not found"),
            Pattern.compile("(?i)permission denied"),
            Pattern.compile("(?i)out of memory"),
            Pattern.compile("(?i)disk quota"),
            Pattern.compile("(?i)segmentation fault"),
            Pattern.compile("(?i)killed"),
            Pattern.compile("(?i)cannot allocate memory"),
            Pattern.compile("(?i)ffmpeg.*not found"),
            Pattern.compile("(?i)latex.*not found"),
            Pattern.compile("(?i)dvisvgm.*not found")
    );

    private static final List<Pattern> GEOGEBRA_ENVIRONMENT_PATTERNS = List.of(
            Pattern.compile("(?i)failed to launch chromium"),
            Pattern.compile("(?i)did not become ready within"),
            Pattern.compile("(?i)install chromium"),
            Pattern.compile("(?i)configured browser executable does not exist"),
            Pattern.compile("(?i)output directory is unavailable"),
            Pattern.compile("(?i)no executable geogebra commands were found"),
            Pattern.compile("(?i)page\\.timed ?out")
    );

    private static final Pattern INFRASTRUCTURE_FAILURE_WORDS = Pattern.compile(
            "(?i)\\b(cannot|failed|unable|refused|denied|not found|not available|timed? ?out|unreachable)\\b"
    );

    private static final class ClassificationRule {
        final ErrorCategory category;
        final List<Pattern> patterns;
        ClassificationRule(ErrorCategory category, List<Pattern> patterns) {
            this.category = category;
            this.patterns = patterns;
        }
    }

    private static final List<ClassificationRule> CLASSIFICATION_RULES = List.of(
            new ClassificationRule(ErrorCategory.SYNTAX, compile(
                    "(?i)syntaxerror", "(?i)indentationerror")),
            new ClassificationRule(ErrorCategory.LATEX_COMPILE_FAILURE, compile(
                    "(?i)missing \\$ inserted", "(?i)latex compilation error",
                    "(?i)latex error converting", "(?i)tex error converting", "(?i)dvisvgm")),
            new ClassificationRule(ErrorCategory.MANIM_API_MISUSE, compile(
                    "(?i)documented manim api call", "(?i)undocumented manim api call",
                    "(?i)invalid animation target")),
            new ClassificationRule(ErrorCategory.EMPTY_REDRAW_TARGET, compile(
                    "(?i)empty redraw target", "(?i)cannot animate empty",
                    "(?i)no points to animate", "(?i)zero mobject")),
            new ClassificationRule(ErrorCategory.NAME_RESOLUTION, compile(
                    "(?i)nameerror", "(?i)attributeerror")),
            new ClassificationRule(ErrorCategory.TYPE_VALUE, compile(
                    "(?i)typeerror", "(?i)valueerror")),
            new ClassificationRule(ErrorCategory.INDEX_KEY, compile(
                    "(?i)indexerror", "(?i)keyerror")),
            new ClassificationRule(ErrorCategory.IMPORT, compile(
                    "(?i)importerror", "(?i)modulenotfounderror")),
            new ClassificationRule(ErrorCategory.NOT_IMPLEMENTED, compile(
                    "(?i)notimplementederror"))
    );

    private static List<Pattern> compile(String... regexes) {
        List<Pattern> patterns = new ArrayList<>(regexes.length);
        for (String regex : regexes) {
            patterns.add(Pattern.compile(regex));
        }
        return patterns;
    }

    /**
     * Checks if the error indicates an environment problem rather than a code bug.
     */
    public static boolean isEnvironmentError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        for (Pattern pattern : NON_CODE_ERROR_PATTERNS) {
            if (pattern.matcher(error).find()) {
                return true;
            }
        }
        for (Pattern pattern : GEOGEBRA_ENVIRONMENT_PATTERNS) {
            if (pattern.matcher(error).find()) {
                return true;
            }
        }
        // Structural fallback: if there's no Python error signature but the text
        // reads like an infrastructure message, classify as environment.
        if (!ERROR_SIGNATURE_PATTERN.matcher(error).find()) {
            String lower = error.toLowerCase();
            if (lower.length() < 200 && INFRASTRUCTURE_FAILURE_WORDS.matcher(lower).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Summarizes a traceback, keeping the most relevant lines.
     *
     * <p>Prioritises user-code frames (scene_render.py, files under output/)
     * over library frames so that the LLM can see the actual call site that
     * triggered the error, not just the Manim-internal frames near the bottom.
     */
    public static String summarizeTraceback(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }

        int markerIndex = stderr.lastIndexOf(TRACEBACK_MARKER);
        if (markerIndex < 0) {
            return extractFocusedError(stderr);
        }

        String tracebackSection = stderr.substring(markerIndex);
        String[] lines = tracebackSection.split("\\R");

        if (lines.length <= MAX_TRACEBACK_LINES) {
            return tracebackSection.trim();
        }

        // --- Phase 1: identify important line ranges ---
        // 1a) The error-signature line(s) at the bottom
        int lastErrorIndex = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (ERROR_SIGNATURE_PATTERN.matcher(lines[i]).find()) {
                lastErrorIndex = i;
                break;
            }
        }
        if (lastErrorIndex < 0) {
            lastErrorIndex = lines.length - 1;
        }

        // 1b) User-code frame ranges (file header + context lines)
        //     A "frame header" in Manim Rich traceback looks like:
        //       │ D:\project\...\scene_render.py:182 in ...  │
        //     followed by blank + source lines.  We record the header and
        //     the > marker line as the essential user-code context.
        List<int[]> userFrameRanges = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (isUserCodeFrameLine(lines[i])) {
                // include the header line itself and up to
                // TRACEBACK_CONTEXT_RADIUS lines after it (the source
                // context with the ">" marker)
                int end = Math.min(lines.length - 1,
                        i + TRACEBACK_CONTEXT_RADIUS + 1);
                userFrameRanges.add(new int[]{i, end});
            }
        }

        // --- Phase 2: build the result ---
        List<String> result = new ArrayList<>();
        result.add(lines[0]); // "Traceback ..." header

        // Add user-code frame ranges first (these are the most actionable)
        int lastIncludedEnd = 0;
        for (int[] range : userFrameRanges) {
            int start = range[0];
            int end = range[1];
            if (start <= lastIncludedEnd) {
                // overlap with previously included range – extend it
                continue;
            }
            if (start > lastIncludedEnd + 1) {
                result.add("  ... (" + (start - lastIncludedEnd - 1) + " lines omitted) ...");
            }
            for (int i = start; i <= end; i++) {
                result.add(lines[i]);
            }
            lastIncludedEnd = end;
        }

        // Add context around the final error-signature line
        int ctxStart = Math.max(1, lastErrorIndex - TRACEBACK_CONTEXT_RADIUS);
        int ctxEnd = Math.min(lines.length - 1, lastErrorIndex + TRACEBACK_CONTEXT_RADIUS);
        if (ctxStart > lastIncludedEnd + 1) {
            result.add("  ... (" + (ctxStart - lastIncludedEnd - 1) + " lines omitted) ...");
        }
        for (int i = Math.max(ctxStart, lastIncludedEnd + 1); i <= ctxEnd; i++) {
            result.add(lines[i]);
        }

        // Catch any remaining error-signature lines after the context window
        for (int i = ctxEnd + 1; i < lines.length; i++) {
            if (ERROR_SIGNATURE_PATTERN.matcher(lines[i]).find()) {
                result.add(lines[i]);
            }
        }

        return String.join("\n", result);
    }

    /**
     * Returns true if the line looks like a Manim Rich traceback frame
     * header that references user code (scene_render.py, files under
     * the output/ directory, etc.).
     */
    private static boolean isUserCodeFrameLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        for (Pattern pattern : USER_CODE_FRAME_PATTERNS) {
            if (pattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the focused error message from stderr.
     */
    public static String extractFocusedError(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }

        String[] lines = stderr.split("\\R");
        List<String> errorLines = new ArrayList<>();

        for (int i = lines.length - 1; i >= 0 && errorLines.size() < MAX_STDOUT_ERROR_LINES; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (ERROR_SIGNATURE_PATTERN.matcher(line).find()) {
                errorLines.add(0, line);
                for (int j = i - 1; j >= 0 && errorLines.size() < MAX_STDOUT_ERROR_LINES; j--) {
                    String contextLine = lines[j].trim();
                    if (contextLine.isEmpty()) {
                        break;
                    }
                    errorLines.add(0, contextLine);
                }
                break;
            }
            errorLines.add(0, line);
        }

        return String.join("\n", errorLines);
    }

    /**
     * Extracts focused error from both stdout and stderr.
     */
    public static String extractFocusedError(String stdout, String stderr) {
        List<String> sections = new ArrayList<>();
        String combined = combineErrorStreams(stdout, stderr);

        String stdoutSummary = extractStdoutErrors(stdout);
        if (!stdoutSummary.isBlank()) {
            sections.add("=== stdout highlights ===\n" + stdoutSummary);
        }

        String stderrSummary = summarizeTraceback(stderr);
        if (!stderrSummary.isBlank()) {
            sections.add("=== stderr traceback ===\n" + stderrSummary);
        }

        String latexLogContext = extractLatexLogContext(combined);
        if (!latexLogContext.isBlank()) {
            sections.add("=== latex log context ===\n" + latexLogContext);
        }

        if (!sections.isEmpty()) {
            return String.join("\n\n", sections);
        }

        return tailLines(combined, MAX_TRACEBACK_LINES);
    }

    /**
     * Extracts error-related lines from stdout.
     */
    public static String extractStdoutErrors(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }

        String[] lines = stdout.split("\\R");
        List<String> errorLines = new ArrayList<>();

        for (int i = 0; i < lines.length && errorLines.size() < MAX_STDOUT_ERROR_LINES; i++) {
            String line = lines[i];
            if (line.matches(".*(?i)(\\bERROR\\b|exception|traceback|not in the script|latex compilation error|context of error).*")) {
                errorLines.add(line);
                for (int j = i + 1; j < lines.length && j <= i + 3 && errorLines.size() < MAX_STDOUT_ERROR_LINES; j++) {
                    String nextLine = lines[j];
                    if (!nextLine.contains("%|") && !nextLine.trim().startsWith("Animation ")) {
                        errorLines.add(nextLine);
                    }
                }
            }
        }

        return String.join("\n", errorLines).trim();
    }

    /**
     * Combines stdout and stderr into a single error string.
     */
    public static String combineErrorStreams(String stdout, String stderr) {
        List<String> sections = new ArrayList<>();
        if (stdout != null && !stdout.isBlank()) {
            sections.add("[stdout]\n" + stdout.strip());
        }
        if (stderr != null && !stderr.isBlank()) {
            sections.add("[stderr]\n" + stderr.strip());
        }
        return String.join("\n\n", sections);
    }

    /**
     * Summarizes an error into a short signature for deduplication.
     */
    public static String summarizeSignature(String focusedError) {
        if (focusedError == null || focusedError.isBlank()) {
            return "";
        }

        String latexSpecific = summarizeLatexSignature(focusedError);
        if (!latexSpecific.isBlank()) {
            return latexSpecific;
        }

        String[] lines = focusedError.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            Matcher matcher = ERROR_SIGNATURE_PATTERN.matcher(line);
            if (matcher.find()) {
                String signature = matcher.group().trim();
                return signature.length() > 200 ? signature.substring(0, 200) : signature;
            }
        }

        String normalized = focusedError.replaceAll("\\s+", " ").trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    public static String buildRenderFixSummary(String focusedError) {
        ErrorCategory category = classifyError(focusedError);
        String signature = summarizeSignature(focusedError);
        if (category == ErrorCategory.FALLBACK) {
            // For unknown/unclassified errors, pass through the full focusedError
            // (already condensed by summarizeTraceback) so the LLM can see user-code
            // frames, not just library frames near the bottom of the stack.
            if (focusedError != null && !focusedError.isBlank()) {
                return ErrorCategory.FALLBACK.name() + ":\n" + focusedError.trim();
            }
            return ErrorCategory.FALLBACK.name();
        }
        if (signature == null || signature.isBlank()) {
            return category.name();
        }
        return category.name() + ": " + signature;
    }

    /**
     * Collapses a longer issue summary into a short single-line history entry.
     */
    public static String compactSummary(String summary, int maxLength) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        String normalized = summary.replaceAll("\\s+", " ").trim();
        if (maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    /**
     * Returns the last N lines of text.
     */
    private static String tailLines(String text, int maxLines) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] lines = text.split("\\R");
        if (lines.length <= maxLines) {
            return text.strip();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - maxLines; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().strip();
    }

    /**
     * Extracts the last traceback chunk from output.
     */
    public static String extractLastTracebackChunk(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }

        int markerIndex = output.lastIndexOf(TRACEBACK_MARKER);
        if (markerIndex < 0) {
            return "";
        }

        return output.substring(markerIndex).trim();
    }

    /**
     * Classifies an error into categories for fix routing.
     */
    public static ErrorCategory classifyError(String error) {
        if (error == null || error.isBlank()) {
            return ErrorCategory.UNKNOWN;
        }

        if (isEnvironmentError(error)) {
            return ErrorCategory.ENVIRONMENT;
        }

        for (ClassificationRule rule : CLASSIFICATION_RULES) {
            for (Pattern pattern : rule.patterns) {
                if (pattern.matcher(error).find()) {
                    return rule.category;
                }
            }
        }

        return ErrorCategory.FALLBACK;
    }

    /**
     * Error categories for fix routing decisions.
     */
    public enum ErrorCategory {
        SYNTAX,
        LATEX_COMPILE_FAILURE,
        MANIM_API_MISUSE,
        EMPTY_REDRAW_TARGET,
        NAME_RESOLUTION,
        TYPE_VALUE,
        INDEX_KEY,
        IMPORT,
        NOT_IMPLEMENTED,
        FALLBACK,
        ENVIRONMENT,
        UNKNOWN
    }

    private static String extractLatexLogContext(String combinedError) {
        if (combinedError == null || combinedError.isBlank()) {
            return "";
        }

        String lower = combinedError.toLowerCase();
        if (!lower.contains("latex compilation error")
                && !lower.contains("latex error converting")
                && !lower.contains("log file:")) {
            return "";
        }

        Path logPath = extractLatexLogPath(combinedError);
        if (logPath == null || !Files.exists(logPath)) {
            return "";
        }

        try {
            String logText = Files.readString(logPath);
            String snippet = summarizeLatexLog(logText);
            if (snippet.isBlank()) {
                return "Log file: " + logPath;
            }
            return "Log file: " + logPath + "\n" + snippet;
        } catch (IOException e) {
            return "";
        }
    }

    private static Path extractLatexLogPath(String combinedError) {
        int logMarker = combinedError.toLowerCase().lastIndexOf("log file:");
        String searchRegion = logMarker >= 0 ? combinedError.substring(logMarker) : combinedError;
        String condensed = searchRegion.replaceAll("\\s+", "");
        Matcher matcher = WINDOWS_LOG_PATH_PATTERN.matcher(condensed);
        if (matcher.find()) {
            return Path.of(matcher.group(1));
        }
        return null;
    }

    private static String summarizeLatexLog(String logText) {
        if (logText == null || logText.isBlank()) {
            return "";
        }

        String[] lines = logText.split("\\R");
        int anchor = findLatexAnchor(lines);
        if (anchor < 0) {
            return tailLines(logText, MAX_STDOUT_ERROR_LINES);
        }

        int start = Math.max(0, anchor - LATEX_LOG_CONTEXT_RADIUS);
        int end = Math.min(lines.length - 1, anchor + LATEX_LOG_CONTEXT_RADIUS + 2);
        List<String> snippet = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            String line = lines[i].stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            snippet.add(line);
        }
        return String.join("\n", snippet);
    }

    private static int findLatexAnchor(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("! ")) {
                return i;
            }
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.matches("l\\.\\d+.*")) {
                return i;
            }
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].toLowerCase();
            if (line.contains("missing $ inserted")
                    || line.contains("latex error")
                    || line.contains("emergency stop")) {
                return i;
            }
        }
        return -1;
    }

    private static String summarizeLatexSignature(String focusedError) {
        String lower = focusedError.toLowerCase();
        if (!lower.contains("missing $ inserted")
                && !lower.contains("latex compilation error")
                && !lower.contains("latex error converting")) {
            return "";
        }

        String offending = extractLatexOffendingToken(focusedError);
        if (offending != null && !offending.isBlank()) {
            return "Missing $ inserted near " + offending;
        }
        return "LaTeX compile failure";
    }

    private static String extractLatexOffendingToken(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Pattern tokenPattern = Pattern.compile(
                "(?:\\}|\")?([A-Za-z]+(?:\\\\[A-Za-z]+|\\^[^\\s\\\\]+|\\*|′|\\^\\*|_[^\\s\\\\]+)*)"
        );
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("l.") || trimmed.contains("special{dvisvgm:raw")) {
                Matcher matcher = tokenPattern.matcher(trimmed);
                while (matcher.find()) {
                    String candidate = matcher.group(1);
                    if (candidate != null && ManimCodeUtils.containsMathIndicator(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return "";
    }
}
