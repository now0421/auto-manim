package com.mathvision.node;

import com.mathvision.config.WorkflowConfig;
import com.mathvision.model.Narrative;
import com.mathvision.model.Narrative.Storyboard;
import com.mathvision.model.Narrative.StoryboardObject;
import com.mathvision.model.Narrative.StoryboardPlacement;
import com.mathvision.model.Narrative.StoryboardPlacementAxis;
import com.mathvision.model.Narrative.StoryboardScene;
import com.mathvision.model.WorkflowKeys;
import com.mathvision.service.AiClient;
import com.mathvision.util.JsonUtils;
import com.mathvision.util.NodeConversationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryboardValidationNodeTest {

    @TempDir
    Path tempDir;

    @Test
    void passesWhenObjectsStayWithinFrameAndDoNotOverlap() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(
                        registryObject("title", "text", "Main title", null),
                        registryObject("diagram", "region", "Main diagram", null)
                ),
                List.of(
                        scenePatch("title", boxPlacement("screen", -1.5, 1.5, 2.1, 2.8)),
                        scenePatch("diagram", boxPlacement("world", -1.2, 1.2, -1.0, 1.0))
                ));

        List<String> issues = node.validate(storyboard);

        assertTrue(issues.isEmpty(), () -> String.join("\n", issues));
    }

    @Test
    void reportsOffscreenLayoutWhenBoundsExceedFrame() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(registryObject("title", "text", "Main title", null)),
                List.of(scenePatch("title", boxPlacement("screen", 6.9, 7.5, 2.0, 2.7))));

        List<String> issues = node.validate(storyboard);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("title"));
        assertTrue(issues.get(0).contains("extends outside the frame bounds"));
    }

    @Test
    void reportsTextOverlapUsingStoryboardBounds() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(
                        registryObject("headline", "text", "Headline", null),
                        registryObject("subhead", "text", "Subhead", null)
                ),
                List.of(
                        scenePatch("headline", boxPlacement("screen", -1.4, 1.2, 1.8, 2.6)),
                        scenePatch("subhead", boxPlacement("screen", -0.9, 1.6, 2.1, 2.9))
                ));

        List<String> issues = node.validate(storyboard);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("headline"));
        assertTrue(issues.get(0).contains("subhead"));
        assertTrue(issues.get(0).contains("overlap"));
    }

    @Test
    void reportsNonTextOverlapWhenBoundsCollide() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(
                        registryObject("left_box", "region", "Left region", null),
                        registryObject("right_box", "region", "Right region", null)
                ),
                List.of(
                        scenePatch("left_box", boxPlacement("world", -1.0, 1.0, -1.0, 1.0)),
                        scenePatch("right_box", boxPlacement("world", -0.5, 1.5, -0.5, 1.5))
                ));

        List<String> issues = node.validate(storyboard);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("left_box"));
        assertTrue(issues.get(0).contains("right_box"));
        assertTrue(issues.get(0).contains("overlap"));
    }

    @Test
    void ignoresAttachedLabelOverlapWithItsAnchorObject() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(
                        registryObject("point_a", "point", "Point A", null),
                        registryObject("point_a_label", "label", "A", "point_a")
                ),
                List.of(
                        scenePatch("point_a", boxPlacement("world", -0.1, 0.1, -0.1, 0.1)),
                        scenePatch("point_a_label", boxPlacement("anchor", -0.15, 0.15, -0.05, 0.15))
                ));

        List<String> issues = node.validate(storyboard);

        assertTrue(issues.isEmpty(), () -> String.join("\n", issues));
    }

    @Test
    void ignoresAttachedLabelOverlapByPrefixStyleNamingWithoutAnchorId() {
        // Covers the "label_a" / "point_a" prefix pattern where no anchorId is set —
        // semanticStem must strip type prefixes from both sides so the stems match.
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(
                        registryObject("point_a", "point", "Point A", null),
                        registryObject("label_a", "label", "A", null)  // no anchorId, relies on stem matching
                ),
                List.of(
                        scenePatch("point_a", boxPlacement("world", -0.1, 0.1, -0.1, 0.1)),
                        scenePatch("label_a", boxPlacement("world", -0.15, 0.15, -0.05, 0.15))
                ));

        List<String> issues = node.validate(storyboard);

        assertTrue(issues.isEmpty(), () -> String.join("\n", issues));
    }

    @Test
    void reportsOffscreenChecksForGeoGebraStoryboardValidation() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_GEOGEBRA);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(registryObject("title", "text", "Main title", null)),
                List.of(scenePatch("title", boxPlacement("screen", 6.9, 7.5, 2.0, 2.7))));

        List<String> issues = node.validate(storyboard);

        assertEquals(1, issues.size(), () -> String.join("\n", issues));
        assertTrue(issues.get(0).contains("title"));
        assertTrue(issues.get(0).contains("extends outside the frame bounds"));
    }

    @Test
    void reportsDependencyDrivenObjectWithoutDependencyObjects() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        StoryboardObject reflectedPoint = registryObject("Bprime", "point", "Reflected point", null);
        reflectedPoint.setBehavior(StoryboardObject.BEHAVIOR_DERIVED);
        reflectedPoint.setDependencyRelation("reflection_across_line");
        reflectedPoint.setConstraintNote("mirror symmetric across river");
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(reflectedPoint),
                List.of(scenePatch("Bprime", boxPlacement("world", 2.9, 3.1, -3.6, -3.4))));

        List<String> issues = node.validate(storyboard);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("dependency_objects")),
                () -> String.join("\n", issues));
    }

    @Test
    void formatsOffscreenDependencyChainWithPlacementRelationAndConstraint() {
        StoryboardValidationNode node = prepareNode(WorkflowConfig.OUTPUT_TARGET_MANIM);
        StoryboardObject pointB = registryObject("B", "point", "Point B", null);
        StoryboardObject river = registryObject("river", "line", "River", null);
        StoryboardObject reflectedPoint = registryObject("Bprime", "point", "Reflected point", null);
        reflectedPoint.setBehavior(StoryboardObject.BEHAVIOR_DERIVED);
        reflectedPoint.setDependencyObjects(List.of("B", "river"));
        reflectedPoint.setDependencyRelation("reflection_across_line");
        reflectedPoint.setConstraintNote("mirror symmetric across river");
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(pointB, river, reflectedPoint),
                List.of(
                        scenePatch("B", boxPlacement("world", 3.0, 3.0, 1.5, 1.5)),
                        scenePatch("river", yOnlyPlacement("world", -1.5)),
                        scenePatch("Bprime", boxPlacement("world", 3.0, 3.0, -4.5, -4.5))
                ));

        List<String> issues = node.validate(storyboard);

        assertEquals(1, issues.size(), () -> String.join("\n", issues));
        String issue = issues.get(0);
        assertTrue(issue.startsWith("Issue: scene 1 (scene_1): object 'Bprime' extends outside the frame bounds"));
        assertTrue(issue.contains("\nDependency chain:\n"));
        assertTrue(issue.contains("- Bprime depends on [B, river]"));
        assertTrue(issue.contains("- B: world placement x=3.0, y=1.5"));
        assertTrue(issue.contains("- river: world placement y=-1.5"));
        assertTrue(issue.contains("- relation: reflection_across_line"));
        assertTrue(issue.contains("- constraint: mirror symmetric across river"));
        assertFalse(issue.contains("repair_rule"));
    }

    @Test
    void postWritesStoryboardValidationArtifactWithIssues() throws IOException {
        StoryboardValidationNode node = new StoryboardValidationNode();
        WorkflowConfig config = new WorkflowConfig();
        config.setOutputTarget(WorkflowConfig.OUTPUT_TARGET_MANIM);

        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(registryObject("title", "text", "Main title", null)),
                List.of(scenePatch("title", boxPlacement("screen", 6.9, 7.5, 2.0, 2.7))));
        Narrative narrative = new Narrative("Demo concept", "Demo description", storyboard);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.CONFIG, config);
        ctx.put(WorkflowKeys.NARRATIVE, narrative);
        ctx.put(WorkflowKeys.OUTPUT_DIR, tempDir);

        Narrative prepNarrative = node.prep(ctx);
        Narrative resultNarrative = node.exec(prepNarrative);
        node.post(ctx, prepNarrative, resultNarrative);

        String validatedStoryboardJson = Files.readString(tempDir.resolve("3_storyboard_validated.json"));
        String reportJson = Files.readString(tempDir.resolve("3_storyboard_validation.json"));
        assertFalse(Files.exists(tempDir.resolve("3_narrative.json")));
        assertTrue(validatedStoryboardJson.contains("\"scenes\""));
        assertTrue(validatedStoryboardJson.contains("Layout validation"));
        assertTrue(reportJson.contains("\"initial_issue_count\""));
        assertTrue(reportJson.contains("\"initial_issues\""));
        assertTrue(reportJson.contains("\"total_validation_events\""));
        assertTrue(reportJson.contains("\"entries\""));
        assertTrue(reportJson.contains("\"phase\" : \"initial_validation\""));
        assertTrue(reportJson.contains("extends outside the frame bounds"));
    }

    @Test
    void stopsValidationFixLoopAfterThreeUnsuccessfulPasses() throws IOException {
        StoryboardValidationNode node = new StoryboardValidationNode();
        WorkflowConfig config = new WorkflowConfig();
        config.setOutputTarget(WorkflowConfig.OUTPUT_TARGET_MANIM);
        Storyboard storyboard = buildSingleSceneStoryboard(
                List.of(registryObject("title", "text", "Main title", null)),
                List.of(scenePatch("title", boxPlacement("screen", 6.9, 7.5, 2.0, 2.7))));
        RepeatingStoryboardAiClient aiClient = new RepeatingStoryboardAiClient(storyboard);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.CONFIG, config);
        ctx.put(WorkflowKeys.NARRATIVE, new Narrative("Demo concept", "Demo description", storyboard));
        ctx.put(WorkflowKeys.AI_CLIENT, aiClient);
        ctx.put(WorkflowKeys.OUTPUT_DIR, tempDir);

        Narrative prepNarrative = node.prep(ctx);
        Narrative resultNarrative = node.exec(prepNarrative);
        node.post(ctx, prepNarrative, resultNarrative);

        assertEquals(3, aiClient.toolCalls.get());
        assertEquals(1, node.validate(resultNarrative.getStoryboard()).size());

        JsonNode report = JsonUtils.mapper().readTree(tempDir.resolve("3_storyboard_validation.json").toFile());
        assertEquals(4, report.get("total_validation_events").asInt());
        assertEquals(4, report.get("entries").size());
        assertEquals("initial_validation", report.get("entries").get(0).get("phase").asText());
        assertEquals("post_cleanup_validation", report.get("entries").get(3).get("phase").asText());
        assertEquals(3, report.get("entries").get(3).get("cleanup_attempt").asInt());
    }

    private StoryboardValidationNode prepareNode(String outputTarget) {
        StoryboardValidationNode node = new StoryboardValidationNode();
        WorkflowConfig config = new WorkflowConfig();
        config.setOutputTarget(outputTarget);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.CONFIG, config);
        node.prep(ctx);
        return node;
    }

    private Storyboard buildSingleSceneStoryboard(List<StoryboardObject> registryObjects,
                                                  List<StoryboardObject> enteringObjects) {
        StoryboardScene scene = new StoryboardScene();
        scene.setSceneId("scene_1");
        scene.setTitle("Layout validation");
        scene.setGoal("Validate static storyboard geometry.");
        scene.setNarration("Validate positions.");
        scene.setLayoutGoal("Keep everything readable.");
        scene.setSafeAreaPlan("Stay within the frame.");
        scene.setScreenOverlayPlan("No extra overlay.");
        scene.setEnteringObjects(new ArrayList<>(enteringObjects));
        scene.setPersistentObjects(new ArrayList<>());
        scene.setExitingObjects(new ArrayList<>());

        Storyboard storyboard = new Storyboard();
        storyboard.setContinuityPlan("Keep object ids stable.");
        storyboard.setObjectRegistry(new ArrayList<>(registryObjects));
        storyboard.setScenes(List.of(scene));
        return storyboard;
    }

    private StoryboardObject registryObject(String id,
                                            String kind,
                                            String content,
                                            String anchorId) {
        StoryboardObject object = new StoryboardObject();
        object.setId(id);
        object.setKind(kind);
        object.setContent(content);
        object.setAnchorId(anchorId);
        return object;
    }

    private StoryboardObject scenePatch(String id, StoryboardPlacement placement) {
        StoryboardObject object = new StoryboardObject();
        object.setId(id);
        object.setPlacement(placement);
        return object;
    }

    private StoryboardPlacement boxPlacement(String coordinateSpace,
                                             double minX,
                                             double maxX,
                                             double minY,
                                             double maxY) {
        StoryboardPlacement placement = new StoryboardPlacement();
        placement.setCoordinateSpace(coordinateSpace);
        placement.setX(axis(minX, maxX));
        placement.setY(axis(minY, maxY));
        return placement;
    }

    private StoryboardPlacement yOnlyPlacement(String coordinateSpace, double y) {
        StoryboardPlacement placement = new StoryboardPlacement();
        placement.setCoordinateSpace(coordinateSpace);
        placement.setY(axis(y, y));
        return placement;
    }

    private StoryboardPlacementAxis axis(double min, double max) {
        StoryboardPlacementAxis axis = new StoryboardPlacementAxis();
        axis.setMin(min);
        axis.setMax(max);
        return axis;
    }

    private static JsonNode wrapToolResponse(JsonNode arguments) {
        ObjectNode response = JsonUtils.mapper().createObjectNode();
        ArrayNode choices = response.putArray("choices");
        ObjectNode message = choices.addObject().putObject("message");
        ArrayNode toolCalls = message.putArray("tool_calls");
        ObjectNode function = toolCalls.addObject().putObject("function");
        function.put("name", "tool");
        function.set("arguments", arguments);
        return response;
    }

    private static final class RepeatingStoryboardAiClient implements AiClient {
        private final Storyboard storyboard;
        private final AtomicInteger toolCalls = new AtomicInteger();

        private RepeatingStoryboardAiClient(Storyboard storyboard) {
            this.storyboard = storyboard;
        }

        @Override
        public CompletableFuture<String> chatAsync(List<NodeConversationContext.Message> snapshot) {
            return CompletableFuture.completedFuture("{}");
        }

        @Override
        public CompletableFuture<JsonNode> chatWithToolsRawAsync(List<NodeConversationContext.Message> snapshot,
                                                                 String toolsJson) {
            toolCalls.incrementAndGet();
            ObjectNode arguments = JsonUtils.mapper().createObjectNode();
            arguments.set("storyboard", JsonUtils.mapper().valueToTree(storyboard));
            return CompletableFuture.completedFuture(wrapToolResponse(arguments));
        }

        @Override
        public String providerName() {
            return "repeating-storyboard";
        }
    }
}
