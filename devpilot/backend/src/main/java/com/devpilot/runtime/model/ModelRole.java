package com.devpilot.runtime.model;

/** Who a model message belongs to. */
public enum ModelRole {
    /** Instructions assembled by the runtime. */
    SYSTEM,
    /** Input from the user. */
    USER,
    /** Output from the model. */
    ASSISTANT,
    /** A tool result fed back into the conversation. */
    TOOL
}
