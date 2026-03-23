package com.automanim.node;

import com.automanim.config.WorkflowConfig;
import com.automanim.model.CodeResult;
import com.automanim.model.Narrative;
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
import java.util.Deque;
import java.util.LinkedHashMap;
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

    private static Narrative buildNarrative() {
        Narrative narrative = new Narrative();
        narrative.setTargetConcept("Demo concept");
        narrative.setTargetDescription("Demo description");
        narrative.setVerbosePrompt("Generate a minimal demo scene.");
        return narrative;
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

        @Override
        public String chat(String userMessage, String systemPrompt) {
            return chatResponses.removeFirst();
        }

        @Override
        public CompletableFuture<JsonNode> chatWithToolsRawAsync(String userMessage,
                                                                 String systemPrompt,
                                                                 String toolsJson) {
            return CompletableFuture.completedFuture(toolResponses.removeFirst());
        }

        @Override
        public String providerName() {
            return "test";
        }
    }
}
