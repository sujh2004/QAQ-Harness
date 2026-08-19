package com.devpilot.runtime.model.provider;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Advertises a tool to a model without being able to run it.
 *
 * <p>DevPilot executes every tool through its own registry so scope, authorisation, timeouts,
 * result limits and the paired lifecycle events cannot be bypassed. Spring AI still needs a
 * callback object to publish the schema, so this one carries the declaration and refuses to
 * execute: reaching {@link #call(String)} would mean internal tool execution was left on, which is
 * a wiring bug worth failing loudly for.
 */
final class SchemaOnlyToolCallback implements ToolCallback {

    private final ToolDefinition definition;

    /**
     * Creates the callback.
     *
     * @param name tool name the model calls
     * @param description model-facing description
     * @param inputSchema JSON Schema of the arguments, serialized
     */
    SchemaOnlyToolCallback(String name, String description, String inputSchema) {
        this.definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        throw new IllegalStateException("Tool " + definition.name()
                + " must be executed by the DevPilot tool registry, not by the model provider. "
                + "Internal tool execution should be disabled on the chat options.");
    }
}
