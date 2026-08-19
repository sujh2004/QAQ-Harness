package com.devpilot.runtime.session.payload;

/**
 * Starts a step, which covers at most one model request and the tool calls it produces.
 *
 * @param index zero-based position of the step inside its turn
 * @param purpose short description of what the step is for
 */
public record StepStartedPayload(int index, String purpose) implements SessionEventPayload {
}
