package com.devpilot.runtime.tool;

/**
 * Capability a tool needs before it may run.
 *
 * <p>Permissions are granted by scope, not requested by the model: a tool that claims a permission
 * the current scope does not hold is refused.
 */
public enum ToolPermission {
    /** Read the project source repository. */
    CODE_READ,
    /** Read system logs. */
    LOG_READ,
    /** Search the knowledge base. */
    KNOWLEDGE_READ,
    /** Write to the knowledge index. */
    KNOWLEDGE_INDEX_WRITE,
    /** Persist generated test cases. */
    TEST_CASE_WRITE
}
