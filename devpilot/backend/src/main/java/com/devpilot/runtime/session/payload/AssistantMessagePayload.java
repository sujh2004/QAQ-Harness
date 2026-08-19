package com.devpilot.runtime.session.payload;

/**
 * A complete assistant message. Only visible output is recorded, never hidden reasoning.
 *
 * @param agentName agent that produced the message
 * @param content message text
 */
public record AssistantMessagePayload(String agentName, String content) implements SessionEventPayload {
}
