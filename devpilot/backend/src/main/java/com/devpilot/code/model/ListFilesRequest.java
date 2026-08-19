package com.devpilot.code.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to list files under a directory of a project repository.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param relativePath directory relative to the repository root, empty for the root
 * @param maxDepth how many directory levels to descend, defaults to 3
 * @param limit maximum number of paths to return, defaults to 100
 */
public record ListFilesRequest(
        @NotNull Long projectId,
        @Size(max = 500) String relativePath,
        @Min(1) @Max(10) Integer maxDepth,
        @Min(1) @Max(500) Integer limit) {

    /** Applies the defaults so a model does not have to supply every bound. */
    public ListFilesRequest {
        relativePath = relativePath == null ? "" : relativePath;
        maxDepth = maxDepth == null ? 3 : maxDepth;
        limit = limit == null ? 100 : limit;
    }
}
