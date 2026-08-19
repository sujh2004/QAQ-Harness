package com.devpilot.runtime.stream;

import com.devpilot.runtime.session.SessionEventType;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping from runtime event types to the short names an earlier draft of the UI used.
 *
 * <p>The wire protocol is the unified envelope; this mapping only lets a client keep rendering with
 * the older vocabulary. Event types without a legacy name simply have no entry.
 */
public enum LegacyUiEventType {
    /** Legacy name of {@link SessionEventType#TURN_STARTED}. */
    MESSAGE_START("message_start", SessionEventType.TURN_STARTED),
    /** Legacy name of {@link SessionEventType#TURN_ENDED}. */
    MESSAGE_FINISH("message_finish", SessionEventType.TURN_ENDED),
    /** Legacy name of {@link SessionEventType#AGENT_STARTED}. */
    AGENT_START("agent_start", SessionEventType.AGENT_STARTED),
    /** Legacy name of {@link SessionEventType#AGENT_FINISHED}. */
    AGENT_FINISH("agent_finish", SessionEventType.AGENT_FINISHED),
    /** Legacy name of {@link SessionEventType#TOOL_CALL_REQUESTED}. */
    TOOL_CALL("tool_call", SessionEventType.TOOL_CALL_REQUESTED),
    /** Legacy name of {@link SessionEventType#TOOL_CALL_FINISHED}. */
    TOOL_RESULT("tool_result", SessionEventType.TOOL_CALL_FINISHED),
    /** Legacy name of {@link SessionEventType#ASSISTANT_DELTA}. */
    TEXT_DELTA("text_delta", SessionEventType.ASSISTANT_DELTA),
    /** Legacy name of {@link SessionEventType#RUNTIME_ERROR}. */
    ERROR("error", SessionEventType.RUNTIME_ERROR);

    private final String uiType;
    private final SessionEventType eventType;

    LegacyUiEventType(String uiType, SessionEventType eventType) {
        this.uiType = uiType;
        this.eventType = eventType;
    }

    /**
     * Finds the legacy name of an event type.
     *
     * @param eventType runtime event type
     * @return legacy mapping, empty when the type has no legacy name
     */
    public static Optional<LegacyUiEventType> forEventType(SessionEventType eventType) {
        return Arrays.stream(values()).filter(value -> value.eventType == eventType).findFirst();
    }

    /** @return short name used by the earlier UI draft */
    public String uiType() {
        return uiType;
    }

    /** @return runtime event type this name refers to */
    public SessionEventType eventType() {
        return eventType;
    }
}
