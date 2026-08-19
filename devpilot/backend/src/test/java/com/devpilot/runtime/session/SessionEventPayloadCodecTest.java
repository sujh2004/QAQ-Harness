package com.devpilot.runtime.session;

import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.lifecycle.TurnStatus;
import com.devpilot.runtime.session.payload.RuntimeErrorPayload;
import com.devpilot.runtime.session.payload.SessionEventPayload;
import com.devpilot.runtime.session.payload.TurnEndedPayload;
import com.devpilot.runtime.session.payload.UnknownPayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: payloads are decoded by event type plus schema version, an undecodable non-critical
 * event may be skipped, and an undecodable critical event stops replay.
 */
class SessionEventPayloadCodecTest {

    private final SessionEventPayloadCodec codec = new SessionEventPayloadCodec(new ObjectMapper());

    @Test
    void roundTripsAKnownPayload() {
        String json = codec.serialize(new UserMessagePayload("why does order-service return 500?"));

        SessionEventPayload decoded = codec.deserialize("user_message", 1, json);

        assertThat(decoded).isEqualTo(new UserMessagePayload("why does order-service return 500?"));
    }

    @Test
    void keepsAnUnknownSchemaVersionOfANonCriticalEvent() {
        String json = codec.serialize(new RuntimeErrorPayload("X", "y", "TOOL"));

        SessionEventPayload decoded = codec.deserialize("runtime_error", 99, json);

        assertThat(decoded).isInstanceOf(UnknownPayload.class);
        UnknownPayload unknown = (UnknownPayload) decoded;
        assertThat(unknown.eventType()).isEqualTo("runtime_error");
        assertThat(unknown.schemaVersion()).isEqualTo(99);
        assertThat(unknown.rawJson()).isEqualTo(json);
    }

    @Test
    void refusesAnUnknownSchemaVersionOfACriticalEvent() {
        String json = codec.serialize(
                new TurnEndedPayload(TurnStatus.COMPLETED, TurnEndReason.COMPLETED, "done"));

        assertThatThrownBy(() -> codec.deserialize("turn_ended", 99, json))
                .isInstanceOf(UnsupportedEventSchemaException.class)
                .hasMessageContaining("turn_ended");
    }

    @Test
    void refusesAnEventTypeThisBuildDoesNotKnow() {
        assertThatThrownBy(() -> codec.deserialize("some_future_event", 1, "{}"))
                .isInstanceOf(UnsupportedEventSchemaException.class)
                .hasMessageContaining("some_future_event");
    }
}
