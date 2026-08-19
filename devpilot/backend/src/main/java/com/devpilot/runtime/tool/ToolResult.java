package com.devpilot.runtime.tool;

/**
 * Normalised value a provider returns.
 *
 * <p>The three summaries may differ — a model needs different detail than an audit record or a UI
 * card — but all of them belong to the same {@code callId}, so they stay correlatable.
 *
 * @param data structured result, truncated by the pipeline when it exceeds the declared limits
 * @param itemCount number of items the provider found before truncation
 * @param modelSummary short summary handed to the model
 * @param persistSummary summary stored in the event log and audit views
 * @param uiPayload projection the UI renders, may be the same object as {@code data}
 */
public record ToolResult(
        Object data, int itemCount, String modelSummary, String persistSummary, Object uiPayload) {

    /**
     * Builds a result that uses one summary for the model, the audit log and the UI.
     *
     * @param data structured result
     * @param itemCount number of items found
     * @param summary shared summary
     * @return normalised result
     */
    public static ToolResult of(Object data, int itemCount, String summary) {
        return new ToolResult(data, itemCount, summary, summary, data);
    }
}
