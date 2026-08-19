package com.devpilot.chat.model;

import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.lifecycle.TurnStatus;
import com.devpilot.runtime.projection.TurnView;

import java.time.Instant;

/**
 * State of one turn, projected from the event log.
 *
 * @param turnId turn identifier
 * @param status current turn status
 * @param endReason why the turn ended, null while running
 * @param detail explanation recorded when the turn ended
 * @param startedAt when the turn started
 * @param endedAt when the turn ended, null while running
 */
public record TurnResponse(
        String turnId,
        TurnStatus status,
        TurnEndReason endReason,
        String detail,
        Instant startedAt,
        Instant endedAt) {

    /**
     * Converts a projected turn.
     *
     * @param view projected turn
     * @return API representation
     */
    public static TurnResponse from(TurnView view) {
        return new TurnResponse(
                view.turnId(), view.status(), view.endReason(), view.detail(),
                view.startedAt(), view.endedAt());
    }
}
