package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.StepStatus;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.TurnStatus;

import java.util.List;
import java.util.Optional;

/**
 * Everything the runtime and the UI need to know about a session, folded from its event stream.
 *
 * <p>The lifecycle service and the audit views share this projection, so "current state" and
 * "state rebuilt from the log" are produced by the same function and cannot drift apart.
 *
 * @param sessionId owning session
 * @param lastSeq sequence number of the last folded event, zero for an empty stream
 * @param projectId owning project, null until the project module exists
 * @param profileVersion agent profile version pinned when the session was created
 * @param capabilities capability identifiers available to the session
 * @param title human-readable session title
 * @param messages chat timeline in order
 * @param turns turns in start order
 * @param runs agent runs in start order
 * @param toolCalls tool calls in request order
 * @param skippedEvents descriptions of events this build could not decode and safely skipped
 */
public record SessionProjection(
        String sessionId,
        long lastSeq,
        Long projectId,
        String profileVersion,
        List<String> capabilities,
        String title,
        List<MessageView> messages,
        List<TurnView> turns,
        List<AgentRunView> runs,
        List<ToolCallView> toolCalls,
        List<String> skippedEvents) {

    /**
     * Creates the projection of a stream with no events.
     *
     * @param sessionId owning session
     * @return empty projection
     */
    public static SessionProjection empty(String sessionId) {
        return new SessionProjection(
                sessionId, 0L, null, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Finds the turn that is still running. A session has at most one.
     *
     * @return running turn, empty when the session is idle
     */
    public Optional<TurnView> activeTurn() {
        return turns.stream().filter(turn -> turn.status() == TurnStatus.RUNNING).findFirst();
    }

    /**
     * Finds a turn by identifier.
     *
     * @param turnId turn identifier
     * @return matching turn, empty when the session has no such turn
     */
    public Optional<TurnView> turn(String turnId) {
        return turns.stream().filter(turn -> turn.turnId().equals(turnId)).findFirst();
    }

    /**
     * Lists the steps of a turn that have not reached a terminal status.
     *
     * @param turnId turn identifier
     * @return running steps in start order
     */
    public List<StepView> openSteps(String turnId) {
        return turn(turnId)
                .map(turn -> turn.steps().stream().filter(step -> step.status() == StepStatus.RUNNING).toList())
                .orElseGet(List::of);
    }

    /**
     * Lists the running steps opened by one agent run.
     *
     * <p>Steps are scoped to a run rather than to the turn, so a supervisor may hold a step open
     * while a specialist it delegated to runs steps of its own.
     *
     * @param turnId turn identifier
     * @param runId agent run identifier, null for steps opened outside any run
     * @return running steps of that run, in start order
     */
    public List<StepView> openSteps(String turnId, String runId) {
        return openSteps(turnId).stream()
                .filter(step -> java.util.Objects.equals(step.runId(), runId))
                .toList();
    }

    /**
     * Lists the agent runs of a turn that have not reached a terminal status.
     *
     * @param turnId turn identifier
     * @return running runs in start order
     */
    public List<AgentRunView> openRuns(String turnId) {
        return runs.stream()
                .filter(run -> turnId.equals(run.turnId()) && run.status() == RunStatus.RUNNING)
                .toList();
    }

    /**
     * Lists the tool calls of a turn that have not reached a terminal status.
     *
     * @param turnId turn identifier
     * @return unfinished calls in request order
     */
    public List<ToolCallView> openToolCalls(String turnId) {
        return toolCalls.stream()
                .filter(call -> turnId.equals(call.turnId()) && call.status() == ToolCallStatus.REQUESTED)
                .toList();
    }

    /**
     * Finds a tool call by identifier.
     *
     * @param callId call identifier
     * @return matching call, empty when the session has no such call
     */
    public Optional<ToolCallView> toolCall(String callId) {
        return toolCalls.stream().filter(call -> call.callId().equals(callId)).findFirst();
    }

    /**
     * Finds a step by identifier across every turn of the session.
     *
     * @param stepId step identifier
     * @return matching step, empty when the session has no such step
     */
    public Optional<StepView> step(String stepId) {
        return turns.stream()
                .flatMap(turn -> turn.steps().stream())
                .filter(step -> step.stepId().equals(stepId))
                .findFirst();
    }

    /**
     * Finds an agent run by identifier.
     *
     * @param runId run identifier
     * @return matching run, empty when the session has no such run
     */
    public Optional<AgentRunView> run(String runId) {
        return runs.stream().filter(run -> run.runId().equals(runId)).findFirst();
    }
}
