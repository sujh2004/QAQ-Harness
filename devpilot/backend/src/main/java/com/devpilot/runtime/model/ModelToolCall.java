package com.devpilot.runtime.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tool call the model asked for.
 *
 * @param callId identifier supplied by the model provider
 * @param toolName requested tool name
 * @param arguments raw arguments, validated later by the tool pipeline
 */
public record ModelToolCall(String callId, String toolName, Map<String, Object> arguments) {

    /** Defensively copies the argument map, which may legitimately contain null values. */
    public ModelToolCall {
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
