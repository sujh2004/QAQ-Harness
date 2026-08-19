package com.devpilot.runtime.session.payload;

/**
 * Starts a turn.
 *
 * @param trigger what started the turn, for example {@code USER} or {@code SYSTEM}
 * @param inputSummary short description of the triggering input
 */
public record TurnStartedPayload(String trigger, String inputSummary) implements SessionEventPayload {
}
