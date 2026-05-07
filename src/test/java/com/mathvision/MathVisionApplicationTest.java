package com.mathvision;

import com.mathvision.config.ConfigLoader;
import com.mathvision.config.WorkflowConfig;
import com.mathvision.model.CodeFixTraceReport;
import com.mathvision.model.KnowledgeGraph;
import com.mathvision.model.KnowledgeNode;
import com.mathvision.model.WorkflowKeys;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MathVisionApplicationTest {

    @Test
    void workflowSummaryUsesTargetInputAndResolvedModeNames() throws Exception {
        WorkflowConfig config = ConfigLoader.load(null, null);
        config.setInputMode(WorkflowConfig.INPUT_MODE_AUTO);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(WorkflowKeys.CONFIG, config);
        ctx.put(WorkflowKeys.TARGET_INPUT, "Given A and B, find the shortest path.");
        ctx.put(WorkflowKeys.KNOWLEDGE_GRAPH, problemGraph());
        ctx.put(WorkflowKeys.RESOLVED_INPUT_MODE, WorkflowConfig.INPUT_MODE_PROBLEM);

        Map<String, Object> summary = buildSummary(ctx);

        assertEquals("Given A and B, find the shortest path.", summary.get("target_input"));
        assertEquals(WorkflowConfig.INPUT_MODE_AUTO, summary.get("input_mode_configured"));
        assertEquals(WorkflowConfig.INPUT_MODE_PROBLEM, summary.get("input_mode_resolved"));
        assertFalse(summary.containsKey("concept"));
        assertFalse(summary.containsKey("input_mode"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSummary(Map<String, Object> ctx) throws Exception {
        Method method = MathVisionApplication.class.getDeclaredMethod(
                "buildSummary", Map.class, Duration.class, CodeFixTraceReport.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                null, ctx, Duration.ofSeconds(2), new CodeFixTraceReport());
    }

    private KnowledgeGraph problemGraph() {
        KnowledgeNode start = new KnowledgeNode("start", "Set up the problem", 0);
        start.setNodeType(KnowledgeNode.NODE_TYPE_PROBLEM);
        return new KnowledgeGraph(
                "start",
                "Given A and B, find the shortest path.",
                Map.of("start", start),
                Map.of("start", List.of()),
                List.of("start"));
    }
}
