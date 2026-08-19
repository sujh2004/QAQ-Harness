package com.devpilot.runtime.stream;

/**
 * One Server-Sent Events frame.
 *
 * @param id value of the SSE {@code id} field, the event sequence number
 * @param event value of the SSE {@code event} field, the event type wire name
 * @param data serialized {@link SessionEventEnvelope}
 */
public record SseFrame(String id, String event, String data) {
}
