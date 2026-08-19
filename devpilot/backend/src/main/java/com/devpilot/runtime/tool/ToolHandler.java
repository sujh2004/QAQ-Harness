package com.devpilot.runtime.tool;

/**
 * Provider-side implementation of a tool.
 *
 * <p>Implementations own infrastructure access and may throw: the registry converts any failure
 * into a structured result and a terminal event, so a provider never has to invent error handling
 * that the model can see.
 *
 * @param <A> validated argument type
 */
@FunctionalInterface
public interface ToolHandler<A> {

    /**
     * Executes the tool.
     *
     * @param context validated arguments and call identity
     * @return normalised result
     * @throws Exception when the provider fails; the registry converts it to a safe result
     */
    ToolResult execute(ToolExecutionContext<A> context) throws Exception;
}
