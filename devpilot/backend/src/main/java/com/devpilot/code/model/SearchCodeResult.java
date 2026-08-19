package com.devpilot.code.model;

import java.util.List;

/**
 * Matches found by a code search.
 *
 * @param matches matching lines in scan order
 * @param scannedFiles how many files were opened
 * @param truncated whether the search stopped at the requested limit
 */
public record SearchCodeResult(List<CodeMatch> matches, int scannedFiles, boolean truncated) {

    /** Normalises the match list into an immutable copy. */
    public SearchCodeResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
