package com.devpilot.agent.runtime;

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
import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.model.ModelGateway;
import com.devpilot.runtime.model.ModelMessage;
import com.devpilot.runtime.model.ModelRequest;
import com.devpilot.runtime.model.ModelRole;
import com.devpilot.runtime.projection.MessageRole;
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
 * Contract: the agent loop picks tools by itself, sees only what was recorded, and closes every
 * lifecycle element it opens — verified without an API key.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentRuntimeTest {

    private static final String DEMO_REPOSITORY = "../demo-project/order-demo";
    private static final String ORDER_SERVICE = "src/main/java/com/demo/order/OrderService.java";
    private static final LocalDateTime INCIDENT = LocalDateTime.of(2026, 8, 16, 10, 31, 2);

    /** Replaces the model provider with a script the test controls. */
    @TestConfiguration
    static class ScriptedModelConfiguration {

        /**
         * Publishes the scripted gateway as the primary {@link ModelGateway}. One primary is
         * enough: the class already implements the interface.
         *
         * @return scripted gateway shared by the test
         */
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

    private long projectId;
    private String sessionId;
    private String turnId;

    @BeforeEach
    void openSession() {
        model.reset();
        projectId = projectService.create(new CreateProjectRequest(
                        "agent-test", "agent-" + UUID.randomUUID().toString().substring(0, 8),
                        null, DEMO_REPOSITORY, null))
                .id();
        logService.importLogs(projectId, new ImportLogsRequest(List.of(
                new LogEntryRequest("order-service", "ERROR", "t-1001", "com.demo.order.OrderService",
                        "Cannot invoke \"CouponInfo.getDiscountAmount()\" because \"coupon\" is null",
                        "java.lang.NullPointerException",
                        "java.lang.NullPointerException\n"
                                + "\tat com.demo.order.OrderService.createOrder(OrderService.java:86)",
                        INCIDENT))));
        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest("排查"));
        sessionId = session.sessionId();
    }

    @Test
    void answersACodeQuestionByCallingACodeToolFirst() {
        startTurn("OrderService 的 createOrder 在哪？");
        model.enqueueToolCall(CodeSearchTools.SEARCH_CODE,
                Map.of("projectId", projectId, "keyword", "createOrder", "filePattern", "*.java"));
        model.enqueueAnswer("createOrder 定义在 " + ORDER_SERVICE + "。");

        AgentTurnResult result = run("OrderService 的 createOrder 在哪？");

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.finalMessage()).contains(ORDER_SERVICE);

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.toolCalls())
                .extracting(call -> call.toolName())
                .containsExactly(CodeSearchTools.SEARCH_CODE);
        assertThat(projection.messages())
                .extracting(message -> message.role())
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
    }

    @Test
    void answersALogQuestionWithLogToolsAndNotCodeTools() {
        startTurn("最近有哪些 ERROR？");
        model.enqueueToolCall(LogTools.GET_RECENT_ERROR_SUMMARY,
                Map.of("projectId", projectId, "hours", 720));
        model.enqueueAnswer("最近的错误集中在 order-service 的空指针。");

        AgentTurnResult result = run("最近有哪些 ERROR？");

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(lifecycleService.project(sessionId).toolCalls())
                .extracting(call -> call.toolName())
                .containsExactly(LogTools.GET_RECENT_ERROR_SUMMARY)
                .doesNotContain(CodeSearchTools.SEARCH_CODE, CodeSearchTools.READ_CODE_FILE);
    }

    @Test
    void showsTheModelOnlyEvidenceThatWasRecordedFirst() {
        startTurn("createOrder 在哪？");
        model.enqueueToolCall(CodeSearchTools.SEARCH_CODE,
                Map.of("projectId", projectId, "keyword", "createOrder", "filePattern", "*.java"));
        model.enqueueAnswer("见 " + ORDER_SERVICE);

        run("createOrder 在哪？");

        List<ModelRequest> requests = model.received();
        assertThat(requests).hasSize(2);

        // The first request carries only the persona and the user question.
        assertThat(requests.getFirst().messages())
                .extracting(ModelMessage::role)
                .containsExactly(ModelRole.SYSTEM, ModelRole.USER);

        // The second replays the tool exchange the way a provider expects it: the assistant turn
        // that asked for the call, then the result under the same callId.
        List<ModelMessage> second = requests.get(1).messages();
        ModelMessage request = second.stream()
                .filter(message -> !message.toolCalls().isEmpty())
                .findFirst()
                .orElseThrow();
        ModelMessage observation = second.stream()
                .filter(message -> message.role() == ModelRole.TOOL)
                .findFirst()
                .orElseThrow();

        assertThat(request.role()).isEqualTo(ModelRole.ASSISTANT);
        assertThat(request.toolCalls()).singleElement().satisfies(toolCall -> {
            assertThat(toolCall.toolName()).isEqualTo(CodeSearchTools.SEARCH_CODE);
            assertThat(toolCall.arguments()).containsEntry("keyword", "createOrder");
        });
        assertThat(observation.callId()).isEqualTo(request.toolCalls().getFirst().callId());
        assertThat(observation.name()).isEqualTo(CodeSearchTools.SEARCH_CODE);
        assertThat(observation.content()).contains(ORDER_SERVICE);

        // The evidence the model reads is exactly the evidence the event log stored.
        String recordedSummary = lifecycleService.project(sessionId).toolCalls().getFirst().resultSummary();
        assertThat(observation.content()).isEqualTo(recordedSummary);
    }

    @Test
    void advertisesOnlyTheToolsTheProfileGrants() {
        startTurn("问题");
        model.enqueueAnswer("好的");

        run("问题");

        assertThat(model.received().getFirst().tools())
                .extracting(spec -> spec.name())
                .containsExactlyInAnyOrder(
                        CodeSearchTools.LIST_FILES, CodeSearchTools.SEARCH_CODE,
                        CodeSearchTools.READ_CODE_FILE, LogTools.SEARCH_LOGS,
                        LogTools.GET_LOG_BY_TRACE_ID, LogTools.GET_RECENT_ERROR_SUMMARY);
    }

    @Test
    void keepsGoingWhenTheModelAsksForSomethingItMayNotHave() {
        startTurn("保存测试用例");
        model.enqueueToolCall("saveTestCases", Map.of("projectId", projectId));
        model.enqueueAnswer("我没有保存测试用例的权限。");

        AgentTurnResult result = run("保存测试用例");

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.toolName()).isEqualTo("saveTestCases");
            assertThat(call.status().terminal()).isTrue();
        });
        assertThat(projection.openToolCalls(turnId)).isEmpty();
    }

    @Test
    void failsWithAClearReasonWhenTheModelProviderFails() {
        startTurn("问题");
        model.enqueueFailure("provider unavailable");

        AgentTurnResult result = run("问题");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.errorMessage()).contains("provider unavailable");
        assertThat(lifecycleService.project(sessionId).runs()).singleElement()
                .satisfies(run -> assertThat(run.status()).isEqualTo(RunStatus.FAILED));
    }

    @Test
    void stopsAtTheStepBudgetInsteadOfLoopingForever() {
        startTurn("问题");
        for (int index = 0; index < 6; index++) {
            model.enqueueToolCall(CodeSearchTools.LIST_FILES, Map.of("projectId", projectId));
        }

        AgentTurnResult result = run("问题");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.errorMessage()).contains("limit of 6");
        assertThat(model.received()).hasSize(6);
    }

    @Test
    void refusesToWorkOnATurnThatWasAlreadyCancelled() {
        startTurn("问题");
        lifecycleService.cancelTurn(sessionId, turnId);

        AgentTurnResult result = run("问题");

        assertThat(result.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(model.received()).isEmpty();
    }

    @Test
    void leavesNothingOpenAfterTheTurnEnds() {
        startTurn("问题");
        model.enqueueToolCall(CodeSearchTools.SEARCH_CODE,
                Map.of("projectId", projectId, "keyword", "createOrder"));
        model.enqueueAnswer("完成");

        run("问题");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.openToolCalls(turnId)).isEmpty();
        assertThat(projection.openRuns(turnId)).isEmpty();
        assertThat(projection.openSteps(turnId)).isEmpty();
        assertThat(projection.turn(turnId).orElseThrow().status().terminal()).isTrue();
    }

    private void startTurn(String userMessage) {
        turnId = lifecycleService.startTurn(sessionId, "USER", userMessage);
    }

    private AgentTurnResult run(String userMessage) {
        return agentRuntime.runTurn(
                AgentTurnRequest.of(sessionId, projectId, turnId, "debug_agent", userMessage));
    }
}
