package com.automanim.node;

import com.automanim.config.PipelineConfig;
import com.automanim.model.KnowledgeGraph;
import com.automanim.model.KnowledgeNode;
import com.automanim.model.PipelineKeys;
import com.automanim.service.AiClient;
import com.automanim.service.FileOutputService;
import com.automanim.util.JsonUtils;
import com.automanim.util.PromptTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.the_pocket.PocketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stage 1b: Visual Design - adds visual specifications to each node.
 *
 * Depth levels processed root-first (depth 0, then 1, then 2, ...):
 * - Nearest parent nodes are fully finalized before any child begins.
 * - Children at the same depth may run in parallel since they only read
 *   already-finalized parent specs.
 */
public class VisualDesignNode extends PocketFlow.Node<KnowledgeGraph, KnowledgeGraph, String> {

    private static final Logger log = LoggerFactory.getLogger(VisualDesignNode.class);

    private static final String VISUAL_DESIGN_TOOL = "["
            + "{"
            + "  \"type\": \"function\","
            + "  \"function\": {"
            + "    \"name\": \"write_visual_design\","
            + "    \"description\": \"Return a visual design spec for a concept animation scene.\","
            + "    \"parameters\": {"
            + "      \"type\": \"object\","
            + "      \"properties\": {"
            + "        \"visual_description\": { \"type\": \"string\", \"description\": \"Main visual objects and shapes\" },"
            + "        \"color_scheme\": { \"type\": \"string\", \"description\": \"Color scheme summary\" },"
            + "        \"animation_description\": { \"type\": \"string\", \"description\": \"Animation feel and transitions\" },"
            + "        \"transitions\": { \"type\": \"string\", \"description\": \"Scene transition style\" },"
            + "        \"duration\": { \"type\": \"number\", \"description\": \"Duration in seconds\" },"
            + "        \"layout\": { \"type\": \"string\", \"description\": \"Concrete 16:9 canvas layout\" },"
            + "        \"color_palette\": { \"type\": \"array\", \"items\": { \"type\": \"string\" }, \"description\": \"Preferred Manim color names\" }"
            + "      },"
            + "      \"required\": [\"visual_description\", \"color_scheme\", \"layout\"]"
            + "    }"
            + "  }"
            + "}"
            + "]";

    private AiClient aiClient;
    private final AtomicInteger toolCalls = new AtomicInteger(0);
    private boolean parallelEnabled = true;
    private int maxConcurrent = 4;
    private final List<String> globalColorPalette = java.util.Collections.synchronizedList(new ArrayList<>());
    private KnowledgeGraph graph;

    public VisualDesignNode() {
        super(1, 0);
    }

    @Override
    public KnowledgeGraph prep(Map<String, Object> ctx) {
        this.aiClient = (AiClient) ctx.get(PipelineKeys.AI_CLIENT);
        PipelineConfig config = (PipelineConfig) ctx.get(PipelineKeys.CONFIG);
        if (config != null) {
            this.parallelEnabled = config.isParallelVisualDesign();
            this.maxConcurrent = config.getMaxConcurrent();
        }
        return (KnowledgeGraph) ctx.get(PipelineKeys.KNOWLEDGE_GRAPH);
    }

    @Override
    public KnowledgeGraph exec(KnowledgeGraph graph) {
        log.info("=== Stage 1b: Visual Design (parallel={}) ===", parallelEnabled);
        toolCalls.set(0);
        globalColorPalette.clear();
        this.graph = graph;

        Map<Integer, List<KnowledgeNode>> levels = graph.groupByDepth();
        List<Integer> depths = new ArrayList<>(levels.keySet());
        java.util.Collections.sort(depths); // root-first: nearest parent spec finalized before children

        ExecutorService executor = parallelEnabled
                ? Executors.newFixedThreadPool(maxConcurrent) : null;

        try {
            for (int depth : depths) {
                List<KnowledgeNode> nodes = levels.get(depth);
                log.info("  Designing depth {} ({} nodes{})", depth, nodes.size(),
                        parallelEnabled && nodes.size() > 1 ? ", parallel" : "");

                if (parallelEnabled && nodes.size() > 1 && executor != null) {
                    List<Future<?>> futures = new ArrayList<>();
                    for (KnowledgeNode node : nodes) {
                        futures.add(executor.submit(() -> designNode(node)));
                    }
                    for (Future<?> f : futures) {
                        try {
                            f.get();
                        } catch (Exception e) {
                            log.warn("  Parallel visual design error: {}", e.getMessage());
                        }
                    }
                } else {
                    for (KnowledgeNode node : nodes) {
                        designNode(node);
                    }
                }
            }
        } finally {
            if (executor != null) executor.shutdown();
        }

        log.info("Visual design complete: {} API calls, palette: {}", toolCalls.get(), globalColorPalette);
        return graph;
    }

    @Override
    public String post(Map<String, Object> ctx, KnowledgeGraph prepRes, KnowledgeGraph graph) {
        ctx.put(PipelineKeys.KNOWLEDGE_GRAPH, graph);
        int prevCalls = (int) ctx.getOrDefault(PipelineKeys.ENRICHMENT_TOOL_CALLS, 0);
        ctx.put(PipelineKeys.ENRICHMENT_TOOL_CALLS, prevCalls + toolCalls.get());

        Path outputDir = (Path) ctx.get(PipelineKeys.OUTPUT_DIR);
        if (outputDir != null) {
            FileOutputService.saveEnrichedGraph(outputDir, graph);
        }
        return null;
    }

    private void designNode(KnowledgeNode node) {
        Map<String, Object> existingSpec = node.getVisualSpec();
        if (existingSpec != null && existingSpec.containsKey("visual_description")) {
            log.debug("  Skipping already-designed node: {}", node.getConcept());
            return;
        }

        String equationsInfo = node.getEquations() != null && !node.getEquations().isEmpty()
                ? String.join(", ", node.getEquations()) : "none";

        String parentSpecContext = buildParentSpecContext(node);

        String paletteContext = globalColorPalette.isEmpty()
                ? "No colors have been assigned yet."
                : "Colors already used: " + String.join(", ", globalColorPalette)
                  + ". Prefer harmonious contrast and avoid unnecessary repetition.";

        String userPrompt = String.format(
                "Concept: %s\nDepth: %d\nFoundation concept: %s\nRelevant equations: %s\n\n%s\n%s",
                node.getConcept(), node.getMinDepth(), node.isFoundation(),
                equationsInfo, parentSpecContext, paletteContext);

        try {
            JsonNode data = null;
            try {
                JsonNode rawResponse = aiClient.chatWithToolsRaw(
                        userPrompt, PromptTemplates.VISUAL_DESIGN_SYSTEM, VISUAL_DESIGN_TOOL);
                toolCalls.incrementAndGet();
                data = JsonUtils.extractToolCallPayload(rawResponse);

                if (data == null) {
                    String textContent = JsonUtils.extractTextFromResponse(rawResponse);
                    if (textContent != null && !textContent.isBlank()) {
                        data = JsonUtils.parseTree(JsonUtils.extractJsonObject(textContent));
                    }
                }
            } catch (Exception e) {
                log.debug("  Tool calling failed for '{}', falling back to plain chat", node.getConcept());
                String response = aiClient.chat(userPrompt, PromptTemplates.VISUAL_DESIGN_SYSTEM);
                toolCalls.incrementAndGet();
                data = JsonUtils.parseTree(JsonUtils.extractJsonObject(response));
            }

            if (data != null) {
                applyVisualSpec(node, data);
                log.debug("  Visual spec set for: {}", node.getConcept());
            }
        } catch (Exception e) {
            log.warn("  Visual design failed for '{}': {}", node.getConcept(), e.getMessage());
        }
    }

    /**
     * Uses the nearest parent set in the DAG.
     * Those parents are guaranteed to be at an earlier depth, so their
     * visual specs are finalized before this node is processed.
     */
    private String buildParentSpecContext(KnowledgeNode node) {
        List<KnowledgeNode> parents = graph.getNearestParents(node.getId());
        if (parents.isEmpty()) {
            return "This is the root concept. Establish the overall visual theme for the animation.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Nearest parent concepts:\n");
        for (KnowledgeNode parent : parents) {
            Map<String, Object> parentSpec = parent.getVisualSpec();
            sb.append(String.format("- %s%n", parent.getConcept()));
            if (parentSpec == null || parentSpec.isEmpty()) {
                sb.append("  No visual spec available yet.\n");
                continue;
            }
            if (parentSpec.containsKey("color_scheme")) {
                sb.append("  Color scheme: ").append(parentSpec.get("color_scheme")).append("\n");
            }
            if (parentSpec.containsKey("layout")) {
                sb.append("  Layout style: ").append(parentSpec.get("layout")).append("\n");
            }
            if (parentSpec.containsKey("visual_description")) {
                sb.append("  Visual style: ").append(parentSpec.get("visual_description")).append("\n");
            }
        }
        sb.append("Keep the style, palette, rhythm, and visual density consistent with these parents.");
        return sb.toString();
    }

    private void applyVisualSpec(KnowledgeNode node, JsonNode data) {
        Map<String, Object> visualSpec = node.getVisualSpec();
        if (visualSpec == null) {
            visualSpec = new LinkedHashMap<>();
        }

        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if ("color_palette".equals(key) && value.isArray()) {
                List<String> nodeColors = new ArrayList<>();
                for (JsonNode color : value) {
                    String colorName = color.asText();
                    nodeColors.add(colorName);
                    if (!globalColorPalette.contains(colorName)) {
                        globalColorPalette.add(colorName);
                    }
                }
                visualSpec.put(key, nodeColors);
            } else if ("duration".equals(key) && value.isNumber()) {
                visualSpec.put(key, value.numberValue());
            } else {
                visualSpec.put(key, value.asText());
            }
        }

        node.setVisualSpec(visualSpec);
    }
}
