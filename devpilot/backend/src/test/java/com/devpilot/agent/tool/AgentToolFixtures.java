package com.devpilot.agent.tool;

import com.devpilot.agent.tool.code.CodeSearchTools;
import com.devpilot.agent.tool.logs.LogTools;
import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.log.model.ImportLogsRequest;
import com.devpilot.log.model.LogEntryRequest;
import com.devpilot.log.service.LogService;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.projection.ToolCallView;
import com.devpilot.runtime.tool.ToolExecutionResult;
import com.devpilot.runtime.tool.ToolInvocation;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolScope;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared setup for the tests that drive the real code and log tools through the registry. */
public final class AgentToolFixtures {

    public static final String AGENT = "debug_agent";
    public static final String PROFILE_VERSION = "standard@1";
    public static final LocalDateTime INCIDENT = LocalDateTime.of(2026, 8, 16, 10, 31, 2);

    public static final ToolScope FULL_SCOPE = ToolScope.readOnly(
            Set.of(CodeSearchTools.LIST_FILES, CodeSearchTools.SEARCH_CODE, CodeSearchTools.READ_CODE_FILE,
                    LogTools.SEARCH_LOGS, LogTools.GET_LOG_BY_TRACE_ID, LogTools.GET_RECENT_ERROR_SUMMARY),
            Set.of(ToolPermission.CODE_READ, ToolPermission.LOG_READ));

    private AgentToolFixtures() {
    }

    /**
     * Creates a project bound to the given repository path.
     *
     * @param projectService project service
     * @param repositoryPath repository path to bind
     * @return new project identity
     */
    public static long newProject(ProjectService projectService, String repositoryPath) {
        return projectService.create(new CreateProjectRequest(
                        "tool-test", "tool-" + UUID.randomUUID().toString().substring(0, 8),
                        null, repositoryPath, null))
                .id();
    }

    /**
     * Opens a session and starts a turn so tool events have somewhere to live.
     *
     * @param chatSessionService session service
     * @param lifecycleService lifecycle service
     * @param projectId owning project
     * @return session identifier and turn identifier
     */
    public static String[] newSessionTurn(
            ChatSessionService chatSessionService,
            SessionLifecycleService lifecycleService,
            long projectId) {
        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest("tools"));
        String turnId = lifecycleService.startTurn(session.sessionId(), "USER", "诊断问题");
        return new String[] {session.sessionId(), turnId};
    }

    /**
     * Runs one tool through the registry.
     *
     * @param registry tool registry
     * @param sessionId owning session
     * @param turnId owning turn
     * @param projectId project of the session
     * @param toolName tool to run
     * @param arguments raw arguments
     * @return terminal outcome
     */
    public static ToolExecutionResult invoke(
            ToolRegistry registry,
            String sessionId,
            String turnId,
            Long projectId,
            String toolName,
            Map<String, Object> arguments) {
        return registry.execute(
                new ToolInvocation(sessionId, turnId, null, null, AGENT, toolName, arguments),
                FULL_SCOPE, PROFILE_VERSION, projectId);
    }

    /**
     * Reads the audit projection of one tool call.
     *
     * @param lifecycleService lifecycle service
     * @param sessionId owning session
     * @param callId call to look up
     * @return projected call
     */
    public static ToolCallView auditOf(
            SessionLifecycleService lifecycleService, String sessionId, String callId) {
        SessionProjection projection = lifecycleService.project(sessionId);
        return projection.toolCall(callId).orElseThrow();
    }

    /**
     * Seeds the two demo incidents for a project.
     *
     * @param logService log service
     * @param projectId owning project
     */
    public static void seedIncidentLogs(LogService logService, long projectId) {
        logService.importLogs(projectId, new ImportLogsRequest(List.of(
                new LogEntryRequest("order-service", "ERROR", "t-1001", "com.demo.order.OrderService",
                        "Cannot invoke \"CouponInfo.getDiscountAmount()\" because \"coupon\" is null",
                        "java.lang.NullPointerException",
                        "java.lang.NullPointerException: coupon is null\n"
                                + "\tat com.demo.order.OrderService.createOrder(OrderService.java:86)",
                        INCIDENT),
                new LogEntryRequest("order-service", "WARN", "t-1001", "com.demo.order.CouponClient",
                        "coupon service responded 503, returning null", null, null,
                        INCIDENT.minusSeconds(1)),
                new LogEntryRequest("order-service", "ERROR", "t-1002", "com.demo.order.OrderService",
                        "Cannot invoke \"CouponInfo.getDiscountAmount()\" because \"coupon\" is null",
                        "java.lang.NullPointerException", null, INCIDENT.plusMinutes(3)),
                new LogEntryRequest("order-service", "ERROR", "t-2001", "com.demo.order.OrderMapper",
                        "query listOrdersByUser timed out", "java.sql.SQLTimeoutException", null,
                        INCIDENT.plusDays(1)))));
    }
}
