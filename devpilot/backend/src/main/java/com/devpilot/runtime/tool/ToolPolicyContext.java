package com.devpilot.runtime.tool;

/**
 * Everything a policy is allowed to reason about.
 *
 * <p>The model's own claims are deliberately absent: authorisation is decided from the caller, the
 * project, the session, the effective scope and the declared tool, never from what the model says
 * about its own permissions.
 *
 * @param invocation the request, including raw arguments
 * @param definition declaration of the resolved tool
 * @param scope effective scope of the calling agent
 * @param profileVersion agent profile version pinned for the session
 * @param projectId owning project, may be null until the project module exists
 */
public record ToolPolicyContext(
        ToolInvocation invocation,
        ToolDefinition definition,
        ToolScope scope,
        String profileVersion,
        Long projectId) {
}
