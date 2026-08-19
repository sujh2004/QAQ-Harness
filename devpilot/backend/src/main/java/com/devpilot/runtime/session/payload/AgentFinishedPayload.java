package com.devpilot.runtime.session.payload;

import com.devpilot.runtime.lifecycle.RunStatus;

/**
 * Closes one agent run.
 *
 * @param status terminal run status
 * @param outputSummary short description of what the agent produced
 * @param errorMessage safe failure message, null when the run succeeded
 */
public record AgentFinishedPayload(RunStatus status, String outputSummary, String errorMessage)
        implements SessionEventPayload {
}
