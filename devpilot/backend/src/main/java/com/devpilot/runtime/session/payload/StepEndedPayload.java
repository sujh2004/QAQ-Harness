package com.devpilot.runtime.session.payload;

import com.devpilot.runtime.lifecycle.StepStatus;

/**
 * Closes a step.
 *
 * @param status terminal step status
 * @param detail safe explanation shown in the audit trail
 */
public record StepEndedPayload(StepStatus status, String detail) implements SessionEventPayload {
}
