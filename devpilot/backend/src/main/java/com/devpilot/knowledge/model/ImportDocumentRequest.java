package com.devpilot.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for importing a knowledge document.
 *
 * @param documentName file or title
 * @param documentType category such as {@code incident-review}
 * @param sourcePath where it came from, for traceability
 * @param content document text
 */
public record ImportDocumentRequest(
        @NotBlank @Size(max = 255) String documentName,
        @Size(max = 50) String documentType,
        @Size(max = 500) String sourcePath,
        @NotBlank @Size(max = 400_000) String content) {
}
