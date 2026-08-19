package com.devpilot.chat.model;

import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.projection.AgentRunView;

import java.time.Instant;

/**
 * One agent run of a session, projected from the event log.
 *
 * @param runId run identifier
 * @param turnId owning turn
 * @param parentRunId calling run, null for the outermost run
 * @param agentName agent that executed the run
 * @param displayName label shown in the UI
 * @param status current run status
 * @param outputSummary short description of the produced result
 * @param errorMessage safe failure message, null unless the run failed
 * @param startedAt when the run started
 * @param endedAt when the run ended, null while running
 */
public record AgentRunResponse(
        String runId,
        String turnId,
        String parentRunId,
        String agentName,
        String displayName,
        RunStatus status,
        String outputSummary,
        String errorMessage,
        Instant startedAt,
        Instant endedAt) {

    /**
     * Converts a projected run.
     *
     * @param view projected run
     * @return API representation
     */
    public static AgentRunResponse from(AgentRunView view) {
        return new AgentRunResponse(
                view.runId(), view.turnId(), view.parentRunId(), view.agentName(), view.displayName(),
                view.status(), view.outputSummary(), view.errorMessage(), view.startedAt(), view.endedAt());
    }
}
