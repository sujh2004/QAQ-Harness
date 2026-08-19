package com.devpilot.runtime.tool;

import java.util.List;
import java.util.Optional;

/**
 * The single entry point through which a model-initiated tool call may run.
 *
 * <p>Agents never reach a provider directly. Resolution, argument validation, authorisation,
 * approval, timeouts, concurrency, result limits and the {@code tool_call_requested} /
 * {@code tool_call_finished} events all happen here, so no caller can skip a step.
 */
public interface ToolRegistry {

    /**
     * Registers a tool and its provider.
     *
     * @param definition complete tool declaration
     * @param handler provider implementation
     * @throws IllegalStateException when the name is already registered
     */
    void register(ToolDefinition definition, ToolHandler<?> handler);

    /**
     * Looks up a registered tool.
     *
     * @param toolName tool name
     * @return declaration, empty when nothing is registered under the name
     */
    Optional<ToolDefinition> find(String toolName);

    /**
     * Lists every registered tool, regardless of scope. Used to record the capability set a session
     * started with.
     *
     * @return all declarations ordered by name
     */
    List<ToolDefinition> registeredTools();

    /**
     * Lists the tools a scope may expose to a model.
     *
     * @param scope effective scope
     * @return visible declarations ordered by name
     */
    List<ToolDefinition> visibleTools(ToolScope scope);

    /**
     * Runs one tool call through the full pipeline.
     *
     * <p>Failures are returned as data, never thrown: an unknown tool, an out-of-scope tool, bad
     * arguments, a policy denial, a refused approval, a timeout and a provider fault all produce a
     * terminal result and a matching pair of events.
     *
     * @param invocation the model request
     * @param scope effective scope of the calling agent
     * @param profileVersion agent profile version pinned for the session
     * @param projectId owning project, may be null until the project module exists
     * @return terminal outcome of the call
     */
    ToolExecutionResult execute(ToolInvocation invocation, ToolScope scope, String profileVersion, Long projectId);
}
