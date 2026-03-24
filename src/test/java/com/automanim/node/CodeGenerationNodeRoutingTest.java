package com.automanim.node;

import com.automanim.config.WorkflowConfig;
import com.automanim.model.CodeResult;
import com.automanim.model.Narrative;
import com.automanim.model.Narrative.Storyboard;
import com.automanim.model.Narrative.StoryboardAction;
import com.automanim.model.Narrative.StoryboardObject;
import com.automanim.model.Narrative.StoryboardScene;
import com.automanim.model.WorkflowActions;
import com.automanim.model.WorkflowKeys;
import com.automanim.service.AiClient;
import com.automanim.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.the_pocket.PocketFlow;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationNodeRoutingTest {

    @Test
    void routesValidationFixThroughSharedCodeFixNode() {
        QueueAiClient aiClient = new QueueAiClient();
        aiClient.toolResponses.add(codegenResponse(String.join("\n",
                "from manim import *",
                "",
                "class DemoScene(Scene):",
                "    def construct(self):",
                "        self.bad = Text(\"bad\")")));
        aiClient.chatResponses.add(wrapCodeResponse(String.join("\n",
                "from manim import *",
                "",
                "class MainScene(Scene):",
                "    def construct(self):",
                "        label = Text(\"ok\")",
                "        self.play(Write(label))")));

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.AI_CLIENT, aiClient);
        ctx.put(WorkflowKeys.CONFIG, new WorkflowConfig());
        ctx.put(WorkflowKeys.NARRATIVE, buildNarrative());

        CodeGenerationNode codeGeneration = new CodeGenerationNode();
        CodeFixNode codeFix = new CodeFixNode();
        codeGeneration.next(codeFix, WorkflowActions.FIX_CODE);
        codeFix.next(codeGeneration, WorkflowActions.RETRY_CODE_GENERATION);

        new PocketFlow.Flow<>(codeGeneration).run(ctx);

        CodeResult codeResult = (CodeResult) ctx.get(WorkflowKeys.CODE_RESULT);
        assertNotNull(codeResult);
        assertEquals("MainScene", codeResult.getSceneName());
        assertTrue(codeResult.getManimCode().contains("label = Text"));
        assertFalse(codeResult.getManimCode().contains("self.bad"));
        assertEquals(2, codeResult.getToolCalls());
    }

    @Test
    void codegenPromptUsesCompactStoryboardFocusedOnSceneExecution() {
        QueueAiClient aiClient = new QueueAiClient();
        aiClient.toolResponses.add(codegenResponse(String.join("\n",
                "from manim import *",
                "",
                "class MainScene(Scene):",
                "    def construct(self):",
                "        title = Text(\"ok\")",
                "        self.play(Write(title))")));

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.AI_CLIENT, aiClient);
        ctx.put(WorkflowKeys.CONFIG, new WorkflowConfig());
        ctx.put(WorkflowKeys.NARRATIVE, buildStoryboardNarrative());

        new CodeGenerationNode().run(ctx);

        assertNotNull(aiClient.lastUserMessage);
        assertTrue(aiClient.lastUserMessage.contains("\"scenes\""));
        assertTrue(aiClient.lastUserMessage.contains("\"entering_objects\""));
        assertTrue(aiClient.lastUserMessage.contains("\"actions\""));
        assertTrue(aiClient.lastUserMessage.contains("\"continuity_plan\""));
        assertTrue(aiClient.lastUserMessage.contains("\"safe_area_plan\""));

        assertFalse(aiClient.lastUserMessage.contains("\"hook\""));
        assertFalse(aiClient.lastUserMessage.contains("\"summary\""));
        assertFalse(aiClient.lastUserMessage.contains("\"goal\""));
        assertFalse(aiClient.lastUserMessage.contains("\"layout_goal\""));
    }

    private static Narrative buildNarrative() {
        Narrative narrative = new Narrative();
        narrative.setTargetConcept("Demo concept");
        narrative.setTargetDescription("Demo description");
        narrative.setVerbosePrompt("Generate a minimal demo scene.");
        return narrative;
    }

    private static Narrative buildStoryboardNarrative() {
        Narrative narrative = new Narrative();
        narrative.setTargetConcept("Demo concept");
        narrative.setTargetDescription("Demo description");
        narrative.setStoryboard(buildStoryboard());
        return narrative;
    }

    private static Storyboard buildStoryboard() {
        Storyboard storyboard = new Storyboard();
        storyboard.setHook("Open with a motivating question.");
        storyboard.setSummary("Show one scene clearly.");
        storyboard.setContinuityPlan("Keep the same title object alive.");
        storyboard.setGlobalVisualRules(List.of("Keep the title in the safe area."));

        StoryboardScene scene = new StoryboardScene();
        scene.setSceneId("scene_1");
        scene.setTitle("Intro");
        scene.setGoal("Introduce the main idea.");
        scene.setNarration("Write the title and pause.");
        scene.setDurationSeconds(6);
        scene.setSceneMode("2d");
        scene.setCameraAnchor("center");
        scene.setCameraPlan("Static 2D camera.");
        scene.setLayoutGoal("Place the title near the top.");
        scene.setSafeAreaPlan("Leave a top margin and keep all text centered.");
        scene.setScreenOverlayPlan("No fixed overlay needed.");
        scene.setStepRefs(List.of("problem"));

        StoryboardObject title = new StoryboardObject();
        title.setId("title_main");
        title.setKind("text");
        title.setContent("Demo title");
        title.setPlacement("top-center, y = 3.0");
        title.setStyle("WHITE, scale 0.9");
        title.setSourceNode("problem");
        scene.setEnteringObjects(List.of(title));
        scene.setPersistentObjects(List.of("title_main"));
        scene.setExitingObjects(new ArrayList<>());

        StoryboardAction action = new StoryboardAction();
        action.setOrder(1);
        action.setType("create");
        action.setTargets(List.of("title_main"));
        action.setDescription("Write the title.");
        scene.setActions(List.of(action));
        scene.setNotesForCodegen(List.of("Keep the title anchored near the top."));

        storyboard.setScenes(List.of(scene));
        return storyboard;
    }

    private static JsonNode codegenResponse(String code) {
        ObjectNode response = JsonUtils.mapper().createObjectNode();
        ArrayNode choices = response.putArray("choices");
        ObjectNode message = choices.addObject().putObject("message");
        ArrayNode toolCalls = message.putArray("tool_calls");
        ObjectNode function = toolCalls.addObject().putObject("function");
        function.put("name", "write_manim_code");

        ObjectNode arguments = JsonUtils.mapper().createObjectNode();
        arguments.put("code", code);
        arguments.put("scene_name", "DemoScene");
        arguments.put("description", "demo");
        function.set("arguments", arguments);
        return response;
    }

    private static String wrapCodeResponse(String code) {
        return "```python\n" + code + "\n```";
    }

    private static final class QueueAiClient implements AiClient {
        private final Deque<JsonNode> toolResponses = new ArrayDeque<>();
        private final Deque<String> chatResponses = new ArrayDeque<>();
        private String lastUserMessage;
        private String lastSystemPrompt;
        private String lastToolsJson;

        @Override
        public String chat(String userMessage, String systemPrompt) {
            lastUserMessage = userMessage;
            lastSystemPrompt = systemPrompt;
            return chatResponses.removeFirst();
        }

        @Override
        public CompletableFuture<JsonNode> chatWithToolsRawAsync(String userMessage,
                                                                 String systemPrompt,
                                                                 String toolsJson) {
            lastUserMessage = userMessage;
            lastSystemPrompt = systemPrompt;
            lastToolsJson = toolsJson;
            return CompletableFuture.completedFuture(toolResponses.removeFirst());
        }

        @Override
        public String providerName() {
            return "test";
        }
    }
}
