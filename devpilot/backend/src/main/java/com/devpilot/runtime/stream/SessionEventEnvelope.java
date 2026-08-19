package com.devpilot.runtime.stream;

import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.payload.SessionEventPayload;

import java.time.Instant;

/**
 * The wire shape of one session event pushed to a browser.
 *
 * <p>SSE is a live projection of the event log, not a second source of truth: every frame carries
 * the sequence number of the committed event it came from, so a client can reconnect with
 * {@code Last-Event-ID} and consume idempotently by {@code (sessionId, seq)}.
 *
 * @param sessionId owning session
 * @param seq sequence number, also used as the SSE event id
 * @param occurredAt when the fact happened
 * @param eventType wire name of the event type
 * @param schemaVersion payload schema version
 * @param turnId owning turn, may be null
 * @param stepId owning step, may be null
 * @param runId owning agent run, may be null
 * @param callId owning tool call, may be null
 * @param payload event body
 */
public record SessionEventEnvelope(
        String sessionId,
        long seq,
        Instant occurredAt,
        String eventType,
        int schemaVersion,
        String turnId,
        String stepId,
        String runId,
        String callId,
        SessionEventPayload payload) {

    /**
     * Wraps a committed event.
     *
     * @param event committed session event
     * @return envelope ready to be serialized into an SSE frame
     */
    public static SessionEventEnvelope from(SessionEvent event) {
        return new SessionEventEnvelope(
                event.sessionId(),
                event.seq(),
                event.occurredAt(),
                event.eventType().wireName(),
                event.schemaVersion(),
                event.turnId(),
                event.stepId(),
                event.runId(),
                event.callId(),
                event.payload());
    }
}
