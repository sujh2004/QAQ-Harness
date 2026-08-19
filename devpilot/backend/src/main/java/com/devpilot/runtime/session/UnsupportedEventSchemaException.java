package com.devpilot.runtime.session;

/**
 * Raised when an event cannot be decoded because this build does not know its type or schema
 * version and the event is critical to replay.
 *
 * <p>Recovery stops here on purpose. Skipping a critical event would silently change what the model
 * sees on the next request.
 */
public final class UnsupportedEventSchemaException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description naming the event type and schema version
     */
    public UnsupportedEventSchemaException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message description naming the event type and schema version
     * @param cause underlying decoding failure
     */
    public UnsupportedEventSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
