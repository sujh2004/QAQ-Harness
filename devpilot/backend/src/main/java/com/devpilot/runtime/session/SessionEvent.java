package com.devpilot.runtime.session;

import com.devpilot.runtime.session.payload.SessionEventPayload;

import java.time.Instant;

/**
 * One committed fact in a session event stream.
 *
 * <p>Events are append-only. A state change is expressed by appending a new event, never by
 * updating an existing one, which is what makes the stream replayable.
 *
 * @param id database identity
 * @param sessionId owning session
 * @param seq monotonically increasing position inside the session, starting at 1
 * @param eventType kind of fact recorded
 * @param schemaVersion payload schema version the event was written with
 * @param turnId owning turn, null for session-level events
 * @param stepId owning step, null when the event is not step-scoped
 * @param runId owning agent run, null when the event is not run-scoped
 * @param callId owning tool call, null when the event is not call-scoped
 * @param payload decoded event body
 * @param occurredAt when the fact happened
 */
public record SessionEvent(
        Long id,
        String sessionId,
        long seq,
        SessionEventType eventType,
        int schemaVersion,
        String turnId,
        String stepId,
        String runId,
        String callId,
        SessionEventPayload payload,
        Instant occurredAt) {

    /**
     * Returns the payload as the given type.
     *
     * @param type expected payload record
     * @param <T> payload type
     * @return typed payload
     * @throws IllegalStateException when the event carries a different payload type
     */
    public <T extends SessionEventPayload> T payloadAs(Class<T> type) {
        if (!type.isInstance(payload)) {
            throw new IllegalStateException(
                    "Event " + eventType.wireName() + "#" + seq + " does not carry " + type.getSimpleName());
        }
        return type.cast(payload);
    }
}
