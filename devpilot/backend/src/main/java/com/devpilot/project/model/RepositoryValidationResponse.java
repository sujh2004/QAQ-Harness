package com.devpilot.project.model;

/**
 * Outcome of checking a configured repository path.
 *
 * <p>This is reported as data rather than thrown, so the UI can show exactly which check failed
 * while the project stays editable.
 *
 * @param repositoryPath path as configured
 * @param resolvedPath absolute, normalised path the runtime would read from
 * @param exists whether the path exists
 * @param directory whether the path is a directory
 * @param readable whether the process can read it
 * @param gitRepository whether it contains a {@code .git} entry
 * @param accessible whether every required check passed
 * @param detail safe explanation of the outcome
 */
public record RepositoryValidationResponse(
        String repositoryPath,
        String resolvedPath,
        boolean exists,
        boolean directory,
        boolean readable,
        boolean gitRepository,
        boolean accessible,
        String detail) {
}
