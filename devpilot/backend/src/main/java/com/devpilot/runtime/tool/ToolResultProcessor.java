package com.devpilot.runtime.tool;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the declared result limits and redaction between a provider and everything downstream.
 *
 * <p>A provider result is never handed to the model or written to the event log unchanged: item
 * counts and byte sizes are capped at the stricter of the tool declaration and the application
 * defaults, and credential-shaped content is masked.
 */
@Component
public class ToolResultProcessor {

    // A summary is what the model actually reads and what the event log stores, so it has to be
    // large enough to carry evidence such as file paths, line numbers and log lines.
    private static final int SUMMARY_LIMIT = 4_000;
    private static final int PREVIEW_LIMIT = 512;

    private final ObjectMapper objectMapper;
    private final SensitiveDataRedactor redactor;
    private final AppProperties.RuntimeSettings.Tool defaults;

    /**
     * Creates the processor.
     *
     * @param objectMapper shared JSON mapper used to measure result size
     * @param redactor credential redactor
     * @param appProperties application configuration supplying the default limits
     */
    public ToolResultProcessor(
            ObjectMapper objectMapper, SensitiveDataRedactor redactor, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.redactor = redactor;
        this.defaults = appProperties.runtime().tool();
    }

    /**
     * Limits and redacts one provider result.
     *
     * @param result raw provider result
     * @param definition declaration supplying the tool-specific limits
     * @return result that is safe to hand to the model and to persist
     */
    public ProcessedToolResult process(ToolResult result, ToolDefinition definition) {
        int maxItems = Math.min(definition.maxResultItems(), defaults.maxResultItems());
        int maxBytes = Math.min(definition.maxResultBytes(), defaults.maxResultBytes());

        boolean truncated = false;
        Object data = result.data();
        if (data instanceof Collection<?> collection && collection.size() > maxItems) {
            data = collection.stream().limit(maxItems).toList();
            truncated = true;
        }
        data = redactor.redact(data);

        byte[] serialized = serialize(data);
        if (serialized.length > maxBytes) {
            data = oversizedPlaceholder(definition, serialized, maxBytes);
            truncated = true;
        }

        return new ProcessedToolResult(
                data,
                cap(redactor.redactText(result.modelSummary())),
                cap(redactor.redactText(result.persistSummary())),
                truncated);
    }

    private Map<String, Object> oversizedPlaceholder(ToolDefinition definition, byte[] serialized, int maxBytes) {
        String preview = new String(serialized, StandardCharsets.UTF_8);
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("truncated", true);
        placeholder.put("reason", "Result of " + definition.name() + " exceeded " + maxBytes + " bytes");
        placeholder.put("sizeBytes", serialized.length);
        placeholder.put("preview", preview.length() <= PREVIEW_LIMIT
                ? preview
                : preview.substring(0, PREVIEW_LIMIT) + "…");
        return Map.copyOf(placeholder);
    }

    private byte[] serialize(Object data) {
        try {
            return objectMapper.writeValueAsBytes(data == null ? List.of() : data);
        } catch (JsonProcessingException exception) {
            throw new ToolExecutionException(ToolErrorCode.PROVIDER_ERROR, "Tool result cannot be serialized");
        }
    }

    private static String cap(String summary) {
        if (summary == null) {
            return null;
        }
        return summary.length() <= SUMMARY_LIMIT ? summary : summary.substring(0, SUMMARY_LIMIT) + "…";
    }
}
