package com.devpilot.runtime.tool;

/**
 * Authorises tool calls inside the execution pipeline.
 *
 * <p>Visibility and executability are two separate checks: a profile decides what the model can
 * see, and the policy still re-authorises at execution time so a forged tool name or a stale
 * session cannot slip past the scope.
 */
@FunctionalInterface
public interface ToolPolicy {

    /**
     * Decides whether a call may run.
     *
     * @param context caller, scope and tool declaration
     * @return verdict
     */
    ToolPolicyDecision decide(ToolPolicyContext context);
}
