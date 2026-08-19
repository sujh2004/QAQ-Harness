package com.devpilot.agent.runtime;

/**
 * Runs an agent for one turn.
 *
 * <p>The interface is owned by DevPilot, not by a framework: Spring AI Alibaba will implement it as
 * a provider in a later phase, and controllers and application services keep depending on this
 * contract instead of a vendor type. An implementation must drive the turn through
 * {@code SessionLifecycleService} and reach tools only through {@code ToolRegistry}, so every piece
 * of model-visible state is recorded before the next model request sees it.
 */
public interface AgentRuntime {

    /**
     * Executes one turn.
     *
     * @param request session, turn, agent and effective scope
     * @return terminal outcome of the agent run
     */
    AgentTurnResult runTurn(AgentTurnRequest request);
}
