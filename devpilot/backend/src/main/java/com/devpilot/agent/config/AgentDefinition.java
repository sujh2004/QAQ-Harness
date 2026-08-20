package com.devpilot.agent.config;

import com.devpilot.runtime.tool.ToolPermission;

import java.util.Set;

/**
 * One agent as declared by a profile.
 *
 * <p>An agent is a composition, not a subclass: a persona file, a model route, a tool view and a
 * step budget. Adding an agent means adding a profile entry, not changing the agent loop.
 *
 * @param name stable identifier the runtime and the audit trail use
 * @param displayName label shown in the UI
 * @param description what the agent is for, used when routing work to it
 * @param promptFile persona file under {@code resources/prompts}
 * @param modelRoute logical model route resolved by the model gateway
 * @param maxSteps largest number of model requests one turn may make
 * @param tools tool names this agent may see
 * @param permissions capabilities this agent holds
 * @param allowMutating whether this agent may run tools that change state; still subject to the
 *     policy allow list at execution time
 * @param allowSkills whether this agent may see the skills a person enabled for the project;
 *     skills are installed at runtime, so a profile can only grant the category — which specific
 *     skills exist is decided per project, and running one still needs approval in the session
 */
public record AgentDefinition(
        String name,
        String displayName,
        String description,
        String promptFile,
        String modelRoute,
        int maxSteps,
        Set<String> tools,
        Set<ToolPermission> permissions,
        boolean allowMutating,
        boolean allowSkills) {

    /** Normalises the collections into immutable copies. */
    public AgentDefinition {
        tools = tools == null ? Set.of() : Set.copyOf(tools);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
