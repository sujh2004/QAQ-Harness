package com.devpilot.project.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a project.
 *
 * @param name display name
 * @param code unique short code used in URLs and logs
 * @param description optional description
 * @param repositoryPath local repository path, absolute or relative to the configured base
 *     directory
 * @param defaultBranch default branch name, defaults to {@code main} when omitted
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-zA-Z0-9._-]+",
                message = "may only contain letters, digits, dot, underscore and dash") String code,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 500) String repositoryPath,
        @Size(max = 100) String defaultBranch) {
}
