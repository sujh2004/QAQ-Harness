package com.devpilot.runtime.tool;

/**
 * What a provider receives when the pipeline has accepted a call.
 *
 * <p>The provider only sees validated arguments and the identifiers it needs for logging. It has no
 * access to the event store, the SSE stream or the agent lifecycle.
 *
 * <p>{@code projectId} is the project the session belongs to, not something the model supplied. A
 * tool whose arguments also name a project must check the two agree, otherwise a model could read
 * across project boundaries.
 *
 * @param sessionId owning session
 * @param projectId project the session belongs to, may be null outside a project context
 * @param turnId owning turn
 * @param stepId owning step, may be null
 * @param runId owning agent run, may be null
 * @param callId identifier shared by the model summary, the persisted record and the UI projection
 * @param agentName agent that requested the call
 * @param definition declaration of the tool being executed
 * @param arguments validated arguments bound to {@link ToolDefinition#argumentType()}
 * @param <A> argument type
 */
public record ToolExecutionContext<A>(
        String sessionId,
        Long projectId,
        String turnId,
        String stepId,
        String runId,
        String callId,
        String agentName,
        ToolDefinition definition,
        A arguments) {
}
