package com.devpilot.agent.runtime;

import com.devpilot.agent.config.AgentDefinition;
import com.devpilot.agent.config.AgentProfile;
import com.devpilot.agent.config.AgentProfileLoader;
import com.devpilot.runtime.model.ModelToolSpec;
import com.devpilot.runtime.prompt.PromptLibrary;
import com.devpilot.runtime.tool.ToolDefinition;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves what an agent is allowed to see and say.
 *
 * <p>Composition happens here: a profile entry plus a persona file plus a tool view. The scope an
 * agent runs with is the application scope narrowed by its profile, and a profile that tries to
 * widen it is rejected rather than quietly trimmed.
 */
@Component
public class AgentRegistry {

    private final AgentProfile profile;
    private final PromptLibrary promptLibrary;
    private final ToolRegistry toolRegistry;

    /**
     * Creates the registry.
     *
     * @param profileLoader loaded agent profile
     * @param promptLibrary persona loader
     * @param toolRegistry registry of published tools
     */
    public AgentRegistry(
            AgentProfileLoader profileLoader, PromptLibrary promptLibrary, ToolRegistry toolRegistry) {
        this.profile = profileLoader.profile();
        this.promptLibrary = promptLibrary;
        this.toolRegistry = toolRegistry;
    }

    /** @return version of the active profile */
    public String profileVersion() {
        return profile.version();
    }

    /**
     * Looks up an agent.
     *
     * @param agentName agent identifier
     * @return the agent declaration
     */
    public AgentDefinition require(String agentName) {
        return profile.require(agentName);
    }

    /**
     * Loads the persona of an agent.
     *
     * @param agent agent declaration
     * @return system prompt text
     */
    public String systemPrompt(AgentDefinition agent) {
        return promptLibrary.load(agent.promptFile());
    }

    /**
     * Computes the effective tool scope of an agent.
     *
     * @param agent agent declaration
     * @return application scope narrowed by the agent profile
     * @throws com.devpilot.runtime.tool.ToolScopeViolationException when the profile asks for a tool
     *     or permission the application does not grant
     */
    public ToolScope scopeOf(AgentDefinition agent) {
        ToolScope application = applicationScope();
        ToolScope declared = ToolScope.readOnly(agent.tools(), agent.permissions());
        declared.requireNarrowerThan(application);
        return application.narrow(declared);
    }

    /**
     * Lists the tools an agent may advertise to a model.
     *
     * @param scope effective scope
     * @return model-facing tool specifications
     */
    public List<ModelToolSpec> toolSpecs(ToolScope scope) {
        return toolRegistry.visibleTools(scope).stream()
                .map(definition -> new ModelToolSpec(
                        definition.name(), definition.description(), definition.inputSchema()))
                .toList();
    }

    /**
     * Builds the outermost scope: every registered tool, read-only.
     *
     * <p>The MVP runs read-only, so mutation is withheld here rather than in each profile. Enabling
     * a write tool later is a deliberate change at this one place plus the policy allow list.
     *
     * @return application scope
     */
    private ToolScope applicationScope() {
        List<ToolDefinition> registered = toolRegistry.registeredTools();
        Set<String> names = registered.stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<ToolPermission> permissions = registered.stream()
                .map(ToolDefinition::requiredPermission)
                .collect(Collectors.toUnmodifiableSet());
        return ToolScope.readOnly(names, permissions);
    }
}
