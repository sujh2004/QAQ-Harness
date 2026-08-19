package com.devpilot.agent.tool;

import com.devpilot.agent.tool.logs.LogTools;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.log.model.ErrorSummaryResponse;
import com.devpilot.log.model.LogToolEntry;
import com.devpilot.log.service.LogService;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.projection.ToolCallView;
import com.devpilot.runtime.tool.ToolExecutionResult;
import com.devpilot.runtime.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * Contract: the log tools are bounded, scoped to the session project and auditable.
 */
@SpringBootTest
@ActiveProfiles("test")
class LogToolTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private LogService logService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    private long projectId;
    private String sessionId;
    private String turnId;

    @BeforeEach
    void openSession() {
        projectId = AgentToolFixtures.newProject(projectService, "/srv/repos/log-tool");
        AgentToolFixtures.seedIncidentLogs(logService, projectId);
        String[] ids = AgentToolFixtures.newSessionTurn(chatSessionService, lifecycleService, projectId);
        sessionId = ids[0];
        turnId = ids[1];
    }

    @Test
    void searchLogsReturnsTheIncidentLines() {
        ToolExecutionResult result = invoke(LogTools.SEARCH_LOGS,
                Map.of("projectId", projectId, "level", "ERROR", "keyword", "getDiscountAmount"));

        assertThat(result.successful()).isTrue();
        @SuppressWarnings("unchecked")
        List<LogToolEntry> lines = (List<LogToolEntry>) result.data();
        assertThat(lines).hasSize(2);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.level()).isEqualTo("ERROR");
            assertThat(line.serviceName()).isEqualTo("order-service");
        });
        assertThat(result.modelSummary()).contains("2 log line");
    }

    @Test
    void searchLogsRefusesALimitAboveTheDocumentedMaximum() {
        ToolExecutionResult result = invoke(LogTools.SEARCH_LOGS,
                Map.of("projectId", projectId, "limit", 500));

        assertThat(result.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.INVALID_ARGUMENT);
        assertThat(result.message()).contains("limit");
    }

    @Test
    void getLogByTraceIdReturnsEveryLineOfOneRequest() {
        ToolExecutionResult result = invoke(LogTools.GET_LOG_BY_TRACE_ID,
                Map.of("projectId", projectId, "traceId", "t-1001"));

        assertThat(result.successful()).isTrue();
        assertThat(result.data()).asInstanceOf(LIST).hasSize(2);
        assertThat(result.modelSummary()).contains("t-1001");
    }

    @Test
    void getRecentErrorSummaryGroupsTheIncidentsWithoutDumpingEveryLine() {
        int hours = (int) Duration.between(AgentToolFixtures.INCIDENT, LocalDateTime.now()).toHours() + 48;

        ToolExecutionResult result = invoke(LogTools.GET_RECENT_ERROR_SUMMARY,
                Map.of("projectId", projectId, "hours", hours));

        assertThat(result.successful()).isTrue();
        @SuppressWarnings("unchecked")
        List<ErrorSummaryResponse> groups = (List<ErrorSummaryResponse>) result.data();
        assertThat(groups).anySatisfy(group -> {
            assertThat(group.exceptionType()).isEqualTo("java.lang.NullPointerException");
            assertThat(group.occurrences()).isEqualTo(2L);
            assertThat(group.sampleMessage()).contains("getDiscountAmount");
        });
        assertThat(result.modelSummary()).contains("kind(s)");
    }

    @Test
    void shortensTheStackTraceHandedToTheModel() {
        ToolExecutionResult result = invoke(LogTools.GET_LOG_BY_TRACE_ID,
                Map.of("projectId", projectId, "traceId", "t-1001"));

        @SuppressWarnings("unchecked")
        List<LogToolEntry> lines = (List<LogToolEntry>) result.data();
        assertThat(lines).anySatisfy(line ->
                assertThat(line.stackTracePreview()).contains("OrderService.java:86"));
    }

    @Test
    void refusesArgumentsThatNameAnotherProject() {
        long otherProject = AgentToolFixtures.newProject(projectService, "/srv/repos/other");

        ToolExecutionResult result = invoke(LogTools.SEARCH_LOGS, Map.of("projectId", otherProject));

        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
    }

    @Test
    void everyCallIsProjectableAsAnAuditRecord() {
        ToolExecutionResult ok = invoke(LogTools.SEARCH_LOGS, Map.of("projectId", projectId));
        ToolExecutionResult rejected = invoke(LogTools.SEARCH_LOGS,
                Map.of("projectId", projectId, "limit", 999));

        ToolCallView okAudit = AgentToolFixtures.auditOf(lifecycleService, sessionId, ok.callId());
        ToolCallView rejectedAudit =
                AgentToolFixtures.auditOf(lifecycleService, sessionId, rejected.callId());

        assertThat(okAudit.status()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(okAudit.resultSummary()).contains("log line");
        assertThat(rejectedAudit.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(lifecycleService.project(sessionId).openToolCalls(turnId)).isEmpty();
    }

    private ToolExecutionResult invoke(String toolName, Map<String, Object> arguments) {
        return AgentToolFixtures.invoke(
                toolRegistry, sessionId, turnId, projectId, toolName, arguments);
    }
}
