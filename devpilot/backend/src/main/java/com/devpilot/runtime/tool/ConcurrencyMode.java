package com.devpilot.runtime.tool;

/** Whether a tool may run alongside other calls to itself in the same session. */
public enum ConcurrencyMode {
    /** Only one call at a time per session. */
    EXCLUSIVE,
    /** Concurrent calls are safe. */
    CONCURRENCY_SAFE
}
