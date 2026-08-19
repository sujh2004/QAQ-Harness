package com.devpilot.runtime.session.payload;

/**
 * Records the outcome of an approval request.
 *
 * @param toolName tool the decision applies to
 * @param approved whether execution was allowed
 * @param resolvedBy who decided, for example {@code POLICY} while MVP refuses every write
 * @param reason safe explanation of the decision
 */
public record ApprovalResolvedPayload(String toolName, boolean approved, String resolvedBy, String reason)
        implements SessionEventPayload {
}
