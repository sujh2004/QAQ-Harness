package com.devpilot.runtime.tool;

/**
 * Raised when a scope declaration tries to grant a tool or permission the enclosing scope withheld.
 *
 * <p>Visibility narrows from application to project to session to agent. Once a project forbids
 * something, no profile below it may bring it back.
 */
public final class ToolScopeViolationException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the attempted widening
     */
    public ToolScopeViolationException(String message) {
        super(message);
    }
}
