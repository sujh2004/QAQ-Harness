package com.devpilot.runtime.session.payload;

/**
 * Starts one agent run. Nested runs reference their caller so the audit trail forms a tree.
 *
 * @param agentName stable agent identifier
 * @param displayName label shown in the UI
 * @param parentRunId caller run id, null for the outermost run
 * @param inputSummary short description of the delegated task
 */
public record AgentStartedPayload(
        String agentName,
        String displayName,
        String parentRunId,
        String inputSummary) implements SessionEventPayload {
}
