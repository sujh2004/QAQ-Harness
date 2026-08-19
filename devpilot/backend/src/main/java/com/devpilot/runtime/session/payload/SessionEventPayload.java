package com.devpilot.runtime.session.payload;

/**
 * Marker for the typed body of a session event.
 *
 * <p>Each event type owns exactly one payload record per schema version. Payloads only carry
 * content that is safe to persist and replay: no credentials, no raw sensitive file content and no
 * hidden model reasoning.
 */
public interface SessionEventPayload {
}
