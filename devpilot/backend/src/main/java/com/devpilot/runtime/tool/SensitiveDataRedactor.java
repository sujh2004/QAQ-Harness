package com.devpilot.runtime.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes credential-shaped content from anything on its way into the event log or the model.
 *
 * <p>Redaction is enforced in code rather than requested in a prompt: arguments are cleaned before
 * {@code tool_call_requested} is written, and results are cleaned before they reach either the
 * model or {@code tool_call_finished}.
 */
@Component
public class SensitiveDataRedactor {

    /** Replacement written in place of a redacted value. */
    public static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEY_SUFFIXES =
            Set.of("key", "token", "secret", "password", "credential", "credentials", "authorization");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern VENDOR_API_KEY = Pattern.compile("(?i)\\b[a-z]{2}-[A-Za-z0-9]{16,}\\b");

    /**
     * Redacts a map of tool arguments.
     *
     * @param arguments raw arguments
     * @return arguments with credential-shaped entries masked
     */
    public Map<String, Object> redactArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> redacted = new LinkedHashMap<>(arguments.size());
        arguments.forEach((key, value) -> redacted.put(key, redactEntry(key, value)));
        return Collections.unmodifiableMap(redacted);
    }

    /**
     * Redacts an arbitrary value, walking maps and collections.
     *
     * @param value value to clean
     * @return cleaned value
     */
    public Object redact(Object value) {
        return redactEntry(null, value);
    }

    /**
     * Redacts credential-shaped substrings in free text.
     *
     * @param text text to clean, may be null
     * @return cleaned text, null when the input was null
     */
    public String redactText(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = BEARER_TOKEN.matcher(text).replaceAll("Bearer " + MASK);
        return VENDOR_API_KEY.matcher(cleaned).replaceAll(MASK);
    }

    private Object redactEntry(String key, Object value) {
        if (key != null && isSensitiveKey(key)) {
            return MASK;
        }
        return switch (value) {
            case null -> null;
            case String text -> redactText(text);
            case Map<?, ?> map -> redactMap(map);
            case Collection<?> collection -> redactCollection(collection);
            default -> value;
        };
    }

    private Object redactMap(Map<?, ?> map) {
        Map<String, Object> redacted = new LinkedHashMap<>(map.size());
        map.forEach((key, value) -> {
            String name = String.valueOf(key);
            redacted.put(name, redactEntry(name, value));
        });
        return redacted;
    }

    private Object redactCollection(Collection<?> collection) {
        List<Object> redacted = new ArrayList<>(collection.size());
        for (Object item : collection) {
            redacted.add(redactEntry(null, item));
        }
        return redacted;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase().replaceAll("[^a-z]", "");
        return SENSITIVE_KEY_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }
}
