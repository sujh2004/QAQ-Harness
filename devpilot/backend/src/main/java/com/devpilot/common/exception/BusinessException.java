package com.devpilot.common.exception;

import com.devpilot.common.api.ErrorCode;

/** A business failure that can be safely converted to an API response. */
public final class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates a business exception.
     *
     * @param errorCode stable error definition
     * @param message safe user-facing message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** @return stable error definition */
    public ErrorCode errorCode() {
        return errorCode;
    }
}

