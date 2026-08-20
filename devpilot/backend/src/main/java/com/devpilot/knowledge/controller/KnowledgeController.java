package com.devpilot.knowledge.controller;

import com.devpilot.common.api.Result;
import com.devpilot.knowledge.model.ImportDocumentRequest;
import com.devpilot.knowledge.model.KnowledgeDocumentResponse;
import com.devpilot.knowledge.model.KnowledgeMatch;
import com.devpilot.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Knowledge base endpoints of one project. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * Creates the controller.
     *
     * @param knowledgeService knowledge application service
     */
    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * Lists the imported documents.
     *
     * @param projectId owning project
     * @return imported documents
     */
    @GetMapping
    public Result<List<KnowledgeDocumentResponse>> list(@PathVariable long projectId) {
        return Result.success(knowledgeService.list(projectId));
    }

    /**
     * Imports and indexes one document.
     *
     * @param projectId owning project
     * @param request document name, type and content
     * @return the imported document
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocumentResponse> upload(
            @PathVariable long projectId, @Valid @RequestBody ImportDocumentRequest request) {
        return Result.success(knowledgeService.importDocument(projectId, request));
    }

    /**
     * Rebuilds the whole index from the stored documents.
     *
     * @param projectId owning project
     * @return the documents after reindexing
     */
    @PostMapping("/reindex")
    public Result<List<KnowledgeDocumentResponse>> reindex(@PathVariable long projectId) {
        knowledgeService.reindex(projectId);
        return Result.success(knowledgeService.list(projectId));
    }

    /**
     * Retrieves passages relevant to a query. Exposed for the knowledge page and for verifying
     * retrieval quality without going through an agent.
     *
     * @param projectId owning project
     * @param query natural-language query
     * @param topK how many passages to return
     * @return matching passages, best first
     */
    @GetMapping("/search")
    public Result<List<KnowledgeMatch>> search(
            @PathVariable long projectId,
            @RequestParam String query,
            @RequestParam(required = false) Integer topK) {
        return Result.success(knowledgeService.search(projectId, query, topK, null));
    }

    /**
     * Removes one document and rebuilds the index without it.
     *
     * @param projectId owning project
     * @param documentId document to remove
     * @return empty success response
     */
    @DeleteMapping("/{documentId}")
    public Result<Void> delete(@PathVariable long projectId, @PathVariable long documentId) {
        knowledgeService.delete(projectId, documentId);
        return Result.success(null);
    }
}
