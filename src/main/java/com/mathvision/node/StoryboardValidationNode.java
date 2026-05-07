package com.mathvision.node;

import com.mathvision.config.WorkflowConfig;
import com.mathvision.model.KnowledgeGraph;
import com.mathvision.model.Narrative;
import com.mathvision.model.Narrative.Storyboard;
import com.mathvision.model.Narrative.StoryboardObject;
import com.mathvision.model.Narrative.StoryboardPlacement;
import com.mathvision.model.Narrative.StoryboardPlacementAxis;
import com.mathvision.model.Narrative.StoryboardScene;
import com.mathvision.model.StoryboardValidationReport;
import com.mathvision.model.StoryboardValidationTraceEntry;
import com.mathvision.model.WorkflowKeys;
import com.mathvision.prompt.NarrativePrompts;
import com.mathvision.prompt.SystemPrompts;
import com.mathvision.prompt.ToolSchemas;
import com.mathvision.service.AiClient;
import com.mathvision.service.FileOutputService;
import com.mathvision.util.AiRequestUtils;
import com.mathvision.util.JsonUtils;
import com.mathvision.util.NodeConversationContext;
import com.mathvision.util.StoryboardNormalizer;
import com.mathvision.util.StoryboardPatchResolver;
import com.mathvision.util.TargetDescriptionBuilder;
import com.mathvision.util.TimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.the_pocket.PocketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Stage 1c: Storyboard Validation - static checks on the assembled storyboard
 * followed by an optional LLM fix pass when issues are detected.
 *
 * Replaces NarrativeNode in the pipeline. Receives the Narrative already
 * assembled by VisualDesignNode and validates object lifecycle consistency,
 * scene ordering, and field completeness.
 */
public class StoryboardValidationNode extends PocketFlow.Node<Narrative, Narrative, String> {

    private static final Logger log = LoggerFactory.getLogger(StoryboardValidationNode.class);
    private static final String DEFAULT_STORYBOARD_BACKGROUND = "#000000";
    private static final double NON_TEXT_CONTRAST_THRESHOLD = 3.0;
    private static final double TEXT_CONTRAST_THRESHOLD = 4.5;
    private static final double FRAME_MIN_X = -7.111111;
    private static final double FRAME_MAX_X = 7.111111;
    private static final double FRAME_MIN_Y = -4.0;
    private static final double FRAME_MAX_Y = 4.0;
    private static final double OFFSCREEN_TOLERANCE = 0.03;
    private static final double MIN_OVERLAP_AREA = 0.015;
    private static final double MIN_OVERLAP_RATIO = 0.08;
    private static final double SPATIAL_BUCKET_SIZE = 1.25;
    private static final int MAX_VALIDATION_FIX_ATTEMPTS = 3;

    private AiClient aiClient;
    private WorkflowConfig workflowConfig;
    private KnowledgeGraph knowledgeGraph;
    private String outputTarget = WorkflowConfig.OUTPUT_TARGET_MANIM;
    private int toolCalls = 0;
    private StoryboardValidationReport storyboardValidationReport;

    public StoryboardValidationNode() {
        super(1, 0);
    }

    @Override
    public Narrative prep(Map<String, Object> ctx) {
        this.aiClient = (AiClient) ctx.get(WorkflowKeys.AI_CLIENT);
        this.workflowConfig = (WorkflowConfig) ctx.get(WorkflowKeys.CONFIG);
        this.knowledgeGraph = (KnowledgeGraph) ctx.get(WorkflowKeys.KNOWLEDGE_GRAPH);
        if (workflowConfig != null) {
            this.outputTarget = workflowConfig.getOutputTarget();
        }
        this.toolCalls = 0;
        this.storyboardValidationReport = null;
        return (Narrative) ctx.get(WorkflowKeys.NARRATIVE);
    }

    @Override
    public Narrative exec(Narrative narrative) {
        log.info("=== Stage 1c: Storyboard Validation ===");

        if (narrative == null || narrative.getStoryboard() == null) {
            log.warn("No narrative/storyboard to validate");
            this.storyboardValidationReport = buildSkippedReport("No narrative/storyboard to validate");
            return narrative;
        }

        Narrative current = narrative;
        Instant initialValidationStart = Instant.now();
        List<String> issues = validate(current.getStoryboard());
        this.storyboardValidationReport = baseReport(current.getStoryboard(), issues);
        appendValidationTraceEntry(
                current.getStoryboard(),
                "initial_validation",
                0,
                false,
                false,
                issues,
                0,
                TimeUtils.secondsSince(initialValidationStart),
                "Initial storyboard validation");
        logValidationIssues(issues);

        if (issues.isEmpty()) {
            log.info("Storyboard validation passed (no issues)");
            finalizeReport(storyboardValidationReport, true, false, false, List.of(),
                    "Storyboard validation passed");
            return current;
        }

        boolean fixApplied = false;
        int attempts = 0;
        while (!issues.isEmpty() && attempts < MAX_VALIDATION_FIX_ATTEMPTS) {
            attempts++;
            log.warn("Attempting LLM storyboard cleanup pass {}/{}",
                    attempts, MAX_VALIDATION_FIX_ATTEMPTS);
            Instant cleanupStart = Instant.now();
            int toolCallsBefore = toolCalls;
            Narrative fixed = attemptLlmFix(current, issues);
            int cleanupToolCalls = toolCalls - toolCallsBefore;
            if (fixed == null || fixed.getStoryboard() == null) {
                log.warn("LLM storyboard cleanup pass {}/{} did not return a usable storyboard",
                        attempts, MAX_VALIDATION_FIX_ATTEMPTS);
                appendValidationTraceEntry(
                        current.getStoryboard(),
                        "cleanup_failed",
                        attempts,
                        true,
                        false,
                        issues,
                        cleanupToolCalls,
                        TimeUtils.secondsSince(cleanupStart),
                        "LLM storyboard cleanup did not return a usable storyboard");
                finalizeReport(storyboardValidationReport, false, true, fixApplied, issues,
                        "Storyboard validation found issues and the automatic LLM cleanup did not succeed");
                return current;
            }

            current = fixed;
            fixApplied = true;
            issues = validate(current.getStoryboard());
            appendValidationTraceEntry(
                    current.getStoryboard(),
                    "post_cleanup_validation",
                    attempts,
                    true,
                    true,
                    issues,
                    cleanupToolCalls,
                    TimeUtils.secondsSince(cleanupStart),
                    issues.isEmpty()
                            ? "Cleanup pass " + attempts + " resolved all storyboard validation issues"
                            : "Cleanup pass " + attempts + " left " + issues.size() + " storyboard validation issue(s)");
            if (issues.isEmpty()) {
                log.info("LLM storyboard cleanup completed successfully after {} pass(es)", attempts);
                finalizeReport(storyboardValidationReport, true, true, true, issues,
                        "Storyboard validation issues were fixed successfully after " + attempts + " pass(es)");
                return current;
            }

            log.warn("LLM storyboard cleanup pass {}/{} left {} issues",
                    attempts, MAX_VALIDATION_FIX_ATTEMPTS, issues.size());
            logValidationIssues(issues);
        }

        log.warn("Storyboard validation still has {} issues after {} cleanup pass(es); proceeding to next node",
                issues.size(), attempts);
        finalizeReport(storyboardValidationReport, false, attempts > 0, fixApplied, issues,
                "Storyboard validation reached the maximum of " + MAX_VALIDATION_FIX_ATTEMPTS
                        + " cleanup pass(es); proceeding with remaining issues");
        return current;
    }

    private void logValidationIssues(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        log.warn("Storyboard validation found {} issues:", issues.size());
        for (String issue : issues) {
            log.warn("  - {}", issue);
        }
    }

    @Override
    public String post(Map<String, Object> ctx, Narrative prepRes, Narrative narrative) {
        ctx.put(WorkflowKeys.NARRATIVE, narrative);

        int prevCalls = (int) ctx.getOrDefault(WorkflowKeys.ENRICHMENT_TOOL_CALLS, 0);
        ctx.put(WorkflowKeys.ENRICHMENT_TOOL_CALLS, prevCalls + toolCalls);

        Path outputDir = (Path) ctx.get(WorkflowKeys.OUTPUT_DIR);
        if (outputDir != null) {
            FileOutputService.saveValidatedStoryboard(outputDir,
                    narrative != null ? narrative.getStoryboard() : null);
            StoryboardValidationReport reportToSave = storyboardValidationReport != null
                    ? storyboardValidationReport
                    : buildSkippedReport("Storyboard validation report was not produced");
            FileOutputService.saveStoryboardValidation(outputDir, reportToSave);
        }

        return null;
    }

    // ---- Static validation ----

    List<String> validate(Storyboard storyboard) {
        List<String> issues = new ArrayList<>();
        if (storyboard.getScenes() == null || storyboard.getScenes().isEmpty()) {
            issues.add("Storyboard has no scenes");
            return issues;
        }
        Storyboard mergedStoryboard = StoryboardPatchResolver.buildMergedStoryboard(storyboard);

        for (int i = 0; i < storyboard.getScenes().size(); i++) {
            StoryboardScene scene = storyboard.getScenes().get(i);
            String label = "scene " + (i + 1) + " (" + scene.getSceneId() + ")";
            StoryboardScene mergedScene = mergedStoryboard != null
                    && mergedStoryboard.getScenes() != null
                    && i < mergedStoryboard.getScenes().size()
                    ? mergedStoryboard.getScenes().get(i)
                    : null;
            validateSceneLayout(label, mergedScene, issues);

            // Check required fields
            if (scene.getTitle() == null || scene.getTitle().isBlank()) {
                issues.add(label + ": missing title");
            }
            if (scene.getGoal() == null || scene.getGoal().isBlank()) {
                issues.add(label + ": missing goal");
            }
        }

        issues.addAll(validateAsciiText(storyboard));
        issues.addAll(validateStoryboardColors(storyboard));
        issues.addAll(validateDependencyFields(storyboard));
        issues.addAll(validateGeometricMarkerDefinitions(storyboard));

        return issues;
    }

    private List<String> validateDependencyFields(Storyboard storyboard) {
        List<String> issues = new ArrayList<>();
        if (storyboard == null || storyboard.getObjectRegistry() == null) {
            return issues;
        }

        Map<String, StoryboardObject> registry = new LinkedHashMap<>();
        for (StoryboardObject object : storyboard.getObjectRegistry()) {
            String objectId = StoryboardPatchResolver.objectId(object);
            if (objectId != null) {
                registry.put(objectId, object);
            }
        }

        for (StoryboardObject object : registry.values()) {
            String objectId = StoryboardPatchResolver.objectId(object);
            List<String> dependencies = cleanDependencyObjects(object);
            boolean dependencyDriven = isDependencyDriven(object);
            if (dependencyDriven && dependencies.isEmpty()) {
                issues.add("object_registry: dependency-driven object '" + objectId
                        + "' must define dependency_objects with source object ids");
            }

            for (String dependencyId : dependencies) {
                if (objectId != null && objectId.equals(dependencyId)) {
                    issues.add("object_registry: object '" + objectId + "' cannot depend on itself");
                } else if (!registry.containsKey(dependencyId)) {
                    issues.add("object_registry: object '" + objectId
                            + "' references unknown dependency_object '" + dependencyId + "'");
                }
            }
        }

        return issues;
    }

    private List<String> validateGeometricMarkerDefinitions(Storyboard storyboard) {
        List<String> issues = new ArrayList<>();
        if (storyboard == null || storyboard.getObjectRegistry() == null) {
            return issues;
        }
        for (StoryboardObject object : storyboard.getObjectRegistry()) {
            if (!isAngleOrArcMarker(object)) {
                continue;
            }
            String objectId = StoryboardPatchResolver.objectId(object);
            List<String> dependencies = cleanDependencyObjects(object);
            String dependency = normalizeForSemanticCheck(String.join(" ", dependencies) + " "
                    + safe(object.getDependencyRelation()) + " " + safe(object.getConstraintNote()));
            String constraint = normalizeForSemanticCheck(object.getConstraintNote());

            if (dependencies.isEmpty() && !mentionsAngleVertex(object, dependency)) {
                issues.add("object_registry: angle/arc marker '" + objectId
                        + "' must include its measured vertex or anchor in dependency_objects");
            }
            if (dependencies.size() < 2 && !mentionsTwoAngleBoundaries(dependency)) {
                issues.add("object_registry: angle/arc marker '" + objectId
                        + "' must define both boundary rays, segments, lines, normals, tangents, or source objects in dependency_objects/dependency_relation");
            }
            if (isArcMarker(object) && !mentionsOrderedArcSweep(dependency, constraint)) {
                issues.add("object_registry: arc marker '" + objectId
                        + "' must define the ordered arc sweep, including where the arc starts and where it ends");
            }
            if (!mentionsAngleSector(constraint)) {
                issues.add("object_registry: angle/arc marker '" + objectId
                        + "' must define the intended displayed sector in constraint_note (for example smaller/interior, directed, exterior, or side of a reference line/normal)");
            }
            if (usesLayoutOnlyQuadrant(constraint)) {
                issues.add("object_registry: angle/arc marker '" + objectId
                        + "' uses layout-only quadrant wording; define the measured sector geometrically before label-clearance or visibility notes");
            }
        }
        return issues;
    }

    private boolean isAngleOrArcMarker(StoryboardObject object) {
        if (object == null) {
            return false;
        }
        String kind = normalizeForSemanticCheck(object.getKind());
        String relation = normalizeForSemanticCheck(object.getDependencyRelation());

        if (isTextRenderKind(kind) || isLabelRelation(relation)) {
            return false;
        }

        boolean markerKind = isAngleOrArcMarkerKind(kind);
        boolean markerRelation = isAngleOrArcMarkerRelation(relation);
        if (markerKind || markerRelation) {
            return true;
        }

        if (!kind.isBlank()) {
            return false;
        }

        return hasLegacyAngleOrArcMarkerIdFallback(object);
    }

    private boolean isArcMarker(StoryboardObject object) {
        if (object == null) {
            return false;
        }
        String kind = normalizeForSemanticCheck(object.getKind());
        String relation = normalizeForSemanticCheck(object.getDependencyRelation());
        if (isTextRenderKind(kind) || isLabelRelation(relation)) {
            return false;
        }
        if (isArcMarkerKind(kind) || containsAny(relation, " arc_sweep ")) {
            return true;
        }
        return kind.isBlank() && containsAny(normalizeForSemanticCheck(object.getId()), " arc ");
    }

    private boolean isAngleOrArcMarkerKind(String kind) {
        return containsAny(kind,
                " angle_marker ", " anglemarker ", " arc_marker ", " arc ", " right_angle ", " rightangle ");
    }

    private boolean isAngleOrArcMarkerRelation(String relation) {
        return containsAny(relation,
                " angle_between ", " angle_at_vertex ", " directed_angle ", " arc_sweep ",
                " angle_marker ", " right_angle ");
    }

    private boolean isArcMarkerKind(String kind) {
        return containsAny(kind, " arc ", " arc_marker ");
    }

    private boolean hasLegacyAngleOrArcMarkerIdFallback(StoryboardObject object) {
        String id = normalizeForSemanticCheck(object.getId());
        String semanticFields = normalizeForSemanticCheck(String.join(" ",
                safe(object.getContent()),
                String.join(" ", cleanDependencyObjects(object)),
                safe(object.getDependencyRelation()),
                safe(object.getConstraintNote())));
        boolean markerId = containsAny(id, " angle ", " arc ", " anglemarker ", " angle_marker ");
        boolean angleMeaning = containsAny(semanticFields, "theta", " angle ", " perpendicular", " normal", " tangent");
        return markerId && angleMeaning;
    }

    private boolean isTextRenderKind(String kind) {
        return containsAny(kind,
                " text ", " label ", " text_card ", " equation ", " formula ", " formula_card ",
                " title ", " caption ");
    }

    private boolean isLabelRelation(String relation) {
        return containsAny(relation, " label_for ", " follows_anchor ");
    }

    private boolean mentionsAngleVertex(StoryboardObject object, String dependency) {
        if (containsAny(dependency, "vertex", " at ", "center", "shared point", "anchor")) {
            return true;
        }
        String anchorId = normalizeForSemanticCheck(object != null ? object.getAnchorId() : null);
        return !anchorId.isBlank() && containsToken(dependency, anchorId);
    }

    private boolean mentionsTwoAngleBoundaries(String dependency) {
        if (dependency.isBlank()) {
            return false;
        }
        if (containsAny(dependency, " between ") && containsAny(dependency, " and ", " vs ", " versus ")) {
            return true;
        }
        int boundaryTerms = 0;
        for (String term : List.of("ray", "segment", "line", "normal", "perpendicular", "tangent", "vector")) {
            if (containsAny(dependency, term)) {
                boundaryTerms++;
            }
        }
        return boundaryTerms >= 2;
    }

    private boolean mentionsOrderedArcSweep(String dependency, String constraint) {
        String combined = normalizeForSemanticCheck(dependency + " " + constraint);
        boolean hasStart = containsAny(combined, " from ", " start ", " starts ", " starting ", " first ");
        boolean hasEnd = containsAny(combined, " to ", " end ", " ends ", " ending ", " second ");
        return hasStart && hasEnd;
    }

    private boolean mentionsAngleSector(String constraint) {
        return containsAny(constraint,
                "smaller", "small ", "minor", "interior", "inside", "sector", "wedge",
                "directed", "exterior", "reflex", "clockwise", "counterclockwise",
                "same side", "opposite side", "left side", "right side", "above", "below",
                "toward", "away from", "side of");
    }

    private boolean usesLayoutOnlyQuadrant(String constraint) {
        if (!containsAny(constraint, "quadrant")) {
            return false;
        }
        boolean layoutOnly = containsAny(constraint, "view", "clear", "label", "visible", "readable", "layout");
        return layoutOnly && !mentionsAngleSector(constraint);
    }

    private String normalizeForSemanticCheck(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_*']+", " ").trim() + " ";
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsToken(String haystack, String token) {
        if (haystack == null || token == null || token.isBlank()) {
            return false;
        }
        return haystack.contains(" " + token.trim() + " ");
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private List<String> cleanDependencyObjects(StoryboardObject object) {
        List<String> dependencies = new ArrayList<>();
        if (object == null || object.getDependencyObjects() == null) {
            return dependencies;
        }
        for (String dependencyId : object.getDependencyObjects()) {
            if (dependencyId != null && !dependencyId.isBlank()) {
                dependencies.add(dependencyId.trim());
            }
        }
        return dependencies;
    }

    private boolean isDependencyDriven(StoryboardObject object) {
        if (object == null) {
            return false;
        }
        String behavior = object.getBehavior();
        String relation = object.getDependencyRelation();
        return Narrative.StoryboardObject.BEHAVIOR_DERIVED.equalsIgnoreCase(behavior)
                || Narrative.StoryboardObject.BEHAVIOR_FOLLOWS_ANCHOR.equalsIgnoreCase(behavior)
                || (!isBlank(relation) && !relation.trim().equalsIgnoreCase("independent")
                && !relation.trim().equalsIgnoreCase("independent_overlay"));
    }

    private List<String> validateStoryboardColors(Storyboard storyboard) {
        List<String> issues = new ArrayList<>();
        if (storyboard == null) {
            return issues;
        }
        validateObjectColors("object_registry", storyboard.getObjectRegistry(), issues);
        if (storyboard.getScenes() != null) {
            for (int i = 0; i < storyboard.getScenes().size(); i++) {
                StoryboardScene scene = storyboard.getScenes().get(i);
                String sceneLabel = "scene " + (i + 1) + " (" + (scene != null ? scene.getSceneId() : null) + ")";
                if (scene == null) {
                    continue;
                }
                validateObjectColors(sceneLabel + " entering_objects", scene.getEnteringObjects(), issues);
                validateObjectColors(sceneLabel + " persistent_objects", scene.getPersistentObjects(), issues);
            }
        }
        return issues;
    }

    private void validateObjectColors(String context,
                                      List<StoryboardObject> objects,
                                      List<String> issues) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        for (StoryboardObject object : objects) {
            if (object == null || object.getStyle() == null) {
                continue;
            }
            String objectId = StoryboardPatchResolver.objectId(object);
            List<ColorReference> colors = collectColorReferences(object);
            for (ColorReference color : colors) {
                if (!isSixDigitHexColor(color.value)) {
                    issues.add(context + ": object '" + objectId + "' uses invalid color '" + color.value
                            + "' at style." + color.propertyPath
                            + "; expected 6-digit hex format #RRGGBB with opacity in a separate opacity field");
                }
            }
            validateObjectColorContrast(context, objectId, object, colors, issues);
        }
    }

    private List<ColorReference> collectColorReferences(StoryboardObject object) {
        List<ColorReference> colors = new ArrayList<>();
        if (object == null || object.getStyle() == null) {
            return colors;
        }
        for (Narrative.StoryboardStyle style : object.getStyle()) {
            if (style == null || style.getProperties() == null) {
                continue;
            }
            String role = style.getRole();
            String type = style.getType();
            collectColorProperties(style.getProperties(), "", role, type, colors);
        }
        return colors;
    }

    private void collectColorProperties(Map<String, Object> properties,
                                        String path,
                                        String styleRole,
                                        String styleType,
                                        List<ColorReference> colors) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey();
            String childPath = path.isBlank() ? key : path + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) {
                Map<?, ?> nestedMap = (Map<?, ?>) value;
                Map<String, Object> stringKeyed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> nestedEntry : nestedMap.entrySet()) {
                    if (nestedEntry.getKey() != null) {
                        stringKeyed.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
                    }
                }
                collectColorProperties(stringKeyed, childPath, styleRole, styleType, colors);
            } else if (value instanceof List<?>) {
                List<?> values = (List<?>) value;
                for (int i = 0; i < values.size(); i++) {
                    collectColorValue(childPath + "[" + i + "]", values.get(i), styleRole, styleType, colors);
                }
            } else {
                collectColorValue(childPath, value, styleRole, styleType, colors);
            }
        }
    }

    private void collectColorValue(String propertyPath,
                                   Object rawValue,
                                   String styleRole,
                                   String styleType,
                                   List<ColorReference> colors) {
        if (rawValue == null || propertyPath == null
                || !propertyPath.toLowerCase(Locale.ROOT).contains("color")) {
            return;
        }
        String color = String.valueOf(rawValue).trim();
        if (color.isBlank()) {
            return;
        }
        colors.add(new ColorReference(propertyPath, color, styleRole, styleType));
    }

    private void validateObjectColorContrast(String context,
                                             String objectId,
                                             StoryboardObject object,
                                             List<ColorReference> colors,
                                             List<String> issues) {
        List<ColorReference> validColors = colors.stream()
                .filter(color -> isSixDigitHexColor(color.value))
                .collect(java.util.stream.Collectors.toList());
        if (validColors.isEmpty()) {
            return;
        }

        if (isTextualColorObject(object, validColors)) {
            ColorReference foreground = selectTextForeground(validColors);
            if (foreground == null) {
                return;
            }
            ColorReference background = selectTextBackground(validColors);
            String backgroundColor = background != null ? background.value : DEFAULT_STORYBOARD_BACKGROUND;
            validateContrast(context, objectId, foreground.value, backgroundColor,
                    TEXT_CONTRAST_THRESHOLD, "text", issues);
            return;
        }

        for (ColorReference foreground : validColors) {
            if (foreground.isExplicitBackground()) {
                continue;
            }
            validateContrast(context, objectId, foreground.value, DEFAULT_STORYBOARD_BACKGROUND,
                    NON_TEXT_CONTRAST_THRESHOLD, "non-text", issues);
        }
    }

    private boolean isSixDigitHexColor(String color) {
        return color != null && color.matches("#[0-9A-Fa-f]{6}");
    }

    private void validateContrast(String context,
                                  String objectId,
                                  String foreground,
                                  String background,
                                  double threshold,
                                  String category,
                                  List<String> issues) {
        double contrast = contrastRatio(foreground, background);
        if (contrast + 1e-9 < threshold) {
            issues.add(context + ": object '" + objectId + "' has insufficient " + category
                    + " color contrast; foreground=" + foreground.toUpperCase(Locale.ROOT)
                    + ", background=" + background.toUpperCase(Locale.ROOT)
                    + ", contrast=" + formatContrast(contrast)
                    + ", required>=" + formatContrast(threshold));
        }
    }

    private double contrastRatio(String foreground, String background) {
        double fg = relativeLuminance(foreground);
        double bg = relativeLuminance(background);
        double lighter = Math.max(fg, bg);
        double darker = Math.min(fg, bg);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double relativeLuminance(String color) {
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);
        return 0.2126 * linearRgbChannel(r)
                + 0.7152 * linearRgbChannel(g)
                + 0.0722 * linearRgbChannel(b);
    }

    private double linearRgbChannel(int channel) {
        double srgb = channel / 255.0;
        return srgb <= 0.03928 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private String formatContrast(double contrast) {
        return String.format(Locale.ROOT, "%.2f", contrast);
    }

    private boolean isTextualColorObject(StoryboardObject object, List<ColorReference> colors) {
        if (isTextual(object)) {
            return true;
        }
        return colors.stream().anyMatch(ColorReference::isTextLayer);
    }

    private ColorReference selectTextForeground(List<ColorReference> colors) {
        for (ColorReference color : colors) {
            if (color.isTextLayer() && !color.isExplicitBackground()) {
                return color;
            }
        }
        for (ColorReference color : colors) {
            if (!color.isExplicitBackground()) {
                return color;
            }
        }
        return null;
    }

    private ColorReference selectTextBackground(List<ColorReference> colors) {
        for (ColorReference color : colors) {
            if (color.isTextBackgroundFill()) {
                return color;
            }
        }
        for (ColorReference color : colors) {
            if (color.isExplicitBackground()) {
                return color;
            }
        }
        return null;
    }

    private List<String> validateAsciiText(Storyboard storyboard) {
        List<String> issues = new ArrayList<>();
        List<String> nonAsciiTokens = findNonAsciiTextTokens(storyboard);
        if (!nonAsciiTokens.isEmpty()) {
            issues.add("Storyboard contains non-ASCII text tokens that must be replaced with ASCII equivalents: "
                    + nonAsciiTokens);
        }
        return issues;
    }

    private List<String> findNonAsciiTextTokens(Storyboard storyboard) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (storyboard == null) {
            return new ArrayList<>();
        }
        JsonNode root = JsonUtils.mapper().valueToTree(storyboard);
        collectNonAsciiTextTokens(root, tokens);
        return new ArrayList<>(tokens);
    }

    private void collectNonAsciiTextTokens(JsonNode node, Set<String> tokens) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addNonAsciiTokens(node.asText(), tokens);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectNonAsciiTextTokens(item, tokens);
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    collectNonAsciiTextTokens(entry.getValue(), tokens));
        }
    }

    private void addNonAsciiTokens(String text, Set<String> tokens) {
        if (isBlank(text) || !containsNonAscii(text)) {
            return;
        }
        for (String rawToken : text.split("\\s+")) {
            String token = trimAsciiBoundaryPunctuation(rawToken);
            if (!token.isBlank() && containsNonAscii(token)) {
                tokens.add(token);
            }
        }
    }

    private boolean containsNonAscii(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    private String trimAsciiBoundaryPunctuation(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        int start = 0;
        int end = token.length();
        while (start < end && isAsciiBoundaryPunctuation(token.charAt(start))) {
            start++;
        }
        while (end > start && isAsciiBoundaryPunctuation(token.charAt(end - 1))) {
            end--;
        }
        return token.substring(start, end);
    }

    private boolean isAsciiBoundaryPunctuation(char ch) {
        return ch <= 0x7F && !Character.isLetterOrDigit(ch);
    }

    private void validateSceneLayout(String sceneLabel,
                                     StoryboardScene mergedScene,
                                     List<String> issues) {
        if (mergedScene == null) {
            return;
        }

        List<StoryboardLayoutElement> elements = resolveSceneLayoutElements(sceneLabel, mergedScene, issues);
        if (elements.isEmpty()) {
            return;
        }

        for (StoryboardLayoutElement element : elements) {
            String overflowSummary = summarizeOverflow(element.bounds);
            if (overflowSummary != null) {
                issues.add(formatOffscreenIssue(sceneLabel, element, overflowSummary, elements));
            }
        }

        issues.addAll(evaluateLayoutOverlapIssues(sceneLabel, elements));
    }

    private List<StoryboardLayoutElement> resolveSceneLayoutElements(String sceneLabel,
                                                                     StoryboardScene mergedScene,
                                                                     List<String> issues) {
        Map<String, StoryboardObject> visibleObjects = new LinkedHashMap<>();
        addVisibleObjects(visibleObjects, mergedScene.getPersistentObjects());
        addVisibleObjects(visibleObjects, mergedScene.getEnteringObjects());

        List<StoryboardLayoutElement> elements = new ArrayList<>();
        Map<String, StoryboardLayoutElement> cache = new LinkedHashMap<>();
        Set<String> resolvingIds = new HashSet<>();
        for (StoryboardObject object : visibleObjects.values()) {
            StoryboardLayoutElement element = resolveLayoutElement(
                    sceneLabel, object, visibleObjects, cache, resolvingIds, issues);
            if (element != null) {
                elements.add(element);
            }
        }
        return elements;
    }

    private void addVisibleObjects(Map<String, StoryboardObject> visibleObjects,
                                   List<StoryboardObject> objects) {
        if (objects == null) {
            return;
        }
        for (StoryboardObject object : objects) {
            String objectId = StoryboardPatchResolver.objectId(object);
            if (objectId != null) {
                visibleObjects.put(objectId, object);
            }
        }
    }

    private StoryboardLayoutElement resolveLayoutElement(String sceneLabel,
                                                         StoryboardObject object,
                                                         Map<String, StoryboardObject> visibleObjects,
                                                         Map<String, StoryboardLayoutElement> cache,
                                                         Set<String> resolvingIds,
                                                         List<String> issues) {
        String objectId = StoryboardPatchResolver.objectId(object);
        if (objectId == null) {
            return null;
        }
        if (cache.containsKey(objectId)) {
            return cache.get(objectId);
        }

        StoryboardPlacement placement = object != null ? object.getPlacement() : null;
        if (placement == null || !placement.hasData()) {
            cache.put(objectId, null);
            return null;
        }

        if (!resolvingIds.add(objectId)) {
            cache.put(objectId, null);
            return null;
        }

        try {
            StoryboardLayoutBounds bounds = resolveLayoutBounds(
                    sceneLabel, object, visibleObjects, cache, resolvingIds, issues);
            if (bounds == null) {
                cache.put(objectId, null);
                return null;
            }

            StoryboardLayoutElement element = new StoryboardLayoutElement(objectId, object, bounds);
            cache.put(objectId, element);
            return element;
        } finally {
            resolvingIds.remove(objectId);
        }
    }

    private StoryboardLayoutBounds resolveLayoutBounds(String sceneLabel,
                                                       StoryboardObject object,
                                                       Map<String, StoryboardObject> visibleObjects,
                                                       Map<String, StoryboardLayoutElement> cache,
                                                       Set<String> resolvingIds,
                                                       List<String> issues) {
        if (object == null || object.getPlacement() == null || !object.getPlacement().hasData()) {
            return null;
        }

        StoryboardPlacement placement = object.getPlacement();
        String coordinateSpace = placement.getCoordinateSpace();
        if (isBlank(coordinateSpace)) {
            return null;
        }

        AxisBounds xBounds;
        AxisBounds yBounds;
        if (Narrative.StoryboardPlacement.COORDINATE_SPACE_ANCHOR.equalsIgnoreCase(coordinateSpace)) {
            String rawAnchorId = !isBlank(object.getAnchorId()) ? object.getAnchorId().trim() : null;
            String anchorId = StoryboardPatchResolver.objectId(visibleObjects.get(rawAnchorId));
            if (anchorId == null) {
                return null;
            }
            StoryboardLayoutElement anchorElement = resolveLayoutElement(
                    sceneLabel,
                    visibleObjects.get(anchorId),
                    visibleObjects,
                    cache,
                    resolvingIds,
                    issues);
            if (anchorElement == null) {
                return null;
            }
            xBounds = resolveAxisBounds(placement.getX(), anchorElement.bounds.centerX(), true);
            yBounds = resolveAxisBounds(placement.getY(), anchorElement.bounds.centerY(), true);
        } else if (Narrative.StoryboardPlacement.COORDINATE_SPACE_WORLD.equalsIgnoreCase(coordinateSpace)
                || Narrative.StoryboardPlacement.COORDINATE_SPACE_SCREEN.equalsIgnoreCase(coordinateSpace)) {
            if (placement.getX() == null && placement.getY() == null) {
                return null;
            }
            xBounds = resolveAxisBounds(placement.getX(), 0.0, false);
            yBounds = resolveAxisBounds(placement.getY(), 0.0, false);
        } else {
            return null;
        }

        return new StoryboardLayoutBounds(xBounds.min, xBounds.max, yBounds.min, yBounds.max);
    }

    private AxisBounds resolveAxisBounds(StoryboardPlacementAxis axis,
                                         double fallbackCenter,
                                         boolean relativeToBase) {
        if (axis == null || !axis.hasData()) {
            return new AxisBounds(fallbackCenter, fallbackCenter);
        }

        Double rawMin = axis.getMin() != null
                ? axis.getMin()
                : axis.getValue() != null ? axis.getValue() : axis.getMax();
        Double rawMax = axis.getMax() != null
                ? axis.getMax()
                : axis.getValue() != null ? axis.getValue() : axis.getMin();
        if (rawMin == null || rawMax == null) {
            return new AxisBounds(fallbackCenter, fallbackCenter);
        }

        double resolvedMin = relativeToBase ? fallbackCenter + rawMin : rawMin;
        double resolvedMax = relativeToBase ? fallbackCenter + rawMax : rawMax;
        return new AxisBounds(
                round(Math.min(resolvedMin, resolvedMax)),
                round(Math.max(resolvedMin, resolvedMax)));
    }

    private String summarizeOverflow(StoryboardLayoutBounds bounds) {
        double left = Math.max(FRAME_MIN_X - bounds.minX, 0.0);
        double right = Math.max(bounds.maxX - FRAME_MAX_X, 0.0);
        double bottom = Math.max(FRAME_MIN_Y - bounds.minY, 0.0);
        double top = Math.max(bounds.maxY - FRAME_MAX_Y, 0.0);
        if (Math.max(Math.max(left, right), Math.max(bottom, top)) <= OFFSCREEN_TOLERANCE) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        if (left > OFFSCREEN_TOLERANCE) {
            parts.add("left=" + round(left));
        }
        if (right > OFFSCREEN_TOLERANCE) {
            parts.add("right=" + round(right));
        }
        if (bottom > OFFSCREEN_TOLERANCE) {
            parts.add("bottom=" + round(bottom));
        }
        if (top > OFFSCREEN_TOLERANCE) {
            parts.add("top=" + round(top));
        }
        return String.join(", ", parts);
    }

    private String formatOffscreenIssue(String sceneLabel,
                                        StoryboardLayoutElement element,
                                        String overflowSummary,
                                        List<StoryboardLayoutElement> elements) {
        StringBuilder sb = new StringBuilder();
        sb.append("Issue: ").append(sceneLabel).append(": object '").append(element.objectId)
                .append("' extends outside the frame bounds (").append(overflowSummary).append(")");
        String dependencyContext = formatDependencyContext(element.objectId, element.object, elements);
        if (!dependencyContext.isBlank()) {
            sb.append("\n").append(dependencyContext);
        }
        return sb.toString();
    }

    private String formatDependencyContext(String objectId,
                                           StoryboardObject object,
                                           List<StoryboardLayoutElement> elements) {
        List<String> dependencies = cleanDependencyObjects(object);
        if (object == null || dependencies.isEmpty()) {
            return "";
        }
        Map<String, StoryboardLayoutElement> byId = new LinkedHashMap<>();
        for (StoryboardLayoutElement element : elements) {
            if (element != null && element.objectId != null) {
                byId.put(element.objectId, element);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Dependency chain:\n");
        sb.append("- ").append(objectId).append(" depends on [")
                .append(String.join(", ", dependencies)).append("]\n");
        appendDependencyPlacementLines(dependencies, byId, new LinkedHashSet<>(), sb);
        if (!isBlank(object.getDependencyRelation())) {
            sb.append("- relation: ").append(object.getDependencyRelation().trim()).append("\n");
        }
        if (!isBlank(object.getConstraintNote())) {
            sb.append("- constraint: ").append(object.getConstraintNote().trim()).append("\n");
        }
        return sb.toString();
    }

    private void appendDependencyPlacementLines(List<String> dependencyIds,
                                                Map<String, StoryboardLayoutElement> byId,
                                                LinkedHashSet<String> visited,
                                                StringBuilder sb) {
        for (String dependencyId : dependencyIds) {
            if (dependencyId == null || !visited.add(dependencyId)) {
                continue;
            }
            StoryboardLayoutElement dependencyElement = byId.get(dependencyId);
            if (dependencyElement == null || dependencyElement.object == null) {
                sb.append("- ").append(dependencyId).append(": placement unavailable\n");
                continue;
            }
            StoryboardObject dependencyObject = dependencyElement.object;
            sb.append("- ").append(dependencyId).append(": ")
                    .append(formatPlacementSummary(dependencyObject)).append("\n");
            List<String> nestedDependencies = cleanDependencyObjects(dependencyObject);
            if (!nestedDependencies.isEmpty()) {
                sb.append("- ").append(dependencyId).append(" depends on [")
                        .append(String.join(", ", nestedDependencies)).append("]\n");
                appendDependencyPlacementLines(nestedDependencies, byId, visited, sb);
            }
        }
    }

    private String formatPlacementSummary(StoryboardObject object) {
        if (object == null || object.getPlacement() == null || !object.getPlacement().hasData()) {
            return "placement unavailable";
        }
        StoryboardPlacement placement = object.getPlacement();
        StringBuilder sb = new StringBuilder();
        String coordinateSpace = !isBlank(placement.getCoordinateSpace())
                ? placement.getCoordinateSpace().trim()
                : "unknown";
        sb.append(coordinateSpace).append(" placement");
        if (Narrative.StoryboardPlacement.COORDINATE_SPACE_ANCHOR.equalsIgnoreCase(coordinateSpace)
                && !isBlank(object.getAnchorId())) {
            sb.append(" anchor=").append(object.getAnchorId().trim());
        }
        List<String> axisParts = new ArrayList<>();
        String xSummary = formatAxisSummary("x", placement.getX());
        String ySummary = formatAxisSummary("y", placement.getY());
        if (!xSummary.isBlank()) {
            axisParts.add(xSummary);
        }
        if (!ySummary.isBlank()) {
            axisParts.add(ySummary);
        }
        if (!axisParts.isEmpty()) {
            sb.append(" ").append(String.join(", ", axisParts));
        }
        return sb.toString();
    }

    private String formatAxisSummary(String axisName, StoryboardPlacementAxis axis) {
        if (axis == null || !axis.hasData()) {
            return "";
        }
        if (axis.getValue() != null) {
            return axisName + "=" + round(axis.getValue());
        }
        Double min = axis.getMin();
        Double max = axis.getMax();
        if (min != null && max != null) {
            double roundedMin = round(min);
            double roundedMax = round(max);
            if (Double.compare(roundedMin, roundedMax) == 0) {
                return axisName + "=" + roundedMin;
            }
            return axisName + "=" + roundedMin + ".." + roundedMax;
        }
        if (min != null) {
            return axisName + ">=" + round(min);
        }
        if (max != null) {
            return axisName + "<=" + round(max);
        }
        return "";
    }

    private List<String> evaluateLayoutOverlapIssues(String sceneLabel,
                                                     List<StoryboardLayoutElement> elements) {
        List<String> issues = new ArrayList<>();
        if (elements.size() < 2) {
            return issues;
        }

        Map<Long, List<Integer>> buckets = buildSpatialBuckets(elements);
        for (int index = 0; index < elements.size(); index++) {
            StoryboardLayoutElement left = elements.get(index);
            LayoutBucketRange range = bucketRange(left.bounds);
            Set<Integer> seenCandidates = new LinkedHashSet<>();

            for (int bucketX = range.minX; bucketX <= range.maxX; bucketX++) {
                for (int bucketY = range.minY; bucketY <= range.maxY; bucketY++) {
                    List<Integer> bucketElements = buckets.get(bucketKey(bucketX, bucketY));
                    if (bucketElements == null || bucketElements.isEmpty()) {
                        continue;
                    }

                    for (Integer candidateIndex : bucketElements) {
                        if (candidateIndex == null
                                || candidateIndex <= index
                                || !seenCandidates.add(candidateIndex)) {
                            continue;
                        }

                        StoryboardLayoutElement right = elements.get(candidateIndex);
                        if (!shouldCheckLayoutOverlap(left, right)) {
                            continue;
                        }

                        if (!overlapsSignificantly(left.bounds, right.bounds)) {
                            continue;
                        }

                        String blockingIssue = classifyLayoutOverlap(sceneLabel, left, right);
                        if (blockingIssue != null) {
                            issues.add(blockingIssue);
                        }
                    }
                }
            }
        }
        return issues;
    }

    private Map<Long, List<Integer>> buildSpatialBuckets(List<StoryboardLayoutElement> elements) {
        Map<Long, List<Integer>> buckets = new LinkedHashMap<>();
        for (int index = 0; index < elements.size(); index++) {
            LayoutBucketRange range = bucketRange(elements.get(index).bounds);
            for (int bucketX = range.minX; bucketX <= range.maxX; bucketX++) {
                for (int bucketY = range.minY; bucketY <= range.maxY; bucketY++) {
                    long key = bucketKey(bucketX, bucketY);
                    buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
                }
            }
        }
        return buckets;
    }

    private LayoutBucketRange bucketRange(StoryboardLayoutBounds bounds) {
        return new LayoutBucketRange(
                bucketIndex(bounds.minX),
                bucketIndex(bounds.maxX),
                bucketIndex(bounds.minY),
                bucketIndex(bounds.maxY));
    }

    private int bucketIndex(double value) {
        return (int) Math.floor(value / SPATIAL_BUCKET_SIZE);
    }

    private long bucketKey(int bucketX, int bucketY) {
        return (((long) bucketX) << 32) ^ (bucketY & 0xffffffffL);
    }

    private boolean shouldCheckLayoutOverlap(StoryboardLayoutElement left,
                                             StoryboardLayoutElement right) {
        if (left == null || right == null) {
            return false;
        }
        return !left.objectId.equals(right.objectId);
    }

    private boolean overlapsSignificantly(StoryboardLayoutBounds left,
                                          StoryboardLayoutBounds right) {
        double overlapWidth = Math.min(left.maxX, right.maxX) - Math.max(left.minX, right.minX);
        double overlapHeight = Math.min(left.maxY, right.maxY) - Math.max(left.minY, right.minY);
        if (overlapWidth <= 1e-9 || overlapHeight <= 1e-9) {
            return false;
        }
        double area = overlapWidth * overlapHeight;
        double leftArea = Math.max(left.area(), 1e-9);
        double rightArea = Math.max(right.area(), 1e-9);
        double minAreaRatio = area / Math.min(leftArea, rightArea);
        return area >= MIN_OVERLAP_AREA && minAreaRatio >= MIN_OVERLAP_RATIO;
    }

    private String classifyLayoutOverlap(String sceneLabel,
                                         StoryboardLayoutElement left,
                                         StoryboardLayoutElement right) {
        boolean leftText = isTextual(left.object);
        boolean rightText = isTextual(right.object);
        if (leftText && rightText) {
            return sceneLabel + ": text objects '" + left.objectId
                    + "' and '" + right.objectId + "' overlap";
        }

        if (leftText ^ rightText) {
            StoryboardLayoutElement textElement = leftText ? left : right;
            StoryboardLayoutElement otherElement = leftText ? right : left;
            if (isAttachedLabelPair(textElement.object, otherElement.object)) {
                return null;
            }
            return sceneLabel + ": text object '" + textElement.objectId
                    + "' overlaps object '" + otherElement.objectId + "'";
        }

        return sceneLabel + ": objects '" + left.objectId
                + "' and '" + right.objectId + "' overlap";
    }

    private boolean isTextual(StoryboardObject object) {
        if (object == null) {
            return false;
        }
        if (isTextRenderKind(normalizeForSemanticCheck(object.getKind()))) {
            return true;
        }
        if (object.getStyle() == null) {
            return false;
        }
        return object.getStyle().stream()
                .anyMatch(style -> style != null
                        && !isBlank(style.getType())
                        && style.getType().toLowerCase(Locale.ROOT).contains("text"));
    }

    private boolean isAttachedLabelPair(StoryboardObject textObject,
                                        StoryboardObject otherObject) {
        if (textObject == null || otherObject == null) {
            return false;
        }
        String otherId = StoryboardPatchResolver.objectId(otherObject);
        if (!isBlank(textObject.getAnchorId()) && textObject.getAnchorId().trim().equals(otherId)) {
            return true;
        }
        if (!isLikelyLabel(textObject)) {
            return false;
        }
        String textStem = semanticStem(StoryboardPatchResolver.objectId(textObject));
        String otherStem = semanticStem(otherId);
        return textStem != null && textStem.equals(otherStem);
    }

    private boolean isLikelyLabel(StoryboardObject object) {
        if (object == null) {
            return false;
        }
        String kind = normalizeForSemanticCheck(object.getKind());
        String relation = normalizeForSemanticCheck(object.getDependencyRelation());
        if (containsAny(kind, " label ")
                || isLabelRelation(relation)
                || !isBlank(object.getAnchorId())) {
            return true;
        }
        if (!kind.isBlank()) {
            return false;
        }
        String objectId = StoryboardPatchResolver.objectId(object);
        if (objectId == null) {
            return false;
        }
        String normalized = objectId.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith("_label")
                || normalized.endsWith("label")
                || normalized.endsWith("_text")
                || normalized.endsWith("text")
                || normalized.startsWith("label_")
                || normalized.startsWith("text_");
    }

    private String semanticStem(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return null;
        }
        String normalized = objectId.trim().toLowerCase(Locale.ROOT);
        // Strip common label/text suffixes first
        String[] suffixes = {"_label", "_text", "label", "text"};
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                normalized = normalized.substring(0, normalized.length() - suffix.length());
                break;
            }
        }
        // Strip common type prefixes (label, text, and geometry types) to align with
        // SceneEvaluationNode.semanticFamilyKey behaviour so that e.g. "label_a" and
        // "point_a" resolve to the same stem "a".
        String[] prefixes = {"label_", "text_", "point_", "seg_", "line_", "brace_", "bar_"};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        return normalized;
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private StoryboardValidationReport buildSkippedReport(String message) {
        StoryboardValidationReport report = new StoryboardValidationReport();
        report.setValidated(false);
        report.setPassed(true);
        report.setOutputTarget(outputTarget);
        report.setSceneCount(0);
        report.setInitialIssueCount(0);
        report.setInitialIssues(new ArrayList<>());
        report.setFixAttempted(false);
        report.setFixApplied(false);
        report.setResolvedIssueCount(0);
        report.setFinalIssueCount(0);
        report.setFinalIssues(new ArrayList<>());
        report.setMessage(message);
        return report;
    }

    private StoryboardValidationReport baseReport(Storyboard storyboard,
                                                  List<String> initialIssues) {
        StoryboardValidationReport report = new StoryboardValidationReport();
        report.setValidated(true);
        report.setPassed(initialIssues == null || initialIssues.isEmpty());
        report.setOutputTarget(outputTarget);
        report.setSceneCount(storyboard != null && storyboard.getScenes() != null
                ? storyboard.getScenes().size()
                : 0);
        report.setInitialIssueCount(initialIssues != null ? initialIssues.size() : 0);
        report.setInitialIssues(initialIssues != null ? new ArrayList<>(initialIssues) : new ArrayList<>());
        report.setFixAttempted(false);
        report.setFixApplied(false);
        report.setResolvedIssueCount(0);
        report.setFinalIssueCount(initialIssues != null ? initialIssues.size() : 0);
        report.setFinalIssues(initialIssues != null ? new ArrayList<>(initialIssues) : new ArrayList<>());
        return report;
    }

    private void finalizeReport(StoryboardValidationReport report,
                                boolean passed,
                                boolean fixAttempted,
                                boolean fixApplied,
                                List<String> finalIssues,
                                String message) {
        if (report == null) {
            return;
        }
        List<String> resolvedFinalIssues = finalIssues != null ? new ArrayList<>(finalIssues) : new ArrayList<>();
        report.setPassed(passed);
        report.setFixAttempted(fixAttempted);
        report.setFixApplied(fixApplied);
        report.setFinalIssueCount(resolvedFinalIssues.size());
        report.setFinalIssues(resolvedFinalIssues);
        report.setResolvedIssueCount(Math.max(report.getInitialIssueCount() - resolvedFinalIssues.size(), 0));
        report.setMessage(message);
    }

    private void appendValidationTraceEntry(Storyboard storyboard,
                                            String phase,
                                            int cleanupAttempt,
                                            boolean fixAttempted,
                                            boolean fixApplied,
                                            List<String> issues,
                                            int toolCalls,
                                            double executionTimeSeconds,
                                            String message) {
        if (storyboardValidationReport == null) {
            return;
        }
        List<String> resolvedIssues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
        StoryboardValidationTraceEntry entry = new StoryboardValidationTraceEntry();
        entry.setSequence(storyboardValidationReport.getEntries().size() + 1);
        entry.setPhase(phase);
        entry.setCleanupAttempt(cleanupAttempt);
        entry.setPassed(resolvedIssues.isEmpty());
        entry.setSceneCount(countScenes(storyboard));
        entry.setIssueCount(resolvedIssues.size());
        entry.setIssues(resolvedIssues);
        entry.setFixAttempted(fixAttempted);
        entry.setFixApplied(fixApplied);
        entry.setToolCalls(toolCalls);
        entry.setExecutionTimeSeconds(executionTimeSeconds);
        entry.setMessage(message);
        storyboardValidationReport.addEntry(entry);
    }

    private int countScenes(Storyboard storyboard) {
        return storyboard != null && storyboard.getScenes() != null
                ? storyboard.getScenes().size()
                : 0;
    }

    // ---- LLM fix pass ----

    private Narrative attemptLlmFix(Narrative narrative, List<String> issues) {
        try {
            if (aiClient == null) {
                log.warn("No AI client available for storyboard cleanup");
                return null;
            }
            String storyboardJson = JsonUtils.mapper().writeValueAsString(narrative.getStoryboard());
            NodeConversationContext conversationContext = new NodeConversationContext(Integer.MAX_VALUE);
            String systemPrompt = NarrativePrompts.buildRulesPrompt(outputTarget)
                    + "\n\nStoryboard validation repair rules:\n"
                    + "- Use dependency_objects as the authoritative dependency graph.\n"
                    + "- Rewrite storyboard colors as 6-digit hex strings (`#RRGGBB`) only; do not use named colors or 8-digit hex.\n"
                    + "- Keep opacity in separate `opacity`, `fill_opacity`, or `stroke_opacity` fields.\n"
                    + "- For text objects, ensure text color contrasts with the text box/background color at ratio >= 4.5, falling back to #000000 when no text background is present.\n"
                    + "- For non-text objects, ensure foreground colors contrast with #000000 at ratio >= 3.0.\n"
                    + "- If a derived object is out of bounds, adjust upstream dependency_objects, the whole constrained group, or the camera/layout; do not repair by directly moving that derived object when it would contradict dependency_relation or constraint_note.\n"
                    + "- Every dependency-driven object must define dependency_objects as object ids and dependency_relation as a concise construction relation; put hard geometric invariants in constraint_note.";
            conversationContext.setSystemMessage(systemPrompt);
            conversationContext.setFixedContextMessage(NarrativePrompts.buildFixedContextPrompt(
                    narrative.getTargetConcept(),
                    narrative.getTargetDescription(),
                    outputTarget,
                    buildDagChainSummary(narrative.getStoryboard())));

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Please clean up this storyboard so it is coherent, and ensure that all coordinate-based elements stay within bounds and do not visibly overlap.\n");
            userPrompt.append("Preserve the original narrative order, object identity, and teaching intent as much as possible; only adjust the layout and wording where necessary.\n");
            userPrompt.append("Replace every non-ASCII text token reported below with an ASCII equivalent across the full storyboard.\n");
            userPrompt.append("If you find issues, fix them directly. If there are no issues, still perform a full cleanup to make the storyboard more stable and readable.\n");
            if (issues != null && !issues.isEmpty()) {
                userPrompt.append("Static validation findings:\n");
                for (String issue : issues) {
                    if (issue.startsWith("Issue:")) {
                        userPrompt.append(issue).append("\n");
                    } else {
                        userPrompt.append("- ").append(issue).append("\n");
                    }
                }
                userPrompt.append("\n");
            }
            userPrompt.append("Current storyboard:\n```json\n").append(storyboardJson).append("\n```\n\n");
            userPrompt.append("Return the full corrected storyboard JSON with all scenes, object_registry, and metadata.");

            JsonNode fixedData = AiRequestUtils.requestJsonObjectAsync(
                            aiClient,
                            log,
                            "storyboard-fix",
                            conversationContext,
                            SystemPrompts.buildCurrentRequestSection(userPrompt.toString()),
                            ToolSchemas.STORYBOARD,
                            () -> toolCalls++)
                    .join();

            if (fixedData == null) {
                return null;
            }

            JsonNode storyboardNode = fixedData.has("storyboard")
                    ? fixedData.get("storyboard") : fixedData;
            if (storyboardNode == null || !storyboardNode.has("scenes")) {
                return null;
            }

            Storyboard fixedStoryboard = JsonUtils.mapper().treeToValue(storyboardNode, Storyboard.class);
            fixedStoryboard = StoryboardNormalizer.normalize(fixedStoryboard);

            return new Narrative(
                    narrative.getTargetConcept(),
                    narrative.getTargetDescription(),
                    fixedStoryboard
            );
        } catch (CompletionException e) {
            log.warn("LLM fix call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("LLM fix failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildDagChainSummary(Storyboard storyboard) {
        String dagSummary = TargetDescriptionBuilder.buildSolutionChain(knowledgeGraph, null);
        if (dagSummary != null && !dagSummary.isBlank()) {
            return dagSummary;
        }
        if (storyboard == null || storyboard.getScenes() == null || storyboard.getScenes().isEmpty()) {
            return "DAG summary chain: unavailable.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DAG summary chain:\n");
        int step = 1;
        for (StoryboardScene scene : storyboard.getScenes()) {
            if (scene == null) {
                continue;
            }
            sb.append(step).append(". ");
            String sceneId = scene.getSceneId();
            if (sceneId != null && !sceneId.isBlank()) {
                sb.append(sceneId.trim()).append(" - ");
            }
            String title = scene.getTitle();
            if (title != null && !title.isBlank()) {
                sb.append(title.trim());
            } else {
                sb.append("Untitled scene");
            }
            if (scene.getGoal() != null && !scene.getGoal().isBlank()) {
                sb.append(" | goal: ").append(scene.getGoal().trim());
            }
            if (scene.getLayoutGoal() != null && !scene.getLayoutGoal().isBlank()) {
                sb.append(" | layout: ").append(scene.getLayoutGoal().trim());
            }
            sb.append("\n");
            step++;
        }
        return sb.toString().trim();
    }

    private String buildStoryboardChainSummary(Storyboard storyboard) {
        if (storyboard == null || storyboard.getScenes() == null || storyboard.getScenes().isEmpty()) {
            return "DAG summary chain: no scenes available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DAG summary chain:\n");
        int step = 1;
        for (StoryboardScene scene : storyboard.getScenes()) {
            if (scene == null) {
                continue;
            }
            sb.append(step).append(". ");
            String sceneId = scene.getSceneId();
            if (sceneId != null && !sceneId.isBlank()) {
                sb.append(sceneId.trim()).append(" - ");
            }
            String title = scene.getTitle();
            if (title != null && !title.isBlank()) {
                sb.append(title.trim());
            } else {
                sb.append("Untitled scene");
            }
            if (scene.getGoal() != null && !scene.getGoal().isBlank()) {
                sb.append(" | goal: ").append(scene.getGoal().trim());
            }
            if (scene.getLayoutGoal() != null && !scene.getLayoutGoal().isBlank()) {
                sb.append(" | layout: ").append(scene.getLayoutGoal().trim());
            }
            sb.append("\n");
            step++;
        }
        return sb.toString().trim();
    }

    private static final class StoryboardLayoutElement {
        private final String objectId;
        private final StoryboardObject object;
        private final StoryboardLayoutBounds bounds;

        private StoryboardLayoutElement(String objectId,
                                        StoryboardObject object,
                                        StoryboardLayoutBounds bounds) {
            this.objectId = objectId;
            this.object = object;
            this.bounds = bounds;
        }
    }

    private static final class StoryboardLayoutBounds {
        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;

        private StoryboardLayoutBounds(double minX,
                                       double maxX,
                                       double minY,
                                       double maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        private double centerX() {
            return (minX + maxX) / 2.0;
        }

        private double centerY() {
            return (minY + maxY) / 2.0;
        }

        private double area() {
            return Math.max(maxX - minX, 0.0) * Math.max(maxY - minY, 0.0);
        }
    }

    private static final class AxisBounds {
        private final double min;
        private final double max;

        private AxisBounds(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class LayoutBucketRange {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;

        private LayoutBucketRange(int minX, int maxX, int minY, int maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static final class ColorReference {
        private final String propertyPath;
        private final String value;
        private final String styleRole;
        private final String styleType;

        private ColorReference(String propertyPath,
                               String value,
                               String styleRole,
                               String styleType) {
            this.propertyPath = propertyPath == null ? "" : propertyPath;
            this.value = value == null ? "" : value.trim();
            this.styleRole = styleRole == null ? "" : styleRole;
            this.styleType = styleType == null ? "" : styleType;
        }

        private boolean isTextLayer() {
            String combined = normalizedLayer();
            return combined.contains("text")
                    || combined.contains("math")
                    || combined.contains("formula")
                    || combined.contains("title")
                    || combined.contains("caption")
                    || combined.contains("label");
        }

        private boolean isExplicitBackground() {
            String path = propertyPath.toLowerCase(Locale.ROOT);
            String combined = normalizedLayer();
            return path.contains("background")
                    || path.contains("backstroke")
                    || combined.contains("background")
                    || combined.contains("box")
                    || combined.contains("card");
        }

        private boolean isTextBackgroundFill() {
            if (!isExplicitBackground()) {
                return false;
            }
            String path = propertyPath.toLowerCase(Locale.ROOT);
            return path.contains("background")
                    || path.contains("fill_color")
                    || path.equals("color")
                    || path.endsWith(".color");
        }

        private String normalizedLayer() {
            return (styleRole + " " + styleType).toLowerCase(Locale.ROOT);
        }
    }

}
