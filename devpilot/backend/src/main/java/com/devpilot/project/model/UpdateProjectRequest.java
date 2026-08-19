package com.devpilot.project.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a project. The code is immutable because it identifies the project in
 * logs and knowledge metadata.
 *
 * @param name display name
 * @param description optional description
 * @param repositoryPath local repository path, absolute or relative to the configured base
 *     directory
 * @param defaultBranch default branch name
 * @param status 1 for active, 0 for archived
 */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 500) String repositoryPath,
        @Size(max = 100) String defaultBranch,
        @Min(0) @Max(1) Integer status) {
}
