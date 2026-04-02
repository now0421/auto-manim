package com.automanim.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolSchemas constants.
 */
class ToolSchemasTest {

    @Test
    void prerequisitesTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.PREREQUISITES);
        });
    }

    @Test
    void foundationCheckTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.FOUNDATION_CHECK);
        });
    }

    @Test
    void inputModeTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.INPUT_MODE);
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
    void visualDesignTool_isValidJson() {
        assertDoesNotThrow(() -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(ToolSchemas.VISUAL_DESIGN);
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
    void prerequisitesTool_hasRequiredFields() {
        assertTrue(ToolSchemas.PREREQUISITES.contains("write_prerequisites"));
        assertTrue(ToolSchemas.PREREQUISITES.contains("prerequisites"));
        assertTrue(ToolSchemas.PREREQUISITES.contains("step"));
        assertTrue(ToolSchemas.PREREQUISITES.contains("reason"));
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
        assertTrue(ToolSchemas.CODE_REVIEW.contains("revision_directives"));
        assertTrue(ToolSchemas.CODE_REVIEW.contains("likely_offscreen_risk"));
    }

    @Test
    void storyboardTool_usesPropertiesNotInstructionsForStyle() {
        assertTrue(ToolSchemas.STORYBOARD.contains("\"properties\""));
        assertTrue(ToolSchemas.STORYBOARD.contains("\"required\": [\"role\", \"type\", \"properties\"]"));
        assertFalse(ToolSchemas.STORYBOARD.contains("\"instructions\""));
    }

    @Test
    void storyboardTool_requiresReferencedObjectsToBeNamedByIdOnly() {
        assertTrue(ToolSchemas.STORYBOARD.contains("mention them by id only"));
        assertTrue(ToolSchemas.STORYBOARD.contains("angle between AP and l at P"));
        assertTrue(ToolSchemas.STORYBOARD.contains("do not restate their kind"));
    }
}
