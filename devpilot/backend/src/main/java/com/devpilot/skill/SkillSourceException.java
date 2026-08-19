package com.devpilot.skill;

/** Raised when a skill marketplace cannot be read or offers something unusable. */
public final class SkillSourceException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message safe explanation
     */
    public SkillSourceException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message safe explanation
     * @param cause underlying failure
     */
    public SkillSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
