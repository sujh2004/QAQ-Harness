package com.devpilot.runtime.session;

import com.devpilot.runtime.session.payload.AgentFinishedPayload;
import com.devpilot.runtime.session.payload.AgentStartedPayload;
import com.devpilot.runtime.session.payload.ApprovalRequestedPayload;
import com.devpilot.runtime.session.payload.ApprovalResolvedPayload;
import com.devpilot.runtime.session.payload.AssistantDeltaPayload;
import com.devpilot.runtime.session.payload.AssistantMessagePayload;
import com.devpilot.runtime.session.payload.RuntimeErrorPayload;
import com.devpilot.runtime.session.payload.SessionCreatedPayload;
import com.devpilot.runtime.session.payload.SessionEventPayload;
import com.devpilot.runtime.session.payload.StepEndedPayload;
import com.devpilot.runtime.session.payload.StepStartedPayload;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.devpilot.runtime.session.payload.ToolCallRequestedPayload;
import com.devpilot.runtime.session.payload.TurnEndedPayload;
import com.devpilot.runtime.session.payload.TurnStartedPayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The closed set of facts a session event stream can record.
 *
 * <p>Each constant binds a stable wire name, the current schema version and the payload type used
 * to decode it. {@link #critical()} marks the events that carry lifecycle or model-visible state:
 * if such an event cannot be decoded, replay must fail loudly rather than silently change what the
 * model would have seen.
 */
public enum SessionEventType {
    /** Opens the stream and pins the profile version it replays under. */
    SESSION_CREATED("session_created", 1, SessionCreatedPayload.class, true),
    /** Opens a turn. */
    TURN_STARTED("turn_started", 1, TurnStartedPayload.class, true),
    /** Closes a turn. */
    TURN_ENDED("turn_ended", 1, TurnEndedPayload.class, true),
    /** Opens a step. */
    STEP_STARTED("step_started", 1, StepStartedPayload.class, true),
    /** Closes a step. */
    STEP_ENDED("step_ended", 1, StepEndedPayload.class, true),
    /** Records user input. */
    USER_MESSAGE("user_message", 1, UserMessagePayload.class, true),
    /** Records a streamed fragment of an assistant message. */
    ASSISTANT_DELTA("assistant_delta", 1, AssistantDeltaPayload.class, false),
    /** Records a complete assistant message. */
    ASSISTANT_MESSAGE("assistant_message", 1, AssistantMessagePayload.class, true),
    /** Opens an agent run. */
    AGENT_STARTED("agent_started", 1, AgentStartedPayload.class, true),
    /** Closes an agent run. */
    AGENT_FINISHED("agent_finished", 1, AgentFinishedPayload.class, true),
    /** Records that a tool call entered the execution pipeline. */
    TOOL_CALL_REQUESTED("tool_call_requested", 1, ToolCallRequestedPayload.class, true),
    /** Closes a tool call. */
    TOOL_CALL_FINISHED("tool_call_finished", 1, ToolCallFinishedPayload.class, true),
    /** Records that a call is waiting for human approval. */
    APPROVAL_REQUESTED("approval_requested", 1, ApprovalRequestedPayload.class, true),
    /** Records an approval decision. */
    APPROVAL_RESOLVED("approval_resolved", 1, ApprovalResolvedPayload.class, true),
    /** Records a runtime failure for the audit trail. */
    RUNTIME_ERROR("runtime_error", 1, RuntimeErrorPayload.class, false);

    private static final Map<String, SessionEventType> BY_WIRE_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(SessionEventType::wireName, Function.identity()));

    private final String wireName;
    private final int currentSchemaVersion;
    private final Class<? extends SessionEventPayload> payloadType;
    private final boolean critical;

    SessionEventType(
            String wireName,
            int currentSchemaVersion,
            Class<? extends SessionEventPayload> payloadType,
            boolean critical) {
        this.wireName = wireName;
        this.currentSchemaVersion = currentSchemaVersion;
        this.payloadType = payloadType;
        this.critical = critical;
    }

    /**
     * Looks up an event type by the name stored in the database.
     *
     * @param wireName persisted event type name
     * @return matching type, empty when this build does not know the name
     */
    public static Optional<SessionEventType> fromWireName(String wireName) {
        return Optional.ofNullable(BY_WIRE_NAME.get(wireName));
    }

    /** @return stable name persisted in {@code session_event.event_type} and sent over SSE */
    public String wireName() {
        return wireName;
    }

    /** @return schema version this build writes */
    public int currentSchemaVersion() {
        return currentSchemaVersion;
    }

    /** @return payload record used to decode the current schema version */
    public Class<? extends SessionEventPayload> payloadType() {
        return payloadType;
    }

    /** @return whether an undecodable occurrence must abort replay instead of being skipped */
    public boolean critical() {
        return critical;
    }
}
