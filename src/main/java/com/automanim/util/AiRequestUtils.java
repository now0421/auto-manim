package com.automanim.util;

import com.automanim.service.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Shared async AI request helpers for JSON-oriented tool calls.
 */
public final class AiRequestUtils {

    private AiRequestUtils() {}

    public static CompletableFuture<JsonNode> requestJsonObjectAsync(AiClient aiClient,
                                                                     Logger log,
                                                                     String subject,
                                                                     String userPrompt,
                                                                     String systemPrompt,
                                                                     String toolsJson,
                                                                     Runnable onApiCall) {
        return aiClient.chatWithToolsRawAsync(userPrompt, systemPrompt, toolsJson)
                .thenApply(rawResponse -> {
                    onApiCall.run();
                    return extractJsonObject(rawResponse);
                })
                .exceptionally(error -> {
                    Throwable cause = ConcurrencyUtils.unwrapCompletionException(error);
                    log.debug("  Tool calling failed for '{}', falling back to plain chat: {}",
                            subject, cause.getMessage());
                    return null;
                })
                .thenCompose(data -> {
                    if (data != null) {
                        return CompletableFuture.completedFuture(data);
                    }
                    return aiClient.chatAsync(userPrompt, systemPrompt)
                            .thenApply(response -> {
                                onApiCall.run();
                                return JsonUtils.parseTree(JsonUtils.extractJsonObject(response));
                            });
                });
    }

    private static JsonNode extractJsonObject(JsonNode rawResponse) {
        JsonNode data = JsonUtils.extractToolCallPayload(rawResponse);
        if (data != null) {
            return data;
        }

        String textContent = JsonUtils.extractTextFromResponse(rawResponse);
        if (textContent != null && !textContent.isBlank()) {
            return JsonUtils.parseTree(JsonUtils.extractJsonObject(textContent));
        }

        return null;
    }
}
