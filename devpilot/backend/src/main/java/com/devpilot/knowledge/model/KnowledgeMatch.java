package com.devpilot.knowledge.model;

/**
 * One chunk retrieved from the knowledge base.
 *
 * @param documentId document the chunk came from
 * @param documentName file or title, so an answer can cite its source
 * @param documentType category of the source document
 * @param chunk the retrieved text
 * @param score similarity score, higher is closer
 */
public record KnowledgeMatch(
        Long documentId, String documentName, String documentType, String chunk, double score) {
}
