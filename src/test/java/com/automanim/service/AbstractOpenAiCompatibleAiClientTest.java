package com.automanim.service;

import com.automanim.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbstractOpenAiCompatibleAiClientTest {

    @Test
    void extractTextContentSupportsSegmentedContent() {
        ObjectNode response = JsonUtils.mapper().createObjectNode();
        ArrayNode choices = response.putArray("choices");
        ObjectNode message = choices.addObject().putObject("message");
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "text").put("text", "line 1");
        content.addObject().put("type", "text").put("text", "line 2");

        assertEquals("line 1\nline 2", AbstractOpenAiCompatibleAiClient.extractTextContent(response));
    }

    @Test
    void extractReasoningContentSupportsSegmentedReasoning() {
        ObjectNode response = JsonUtils.mapper().createObjectNode();
        ArrayNode choices = response.putArray("choices");
        ObjectNode message = choices.addObject().putObject("message");
        ArrayNode reasoning = message.putArray("reasoning_content");
        reasoning.addObject().put("type", "text").put("text", "step 1");
        reasoning.addObject().put("type", "text").put("text", "step 2");

        assertEquals("step 1\nstep 2",
                AbstractOpenAiCompatibleAiClient.extractReasoningContent(response));
    }

    @Test
    void extractTextContentReturnsNullForBlankContent() {
        ObjectNode response = JsonUtils.mapper().createObjectNode();
        response.putArray("choices").addObject().putObject("message");

        assertNull(AbstractOpenAiCompatibleAiClient.extractTextContent(response));
    }
}
