package com.devpilot.runtime.stream;

import com.devpilot.runtime.session.SessionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Turns committed events into SSE frames.
 *
 * <p>Kept separate from the transport so the chat endpoint added in a later phase only has to write
 * the frames this codec produces, and replay after a reconnect uses exactly the same encoding as
 * the live stream.
 */
@Component
public class SessionEventSseCodec {

    private final ObjectMapper objectMapper;

    /**
     * Creates the codec.
     *
     * @param objectMapper shared JSON mapper
     */
    public SessionEventSseCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Encodes one event.
     *
     * @param event committed session event
     * @return frame carrying the sequence number as its id and the event type as its name
     */
    public SseFrame encode(SessionEvent event) {
        SessionEventEnvelope envelope = SessionEventEnvelope.from(event);
        try {
            return new SseFrame(
                    Long.toString(event.seq()),
                    event.eventType().wireName(),
                    objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot encode event " + event.sessionId() + "#" + event.seq() + " for SSE", exception);
        }
    }
}
