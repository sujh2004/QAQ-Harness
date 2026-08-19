package com.devpilot.runtime.lifecycle;

/**
 * Status of a step, which wraps at most one model request and the tool calls it produced.
 */
public enum StepStatus {
    /** The step is still executing. */
    RUNNING,
    /** The step finished normally. */
    COMPLETED,
    /** The step failed. */
    FAILED,
    /** The step was cancelled together with its turn. */
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
    public boolean canTransitionTo(StepStatus target) {
        return this == RUNNING && target.terminal();
    }
}
