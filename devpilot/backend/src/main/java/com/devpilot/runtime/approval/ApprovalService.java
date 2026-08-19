package com.devpilot.runtime.approval;

/**
 * Human-in-the-loop gate for calls that change state.
 *
 * <p>The interface exists from Phase 1 so the tool pipeline has a single place to ask, even while
 * the MVP answer is always no. A future interactive implementation replaces the provider without
 * touching the pipeline or any agent prompt.
 */
@FunctionalInterface
public interface ApprovalService {

    /**
     * Asks whether a call may run.
     *
     * @param request the call awaiting authorisation
     * @return decision covering exactly the requested arguments
     */
    ApprovalDecision request(ApprovalRequest request);
}
