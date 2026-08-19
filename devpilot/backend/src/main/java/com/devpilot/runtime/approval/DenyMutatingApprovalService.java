package com.devpilot.runtime.approval;

import org.springframework.stereotype.Component;

/**
 * The MVP approval provider: it refuses every request.
 *
 * <p>This is the actual product decision for the read-only MVP, not a placeholder. Interactive
 * approval arrives with the write-capable tools, and until then a refusal is the honest answer
 * rather than an implicit yes.
 */
@Component
public class DenyMutatingApprovalService implements ApprovalService {

    private static final String RESOLVER = "POLICY";

    @Override
    public ApprovalDecision request(ApprovalRequest request) {
        return ApprovalDecision.rejected(
                RESOLVER,
                "The MVP runs read-only; " + request.toolName() + " needs an interactive approval flow");
    }
}
