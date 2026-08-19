package com.devpilot.code.model;

import java.util.List;

/**
 * A line range of one file.
 *
 * @param filePath repository-relative path, using forward slashes
 * @param startLine one-based first returned line
 * @param endLine one-based last returned line
 * @param totalLines number of lines in the whole file
 * @param lines returned lines, without line terminators
 * @param truncated whether the requested range was shortened to respect the read limit
 */
public record ReadCodeFileResult(
        String filePath, int startLine, int endLine, int totalLines, List<String> lines, boolean truncated) {

    /** Normalises the line list into an immutable copy. */
    public ReadCodeFileResult {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
