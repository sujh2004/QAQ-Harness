package com.devpilot.runtime.tool;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.RuntimeTestFixtures;
import com.devpilot.runtime.approval.ApprovalService;
import com.devpilot.runtime.lifecycle.RuntimeIds;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.payload.ApprovalResolvedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
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
 * Contract: every model-initiated call goes through resolution, validation, authorisation, approval,
 * timeout and limit enforcement, and every one of those paths leaves a matching
 * {@code tool_call_requested} / {@code tool_call_finished} pair.
 */
@SpringBootTest
@ActiveProfiles("test")
class ToolRegistryTest {

    private static final String AGENT = "code_agent";

    @Autowired
    private SessionEventStore eventStore;

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private SensitiveDataRedactor redactor;

    @Autowired
    private ToolResultProcessor resultProcessor;

    @Autowired
    private Validator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties appProperties;

    private DefaultToolRegistry registry;
    private String sessionId;
    private String turnId;
    private ToolScope scope;

    @BeforeEach
    void setUp() {
        registry = newRegistry(List.of());
        FakeTools.registerAll(registry);

        sessionId = RuntimeIds.newSessionId();
        lifecycleService.createSession(RuntimeTestFixtures.descriptor(sessionId, "tools"));
        turnId = lifecycleService.startTurn(sessionId, "USER", "question");

        scope = ToolScope.readOnly(
                Set.of(FakeTools.ECHO, FakeTools.SLOW, FakeTools.FAILING, FakeTools.TYPED_FAILING,
                        FakeTools.APPROVAL_REQUIRED, FakeTools.BULK, FakeTools.OVERSIZED, FakeTools.LEAKY,
                        FakeTools.MUTATING),
                Set.of(ToolPermission.CODE_READ, ToolPermission.LOG_READ, ToolPermission.TEST_CASE_WRITE));
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    @Test
    void runsAVisibleReadOnlyTool() {
        ToolExecutionResult result = invoke(FakeTools.ECHO, Map.of("message", "hello", "repeat", 3));

        assertThat(result.successful()).isTrue();
        assertThat(result.data()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(3);
        assertThat(result.modelSummary()).isEqualTo("Echoed 3 line(s)");
        assertThat(finishedStatus(result)).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(eventsFor(result)).hasSize(2);
    }

    @Test
    void refusesAToolThatIsNotRegistered() {
        ToolExecutionResult result = invoke("noSuchTool", Map.of("message", "hi"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.TOOL_NOT_FOUND);
        assertThat(eventsFor(result))
                .extracting(SessionEvent::eventType)
                .containsExactly(SessionEventType.TOOL_CALL_REQUESTED, SessionEventType.TOOL_CALL_FINISHED);
    }

    @Test
    void refusesAToolOutsideTheAgentScope() {
        ToolScope narrowed = ToolScope.readOnly(Set.of(FakeTools.BULK), Set.of(ToolPermission.LOG_READ));

        ToolExecutionResult result = registry.execute(
                invocation(FakeTools.ECHO, Map.of("message", "hello", "repeat", 1)),
                narrowed, RuntimeTestFixtures.PROFILE_VERSION, 1L);

        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.TOOL_NOT_VISIBLE);
        assertThat(eventsFor(result)).hasSize(2);
    }

    @Test
    void refusesArgumentsThatFailValidation() {
        ToolExecutionResult result = invoke(FakeTools.ECHO, Map.of("message", "", "repeat", 99));

        assertThat(result.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.INVALID_ARGUMENT);
        assertThat(result.message()).contains("message").contains("repeat");
        assertThat(eventsFor(result)).hasSize(2);
    }

    @Test
    void reportsATimeoutInsteadOfHangingTheTurn() {
        ToolExecutionResult result = invoke(FakeTools.SLOW, Map.of("delayMs", 2_000));

        assertThat(result.status()).isEqualTo(ToolCallStatus.TIMEOUT);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.TIMEOUT);
        assertThat(finishedStatus(result)).isEqualTo(ToolCallStatus.TIMEOUT);
    }

    @Test
    void hidesProviderInternalsFromTheModelAndTheEventLog() {
        ToolExecutionResult result = invoke(FakeTools.FAILING, Map.of("note", "read config"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.PROVIDER_ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PROVIDER_ERROR);
        assertThat(result.message()).contains("IllegalStateException");
        assertThat(result.message())
                .doesNotContain("application-prod.yml")
                .doesNotContain("at com.devpilot");
    }

    @Test
    void keepsTheSafeMessageOfATypedProviderFailure() {
        ToolExecutionResult result = invoke(FakeTools.TYPED_FAILING, Map.of("note", "read repo"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.PROVIDER_ERROR);
        assertThat(result.message()).isEqualTo("Configured repository path cannot be accessed");
    }

    @Test
    void refusesMutatingToolsByDefault() {
        ToolExecutionResult result = invoke(FakeTools.MUTATING, Map.of("note", "remember this"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
        assertThat(result.message()).contains("changes state");
    }

    @Test
    void allowsAMutatingToolOnlyWhenItIsExplicitlyWhitelistedAndTheScopeAllowsIt() {
        DefaultToolRegistry whitelisting = newRegistry(List.of(FakeTools.MUTATING));
        FakeTools.registerAll(whitelisting);
        ToolScope writable = new ToolScope(
                Set.of(FakeTools.MUTATING), Set.of(ToolPermission.TEST_CASE_WRITE), true);

        try {
            ToolExecutionResult result = whitelisting.execute(
                    invocation(FakeTools.MUTATING, Map.of("note", "remember this")),
                    writable, RuntimeTestFixtures.PROFILE_VERSION, 1L);

            assertThat(result.successful()).isTrue();
            assertThat(result.modelSummary()).isEqualTo("Saved 1 note");
        } finally {
            whitelisting.shutdown();
        }
    }

    @Test
    void recordsApprovalRequestAndRefusalForToolsThatNeedIt() {
        ToolExecutionResult result = invoke(FakeTools.APPROVAL_REQUIRED, Map.of("note", "please run"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.APPROVAL_REJECTED);
        assertThat(eventsFor(result))
                .extracting(SessionEvent::eventType)
                .containsExactly(
                        SessionEventType.TOOL_CALL_REQUESTED,
                        SessionEventType.APPROVAL_REQUESTED,
                        SessionEventType.APPROVAL_RESOLVED,
                        SessionEventType.TOOL_CALL_FINISHED);
        ApprovalResolvedPayload resolved = eventsFor(result).stream()
                .filter(event -> event.eventType() == SessionEventType.APPROVAL_RESOLVED)
                .findFirst()
                .orElseThrow()
                .payloadAs(ApprovalResolvedPayload.class);
        assertThat(resolved.approved()).isFalse();
        assertThat(resolved.resolvedBy()).isEqualTo("POLICY");
    }

    @Test
    void truncatesResultsToTheDeclaredItemLimit() {
        ToolExecutionResult result = invoke(FakeTools.BULK, Map.of("count", 50));

        assertThat(result.successful()).isTrue();
        assertThat(result.truncated()).isTrue();
        assertThat(result.data()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(5);
    }

    @Test
    void redactsCredentialShapedContentBeforeItReachesTheModelOrTheLog() {
        ToolExecutionResult result = invoke(FakeTools.LEAKY, Map.of("note", "token=sk-0123456789abcdefghij"));

        assertThat(result.modelSummary()).doesNotContain("0123456789abcdefghij");
        assertThat(result.data().toString()).doesNotContain("sk-0123456789abcdefghij");
        SessionEvent requested = eventsFor(result).getFirst();
        assertThat(requested.payload().toString()).doesNotContain("sk-0123456789abcdefghij");
    }

    @Test
    void leavesNoToolCallOpenAcrossEveryFailurePath() {
        invoke(FakeTools.ECHO, Map.of("message", "hello", "repeat", 1));
        invoke("noSuchTool", Map.of());
        invoke(FakeTools.ECHO, Map.of("message", "", "repeat", 1));
        invoke(FakeTools.FAILING, Map.of("note", "x"));
        invoke(FakeTools.MUTATING, Map.of("note", "x"));
        invoke(FakeTools.APPROVAL_REQUIRED, Map.of("note", "x"));

        SessionProjection projection = lifecycleService.project(sessionId);
        assertThat(projection.toolCalls()).hasSize(6);
        assertThat(projection.openToolCalls(turnId)).isEmpty();
    }

    private ToolExecutionResult invoke(String toolName, Map<String, Object> arguments) {
        return registry.execute(
                invocation(toolName, arguments), scope, RuntimeTestFixtures.PROFILE_VERSION, 1L);
    }

    private ToolInvocation invocation(String toolName, Map<String, Object> arguments) {
        return new ToolInvocation(sessionId, turnId, null, null, AGENT, toolName, arguments);
    }

    private List<SessionEvent> eventsFor(ToolExecutionResult result) {
        return eventStore.readAll(sessionId).stream()
                .filter(event -> result.callId().equals(event.callId()))
                .toList();
    }

    private ToolCallStatus finishedStatus(ToolExecutionResult result) {
        return eventsFor(result).stream()
                .filter(event -> event.eventType() == SessionEventType.TOOL_CALL_FINISHED)
                .findFirst()
                .orElseThrow()
                .payloadAs(com.devpilot.runtime.session.payload.ToolCallFinishedPayload.class)
                .status();
    }

    private DefaultToolRegistry newRegistry(List<String> mutatingAllowList) {
        return new DefaultToolRegistry(
                eventStore,
                new DefaultDenyToolPolicy(mutatingAllowList),
                approvalService,
                redactor,
                resultProcessor,
                validator,
                objectMapper,
                appProperties);
    }
}
