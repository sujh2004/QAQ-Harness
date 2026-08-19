package com.devpilot.runtime.session;

/** Raised when a session event stream is opened twice. */
public final class SessionStreamAlreadyExistsException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param sessionId duplicated session identifier
     */
    public SessionStreamAlreadyExistsException(String sessionId) {
        super("Session event stream already exists: " + sessionId);
    }
}
