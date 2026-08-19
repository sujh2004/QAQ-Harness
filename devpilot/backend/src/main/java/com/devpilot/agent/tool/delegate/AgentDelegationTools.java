package com.devpilot.agent.tool.delegate;

import com.devpilot.agent.config.AgentDefinition;
import com.devpilot.agent.config.AgentProfileLoader;
import com.devpilot.agent.runtime.AgentRuntime;
import com.devpilot.agent.runtime.AgentTurnRequest;
import com.devpilot.agent.runtime.AgentTurnResult;
import com.devpilot.agent.tool.AgentToolSupport;
import com.devpilot.runtime.lifecycle.RunStatus;
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
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes each specialist agent as a tool the supervisor can call.
 *
 * <p>Delegation reuses the tool pipeline instead of adding an orchestration engine of its own, so a
 * handed-off task gets the same scope resolution, timeout, result limiting and paired events as any
 * other call. The specialist runs as a nested agent run, which is what makes the audit trail a tree
 * rather than a flat list.
 */
@Component
public class AgentDelegationTools {

    /** Prefix of every delegation tool, for example {@code askLogAgent}. */
    public static final String TOOL_PREFIX = "ask";

    private static final String VERSION = "1";
    private static final String SUPERVISOR = "supervisor";

    private final ToolRegistry toolRegistry;
    private final AgentRuntime agentRuntime;
    private final AgentProfileLoader profileLoader;

    /**
     * Creates the contributor.
     *
     * @param toolRegistry registry the tools are published to
     * @param agentRuntime agent loop used to run the specialist
     * @param profileLoader active profile, which declares the delegatable agents
     */
    public AgentDelegationTools(
            ToolRegistry toolRegistry, AgentRuntime agentRuntime, AgentProfileLoader profileLoader) {
        this.toolRegistry = toolRegistry;
        this.agentRuntime = agentRuntime;
        this.profileLoader = profileLoader;
    }

    /**
     * Builds the delegation tool name of an agent.
     *
     * @param agentName agent identifier such as {@code log_agent}
     * @return tool name such as {@code askLogAgent}
     */
    public static String toolNameOf(String agentName) {
        StringBuilder name = new StringBuilder(TOOL_PREFIX);
        for (String part : agentName.split("_")) {
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return name.toString();
    }

    /** Publishes one delegation tool per specialist declared by the profile. */
    @PostConstruct
    public void register() {
        profileLoader.profile().agents().values().stream()
                .filter(agent -> !SUPERVISOR.equals(agent.name()))
                .forEach(agent -> toolRegistry.register(
                        definitionOf(agent),
                        (ToolHandler<DelegationArguments>) context -> delegate(agent.name(), context)));
    }

    private ToolResult delegate(String agentName, ToolExecutionContext<DelegationArguments> context) {
        AgentTurnResult result = agentRuntime.runTurn(new AgentTurnRequest(
                context.sessionId(),
                context.projectId(),
                context.turnId(),
                agentName,
                context.arguments().task(),
                // The specialist hangs under the run that delegated it, so the audit trail is a tree.
                context.runId()));

        if (result.status() != RunStatus.COMPLETED) {
            throw new ToolExecutionException(
                    ToolErrorCode.PROVIDER_ERROR,
                    agentName + " could not finish: " + result.errorMessage());
        }

        String answer = result.finalMessage() == null ? "" : result.finalMessage();
        return ToolResult.of(answer, 1, agentName + " 的结论：\n" + answer);
    }

    private static ToolDefinition definitionOf(AgentDefinition agent) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("task", AgentToolSupport.field(
                "string",
                "What this specialist should find out. Be specific and self-contained: the "
                        + "specialist does not see your conversation, only this task."));

        return ToolDefinition.builder(toolNameOf(agent.name()), DelegationArguments.class)
                .version(VERSION)
                .description("Delegate to " + agent.displayName() + ". " + agent.description())
                // Delegation itself reads nothing; whatever the specialist does is authorised
                // against the specialist's own scope when it runs.
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.EXCLUSIVE)
                .timeout(Duration.ofMinutes(3))
                .maxResultItems(1)
                .maxResultBytes(65_536)
                .requiredPermission(ToolPermission.AGENT_DELEGATE)
                .displayIntent(ToolDisplayIntent.GENERIC)
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("task")))
                .build();
    }
}
