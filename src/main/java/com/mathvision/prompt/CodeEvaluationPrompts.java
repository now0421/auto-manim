package com.mathvision.prompt;

/**
 * Prompts for Stage 3: code evaluation and revision.
 */
public final class CodeEvaluationPrompts {

    private static final String REVIEW_OUTPUT_SCHEMA =
            "Output format:\n"
                    + "Return a JSON object with this shape. Do not score anything:\n"
                    + "{\n"
                    + "  \"approved_for_render\": \"boolean, true only if every mandatory rule is pass or not_applicable and no blocking issue exists\",\n"
                    + "  \"rule_checks\": [\n"
                    + "    {\n"
                    + "      \"rule_id\": \"string, stable snake_case id from the checklist\",\n"
                    + "      \"requirement\": \"string, the concrete rule being checked\",\n"
                    + "      \"status\": \"pass | warn | fail | not_applicable\",\n"
                    + "      \"evidence\": \"string, cite concrete code evidence and storyboard reference evidence when relevant; say why not_applicable when relevant\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"summary\": \"string, concise compliance summary against the code, render-readiness rules, and storyboard reference context\",\n"
                    + "  \"strengths\": [\"string, specific strength that should be preserved\"],\n"
                    + "  \"blocking_issues\": [\"string, only failed mandatory checks that should block render\"],\n"
                    + "  \"revision_directives\": [\"string, concrete change request for each fail or warn\"]\n"
                    + "}\n\n"
                    + "Every fail must have a matching blocking issue and revision directive.\n"
                    + "Use warn for non-blocking risk with concrete evidence. Use not_applicable only when the code or storyboard reference context makes the rule irrelevant.\n"
                    + SystemPrompts.TOOL_CALL_HINT
                    + SystemPrompts.JSON_ONLY_OUTPUT;

    private static final String REVIEW_API_WHITELIST_WARNING_POLICY =
            "API whitelist warning policy:\n"
                    + "- Static findings with rule_id `api_whitelist_warning` or text like `Static rule warning: undocumented ...` are advisory warnings.\n"
                    + "- Report those findings as `warn`, keep them out of `blocking_issues`, and do not set `approved_for_render=false` solely because of them.\n"
                    + "- Use `fail` for API/syntax issues only when static analysis reports a `fail`, the code clearly cannot execute, or the code uses a documented-invalid form that directly breaks runtime correctness or visual intent.\n\n";

    private static final String REVIEW_SYSTEM_MANIM =
            "You are a senior Manim code reviewer.\n"
                    + "Your job is NOT to debug runtime errors.\n"
                    + "Your primary job is rule-compliance inspection before render.\n"
                    + "Do not assign numeric quality scores. Instead, check each rule below as pass, warn, fail, or not_applicable using concrete code evidence and storyboard reference context when useful.\n\n"
                    + SystemPrompts.STORYBOARD_REFERENCE_RULES
                    + "Mandatory Manim rule checklist:\n"
                    + "- `teaching_coherence`: the code presents a coherent teaching animation for the target concept; storyboard details are reference hints, not required one-for-one implementation requirements.\n"
                    + "- `geometry_consistency`: geometry implemented in the code is internally consistent; storyboard constraints may guide the review but should not be treated as hard blockers by themselves.\n"
                    + "- `layout_and_hierarchy`: important visible content maintains one clear focus, uses opacity hierarchy, preserves empty overlay space, and avoids code-evident persistent crowding.\n"
                    + "- `continuity_and_identity`: persistent code objects remain stable where continuity matters, prefer transforms/restyles over unnecessary redraws, and clean temporary annotations when their beat is done.\n"
                    + "- `pacing_and_narration`: important reveals have subtitle-ready beats, `self.add_subcaption(...)` or `subcaption=`, and enough `self.wait(...)` breathing room instead of stacked animations.\n"
                    + "- `text_readability`: `Text(...)`/`MarkupText(...)` use monospace fonts, on-screen text uses `font_size >= 18`, `.to_edge()` uses `buff >= 0.5`, long text width is constrained, and light cards have dark text.\n"
                    + "- `manim_code_hygiene`: code uses documented Manim APIs, shared color constants, `self.camera.background_color = BG`, no hardcoded hex colors inside scene methods, stable animation targets, and no unsafe empty `always_redraw` animation targets.\n"
                    + "- Imported external libraries and aliases used by the code, such as `import numpy as np`, are allowed; do not flag calls like `np.array(...)` or `np.linalg.norm(...)` when the import is present.\n"
                    + "- `supported_equivalence`: backend-supported substitutions are acceptable when they preserve the overall teaching intent; fail only when the substitution makes the code incoherent, misleading, or unsupported.\n"
                    + "- `angle_and_attachment`: angle markers use true shared vertices/rays with explicit quadrant/other_angle when needed, and labels attached to moving objects use an updater or `always_redraw(...)`.\n"
                    + "- `minimize_helpers`: auxiliary helper mobjects (proxy points on existing lines, duplicate line/ray objects created solely for angle measurement) are replaced with direct Manim constructs when available (e.g. `Angle(line1, line2)` instead of hand-crafted arcs or helper points).\n"
                    + "- `three_d_readability`: 3D code uses `ThreeDScene`, applies explicit camera plan/orientation, and keeps explanatory overlays fixed in frame or fixed orientation when camera motion would make them rotate away.\n"
                    + "- Specifically fail semantically wrong placements such as angle arcs on the wrong side, labels attached to the wrong point or segment, braces spanning the wrong expression, or highlights pointing at the wrong target.\n"
                    + "- For 3D scenes, check projected readability, camera clarity, and fixed-in-frame overlays.\n"
                    + "- A later geometry-based stage will inspect rendered frames for actual overlap/offscreen issues. Here, fail only when the code itself clearly violates runtime readiness, readability, or these rules.\n"
                    + "- Treat density counts from static analysis as heuristics, not automatic failures, when the code uses staging, dimming, grouping, cleanup, or pauses that keep the frame readable.\n\n"
                    + REVIEW_API_WHITELIST_WARNING_POLICY
                    + REVIEW_OUTPUT_SCHEMA;

    private static final String REVIEW_SYSTEM_GEOGEBRA =
            "You are a senior GeoGebra construction reviewer.\n"
                    + "Your job is NOT to debug runtime errors unless they directly affect runtime validity or construction clarity.\n"
                    + "Your primary job is rule-compliance inspection for a GeoGebra teaching construction before render.\n"
                    + "Do not assign numeric quality scores. Instead, check each rule below as pass, warn, fail, or not_applicable using concrete code evidence and storyboard reference context when useful.\n\n"
                    + SystemPrompts.STORYBOARD_REFERENCE_RULES
                    + "Mandatory GeoGebra rule checklist:\n"
                    + "- `teaching_coherence`: the command script presents a coherent teaching construction for the target concept; storyboard details are reference hints, not required one-for-one implementation requirements.\n"
                    + "- `visibility_progression`: scene-level visibility and highlight progression is coherent in the script; exact storyboard progression is not a hard blocker.\n"
                    + "- `geometry_consistency`: geometry implemented in the script is internally consistent and uses documented constructions.\n"
                    + "- `object_identity`: object ids/names remain stable, helpers are not mistaken for storyboard objects, and redundant duplicates on the same endpoints are avoided.\n"
                    + "- `layout_and_readability`: coordinates, labels, style, contrast, and initial view are readable and coherent.\n"
                    + "- `viewport_contract`: the initial visible coordinate window is treated as x[-7,7], y[-4,4]; important objects should fit this view without relying on user zooming or panning, and the construction should not be tiny or clustered inside an over-wide view.\n"
                    + "- `geogebra_syntax`: command names and syntax are documented in the attached GeoGebra manual, one executable command is used per line, and unsupported guessed overloads are not used.\n"
                    + "- `supported_equivalence`: documented GeoGebra substitutions are acceptable when they preserve the overall teaching intent; fail only when the substitution makes the construction incoherent, misleading, or unsupported.\n"
                    + "- `minimize_helpers`: auxiliary helper objects (points on existing lines created solely for `Angle(Point, Point, Point)`, duplicate lines, or proxy scaffolding) are replaced with direct syntax when available (e.g. `Angle(Line, Line)`, referencing existing named objects directly).\n"
                    + "- `teaching_evidence`: result text or labels are supported by matching constructed geometry; no semantically wrong substitution such as drawing a border where a full grid was requested.\n"
                    + "- A later geometry-based stage will inspect rendered geometry for actual overlap/offscreen issues. Do not duplicate that stage.\n"
                    + "- GeoGebra is interactive, but initial-view readability is mandatory. Focus on the default rendered view and construction coherence.\n\n"
                    + REVIEW_API_WHITELIST_WARNING_POLICY
                    + REVIEW_OUTPUT_SCHEMA;

    private static final String REVISION_SYSTEM_MANIM =
            "You are a Manim code revision specialist.\n"
                    + "You will receive storyboard JSON, static visual findings, a structured review, and the current code.\n"
                    + "Rewrite the full code.\n"
                    + "Reduce clutter, preserve continuity with transforms, correct semantically wrong placements, keep 3D camera plans readable, and also fix common Python/Manim runtime mistakes.\n"
                    + SystemPrompts.STORYBOARD_REPAIR_REFERENCE_RULES
                    + SystemPrompts.MANIM_MANUAL_ONLY_RULES
                    + SystemPrompts.COMPOSITION_RULES
                    + SystemPrompts.MANIM_TEXT_AND_READABILITY_RULES
                    + SystemPrompts.MANIM_CODE_HYGIENE_RULES
                    + SystemPrompts.COMMON_RENDER_FAILURE_GUARDRAILS
                    + SystemPrompts.MANIM_ANGLE_MARKER_RULES
                    + SystemPrompts.MINIMIZE_HELPER_OBJECTS_CODEGEN_RULES
                    + SystemPrompts.MANIM_CODE_OUTPUT_FORMAT;

    private static final String REVISION_SYSTEM_GEOGEBRA =
            "You are a GeoGebra command revision specialist.\n"
                    + "You will receive storyboard JSON, static visual findings, a structured review, and the current command script.\n"
                    + "Rewrite the full command script.\n"
                    + "Preserve runtime validity, construction coherence, object identities where useful, scene visibility progression, and teaching intent.\n"
                    + "Use storyboard details as reference context, not as strict requirements that must be restored one-for-one.\n"
                    + SystemPrompts.STORYBOARD_REPAIR_REFERENCE_RULES
                    + SystemPrompts.OBJECT_LIFECYCLE_RULES
                    + SystemPrompts.GEOGEBRA_VIEWPORT_RULES
                    + SystemPrompts.GEOGEBRA_MANUAL_ONLY_RULES
                    + SystemPrompts.MINIMIZE_HELPER_OBJECTS_CODEGEN_RULES
                    + SystemPrompts.COMPOSITION_RULES
                    + SystemPrompts.GEOGEBRA_CODE_OUTPUT_FORMAT;

    private CodeEvaluationPrompts() {}

    public static String buildReviewRulesPrompt(String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return SystemPrompts.buildRulesSection(
                    SystemPrompts.ensureGeoGebraSyntaxManual(REVIEW_SYSTEM_GEOGEBRA));
        }
        return SystemPrompts.buildRulesSection(REVIEW_SYSTEM_MANIM);
    }

    public static String buildReviewFixedContextPrompt(String targetConcept,
                                                       String targetDescription,
                                                       String outputTarget) {
        return SystemPrompts.buildFixedContextSection(SystemPrompts.buildWorkflowPrefix(
                "Stage 3 / Code Evaluation",
                "Review " + ("geogebra".equalsIgnoreCase(outputTarget) ? "GeoGebra code" : "code")
                        + " for render readiness, layout, continuity, pacing, and clutter risk",
                targetConcept,
                targetDescription,
                outputTarget
        ));
    }

    public static String reviewUserPrompt(String sceneName,
                                          String storyboardJson,
                                          String staticAnalysisJson,
                                          String generatedCode) {
        return reviewUserPrompt(sceneName, storyboardJson, staticAnalysisJson, generatedCode, "manim");
    }

    public static String reviewUserPrompt(String sceneName,
                                          String storyboardJson,
                                          String staticAnalysisJson,
                                          String generatedCode,
                                          String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return SystemPrompts.buildCurrentRequestSection(String.format(
                    "Figure name: %s\n\n"
                            + "Compact storyboard JSON (reference context, not strict authority):\n```json\n%s\n```\n\n"
                            + "Static visual analysis:\n```json\n%s\n```\n\n"
                            + "GeoGebra command script to review:\n```geogebra\n%s\n```\n\n"
                            + "Check every mandatory GeoGebra rule before render.\n"
                            + "Focus on whether the actual construction, scene visibility progression, and teaching evidence are coherent and render-ready; use the storyboard only as reference context.\n"
                            + "Return only the structured rule-compliance output.",
                    sceneName, storyboardJson, staticAnalysisJson, generatedCode));
        }
        return SystemPrompts.buildCurrentRequestSection(String.format(
                "Scene class name: %s\n\n"
                        + "Compact storyboard JSON (reference context, not strict authority):\n```json\n%s\n```\n\n"
                        + "Static visual analysis:\n```json\n%s\n```\n\n"
                        + "Manim code to review:\n```python\n%s\n```\n\n"
                        + "Check every mandatory Manim rule before render.\n"
                        + "Focus on teaching coherence, internally consistent geometry, continuity, pacing versus narration, 3D readability, fixed-in-frame overlays, correct spatial relationships, text readability, and code-evident clutter. Use storyboard details as reference hints only.\n"
                        + "Return only the structured rule-compliance output.",
                sceneName, storyboardJson, staticAnalysisJson, generatedCode));
    }

    public static String buildRevisionRulesPrompt(String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return SystemPrompts.buildRulesSection(
                    SystemPrompts.ensureGeoGebraSyntaxManual(REVISION_SYSTEM_GEOGEBRA));
        }
        return SystemPrompts.buildRulesSection(
                SystemPrompts.ensureManimSyntaxManual(REVISION_SYSTEM_MANIM));
    }

    public static String buildRevisionFixedContextPrompt(String targetConcept,
                                                         String targetDescription,
                                                         String outputTarget) {
        return SystemPrompts.buildFixedContextSection(SystemPrompts.buildWorkflowPrefix(
                "Stage 3 / Code Evaluation",
                "Revise " + ("geogebra".equalsIgnoreCase(outputTarget) ? "GeoGebra code" : "Manim code")
                        + " after code evaluation before render",
                targetConcept,
                targetDescription,
                outputTarget
        ));
    }

    public static String revisionUserPrompt(String sceneName,
                                            String storyboardJson,
                                            String staticAnalysisJson,
                                            String reviewJson,
                                            String generatedCode) {
        return revisionUserPrompt(
                sceneName,
                storyboardJson,
                staticAnalysisJson,
                reviewJson,
                generatedCode,
                "manim");
    }

    public static String revisionUserPrompt(String sceneName,
                                            String storyboardJson,
                                            String staticAnalysisJson,
                                            String reviewJson,
                                            String generatedCode,
                                            String outputTarget) {
        if ("geogebra".equalsIgnoreCase(outputTarget)) {
            return SystemPrompts.buildCurrentRequestSection(String.format(
                    "Figure name: %s\n\n"
                            + "Compact storyboard JSON (reference context, not strict authority):\n```json\n%s\n```\n\n"
                            + "Static visual analysis:\n```json\n%s\n```\n\n"
                            + "Structured code review:\n```json\n%s\n```\n\n"
                            + "Current GeoGebra command script:\n```geogebra\n%s\n```\n\n"
                            + "Rewrite the FULL command script to be valid, coherent, readable, and aligned with the overall teaching goal. Use storyboard details as optional reference context.\n"
                            + "Keep implemented geometric relationships internally consistent; do not enforce storyboard geometry one-for-one when it conflicts with a safer implementation.\n"
                            + "Preserve the initial viewport contract with `SetCoordSystem(-7, 7, -4, 4)`, and fix layout by scaling/spreading/recentering the construction rather than relying on user zoom.\n"
                            + "Use only command names and syntax forms documented in the attached GeoGebra syntax manual. Replace any undocumented command or guessed syntax with a documented equivalent.\n"
                            + "Return ONLY the full GeoGebra code block.",
                    sceneName, storyboardJson, staticAnalysisJson, reviewJson, generatedCode));
        }
        return SystemPrompts.buildCurrentRequestSection(String.format(
                "Scene class name: %s\n\n"
                        + "Compact storyboard JSON (reference context, not strict authority):\n```json\n%s\n```\n\n"
                        + "Static visual analysis:\n```json\n%s\n```\n\n"
                        + "Structured code review:\n```json\n%s\n```\n\n"
                        + "Current Manim code:\n```python\n%s\n```\n\n"
                        + "Rewrite the FULL code to reduce clutter, preserve continuity, correct semantically wrong placements such as angle arcs or labels attached to the wrong geometry, better match pacing to narration, and keep 3D overlays readable.\n"
                        + "Keep implemented geometric relationships internally consistent while making layout safer; use storyboard geometry as reference context, not as a strict constraint.\n"
                        + "Also fix nearby Python/Manim runtime mistakes. Preserve the scene class name and teaching goal.\n"
                        + "Return ONLY the full Python code block.",
                sceneName, storyboardJson, staticAnalysisJson, reviewJson, generatedCode));
    }
}
