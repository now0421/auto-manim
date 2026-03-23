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
}
