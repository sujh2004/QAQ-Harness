package com.devpilot.runtime.session;

import com.devpilot.runtime.session.payload.SessionEventPayload;

/**
 * A request to append one event. The store assigns {@code seq} and {@code occurredAt}, so callers
 * cannot invent a position in the stream.
 *
 * @param eventType kind of fact to record
 * @param turnId owning turn, null for session-level events
 * @param stepId owning step, null when not step-scoped
 * @param runId owning agent run, null when not run-scoped
 * @param callId owning tool call, null when not call-scoped
 * @param payload event body
 */
public record AppendEventCommand(
        SessionEventType eventType,
        String turnId,
        String stepId,
        String runId,
        String callId,
        SessionEventPayload payload) {

    /**
     * Builds a session-scoped command.
     *
     * @param eventType kind of fact to record
     * @param payload event body
     * @return append command
     */
    public static AppendEventCommand ofSession(SessionEventType eventType, SessionEventPayload payload) {
        return new AppendEventCommand(eventType, null, null, null, null, payload);
    }

    /**
     * Builds a turn-scoped command.
     *
     * @param eventType kind of fact to record
     * @param turnId owning turn
     * @param payload event body
     * @return append command
     */
    public static AppendEventCommand ofTurn(SessionEventType eventType, String turnId, SessionEventPayload payload) {
        return new AppendEventCommand(eventType, turnId, null, null, null, payload);
    }

    /**
     * Builds a step-scoped command.
     *
     * @param eventType kind of fact to record
     * @param turnId owning turn
     * @param stepId owning step
     * @param payload event body
     * @return append command
     */
    public static AppendEventCommand ofStep(
            SessionEventType eventType, String turnId, String stepId, SessionEventPayload payload) {
        return new AppendEventCommand(eventType, turnId, stepId, null, null, payload);
    }

    /**
     * Builds a run-scoped command.
     *
     * @param eventType kind of fact to record
     * @param turnId owning turn
     * @param stepId owning step, may be null
     * @param runId owning agent run
     * @param payload event body
     * @return append command
     */
    public static AppendEventCommand ofRun(
            SessionEventType eventType, String turnId, String stepId, String runId, SessionEventPayload payload) {
        return new AppendEventCommand(eventType, turnId, stepId, runId, null, payload);
    }

    /**
     * Builds a tool-call-scoped command.
     *
     * @param eventType kind of fact to record
     * @param turnId owning turn
     * @param stepId owning step, may be null
     * @param runId owning agent run, may be null
     * @param callId owning tool call
     * @param payload event body
     * @return append command
     */
    public static AppendEventCommand ofCall(
            SessionEventType eventType,
            String turnId,
            String stepId,
            String runId,
            String callId,
            SessionEventPayload payload) {
        return new AppendEventCommand(eventType, turnId, stepId, runId, callId, payload);
    }
}
