package com.devpilot.runtime.session.payload;

/**
 * A streamed fragment of an assistant message.
 *
 * <p>Deltas are a presentation detail: the durable message is {@link AssistantMessagePayload}. A
 * replay that cannot understand this event may skip it without losing model-visible state.
 *
 * @param content text fragment
 */
public record AssistantDeltaPayload(String content) implements SessionEventPayload {
}
