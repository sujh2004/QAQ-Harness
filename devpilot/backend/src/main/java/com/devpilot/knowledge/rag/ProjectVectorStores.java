package com.devpilot.knowledge.rag;

import com.devpilot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one vector store per project, persisted to its own file.
 *
 * <p>Isolation is physical rather than a metadata filter: project 1's chunks live in
 * {@code project-1.json} and are never loaded into the same index as project 2's. A filter that is
 * forgotten leaks knowledge across teams; a separate store cannot be forgotten.
 *
 * <p>Stores are persisted so a restart does not silently lose the index while the documents still
 * look imported in the database.
 */
@Component
public class ProjectVectorStores {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectVectorStores.class);

    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final Path vectorDir;
    private final Map<Long, SimpleVectorStore> stores = new ConcurrentHashMap<>();

    /**
     * Creates the store registry.
     *
     * @param embeddingModels embedding provider, resolved lazily so the application starts without
     *     model credentials
     * @param appProperties application configuration supplying the vector directory
     */
    public ProjectVectorStores(
            ObjectProvider<EmbeddingModel> embeddingModels, AppProperties appProperties) {
        this.embeddingModels = embeddingModels;
        this.vectorDir = Path.of(appProperties.knowledge().vectorDir()).toAbsolutePath().normalize();
    }

    /**
     * Adds chunks to a project index and persists it.
     *
     * @param projectId owning project
     * @param documents chunks with their metadata
     */
    public void add(long projectId, List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }
        SimpleVectorStore store = storeOf(projectId);
        store.add(documents);
        persist(projectId, store);
    }

    /**
     * Searches one project index.
     *
     * @param projectId owning project
     * @param query natural-language query
     * @param topK how many chunks to return at most
     * @param similarityThreshold lowest acceptable score
     * @return matching chunks, best first
     */
    public List<Document> search(long projectId, String query, int topK, double similarityThreshold) {
        List<Document> matches = storeOf(projectId).similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        return matches == null ? List.of() : matches;
    }

    /**
     * Drops a project index entirely, on disk and in memory.
     *
     * <p>Used when documents are removed or reindexed: rebuilding from the stored documents is
     * cheap and leaves no orphaned chunk that a search could still return.
     *
     * @param projectId owning project
     */
    public void reset(long projectId) {
        stores.remove(projectId);
        try {
            Files.deleteIfExists(fileOf(projectId));
        } catch (IOException exception) {
            throw new UncheckedIOException("Vector store of project " + projectId + " could not be reset",
                    exception);
        }
    }

    private SimpleVectorStore storeOf(long projectId) {
        return stores.computeIfAbsent(projectId, id -> {
            EmbeddingModel embeddingModel = embeddingModels.getIfAvailable();
            if (embeddingModel == null) {
                throw new KnowledgeUnavailableException(
                        "No embedding model is configured. Set DASHSCOPE_API_KEY and activate the "
                                + "\"dashscope\" profile to use the knowledge base.");
            }
            SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
            File file = fileOf(id).toFile();
            if (file.exists()) {
                store.load(file);
                LOGGER.info("Loaded vector store of project {} from {}", id, file);
            }
            return store;
        });
    }

    private void persist(long projectId, SimpleVectorStore store) {
        try {
            Files.createDirectories(vectorDir);
            store.save(fileOf(projectId).toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Vector store of project " + projectId + " could not be saved", exception);
        }
    }

    private Path fileOf(long projectId) {
        return vectorDir.resolve("project-" + projectId + ".json");
    }
}
