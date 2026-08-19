package com.devpilot.runtime.session;

import com.devpilot.runtime.session.payload.SessionEventPayload;
import com.devpilot.runtime.session.payload.UnknownPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Converts event payloads between their records and the JSON stored in {@code payload_json}.
 *
 * <p>Decoding is driven by {@code event_type} plus {@code schema_version}, never by a type hint
 * inside the JSON, so a stored event cannot claim to be something else.
 */
@Component
public class SessionEventPayloadCodec {

    private final ObjectMapper objectMapper;

    /**
     * Creates the codec.
     *
     * @param objectMapper shared JSON mapper
     */
    public SessionEventPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialises a payload for storage.
     *
     * @param payload event body
     * @return JSON text
     * @throws UnsupportedEventSchemaException when the payload cannot be written
     */
    public String serialize(SessionEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new UnsupportedEventSchemaException(
                    "Cannot serialize payload " + payload.getClass().getSimpleName(), exception);
        }
    }

    /**
     * Decodes a stored payload.
     *
     * <p>An unknown event type is always rejected: this build cannot prove the event is free of
     * model-visible state. A known but newer schema version is rejected for critical events and
     * kept verbatim as {@link UnknownPayload} for the rest, which projections skip.
     *
     * @param wireEventType persisted event type name
     * @param schemaVersion persisted schema version
     * @param json persisted payload JSON
     * @return decoded payload
     * @throws UnsupportedEventSchemaException when a critical event cannot be decoded
     */
    public SessionEventPayload deserialize(String wireEventType, int schemaVersion, String json) {
        SessionEventType eventType = SessionEventType.fromWireName(wireEventType)
                .orElseThrow(() -> new UnsupportedEventSchemaException(
                        "Unknown event type '" + wireEventType + "'; refusing to replay a stream this build "
                                + "cannot fully interpret"));

        if (schemaVersion != eventType.currentSchemaVersion()) {
            if (eventType.critical()) {
                throw new UnsupportedEventSchemaException("Unsupported schema version " + schemaVersion
                        + " for critical event '" + wireEventType + "'; this build writes version "
                        + eventType.currentSchemaVersion());
            }
            return new UnknownPayload(wireEventType, schemaVersion, json);
        }

        try {
            return objectMapper.readValue(json, eventType.payloadType());
        } catch (JsonProcessingException exception) {
            if (eventType.critical()) {
                throw new UnsupportedEventSchemaException(
                        "Cannot decode critical event '" + wireEventType + "' at schema version " + schemaVersion,
                        exception);
            }
            return new UnknownPayload(wireEventType, schemaVersion, json);
        }
    }
}
