package com.devpilot.runtime.lifecycle;

/**
 * Raised when a caller requests a lifecycle transition the state machine does not allow, for
 * example starting a second turn while one is running or ending an already terminal step.
 */
public final class IllegalLifecycleTransitionException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the rejected transition
     */
    public IllegalLifecycleTransitionException(String message) {
        super(message);
    }
}
