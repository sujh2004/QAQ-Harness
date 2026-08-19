package com.devpilot.runtime.approval;

/**
 * Result of an approval request.
 *
 * <p>An approval covers exactly the arguments it was asked about and authorises nothing that
 * follows from them.
 *
 * @param approved whether execution may proceed
 * @param resolvedBy who decided, for example {@code POLICY} or a user identifier
 * @param reason safe explanation of the decision
 */
public record ApprovalDecision(boolean approved, String resolvedBy, String reason) {

    /**
     * Refuses the request.
     *
     * @param resolvedBy who decided
     * @param reason safe explanation
     * @return refusing decision
     */
    public static ApprovalDecision rejected(String resolvedBy, String reason) {
        return new ApprovalDecision(false, resolvedBy, reason);
    }

    /**
     * Approves the request.
     *
     * @param resolvedBy who decided
     * @param reason safe explanation
     * @return approving decision
     */
    public static ApprovalDecision approved(String resolvedBy, String reason) {
        return new ApprovalDecision(true, resolvedBy, reason);
    }
}
