package com.devpilot.runtime.session.payload;

import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;

/**
 * Closes a tool call. Success, validation failure, denial, timeout and provider faults all arrive
 * here, so a {@code tool_call_requested} event is never left open.
 *
 * @param agentName agent that requested the call
 * @param toolName requested tool name
 * @param status terminal call status
 * @param errorCode stable failure code, null when the call succeeded
 * @param message safe failure message, null when the call succeeded
 * @param resultSummary persisted result summary, never the full provider payload
 * @param truncated whether the result was cut down to the declared limits
 * @param durationMs provider execution time in milliseconds
 */
public record ToolCallFinishedPayload(
        String agentName,
        String toolName,
        ToolCallStatus status,
        ToolErrorCode errorCode,
        String message,
        String resultSummary,
        boolean truncated,
        long durationMs) implements SessionEventPayload {
}
