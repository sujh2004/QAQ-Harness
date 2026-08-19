package com.devpilot.runtime.model;

import java.util.List;

/**
 * One message in a model request.
 *
 * <p>A tool exchange is two messages, mirroring what chat providers expect: an assistant message
 * carrying the tool call, then a {@link ModelRole#TOOL} message carrying its result under the same
 * {@code callId}. Sending a tool result without the matching request is rejected by real providers.
 *
 * @param role who the message belongs to
 * @param content message text
 * @param name tool name for {@link ModelRole#TOOL} messages, null otherwise
 * @param callId tool call this message belongs to, null for plain conversation
 * @param toolCalls tool calls an assistant message asks for, empty otherwise
 */
public record ModelMessage(
        ModelRole role, String content, String name, String callId, List<ModelToolCall> toolCalls) {

    /** Normalises the tool call list into an immutable copy. */
    public ModelMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * Builds a system message.
     *
     * @param content message text
     * @return system message
     */
    public static ModelMessage system(String content) {
        return new ModelMessage(ModelRole.SYSTEM, content, null, null, List.of());
    }

    /**
     * Builds a user message.
     *
     * @param content message text
     * @return user message
     */
    public static ModelMessage user(String content) {
        return new ModelMessage(ModelRole.USER, content, null, null, List.of());
    }

    /**
     * Builds an assistant message.
     *
     * @param content message text
     * @return assistant message
     */
    public static ModelMessage assistant(String content) {
        return new ModelMessage(ModelRole.ASSISTANT, content, null, null, List.of());
    }

    /**
     * Builds the assistant turn that asks for a tool.
     *
     * @param toolCall call the model asked for
     * @return assistant message carrying the tool call
     */
    public static ModelMessage assistantToolCall(ModelToolCall toolCall) {
        return new ModelMessage(
                ModelRole.ASSISTANT, "", null, toolCall.callId(), List.of(toolCall));
    }

    /**
     * Builds a tool result message.
     *
     * @param toolName tool that produced the result
     * @param callId call the result belongs to
     * @param content result summary handed to the model
     * @return tool message
     */
    public static ModelMessage tool(String toolName, String callId, String content) {
        return new ModelMessage(ModelRole.TOOL, content, toolName, callId, List.of());
    }
}
