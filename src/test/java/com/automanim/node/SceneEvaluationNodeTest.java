package com.automanim.node;

import com.automanim.config.WorkflowConfig;
import com.automanim.model.CodeFixRequest;
import com.automanim.model.CodeFixSource;
import com.automanim.model.CodeResult;
import com.automanim.model.RenderResult;
import com.automanim.model.SceneEvaluationResult;
import com.automanim.model.WorkflowActions;
import com.automanim.model.WorkflowKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneEvaluationNodeTest {

    @TempDir
    Path tempDir;

    @Test
    void approvesWhenAllSamplesAreWithinFrameAndNonOverlapping() throws IOException {
        Path geometryPath = tempDir.resolve("5_mobject_geometry.json");
        Files.writeString(geometryPath, cleanGeometryJson());

        Map<String, Object> ctx = buildContext(geometryPath);
        SceneEvaluationNode node = new SceneEvaluationNode();

        SceneEvaluationNode.SceneEvaluationInput input = node.prep(ctx);
        SceneEvaluationResult result = node.exec(input);
        String action = node.post(ctx, input, result);

        assertTrue(result.isEvaluated());
        assertTrue(result.isApproved());
        assertEquals(0, result.getTotalIssueCount());
        assertNull(action);
        assertTrue(Files.exists(tempDir.resolve("6_scene_evaluation.json")));
    }

    @Test
    void requestsCodeFixWhenGeometryContainsOverlapOrOffscreenIssues() throws IOException {
        Path geometryPath = tempDir.resolve("5_mobject_geometry.json");
        Files.writeString(geometryPath, problematicGeometryJson());

        Map<String, Object> ctx = buildContext(geometryPath);
        SceneEvaluationNode node = new SceneEvaluationNode();

        SceneEvaluationNode.SceneEvaluationInput input = node.prep(ctx);
        SceneEvaluationResult result = node.exec(input);
        String action = node.post(ctx, input, result);

        assertTrue(result.isEvaluated());
        assertFalse(result.isApproved());
        assertTrue(result.isRevisionTriggered());
        assertTrue(result.getTotalIssueCount() >= 2);
        assertEquals(WorkflowActions.FIX_CODE, action);

        CodeFixRequest request = (CodeFixRequest) ctx.get(WorkflowKeys.CODE_FIX_REQUEST);
        assertNotNull(request);
        assertEquals(CodeFixSource.SCENE_LAYOUT_EVALUATION, request.getSource());
        assertEquals(WorkflowActions.RETRY_RENDER, request.getReturnAction());
        assertNotNull(request.getSceneEvaluationJson());
        assertTrue(request.getSceneEvaluationJson().contains("\"issue_sample_count\""));
    }

    private Map<String, Object> buildContext(Path geometryPath) {
        WorkflowConfig config = new WorkflowConfig();
        config.setRenderEnabled(true);
        config.setSceneEvaluationMaxRetries(2);

        CodeResult codeResult = new CodeResult(
                String.join("\n",
                        "from manim import *",
                        "",
                        "class MainScene(Scene):",
                        "    def construct(self):",
                        "        self.wait(1)"),
                "MainScene",
                "demo",
                "Demo concept",
                "Demo description");

        RenderResult renderResult = new RenderResult();
        renderResult.setSuccess(true);
        renderResult.setSceneName("MainScene");
        renderResult.setGeometryPath(geometryPath.toString());
        renderResult.setAttempts(1);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.CONFIG, config);
        ctx.put(WorkflowKeys.CODE_RESULT, codeResult);
        ctx.put(WorkflowKeys.RENDER_RESULT, renderResult);
        ctx.put(WorkflowKeys.OUTPUT_DIR, tempDir);
        return ctx;
    }

    private String cleanGeometryJson() {
        return String.join("\n",
                "{",
                "  \"scene_name\": \"MainScene\",",
                "  \"frame_bounds\": {",
                "    \"min\": [-7.111111, -4.0, 0.0],",
                "    \"max\": [7.111111, 4.0, 0.0]",
                "  },",
                "  \"samples\": [",
                "    {",
                "      \"sample_id\": \"sample-0001\",",
                "      \"sample_role\": \"scene_final\",",
                "      \"elements\": [",
                "        {",
                "          \"stable_id\": \"title\",",
                "          \"semantic_name\": \"title\",",
                "          \"class_name\": \"Text\",",
                "          \"semantic_class\": \"text\",",
                "          \"visible\": true,",
                "          \"bounds\": {",
                "            \"min\": [-1.5, 2.0, 0.0],",
                "            \"max\": [1.5, 2.8, 0.0]",
                "          }",
                "        },",
                "        {",
                "          \"stable_id\": \"diagram\",",
                "          \"semantic_name\": \"diagram\",",
                "          \"class_name\": \"Circle\",",
                "          \"visible\": true,",
                "          \"bounds\": {",
                "            \"min\": [-1.0, -1.0, 0.0],",
                "            \"max\": [1.0, 1.0, 0.0]",
                "          }",
                "        }",
                "      ]",
                "    }",
                "  ]",
                "}");
    }

    private String problematicGeometryJson() {
        return String.join("\n",
                "{",
                "  \"scene_name\": \"MainScene\",",
                "  \"frame_bounds\": {",
                "    \"min\": [-7.111111, -4.0, 0.0],",
                "    \"max\": [7.111111, 4.0, 0.0]",
                "  },",
                "  \"samples\": [",
                "    {",
                "      \"sample_id\": \"sample-0001\",",
                "      \"sample_role\": \"scene_final\",",
                "      \"scene_method\": \"construct\",",
                "      \"source_code\": \"self.play(FadeIn(title), FadeIn(dot))\",",
                "      \"elements\": [",
                "        {",
                "          \"stable_id\": \"title\",",
                "          \"semantic_name\": \"title\",",
                "          \"class_name\": \"Text\",",
                "          \"semantic_class\": \"text\",",
                "          \"visible\": true,",
                "          \"bounds\": {",
                "            \"min\": [-1.0, -0.5, 0.0],",
                "            \"max\": [1.0, 0.5, 0.0]",
                "          }",
                "        },",
                "        {",
                "          \"stable_id\": \"dot\",",
                "          \"semantic_name\": \"dot\",",
                "          \"class_name\": \"Dot\",",
                "          \"visible\": true,",
                "          \"bounds\": {",
                "            \"min\": [0.2, -0.2, 0.0],",
                "            \"max\": [1.2, 0.8, 0.0]",
                "          }",
                "        },",
                "        {",
                "          \"stable_id\": \"formula\",",
                "          \"semantic_name\": \"formula\",",
                "          \"class_name\": \"MathTex\",",
                "          \"semantic_class\": \"formula\",",
                "          \"visible\": true,",
                "          \"bounds\": {",
                "            \"min\": [6.8, 3.2, 0.0],",
                "            \"max\": [7.5, 4.4, 0.0]",
                "          }",
                "        }",
                "      ]",
                "    }",
                "  ]",
                "}");
    }
}
