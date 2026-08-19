package com.devpilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Common response envelope for non-streaming HTTP APIs.
 *
 * @param code stable application code; zero means success
 * @param message safe user-facing message
 * @param data response payload
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Result<T>(int code, String message, T data) {

    /**
     * Creates a successful response.
     *
     * @param data response payload
     * @param <T> payload type
     * @return success envelope
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.defaultMessage(), data);
    }

    /**
     * Creates a failed response with a safe message.
     *
     * @param errorCode stable error definition
     * @param message safe user-facing message
     * @return failure envelope
     */
    public static Result<Void> failure(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null);
    }
}

