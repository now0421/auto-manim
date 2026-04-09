package com.automanim.prompt;

/**
 * Prompts for Stage 3: code evaluation and revision.
 */
public final class CodeEvaluationPrompts {

    private static final String REVIEW_SYSTEM_MANIM =
            "You are a senior Manim code reviewer.\n"
                    + "Your job is NOT to debug runtime errors.\n"
                    + "Your primary job is storyboard-to-code alignment review before render.\n"
                    + "Predict whether the generated animation is likely to feel visually discontinuous, semantically mis-attached, hard to read in 3D, or badly paced against the storyboard narration.\n\n"
                    + "The storyboard JSON is the source of truth.\n"
                    + "- Explicitly review storyboard/code alignment, not just generic code quality.\n"
                    + "- Compare the code against storyboard continuity, safe-area intent, layout goals, and scene pacing.\n"
                    + "- Compare the code against storyboard geometric invariants: reflection, symmetry, collinearity, intersections, equal-length constructions must survive layout choices.\n"
                    + "- " + SystemPrompts.STORYBOARD_FIELD_GUIDE.replace("\n", "\n- ").trim() + "\n"
                    + "- A later geometry-based stage will inspect rendered frames for actual overlap/offscreen issues. Do not duplicate that stage.\n"
                    + "- For 3D scenes, judge projected readability, camera clarity, and fixed-in-frame overlays.\n"
                    + "- Penalize semantically wrong placements such as angle arcs on the wrong side, labels attached to the wrong point or segment, braces spanning the wrong expression, or highlights pointing at the wrong target.\n"
                    + "- For angle markers, check that the code uses the true shared vertex and stable defining rays; flag hand-built `Arc(...)` angle formulas when they can drift, flip sides, or detach from the intended geometry.\n"
                    + "- Also flag `Angle(...)` usage when the intended small interior sector is ambiguous but `quadrant` or `other_angle` is left implicit, causing the code to mark a large exterior angle or the wrong side of a normal.\n"
                    + "- Penalize labels on moving objects when they are placed once but not kept attached with `always_redraw(...)` or an updater.\n\n"
                    + "Output format:\n"
                    + "Return a JSON object with this shape:\n"
                    + "{\n"
                    + "  \"approved_for_render\": \"boolean, whether the code is safe enough to proceed to render\",\n"
                    + "  \"layout_score\": \"integer 1-10, quality of overall layout and spatial organization\",\n"
                    + "  \"continuity_score\": \"integer 1-10, how well object reuse and scene continuity match the storyboard\",\n"
                    + "  \"pacing_score\": \"integer 1-10, how well animation timing matches the storyboard narration and beat structure\",\n"
                    + "  \"clutter_risk\": \"integer 1-10, risk that the scene feels crowded or visually overloaded\",\n"
                    + "  \"likely_offscreen_risk\": \"integer 1-10, risk that important content may drift out of the readable frame\",\n"
                    + "  \"summary\": \"string, concise overall judgment of code quality against the storyboard\",\n"
                    + "  \"strengths\": [\"string, specific strength that should be preserved\"],\n"
                    + "  \"blocking_issues\": [\"string, issue serious enough to block confident render approval\"],\n"
                    + "  \"revision_directives\": [\"string, concrete change request for the next revision\"]\n"
                    + "}\n\n"
                    + "Scores use 1 to 10 integers.\n"
                    + SystemPrompts.TOOL_CALL_HINT
                    + SystemPrompts.JSON_ONLY_OUTPUT;

    private static final String REVIEW_SYSTEM_GEOGEBRA =
            "You are a senior GeoGebra construction reviewer.\n"
                    + "Your job is NOT to debug runtime errors unless they directly affect storyboard fidelity.\n"
                    + "Your primary job is storyboard-to-code alignment review for a GeoGebra teaching construction before render.\n"
                    + "Judge whether the command script truly realizes the storyboard's objects, scene progression, and teaching intent.\n\n"
                    + "The storyboard JSON is the source of truth.\n"
                    + "- Explicitly review storyboard/code alignment, not just generic code quality.\n"
                    + "- Check whether required constructed objects are actually present, not merely suggested by captions, counts, or comments.\n"
                    + "- Check whether scene-level visibility progression matches `entering_objects`, `persistent_objects`, `exiting_objects`, and `actions`.\n"
                    + "- Compare the code against storyboard continuity, layout goals, safe-area intent, and screen-overlay intent.\n"
                    + "- Compare the code against storyboard geometric invariants: reflections, symmetry, collinearity, intersections, equal lengths, grids, partitions, and dependency-safe constructions must survive layout choices.\n"
                    + "- " + SystemPrompts.STORYBOARD_FIELD_GUIDE_GEOGEBRA.replace("\n", "\n- ").trim() + "\n"
                    + "- Penalize semantically wrong substitutions, such as drawing a border where a full grid was requested, or showing result text without constructing the matching geometry.\n"
                    + "- Penalize scripts that define helper or cloned objects but fail to include them in the scene visibility progression.\n"
                    + "- A later geometry-based stage will inspect rendered geometry for actual overlap/offscreen issues. Do not duplicate that stage.\n"
                    + "- GeoGebra is interactive, so do not over-penalize pure zoomability issues. Focus on likely initial-view readability and storyboard fidelity.\n\n"
                    + "Output format:\n"
                    + "Return a JSON object with this shape:\n"
                    + "{\n"
                    + "  \"approved_for_render\": \"boolean, whether the code is safe enough to proceed to render\",\n"
                    + "  \"layout_score\": \"integer 1-10, quality of overall layout and storyboard realization\",\n"
                    + "  \"continuity_score\": \"integer 1-10, how well object reuse and scene continuity match the storyboard\",\n"
                    + "  \"pacing_score\": \"integer 1-10, how well scene progression matches the storyboard beat structure\",\n"
                    + "  \"clutter_risk\": \"integer 1-10, risk that the construction feels crowded or visually overloaded\",\n"
                    + "  \"likely_offscreen_risk\": \"integer 1-10, risk that important content is initially framed poorly or reads as out of place\",\n"
                    + "  \"summary\": \"string, concise overall judgment of code quality against the storyboard\",\n"
                    + "  \"strengths\": [\"string, specific strength that should be preserved\"],\n"
                    + "  \"blocking_issues\": [\"string, issue serious enough to block confident render approval\"],\n"
                    + "  \"revision_directives\": [\"string, concrete change request for the next revision\"]\n"
                    + "}\n\n"
                    + "Scores use 1 to 10 integers.\n"
                    + SystemPrompts.TOOL_CALL_HINT
                    + SystemPrompts.JSON_ONLY_OUTPUT;

    private static final String REVISION_SYSTEM_MANIM =
            "You are a Manim code revision specialist.\n"
                    + "You will receive storyboard JSON, static visual findings, a structured review, and the current code.\n"
                    + "Rewrite the full code so it is visually safer before render.\n"
                    + "Reduce clutter, preserve continuity with transforms, correct semantically wrong placements, keep 3D camera plans readable, and also fix common Python/Manim runtime mistakes.\n"
                    + SystemPrompts.MANIM_MANUAL_ONLY_RULES
                    + SystemPrompts.ANGLE_MARKER_RULES
                    + SystemPrompts.STORYBOARD_FIELD_GUIDE
                    + SystemPrompts.GEOMETRY_CONSTRAINT_RULES + "\n"
                    + SystemPrompts.PYTHON_CODE_OUTPUT_FORMAT;

    private static final String REVISION_SYSTEM_GEOGEBRA =
            "You are a GeoGebra command revision specialist.\n"
                    + "You will receive storyboard JSON, static visual findings, a structured review, and the current command script.\n"
                    + "Rewrite the full command script so it better aligns with the storyboard before render.\n"
                    + "Preserve dependency-safe geometry, object identities, scene visibility progression, and teaching intent.\n"
                    + "Fix storyboard misalignments such as missing constructions, incorrect scene visibility, incorrect substitutions for requested geometry, and captions unsupported by the actual construction.\n"
                    + SystemPrompts.GEOGEBRA_MANUAL_ONLY_RULES
                    + SystemPrompts.STORYBOARD_FIELD_GUIDE_GEOGEBRA_REPAIR
                    + SystemPrompts.GEOMETRY_CONSTRAINT_RULES + "\n"
                    + SystemPrompts.GEOGEBRA_CODE_OUTPUT_FORMAT;

    private CodeEvaluationPrompts() {}

    public static String reviewSystemPrompt(String targetConcept, String targetDescription) {
        return reviewSystemPrompt(targetConcept, targetDescription, "manim");
    }

    public static String reviewSystemPrompt(String targetConcept,
                                            String targetDescription,
                                            String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return SystemPrompts.ensureGeoGebraSyntaxManual(SystemPrompts.buildWorkflowPrefix(
                    "Stage 3 / Code Evaluation",
                    "Review GeoGebra code for storyboard alignment, layout, continuity, and clutter risk",
                    targetConcept,
                    targetDescription,
                    "geogebra"
            ) + REVIEW_SYSTEM_GEOGEBRA);
        }
        return SystemPrompts.buildWorkflowPrefix(
                "Stage 3 / Code Evaluation",
                "Review code for storyboard alignment, layout, continuity, pacing, and clutter risk",
                targetConcept,
                targetDescription,
                "manim"
        ) + REVIEW_SYSTEM_MANIM;
    }

    public static String reviewUserPrompt(String targetConcept,
                                          String sceneName,
                                          String storyboardJson,
                                          String staticAnalysisJson,
                                          String generatedCode) {
        return reviewUserPrompt(targetConcept, sceneName, storyboardJson, staticAnalysisJson, generatedCode, "manim");
    }

    public static String reviewUserPrompt(String targetConcept,
                                          String sceneName,
                                          String storyboardJson,
                                          String staticAnalysisJson,
                                          String generatedCode,
                                          String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return String.format(
                    "Target concept: %s\n"
                            + "Figure name: %s\n\n"
                            + "Compact storyboard JSON (source of truth):\n```json\n%s\n```\n\n"
                            + "Static visual analysis:\n```json\n%s\n```\n\n"
                            + "GeoGebra command script to review:\n```geogebra\n%s\n```\n\n"
                            + "Review for storyboard/code alignment problems before render.\n"
                            + "Focus on whether the actual construction, scene visibility progression, and teaching evidence match the storyboard.\n"
                            + "Return only the structured review output.",
                    targetConcept, sceneName, storyboardJson, staticAnalysisJson, generatedCode);
        }
        return String.format(
                "Target concept: %s\n"
                        + "Scene class name: %s\n\n"
                        + "Compact storyboard JSON (source of truth):\n```json\n%s\n```\n\n"
                        + "Static visual analysis:\n```json\n%s\n```\n\n"
                        + "Manim code to review:\n```python\n%s\n```\n\n"
                        + "Review for likely presentation quality problems before render.\n"
                        + "Focus on continuity, pacing versus narration, 3D readability, fixed-in-frame overlays, and correct spatial relationships.\n"
                        + "Return only the structured review output.",
                targetConcept, sceneName, storyboardJson, staticAnalysisJson, generatedCode);
    }

    public static String revisionSystemPrompt(String targetConcept, String targetDescription) {
        return revisionSystemPrompt(targetConcept, targetDescription, "manim");
    }

    public static String revisionSystemPrompt(String targetConcept,
                                              String targetDescription,
                                              String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return SystemPrompts.ensureGeoGebraSyntaxManual(SystemPrompts.buildWorkflowPrefix(
                    "Stage 3 / Code Evaluation",
                    "Revise GeoGebra code after storyboard alignment review before render",
                    targetConcept,
                    targetDescription,
                    "geogebra"
            ) + REVISION_SYSTEM_GEOGEBRA);
        }
        return SystemPrompts.ensureManimSyntaxManual(SystemPrompts.buildWorkflowPrefix(
                "Stage 3 / Code Evaluation",
                "Revise Manim code after code evaluation before render",
                targetConcept,
                targetDescription,
                "manim"
        ) + REVISION_SYSTEM_MANIM);
    }

    public static String revisionUserPrompt(String targetConcept,
                                            String sceneName,
                                            String storyboardJson,
                                            String staticAnalysisJson,
                                            String reviewJson,
                                            String generatedCode) {
        return revisionUserPrompt(
                targetConcept,
                sceneName,
                storyboardJson,
                staticAnalysisJson,
                reviewJson,
                generatedCode,
                "manim");
    }

    public static String revisionUserPrompt(String targetConcept,
                                            String sceneName,
                                            String storyboardJson,
                                            String staticAnalysisJson,
                                            String reviewJson,
                                            String generatedCode,
                                            String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return String.format(
                    "Target concept: %s\n"
                            + "Figure name: %s\n\n"
                            + "Compact storyboard JSON (source of truth):\n```json\n%s\n```\n\n"
                            + "Static visual analysis:\n```json\n%s\n```\n\n"
                            + "Structured code review:\n```json\n%s\n```\n\n"
                            + "Current GeoGebra command script:\n```geogebra\n%s\n```\n\n"
                            + "Rewrite the FULL command script to better match the storyboard, preserve dependency-safe geometry, correct scene visibility progression, and restore missing visual evidence requested by the storyboard.\n"
                            + "Preserve storyboard geometric invariants and the teaching goal.\n"
                            + "Use only command names and syntax forms documented in the attached GeoGebra syntax manual. Replace any undocumented command or guessed syntax with a documented equivalent.\n"
                            + "Return ONLY the full GeoGebra code block.",
                    targetConcept, sceneName, storyboardJson, staticAnalysisJson, reviewJson, generatedCode);
        }
        return String.format(
                "Target concept: %s\n"
                        + "Scene class name: %s\n\n"
                        + "Compact storyboard JSON (source of truth):\n```json\n%s\n```\n\n"
                        + "Static visual analysis:\n```json\n%s\n```\n\n"
                        + "Structured code review:\n```json\n%s\n```\n\n"
                        + "Current Manim code:\n```python\n%s\n```\n\n"
                        + "Rewrite the FULL code to reduce clutter, preserve continuity, correct semantically wrong placements such as angle arcs or labels attached to the wrong geometry, better match pacing to narration, and keep 3D overlays readable.\n"
                        + "Preserve any storyboard geometric invariants such as symmetry, reflection, collinearity, and intersection definitions while making layout safer.\n"
                        + "Use only classes, functions, methods, arguments, and code forms documented in the attached Manim syntax manual. Replace any undocumented or guessed API usage with a documented equivalent.\n"
                        + "Also fix nearby Python/Manim runtime mistakes. Preserve the scene class name and teaching goal.\n"
                        + "Return ONLY the full Python code block.",
                targetConcept, sceneName, storyboardJson, staticAnalysisJson, reviewJson, generatedCode);
    }
}
