package com.devpilot.agent.runtime;

/**
 * One request for an agent to work on a turn.
 *
 * <p>The turn is already open when this arrives: the caller records the user input and decides how
 * the turn ends. The agent owns its own run and the steps inside it.
 *
 * <p>Neither the tool scope nor the profile version is passed in. Both come from the profile the
 * session was pinned to, so a caller cannot widen what an agent may do.
 *
 * @param sessionId owning session
 * @param projectId project the session belongs to
 * @param turnId turn the agent works in
 * @param agentName agent that should run
 * @param userMessage input to work on
 * @param parentRunId run that delegated this work, null for the outermost agent
 */
public record AgentTurnRequest(
        String sessionId,
        Long projectId,
        String turnId,
        String agentName,
        String userMessage,
        String parentRunId) {

    /**
     * Builds a request for an agent nobody delegated to.
     *
     * @param sessionId owning session
     * @param projectId project the session belongs to
     * @param turnId turn the agent works in
     * @param agentName agent that should run
     * @param userMessage input to work on
     * @return top-level agent request
     */
    public static AgentTurnRequest of(
            String sessionId, Long projectId, String turnId, String agentName, String userMessage) {
        return new AgentTurnRequest(sessionId, projectId, turnId, agentName, userMessage, null);
    }
}
