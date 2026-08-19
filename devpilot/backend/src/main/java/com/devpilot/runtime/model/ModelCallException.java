package com.devpilot.runtime.model;

/**
 * Raised when a model request cannot be made or fails at the provider.
 *
 * <p>The message is written to be safe for an audit trail: it names the provider and the reason,
 * never the credential or the request body.
 */
public final class ModelCallException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message safe explanation
     */
    public ModelCallException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message safe explanation
     * @param cause underlying provider failure
     */
    public ModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
