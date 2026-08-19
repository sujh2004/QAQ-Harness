package com.devpilot.runtime.session;

/** Raised when an operation targets a session event stream that was never opened. */
public final class SessionStreamNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param sessionId missing session identifier
     */
    public SessionStreamNotFoundException(String sessionId) {
        super("Session event stream not found: " + sessionId);
    }
}
