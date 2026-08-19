package com.devpilot.runtime.tool;

import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;

/**
 * Outcome of one tool call as the caller and the model see it.
 *
 * <p>Every failure mode — unknown tool, invisible tool, invalid arguments, policy denial, refused
 * approval, timeout, provider fault — arrives here as data. Nothing escapes the pipeline as an
 * exception, and no server stack trace is carried in {@code message}.
 *
 * @param callId identifier shared with the persisted events and the UI projection
 * @param toolName requested tool name
 * @param status terminal call status
 * @param errorCode stable failure code, null when the call succeeded
 * @param message safe failure message, null when the call succeeded
 * @param modelSummary summary handed to the model
 * @param data structured result, null when the call failed
 * @param truncated whether the result was cut down to the declared limits
 * @param durationMs provider execution time in milliseconds
 */
public record ToolExecutionResult(
        String callId,
        String toolName,
        ToolCallStatus status,
        ToolErrorCode errorCode,
        String message,
        String modelSummary,
        Object data,
        boolean truncated,
        long durationMs) {

    /** @return whether the provider produced a result */
    public boolean successful() {
        return status == ToolCallStatus.SUCCESS;
    }
}
