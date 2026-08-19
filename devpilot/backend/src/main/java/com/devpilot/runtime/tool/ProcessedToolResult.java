package com.devpilot.runtime.tool;

/**
 * A provider result after the pipeline applied the declared limits and redaction.
 *
 * @param data structured result, possibly truncated
 * @param modelSummary summary handed to the model
 * @param persistSummary summary stored in the event log
 * @param truncated whether items or bytes were dropped to respect the limits
 */
public record ProcessedToolResult(Object data, String modelSummary, String persistSummary, boolean truncated) {
}
