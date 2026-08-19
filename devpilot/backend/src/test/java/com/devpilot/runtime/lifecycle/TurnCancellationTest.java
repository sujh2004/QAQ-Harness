package com.devpilot.runtime.lifecycle;

import com.devpilot.runtime.RuntimeTestFixtures;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.projection.TurnView;
import com.devpilot.runtime.session.SessionEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: cancelling is idempotent, always reaches {@code ABORTED_BY_USER} and closes everything
 * the turn had open.
 */
@SpringBootTest
@ActiveProfiles("test")
class TurnCancellationTest {

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private SessionEventStore eventStore;

    private String sessionId;

    @BeforeEach
    void openSession() {
        sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "cancellation"));
    }

    @Test
    void cancellingARunningTurnClosesItAndItsChildren() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        String stepId = lifecycleService.startStep(sessionId, turnId, "model request");
        String runId = lifecycleService.startAgentRun(
                sessionId, turnId, stepId, null, "code_agent", "代码分析 Agent", "read OrderService");
        String callId = RuntimeIds.newCallId();
        RuntimeTestFixtures.appendPendingToolCall(eventStore, sessionId, turnId, stepId, runId, callId);

        TurnView cancelled = lifecycleService.cancelTurn(sessionId, turnId);

        assertThat(cancelled.status()).isEqualTo(TurnStatus.CANCELLED);
        assertThat(cancelled.endReason()).isEqualTo(TurnEndReason.ABORTED_BY_USER);
        SessionProjection projection = lifecycleService.project(sessionId);
        RuntimeTestFixtures.assertNothingLeftOpen(projection);
        assertThat(projection.run(runId).orElseThrow().status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(projection.step(stepId).orElseThrow().status()).isEqualTo(StepStatus.CANCELLED);
    }

    @Test
    void cancellingTwiceAppendsNothingAndReturnsTheSameTerminalState() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        TurnView first = lifecycleService.cancelTurn(sessionId, turnId);
        long seqAfterFirstCancel = lifecycleService.project(sessionId).lastSeq();

        TurnView second = lifecycleService.cancelTurn(sessionId, turnId);

        assertThat(second).isEqualTo(first);
        assertThat(lifecycleService.project(sessionId).lastSeq()).isEqualTo(seqAfterFirstCancel);
    }

    @Test
    void cancellingAnAlreadyCompletedTurnReportsItsExistingOutcome() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        TurnView cancelled = lifecycleService.cancelTurn(sessionId, turnId);

        assertThat(cancelled.status()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(cancelled.endReason()).isEqualTo(TurnEndReason.COMPLETED);
    }

    @Test
    void cancellingAllowsTheNextTurnToStart() {
        String firstTurn = lifecycleService.startTurn(sessionId, "USER", "question");
        lifecycleService.cancelTurn(sessionId, firstTurn);

        String secondTurn = lifecycleService.startTurn(sessionId, "USER", "follow-up");

        assertThat(secondTurn).isNotEqualTo(firstTurn);
        assertThat(lifecycleService.project(sessionId).activeTurn().orElseThrow().turnId()).isEqualTo(secondTurn);
    }
}
