package com.mathvision.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utilities for Manim code post-processing, validation, and normalization.
 */
public final class ManimCodeUtils {

    private ManimCodeUtils() {}

    public static final String EXPECTED_SCENE_NAME = "MainScene";

    private static final Pattern MAIN_SCENE_CLASS = Pattern.compile(
            "class\\s+MainScene\\s*\\(.*?Scene.*?\\)");

    private static final Pattern ANY_SCENE_CLASS = Pattern.compile(
            "class\\s+[^\\s(]+\\s*\\((.*?Scene.*?)\\)");

    private static final Pattern SCENE_CLASS = Pattern.compile(
            "class\\s+(\\w+)\\s*\\(.*?Scene.*?\\)");

    private static final Pattern STATIC_INDEXING_VIOLATION = Pattern.compile(
            "\\w+\\[\\d+\\]\\[\\d+:\\d+\\]");

    private static final Pattern UNSAFE_SET_POINTS_CALL = Pattern.compile(
            "\\.set_points\\s*\\(");

    private static final String MANIM_NAMED_COLOR_PATTERN =
            "WHITE|BLACK|BLUE|GREEN|YELLOW|RED|PURPLE|PINK|ORANGE|TEAL|GOLD|LIGHT_PINK|"
                    + "BLUE_[A-E]|GREEN_[A-E]|YELLOW_[A-E]|RED_[A-E]|PURPLE_[A-E]|TEAL_[A-E]|"
                    + "GOLD_[A-E]|MAROON_[A-E]|GRAY|GREY|DARK_GRAY|DARK_GREY|LIGHT_GRAY|LIGHT_GREY|"
                    + "PURE_RED|PURE_GREEN|PURE_BLUE|PURE_YELLOW|PURE_CYAN|PURE_MAGENTA|LOGO_BLACK";

    private static final Pattern NAMED_COLOR_VALUE = Pattern.compile(
            "\\b(?:" + MANIM_NAMED_COLOR_PATTERN + ")\\b");

    private static final Pattern COLOR_CONTEXT_PATTERN = Pattern.compile(
            "(?i)(color|fill|stroke|background|palette|gradient|\\bBG\\b|\\bFG\\b|\\bPRIMARY\\b|\\bSECONDARY\\b|\\bACCENT\\b)");

    private static final Pattern NON_SIX_DIGIT_HEX_COLOR = Pattern.compile(
            "#(?![0-9A-Fa-f]{6}(?![0-9A-Fa-f]))[0-9A-Fa-f]{3,8}\\b");

    private static final Pattern TEXT_CONSTRUCTOR_PATTERN = Pattern.compile(
            "\\b(Text|Tex|MathTex)\\s*\\(\\s*(?:r|rf|fr)?([\"'])(.*?)\\2",
            Pattern.DOTALL
    );

    private static final Pattern QUALIFIED_METHOD_CALL_PATTERN = Pattern.compile(
            "\\b((?:[A-Za-z_][A-Za-z0-9_]*\\.)+)([A-Za-z_][A-Za-z0-9_]*)\\s*\\("
    );

    private static final Pattern IMPORT_LINE_PATTERN = Pattern.compile("^\\s*import\\s+(.+)$");
    private static final Pattern FROM_IMPORT_LINE_PATTERN = Pattern.compile(
            "^\\s*from\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s+import\\s+(.+)$");

    private static final Set<String> SKIPPED_RECEIVERS = Set.of(
            "self",
            "cls"
    );

    public static String extractCode(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }
        String extracted = JsonUtils.extractCodeBlock(response);
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }
        return response.trim();
    }

    public static String enforceMainSceneName(String manimCode) {
        if (manimCode == null || manimCode.isBlank()) {
            return manimCode;
        }
        return ANY_SCENE_CLASS.matcher(manimCode)
                .replaceFirst("class MainScene($1)");
    }

    public static String expectedSceneName() {
        return EXPECTED_SCENE_NAME;
    }

    public static String extractSceneName(String manimCode, String fallback) {
        if (manimCode != null) {
            Matcher matcher = SCENE_CLASS.matcher(manimCode);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return EXPECTED_SCENE_NAME;
    }

    /**
     * Builds a Python method name for a scene, e.g. "scene_1_setup_coordinates".
     */
    public static String buildSceneMethodName(String sceneId, String title, int index) {
        String base = "scene_" + (index + 1);
        String suffix = "";
        if (title != null && !title.isBlank()) {
            suffix = "_" + title.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_|_$", "")
                    .replaceAll("_{2,}", "_");
            if (suffix.length() > 30) {
                suffix = suffix.substring(0, 30).replaceAll("_$", "");
            }
        }
        return base + suffix;
    }

    public static List<String> validateStructure(String manimCode) {
        List<String> violations = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            violations.add("Code is empty");
            return violations;
        }

        if (!manimCode.contains("from manim import")) {
            violations.add("Missing 'from manim import' statement");
        }
        if (!MAIN_SCENE_CLASS.matcher(manimCode).find()) {
            violations.add("Scene class must be named MainScene");
        }
        if (!manimCode.contains("def construct(")) {
            violations.add("Missing construct() method");
        }

        return violations;
    }

    public static List<String> validateManimRules(String manimCode) {
        List<String> violations = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            return violations;
        }

        for (String evidence : CodeValidationSupport.findAllMatchEvidences(manimCode, STATIC_INDEXING_VIOLATION)) {
            violations.add("Static rule violation: hardcoded MathTex subobject indexing"
                    + " (" + evidence + ")");
        }

        for (String evidence : findAllUnsafeSetPointsCalls(manimCode)) {
            violations.add("Static rule violation: unsafe VMobject.set_points() call"
                    + " (" + evidence + ")");
        }

        for (String evidence : findAllNamedColorUsages(manimCode)) {
            violations.add("Static rule violation: Manim color must use 6-digit hex #RRGGBB, not named color constants"
                    + " (" + evidence + ")");
        }

        for (String evidence : findAllNonSixDigitHexColorUsages(manimCode)) {
            violations.add("Static rule violation: color hex values must use exactly 6 digits #RRGGBB"
                    + " (" + evidence + ")");
        }

        return violations;
    }

    public static List<String> validateManimApiWhitelistWarnings(String manimCode) {
        List<String> warnings = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            return warnings;
        }

        for (String evidence : findAllUndocumentedManimMethodCalls(manimCode)) {
            warnings.add("Static rule warning: undocumented Manim API call"
                    + " (" + evidence + ")");
        }

        warnings.addAll(validateTextConstructorSemantics(manimCode));

        return warnings;
    }

    public static List<String> validateFull(String manimCode) {
        List<String> violations = new ArrayList<>();
        violations.addAll(validateStructure(manimCode));
        violations.addAll(validateManimRules(manimCode));
        return violations;
    }

    public static List<String> validateFullWarnings(String manimCode) {
        return validateManimApiWhitelistWarnings(manimCode);
    }

    public static boolean hasMainSceneClass(String manimCode) {
        return manimCode != null && MAIN_SCENE_CLASS.matcher(manimCode).find();
    }

    public static int countLines(String manimCode) {
        return CodeValidationSupport.countLines(manimCode);
    }

    /**
     * Scans code for undocumented snake_case method calls while ignoring
     * user-defined helpers on {@code self} or class-level receivers.
     */
    static List<String> findAllUndocumentedManimMethodCalls(String manimCode) {
        List<String> evidences = new ArrayList<>();
        Set<String> documented = ManimValidationSupport.documentedInstanceMethodNames();
        Set<String> importedReceivers = extractImportedReceivers(manimCode);
        String[] lines = manimCode.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            Matcher matcher = QUALIFIED_METHOD_CALL_PATTERN.matcher(line);
            while (matcher.find()) {
                String receiver = matcher.group(1);
                String methodName = matcher.group(2);
                String receiverRoot = extractReceiverRoot(receiver);
                if (receiverRoot == null
                        || SKIPPED_RECEIVERS.contains(receiverRoot)
                        || importedReceivers.contains(receiverRoot)) {
                    continue;
                }
                if (!documented.contains(methodName)) {
                    String fragment = trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed;
                    evidences.add("line " + (i + 1) + ": " + receiver + "." + methodName + "() - " + fragment);
                }
            }
        }
        return evidences;
    }

    private static List<String> findAllUnsafeSetPointsCalls(String manimCode) {
        List<String> evidences = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            return evidences;
        }
        String[] lines = manimCode.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            if (UNSAFE_SET_POINTS_CALL.matcher(line).find()) {
                String fragment = trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed;
                evidences.add("line " + (i + 1) + ": " + fragment);
            }
        }
        return evidences;
    }

    private static List<String> findAllNamedColorUsages(String manimCode) {
        List<String> evidences = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            return evidences;
        }
        String[] lines = manimCode.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = stripInlineComment(lines[i]);
            if (line.isBlank() || !COLOR_CONTEXT_PATTERN.matcher(line).find()) {
                continue;
            }
            Matcher matcher = NAMED_COLOR_VALUE.matcher(line);
            if (matcher.find()) {
                String fragment = line.trim().length() > 100 ? line.trim().substring(0, 100) + "..." : line.trim();
                evidences.add("line " + (i + 1) + ": " + fragment);
            }
        }
        return evidences;
    }

    private static List<String> findAllNonSixDigitHexColorUsages(String manimCode) {
        List<String> evidences = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            return evidences;
        }
        String[] lines = manimCode.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = stripInlineComment(lines[i]);
            if (line.isBlank() || !COLOR_CONTEXT_PATTERN.matcher(line).find()) {
                continue;
            }
            Matcher matcher = NON_SIX_DIGIT_HEX_COLOR.matcher(line);
            if (matcher.find()) {
                String fragment = line.trim().length() > 100 ? line.trim().substring(0, 100) + "..." : line.trim();
                evidences.add("line " + (i + 1) + ": " + fragment);
            }
        }
        return evidences;
    }

    private static String stripInlineComment(String line) {
        if (line == null) {
            return "";
        }
        int commentIndex = line.indexOf('#');
        if (commentIndex < 0) {
            return line;
        }
        int quoteCount = 0;
        for (int i = 0; i < commentIndex; i++) {
            char ch = line.charAt(i);
            if (ch == '"' || ch == '\'') {
                quoteCount++;
            }
        }
        return quoteCount % 2 == 0 ? line.substring(0, commentIndex) : line;
    }

    private static Set<String> extractImportedReceivers(String manimCode) {
        Set<String> receivers = new LinkedHashSet<>(SKIPPED_RECEIVERS);
        if (manimCode == null || manimCode.isBlank()) {
            return receivers;
        }

        String[] lines = manimCode.split("\\R");
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.startsWith("#")) {
                continue;
            }

            Matcher importMatcher = IMPORT_LINE_PATTERN.matcher(line);
            if (importMatcher.matches()) {
                addImportedModuleAliases(receivers, importMatcher.group(1));
                continue;
            }

            Matcher fromImportMatcher = FROM_IMPORT_LINE_PATTERN.matcher(line);
            if (fromImportMatcher.matches()) {
                addImportedNames(receivers, fromImportMatcher.group(2));
            }
        }
        return receivers;
    }

    private static void addImportedModuleAliases(Set<String> receivers, String importClause) {
        if (importClause == null || importClause.isBlank()) {
            return;
        }

        for (String segment : importClause.split(",")) {
            String alias = extractImportAlias(segment);
            if (!alias.isBlank()) {
                receivers.add(alias);
            }
        }
    }

    private static void addImportedNames(Set<String> receivers, String importClause) {
        if (importClause == null || importClause.isBlank()) {
            return;
        }

        for (String segment : importClause.split(",")) {
            String alias = extractImportAlias(segment);
            if (!alias.isBlank()) {
                receivers.add(alias);
            }
        }
    }

    private static String extractImportAlias(String importToken) {
        if (importToken == null) {
            return "";
        }
        String normalized = importToken.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        int aliasIndex = normalized.indexOf(" as ");
        if (aliasIndex >= 0) {
            return normalized.substring(aliasIndex + 4).trim();
        }

        int dotIndex = normalized.indexOf('.');
        if (dotIndex >= 0) {
            return normalized.substring(0, dotIndex).trim();
        }

        return normalized;
    }

    private static String extractReceiverRoot(String receiver) {
        if (receiver == null || receiver.isBlank()) {
            return null;
        }

        String normalized = receiver.trim();
        int dotIndex = normalized.indexOf('.');
        if (dotIndex >= 0) {
            return normalized.substring(0, dotIndex).trim();
        }
        return normalized;
    }

    static List<String> validateTextConstructorSemantics(String manimCode) {
        List<String> issues = new ArrayList<>();
        if (manimCode == null || manimCode.isBlank()) {
            return issues;
        }

        Matcher matcher = TEXT_CONSTRUCTOR_PATTERN.matcher(manimCode);
        int line = 1;
        int previousIndex = 0;
        while (matcher.find()) {
            line += countNewlines(manimCode, previousIndex, matcher.start());
            previousIndex = matcher.start();

            String constructor = matcher.group(1);
            String content = matcher.group(3);
            String normalizedContent = content != null ? content.trim() : "";
            if (normalizedContent.isBlank()) {
                continue;
            }

            if ("Text".equals(constructor) && looksLikeLatexMath(normalizedContent)) {
                issues.add("Static rule warning: Text constructor with math-like content, consider MathTex"
                        + " (line " + line + ": " + summarizeSnippet(normalizedContent) + ")");
                continue;
            }

            if ("Tex".equals(constructor) && looksLikeMathModeContent(normalizedContent)) {
                issues.add("Static rule warning: Tex constructor with math-mode content, consider MathTex"
                        + " (line " + line + ": " + summarizeSnippet(normalizedContent) + ")");
                continue;
            }

            if ("MathTex".equals(constructor) && looksLikePlainSentence(normalizedContent)) {
                issues.add("Static rule warning: MathTex constructor with plain-language content, consider Text"
                        + " (line " + line + ": " + summarizeSnippet(normalizedContent) + ")");
            }
        }

        return issues;
    }

    private static int countNewlines(String text, int start, int end) {
        int count = 0;
        for (int i = Math.max(0, start); i < Math.min(text.length(), end); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static boolean looksLikeLatexMath(String content) {
        return looksLikeMathModeContent(content);
    }

    private static boolean looksLikeMathModeContent(String content) {
        if (content.contains("^") || content.contains("_")) {
            return true;
        }
        if (content.matches(".*\\\\[a-zA-Z]{2,}.*")) {
            return true;
        }
        if (content.matches(".*\\\\[a-zA-Z].*") && !content.matches(".*\\\\[ntrfu0](?![a-zA-Z]).*")) {
            return true;
        }
        if (content.contains("$")) {
            return true;
        }
        if (content.matches(".*[\\u2200-\\u22FF\\u0391-\\u03C9\\u2070-\\u209F].*")) {
            return true;
        }
        return false;
    }

    /**
     * Public structural math indicator check, reused by ErrorSummarizer
     * for LaTeX offending token extraction.
     */
    public static boolean containsMathIndicator(String token) {
        if (token.contains("^") || token.contains("_") || token.contains("*")) {
            return true;
        }
        if (token.matches(".*\\\\[a-zA-Z]{2,}.*")) {
            return true;
        }
        if (token.matches(".*\\\\[a-zA-Z].*") && !token.matches(".*\\\\[ntrfu0](?![a-zA-Z]).*")) {
            return true;
        }
        if (token.matches(".*[\\u2200-\\u22FF\\u0391-\\u03C9].*")) {
            return true;
        }
        return token.contains("’") || token.contains("'");
    }

    private static boolean looksLikePlainSentence(String content) {
        if (looksLikeMathModeContent(content)) {
            return false;
        }
        if (content.contains("{")) {
            return false;
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() < 12) {
            return false;
        }
        String[] words = normalized.split(" ");
        return words.length >= 3 && normalized.matches(".*[A-Za-z]{3,}.*");
    }

    private static String summarizeSnippet(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }
}
