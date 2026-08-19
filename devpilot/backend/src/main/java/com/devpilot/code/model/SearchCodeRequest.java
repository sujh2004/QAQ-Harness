package com.devpilot.code.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to search the text of a project repository.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param keyword literal text to look for, matched case-insensitively
 * @param filePattern glob restricting which files are searched, for example {@code *.java}
 * @param limit maximum number of matches to return, defaults to 30
 */
public record SearchCodeRequest(
        Long projectId,
        @NotBlank @Size(max = 200) String keyword,
        @Size(max = 100) String filePattern,
        @Min(1) @Max(200) Integer limit) {

    /** Applies the default limit so a model does not have to supply it. */
    public SearchCodeRequest {
        limit = limit == null ? 30 : limit;
    }
}
