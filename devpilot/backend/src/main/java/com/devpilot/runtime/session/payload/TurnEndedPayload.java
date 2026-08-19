package com.devpilot.runtime.session.payload;

import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.lifecycle.TurnStatus;

/**
 * Closes a turn. Every {@code turn_started} event has exactly one of these, including the
 * cancellation, timeout and restart-recovery paths.
 *
 * @param status terminal turn status
 * @param reason why the turn ended
 * @param detail safe explanation shown in the audit trail
 */
public record TurnEndedPayload(TurnStatus status, TurnEndReason reason, String detail)
        implements SessionEventPayload {
}
