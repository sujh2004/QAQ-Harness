package com.devpilot.runtime.session.payload;

import java.util.Map;

/**
 * Records that a call needs human approval before it may execute. The approval covers exactly the
 * arguments recorded here and authorises nothing beyond them.
 *
 * @param agentName agent that requested the call
 * @param toolName tool awaiting approval
 * @param reason why approval is required
 * @param arguments redacted arguments the approval applies to
 */
public record ApprovalRequestedPayload(
        String agentName,
        String toolName,
        String reason,
        Map<String, Object> arguments) implements SessionEventPayload {
}
