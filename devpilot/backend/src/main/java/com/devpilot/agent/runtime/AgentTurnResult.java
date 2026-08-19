package com.devpilot.agent.runtime;

import com.devpilot.runtime.lifecycle.RunStatus;

/**
 * What an agent produced for a turn.
 *
 * <p>Only visible output is returned. Hidden reasoning is neither surfaced nor stored.
 *
 * @param runId run the agent executed under
 * @param status terminal run status
 * @param finalMessage visible answer, null when the run failed
 * @param errorMessage safe failure message, null when the run succeeded
 */
public record AgentTurnResult(String runId, RunStatus status, String finalMessage, String errorMessage) {
}
