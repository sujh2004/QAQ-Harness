package com.devpilot.code.model;

import java.util.List;

/**
 * One line matching a search, with a little surrounding context so the reader can judge relevance
 * without opening the file.
 *
 * @param filePath repository-relative path, using forward slashes
 * @param lineNumber one-based line number of the match
 * @param lineText text of the matching line
 * @param contextBefore lines immediately before the match
 * @param contextAfter lines immediately after the match
 */
public record CodeMatch(
        String filePath,
        int lineNumber,
        String lineText,
        List<String> contextBefore,
        List<String> contextAfter) {

    /** Normalises the context lists into immutable copies. */
    public CodeMatch {
        contextBefore = contextBefore == null ? List.of() : List.copyOf(contextBefore);
        contextAfter = contextAfter == null ? List.of() : List.copyOf(contextAfter);
    }
}
