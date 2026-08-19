package com.devpilot.runtime.model;

import java.util.Map;

/**
 * A tool as advertised to the model.
 *
 * <p>This is deliberately independent of the tool registry types: a model provider must not need to
 * know how DevPilot resolves, authorises or executes a tool.
 *
 * @param name tool name the model calls
 * @param description model-facing description
 * @param inputSchema JSON Schema of the arguments
 */
public record ModelToolSpec(String name, String description, Map<String, Object> inputSchema) {

    /** Normalises the schema into an immutable copy. */
    public ModelToolSpec {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
