package com.devpilot.agent.config;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.tool.ToolPermission;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the agent profile named by {@code app.runtime.profile.version}.
 *
 * <p>Profiles are files under {@code resources/agent-profiles} rather than Java code, so the set of
 * agents, their personas and their tool views can change without touching the agent loop.
 */
@Component
public class AgentProfileLoader {

    private static final String ROOT = "agent-profiles/";

    private final AgentProfile profile;

    /**
     * Loads the configured profile at startup so a broken profile fails the build-out immediately
     * rather than the first user request.
     *
     * @param appProperties application configuration
     */
    public AgentProfileLoader(AppProperties appProperties) {
        this.profile = load(appProperties.runtime().profile().version());
    }

    /** @return the loaded profile */
    public AgentProfile profile() {
        return profile;
    }

    private static AgentProfile load(String version) {
        String fileName = version.contains("@") ? version.substring(0, version.indexOf('@')) : version;
        ClassPathResource resource = new ClassPathResource(ROOT + fileName + ".yml");
        if (!resource.exists()) {
            throw new IllegalStateException("Agent profile not found: " + ROOT + fileName + ".yml");
        }

        Map<String, Object> root;
        try (InputStream stream = resource.getInputStream()) {
            root = new Yaml().load(stream);
        } catch (IOException exception) {
            throw new UncheckedIOException("Agent profile cannot be read: " + fileName, exception);
        }
        if (root == null) {
            throw new IllegalStateException("Agent profile " + fileName + " is empty");
        }

        String declaredVersion = String.valueOf(root.get("version"));
        if (!version.equals(declaredVersion)) {
            throw new IllegalStateException("Agent profile " + fileName + " declares version "
                    + declaredVersion + " but the application is configured for " + version);
        }

        Object agentsNode = root.get("agents");
        if (!(agentsNode instanceof Map<?, ?> agentsMap) || agentsMap.isEmpty()) {
            throw new IllegalStateException("Agent profile " + fileName + " declares no agents");
        }

        Map<String, AgentDefinition> agents = new LinkedHashMap<>();
        agentsMap.forEach((key, value) -> {
            String name = String.valueOf(key);
            if (!(value instanceof Map<?, ?> fields)) {
                throw new IllegalStateException("Agent " + name + " in profile " + fileName + " is malformed");
            }
            agents.put(name, toDefinition(name, fields, fileName));
        });
        return new AgentProfile(version, agents);
    }

    private static AgentDefinition toDefinition(String name, Map<?, ?> fields, String fileName) {
        return new AgentDefinition(
                name,
                requiredString(fields, "displayName", name, fileName),
                requiredString(fields, "description", name, fileName),
                requiredString(fields, "promptFile", name, fileName),
                requiredString(fields, "modelRoute", name, fileName),
                requiredInt(fields, "maxSteps", name, fileName),
                Set.copyOf(stringList(fields, "tools")),
                stringList(fields, "permissions").stream()
                        .map(ToolPermission::valueOf)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private static String requiredString(Map<?, ?> fields, String key, String agent, String fileName) {
        Object value = fields.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException(
                    "Agent " + agent + " in profile " + fileName + " is missing " + key);
        }
        return String.valueOf(value);
    }

    private static int requiredInt(Map<?, ?> fields, String key, String agent, String fileName) {
        Object value = fields.get(key);
        if (!(value instanceof Number number) || number.intValue() <= 0) {
            throw new IllegalStateException(
                    "Agent " + agent + " in profile " + fileName + " needs a positive " + key);
        }
        return number.intValue();
    }

    private static List<String> stringList(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(key + " must be a list");
        }
        return list.stream().map(String::valueOf).toList();
    }
}
