package com.devpilot.runtime.stream;

import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: an SSE frame is a projection of a committed event, keyed by its sequence number so a
 * client can reconnect with {@code Last-Event-ID} and consume idempotently.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionEventEnvelopeTest {

    @Autowired
    private SessionEventSseCodec codec;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void framesCarryTheSequenceNumberAsIdAndTheEventTypeAsName() throws Exception {
        SseFrame frame = codec.encode(sampleEvent());

        assertThat(frame.id()).isEqualTo("42");
        assertThat(frame.event()).isEqualTo("tool_call_finished");

        Map<?, ?> data = objectMapper.readValue(frame.data(), Map.class);
        assertThat(data.get("sessionId")).isEqualTo("session_01J");
        assertThat(data.get("seq")).isEqualTo(42);
        assertThat(data.get("eventType")).isEqualTo("tool_call_finished");
        assertThat(data.get("turnId")).isEqualTo("turn_1");
        assertThat(data.get("stepId")).isEqualTo("step_1");
        assertThat(data.get("runId")).isEqualTo("run_1");
        assertThat(data.get("callId")).isEqualTo("tool_1");
        assertThat(data.get("occurredAt")).isNotNull();
        assertThat(data.get("payload")).isInstanceOf(Map.class);
    }

    @Test
    void envelopePayloadKeepsTheDecodedEventBody() {
        SessionEventEnvelope envelope = SessionEventEnvelope.from(sampleEvent());

        assertThat(envelope.payload()).isInstanceOf(ToolCallFinishedPayload.class);
        assertThat(((ToolCallFinishedPayload) envelope.payload()).status()).isEqualTo(ToolCallStatus.SUCCESS);
    }

    @Test
    void exposesTheLegacyUiNamesTheFirstDraftUsed() {
        assertThat(LegacyUiEventType.forEventType(SessionEventType.TURN_STARTED).orElseThrow().uiType())
                .isEqualTo("message_start");
        assertThat(LegacyUiEventType.forEventType(SessionEventType.TURN_ENDED).orElseThrow().uiType())
                .isEqualTo("message_finish");
        assertThat(LegacyUiEventType.forEventType(SessionEventType.TOOL_CALL_REQUESTED).orElseThrow().uiType())
                .isEqualTo("tool_call");
        assertThat(LegacyUiEventType.forEventType(SessionEventType.TOOL_CALL_FINISHED).orElseThrow().uiType())
                .isEqualTo("tool_result");
        assertThat(LegacyUiEventType.forEventType(SessionEventType.USER_MESSAGE)).isEmpty();
    }

    private static SessionEvent sampleEvent() {
        return new SessionEvent(
                7L,
                "session_01J",
                42L,
                SessionEventType.TOOL_CALL_FINISHED,
                1,
                "turn_1",
                "step_1",
                "run_1",
                "tool_1",
                new ToolCallFinishedPayload("log_agent", "searchLogs", ToolCallStatus.SUCCESS, null, null,
                        "18 NullPointerException entries", false, 42L),
                Instant.parse("2026-08-18T10:00:00.123Z"));
    }
}
