package com.devpilot.runtime.tool;

/** What a policy decided about one call. */
public enum ToolPolicyOutcome {
    /** The call may run. */
    ALLOW,
    /** The call is refused. */
    DENY,
    /** The call may only run after a human approves the exact arguments. */
    REQUIRE_APPROVAL
}
