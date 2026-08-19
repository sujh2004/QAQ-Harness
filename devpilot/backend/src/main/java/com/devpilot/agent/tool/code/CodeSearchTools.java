package com.devpilot.agent.tool.code;

import com.devpilot.agent.tool.AgentToolSupport;
import com.devpilot.code.CodeRepositoryService;
import com.devpilot.code.RepositoryAccessException;
import com.devpilot.code.model.CodeMatch;
import com.devpilot.code.model.ListFilesRequest;
import com.devpilot.code.model.ListFilesResult;
import com.devpilot.code.model.ReadCodeFileRequest;
import com.devpilot.code.model.ReadCodeFileResult;
import com.devpilot.code.model.SearchCodeRequest;
import com.devpilot.code.model.SearchCodeResult;
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
 * Exposes the code repository capability as model-visible tools.
 *
 * <p>This is the consumer side of the capability: it owns the model-facing names, schemas and
 * summaries, while all repository access, path checking and limiting stay in the provider. Nothing
 * here talks to the file system directly.
 */
@Component
public class CodeSearchTools {

    /** Name of the directory listing tool. */
    public static final String LIST_FILES = "listFiles";
    /** Name of the code search tool. */
    public static final String SEARCH_CODE = "searchCode";
    /** Name of the file reading tool. */
    public static final String READ_CODE_FILE = "readCodeFile";

    private static final String VERSION = "1";

    private final ToolRegistry toolRegistry;
    private final CodeRepositoryService repository;

    /**
     * Creates the contributor.
     *
     * @param toolRegistry registry the tools are published to
     * @param repository code repository capability
     */
    public CodeSearchTools(ToolRegistry toolRegistry, CodeRepositoryService repository) {
        this.toolRegistry = toolRegistry;
        this.repository = repository;
    }

    /** Publishes the code tools once the registry is available. */
    @PostConstruct
    public void register() {
        toolRegistry.register(listFilesDefinition(), (ToolHandler<ListFilesRequest>) this::listFiles);
        toolRegistry.register(searchCodeDefinition(), (ToolHandler<SearchCodeRequest>) this::searchCode);
        toolRegistry.register(readCodeFileDefinition(), (ToolHandler<ReadCodeFileRequest>) this::readCodeFile);
    }

    private ToolResult listFiles(ToolExecutionContext<ListFilesRequest> context) {
        ListFilesRequest arguments = new ListFilesRequest(
                AgentToolSupport.resolveProjectId(context, context.arguments().projectId()),
                context.arguments().relativePath(),
                context.arguments().maxDepth(),
                context.arguments().limit());
        ListFilesResult result = translate(() -> repository.listFiles(arguments));

        String location = arguments.relativePath().isEmpty() ? "repository root" : arguments.relativePath();
        StringBuilder summary = new StringBuilder("Listed ")
                .append(result.files().size()).append(" file(s) under ").append(location)
                .append(result.truncated() ? " (truncated)" : "").append(':');
        result.files().forEach(file -> summary.append("\n- ").append(file));
        return ToolResult.of(result.files(), result.files().size(), summary.toString());
    }

    private ToolResult searchCode(ToolExecutionContext<SearchCodeRequest> context) {
        SearchCodeRequest arguments = new SearchCodeRequest(
                AgentToolSupport.resolveProjectId(context, context.arguments().projectId()),
                context.arguments().keyword(),
                context.arguments().filePattern(),
                context.arguments().limit());
        SearchCodeResult result = translate(() -> repository.searchCode(arguments));

        StringBuilder summary = new StringBuilder("Found ")
                .append(result.matches().size()).append(" match(es) for \"").append(arguments.keyword())
                .append("\" in ").append(result.scannedFiles()).append(" file(s)")
                .append(result.truncated() ? " (truncated)" : "").append(':');
        for (CodeMatch match : result.matches()) {
            summary.append("\n- ").append(match.filePath()).append(':').append(match.lineNumber())
                    .append("  ").append(match.lineText().strip());
        }
        return ToolResult.of(result.matches(), result.matches().size(), summary.toString());
    }

    private ToolResult readCodeFile(ToolExecutionContext<ReadCodeFileRequest> context) {
        ReadCodeFileRequest arguments = new ReadCodeFileRequest(
                AgentToolSupport.resolveProjectId(context, context.arguments().projectId()),
                context.arguments().relativePath(),
                context.arguments().startLine(),
                context.arguments().endLine());
        ReadCodeFileResult result = translate(() -> repository.readFile(arguments));

        StringBuilder summary = new StringBuilder(result.filePath())
                .append(" lines ").append(result.startLine()).append('-').append(result.endLine())
                .append(" of ").append(result.totalLines())
                .append(result.truncated() ? " (truncated)" : "").append(':');
        int lineNumber = result.startLine();
        for (String line : result.lines()) {
            summary.append('\n').append(lineNumber++).append("| ").append(line);
        }
        return ToolResult.of(result, result.lines().size(), summary.toString());
    }

    /**
     * Converts a repository failure into a model-safe tool failure.
     *
     * @param call repository operation
     * @param <T> result type
     * @return repository result
     */
    private static <T> T translate(RepositoryCall<T> call) {
        try {
            return call.execute();
        } catch (RepositoryAccessException exception) {
            ToolErrorCode code = switch (exception.reason()) {
                case PATH_ESCAPES_REPOSITORY, PATH_FORBIDDEN -> ToolErrorCode.PERMISSION_DENIED;
                case PATH_NOT_FOUND, UNSUPPORTED_FILE -> ToolErrorCode.INVALID_ARGUMENT;
                case REPOSITORY_UNAVAILABLE -> ToolErrorCode.PROVIDER_ERROR;
            };
            throw new ToolExecutionException(code, exception.getMessage());
        }
    }

    private static ToolDefinition listFilesDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("relativePath", AgentToolSupport.field(
                "string", "Directory relative to the repository root; omit for the root"));
        properties.put("maxDepth", AgentToolSupport.boundedInteger(
                "How many directory levels to descend, default 3", 1, 10));
        properties.put("limit", AgentToolSupport.boundedInteger(
                "Maximum number of paths, default 100", 1, 500));

        return ToolDefinition.builder(LIST_FILES, ListFilesRequest.class)
                .version(VERSION)
                .description("List files under a directory of the project source repository.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of()))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(10))
                .maxResultItems(500)
                .maxResultBytes(65_536)
                .requiredPermission(ToolPermission.CODE_READ)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }

    private static ToolDefinition searchCodeDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", AgentToolSupport.field(
                "string", "Literal text to look for; matching is case-insensitive"));
        properties.put("filePattern", AgentToolSupport.field(
                "string", "Glob restricting which files are searched, for example *.java"));
        properties.put("limit", AgentToolSupport.boundedInteger(
                "Maximum number of matches, default 30", 1, 200));

        return ToolDefinition.builder(SEARCH_CODE, SearchCodeRequest.class)
                .version(VERSION)
                .description("Search the project source repository for a keyword and return matching "
                        + "lines with their file path, line number and surrounding context.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("keyword")))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(20))
                .maxResultItems(200)
                .maxResultBytes(131_072)
                .requiredPermission(ToolPermission.CODE_READ)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }

    private static ToolDefinition readCodeFileDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("relativePath", AgentToolSupport.field(
                "string", "File relative to the repository root"));
        properties.put("startLine", AgentToolSupport.field("integer", "One-based first line, default 1"));
        properties.put("endLine", AgentToolSupport.field(
                "integer", "One-based last line, default 200 lines after the start"));

        return ToolDefinition.builder(READ_CODE_FILE, ReadCodeFileRequest.class)
                .version(VERSION)
                .description("Read a line range of one file in the project source repository.")
                .inputSchema(AgentToolSupport.objectSchema(
                        properties, List.of("relativePath")))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(10))
                .maxResultItems(2_000)
                .maxResultBytes(131_072)
                .requiredPermission(ToolPermission.CODE_READ)
                .displayIntent(ToolDisplayIntent.READ)
                .build();
    }

    /** A repository operation whose failures are translated for the model. */
    @FunctionalInterface
    private interface RepositoryCall<T> {

        /**
         * Runs the operation.
         *
         * @return repository result
         */
        T execute();
    }
}
