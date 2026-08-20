package com.devpilot.agent.tool.knowledge;

import com.devpilot.agent.tool.AgentToolSupport;
import com.devpilot.knowledge.model.KnowledgeDocumentResponse;
import com.devpilot.knowledge.model.KnowledgeMatch;
import com.devpilot.knowledge.model.ListKnowledgeArguments;
import com.devpilot.knowledge.model.SearchKnowledgeArguments;
import com.devpilot.knowledge.rag.KnowledgeUnavailableException;
import com.devpilot.knowledge.service.KnowledgeService;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.tool.ConcurrencyMode;
import com.devpilot.runtime.tool.SideEffectLevel;
import com.devpilot.runtime.tool.ToolDefinition;
import com.devpilot.runtime.tool.ToolDisplayIntent;
import com.devpilot.runtime.tool.ToolExecutionContext;
import com.devpilot.runtime.tool.ToolExecutionException;
import com.devpilot.runtime.tool.ToolHandler;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolResult;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the project knowledge base as model-visible tools.
 *
 * <p>Retrieval returns the source document name and score with every chunk, because a knowledge
 * answer without a citation is indistinguishable from the model's own prior, which is exactly what
 * this capability exists to avoid.
 */
@Component
public class KnowledgeTools {

    /** Name of the retrieval tool. */
    public static final String SEARCH_KNOWLEDGE = "searchKnowledge";
    /** Name of the document listing tool. */
    public static final String LIST_KNOWLEDGE_DOCUMENTS = "listKnowledgeDocuments";

    private static final String VERSION = "1";

    private final ToolRegistry toolRegistry;
    private final KnowledgeService knowledgeService;

    /**
     * Creates the contributor.
     *
     * @param toolRegistry registry the tools are published to
     * @param knowledgeService knowledge capability
     */
    public KnowledgeTools(ToolRegistry toolRegistry, KnowledgeService knowledgeService) {
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
    }

    /** Publishes the knowledge tools once the registry is available. */
    @PostConstruct
    public void register() {
        toolRegistry.register(
                searchDefinition(), (ToolHandler<SearchKnowledgeArguments>) this::searchKnowledge);
        toolRegistry.register(
                listDefinition(), (ToolHandler<ListKnowledgeArguments>) this::listDocuments);
    }

    private ToolResult searchKnowledge(ToolExecutionContext<SearchKnowledgeArguments> context) {
        SearchKnowledgeArguments arguments = context.arguments();
        Long projectId = AgentToolSupport.resolveProjectId(context, null);

        List<KnowledgeMatch> matches;
        try {
            matches = knowledgeService.search(
                    projectId, arguments.query(), arguments.topK(), arguments.similarityThreshold());
        } catch (KnowledgeUnavailableException exception) {
            throw new ToolExecutionException(ToolErrorCode.PROVIDER_ERROR, exception.getMessage());
        }

        if (matches.isEmpty()) {
            // Said plainly so the agent reports "the knowledge base has nothing on this" rather
            // than filling the gap from its own prior.
            return ToolResult.of(matches, 0,
                    "知识库中没有与「" + arguments.query() + "」相关度足够高的内容。"
                            + "不要用通用知识补充，如实说明未找到。");
        }

        StringBuilder summary = new StringBuilder("知识库命中 ")
                .append(matches.size()).append(" 段内容：");
        for (KnowledgeMatch match : matches) {
            summary.append("\n- ").append(match.documentName())
                    .append(match.documentType().isEmpty() ? "" : " [" + match.documentType() + "]")
                    .append("  相关度 ").append(String.format("%.2f", match.score()))
                    .append("\n  ").append(match.chunk().replace('\n', ' ').strip());
        }
        return ToolResult.of(matches, matches.size(), summary.toString());
    }

    private ToolResult listDocuments(ToolExecutionContext<ListKnowledgeArguments> context) {
        Long projectId = AgentToolSupport.resolveProjectId(context, null);
        List<KnowledgeDocumentResponse> documents = knowledgeService.list(projectId);

        StringBuilder summary = new StringBuilder("项目共有 ")
                .append(documents.size()).append(" 份知识文档：");
        for (KnowledgeDocumentResponse document : documents) {
            summary.append("\n- ").append(document.documentName())
                    .append(document.documentType() == null ? "" : " [" + document.documentType() + "]")
                    .append("  ").append(document.chunkCount()).append(" 段")
                    .append("  ").append(document.vectorStatus());
        }
        return ToolResult.of(documents, documents.size(), summary.toString());
    }

    private static ToolDefinition searchDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", AgentToolSupport.field(
                "string", "What to look for, in natural language"));
        properties.put("topK", AgentToolSupport.boundedInteger(
                "How many passages to return, default 5", 1, 20));
        properties.put("similarityThreshold", Map.of(
                "type", "number",
                "description", "Lowest acceptable relevance between 0 and 1, default 0.6",
                "minimum", 0,
                "maximum", 1));

        return ToolDefinition.builder(SEARCH_KNOWLEDGE, SearchKnowledgeArguments.class)
                .version(VERSION)
                .description("Search the project knowledge base — architecture notes, coding and API "
                        + "standards, and past incident reviews — and return matching passages with "
                        + "their source document and relevance score.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("query")))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(20))
                .maxResultItems(20)
                .maxResultBytes(131_072)
                .requiredPermission(ToolPermission.KNOWLEDGE_READ)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }

    private static ToolDefinition listDefinition() {
        return ToolDefinition.builder(LIST_KNOWLEDGE_DOCUMENTS, ListKnowledgeArguments.class)
                .version(VERSION)
                .description("List the knowledge documents imported for this project, so you can tell "
                        + "whether a topic is covered at all before searching for it.")
                .inputSchema(AgentToolSupport.objectSchema(Map.of(), List.of()))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(10))
                .maxResultItems(200)
                .maxResultBytes(32_768)
                .requiredPermission(ToolPermission.KNOWLEDGE_READ)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }
}
