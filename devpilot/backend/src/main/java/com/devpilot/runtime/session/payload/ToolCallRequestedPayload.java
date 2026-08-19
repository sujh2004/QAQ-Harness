package com.devpilot.runtime.session.payload;

import java.util.Map;

/**
 * Records that a tool call entered the execution pipeline.
 *
 * <p>This event is appended for accepted calls and for rejected ones alike, so every denial stays
 * auditable. Arguments are already redacted when they reach this payload.
 *
 * @param agentName agent that requested the call
 * @param toolName requested tool name, as sent by the model
 * @param toolVersion resolved tool version, null when the tool is unknown
 * @param arguments redacted arguments
 * @param requestSummary short human-readable description of the request
 */
public record ToolCallRequestedPayload(
        String agentName,
        String toolName,
        String toolVersion,
        Map<String, Object> arguments,
        String requestSummary) implements SessionEventPayload {
}
