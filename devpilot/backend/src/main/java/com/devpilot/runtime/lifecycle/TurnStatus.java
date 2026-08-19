package com.devpilot.runtime.lifecycle;

/**
 * Status of a turn, the complete cycle from one user input until the runtime stops working.
 *
 * <p>A turn is created in {@link #RUNNING} and may reach exactly one terminal status. Terminal
 * statuses never transition again; a later state change is expressed by a new turn.
 */
public enum TurnStatus {
    /** The runtime is still working on the turn. */
    RUNNING,
    /** The runtime finished the turn normally. */
    COMPLETED,
    /** The turn stopped because of an unrecoverable error or timeout. */
    FAILED,
    /** The turn stopped because the user cancelled it or the service restarted. */
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
    public boolean canTransitionTo(TurnStatus target) {
        return this == RUNNING && target.terminal();
    }
}
