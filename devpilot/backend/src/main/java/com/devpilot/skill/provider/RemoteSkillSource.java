package com.devpilot.skill.provider;

import com.devpilot.config.AppProperties;
import com.devpilot.skill.SkillSource;
import com.devpilot.skill.SkillSourceException;
import com.devpilot.skill.model.SkillPackage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Reads a skill marketplace from an HTTPS manifest.
 *
 * <p>The manifest is a single JSON document listing packages with their files inline. Fetching a
 * catalogue is therefore one request and installs nothing — a package only reaches disk when a
 * person asks for it.
 *
 * <p>Plain HTTP is refused: a marketplace that can be rewritten in transit is a code-execution
 * channel, and skills execute.
 */
@Component
public class RemoteSkillSource implements SkillSource {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final String manifestUrl;
    private final HttpClient httpClient;

    /**
     * Creates the source.
     *
     * @param objectMapper shared JSON mapper
     * @param appProperties application configuration supplying the marketplace URL
     */
    public RemoteSkillSource(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.manifestUrl = appProperties.skill().marketplaceUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<SkillPackage> catalogue() {
        Map<String, Object> manifest = readManifest();
        Object skills = manifest.get("skills");
        if (!(skills instanceof List<?> entries)) {
            throw new SkillSourceException("Marketplace manifest has no \"skills\" array");
        }
        return entries.stream()
                .map(entry -> objectMapper.convertValue(entry, SkillPackage.class))
                .toList();
    }

    @Override
    public SkillPackage fetch(String skillKey) {
        return catalogue().stream()
                .filter(candidate -> candidate.key().equals(skillKey))
                .findFirst()
                .orElseThrow(() -> new SkillSourceException(
                        "Marketplace does not offer a skill called " + skillKey));
    }

    @Override
    public String origin() {
        return manifestUrl == null || manifestUrl.isBlank() ? "(not configured)" : manifestUrl;
    }

    private Map<String, Object> readManifest() {
        if (manifestUrl == null || manifestUrl.isBlank()) {
            throw new SkillSourceException(
                    "No skill marketplace is configured. Set SKILL_MARKETPLACE_URL to an HTTPS "
                            + "manifest.");
        }

        URI uri;
        try {
            uri = new URI(manifestUrl);
        } catch (URISyntaxException exception) {
            throw new SkillSourceException("Marketplace URL is not a valid URI");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SkillSourceException(
                    "Marketplace must be served over HTTPS; skills execute, so a manifest that can "
                            + "be rewritten in transit is a code-execution channel");
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new SkillSourceException(
                        "Marketplace responded with HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body.length > MAX_MANIFEST_BYTES) {
                throw new SkillSourceException("Marketplace manifest is larger than the 2 MiB limit");
            }
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() { });
        } catch (IOException exception) {
            throw new SkillSourceException("Marketplace could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SkillSourceException("Marketplace read was interrupted");
        }
    }
}
