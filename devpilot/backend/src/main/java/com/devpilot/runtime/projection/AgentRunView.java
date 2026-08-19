package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.RunStatus;

import java.time.Instant;

/**
 * Projected state of one agent run. Runs form a tree through {@code parentRunId}, which is the
 * audit view of supervisor delegation.
 *
 * @param runId run identifier
 * @param turnId owning turn
 * @param stepId owning step, null when the run is not step-scoped
 * @param parentRunId calling run, null for the outermost run
 * @param agentName agent that executed the run
 * @param displayName label shown in the UI
 * @param status current run status
 * @param outputSummary short description of the produced result, null while running
 * @param errorMessage safe failure message, null unless the run failed
 * @param startedAt when the run started
 * @param endedAt when the run ended, null while running
 */
public record AgentRunView(
        String runId,
        String turnId,
        String stepId,
        String parentRunId,
        String agentName,
        String displayName,
        RunStatus status,
        String outputSummary,
        String errorMessage,
        Instant startedAt,
        Instant endedAt) {
}
