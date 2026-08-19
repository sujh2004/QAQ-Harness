package com.devpilot.common.api;

import org.springframework.http.HttpStatus;

/** Stable error codes returned by non-streaming HTTP endpoints. */
public enum ErrorCode {
    /** The request succeeded. */
    SUCCESS(0, HttpStatus.OK, "success"),
    /** The request was rejected by validation. */
    INVALID_ARGUMENT(40000, HttpStatus.BAD_REQUEST, "Invalid request argument"),
    /** The referenced project does not exist. */
    PROJECT_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "Project not found"),
    /** The referenced session does not exist. */
    SESSION_NOT_FOUND(40402, HttpStatus.NOT_FOUND, "Session not found"),
    /** Another project already uses the requested code. */
    PROJECT_CODE_CONFLICT(40901, HttpStatus.CONFLICT, "Project code already exists"),
    /** The requested runtime transition is not allowed in the current state. */
    LIFECYCLE_CONFLICT(40902, HttpStatus.CONFLICT, "Runtime lifecycle conflict"),
    /** The configured repository path cannot be read. */
    REPOSITORY_NOT_ACCESSIBLE(42201, HttpStatus.UNPROCESSABLE_ENTITY,
            "Configured repository path cannot be accessed"),
    /** An unexpected server failure. */
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final int code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /** @return numeric API error code */
    public int code() {
        return code;
    }

    /** @return HTTP status this error is reported with */
    public HttpStatus status() {
        return status;
    }

    /** @return safe default message */
    public String defaultMessage() {
        return defaultMessage;
    }
}
