package com.devpilot.runtime.tool;

import com.devpilot.runtime.lifecycle.ToolErrorCode;

/**
 * Thrown by a provider to report an expected failure with a message that is safe to show the model.
 *
 * <p>Anything else a provider throws is reported as a generic provider error carrying only the
 * exception type, because an arbitrary exception message may contain paths or credentials.
 */
public final class ToolExecutionException extends RuntimeException {

    private final ToolErrorCode errorCode;

    /**
     * Creates the exception.
     *
     * @param errorCode stable failure code
     * @param safeMessage message that may be shown to the model and stored in the event log
     */
    public ToolExecutionException(ToolErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
    }

    /** @return stable failure code */
    public ToolErrorCode errorCode() {
        return errorCode;
    }
}
