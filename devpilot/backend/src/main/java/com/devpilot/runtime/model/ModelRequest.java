package com.devpilot.runtime.model;

import java.util.List;

/**
 * One model request.
 *
 * <p>{@code credentialRef} names a credential; the value is resolved by the provider at execution
 * time. Configuration snapshots and event logs therefore never contain an API key.
 *
 * @param modelRoute logical route configured by the profile, for example {@code chat.default}
 * @param messages conversation history, already projected from committed events
 * @param tools tools the model may call
 * @param temperature sampling temperature, null to use the provider default
 * @param maxOutputTokens output cap, null to use the provider default
 * @param credentialRef name of the credential to use, never the credential itself
 */
public record ModelRequest(
        String modelRoute,
        List<ModelMessage> messages,
        List<ModelToolSpec> tools,
        Double temperature,
        Integer maxOutputTokens,
        String credentialRef) {

    /** Normalises the collections into immutable copies. */
    public ModelRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
