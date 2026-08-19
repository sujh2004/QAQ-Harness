package com.devpilot.runtime.tool;

/** How the UI should render a tool call and its result. */
public enum ToolDisplayIntent {
    /** No special rendering. */
    GENERIC,
    /** A list of matches. */
    SEARCH,
    /** A file or document excerpt. */
    READ,
    /** A change between two versions. */
    DIFF,
    /** Command-style output. */
    TERMINAL
}
