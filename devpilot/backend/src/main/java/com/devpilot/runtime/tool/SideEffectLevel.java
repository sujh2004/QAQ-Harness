package com.devpilot.runtime.tool;

/** Whether a tool only reads or also changes state. */
public enum SideEffectLevel {
    /** The tool only reads. */
    READ_ONLY,
    /** The tool changes state and is refused unless policy explicitly allows it. */
    MUTATING
}
