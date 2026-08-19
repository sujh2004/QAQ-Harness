package com.devpilot.runtime.projection;

/** Who produced a message in the projected timeline. */
public enum MessageRole {
    /** Message written by the user. */
    USER,
    /** Message produced by an agent. */
    ASSISTANT
}
