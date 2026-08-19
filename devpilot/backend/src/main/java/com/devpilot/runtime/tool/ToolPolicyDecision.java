package com.devpilot.runtime.tool;

import com.devpilot.runtime.lifecycle.ToolErrorCode;

/**
 * A policy verdict.
 *
 * @param outcome what may happen next
 * @param errorCode stable failure code when the call is refused, null otherwise
 * @param reason safe explanation shown in the audit trail and handed to the model
 */
public record ToolPolicyDecision(ToolPolicyOutcome outcome, ToolErrorCode errorCode, String reason) {

    /**
     * Allows the call.
     *
     * @return allowing decision
     */
    public static ToolPolicyDecision allow() {
        return new ToolPolicyDecision(ToolPolicyOutcome.ALLOW, null, null);
    }

    /**
     * Refuses the call.
     *
     * @param errorCode stable failure code
     * @param reason safe explanation
     * @return refusing decision
     */
    public static ToolPolicyDecision deny(ToolErrorCode errorCode, String reason) {
        return new ToolPolicyDecision(ToolPolicyOutcome.DENY, errorCode, reason);
    }

    /**
     * Routes the call to human approval.
     *
     * @param reason safe explanation of why approval is needed
     * @return approval decision
     */
    public static ToolPolicyDecision requireApproval(String reason) {
        return new ToolPolicyDecision(ToolPolicyOutcome.REQUIRE_APPROVAL, null, reason);
    }
}
