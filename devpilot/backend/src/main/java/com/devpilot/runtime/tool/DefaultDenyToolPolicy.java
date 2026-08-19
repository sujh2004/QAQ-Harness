package com.devpilot.runtime.tool;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The MVP policy: everything is refused unless it is explicitly read-only and in scope.
 *
 * <p>Unknown agents, invisible tools and missing permissions are denied. Tools that change state
 * are denied as well, unless the deployment lists them in {@code app.runtime.tool.mutating-allow-list}
 * and the scope allows mutation — in which case they still go through human approval when the tool
 * declares it.
 */
@Component
public class DefaultDenyToolPolicy implements ToolPolicy {

    private final Set<String> mutatingAllowList;

    /**
     * Creates the policy from application configuration.
     *
     * @param appProperties application configuration
     */
    @Autowired
    public DefaultDenyToolPolicy(AppProperties appProperties) {
        this(appProperties.runtime().tool().mutatingAllowList());
    }

    /**
     * Creates the policy with an explicit allow list.
     *
     * @param mutatingAllowList tool names allowed to change state, null for none
     */
    public DefaultDenyToolPolicy(List<String> mutatingAllowList) {
        this.mutatingAllowList = mutatingAllowList == null ? Set.of() : Set.copyOf(mutatingAllowList);
    }

    @Override
    public ToolPolicyDecision decide(ToolPolicyContext context) {
        ToolInvocation invocation = context.invocation();
        ToolDefinition definition = context.definition();
        ToolScope scope = context.scope();

        if (invocation.agentName() == null || invocation.agentName().isBlank()) {
            return ToolPolicyDecision.deny(ToolErrorCode.PERMISSION_DENIED, "Calling agent is not identified");
        }
        if (!scope.canSee(definition.name())) {
            return ToolPolicyDecision.deny(
                    ToolErrorCode.TOOL_NOT_VISIBLE,
                    "Tool " + definition.name() + " is not available to agent " + invocation.agentName());
        }
        if (!scope.holds(definition.requiredPermission())) {
            return ToolPolicyDecision.deny(
                    ToolErrorCode.PERMISSION_DENIED,
                    "Agent " + invocation.agentName() + " does not hold " + definition.requiredPermission());
        }
        if (definition.sideEffect() == SideEffectLevel.MUTATING) {
            if (!scope.allowMutating() || !mutatingAllowList.contains(definition.name())) {
                return ToolPolicyDecision.deny(
                        ToolErrorCode.PERMISSION_DENIED,
                        "Tool " + definition.name() + " changes state and is not allowed in this deployment");
            }
        }
        if (definition.requiresApproval()) {
            return ToolPolicyDecision.requireApproval(
                    "Tool " + definition.name() + " needs human approval for these exact arguments");
        }
        return ToolPolicyDecision.allow();
    }
}
