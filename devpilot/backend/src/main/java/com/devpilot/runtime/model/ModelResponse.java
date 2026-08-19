package com.devpilot.runtime.model;

import java.util.List;

/**
 * One model answer.
 *
 * @param content visible assistant text, empty when the model only asked for tools
 * @param toolCalls tools the model wants to run
 * @param metadata provider observability data
 */
public record ModelResponse(String content, List<ModelToolCall> toolCalls, ModelCallMetadata metadata) {

    /** Normalises the tool call list into an immutable copy. */
    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** @return whether the model asked to run at least one tool */
    public boolean requestsTools() {
        return !toolCalls.isEmpty();
    }
}
