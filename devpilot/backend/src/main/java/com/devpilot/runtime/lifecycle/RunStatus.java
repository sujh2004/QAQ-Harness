package com.devpilot.runtime.lifecycle;

/**
 * Status of a single agent run inside a turn.
 */
public enum RunStatus {
    /** The agent is still working. */
    RUNNING,
    /** The agent produced its result. */
    COMPLETED,
    /** The agent failed. */
    FAILED,
    /** The agent was cancelled together with its turn. */
    CANCELLED;

    /** @return whether no further transition is allowed */
    public boolean terminal() {
        return this != RUNNING;
    }

    /**
     * Reports whether this status may transition to the given status.
     *
     * @param target requested next status
     * @return whether the transition is part of the state machine
     */
    public boolean canTransitionTo(RunStatus target) {
        return this == RUNNING && target.terminal();
    }
}
