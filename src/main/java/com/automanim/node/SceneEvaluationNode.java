package com.automanim.node;

import com.automanim.config.WorkflowConfig;
import com.automanim.model.CodeFixRequest;
import com.automanim.model.CodeFixSource;
import com.automanim.model.CodeResult;
import com.automanim.model.RenderResult;
import com.automanim.model.SceneEvaluationResult;
import com.automanim.model.SceneEvaluationResult.Bounds;
import com.automanim.model.SceneEvaluationResult.ElementRef;
import com.automanim.model.SceneEvaluationResult.LayoutIssue;
import com.automanim.model.SceneEvaluationResult.Overflow;
import com.automanim.model.SceneEvaluationResult.SampleEvaluation;
import com.automanim.model.WorkflowActions;
import com.automanim.model.WorkflowKeys;
import com.automanim.service.FileOutputService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.the_pocket.PocketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 5: Scene evaluation - inspects sampled geometry output after render and
 * routes code fixes when layout issues are detected.
 */
public class SceneEvaluationNode extends PocketFlow.Node<SceneEvaluationNode.SceneEvaluationInput,
        SceneEvaluationResult, String> {

    private static final Logger log = LoggerFactory.getLogger(SceneEvaluationNode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final double OFFSCREEN_TOLERANCE = 0.03;
    private static final double MIN_OVERLAP_AREA = 0.015;
    private static final double MIN_OVERLAP_RATIO = 0.08;
    private static final int DEFAULT_MAX_FIX_ATTEMPTS = 2;
    private static final int MAX_FIX_REPORT_SAMPLES = 12;
    private static final int MAX_ISSUES_PER_SAMPLE_IN_FIX_REPORT = 6;

    public SceneEvaluationNode() {
        super(1, 0);
    }

    public static class SceneEvaluationInput {
        private final CodeResult codeResult;
        private final RenderResult renderResult;
        private final WorkflowConfig config;
        private final Path outputDir;
        private final SceneEvaluationRetryState retryState;

        public SceneEvaluationInput(CodeResult codeResult,
                                    RenderResult renderResult,
                                    WorkflowConfig config,
                                    Path outputDir,
                                    SceneEvaluationRetryState retryState) {
            this.codeResult = codeResult;
            this.renderResult = renderResult;
            this.config = config;
            this.outputDir = outputDir;
            this.retryState = retryState;
        }

        public CodeResult codeResult() { return codeResult; }
        public RenderResult renderResult() { return renderResult; }
        public WorkflowConfig config() { return config; }
        public Path outputDir() { return outputDir; }
        public SceneEvaluationRetryState retryState() { return retryState; }
    }

    @Override
    public SceneEvaluationInput prep(Map<String, Object> ctx) {
        SceneEvaluationRetryState retryState =
                (SceneEvaluationRetryState) ctx.get(WorkflowKeys.SCENE_EVALUATION_RETRY_STATE);
        if (retryState == null) {
            retryState = new SceneEvaluationRetryState();
            ctx.put(WorkflowKeys.SCENE_EVALUATION_RETRY_STATE, retryState);
        }

        CodeResult codeResult = (CodeResult) ctx.get(WorkflowKeys.CODE_RESULT);
        RenderResult renderResult = (RenderResult) ctx.get(WorkflowKeys.RENDER_RESULT);
        WorkflowConfig config = (WorkflowConfig) ctx.get(WorkflowKeys.CONFIG);
        Path outputDir = (Path) ctx.get(WorkflowKeys.OUTPUT_DIR);
        return new SceneEvaluationInput(codeResult, renderResult, config, outputDir, retryState);
    }

    @Override
    public SceneEvaluationResult exec(SceneEvaluationInput input) {
        Instant start = Instant.now();
        SceneEvaluationRetryState retryState = input.retryState();
        retryState.requestFix = false;
        retryState.pendingIssueSummary = null;
        retryState.pendingSceneEvaluationJson = null;

        SceneEvaluationResult result = new SceneEvaluationResult();
        result.setToolCalls(0);

        CodeResult codeResult = input.codeResult();
        RenderResult renderResult = input.renderResult();
        result.setSceneName(codeResult != null ? codeResult.getSceneName() : null);
        result.setRenderSuccess(renderResult != null && renderResult.isSuccess());
        result.setGeometryPath(renderResult != null ? renderResult.getGeometryPath() : null);

        log.info("=== Stage 5: Scene Evaluation ===");

        if (renderResult == null) {
            result.setEvaluated(false);
            result.setApproved(true);
            result.setGateReason("Scene evaluation skipped: render result unavailable");
            finalizeResult(result, retryState, start, true);
            return result;
        }

        if (!renderResult.isSuccess()) {
            result.setEvaluated(false);
            result.setApproved(true);
            result.setGateReason("Scene evaluation skipped: render was not successful");
            finalizeResult(result, retryState, start, true);
            return result;
        }

        String geometryPath = renderResult.getGeometryPath();
        if (geometryPath == null || geometryPath.isBlank()) {
            result.setEvaluated(false);
            result.setApproved(true);
            result.setGateReason("Scene evaluation skipped: geometry report unavailable");
            finalizeResult(result, retryState, start, true);
            return result;
        }

        Path geometryFile = Path.of(geometryPath);
        if (!Files.exists(geometryFile)) {
            result.setEvaluated(false);
            result.setApproved(true);
            result.setGateReason("Scene evaluation skipped: geometry report not found");
            finalizeResult(result, retryState, start, true);
            return result;
        }

        try {
            JsonNode root = MAPPER.readTree(geometryFile.toFile());
            result.setSceneName(readText(root, "scene_name", result.getSceneName()));
            double[] frameMin = readPoint(root.path("frame_bounds").path("min"), new double[] {-7.111111, -4.0, 0.0});
            double[] frameMax = readPoint(root.path("frame_bounds").path("max"), new double[] {7.111111, 4.0, 0.0});

            List<SampleEvaluation> sampleEvaluations = new ArrayList<>();
            int overlapIssues = 0;
            int offscreenIssues = 0;
            int totalIssues = 0;
            int issueSamples = 0;

            JsonNode samplesNode = root.path("samples");
            if (samplesNode.isArray()) {
                for (JsonNode sampleNode : samplesNode) {
                    SampleEvaluation sampleEvaluation = evaluateSample(sampleNode, frameMin, frameMax);
                    sampleEvaluations.add(sampleEvaluation);
                    if (sampleEvaluation.isHasIssues()) {
                        issueSamples++;
                    }
                    overlapIssues += sampleEvaluation.getOverlapIssueCount();
                    offscreenIssues += sampleEvaluation.getOffscreenIssueCount();
                    totalIssues += sampleEvaluation.getIssueCount();
                }
            }

            result.setSamples(sampleEvaluations);
            result.setSampleCount(sampleEvaluations.size());
            result.setIssueSampleCount(issueSamples);
            result.setOverlapIssueCount(overlapIssues);
            result.setOffscreenIssueCount(offscreenIssues);
            result.setTotalIssueCount(totalIssues);
            result.setEvaluated(true);
            result.setApproved(totalIssues == 0);

            int attemptsSoFar = retryState.attempts;
            if (result.isApproved()) {
                result.setGateReason("Scene evaluation passed: no overlap or offscreen issues detected");
                result.setRevisionTriggered(attemptsSoFar > 0);
                result.setRevisionAttempts(attemptsSoFar);
                finalizeResult(result, retryState, start, true);
                return result;
            }

            int maxFixAttempts = resolveMaxFixAttempts(input.config());
            String issueSummary = buildIssueSummary(result);
            retryState.fixHistory.add(summarizeFixHistory(issueSummary));

            if (attemptsSoFar < maxFixAttempts) {
                retryState.attempts = attemptsSoFar + 1;
                retryState.requestFix = true;
                retryState.pendingIssueSummary = issueSummary;
                retryState.pendingSceneEvaluationJson = buildFixReportJson(result);
                result.setRevisionTriggered(true);
                result.setRevisionAttempts(retryState.attempts);
                result.setGateReason(String.format(
                        "Detected %d layout issues across %d samples; routing to code fix (attempt %d/%d)",
                        totalIssues,
                        issueSamples,
                        retryState.attempts,
                        maxFixAttempts));
                finalizeResult(result, retryState, start, false);
                return result;
            }

            result.setRevisionTriggered(attemptsSoFar > 0);
            result.setRevisionAttempts(attemptsSoFar);
            result.setGateReason(String.format(
                    "Layout issues remain after %d fix attempts; stopping retries", attemptsSoFar));
            finalizeResult(result, retryState, start, true);
            return result;

        } catch (IOException e) {
            log.warn("Scene evaluation could not read geometry report {}: {}", geometryPath, e.getMessage());
            result.setEvaluated(false);
            result.setApproved(true);
            result.setGateReason("Scene evaluation skipped: failed to parse geometry report");
            finalizeResult(result, retryState, start, true);
            return result;
        }
    }

    @Override
    public String post(Map<String, Object> ctx, SceneEvaluationInput input, SceneEvaluationResult result) {
        ctx.put(WorkflowKeys.SCENE_EVALUATION_RESULT, result);

        if (input.outputDir() != null) {
            FileOutputService.saveSceneEvaluation(input.outputDir(), result);
        }

        if (input.retryState().requestFix) {
            ctx.put(WorkflowKeys.CODE_FIX_REQUEST, buildSceneEvaluationFixRequest(input, result));
            return WorkflowActions.FIX_CODE;
        }

        return null;
    }

    private CodeFixRequest buildSceneEvaluationFixRequest(SceneEvaluationInput input,
                                                          SceneEvaluationResult result) {
        CodeResult codeResult = input.codeResult();
        SceneEvaluationRetryState retryState = input.retryState();

        CodeFixRequest request = new CodeFixRequest();
        request.setSource(CodeFixSource.SCENE_LAYOUT_EVALUATION);
        request.setReturnAction(WorkflowActions.RETRY_RENDER);
        request.setCode(codeResult.getManimCode());
        request.setErrorReason(retryState.pendingIssueSummary != null
                ? retryState.pendingIssueSummary
                : buildIssueSummary(result));
        request.setSceneEvaluationJson(retryState.pendingSceneEvaluationJson != null
                ? retryState.pendingSceneEvaluationJson
                : buildFallbackFixJson(result));
        request.setTargetConcept(codeResult.getTargetConcept());
        request.setTargetDescription(codeResult.getTargetDescription());
        request.setSceneName(codeResult.getSceneName());
        request.setExpectedSceneName("MainScene");
        request.setFixHistory(new ArrayList<>(retryState.fixHistory));
        return request;
    }

    private SampleEvaluation evaluateSample(JsonNode sampleNode, double[] frameMin, double[] frameMax) {
        SampleEvaluation sample = new SampleEvaluation();
        sample.setSampleId(readText(sampleNode, "sample_id", ""));
        sample.setPlayIndex(sampleNode.hasNonNull("play_index") ? sampleNode.get("play_index").asInt() : null);
        sample.setSampleRole(readText(sampleNode, "sample_role", ""));
        sample.setTrigger(readText(sampleNode, "trigger", ""));
        sample.setSceneMethod(readText(sampleNode, "scene_method", ""));
        sample.setSourceCode(readText(sampleNode, "source_code", ""));

        List<ElementGeometry> elements = readElements(sampleNode.path("elements"), sample.getSampleRole());
        sample.setElementCount(elements.size());

        List<LayoutIssue> issues = new ArrayList<>();
        int offscreenCount = 0;
        int overlapCount = 0;

        for (ElementGeometry element : elements) {
            OverflowMetrics overflow = frameOverflow(element, frameMin, frameMax);
            if (overflow == null) {
                continue;
            }
            LayoutIssue issue = new LayoutIssue();
            issue.setType("offscreen");
            issue.setMessage(String.format(
                    "Element %s extends outside the frame bounds",
                    element.displayName()));
            issue.setPrimaryElement(toElementRef(element, sample.getSampleRole()));
            issue.setOverflow(toOverflow(overflow));
            issues.add(issue);
            offscreenCount++;
        }

        for (int i = 0; i < elements.size(); i++) {
            ElementGeometry left = elements.get(i);
            for (int j = i + 1; j < elements.size(); j++) {
                ElementGeometry right = elements.get(j);
                if (!shouldCheckOverlap(left, right)) {
                    continue;
                }
                OverlapMetrics overlap = overlap(left, right);
                if (overlap == null) {
                    continue;
                }
                LayoutIssue issue = new LayoutIssue();
                issue.setType("overlap");
                issue.setMessage(String.format(
                        "Elements %s and %s overlap in sampled frame",
                        left.displayName(),
                        right.displayName()));
                issue.setPrimaryElement(toElementRef(left, sample.getSampleRole()));
                issue.setSecondaryElement(toElementRef(right, sample.getSampleRole()));
                issue.setIntersectionArea(overlap.area);
                issue.setIntersectionBounds(toBounds(overlap.min, overlap.max));
                issues.add(issue);
                overlapCount++;
            }
        }

        sample.setIssues(issues);
        sample.setOffscreenIssueCount(offscreenCount);
        sample.setOverlapIssueCount(overlapCount);
        sample.setIssueCount(issues.size());
        sample.setHasIssues(!issues.isEmpty());
        return sample;
    }

    private List<ElementGeometry> readElements(JsonNode elementsNode, String sampleRole) {
        List<ElementGeometry> elements = new ArrayList<>();
        if (!elementsNode.isArray()) {
            return elements;
        }

        for (JsonNode elementNode : elementsNode) {
            double[] min = readPoint(elementNode.path("bounds").path("min"), null);
            double[] max = readPoint(elementNode.path("bounds").path("max"), null);
            if (min == null || max == null) {
                continue;
            }

            ElementGeometry element = new ElementGeometry();
            element.stableId = readNullableText(elementNode, "stable_id");
            element.semanticName = firstNonBlank(
                    readNullableText(elementNode, "semantic_name"),
                    readNullableText(elementNode, "name"));
            element.className = readText(elementNode, "class_name", "Unknown");
            element.semanticClass = readNullableText(elementNode, "semantic_class");
            element.displayText = readNullableText(elementNode, "display_text");
            element.topLevelStableId = readNullableText(elementNode, "top_level_stable_id");
            element.visible = elementNode.path("visible").asBoolean(true);
            element.sampleRole = sampleRole;
            element.min = min;
            element.max = max;
            elements.add(element);
        }

        return elements;
    }

    private OverflowMetrics frameOverflow(ElementGeometry element, double[] frameMin, double[] frameMax) {
        double left = Math.max(frameMin[0] - element.min[0], 0.0);
        double right = Math.max(element.max[0] - frameMax[0], 0.0);
        double bottom = Math.max(frameMin[1] - element.min[1], 0.0);
        double top = Math.max(element.max[1] - frameMax[1], 0.0);
        if (Math.max(Math.max(left, right), Math.max(bottom, top)) <= OFFSCREEN_TOLERANCE) {
            return null;
        }
        return new OverflowMetrics(left, right, top, bottom);
    }

    private boolean shouldCheckOverlap(ElementGeometry left, ElementGeometry right) {
        if (left.stableId != null && left.stableId.equals(right.stableId)) {
            return false;
        }
        if (left.topLevelStableId != null
                && right.topLevelStableId != null
                && left.topLevelStableId.equals(right.topLevelStableId)) {
            return false;
        }
        return isTextual(left) || isTextual(right);
    }

    private boolean isTextual(ElementGeometry element) {
        if (element == null) {
            return false;
        }
        String semanticClass = element.semanticClass != null ? element.semanticClass : "";
        if ("text".equalsIgnoreCase(semanticClass) || "formula".equalsIgnoreCase(semanticClass)) {
            return true;
        }
        String className = element.className != null ? element.className : "";
        return className.equals("Text")
                || className.equals("MathTex")
                || className.equals("Tex")
                || className.equals("DecimalNumber")
                || className.equals("Integer")
                || (element.displayText != null && !element.displayText.isBlank());
    }

    private OverlapMetrics overlap(ElementGeometry left, ElementGeometry right) {
        double overlapMinX = Math.max(left.min[0], right.min[0]);
        double overlapMaxX = Math.min(left.max[0], right.max[0]);
        double overlapMinY = Math.max(left.min[1], right.min[1]);
        double overlapMaxY = Math.min(left.max[1], right.max[1]);
        double overlapWidth = overlapMaxX - overlapMinX;
        double overlapHeight = overlapMaxY - overlapMinY;
        if (overlapWidth <= 1e-9 || overlapHeight <= 1e-9) {
            return null;
        }

        double area = overlapWidth * overlapHeight;
        double leftArea = Math.max((left.max[0] - left.min[0]) * (left.max[1] - left.min[1]), 1e-9);
        double rightArea = Math.max((right.max[0] - right.min[0]) * (right.max[1] - right.min[1]), 1e-9);
        double minAreaRatio = area / Math.min(leftArea, rightArea);
        if (area < MIN_OVERLAP_AREA || minAreaRatio < MIN_OVERLAP_RATIO) {
            return null;
        }

        return new OverlapMetrics(
                round(area),
                new double[] {round(overlapMinX), round(overlapMinY), 0.0},
                new double[] {round(overlapMaxX), round(overlapMaxY), 0.0}
        );
    }

    private ElementRef toElementRef(ElementGeometry element, String sampleRole) {
        ElementRef ref = new ElementRef();
        ref.setStableId(element.stableId);
        ref.setSemanticName(element.displayName());
        ref.setClassName(element.className);
        ref.setSampleRole(sampleRole);
        ref.setBounds(toBounds(element.min, element.max));
        return ref;
    }

    private Overflow toOverflow(OverflowMetrics metrics) {
        Overflow overflow = new Overflow();
        overflow.setLeft(round(metrics.left));
        overflow.setRight(round(metrics.right));
        overflow.setTop(round(metrics.top));
        overflow.setBottom(round(metrics.bottom));
        return overflow;
    }

    private Bounds toBounds(double[] min, double[] max) {
        Bounds bounds = new Bounds();
        bounds.setMin(min);
        bounds.setMax(max);
        return bounds;
    }

    private int resolveMaxFixAttempts(WorkflowConfig config) {
        if (config == null) {
            return DEFAULT_MAX_FIX_ATTEMPTS;
        }
        return Math.max(config.getSceneEvaluationMaxRetries(), 0);
    }

    private void finalizeResult(SceneEvaluationResult result,
                                SceneEvaluationRetryState retryState,
                                Instant start,
                                boolean resetState) {
        result.setExecutionTimeSeconds(toSeconds(start));
        if (resetState) {
            retryState.reset();
        }
    }

    private String buildIssueSummary(SceneEvaluationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Scene evaluation found %d issues across %d/%d samples (%d overlap, %d offscreen).",
                result.getTotalIssueCount(),
                result.getIssueSampleCount(),
                result.getSampleCount(),
                result.getOverlapIssueCount(),
                result.getOffscreenIssueCount()));

        int listedSamples = 0;
        for (SampleEvaluation sample : result.getSamples()) {
            if (!sample.isHasIssues()) {
                continue;
            }
            listedSamples++;
            sb.append("\n- Sample ").append(sample.getSampleId())
                    .append(" [").append(sample.getSampleRole()).append("]");
            if (sample.getSceneMethod() != null && !sample.getSceneMethod().isBlank()) {
                sb.append(" in ").append(sample.getSceneMethod());
            }
            if (sample.getSourceCode() != null && !sample.getSourceCode().isBlank()) {
                sb.append(": ").append(sample.getSourceCode());
            }
            for (int i = 0; i < Math.min(sample.getIssues().size(), 3); i++) {
                sb.append("\n  * ").append(sample.getIssues().get(i).getMessage());
            }
            if (listedSamples >= MAX_FIX_REPORT_SAMPLES) {
                break;
            }
        }
        return sb.toString();
    }

    private String summarizeFixHistory(String issueSummary) {
        if (issueSummary == null || issueSummary.isBlank()) {
            return "";
        }
        String normalized = issueSummary.replaceAll("\\s+", " ").trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private String buildFixReportJson(SceneEvaluationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene_name", result.getSceneName());
        payload.put("geometry_path", result.getGeometryPath());
        payload.put("sample_count", result.getSampleCount());
        payload.put("issue_sample_count", result.getIssueSampleCount());
        payload.put("total_issue_count", result.getTotalIssueCount());
        payload.put("overlap_issue_count", result.getOverlapIssueCount());
        payload.put("offscreen_issue_count", result.getOffscreenIssueCount());

        List<Map<String, Object>> samples = new ArrayList<>();
        int addedSamples = 0;
        for (SampleEvaluation sample : result.getSamples()) {
            if (!sample.isHasIssues()) {
                continue;
            }
            Map<String, Object> sampleMap = new LinkedHashMap<>();
            sampleMap.put("sample_id", sample.getSampleId());
            sampleMap.put("play_index", sample.getPlayIndex());
            sampleMap.put("sample_role", sample.getSampleRole());
            sampleMap.put("trigger", sample.getTrigger());
            sampleMap.put("scene_method", sample.getSceneMethod());
            sampleMap.put("source_code", sample.getSourceCode());
            sampleMap.put("issue_count", sample.getIssueCount());

            List<Map<String, Object>> issues = new ArrayList<>();
            for (int i = 0; i < Math.min(sample.getIssues().size(), MAX_ISSUES_PER_SAMPLE_IN_FIX_REPORT); i++) {
                LayoutIssue issue = sample.getIssues().get(i);
                Map<String, Object> issueMap = new LinkedHashMap<>();
                issueMap.put("type", issue.getType());
                issueMap.put("message", issue.getMessage());
                issueMap.put("primary_element", elementRefMap(issue.getPrimaryElement()));
                if (issue.getSecondaryElement() != null) {
                    issueMap.put("secondary_element", elementRefMap(issue.getSecondaryElement()));
                }
                if (issue.getOverflow() != null) {
                    issueMap.put("overflow", overflowMap(issue.getOverflow()));
                }
                if (issue.getIntersectionArea() != null) {
                    issueMap.put("intersection_area", issue.getIntersectionArea());
                }
                if (issue.getIntersectionBounds() != null) {
                    issueMap.put("intersection_bounds", boundsMap(issue.getIntersectionBounds()));
                }
                issues.add(issueMap);
            }
            sampleMap.put("issues", issues);
            samples.add(sampleMap);
            addedSamples++;
            if (addedSamples >= MAX_FIX_REPORT_SAMPLES) {
                break;
            }
        }
        payload.put("issue_samples", samples);
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return buildFallbackFixJson(result);
        }
    }

    private String buildFallbackFixJson(SceneEvaluationResult result) {
        return String.format(
                "{\"scene_name\":\"%s\",\"issue_sample_count\":%d,\"total_issue_count\":%d}",
                result.getSceneName() != null ? result.getSceneName() : "MainScene",
                result.getIssueSampleCount(),
                result.getTotalIssueCount());
    }

    private Map<String, Object> elementRefMap(ElementRef ref) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (ref == null) {
            return map;
        }
        map.put("stable_id", ref.getStableId());
        map.put("semantic_name", ref.getSemanticName());
        map.put("class_name", ref.getClassName());
        map.put("sample_role", ref.getSampleRole());
        map.put("bounds", boundsMap(ref.getBounds()));
        return map;
    }

    private Map<String, Object> overflowMap(Overflow overflow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("left", overflow.getLeft());
        map.put("right", overflow.getRight());
        map.put("top", overflow.getTop());
        map.put("bottom", overflow.getBottom());
        return map;
    }

    private Map<String, Object> boundsMap(Bounds bounds) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (bounds == null) {
            return map;
        }
        map.put("min", bounds.getMin());
        map.put("max", bounds.getMax());
        return map;
    }

    private String readText(JsonNode node, String fieldName, String fallback) {
        String value = readNullableText(node, fieldName);
        return value != null ? value : fallback;
    }

    private String readNullableText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private double[] readPoint(JsonNode node, double[] fallback) {
        if (node == null || !node.isArray() || node.size() < 2) {
            return fallback;
        }
        double x = round(node.get(0).asDouble());
        double y = round(node.get(1).asDouble());
        double z = node.size() > 2 ? round(node.get(2).asDouble()) : 0.0;
        return new double[] {x, y, z};
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private double toSeconds(Instant start) {
        return Duration.between(start, Instant.now()).toMillis() / 1000.0;
    }

    static final class SceneEvaluationRetryState {
        private int attempts;
        private boolean requestFix;
        private final List<String> fixHistory = new ArrayList<>();
        private String pendingIssueSummary;
        private String pendingSceneEvaluationJson;

        void reset() {
            attempts = 0;
            requestFix = false;
            fixHistory.clear();
            pendingIssueSummary = null;
            pendingSceneEvaluationJson = null;
        }
    }

    private static final class ElementGeometry {
        private String stableId;
        private String semanticName;
        private String className;
        private String semanticClass;
        private String displayText;
        private String topLevelStableId;
        private String sampleRole;
        private boolean visible;
        private double[] min;
        private double[] max;

        private String displayName() {
            return firstNonBlank(semanticName, displayText, className, stableId, "element");
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
    }

    private static final class OverflowMetrics {
        private final double left;
        private final double right;
        private final double top;
        private final double bottom;

        private OverflowMetrics(double left, double right, double top, double bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    private static final class OverlapMetrics {
        private final double area;
        private final double[] min;
        private final double[] max;

        private OverlapMetrics(double area, double[] min, double[] max) {
            this.area = area;
            this.min = min;
            this.max = max;
        }
    }
}
