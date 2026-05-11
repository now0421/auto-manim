package com.mathvision.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolSchemas constants.
 */
class ToolSchemasTest {

    @Test
    void inputModeTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.INPUT_MODE);
        });
    }

    @Test
    void conceptGraphTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.CONCEPT_GRAPH);
        });
    }

    @Test
    void problemGraphTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.PROBLEM_GRAPH);
        });
    }

    @Test
    void mathEnrichmentTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.MATH_ENRICHMENT);
        });
    }

    @Test
    void storyboardTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.STORYBOARD);
        });
    }

    @Test
    void sceneDesignTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.SCENE_DESIGN);
        });
    }

    @Test
    void manimCodeTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.MANIM_CODE);
        });
    }

    @Test
    void codeReviewTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.CODE_REVIEW);
        });
    }

    
    @Test
    void conceptGraphTool_hasRequiredFields() {
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("write_concept_graph"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("start_id"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("nodes"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("next_edges"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("concept"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("observation"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("construction"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("derivation"));
        assertTrue(ToolSchemas.CONCEPT_GRAPH.contains("conclusion"));
        assertFalse(ToolSchemas.CONCEPT_GRAPH.contains("prerequisite_edges"));
    }

    @Test
    void manimCodeTool_hasRequiredFields() {
        assertTrue(ToolSchemas.MANIM_CODE.contains("write_manim_code"));
        assertTrue(ToolSchemas.MANIM_CODE.contains("manimCode"));
        assertTrue(ToolSchemas.MANIM_CODE.contains("scene_name"));
    }

    @Test
    void codeReviewTool_usesCanonicalFields() {
        assertTrue(ToolSchemas.CODE_REVIEW.contains("approved_for_render"));
        assertTrue(ToolSchemas.CODE_REVIEW.contains("rule_checks"));
        assertTrue(ToolSchemas.CODE_REVIEW.contains("rule_id"));
        assertTrue(ToolSchemas.CODE_REVIEW.contains("not_applicable"));
        assertTrue(ToolSchemas.CODE_REVIEW.contains("revision_directives"));
        assertFalse(ToolSchemas.CODE_REVIEW.contains("layout_score"));
        assertFalse(ToolSchemas.CODE_REVIEW.contains("pacing_score"));
    }

    @Test
    void storyboardTool_usesTypedStyleObjectWithoutInstructions() {
        assertTrue(ToolSchemas.STORYBOARD.contains("\"properties\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("Do not invent keys"));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"additionalProperties\": false"));
        assertFalse(ToolSchemas.STORYBOARD.contains("\"required\": [\"role\", \"type\", \"properties\"]"));
        assertFalse(ToolSchemas.STORYBOARD.contains("\"instructions\""));
    }

    @Test
    void storyboardTool_requiresReferencedObjectsToBeNamedByIdOnly() {
        assertTrue(ToolSchemas.STORYBOARD.contains("mention them by id only"));
        assertTrue(ToolSchemas.STORYBOARD.contains("angle between AP and l at P"));
        assertTrue(ToolSchemas.STORYBOARD.contains("do not restate their kind"));
    }

    @Test
    void sharedSchemasUseBackendNeutralContinuityLanguage() {
        assertFalse(ToolSchemas.STORYBOARD.contains("reuse mobjects safely"));
        assertFalse(ToolSchemas.STORYBOARD.contains("Ordered animation operations"));
        assertTrue(ToolSchemas.STORYBOARD.contains("same logical objects safely"));
        assertTrue(ToolSchemas.STORYBOARD.contains("scene or presentation operations"));
    }

    @Test
    void storyboardToolExplainsFixedOverlayAsOverlaySpecific() {
        assertTrue(ToolSchemas.STORYBOARD.contains("Use `fixed_overlay` mainly for explanatory text"));
        assertTrue(ToolSchemas.STORYBOARD.contains("rather than native geometry"));
    }

    @Test
    void schemasCaptureBeatMappingAndExplicitObjectContracts() {
        assertTrue(ToolSchemas.STORYBOARD.contains("learner-visible object"));
        assertTrue(ToolSchemas.STORYBOARD.contains("learner-visible beat"));
        assertTrue(ToolSchemas.STORYBOARD.contains("what the learner should notice"));
    }

    @Test
    void storyboardSchemaUsesStrictObjectContractsAndEnums() {
        assertTrue(ToolSchemas.STORYBOARD.contains("\"additionalProperties\": false"));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"enum\": [\"static\", \"follows_anchor\", \"derived\", \"fixed_overlay\"]"));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"enum\": [\"create\", \"write\", \"transform\", \"highlight\", \"move\", \"fade_out\", \"camera\", \"restyle\"]"));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"enum\": [\"solid\", \"dashed\", \"dotted\", \"dash_dot\"]"));
    }

    @Test
    void storyboardSchemaAddsTypedStylePropertiesGuardrails() {
        assertFalse(ToolSchemas.STORYBOARD.contains("\"patternProperties\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"highlight_color\": { \"type\": \"string\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"point_style\": { \"type\": \"number\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"font_size\": { \"type\": \"number\" }"));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"label_visible\": { \"type\": \"boolean\" }"));
    }

    @Test
    void storyboardSchemaIncludesStructuredConstraintsContract() {
        assertTrue(ToolSchemas.STORYBOARD.contains("\"constraints\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"domain\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"relation\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"refs\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"parameters\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("angle_between_rays"));
        assertFalse(ToolSchemas.STORYBOARD.contains("constraint_note"));
    }

    @Test
    void sceneDesignSchemaIncludesSceneLevelConstraintsContract() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode schema = mapper.readTree(ToolSchemas.SCENE_DESIGN);

        com.fasterxml.jackson.databind.JsonNode sceneProperties = schema.get(0)
                .path("function")
                .path("parameters")
                .path("properties")
                .path("scene")
                .path("properties");

        assertFalse(sceneProperties.has("geometry_constraints"));
        assertTrue(sceneProperties.has("constraints"));
        assertEquals("array", sceneProperties.path("constraints").path("type").asText());
        assertEquals("object", sceneProperties.path("constraints").path("items").path("type").asText());
        assertTrue(sceneProperties.path("constraints").path("items").path("properties").has("relation"));
    }
}
