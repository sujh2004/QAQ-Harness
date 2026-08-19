package com.devpilot.project.model;

import java.time.LocalDateTime;

/**
 * Project as returned by the API.
 *
 * @param id project identity
 * @param name display name
 * @param code unique short code
 * @param description description
 * @param repositoryPath configured local repository path
 * @param defaultBranch default branch name
 * @param status 1 for active, 0 for archived
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record ProjectResponse(
        Long id,
        String name,
        String code,
        String description,
        String repositoryPath,
        String defaultBranch,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Converts a database row.
     *
     * @param row stored project
     * @return API representation
     */
    public static ProjectResponse from(ProjectRow row) {
        return new ProjectResponse(
                row.getId(),
                row.getName(),
                row.getCode(),
                row.getDescription(),
                row.getRepositoryPath(),
                row.getDefaultBranch(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
