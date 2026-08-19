package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.lifecycle.TurnStatus;

import java.time.Instant;
import java.util.List;

/**
 * Projected state of one turn.
 *
 * @param turnId turn identifier
 * @param status current turn status
 * @param endReason why the turn ended, null while running
 * @param detail explanation recorded when the turn ended, null while running
 * @param startedAt when the turn started
 * @param endedAt when the turn ended, null while running
 * @param steps steps of this turn in start order
 */
public record TurnView(
        String turnId,
        TurnStatus status,
        TurnEndReason endReason,
        String detail,
        Instant startedAt,
        Instant endedAt,
        List<StepView> steps) {
}
