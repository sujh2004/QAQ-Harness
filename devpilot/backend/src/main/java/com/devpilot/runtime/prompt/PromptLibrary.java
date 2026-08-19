package com.devpilot.runtime.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads agent prompts from {@code resources/prompts}.
 *
 * <p>Prompts live in files rather than in Java strings so they can be reviewed, versioned and
 * changed without recompiling, and so a profile can point different agents at different personas.
 */
@Component
public class PromptLibrary {

    private static final String ROOT = "prompts/";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Loads a prompt by its path under {@code resources/prompts}.
     *
     * @param promptFile path such as {@code agents/debug_agent.md}
     * @return prompt text
     * @throws IllegalArgumentException when the prompt file does not exist
     */
    public String load(String promptFile) {
        return cache.computeIfAbsent(promptFile, file -> {
            ClassPathResource resource = new ClassPathResource(ROOT + file);
            if (!resource.exists()) {
                throw new IllegalArgumentException("Prompt file not found: " + ROOT + file);
            }
            try (var stream = resource.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException exception) {
                throw new UncheckedIOException("Prompt file cannot be read: " + ROOT + file, exception);
            }
        });
    }
}
