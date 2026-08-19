package com.devpilot.runtime;

import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.session.AppendEventCommand;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.SessionStreamDescriptor;
import com.devpilot.runtime.session.payload.ToolCallRequestedPayload;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared helpers for the runtime contract tests. */
public final class RuntimeTestFixtures {

    /** Profile version pinned by the test sessions. */
    public static final String PROFILE_VERSION = "standard@1";

    private RuntimeTestFixtures() {
    }

    /**
     * Builds a session descriptor for a test.
     *
     * @param sessionId session identifier
     * @param title session title
     * @return descriptor pinned to the test profile
     */
    public static SessionStreamDescriptor descriptor(String sessionId, String title) {
        return new SessionStreamDescriptor(sessionId, 1L, PROFILE_VERSION, List.of("code", "log"), title);
    }

    /**
     * Appends a {@code tool_call_requested} event without a matching terminal event, simulating a
     * process that died in the middle of a tool call.
     *
     * @param eventStore event storage
     * @param sessionId owning session
     * @param turnId owning turn
     * @param stepId owning step
     * @param runId owning agent run
     * @param callId call identifier
     */
    public static void appendPendingToolCall(
            SessionEventStore eventStore,
            String sessionId,
            String turnId,
            String stepId,
            String runId,
            String callId) {
        eventStore.append(sessionId, AppendEventCommand.ofCall(
                SessionEventType.TOOL_CALL_REQUESTED, turnId, stepId, runId, callId,
                new ToolCallRequestedPayload(
                        "log_agent", "searchLogs", "1", Map.of("level", "ERROR"), "searchLogs(level)")));
    }

    /**
     * Asserts that no turn, step, agent run or tool call is still open.
     *
     * @param projection projection to check
     */
    public static void assertNothingLeftOpen(SessionProjection projection) {
        assertThat(projection.turns())
                .allSatisfy(turn -> assertThat(turn.status().terminal())
                        .as("turn %s is closed", turn.turnId()).isTrue());
        assertThat(projection.turns().stream().flatMap(turn -> turn.steps().stream()).toList())
                .allSatisfy(step -> assertThat(step.status().terminal())
                        .as("step %s is closed", step.stepId()).isTrue());
        assertThat(projection.runs())
                .allSatisfy(run -> assertThat(run.status().terminal())
                        .as("run %s is closed", run.runId()).isTrue());
        assertThat(projection.toolCalls())
                .allSatisfy(call -> assertThat(call.status().terminal())
                        .as("tool call %s is closed", call.callId()).isTrue());
    }
}
