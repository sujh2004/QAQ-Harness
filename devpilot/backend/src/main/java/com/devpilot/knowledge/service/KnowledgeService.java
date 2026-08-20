package com.devpilot.knowledge.service;

import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.exception.BusinessException;
import com.devpilot.config.AppProperties;
import com.devpilot.knowledge.ingest.DocumentSplitter;
import com.devpilot.knowledge.model.ImportDocumentRequest;
import com.devpilot.knowledge.model.KnowledgeDocumentResponse;
import com.devpilot.knowledge.model.KnowledgeMatch;
import com.devpilot.knowledge.persistence.KnowledgeDocumentMapper;
import com.devpilot.knowledge.persistence.KnowledgeDocumentRow;
import com.devpilot.knowledge.rag.ProjectVectorStores;
import com.devpilot.project.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports project documents and answers retrieval queries over them.
 *
 * <p>Every chunk carries its project, document id, name and type as metadata, and every search runs
 * against that project's own index. Two independent guarantees, because retrieving another team's
 * incident review into an answer is the failure this feature must not have.
 */
@Service
public class KnowledgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeService.class);
    private static final String INDEXED = "INDEXED";
    private static final String FAILED = "FAILED";

    /** Metadata key holding the owning project. */
    public static final String META_PROJECT_ID = "projectId";
    /** Metadata key holding the source document identity. */
    public static final String META_DOCUMENT_ID = "documentId";
    /** Metadata key holding the source document name. */
    public static final String META_DOCUMENT_NAME = "documentName";
    /** Metadata key holding the source document category. */
    public static final String META_DOCUMENT_TYPE = "documentType";

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentSplitter splitter;
    private final ProjectVectorStores vectorStores;
    private final ProjectService projectService;
    private final Clock clock;
    private final int defaultTopK;
    private final double defaultThreshold;

    /**
     * Creates the service.
     *
     * @param documentMapper document table access
     * @param splitter chunking strategy
     * @param vectorStores per-project vector indexes
     * @param projectService project lookup
     * @param clock runtime clock
     * @param appProperties application configuration supplying the retrieval defaults
     */
    public KnowledgeService(
            KnowledgeDocumentMapper documentMapper,
            DocumentSplitter splitter,
            ProjectVectorStores vectorStores,
            ProjectService projectService,
            Clock clock,
            AppProperties appProperties) {
        this.documentMapper = documentMapper;
        this.splitter = splitter;
        this.vectorStores = vectorStores;
        this.projectService = projectService;
        this.clock = clock;
        this.defaultTopK = appProperties.knowledge().topK();
        this.defaultThreshold = appProperties.knowledge().similarityThreshold();
    }

    /**
     * Imports one document and indexes it.
     *
     * @param projectId owning project
     * @param request document name, type and content
     * @return the imported document
     */
    @Transactional
    public KnowledgeDocumentResponse importDocument(long projectId, ImportDocumentRequest request) {
        projectService.require(projectId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));

        KnowledgeDocumentRow row = new KnowledgeDocumentRow();
        row.setProjectId(projectId);
        row.setDocumentName(request.documentName());
        row.setDocumentType(request.documentType());
        row.setSourcePath(request.sourcePath());
        row.setContent(request.content());
        row.setVectorStatus("PENDING");
        row.setChunkCount(0);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        documentMapper.insert(row);

        int chunks = index(row);
        row.setChunkCount(chunks);
        row.setVectorStatus(INDEXED);
        row.setUpdatedAt(now);
        documentMapper.updateById(row);
        return toResponse(row);
    }

    /**
     * Lists the documents of a project.
     *
     * @param projectId owning project
     * @return imported documents, newest first
     */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> list(long projectId) {
        projectService.require(projectId);
        return documentMapper.selectByProject(projectId).stream()
                .map(KnowledgeService::toResponse)
                .toList();
    }

    /**
     * Removes one document and rebuilds the project index without it.
     *
     * <p>Rebuilding rather than deleting individual vectors is what makes "deleted documents are no
     * longer retrievable" true by construction.
     *
     * @param projectId owning project
     * @param documentId document to remove
     */
    @Transactional
    public void delete(long projectId, long documentId) {
        KnowledgeDocumentRow row = documentMapper.selectById(documentId);
        if (row == null || !row.getProjectId().equals(projectId)) {
            throw new BusinessException(
                    ErrorCode.PROJECT_NOT_FOUND, "Document " + documentId + " does not belong to this project");
        }
        documentMapper.deleteById(documentId);
        reindex(projectId);
    }

    /**
     * Rebuilds the whole index of a project from the stored documents.
     *
     * @param projectId owning project
     * @return how many documents were indexed
     */
    @Transactional
    public int reindex(long projectId) {
        projectService.require(projectId);
        vectorStores.reset(projectId);
        List<KnowledgeDocumentRow> documents = documentMapper.selectByProject(projectId);
        for (KnowledgeDocumentRow row : documents) {
            try {
                row.setChunkCount(index(row));
                row.setVectorStatus(INDEXED);
            } catch (RuntimeException exception) {
                row.setVectorStatus(FAILED);
                LOGGER.error("Document {} of project {} could not be indexed",
                        row.getId(), projectId, exception);
            }
            row.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())));
            documentMapper.updateById(row);
        }
        return documents.size();
    }

    /**
     * Retrieves the chunks most relevant to a query.
     *
     * @param projectId owning project
     * @param query natural-language query
     * @param topK how many chunks to return, null for the configured default
     * @param similarityThreshold lowest acceptable score, null for the configured default
     * @return matching chunks, best first; empty when nothing clears the threshold
     */
    @Transactional(readOnly = true)
    public List<KnowledgeMatch> search(
            long projectId, String query, Integer topK, Double similarityThreshold) {
        projectService.require(projectId);
        int limit = topK == null ? defaultTopK : topK;
        double threshold = similarityThreshold == null ? defaultThreshold : similarityThreshold;

        return vectorStores.search(projectId, query, limit, threshold).stream()
                .map(document -> new KnowledgeMatch(
                        asLong(document.getMetadata().get(META_DOCUMENT_ID)),
                        String.valueOf(document.getMetadata().get(META_DOCUMENT_NAME)),
                        String.valueOf(document.getMetadata().getOrDefault(META_DOCUMENT_TYPE, "")),
                        document.getText(),
                        document.getScore() == null ? 0d : document.getScore()))
                .toList();
    }

    private int index(KnowledgeDocumentRow row) {
        List<String> chunks = splitter.split(row.getContent());
        List<Document> documents = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(META_PROJECT_ID, row.getProjectId());
            metadata.put(META_DOCUMENT_ID, row.getId());
            metadata.put(META_DOCUMENT_NAME, row.getDocumentName());
            metadata.put(META_DOCUMENT_TYPE, row.getDocumentType() == null ? "" : row.getDocumentType());
            documents.add(new Document(chunk, metadata));
        }
        vectorStores.add(row.getProjectId(), documents);
        return documents.size();
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static KnowledgeDocumentResponse toResponse(KnowledgeDocumentRow row) {
        return new KnowledgeDocumentResponse(
                row.getId(), row.getProjectId(), row.getDocumentName(), row.getDocumentType(),
                row.getSourcePath(), row.getVectorStatus(), row.getChunkCount(), row.getCreatedAt());
    }
}
