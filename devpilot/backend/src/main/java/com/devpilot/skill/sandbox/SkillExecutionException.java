package com.devpilot.skill.sandbox;

/**
 * Raised when a skill cannot be launched or its execution is refused.
 *
 * <p>The message is written to be safe for a model and an audit trail to see: it names the rule
 * that was broken, not the absolute paths or the environment behind it.
 */
public final class SkillExecutionException extends RuntimeException {

    private final Reason reason;

    /**
     * Creates the exception.
     *
     * @param reason why execution was refused or failed
     * @param message safe explanation
     */
    public SkillExecutionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** @return why execution was refused or failed */
    public Reason reason() {
        return reason;
    }

    /** Why a skill execution was refused or failed. */
    public enum Reason {
        /** The declared runtime is not on the interpreter allow list. */
        RUNTIME_NOT_ALLOWED,
        /** The entrypoint resolves outside the skill package. */
        ENTRYPOINT_ESCAPES_PACKAGE,
        /** The entrypoint does not exist or cannot be read. */
        ENTRYPOINT_NOT_FOUND,
        /** The interpreter could not be started. */
        LAUNCH_FAILED,
        /** The script exceeded its time budget. */
        TIMEOUT,
        /** The script exited with a non-zero status. */
        SCRIPT_FAILED
    }
}
