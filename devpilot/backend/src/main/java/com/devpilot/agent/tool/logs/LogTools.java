package com.devpilot.agent.tool.logs;

import com.devpilot.agent.tool.AgentToolSupport;
import com.devpilot.log.model.ErrorSummaryArguments;
import com.devpilot.log.model.ErrorSummaryResponse;
import com.devpilot.log.model.LogEntryResponse;
import com.devpilot.log.model.LogToolEntry;
import com.devpilot.log.model.SearchLogsArguments;
import com.devpilot.log.model.TraceLogsArguments;
import com.devpilot.log.service.LogService;
import com.devpilot.runtime.tool.ConcurrencyMode;
import com.devpilot.runtime.tool.SideEffectLevel;
import com.devpilot.runtime.tool.ToolDefinition;
import com.devpilot.runtime.tool.ToolDisplayIntent;
import com.devpilot.runtime.tool.ToolExecutionContext;
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
 * Exposes the system log capability as model-visible tools.
 *
 * <p>The summary tool exists so an agent can understand the shape of an incident without pulling
 * hundreds of lines into the model context; the search and trace tools then fetch only the lines
 * that matter.
 */
@Component
public class LogTools {

    /** Name of the log search tool. */
    public static final String SEARCH_LOGS = "searchLogs";
    /** Name of the trace lookup tool. */
    public static final String GET_LOG_BY_TRACE_ID = "getLogByTraceId";
    /** Name of the recent error summary tool. */
    public static final String GET_RECENT_ERROR_SUMMARY = "getRecentErrorSummary";

    private static final String VERSION = "1";

    private final ToolRegistry toolRegistry;
    private final LogService logService;

    /**
     * Creates the contributor.
     *
     * @param toolRegistry registry the tools are published to
     * @param logService log query capability
     */
    public LogTools(ToolRegistry toolRegistry, LogService logService) {
        this.toolRegistry = toolRegistry;
        this.logService = logService;
    }

    /** Publishes the log tools once the registry is available. */
    @PostConstruct
    public void register() {
        toolRegistry.register(searchLogsDefinition(), (ToolHandler<SearchLogsArguments>) this::searchLogs);
        toolRegistry.register(traceDefinition(), (ToolHandler<TraceLogsArguments>) this::getLogByTraceId);
        toolRegistry.register(
                summaryDefinition(), (ToolHandler<ErrorSummaryArguments>) this::getRecentErrorSummary);
    }

    private ToolResult searchLogs(ToolExecutionContext<SearchLogsArguments> context) {
        SearchLogsArguments arguments = context.arguments();
        AgentToolSupport.requireSameProject(context, arguments.projectId());

        List<LogEntryResponse> entries = logService.search(
                        arguments.projectId(),
                        arguments.serviceName(),
                        arguments.level(),
                        arguments.keyword(),
                        null,
                        arguments.startTime(),
                        arguments.endTime(),
                        0,
                        arguments.limit())
                .items();

        List<LogToolEntry> lines = entries.stream().map(LogToolEntry::from).toList();
        return ToolResult.of(lines, lines.size(),
                render("Found " + lines.size() + " log line(s)", lines));
    }

    private ToolResult getLogByTraceId(ToolExecutionContext<TraceLogsArguments> context) {
        TraceLogsArguments arguments = context.arguments();
        AgentToolSupport.requireSameProject(context, arguments.projectId());

        List<LogToolEntry> lines = logService.search(
                        arguments.projectId(), null, null, null, arguments.traceId(),
                        null, null, 0, arguments.limit())
                .items()
                .stream()
                .map(LogToolEntry::from)
                .toList();

        return ToolResult.of(lines, lines.size(),
                render("Found " + lines.size() + " line(s) for trace " + arguments.traceId(), lines));
    }

    private ToolResult getRecentErrorSummary(ToolExecutionContext<ErrorSummaryArguments> context) {
        ErrorSummaryArguments arguments = context.arguments();
        AgentToolSupport.requireSameProject(context, arguments.projectId());

        List<ErrorSummaryResponse> groups =
                logService.summarizeErrors(arguments.projectId(), arguments.hours());
        long total = groups.stream().mapToLong(ErrorSummaryResponse::occurrences).sum();

        StringBuilder summary = new StringBuilder("Grouped ").append(total).append(" error(s) into ")
                .append(groups.size()).append(" kind(s) over the last ").append(arguments.hours())
                .append("h:");
        for (ErrorSummaryResponse group : groups) {
            summary.append("\n- ").append(group.serviceName()).append("  ")
                    .append(group.exceptionType() == null ? "(no exception type)" : group.exceptionType())
                    .append("  x").append(group.occurrences())
                    .append("  ").append(group.firstSeen()).append(" → ").append(group.lastSeen())
                    .append("\n  ").append(oneLine(group.sampleMessage()));
        }
        return ToolResult.of(groups, groups.size(), summary.toString());
    }

    /**
     * Renders log lines as the evidence the model reads and the event log stores.
     *
     * @param header first line of the summary
     * @param lines matched log lines
     * @return summary carrying time, service, level, trace and the first stack frames
     */
    private static String render(String header, List<LogToolEntry> lines) {
        StringBuilder summary = new StringBuilder(header).append(':');
        for (LogToolEntry line : lines) {
            summary.append("\n- ").append(line.logTime()).append("  ").append(line.level())
                    .append("  ").append(line.serviceName())
                    .append("  [").append(line.traceId() == null ? "-" : line.traceId()).append(']')
                    .append("\n  ").append(oneLine(line.message()));
            if (line.exceptionType() != null) {
                summary.append("\n  ").append(line.exceptionType());
            }
            if (line.stackTracePreview() != null) {
                summary.append("\n  ").append(firstFrames(line.stackTracePreview()));
            }
        }
        return summary.toString();
    }

    private static String firstFrames(String stackTrace) {
        return stackTrace.lines().limit(3).map(String::strip).reduce("", (left, right) ->
                left.isEmpty() ? right : left + " / " + right);
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').strip();
    }

    private static ToolDefinition searchLogsDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", AgentToolSupport.field("integer", "Project to read"));
        properties.put("serviceName", AgentToolSupport.field(
                "string", "Emitting service, for example order-service"));
        properties.put("level", AgentToolSupport.field(
                "string", "One of TRACE, DEBUG, INFO, WARN, ERROR, FATAL"));
        properties.put("keyword", AgentToolSupport.field(
                "string", "Substring of the message or exception type"));
        properties.put("startTime", AgentToolSupport.field(
                "string", "Inclusive lower bound, ISO-8601 local date-time"));
        properties.put("endTime", AgentToolSupport.field(
                "string", "Inclusive upper bound, ISO-8601 local date-time"));
        properties.put("limit", AgentToolSupport.boundedInteger(
                "Maximum number of lines, default 50", 1, 100));

        return ToolDefinition.builder(SEARCH_LOGS, SearchLogsArguments.class)
                .version(VERSION)
                .description("Search the recorded system logs of a project by service, level, keyword "
                        + "and time range.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("projectId")))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(10))
                .maxResultItems(100)
                .maxResultBytes(131_072)
                .requiredPermission(ToolPermission.LOG_READ)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }

    private static ToolDefinition traceDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", AgentToolSupport.field("integer", "Project to read"));
        properties.put("traceId", AgentToolSupport.field(
                "string", "Trace identifier shared by the lines of one request"));
        properties.put("limit", AgentToolSupport.boundedInteger(
                "Maximum number of lines, default 50", 1, 100));

        return ToolDefinition.builder(GET_LOG_BY_TRACE_ID, TraceLogsArguments.class)
                .version(VERSION)
                .description("Fetch every recorded log line of one request by its trace id.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("projectId", "traceId")))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(10))
                .maxResultItems(100)
                .maxResultBytes(131_072)
                .requiredPermission(ToolPermission.LOG_READ)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }

    private static ToolDefinition summaryDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", AgentToolSupport.field("integer", "Project to read"));
        properties.put("hours", AgentToolSupport.boundedInteger(
                "Size of the window in hours, default 24", 1, 720));

        return ToolDefinition.builder(GET_RECENT_ERROR_SUMMARY, ErrorSummaryArguments.class)
                .version(VERSION)
                .description("Group the recent errors of a project by service and exception type, with "
                        + "occurrence counts and a sample message, so an incident can be understood "
                        + "without reading every line.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("projectId")))
                .sideEffect(SideEffectLevel.READ_ONLY)
                .concurrency(ConcurrencyMode.CONCURRENCY_SAFE)
                .timeout(Duration.ofSeconds(10))
                .maxResultItems(20)
                .maxResultBytes(32_768)
                .requiredPermission(ToolPermission.LOG_READ)
                .displayIntent(ToolDisplayIntent.GENERIC)
                .build();
    }
}
