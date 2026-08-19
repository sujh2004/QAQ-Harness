package com.devpilot.code.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to read a line range of one file.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param relativePath file relative to the repository root
 * @param startLine one-based first line to return, defaults to 1
 * @param endLine one-based last line to return inclusive, defaults to 200 lines after the start
 */
public record ReadCodeFileRequest(
        Long projectId,
        @NotBlank @Size(max = 500) String relativePath,
        @Min(1) Integer startLine,
        @Min(1) Integer endLine) {

    private static final int DEFAULT_WINDOW = 200;

    /** Applies the defaults so a model may ask for a file without computing a range. */
    public ReadCodeFileRequest {
        startLine = startLine == null ? 1 : startLine;
        endLine = endLine == null ? startLine + DEFAULT_WINDOW - 1 : endLine;
    }
}
