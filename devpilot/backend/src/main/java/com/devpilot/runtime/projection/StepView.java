package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.StepStatus;

import java.time.Instant;

/**
 * Projected state of one step.
 *
 * <p>A step belongs to the agent run that made the model request, not merely to the turn. That is
 * what lets a supervisor keep its own step open while a specialist agent it delegated to opens
 * steps of its own.
 *
 * @param stepId step identifier
 * @param turnId owning turn
 * @param runId agent run that opened the step, null for a step opened outside any run
 * @param index zero-based position inside the turn
 * @param status current step status
 * @param detail explanation recorded when the step ended, null while running
 * @param startedAt when the step started
 * @param endedAt when the step ended, null while running
 */
public record StepView(
        String stepId,
        String turnId,
        String runId,
        int index,
        StepStatus status,
        String detail,
        Instant startedAt,
        Instant endedAt) {
}
