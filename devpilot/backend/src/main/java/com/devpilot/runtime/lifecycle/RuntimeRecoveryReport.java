package com.devpilot.runtime.lifecycle;

/**
 * Outcome of one restart recovery pass.
 *
 * @param scannedSessions sessions found holding at least one open turn
 * @param closedTurns turns closed with {@link TurnEndReason#ABORTED_BY_RESTART}
 * @param failedSessions sessions whose stream could not be replayed by this build
 */
public record RuntimeRecoveryReport(int scannedSessions, int closedTurns, int failedSessions) {
}
