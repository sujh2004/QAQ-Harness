package com.devpilot.runtime.lifecycle;

import java.util.UUID;

/**
 * Generates the identifiers used by the runtime lifecycle.
 *
 * <p>Identifiers are prefixed so an id read from a log or an SSE frame is self-describing.
 */
public final class RuntimeIds {

    private RuntimeIds() {
    }

    /** @return new session identifier */
    public static String newSessionId() {
        return "session_" + randomSuffix();
    }

    /** @return new turn identifier */
    public static String newTurnId() {
        return "turn_" + randomSuffix();
    }

    /** @return new step identifier */
    public static String newStepId() {
        return "step_" + randomSuffix();
    }

    /** @return new agent run identifier */
    public static String newRunId() {
        return "run_" + randomSuffix();
    }

    /** @return new tool call identifier */
    public static String newCallId() {
        return "tool_" + randomSuffix();
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
