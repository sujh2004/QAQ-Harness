package com.devpilot.runtime.projection;

import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;

import java.time.Instant;

/**
 * Projected state of one tool call. This is the audit record rebuilt from the
 * {@code tool_call_requested} and {@code tool_call_finished} events.
 *
 * @param callId call identifier
 * @param turnId owning turn
 * @param stepId owning step, may be null
 * @param runId owning agent run, may be null
 * @param agentName agent that requested the call
 * @param toolName requested tool name
 * @param status current call status
 * @param errorCode stable failure code, null unless the call failed
 * @param message safe failure message, null unless the call failed
 * @param requestSummary short description of the request
 * @param resultSummary short description of the result, null while running
 * @param truncated whether the result was cut down to the declared limits
 * @param durationMs provider execution time, zero while running
 * @param startedAt when the call was requested
 * @param endedAt when the call finished, null while running
 */
public record ToolCallView(
        String callId,
        String turnId,
        String stepId,
        String runId,
        String agentName,
        String toolName,
        ToolCallStatus status,
        ToolErrorCode errorCode,
        String message,
        String requestSummary,
        String resultSummary,
        boolean truncated,
        long durationMs,
        Instant startedAt,
        Instant endedAt) {
}
