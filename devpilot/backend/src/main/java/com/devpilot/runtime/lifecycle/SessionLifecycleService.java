package com.devpilot.runtime.lifecycle;

import com.devpilot.runtime.projection.AgentRunView;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.projection.SessionProjector;
import com.devpilot.runtime.projection.StepView;
import com.devpilot.runtime.projection.ToolCallView;
import com.devpilot.runtime.projection.TurnView;
import com.devpilot.runtime.session.AppendEventCommand;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.SessionStreamDescriptor;
import com.devpilot.runtime.session.payload.AgentFinishedPayload;
import com.devpilot.runtime.session.payload.AgentStartedPayload;
import com.devpilot.runtime.session.payload.AssistantDeltaPayload;
import com.devpilot.runtime.session.payload.AssistantMessagePayload;
import com.devpilot.runtime.session.payload.RuntimeErrorPayload;
import com.devpilot.runtime.session.payload.SessionCreatedPayload;
import com.devpilot.runtime.session.payload.StepEndedPayload;
import com.devpilot.runtime.session.payload.StepStartedPayload;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.devpilot.runtime.session.payload.TurnEndedPayload;
import com.devpilot.runtime.session.payload.TurnStartedPayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the {@code session → turn → step → model/tool} lifecycle by appending events.
 *
 * <p>The service keeps no mutable state of its own. Every decision is taken against the projection
 * of the committed event stream, so a restarted process reaches the same conclusions as the process
 * that wrote the events. Illegal transitions are rejected instead of being silently absorbed, and
 * every started turn, step, run and tool call is guaranteed a terminal event.
 */
@Service
public class SessionLifecycleService {

    private static final int SUMMARY_LIMIT = 200;

    private final SessionEventStore eventStore;
    private final SessionProjector projector;

    /**
     * Creates the lifecycle service.
     *
     * @param eventStore append-only event storage
     * @param projector event stream folder
     */
    public SessionLifecycleService(SessionEventStore eventStore, SessionProjector projector) {
        this.eventStore = eventStore;
        this.projector = projector;
    }

    /**
     * Opens a session stream and records its pinned runtime configuration.
     *
     * @param descriptor session identity and configuration
     * @return projection of the new session
     */
    @Transactional
    public SessionProjection createSession(SessionStreamDescriptor descriptor) {
        eventStore.createStream(descriptor);
        eventStore.append(descriptor.sessionId(), AppendEventCommand.ofSession(
                SessionEventType.SESSION_CREATED,
                new SessionCreatedPayload(
                        descriptor.projectId(),
                        descriptor.profileVersion(),
                        descriptor.capabilities(),
                        descriptor.title())));
        return project(descriptor.sessionId());
    }

    /**
     * Starts a turn, optionally recording the user input that triggered it in the same commit.
     *
     * @param sessionId owning session
     * @param trigger what started the turn, for example {@code USER}
     * @param userMessage user input, null when the turn is system-triggered
     * @return identifier of the new turn
     * @throws IllegalLifecycleTransitionException when the session already has a running turn
     */
    @Transactional
    public String startTurn(String sessionId, String trigger, String userMessage) {
        SessionProjection projection = project(sessionId);
        projection.activeTurn().ifPresent(active -> {
            throw new IllegalLifecycleTransitionException(
                    "Session " + sessionId + " already has running turn " + active.turnId());
        });

        String turnId = RuntimeIds.newTurnId();
        List<AppendEventCommand> commands = new ArrayList<>(2);
        commands.add(AppendEventCommand.ofTurn(
                SessionEventType.TURN_STARTED, turnId, new TurnStartedPayload(trigger, summarize(userMessage))));
        if (userMessage != null) {
            commands.add(AppendEventCommand.ofTurn(
                    SessionEventType.USER_MESSAGE, turnId, new UserMessagePayload(userMessage)));
        }
        eventStore.append(sessionId, commands);
        return turnId;
    }

    /**
     * Starts a step inside a running turn.
     *
     * @param sessionId owning session
     * @param turnId owning turn
     * @param purpose short description of what the step is for
     * @return identifier of the new step
     * @throws IllegalLifecycleTransitionException when the turn is not running or already has a
     *     running step
     */
    @Transactional
    public String startStep(String sessionId, String turnId, String purpose) {
        return startStep(sessionId, turnId, null, purpose);
    }

    /**
     * Starts a step inside a running turn, owned by one agent run.
     *
     * <p>The "one step at a time" rule is scoped to the run, not the turn: a supervisor holds its
     * own step open while a specialist it delegated to opens steps of its own.
     *
     * @param sessionId owning session
     * @param turnId owning turn
     * @param runId agent run opening the step, null when no run owns it
     * @param purpose short description of what the step is for
     * @return identifier of the new step
     * @throws IllegalLifecycleTransitionException when the turn is not running or the same run
     *     already has a running step
     */
    @Transactional
    public String startStep(String sessionId, String turnId, String runId, String purpose) {
        SessionProjection projection = project(sessionId);
        TurnView turn = requireRunningTurn(projection, sessionId, turnId);
        if (!projection.openSteps(turnId, runId).isEmpty()) {
            throw new IllegalLifecycleTransitionException(
                    "Agent run " + runId + " already has a running step in turn " + turnId);
        }

        String stepId = RuntimeIds.newStepId();
        eventStore.append(sessionId, AppendEventCommand.ofRun(
                SessionEventType.STEP_STARTED, turnId, stepId, runId,
                new StepStartedPayload(turn.steps().size(), purpose)));
        return stepId;
    }

    /**
     * Closes a running step.
     *
     * @param sessionId owning session
     * @param stepId step to close
     * @param status terminal status
     * @param detail safe explanation
     * @throws IllegalLifecycleTransitionException when the step is unknown or already terminal
     */
    @Transactional
    public void endStep(String sessionId, String stepId, StepStatus status, String detail) {
        SessionProjection projection = project(sessionId);
        StepView step = projection.step(stepId).orElseThrow(() -> new IllegalLifecycleTransitionException(
                "Unknown step " + stepId + " in session " + sessionId));
        if (!step.status().canTransitionTo(status)) {
            throw new IllegalLifecycleTransitionException(
                    "Step " + stepId + " cannot move from " + step.status() + " to " + status);
        }
        eventStore.append(sessionId, AppendEventCommand.ofRun(
                SessionEventType.STEP_ENDED, step.turnId(), stepId, step.runId(),
                new StepEndedPayload(status, detail)));
    }

    /**
     * Starts an agent run inside a running turn.
     *
     * @param sessionId owning session
     * @param turnId owning turn
     * @param stepId owning step, may be null
     * @param parentRunId calling run, null for the outermost run
     * @param agentName stable agent identifier
     * @param displayName label shown in the UI
     * @param inputSummary short description of the delegated task
     * @return identifier of the new run
     * @throws IllegalLifecycleTransitionException when the turn is not running
     */
    @Transactional
    public String startAgentRun(
            String sessionId,
            String turnId,
            String stepId,
            String parentRunId,
            String agentName,
            String displayName,
            String inputSummary) {
        SessionProjection projection = project(sessionId);
        requireRunningTurn(projection, sessionId, turnId);

        String runId = RuntimeIds.newRunId();
        eventStore.append(sessionId, AppendEventCommand.ofRun(
                SessionEventType.AGENT_STARTED, turnId, stepId, runId,
                // The input is recorded verbatim: for a delegated run it is the task the specialist
                // will read as its instruction, so truncating it would change what the model sees.
                new AgentStartedPayload(agentName, displayName, parentRunId, inputSummary)));
        return runId;
    }

    /**
     * Closes a running agent run.
     *
     * @param sessionId owning session
     * @param runId run to close
     * @param status terminal status
     * @param outputSummary short description of the produced result
     * @param errorMessage safe failure message, null when the run succeeded
     * @throws IllegalLifecycleTransitionException when the run is unknown or already terminal
     */
    @Transactional
    public void finishAgentRun(
            String sessionId, String runId, RunStatus status, String outputSummary, String errorMessage) {
        SessionProjection projection = project(sessionId);
        AgentRunView run = projection.run(runId).orElseThrow(() -> new IllegalLifecycleTransitionException(
                "Unknown agent run " + runId + " in session " + sessionId));
        if (!run.status().canTransitionTo(status)) {
            throw new IllegalLifecycleTransitionException(
                    "Agent run " + runId + " cannot move from " + run.status() + " to " + status);
        }
        eventStore.append(sessionId, AppendEventCommand.ofRun(
                SessionEventType.AGENT_FINISHED, run.turnId(), run.stepId(), runId,
                new AgentFinishedPayload(status, summarize(outputSummary), errorMessage)));
    }

    /**
     * Records a streamed fragment of an assistant message.
     *
     * @param sessionId owning session
     * @param turnId owning turn
     * @param stepId owning step, may be null
     * @param content text fragment
     */
    @Transactional
    public void recordAssistantDelta(String sessionId, String turnId, String stepId, String content) {
        eventStore.append(sessionId, AppendEventCommand.ofStep(
                SessionEventType.ASSISTANT_DELTA, turnId, stepId, new AssistantDeltaPayload(content)));
    }

    /**
     * Records a complete assistant message.
     *
     * @param sessionId owning session
     * @param turnId owning turn
     * @param stepId owning step, may be null
     * @param agentName producing agent
     * @param content message text
     * @throws IllegalLifecycleTransitionException when the turn is not running
     */
    @Transactional
    public void recordAssistantMessage(
            String sessionId, String turnId, String stepId, String agentName, String content) {
        requireRunningTurn(project(sessionId), sessionId, turnId);
        eventStore.append(sessionId, AppendEventCommand.ofStep(
                SessionEventType.ASSISTANT_MESSAGE, turnId, stepId,
                new AssistantMessagePayload(agentName, content)));
    }

    /**
     * Records a runtime failure so a reloaded page can still explain what went wrong.
     *
     * @param sessionId owning session
     * @param turnId owning turn, may be null
     * @param errorCode stable error identifier
     * @param message safe failure message without stack traces
     * @param scope where the failure happened
     */
    @Transactional
    public void recordRuntimeError(
            String sessionId, String turnId, String errorCode, String message, String scope) {
        eventStore.append(sessionId, AppendEventCommand.ofTurn(
                SessionEventType.RUNTIME_ERROR, turnId, new RuntimeErrorPayload(errorCode, message, scope)));
    }

    /**
     * Closes a running turn, first closing any tool call, agent run and step it left open.
     *
     * <p>The whole closure is one atomic append, so a crash can never leave half of a turn closed.
     *
     * @param sessionId owning session
     * @param turnId turn to close
     * @param reason why the turn ends
     * @param detail safe explanation
     * @return projected state of the closed turn
     * @throws IllegalLifecycleTransitionException when the turn is unknown or already terminal
     */
    @Transactional
    public TurnView endTurn(String sessionId, String turnId, TurnEndReason reason, String detail) {
        SessionProjection projection = project(sessionId);
        TurnView turn = requireRunningTurn(projection, sessionId, turnId);
        eventStore.append(sessionId, closeTurnCommands(projection, turn, reason, detail));
        return project(sessionId).turn(turnId).orElseThrow();
    }

    /**
     * Cancels a turn on behalf of the user.
     *
     * <p>Cancellation is idempotent: cancelling an already finished turn returns its current
     * terminal state without appending anything.
     *
     * @param sessionId owning session
     * @param turnId turn to cancel
     * @return projected state of the turn
     * @throws IllegalLifecycleTransitionException when the turn is unknown
     */
    @Transactional
    public TurnView cancelTurn(String sessionId, String turnId) {
        SessionProjection projection = project(sessionId);
        TurnView turn = projection.turn(turnId).orElseThrow(() -> new IllegalLifecycleTransitionException(
                "Unknown turn " + turnId + " in session " + sessionId));
        if (turn.status().terminal()) {
            return turn;
        }
        eventStore.append(sessionId, closeTurnCommands(
                projection, turn, TurnEndReason.ABORTED_BY_USER, "Cancelled by user"));
        return project(sessionId).turn(turnId).orElseThrow();
    }

    /**
     * Folds the committed event stream of a session.
     *
     * @param sessionId owning session
     * @return current session state
     */
    @Transactional(readOnly = true)
    public SessionProjection project(String sessionId) {
        return projector.project(sessionId, eventStore.readAll(sessionId));
    }

    private List<AppendEventCommand> closeTurnCommands(
            SessionProjection projection, TurnView turn, TurnEndReason reason, String detail) {
        boolean failed = reason.resultingStatus() == TurnStatus.FAILED;
        String childDetail = "Closed with turn: " + reason.name();
        String turnId = turn.turnId();
        List<AppendEventCommand> commands = new ArrayList<>();

        for (ToolCallView call : projection.openToolCalls(turnId)) {
            commands.add(AppendEventCommand.ofCall(
                    SessionEventType.TOOL_CALL_FINISHED, turnId, call.stepId(), call.runId(), call.callId(),
                    new ToolCallFinishedPayload(
                            call.agentName(),
                            call.toolName(),
                            ToolCallStatus.CANCELLED,
                            ToolErrorCode.ABORTED,
                            childDetail,
                            null,
                            false,
                            0L)));
        }
        for (AgentRunView run : projection.openRuns(turnId)) {
            commands.add(AppendEventCommand.ofRun(
                    SessionEventType.AGENT_FINISHED, turnId, run.stepId(), run.runId(),
                    new AgentFinishedPayload(
                            failed ? RunStatus.FAILED : RunStatus.CANCELLED, null, childDetail)));
        }
        for (StepView step : projection.openSteps(turnId)) {
            commands.add(AppendEventCommand.ofRun(
                    SessionEventType.STEP_ENDED, turnId, step.stepId(), step.runId(),
                    new StepEndedPayload(failed ? StepStatus.FAILED : StepStatus.CANCELLED, childDetail)));
        }
        commands.add(AppendEventCommand.ofTurn(
                SessionEventType.TURN_ENDED, turnId,
                new TurnEndedPayload(reason.resultingStatus(), reason, detail)));
        return commands;
    }

    private static TurnView requireRunningTurn(SessionProjection projection, String sessionId, String turnId) {
        TurnView turn = projection.turn(turnId).orElseThrow(() -> new IllegalLifecycleTransitionException(
                "Unknown turn " + turnId + " in session " + sessionId));
        if (turn.status().terminal()) {
            throw new IllegalLifecycleTransitionException(
                    "Turn " + turnId + " already ended with status " + turn.status());
        }
        return turn;
    }

    private static String summarize(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "…";
    }
}
