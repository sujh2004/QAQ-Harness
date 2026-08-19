package com.devpilot.agent.tool;

import com.devpilot.agent.tool.test.TestTools;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.common.api.PageResponse;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.tool.ToolExecutionResult;
import com.devpilot.runtime.tool.ToolInvocation;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolScope;
import com.devpilot.testcase.model.TestCaseResponse;
import com.devpilot.testcase.service.TestCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: the only tool that writes is gated twice — by the agent scope and by the policy allow
 * list — and what it stores is attributed to the session the runtime knows about.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestToolTest {

    private static final ToolScope WRITE_SCOPE = new ToolScope(
            Set.of(TestTools.SAVE_TEST_CASES), Set.of(ToolPermission.TEST_CASE_WRITE), true);

    private static final ToolScope READ_ONLY_SCOPE = ToolScope.readOnly(
            Set.of(TestTools.SAVE_TEST_CASES), Set.of(ToolPermission.TEST_CASE_WRITE));

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private TestCaseService testCaseService;

    private long projectId;
    private String sessionId;
    private String turnId;

    @BeforeEach
    void openSession() {
        projectId = AgentToolFixtures.newProject(projectService, "/srv/repos/test-tool");
        String[] ids = AgentToolFixtures.newSessionTurn(chatSessionService, lifecycleService, projectId);
        sessionId = ids[0];
        turnId = ids[1];
    }

    @Test
    void storesTheCasesAnAgentDesigned() {
        ToolExecutionResult result = invoke(WRITE_SCOPE, Map.of(
                "projectId", projectId,
                "cases", List.of(Map.of(
                        "title", "优惠券服务返回 null 时创建订单",
                        "priority", "P0",
                        "precondition", "已登录且商品库存充足",
                        "steps", List.of("构造 couponCode", "模拟优惠券服务返回 null", "调用创建订单接口"),
                        "expectedResult", "接口返回明确业务错误，不出现 500"))));

        assertThat(result.successful()).isTrue();
        assertThat(result.modelSummary()).contains("Saved 1 test case");

        PageResponse<TestCaseResponse> stored = testCaseService.list(projectId, 0, 20);
        assertThat(stored.total()).isEqualTo(1);
        TestCaseResponse testCase = stored.items().getFirst();
        assertThat(testCase.title()).isEqualTo("优惠券服务返回 null 时创建订单");
        assertThat(testCase.priority()).isEqualTo("P0");
        assertThat(testCase.steps()).hasSize(3);
        assertThat(testCase.source()).isEqualTo("AGENT");
        // The session comes from the runtime, not from anything the model could claim.
        assertThat(testCase.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void refusesToWriteWhenTheAgentScopeForbidsMutation() {
        ToolExecutionResult result = invoke(READ_ONLY_SCOPE, Map.of(
                "projectId", projectId,
                "cases", List.of(validCase())));

        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
        assertThat(result.message()).contains("changes state");
        assertThat(testCaseService.list(projectId, 0, 20).total()).isZero();
    }

    @Test
    void rejectsABatchLargerThanTheDeclaredBound() {
        List<Map<String, Object>> tooMany = java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> Map.<String, Object>of(
                        "title", "case " + index,
                        "steps", List.of("step"),
                        "expectedResult", "ok"))
                .toList();

        ToolExecutionResult result = invoke(WRITE_SCOPE, Map.of("projectId", projectId, "cases", tooMany));

        assertThat(result.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(result.message()).contains("cases");
        assertThat(testCaseService.list(projectId, 0, 20).total()).isZero();
    }

    @Test
    void rejectsACaseWithoutAnExpectedResult() {
        ToolExecutionResult result = invoke(WRITE_SCOPE, Map.of(
                "projectId", projectId,
                "cases", List.of(Map.of("title", "无预期结果", "steps", List.of("step")))));

        assertThat(result.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(testCaseService.list(projectId, 0, 20).total()).isZero();
    }

    @Test
    void refusesToWriteIntoAnotherProject() {
        long otherProject = AgentToolFixtures.newProject(projectService, "/srv/repos/other");

        ToolExecutionResult result = invoke(WRITE_SCOPE, Map.of(
                "projectId", otherProject, "cases", List.of(validCase())));

        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
        assertThat(testCaseService.list(otherProject, 0, 20).total()).isZero();
    }

    @Test
    void everyAttemptIsAuditable() {
        invoke(WRITE_SCOPE, Map.of("projectId", projectId, "cases", List.of(validCase())));
        invoke(READ_ONLY_SCOPE, Map.of("projectId", projectId, "cases", List.of(validCase())));

        assertThat(lifecycleService.project(sessionId).toolCalls()).hasSize(2);
        assertThat(lifecycleService.project(sessionId).openToolCalls(turnId)).isEmpty();
    }

    private static Map<String, Object> validCase() {
        return Map.of(
                "title", "示例用例",
                "priority", "P1",
                "steps", List.of("第一步"),
                "expectedResult", "返回 200");
    }

    private ToolExecutionResult invoke(ToolScope scope, Map<String, Object> arguments) {
        return toolRegistry.execute(
                new ToolInvocation(sessionId, turnId, null, null, "test_agent",
                        TestTools.SAVE_TEST_CASES, arguments),
                scope, AgentToolFixtures.PROFILE_VERSION, projectId);
    }
}
