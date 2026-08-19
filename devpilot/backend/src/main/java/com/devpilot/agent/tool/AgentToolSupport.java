package com.devpilot.agent.tool;

import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.tool.ToolExecutionContext;
import com.devpilot.runtime.tool.ToolExecutionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers shared by the tool consumers that expose DevPilot capabilities to a model. */
public final class AgentToolSupport {

    private AgentToolSupport() {
    }

    /**
     * Resolves the project a call may touch.
     *
     * <p>The project comes from the session, never from the model. Asking a model to repeat an id
     * it cannot verify only invites it to invent one — which is exactly what happens in practice —
     * so {@code projectId} is not published in any tool schema. If a model supplies one anyway it
     * must agree, which keeps the guard meaningful as defence in depth.
     *
     * @param context call context carrying the authoritative project
     * @param suppliedProjectId project the arguments named, usually null
     * @return the project this call may read
     * @throws ToolExecutionException when the session has no project or the two disagree
     */
    public static Long resolveProjectId(ToolExecutionContext<?> context, Long suppliedProjectId) {
        Long sessionProject = context.projectId();
        if (sessionProject == null) {
            throw new ToolExecutionException(
                    ToolErrorCode.PERMISSION_DENIED, "This session is not bound to a project");
        }
        if (suppliedProjectId != null && !sessionProject.equals(suppliedProjectId)) {
            throw new ToolExecutionException(
                    ToolErrorCode.PERMISSION_DENIED,
                    "This session may only read project " + sessionProject);
        }
        return sessionProject;
    }

    /**
     * Refuses a call whose arguments name a different project than the session it runs in.
     *
     * @param context call context carrying the authoritative project
     * @param argumentProjectId project the arguments asked for
     * @throws ToolExecutionException when the two do not agree
     */
    public static void requireSameProject(ToolExecutionContext<?> context, Long argumentProjectId) {
        if (context.projectId() == null
                || argumentProjectId == null
                || !context.projectId().equals(argumentProjectId)) {
            throw new ToolExecutionException(
                    ToolErrorCode.PERMISSION_DENIED,
                    "This session may only read project " + context.projectId());
        }
    }

    /**
     * Builds a JSON Schema object node.
     *
     * @param properties property name to schema
     * @param required names of the required properties
     * @return schema published to the model
     */
    public static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.copyOf(required));
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    /**
     * Builds a scalar property schema.
     *
     * @param type JSON Schema type
     * @param description model-facing description
     * @return property schema
     */
    public static Map<String, Object> field(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    /**
     * Builds a bounded integer property schema.
     *
     * @param description model-facing description
     * @param minimum smallest accepted value
     * @param maximum largest accepted value
     * @return property schema
     */
    public static Map<String, Object> boundedInteger(String description, int minimum, int maximum) {
        return Map.of(
                "type", "integer",
                "description", description,
                "minimum", minimum,
                "maximum", maximum);
    }
}
