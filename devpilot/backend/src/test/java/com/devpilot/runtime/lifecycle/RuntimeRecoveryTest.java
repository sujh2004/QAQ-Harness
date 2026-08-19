package com.devpilot.runtime.lifecycle;

import com.devpilot.runtime.RuntimeTestFixtures;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.session.SessionEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: after a restart, a turn that was still running is closed with
 * {@code ABORTED_BY_RESTART} instead of being shown as work in progress.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuntimeRecoveryTest {

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private RuntimeRecoveryService recoveryService;

    @Autowired
    private SessionEventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStreams() {
        // Recovery scans every session, so this test needs the table to itself.
        jdbcTemplate.execute("DELETE FROM session_event");
        jdbcTemplate.execute("DELETE FROM session_stream");
    }

    @Test
    void closesTurnsLeftOpenByAPreviousProcess() {
        String sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "recovery"));
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        String stepId = lifecycleService.startStep(sessionId, turnId, "model request");
        String runId = lifecycleService.startAgentRun(
                sessionId, turnId, stepId, null, "log_agent", "日志分析 Agent", "search errors");
        String callId = RuntimeIds.newCallId();
        RuntimeTestFixtures.appendPendingToolCall(eventStore, sessionId, turnId, stepId, runId, callId);

        RuntimeRecoveryReport report = recoveryService.recover();

        assertThat(report.scannedSessions()).isEqualTo(1);
        assertThat(report.closedTurns()).isEqualTo(1);
        assertThat(report.failedSessions()).isZero();

        SessionProjection projection = lifecycleService.project(sessionId);
        RuntimeTestFixtures.assertNothingLeftOpen(projection);
        assertThat(projection.turn(turnId).orElseThrow().endReason())
                .isEqualTo(TurnEndReason.ABORTED_BY_RESTART);
        assertThat(projection.toolCall(callId).orElseThrow().errorCode()).isEqualTo(ToolErrorCode.ABORTED);
    }

    @Test
    void leavesFinishedSessionsAlone() {
        String sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "recovery-noop"));
        String turnId = lifecycleService.startTurn(sessionId, "USER", "question");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");
        long lastSeq = lifecycleService.project(sessionId).lastSeq();

        RuntimeRecoveryReport report = recoveryService.recover();

        assertThat(report.scannedSessions()).isZero();
        assertThat(report.closedTurns()).isZero();
        assertThat(lifecycleService.project(sessionId).lastSeq()).isEqualTo(lastSeq);
    }

    @Test
    void isIdempotentAcrossRepeatedRuns() {
        String sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "recovery-twice"));
        lifecycleService.startTurn(sessionId, "USER", "question");

        recoveryService.recover();
        long lastSeq = lifecycleService.project(sessionId).lastSeq();
        RuntimeRecoveryReport second = recoveryService.recover();

        assertThat(second.closedTurns()).isZero();
        assertThat(lifecycleService.project(sessionId).lastSeq()).isEqualTo(lastSeq);
    }
}
