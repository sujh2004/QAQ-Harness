package com.devpilot.agent.tool.skill;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.tool.ConcurrencyMode;
import com.devpilot.runtime.tool.SideEffectLevel;
import com.devpilot.runtime.tool.ToolDefinition;
import com.devpilot.runtime.tool.ToolDisplayIntent;
import com.devpilot.runtime.tool.ToolExecutionContext;
import com.devpilot.runtime.tool.ToolExecutionException;
import com.devpilot.runtime.tool.ToolHandler;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolResult;
import com.devpilot.skill.persistence.SkillMapper;
import com.devpilot.skill.persistence.SkillRow;
import com.devpilot.skill.sandbox.SkillExecutionException;
import com.devpilot.skill.sandbox.SkillExecutionResult;
import com.devpilot.skill.sandbox.SkillSandbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes installed skills as model-visible tools.
 *
 * <p>Skills are registered lazily rather than at startup, because which ones exist depends on what
 * a person installed and enabled. A skill tool is declared {@link SideEffectLevel#MUTATING} and
 * {@code requiresApproval}, so reaching the sandbox at all takes three separate human decisions:
 * install, enable for the project, approve for the session.
 */
@Component
public class SkillTools {

    /** Prefix of every skill tool, so a skill can never collide with a built-in one. */
    public static final String TOOL_PREFIX = "skill_";

    private final ToolRegistry toolRegistry;
    private final SkillMapper skillMapper;
    private final SkillSandbox sandbox;
    private final ObjectMapper objectMapper;
    private final Duration defaultTimeout;

    /**
     * Creates the contributor.
     *
     * @param toolRegistry registry the tools are published to
     * @param skillMapper installed skill lookup
     * @param sandbox confined execution
     * @param objectMapper shared JSON mapper
     * @param appProperties application configuration
     */
    public SkillTools(
            ToolRegistry toolRegistry,
            SkillMapper skillMapper,
            SkillSandbox sandbox,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        this.toolRegistry = toolRegistry;
        this.skillMapper = skillMapper;
        this.sandbox = sandbox;
        this.objectMapper = objectMapper;
        this.defaultTimeout = appProperties.skill().defaultTimeout();
    }

    /**
     * Builds the tool name of a skill.
     *
     * @param skillKey package identifier
     * @return tool name
     */
    public static String toolNameOf(String skillKey) {
        // Used verbatim so the key can be recovered exactly; rewriting characters here once made
        // skillKeyOf return a key that no longer matched anything installed.
        return TOOL_PREFIX + skillKey;
    }

    /**
     * Recovers the skill key from a tool name.
     *
     * @param toolName tool name
     * @return skill key, null when the tool is not a skill
     */
    public static String skillKeyOf(String toolName) {
        return toolName != null && toolName.startsWith(TOOL_PREFIX)
                ? toolName.substring(TOOL_PREFIX.length())
                : null;
    }

    /**
     * Publishes a skill so agents can see it.
     *
     * <p>Called when a skill is enabled rather than at startup; registering twice is harmless
     * because the registry refuses duplicates and the caller ignores that.
     *
     * @param skill installed skill
     */
    public void publish(SkillRow skill) {
        if (toolRegistry.find(toolNameOf(skill.getSkillKey())).isPresent()) {
            return;
        }
        toolRegistry.register(
                definitionOf(skill),
                (ToolHandler<Map<String, Object>>) context -> execute(skill.getSkillKey(), context));
    }

    private ToolResult execute(String skillKey, ToolExecutionContext<Map<String, Object>> context) {
        SkillRow skill = skillMapper.selectByKey(skillKey);
        if (skill == null) {
            throw new ToolExecutionException(
                    ToolErrorCode.PROVIDER_ERROR, "Skill " + skillKey + " is no longer installed");
        }
        if (context.projectId() == null
                || skillMapper.countEnabled(context.projectId(), skill.getId()) == 0) {
            throw new ToolExecutionException(
                    ToolErrorCode.PERMISSION_DENIED,
                    "Skill " + skillKey + " is not enabled for this project");
        }

        String argumentsJson = writeArguments(context.arguments());
        SkillExecutionResult result;
        try {
            result = sandbox.run(
                    Path.of(skill.getInstallPath()),
                    skill.getRuntime(),
                    skill.getEntrypoint(),
                    argumentsJson,
                    defaultTimeout);
        } catch (SkillExecutionException exception) {
            ToolErrorCode code = switch (exception.reason()) {
                case TIMEOUT -> ToolErrorCode.TIMEOUT;
                case RUNTIME_NOT_ALLOWED, ENTRYPOINT_ESCAPES_PACKAGE -> ToolErrorCode.PERMISSION_DENIED;
                case ENTRYPOINT_NOT_FOUND, LAUNCH_FAILED, SCRIPT_FAILED -> ToolErrorCode.PROVIDER_ERROR;
            };
            throw new ToolExecutionException(code, exception.getMessage());
        }

        if (!result.successful()) {
            throw new ToolExecutionException(
                    ToolErrorCode.PROVIDER_ERROR,
                    "Skill " + skillKey + " exited with status " + result.exitCode()
                            + (result.stderr().isBlank() ? "" : ": " + firstLine(result.stderr())));
        }

        String output = result.stdout();
        String summary = "Skill " + skill.getName() + " (" + skillKey + ") finished in "
                + result.durationMs() + " ms" + (result.truncated() ? " (output truncated)" : "")
                + ":\n" + output;
        return ToolResult.of(output, 1, summary);
    }

    private ToolDefinition definitionOf(SkillRow skill) {
        return ToolDefinition.builder(toolNameOf(skill.getSkillKey()), Map.class)
                .version(skill.getVersion())
                .description(skill.getDescription() == null ? skill.getName() : skill.getDescription())
                .inputSchema(readSchema(skill.getArgsSchema()))
                // A downloaded script can do anything the backend user can, so it is treated as a
                // write and gated behind approval regardless of what the package claims.
                .sideEffect(SideEffectLevel.MUTATING)
                .concurrency(ConcurrencyMode.EXCLUSIVE)
                .timeout(defaultTimeout.plusSeconds(5))
                .maxResultItems(1)
                .maxResultBytes(65_536)
                .requiredPermission(ToolPermission.SKILL_EXECUTE)
                .requiresApproval(true)
                .displayIntent(ToolDisplayIntent.TERMINAL)
                .build();
    }

    private Map<String, Object> readSchema(String argsSchema) {
        if (argsSchema == null || argsSchema.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(argsSchema, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private String writeArguments(Map<String, Object> arguments) {
        try {
            Map<String, Object> safe = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException exception) {
            throw new ToolExecutionException(
                    ToolErrorCode.INVALID_ARGUMENT, "Skill arguments cannot be serialized");
        }
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("").strip();
    }

    /**
     * Publishes every installed skill. Called once the application is up so restarts keep working.
     *
     * @return how many skills were published
     */
    public int publishAll() {
        List<SkillRow> skills = skillMapper.selectAll();
        skills.forEach(this::publish);
        return skills.size();
    }
}
