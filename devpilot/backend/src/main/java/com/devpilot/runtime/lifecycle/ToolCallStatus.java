package com.devpilot.runtime.lifecycle;

/**
 * Status of one tool call.
 *
 * <p>Every call starts as {@link #REQUESTED} and must reach a terminal status, including the
 * rejection paths. Argument validation failures, policy denials, timeouts and provider faults are
 * normalised into these statuses instead of escaping as exceptions.
 */
public enum ToolCallStatus {
    /** The call was accepted for execution but has not finished. */
    REQUESTED,
    /** The provider returned a result. */
    SUCCESS,
    /** The arguments failed schema or bean validation. */
    INVALID_ARGUMENT,
    /** The tool is unknown, invisible in the current scope, or forbidden by policy. */
    DENIED,
    /** The provider exceeded the tool timeout. */
    TIMEOUT,
    /** The provider failed. */
    PROVIDER_ERROR,
    /** The call was cancelled with its turn or closed by restart recovery. */
    CANCELLED;

    /** @return whether no further transition is allowed */
    public boolean terminal() {
        return this != REQUESTED;
    }

    /**
     * Reports whether this status may transition to the given status.
     *
     * @param target requested next status
     * @return whether the transition is part of the state machine
     */
    public boolean canTransitionTo(ToolCallStatus target) {
        return this == REQUESTED && target.terminal();
    }
}
