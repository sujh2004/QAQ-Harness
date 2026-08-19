package com.devpilot.runtime.model;

/**
 * Receives incremental output while a model is answering.
 *
 * <p>The runtime turns text deltas into {@code assistant_delta} events and the final answer into an
 * {@code assistant_message} event, so anything the model will see again has been recorded first.
 */
public interface ModelStreamListener {

    /**
     * Receives a fragment of visible assistant text.
     *
     * @param delta text fragment
     */
    void onTextDelta(String delta);

    /**
     * Receives a tool call the model asked for.
     *
     * @param toolCall requested call
     */
    void onToolCall(ModelToolCall toolCall);

    /**
     * Receives the complete answer once generation stopped.
     *
     * @param response final answer with provider metadata
     */
    void onCompleted(ModelResponse response);
}
