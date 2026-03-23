package com.automanim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplatesTest {

    @Test
    void codeEvaluationPromptMentionsSemanticPlacementChecks() {
        String prompt = PromptTemplates.codeEvaluationSystemPrompt("Triangle Angles", "Demo");

        assertTrue(prompt.contains("semantically wrong placements"));
        assertTrue(prompt.contains("angle arc"));
        assertTrue(prompt.contains("labels attached to"));
    }

    @Test
    void codeReviewAndRevisionPromptsMentionPlacementCorrectness() {
        String reviewPrompt = PromptTemplates.codeReviewUserPrompt(
                "Triangle Angles",
                "DemoScene",
                "{\"scenes\":[]}",
                "{}",
                "from manim import *");
        String revisionPrompt = PromptTemplates.codeRevisionUserPrompt(
                "Triangle Angles",
                "DemoScene",
                "{\"scenes\":[]}",
                "{}",
                "{}",
                "from manim import *");

        assertTrue(reviewPrompt.contains("semantically"));
        assertTrue(reviewPrompt.contains("correct spatial relationship"));
        assertTrue(revisionPrompt.contains("angle arcs"));
        assertTrue(revisionPrompt.contains("wrong geometry"));
    }

    @Test
    void promptsMentionThreeDPlanningAndOverlayRules() {
        String visualPrompt = PromptTemplates.visualDesignSystemPrompt("Vector Field", "3D demo");
        String narrativePrompt = PromptTemplates.narrativeSystemPrompt("Vector Field", "3D demo");
        String codegenPrompt = PromptTemplates.storyboardCodegenPrompt(
                "Vector Field",
                "{\"scenes\":[{\"scene_mode\":\"3d\",\"camera_plan\":\"orbit\",\"screen_overlay_plan\":\"Keep title fixed\"}]}");
        String reviewPrompt = PromptTemplates.codeEvaluationSystemPrompt("Vector Field", "3D demo");

        assertTrue(visualPrompt.contains("scene_mode"));
        assertTrue(visualPrompt.contains("screen_overlay_plan"));
        assertTrue(narrativePrompt.contains("camera_plan"));
        assertTrue(narrativePrompt.contains("scene_mode"));
        assertTrue(codegenPrompt.contains("ThreeDScene"));
        assertTrue(codegenPrompt.contains("add_fixed_in_frame_mobjects"));
        assertTrue(reviewPrompt.contains("projected screen image"));
        assertTrue(reviewPrompt.contains("ThreeDScene"));
    }
}
