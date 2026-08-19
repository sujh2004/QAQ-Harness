package com.devpilot.runtime.session.payload;

/**
 * Records the user input that drives a turn. This is the durable fact behind the chat timeline.
 *
 * @param content user message text
 */
public record UserMessagePayload(String content) implements SessionEventPayload {
}
