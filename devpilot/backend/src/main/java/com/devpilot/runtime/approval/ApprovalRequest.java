package com.devpilot.runtime.approval;

import java.util.Map;

/**
 * A request for a human to authorise one tool call.
 *
 * @param sessionId owning session
 * @param turnId owning turn
 * @param agentName agent that wants to run the tool
 * @param toolName tool awaiting approval
 * @param arguments redacted arguments the approval would cover
 * @param reason why approval is required
 */
public record ApprovalRequest(
        String sessionId,
        String turnId,
        String agentName,
        String toolName,
        Map<String, Object> arguments,
        String reason) {
}
