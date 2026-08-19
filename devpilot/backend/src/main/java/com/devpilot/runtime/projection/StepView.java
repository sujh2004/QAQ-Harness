package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.StepStatus;

import java.time.Instant;

/**
 * Projected state of one step.
 *
 * @param stepId step identifier
 * @param turnId owning turn
 * @param index zero-based position inside the turn
 * @param status current step status
 * @param detail explanation recorded when the step ended, null while running
 * @param startedAt when the step started
 * @param endedAt when the step ended, null while running
 */
public record StepView(
        String stepId,
        String turnId,
        int index,
        StepStatus status,
        String detail,
        Instant startedAt,
        Instant endedAt) {
}
