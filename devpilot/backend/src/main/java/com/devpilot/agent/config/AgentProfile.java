package com.devpilot.agent.config;

import java.util.Map;
import java.util.Set;

/**
 * A versioned set of agents.
 *
 * <p>The version is pinned onto every session when it is created, so replaying a session always
 * uses the composition it actually ran under.
 *
 * @param version profile version, for example {@code standard@1}
 * @param agents agents by name
 */
public record AgentProfile(String version, Map<String, AgentDefinition> agents) {

    /** Normalises the agent map into an immutable copy. */
    public AgentProfile {
        agents = agents == null ? Map.of() : Map.copyOf(agents);
    }

    /**
     * Looks up an agent.
     *
     * @param agentName agent identifier
     * @return the agent declaration
     * @throws IllegalArgumentException when the profile declares no such agent
     */
    public AgentDefinition require(String agentName) {
        AgentDefinition agent = agents.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException(
                    "Profile " + version + " declares no agent named " + agentName);
        }
        return agent;
    }

    /** @return names of every declared agent */
    public Set<String> agentNames() {
        return agents.keySet();
    }
}
