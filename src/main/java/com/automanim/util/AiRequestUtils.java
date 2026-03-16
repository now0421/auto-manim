package com.automanim.util;

import com.automanim.service.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
        return requestJsonObjectAsync(
                aiClient,
                log,
                subject,
                userPrompt,
                systemPrompt,
                toolsJson,
                onApiCall,
                AiRequestUtils::parsePlainTextJsonObject
        );
    }

    public static CompletableFuture<JsonNode> requestJsonObjectAsync(AiClient aiClient,
                                                                     Logger log,
                                                                     String subject,
                                                                     String userPrompt,
                                                                     String systemPrompt,
                                                                     String toolsJson,
                                                                     Runnable onApiCall,
                                                                     Function<String, JsonNode> plainTextParser) {
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
                                return parsePlainTextResponse(response, plainTextParser);
                            });
                });
    }

    /**
     * Context-aware variant that uses a {@link NodeConversationContext} for
     * multi-turn conversations.
     *
     * <p>Concurrency-safe: takes a snapshot of the current context plus the new
     * user message, sends the snapshot to the AI (without mutating the shared
     * context), and only appends the completed user+assistant turn after the
     * response arrives. This prevents message interleaving when multiple
     * requests execute concurrently within the same depth level.
     */
    public static CompletableFuture<JsonNode> requestJsonObjectAsync(AiClient aiClient,
                                                                     Logger log,
                                                                     String subject,
                                                                     NodeConversationContext context,
                                                                     String userPrompt,
                                                                     String toolsJson,
                                                                     Runnable onApiCall) {
        return requestJsonObjectAsync(
                aiClient,
                log,
                subject,
                context,
                userPrompt,
                toolsJson,
                onApiCall,
                AiRequestUtils::parsePlainTextJsonObject
        );
    }

    public static CompletableFuture<JsonNode> requestJsonObjectAsync(AiClient aiClient,
                                                                     Logger log,
                                                                     String subject,
                                                                     NodeConversationContext context,
                                                                     String userPrompt,
                                                                     String toolsJson,
                                                                     Runnable onApiCall,
                                                                     Function<String, JsonNode> plainTextParser) {
        // Snapshot: frozen copy of existing messages + the new user message.
        // Does NOT mutate the shared context.
        NodeConversationContext.TurnReservation reservation = context.reserveTurn(userPrompt);
        java.util.List<NodeConversationContext.Message> snapshot = reservation.getSnapshot();
        NodeConversationContext.trimSnapshotToFitBudget(
                snapshot, context.getMaxInputTokens());

        return aiClient.chatWithToolsRawAsync(snapshot, toolsJson)
                .thenApply(rawResponse -> {
                    onApiCall.run();
                    JsonNode data = extractJsonObject(rawResponse);
                    if (data != null) {
                        String assistantText = JsonUtils.buildToolCallTranscript(rawResponse);
                        if (assistantText == null || assistantText.isBlank()) {
                            assistantText = data.toPrettyString();
                        }
                        context.appendReservedTurn(
                                reservation.getSequence(), userPrompt, assistantText);
                    }
                    return data;
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
                    // Fallback: take a fresh snapshot (may now include turns from
                    // concurrent siblings that completed first).
                    java.util.List<NodeConversationContext.Message> fallbackSnapshot =
                            context.snapshotWithUserMessage(userPrompt);
                    NodeConversationContext.trimSnapshotToFitBudget(
                            fallbackSnapshot, context.getMaxInputTokens());

                    return aiClient.chatAsync(fallbackSnapshot)
                            .thenApply(response -> {
                                onApiCall.run();
                                context.appendReservedTurn(
                                        reservation.getSequence(), userPrompt, response);
                                return parsePlainTextResponse(response, plainTextParser);
                            });
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        context.cancelReservedTurn(reservation.getSequence());
                    }
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

    private static JsonNode parsePlainTextResponse(String response,
                                                   Function<String, JsonNode> plainTextParser) {
        Function<String, JsonNode> parser = plainTextParser != null
                ? plainTextParser
                : AiRequestUtils::parsePlainTextJsonObject;
        JsonNode parsed = parser.apply(response);
        return parsed != null ? parsed : JsonUtils.parseTree("{}");
    }

    private static JsonNode parsePlainTextJsonObject(String response) {
        return JsonUtils.parseTree(JsonUtils.extractJsonObject(response));
    }
}
