package com.devpilot.skill.model;

import java.time.LocalDateTime;

/**
 * An installed skill as returned by the API.
 *
 * @param id row identity
 * @param skillKey stable identifier
 * @param name human-readable name
 * @param version package version
 * @param description what the skill does
 * @param runtime runtime the script is launched with
 * @param entrypoint script path inside the package
 * @param sourceUrl marketplace the package came from
 * @param checksum SHA-256 of the installed content
 * @param status INSTALLED or DISABLED
 * @param installedAt installation time
 */
public record SkillResponse(
        Long id,
        String skillKey,
        String name,
        String version,
        String description,
        String runtime,
        String entrypoint,
        String sourceUrl,
        String checksum,
        String status,
        LocalDateTime installedAt) {
}
