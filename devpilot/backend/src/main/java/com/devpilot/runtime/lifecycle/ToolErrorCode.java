package com.devpilot.runtime.lifecycle;

/**
 * Stable, model-safe reason for a failed tool call.
 *
 * <p>These codes are part of the event contract: they are persisted, shown in the audit trail and
 * handed to the model so it can decide whether to correct itself. They never carry server stack
 * traces or credential material.
 */
public enum ToolErrorCode {
    /** No tool with the requested name is registered. */
    TOOL_NOT_FOUND,
    /** The tool exists but is not visible to the calling agent. */
    TOOL_NOT_VISIBLE,
    /** Arguments failed validation. */
    INVALID_ARGUMENT,
    /** The policy refused the call. */
    PERMISSION_DENIED,
    /** The call needed human approval and approval was refused. */
    APPROVAL_REJECTED,
    /** The provider exceeded the declared timeout. */
    TIMEOUT,
    /** The provider raised an error. */
    PROVIDER_ERROR,
    /** The call was closed by cancellation or restart recovery before it finished. */
    ABORTED
}
