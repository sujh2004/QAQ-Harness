package com.devpilot.knowledge.rag;

import com.devpilot.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract: without an embedding model the knowledge base reports itself unavailable. */
class ProjectVectorStoresTest {

    @TempDir
    Path vectorDir;

    @Test
    void reportsWhenNoEmbeddingModelIsConfigured() {
        ProjectVectorStores stores = new ProjectVectorStores(
                providerOf(null),
                new AppProperties(null, null, null, null, null,
                        new AppProperties.Knowledge(vectorDir.toString(), 800, 150, 5, 0.6)));

        assertThatThrownBy(() -> stores.search(1, "优惠券 空指针", 5, 0.5))
                .isInstanceOf(KnowledgeUnavailableException.class)
                .hasMessageContaining("embedding model");
    }

    /**
     * Minimal provider answering the one method the stores ask for.
     *
     * @param model model to return, null for none
     * @return provider holding the model
     */
    private static ObjectProvider<EmbeddingModel> providerOf(EmbeddingModel model) {
        return new ObjectProvider<>() {
            @Override
            public EmbeddingModel getObject() {
                if (model == null) {
                    throw new IllegalStateException("no embedding model");
                }
                return model;
            }

            @Override
            public EmbeddingModel getIfAvailable() {
                return model;
            }
        };
    }
}
