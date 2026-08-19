package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.StepStatus;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.lifecycle.TurnStatus;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.payload.AgentFinishedPayload;
import com.devpilot.runtime.session.payload.AgentStartedPayload;
import com.devpilot.runtime.session.payload.AssistantMessagePayload;
import com.devpilot.runtime.session.payload.SessionCreatedPayload;
import com.devpilot.runtime.session.payload.StepEndedPayload;
import com.devpilot.runtime.session.payload.StepStartedPayload;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.devpilot.runtime.session.payload.ToolCallRequestedPayload;
import com.devpilot.runtime.session.payload.TurnEndedPayload;
import com.devpilot.runtime.session.payload.UnknownPayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds a session event stream into {@link SessionProjection}.
 *
 * <p>This is the only place that interprets the meaning of the event log. Both the live runtime and
 * a cold replay call it, which is what guarantees they agree.
 */
@Component
public class SessionProjector {

    /**
     * Projects a complete event stream.
     *
     * @param sessionId owning session
     * @param events every event of the session, ordered by sequence number starting at 1
     * @return folded session state
     * @throws IllegalStateException when the stream references a turn, step, run or call that was
     *     never opened, which means the caller passed a partial or corrupted stream
     */
    public SessionProjection project(String sessionId, List<SessionEvent> events) {
        if (events.isEmpty()) {
            return SessionProjection.empty(sessionId);
        }

        Long projectId = null;
        String profileVersion = null;
        List<String> capabilities = List.of();
        String title = null;

        List<MessageView> messages = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, MutableTurn> turns = new LinkedHashMap<>();
        Map<String, MutableStep> steps = new LinkedHashMap<>();
        Map<String, MutableRun> runs = new LinkedHashMap<>();
        Map<String, MutableCall> calls = new LinkedHashMap<>();

        long lastSeq = 0L;
        for (SessionEvent event : events) {
            lastSeq = event.seq();
            if (event.payload() instanceof UnknownPayload unknown) {
                skipped.add(unknown.eventType() + "@v" + unknown.schemaVersion());
                continue;
            }

            switch (event.eventType()) {
                case SESSION_CREATED -> {
                    SessionCreatedPayload payload = event.payloadAs(SessionCreatedPayload.class);
                    projectId = payload.projectId();
                    profileVersion = payload.profileVersion();
                    capabilities = payload.capabilities() == null ? List.of() : List.copyOf(payload.capabilities());
                    title = payload.title();
                }
                case TURN_STARTED -> {
                    MutableTurn turn = new MutableTurn();
                    turn.turnId = event.turnId();
                    turn.startedAt = event.occurredAt();
                    turns.put(event.turnId(), turn);
                }
                case TURN_ENDED -> {
                    TurnEndedPayload payload = event.payloadAs(TurnEndedPayload.class);
                    MutableTurn turn = require(turns, event.turnId(), "turn", event);
                    turn.status = payload.status();
                    turn.endReason = payload.reason();
                    turn.detail = payload.detail();
                    turn.endedAt = event.occurredAt();
                }
                case STEP_STARTED -> {
                    StepStartedPayload payload = event.payloadAs(StepStartedPayload.class);
                    MutableTurn turn = require(turns, event.turnId(), "turn", event);
                    MutableStep step = new MutableStep();
                    step.stepId = event.stepId();
                    step.turnId = event.turnId();
                    step.index = payload.index();
                    step.startedAt = event.occurredAt();
                    steps.put(event.stepId(), step);
                    turn.steps.add(step);
                }
                case STEP_ENDED -> {
                    StepEndedPayload payload = event.payloadAs(StepEndedPayload.class);
                    MutableStep step = require(steps, event.stepId(), "step", event);
                    step.status = payload.status();
                    step.detail = payload.detail();
                    step.endedAt = event.occurredAt();
                }
                case USER_MESSAGE -> {
                    UserMessagePayload payload = event.payloadAs(UserMessagePayload.class);
                    messages.add(new MessageView(
                            event.seq(), MessageRole.USER, null, payload.content(), event.occurredAt()));
                }
                case ASSISTANT_MESSAGE -> {
                    AssistantMessagePayload payload = event.payloadAs(AssistantMessagePayload.class);
                    messages.add(new MessageView(
                            event.seq(),
                            MessageRole.ASSISTANT,
                            payload.agentName(),
                            payload.content(),
                            event.occurredAt()));
                }
                case AGENT_STARTED -> {
                    AgentStartedPayload payload = event.payloadAs(AgentStartedPayload.class);
                    MutableRun run = new MutableRun();
                    run.runId = event.runId();
                    run.turnId = event.turnId();
                    run.stepId = event.stepId();
                    run.parentRunId = payload.parentRunId();
                    run.agentName = payload.agentName();
                    run.displayName = payload.displayName();
                    run.startedAt = event.occurredAt();
                    runs.put(event.runId(), run);
                }
                case AGENT_FINISHED -> {
                    AgentFinishedPayload payload = event.payloadAs(AgentFinishedPayload.class);
                    MutableRun run = require(runs, event.runId(), "agent run", event);
                    run.status = payload.status();
                    run.outputSummary = payload.outputSummary();
                    run.errorMessage = payload.errorMessage();
                    run.endedAt = event.occurredAt();
                }
                case TOOL_CALL_REQUESTED -> {
                    ToolCallRequestedPayload payload = event.payloadAs(ToolCallRequestedPayload.class);
                    MutableCall call = new MutableCall();
                    call.callId = event.callId();
                    call.turnId = event.turnId();
                    call.stepId = event.stepId();
                    call.runId = event.runId();
                    call.agentName = payload.agentName();
                    call.toolName = payload.toolName();
                    call.requestSummary = payload.requestSummary();
                    call.startedAt = event.occurredAt();
                    calls.put(event.callId(), call);
                }
                case TOOL_CALL_FINISHED -> {
                    ToolCallFinishedPayload payload = event.payloadAs(ToolCallFinishedPayload.class);
                    MutableCall call = require(calls, event.callId(), "tool call", event);
                    call.status = payload.status();
                    call.errorCode = payload.errorCode();
                    call.message = payload.message();
                    call.resultSummary = payload.resultSummary();
                    call.truncated = payload.truncated();
                    call.durationMs = payload.durationMs();
                    call.endedAt = event.occurredAt();
                }
                // Deltas are a presentation detail of an assistant message, approvals are visible on
                // the raw stream, and runtime errors are informational: none of them change the
                // projected lifecycle state.
                case ASSISTANT_DELTA, APPROVAL_REQUESTED, APPROVAL_RESOLVED, RUNTIME_ERROR -> {
                }
            }
        }

        return new SessionProjection(
                sessionId,
                lastSeq,
                projectId,
                profileVersion,
                capabilities,
                title,
                List.copyOf(messages),
                turns.values().stream().map(MutableTurn::toView).toList(),
                runs.values().stream().map(MutableRun::toView).toList(),
                calls.values().stream().map(MutableCall::toView).toList(),
                List.copyOf(skipped));
    }

    private static <T> T require(Map<String, T> known, String id, String label, SessionEvent event) {
        T value = known.get(id);
        if (value == null) {
            throw new IllegalStateException("Event " + event.eventType().wireName() + "#" + event.seq()
                    + " references unknown " + label + " '" + id + "'; project() needs the complete stream");
        }
        return value;
    }

    private static final class MutableTurn {
        private String turnId;
        private TurnStatus status = TurnStatus.RUNNING;
        private TurnEndReason endReason;
        private String detail;
        private Instant startedAt;
        private Instant endedAt;
        private final List<MutableStep> steps = new ArrayList<>();

        private TurnView toView() {
            return new TurnView(
                    turnId, status, endReason, detail, startedAt, endedAt,
                    steps.stream().map(MutableStep::toView).toList());
        }
    }

    private static final class MutableStep {
        private String stepId;
        private String turnId;
        private int index;
        private StepStatus status = StepStatus.RUNNING;
        private String detail;
        private Instant startedAt;
        private Instant endedAt;

        private StepView toView() {
            return new StepView(stepId, turnId, index, status, detail, startedAt, endedAt);
        }
    }

    private static final class MutableRun {
        private String runId;
        private String turnId;
        private String stepId;
        private String parentRunId;
        private String agentName;
        private String displayName;
        private RunStatus status = RunStatus.RUNNING;
        private String outputSummary;
        private String errorMessage;
        private Instant startedAt;
        private Instant endedAt;

        private AgentRunView toView() {
            return new AgentRunView(
                    runId, turnId, stepId, parentRunId, agentName, displayName,
                    status, outputSummary, errorMessage, startedAt, endedAt);
        }
    }

    private static final class MutableCall {
        private String callId;
        private String turnId;
        private String stepId;
        private String runId;
        private String agentName;
        private String toolName;
        private ToolCallStatus status = ToolCallStatus.REQUESTED;
        private ToolErrorCode errorCode;
        private String message;
        private String requestSummary;
        private String resultSummary;
        private boolean truncated;
        private long durationMs;
        private Instant startedAt;
        private Instant endedAt;

        private ToolCallView toView() {
            return new ToolCallView(
                    callId, turnId, stepId, runId, agentName, toolName, status, errorCode, message,
                    requestSummary, resultSummary, truncated, durationMs, startedAt, endedAt);
        }
    }
}
