package com.devpilot.runtime.model;

/**
 * Observability data every model provider must report.
 *
 * <p>These fields make a run explainable after the fact: which provider and model answered, how
 * long it took and why it stopped. Credentials are never part of it.
 *
 * @param provider provider identifier, for example {@code dashscope}
 * @param model concrete model name the provider used
 * @param requestId provider request identifier, null when the provider returns none
 * @param firstTokenLatencyMs time until the first token, zero for non-streaming calls
 * @param totalDurationMs total call duration
 * @param promptTokens prompt tokens, null when the provider does not report usage
 * @param completionTokens completion tokens, null when the provider does not report usage
 * @param finishReason why generation stopped, for example {@code stop} or {@code tool_calls}
 */
public record ModelCallMetadata(
        String provider,
        String model,
        String requestId,
        long firstTokenLatencyMs,
        long totalDurationMs,
        Integer promptTokens,
        Integer completionTokens,
        String finishReason) {
}
