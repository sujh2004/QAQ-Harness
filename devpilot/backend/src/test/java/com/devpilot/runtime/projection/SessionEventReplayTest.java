package com.devpilot.runtime.projection;

import com.devpilot.runtime.RuntimeTestFixtures;
import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.RuntimeIds;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.StepStatus;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.lifecycle.TurnStatus;
import com.devpilot.runtime.session.AppendEventCommand;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: a stream replayed page by page rebuilds exactly the state the live runtime produced.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionEventReplayTest {

    private static final int PAGE_SIZE = 3;

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private SessionEventStore eventStore;

    @Autowired
    private SessionProjector projector;

    private String sessionId;
    private String completedTurnId;
    private String cancelledTurnId;
    private String runId;
    private String callId;

    @BeforeEach
    void buildSession() {
        sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "replay"));

        completedTurnId = lifecycleService.startTurn(sessionId, "USER", "why does order-service return 500?");
        String firstStep = lifecycleService.startStep(sessionId, completedTurnId, "collect evidence");
        runId = lifecycleService.startAgentRun(
                sessionId, completedTurnId, firstStep, null, "log_agent", "日志分析 Agent", "search errors");
        callId = RuntimeIds.newCallId();
        RuntimeTestFixtures.appendPendingToolCall(eventStore, sessionId, completedTurnId, firstStep, runId, callId);
        eventStore.append(sessionId, AppendEventCommand.ofCall(
                SessionEventType.TOOL_CALL_FINISHED, completedTurnId, firstStep, runId, callId,
                new ToolCallFinishedPayload("log_agent", "searchLogs", ToolCallStatus.SUCCESS, null, null,
                        "18 NullPointerException entries", false, 42L)));
        lifecycleService.finishAgentRun(
                sessionId, runId, RunStatus.COMPLETED, "errors concentrate in OrderService.createOrder", null);
        lifecycleService.endStep(sessionId, firstStep, StepStatus.COMPLETED, "evidence collected");

        String secondStep = lifecycleService.startStep(sessionId, completedTurnId, "write answer");
        lifecycleService.recordAssistantDelta(sessionId, completedTurnId, secondStep, "根据日志与代码分析，");
        lifecycleService.recordAssistantMessage(
                sessionId, completedTurnId, secondStep, null, "supervisor",
                "根据日志与代码分析，优惠券服务返回 null。");
        lifecycleService.endStep(sessionId, secondStep, StepStatus.COMPLETED, "answer written");
        lifecycleService.endTurn(sessionId, completedTurnId, TurnEndReason.COMPLETED, "answered");

        cancelledTurnId = lifecycleService.startTurn(sessionId, "USER", "follow-up");
        lifecycleService.cancelTurn(sessionId, cancelledTurnId);
    }

    @Test
    void pagedReplayRebuildsTheSameEventsAndProjection() {
        List<SessionEvent> full = eventStore.readAll(sessionId);

        List<SessionEvent> paged = new ArrayList<>();
        long cursor = 0L;
        List<SessionEvent> page = eventStore.readAfter(sessionId, cursor, PAGE_SIZE);
        while (!page.isEmpty()) {
            paged.addAll(page);
            cursor = page.getLast().seq();
            page = eventStore.readAfter(sessionId, cursor, PAGE_SIZE);
        }

        assertThat(paged).isEqualTo(full);
        assertThat(projector.project(sessionId, paged)).isEqualTo(projector.project(sessionId, full));
    }

    @Test
    void projectionDescribesWhatTheRuntimeActuallyDid() {
        SessionProjection projection = projector.project(sessionId, eventStore.readAll(sessionId));

        assertThat(projection.turn(completedTurnId).orElseThrow().status()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(projection.turn(completedTurnId).orElseThrow().steps()).hasSize(2);
        assertThat(projection.turn(cancelledTurnId).orElseThrow().endReason())
                .isEqualTo(TurnEndReason.ABORTED_BY_USER);
        assertThat(projection.run(runId).orElseThrow().agentName()).isEqualTo("log_agent");
        assertThat(projection.toolCall(callId).orElseThrow().resultSummary())
                .isEqualTo("18 NullPointerException entries");
        assertThat(projection.messages())
                .extracting(MessageView::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER);
        assertThat(projection.skippedEvents()).isEmpty();
        RuntimeTestFixtures.assertNothingLeftOpen(projection);
    }

    @Test
    void assistantDeltasDoNotDuplicateTheAssistantMessage() {
        SessionProjection projection = projector.project(sessionId, eventStore.readAll(sessionId));

        assertThat(projection.messages())
                .filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(message -> assertThat(message.content()).startsWith("根据日志与代码分析，优惠券服务"));
    }

    @Test
    void refusesToProjectAPartialStream() {
        List<SessionEvent> all = eventStore.readAll(sessionId);
        int finishedIndex = java.util.stream.IntStream.range(0, all.size())
                .filter(index -> all.get(index).eventType() == SessionEventType.TOOL_CALL_FINISHED)
                .findFirst()
                .orElseThrow();
        // Starting at tool_call_finished drops the tool_call_requested that opened the call.
        List<SessionEvent> partial = all.subList(finishedIndex, all.size());

        assertThatThrownBy(() -> projector.project(sessionId, partial))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("complete stream");
    }
}
