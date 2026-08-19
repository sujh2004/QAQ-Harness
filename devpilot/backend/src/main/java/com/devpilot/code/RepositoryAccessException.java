package com.devpilot.code;

/**
 * Raised when a repository read is refused or cannot be completed.
 *
 * <p>The message is written to be safe for a model to see: it names the rule that was broken, not
 * the absolute path or the file content behind it.
 */
public final class RepositoryAccessException extends RuntimeException {

    private final Reason reason;

    /**
     * Creates the exception.
     *
     * @param reason why access was refused
     * @param message safe explanation
     */
    public RepositoryAccessException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** @return why access was refused */
    public Reason reason() {
        return reason;
    }

    /** Why a repository read was refused. */
    public enum Reason {
        /** The configured repository root cannot be read. */
        REPOSITORY_UNAVAILABLE,
        /** The requested path resolves outside the repository root. */
        PATH_ESCAPES_REPOSITORY,
        /** The requested path matches the sensitive-file blacklist. */
        PATH_FORBIDDEN,
        /** The requested path does not exist. */
        PATH_NOT_FOUND,
        /** The requested path is not a readable text file of an allowed type. */
        UNSUPPORTED_FILE
    }
}
