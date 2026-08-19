package com.devpilot.runtime.lifecycle;

import com.devpilot.runtime.RuntimeTestFixtures;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.projection.StepView;
import com.devpilot.runtime.projection.TurnView;
import com.devpilot.runtime.session.SessionEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Contract: the turn and step state machine only allows declared transitions, and no started turn,
 * step, agent run or tool call is ever left without a terminal event.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionLifecycleTest {

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private SessionEventStore eventStore;

    private String sessionId;

    @BeforeEach
    void openSession() {
        sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "lifecycle"));
    }

    @Test
    void pinsTheRuntimeConfigurationWhenTheSessionIsCreated() {
        SessionProjection projection = lifecycleService.project(sessionId);

        assertThat(projection.profileVersion()).isEqualTo(RuntimeTestFixtures.PROFILE_VERSION);
        assertThat(projection.capabilities()).containsExactly("code", "log");
        assertThat(projection.projectId()).isEqualTo(1L);
        assertThat(projection.lastSeq()).isEqualTo(1L);
    }

    @Test
    void recordsTheUserMessageWithTheTurnThatItTriggered() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "why does order-service return 500?");

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.messages()).singleElement().satisfies(message ->
                assertThat(message.content()).isEqualTo("why does order-service return 500?"));
        assertThat(projection.turn(turnId).orElseThrow().status()).isEqualTo(TurnStatus.RUNNING);
    }

    @Test
    void refusesASecondRunningTurn() {
        lifecycleService.startTurn(sessionId, "USER", "first");

        assertThatThrownBy(() -> lifecycleService.startTurn(sessionId, "USER", "second"))
                .isInstanceOf(IllegalLifecycleTransitionException.class)
                .hasMessageContaining("already has running turn");
    }

    @Test
    void refusesASecondRunningStep() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        lifecycleService.startStep(sessionId, turnId, "first model request");

        assertThatThrownBy(() -> lifecycleService.startStep(sessionId, turnId, "second model request"))
                .isInstanceOf(IllegalLifecycleTransitionException.class)
                .hasMessageContaining("already has a running step");
    }

    @Test
    void allowsSequentialStepsAndNumbersThem() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        String first = lifecycleService.startStep(sessionId, turnId, "first");
        lifecycleService.endStep(sessionId, first, StepStatus.COMPLETED, "tool results collected");
        String second = lifecycleService.startStep(sessionId, turnId, "second");

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.turn(turnId).orElseThrow().steps())
                .extracting(StepView::stepId, StepView::index)
                .containsExactly(tuple(first, 0), tuple(second, 1));
    }

    @Test
    void refusesToEndAStepTwice() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        String stepId = lifecycleService.startStep(sessionId, turnId, "only step");
        lifecycleService.endStep(sessionId, stepId, StepStatus.COMPLETED, "done");

        assertThatThrownBy(() -> lifecycleService.endStep(sessionId, stepId, StepStatus.FAILED, "again"))
                .isInstanceOf(IllegalLifecycleTransitionException.class)
                .hasMessageContaining("cannot move from COMPLETED");
    }

    @Test
    void refusesToStartAStepOnAFinishedTurn() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        assertThatThrownBy(() -> lifecycleService.startStep(sessionId, turnId, "late step"))
                .isInstanceOf(IllegalLifecycleTransitionException.class)
                .hasMessageContaining("already ended");
    }

    @Test
    void closesEveryOpenChildWhenTheTurnEnds() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        String stepId = lifecycleService.startStep(sessionId, turnId, "model request");
        String runId = lifecycleService.startAgentRun(
                sessionId, turnId, stepId, null, "log_agent", "日志分析 Agent", "find recent errors");
        String callId = RuntimeIds.newCallId();
        RuntimeTestFixtures.appendPendingToolCall(eventStore, sessionId, turnId, stepId, runId, callId);

        TurnView turn = lifecycleService.endTurn(sessionId, turnId, TurnEndReason.FAILED, "model unavailable");

        assertThat(turn.status()).isEqualTo(TurnStatus.FAILED);
        assertThat(turn.endReason()).isEqualTo(TurnEndReason.FAILED);

        SessionProjection projection = lifecycleService.project(sessionId);
        RuntimeTestFixtures.assertNothingLeftOpen(projection);
        assertThat(projection.run(runId).orElseThrow().status()).isEqualTo(RunStatus.FAILED);
        assertThat(projection.step(stepId).orElseThrow().status()).isEqualTo(StepStatus.FAILED);
        assertThat(projection.toolCall(callId).orElseThrow().status()).isEqualTo(ToolCallStatus.CANCELLED);
        assertThat(projection.toolCall(callId).orElseThrow().errorCode()).isEqualTo(ToolErrorCode.ABORTED);
    }

    @Test
    void refusesToEndATurnTwice() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        assertThatThrownBy(() -> lifecycleService.endTurn(sessionId, turnId, TurnEndReason.FAILED, "again"))
                .isInstanceOf(IllegalLifecycleTransitionException.class);
    }
}
