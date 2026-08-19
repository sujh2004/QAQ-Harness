package com.devpilot.runtime.tool;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tools and permissions available at one level of the runtime.
 *
 * <p>Scopes nest from application to project to session to agent, and an inner scope may only take
 * capabilities away. {@link #narrow(ToolScope)} therefore intersects, and
 * {@link #requireNarrowerThan(ToolScope)} rejects a configuration that tries to grant something the
 * outer scope withheld.
 *
 * @param visibleTools tool names the model may see
 * @param grantedPermissions capabilities held at this level
 * @param allowMutating whether tools that change state may be considered at all
 */
public record ToolScope(Set<String> visibleTools, Set<ToolPermission> grantedPermissions, boolean allowMutating) {

    /** Normalises the collections into immutable copies. */
    public ToolScope {
        visibleTools = visibleTools == null ? Set.of() : Set.copyOf(visibleTools);
        grantedPermissions = grantedPermissions == null ? Set.of() : Set.copyOf(grantedPermissions);
    }

    /**
     * Builds a scope that refuses every mutating tool.
     *
     * @param visibleTools tool names the model may see
     * @param grantedPermissions capabilities held at this level
     * @return read-only scope
     */
    public static ToolScope readOnly(Set<String> visibleTools, Set<ToolPermission> grantedPermissions) {
        return new ToolScope(visibleTools, grantedPermissions, false);
    }

    /**
     * Reports whether a tool is visible here.
     *
     * @param toolName tool name
     * @return whether the model may see the tool
     */
    public boolean canSee(String toolName) {
        return visibleTools.contains(toolName);
    }

    /**
     * Reports whether a capability is held here.
     *
     * @param permission capability to check
     * @return whether the scope holds it
     */
    public boolean holds(ToolPermission permission) {
        return grantedPermissions.contains(permission);
    }

    /**
     * Intersects this scope with an inner one. Anything the inner scope asks for that this scope
     * does not hold is dropped rather than granted.
     *
     * @param inner scope declared by the inner level, for example an agent profile
     * @return effective scope
     */
    public ToolScope narrow(ToolScope inner) {
        Set<String> tools = inner.visibleTools().stream()
                .filter(visibleTools::contains)
                .collect(Collectors.toUnmodifiableSet());
        Set<ToolPermission> permissions = inner.grantedPermissions().stream()
                .filter(grantedPermissions::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new ToolScope(tools, permissions, allowMutating && inner.allowMutating());
    }

    /**
     * Verifies that this scope only takes capabilities away from the given outer scope.
     *
     * @param outer scope of the enclosing level
     * @throws ToolScopeViolationException when this scope grants anything the outer scope withheld
     */
    public void requireNarrowerThan(ToolScope outer) {
        Set<String> extraTools = visibleTools.stream()
                .filter(tool -> !outer.visibleTools().contains(tool))
                .collect(Collectors.toUnmodifiableSet());
        Set<ToolPermission> extraPermissions = grantedPermissions.stream()
                .filter(permission -> !outer.grantedPermissions().contains(permission))
                .collect(Collectors.toUnmodifiableSet());
        if (!extraTools.isEmpty() || !extraPermissions.isEmpty()) {
            throw new ToolScopeViolationException(
                    "Inner scope tries to widen the outer scope; extra tools " + extraTools
                            + ", extra permissions " + extraPermissions);
        }
        if (allowMutating && !outer.allowMutating()) {
            throw new ToolScopeViolationException("Inner scope tries to re-enable mutating tools");
        }
    }
}
