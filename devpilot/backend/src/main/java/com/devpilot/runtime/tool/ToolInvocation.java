package com.devpilot.runtime.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One request from a model to run a tool.
 *
 * <p>Arguments arrive as the raw map the model produced. The pipeline binds and validates them
 * before any provider sees them.
 *
 * @param sessionId owning session
 * @param turnId owning turn
 * @param stepId owning step, may be null
 * @param runId owning agent run, may be null
 * @param agentName agent making the request
 * @param toolName tool name as sent by the model
 * @param arguments raw arguments
 */
public record ToolInvocation(
        String sessionId,
        String turnId,
        String stepId,
        String runId,
        String agentName,
        String toolName,
        Map<String, Object> arguments) {

    /** Defensively copies the argument map, which may legitimately contain null values. */
    public ToolInvocation {
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
