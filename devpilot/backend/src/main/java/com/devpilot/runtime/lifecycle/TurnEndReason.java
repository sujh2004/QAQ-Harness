package com.devpilot.runtime.lifecycle;

/**
 * Why a turn reached its terminal status.
 *
 * <p>Every started turn must end with one of these reasons, including the cancellation and
 * service-restart paths. The runtime never leaves a turn open.
 */
public enum TurnEndReason {
    /** The runtime produced its final answer. */
    COMPLETED(TurnStatus.COMPLETED),
    /** The runtime stopped because of an error it could not recover from. */
    FAILED(TurnStatus.FAILED),
    /** The user cancelled the turn. */
    ABORTED_BY_USER(TurnStatus.CANCELLED),
    /** The service restarted while the turn was still running. */
    ABORTED_BY_RESTART(TurnStatus.CANCELLED),
    /** The turn exceeded its allowed execution time. */
    ABORTED_BY_TIMEOUT(TurnStatus.FAILED);

    private final TurnStatus resultingStatus;

    TurnEndReason(TurnStatus resultingStatus) {
        this.resultingStatus = resultingStatus;
    }

    /** @return terminal status implied by this reason */
    public TurnStatus resultingStatus() {
        return resultingStatus;
    }
}
