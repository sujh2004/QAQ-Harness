package com.devpilot.knowledge;

import com.devpilot.agent.tool.AgentToolFixtures;
import com.devpilot.agent.tool.knowledge.KnowledgeTools;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.knowledge.model.ImportDocumentRequest;
import com.devpilot.knowledge.model.KnowledgeDocumentResponse;
import com.devpilot.knowledge.model.KnowledgeMatch;
import com.devpilot.knowledge.service.KnowledgeService;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.tool.ToolExecutionResult;
import com.devpilot.runtime.tool.ToolInvocation;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: retrieval only ever sees the calling project's own documents, every passage carries
 * its source document, deletion really removes content from the index, and the model-facing tools
 * say plainly when nothing relevant exists instead of leaving silence to be filled with priors.
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeBaseTest {

    /** Deterministic embedding: character uni- and bigrams hashed into one vector. */
    @TestConfiguration
    static class EmbeddingStubConfiguration {

        @Bean
        EmbeddingModel embeddingModel() {
            return new HashingEmbeddingModel();
        }
    }

    private static final ToolScope KNOWLEDGE_SCOPE = ToolScope.readOnly(
            Set.of(KnowledgeTools.SEARCH_KNOWLEDGE, KnowledgeTools.LIST_KNOWLEDGE_DOCUMENTS),
            Set.of(ToolPermission.KNOWLEDGE_READ));

    private static final ToolScope SCOPE_WITHOUT_PERMISSION = ToolScope.readOnly(
            Set.of(KnowledgeTools.SEARCH_KNOWLEDGE), Set.of());

    @DynamicPropertySource
    static void knowledgeProperties(DynamicPropertyRegistry registry) throws IOException {
        Path vectorDir = Files.createTempDirectory("devpilot-test-vector");
        registry.add("app.knowledge.vector-dir", vectorDir::toString);
        // The hashing embedding spreads scores wider than a trained model; what matters here is
        // ordering and isolation, so the default threshold is loosened for these tests.
        registry.add("app.knowledge.similarity-threshold", () -> "0.1");
    }

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    private long projectId;
    private String sessionId;
    private String turnId;

    @BeforeEach
    void openSession() {
        projectId = AgentToolFixtures.newProject(projectService, "/srv/repos/knowledge");
        String[] ids = AgentToolFixtures.newSessionTurn(chatSessionService, lifecycleService, projectId);
        sessionId = ids[0];
        turnId = ids[1];
    }

    @Test
    void retrievesPassagesWithTheirSourceDocument() {
        importCouponIncidentReview();

        List<KnowledgeMatch> matches = knowledgeService.search(projectId, "优惠券服务降级返回 null 怎么处理", null, null);

        assertThat(matches).isNotEmpty();
        assertThat(matches.getFirst().documentName()).isEqualTo("优惠券空指针复盘.md");
        assertThat(matches).anySatisfy(match -> assertThat(match.chunk()).contains("判空"));
        assertThat(matches.getFirst().score()).isGreaterThan(0.1);
    }

    @Test
    void keepsProjectsIsolated() {
        importCouponIncidentReview();
        long otherProject = AgentToolFixtures.newProject(projectService, "/srv/repos/other");

        List<KnowledgeMatch> leaked = knowledgeService.search(otherProject, "优惠券 null 判空", null, null);

        assertThat(leaked).isEmpty();
    }

    @Test
    void deletedDocumentsStopBeingRetrievable() {
        KnowledgeDocumentResponse review = importCouponIncidentReview();
        importErrorCodes();

        knowledgeService.delete(projectId, review.id());

        List<KnowledgeMatch> matches = knowledgeService.search(projectId, "优惠券 降级 null 判空", null, null);
        assertThat(matches).extracting(KnowledgeMatch::documentName).doesNotContain("优惠券空指针复盘.md");
        assertThat(knowledgeService.list(projectId))
                .extracting(KnowledgeDocumentResponse::documentName)
                .containsExactly("错误码规范.md");
    }

    @Test
    void rebuildsTheIndexFromTheStoredDocuments() {
        importErrorCodes();

        knowledgeService.reindex(projectId);

        List<KnowledgeDocumentResponse> documents = knowledgeService.list(projectId);
        assertThat(documents).singleElement()
                .satisfies(document -> {
                    assertThat(document.vectorStatus()).isEqualTo("INDEXED");
                    assertThat(document.chunkCount()).isPositive();
                });
        assertThat(knowledgeService.search(projectId, "错误码 六位数字", null, null)).isNotEmpty();
    }

    @Test
    void searchToolReturnsMatchesWithCitations() {
        importCouponIncidentReview();

        ToolExecutionResult result = invoke(KNOWLEDGE_SCOPE, KnowledgeTools.SEARCH_KNOWLEDGE,
                Map.of("query", "优惠券服务降级返回 null 怎么处理"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.modelSummary()).contains("知识库命中");
        assertThat(result.modelSummary()).contains("优惠券空指针复盘.md");
    }

    @Test
    void searchToolSaysPlainlyWhenNothingClearsTheThreshold() {
        importCouponIncidentReview();

        ToolExecutionResult result = invoke(KNOWLEDGE_SCOPE, KnowledgeTools.SEARCH_KNOWLEDGE,
                Map.of("query", "优惠券判空规范", "similarityThreshold", 0.99));

        assertThat(result.status()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.modelSummary())
                .contains("没有")
                .contains("不要用通用知识补充");
    }

    @Test
    void listToolShowsWhatTheKnowledgeBaseCovers() {
        importCouponIncidentReview();
        importErrorCodes();

        ToolExecutionResult result = invoke(KNOWLEDGE_SCOPE, KnowledgeTools.LIST_KNOWLEDGE_DOCUMENTS, Map.of());

        assertThat(result.status()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.modelSummary()).contains("2 份知识文档");
        assertThat(result.modelSummary()).contains("优惠券空指针复盘.md").contains("错误码规范.md");
    }

    @Test
    void refusesSearchWithoutKnowledgePermission() {
        importCouponIncidentReview();

        ToolExecutionResult result = invoke(SCOPE_WITHOUT_PERMISSION, KnowledgeTools.SEARCH_KNOWLEDGE,
                Map.of("query", "优惠券判空"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
    }

    @Test
    void everyAttemptIsAuditable() {
        importCouponIncidentReview();
        invoke(KNOWLEDGE_SCOPE, KnowledgeTools.SEARCH_KNOWLEDGE, Map.of("query", "优惠券判空"));
        invoke(SCOPE_WITHOUT_PERMISSION, KnowledgeTools.SEARCH_KNOWLEDGE, Map.of("query", "优惠券判空"));

        assertThat(lifecycleService.project(sessionId).toolCalls()).hasSize(2);
        assertThat(lifecycleService.project(sessionId).openToolCalls(turnId)).isEmpty();
    }

    private KnowledgeDocumentResponse importCouponIncidentReview() {
        return knowledgeService.importDocument(projectId, new ImportDocumentRequest(
                "优惠券空指针复盘.md", "incident-review", "docs/review-coupon.md", """
                        # 故障复盘：优惠券降级引发空指针

                        ## 根因

                        CouponClient 降级返回 null，调用方未判空直接调用 getDiscountAmount()，抛出空指针。
                        整改要求所有远程调用返回值在使用前判空，降级用显式结果对象表示。

                        ## 整改

                        补齐依赖降级的回归用例，覆盖有券、无券、券服务降级三种输入。
                        """));
    }

    private KnowledgeDocumentResponse importErrorCodes() {
        return knowledgeService.importDocument(projectId, new ImportDocumentRequest(
                "错误码规范.md", "error-codes", "docs/error-codes.md", """
                        # 错误码规范

                        错误码为六位数字，前两位标识服务，后四位标识具体错误。
                        跨服务传递错误时保留原始错误码并加本服务前缀。
                        """));
    }

    private ToolExecutionResult invoke(ToolScope scope, String toolName, Map<String, Object> arguments) {
        return toolRegistry.execute(
                new ToolInvocation(sessionId, turnId, null, null, "knowledge_agent", toolName, arguments),
                scope, AgentToolFixtures.PROFILE_VERSION, projectId);
    }

    /**
     * Embedding model whose vectors only measure character overlap.
     *
     * <p>Deterministic and dependency-free, which is exactly what the isolation and citation
     * contracts need; it is not a relevance model and the tests never assert ranking quality
     * beyond "a related document is found".
     */
    static final class HashingEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSIONS = 256;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> instructions = request.getInstructions();
            return new EmbeddingResponse(java.util.stream.IntStream.range(0, instructions.size())
                    .mapToObj(index -> new Embedding(vectorOf(instructions.get(index)), index))
                    .toList());
        }

        @Override
        public float[] embed(String text) {
            return vectorOf(text);
        }

        @Override
        public float[] embed(Document document) {
            return vectorOf(document.getText());
        }

        private static float[] vectorOf(String text) {
            double[] vector = new double[DIMENSIONS];
            for (int i = 0; i < text.length(); i++) {
                vector[Math.floorMod(text.charAt(i), DIMENSIONS)] += 1;
                if (i + 1 < text.length()) {
                    vector[Math.floorMod(31 * text.charAt(i) + text.charAt(i + 1), DIMENSIONS)] += 0.5;
                }
            }
            double norm = 0;
            for (double value : vector) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            float[] result = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                result[i] = norm == 0 ? 0f : (float) (vector[i] / norm);
            }
            return result;
        }
    }
}
