package com.devpilot.knowledge.model;

import java.time.LocalDateTime;

/**
 * An imported knowledge document as returned by the API.
 *
 * @param id document identity
 * @param projectId owning project
 * @param documentName file or title
 * @param documentType category such as {@code incident-review}
 * @param sourcePath where it came from
 * @param vectorStatus PENDING, INDEXED or FAILED
 * @param chunkCount how many chunks it produced
 * @param createdAt import time
 */
public record KnowledgeDocumentResponse(
        Long id,
        Long projectId,
        String documentName,
        String documentType,
        String sourcePath,
        String vectorStatus,
        Integer chunkCount,
        LocalDateTime createdAt) {
}
