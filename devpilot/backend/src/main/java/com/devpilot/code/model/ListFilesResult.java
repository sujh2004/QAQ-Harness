package com.devpilot.code.model;

import java.util.List;

/**
 * Files found under a directory.
 *
 * @param files repository-relative paths, using forward slashes
 * @param truncated whether the listing stopped at the requested limit
 */
public record ListFilesResult(List<String> files, boolean truncated) {

    /** Normalises the list into an immutable copy. */
    public ListFilesResult {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
