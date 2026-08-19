package com.devpilot.agent.runtime;

import com.devpilot.agent.tool.code.CodeSearchTools;
import com.devpilot.agent.tool.delegate.AgentDelegationTools;
import com.devpilot.agent.tool.logs.LogTools;
import com.devpilot.agent.tool.test.TestTools;
import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.log.model.ImportLogsRequest;
import com.devpilot.log.model.LogEntryRequest;
import com.devpilot.log.service.LogService;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.model.ModelGateway;
import com.devpilot.runtime.projection.AgentRunView;
import com.devpilot.runtime.projection.SessionProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: the supervisor routes work to specialists, the specialists run as nested agent runs,
 * and the whole tree closes cleanly — verified without an API key.
 */
@SpringBootTest
@ActiveProfiles("test")
class SupervisorRoutingTest {

    private static final String DEMO_REPOSITORY = "../demo-project/order-demo";
    private static final LocalDateTime INCIDENT = LocalDateTime.of(2026, 8, 16, 10, 31, 2);

    private static final String ASK_LOG = AgentDelegationTools.toolNameOf("log_agent");
    private static final String ASK_CODE = AgentDelegationTools.toolNameOf("code_agent");
    private static final String ASK_TEST = AgentDelegationTools.toolNameOf("test_agent");

    /** Replaces the model provider with a script the test controls. */
    @TestConfiguration
    static class ScriptedModelConfiguration {

        /** @return scripted gateway shared by the test */
        @Bean
        @Primary
        ScriptedModelGateway scriptedModelGateway() {
            return new ScriptedModelGateway();
        }
    }

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private ScriptedModelGateway model;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private LogService logService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private AgentRegistry agentRegistry;

    private long projectId;
    private String sessionId;
    private String turnId;

    @BeforeEach
    void openSession() {
        model.reset();
        projectId = projectService.create(new CreateProjectRequest(
                        "supervisor-test", "sup-" + UUID.randomUUID().toString().substring(0, 8),
                        null, DEMO_REPOSITORY, null))
                .id();
        logService.importLogs(projectId, new ImportLogsRequest(List.of(
                new LogEntryRequest("order-service", "ERROR", "t-1001", "com.demo.order.OrderService",
                        "Cannot invoke \"CouponInfo.getDiscountAmount()\" because \"coupon\" is null",
                        "java.lang.NullPointerException",
                        "java.lang.NullPointerException\n"
                                + "\tat com.demo.order.OrderService.createOrder(OrderService.java:86)",
                        INCIDENT))));
        sessionId = chatSessionService.create(projectId, new CreateSessionRequest("排查")).sessionId();
    }

    @Test
    void supervisorSeesDelegationToolsAndNoRawEvidenceTools() {
        var scope = agentRegistry.scopeOf(agentRegistry.require("supervisor"));

        assertThat(scope.visibleTools()).contains(ASK_LOG, ASK_CODE, ASK_TEST);
        assertThat(scope.canSee(CodeSearchTools.SEARCH_CODE)).isFalse();
        assertThat(scope.canSee(LogTools.SEARCH_LOGS)).isFalse();
        assertThat(scope.canSee(TestTools.SAVE_TEST_CASES)).isFalse();
    }

    @Test
    void routesASingleQuestionToOneSpecialist() {
        startTurn("最近有哪些 ERROR？");
        // Supervisor delegates once.
        model.enqueueToolCall(ASK_LOG, Map.of("task", "统计最近的 ERROR 日志与异常类型"));
        // The log agent works, then answers.
        model.enqueueToolCall(LogTools.GET_RECENT_ERROR_SUMMARY, Map.of("projectId", projectId, "hours", 720));
        model.enqueueAnswer("order-service 出现 1 次 NullPointerException。");
        // Supervisor summarises.
        model.enqueueAnswer("结论：order-service 存在优惠券空指针。");

        AgentTurnResult result = run();

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.runs())
                .extracting(AgentRunView::agentName)
                .containsExactly("supervisor", "log_agent");
        assertThat(projection.runs()).noneSatisfy(run ->
                assertThat(run.agentName()).isEqualTo("code_agent"));
    }

    @Test
    void chainsTwoSpecialistsAndBuildsARunTree() {
        startTurn("OrderService 这段 NPE 是为什么？");
        model.enqueueToolCall(ASK_LOG, Map.of("task", "找出 OrderService 相关的异常与堆栈行号"));
        model.enqueueToolCall(LogTools.SEARCH_LOGS,
                Map.of("projectId", projectId, "level", "ERROR", "keyword", "getDiscountAmount"));
        model.enqueueAnswer("异常在 OrderService.java:86，coupon 为 null。");
        model.enqueueToolCall(ASK_CODE,
                Map.of("task", "读取 OrderService.java 第 80-95 行，判断 coupon 为 null 时的行为"));
        model.enqueueToolCall(CodeSearchTools.READ_CODE_FILE, Map.of(
                "projectId", projectId,
                "relativePath", "src/main/java/com/demo/order/OrderService.java",
                "startLine", 80, "endLine", 95));
        model.enqueueAnswer("第 86 行直接调用 coupon.getDiscountAmount()，未判空。");
        model.enqueueAnswer("结论：优惠券服务降级返回 null，OrderService 第 86 行未判空导致 500。");

        AgentTurnResult result = run();

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.finalMessage()).contains("86");

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.runs())
                .extracting(AgentRunView::agentName)
                .containsExactly("supervisor", "log_agent", "code_agent");

        // The specialists hang under the supervisor: the audit trail is a tree, not a flat list.
        AgentRunView supervisor = projection.runs().getFirst();
        assertThat(supervisor.parentRunId()).isNull();
        assertThat(projection.runs().stream().skip(1))
                .allSatisfy(run -> assertThat(run.parentRunId()).isEqualTo(supervisor.runId()));
    }

    @Test
    void reachesTestDesignAfterGatheringEvidence() {
        startTurn("分析订单服务 500，并给出修复后的测试方案。");
        model.enqueueToolCall(ASK_LOG, Map.of("task", "找出订单服务最近的错误"));
        model.enqueueAnswer("NullPointerException，位于 OrderService.java:86。");
        model.enqueueToolCall(ASK_TEST, Map.of("task", "为优惠券返回 null 的场景设计回归用例"));
        model.enqueueToolCall(TestTools.SAVE_TEST_CASES, Map.of(
                "projectId", projectId,
                "cases", List.of(Map.of(
                        "title", "优惠券服务返回 null 时创建订单",
                        "priority", "P0",
                        "steps", List.of("模拟优惠券服务返回 null", "调用创建订单接口"),
                        "expectedResult", "返回明确业务错误，不出现 500"))));
        model.enqueueAnswer("已保存 1 条回归用例。");
        model.enqueueAnswer("结论与回归方案见上。");

        AgentTurnResult result = run();

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.runs())
                .extracting(AgentRunView::agentName)
                .containsExactly("supervisor", "log_agent", "test_agent");
        // The write happened inside the specialist that is allowed to write.
        assertThat(projection.toolCalls())
                .extracting(call -> call.toolName())
                .contains(TestTools.SAVE_TEST_CASES);
    }

    @Test
    void aSpecialistFailureIsReportedToTheSupervisorRatherThanKillingTheTurn() {
        startTurn("查一下");
        model.enqueueToolCall(ASK_CODE, Map.of("task", "读取一个不存在的文件"));
        model.enqueueToolCall(CodeSearchTools.READ_CODE_FILE, Map.of(
                "projectId", projectId, "relativePath", "../../etc/passwd", "startLine", 1, "endLine", 5));
        // The specialist gives up rather than answering.
        model.enqueueFailure("specialist model unavailable");
        // The supervisor still produces an answer from what it has.
        model.enqueueAnswer("代码分析未能完成，建议人工确认仓库配置。");

        AgentTurnResult result = run();

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.runs()).extracting(AgentRunView::status)
                .contains(RunStatus.FAILED, RunStatus.COMPLETED);
    }

    @Test
    void closesEveryNestedRunAndStepWhenTheTurnEnds() {
        startTurn("分析");
        model.enqueueToolCall(ASK_LOG, Map.of("task", "查日志"));
        model.enqueueAnswer("日志结论");
        model.enqueueAnswer("最终结论");

        run();
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.openRuns(turnId)).isEmpty();
        assertThat(projection.openSteps(turnId)).isEmpty();
        assertThat(projection.openToolCalls(turnId)).isEmpty();
    }

    private void startTurn(String userMessage) {
        turnId = lifecycleService.startTurn(sessionId, "USER", userMessage);
    }

    private AgentTurnResult run() {
        return agentRuntime.runTurn(
                AgentTurnRequest.of(sessionId, projectId, turnId, "supervisor", "分析"));
    }
}
