package com.devpilot.skill.model;

import java.util.Map;

/**
 * A skill as offered by a marketplace, before anything is installed.
 *
 * <p>Packages carry their files inline rather than as an archive. That removes a whole class of
 * installation attack — there is no archive to unpack and therefore no path-traversal during
 * extraction — and keeps a package reviewable by reading the manifest.
 *
 * @param key stable identifier, unique within the marketplace
 * @param name human-readable name
 * @param version package version
 * @param description what the skill does
 * @param runtime runtime name, which must be on the interpreter allow list
 * @param entrypoint script path inside the package
 * @param argsSchema JSON Schema of the arguments the skill accepts
 * @param files file path to content, relative to the package root
 */
public record SkillPackage(
        String key,
        String name,
        String version,
        String description,
        String runtime,
        String entrypoint,
        Map<String, Object> argsSchema,
        Map<String, String> files) {

    /** Normalises the maps into immutable copies. */
    public SkillPackage {
        argsSchema = argsSchema == null ? Map.of() : Map.copyOf(argsSchema);
        files = files == null ? Map.of() : Map.copyOf(files);
    }
}
