package com.devpilot.runtime.tool;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.approval.ApprovalDecision;
import com.devpilot.runtime.approval.ApprovalRequest;
import com.devpilot.runtime.approval.ApprovalService;
import com.devpilot.runtime.lifecycle.RuntimeIds;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.session.AppendEventCommand;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.payload.ApprovalRequestedPayload;
import com.devpilot.runtime.session.payload.ApprovalResolvedPayload;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.devpilot.runtime.session.payload.ToolCallRequestedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The tool execution pipeline.
 *
 * <p>Every call runs through the same sequence: resolve the tool in the caller's scope, validate
 * the arguments, append {@code tool_call_requested}, authorise, obtain approval where required,
 * enforce the timeout and concurrency policy, execute the provider, limit and redact the result,
 * and append {@code tool_call_finished}. Rejections take the same path, so a denied or malformed
 * call is as auditable as a successful one and no {@code tool_call_requested} is ever left open.
 */
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultToolRegistry.class);

    private final Map<String, RegisteredTool> tools = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> exclusiveLocks = new ConcurrentHashMap<>();

    private final SessionEventStore eventStore;
    private final ToolPolicy policy;
    private final ApprovalService approvalService;
    private final SensitiveDataRedactor redactor;
    private final ToolResultProcessor resultProcessor;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    /**
     * Creates the registry.
     *
     * @param eventStore append-only event storage
     * @param policy authorisation policy
     * @param approvalService human-in-the-loop gate
     * @param redactor credential redactor
     * @param resultProcessor result limiter
     * @param validator bean validator for tool arguments
     * @param objectMapper shared JSON mapper used to bind arguments
     * @param appProperties application configuration
     */
    public DefaultToolRegistry(
            SessionEventStore eventStore,
            ToolPolicy policy,
            ApprovalService approvalService,
            SensitiveDataRedactor redactor,
            ToolResultProcessor resultProcessor,
            Validator validator,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        this.eventStore = eventStore;
        this.policy = policy;
        this.approvalService = approvalService;
        this.redactor = redactor;
        this.resultProcessor = resultProcessor;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(
                appProperties.runtime().tool().maxConcurrentExecutions(),
                Thread.ofPlatform().name("devpilot-tool-", 0).daemon().factory());
    }

    @Override
    public void register(ToolDefinition definition, ToolHandler<?> handler) {
        RegisteredTool previous =
                tools.putIfAbsent(definition.name(), new RegisteredTool(definition, handler));
        if (previous != null) {
            throw new IllegalStateException("Tool " + definition.name() + " is already registered");
        }
    }

    @Override
    public Optional<ToolDefinition> find(String toolName) {
        return Optional.ofNullable(tools.get(toolName)).map(RegisteredTool::definition);
    }

    @Override
    public List<ToolDefinition> registeredTools() {
        return tools.values().stream()
                .map(RegisteredTool::definition)
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }

    @Override
    public List<ToolDefinition> visibleTools(ToolScope scope) {
        return tools.values().stream()
                .map(RegisteredTool::definition)
                .filter(definition -> scope.canSee(definition.name()))
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }

    @Override
    public ToolExecutionResult execute(
            ToolInvocation invocation, ToolScope scope, String profileVersion, Long projectId) {
        String callId = RuntimeIds.newCallId();
        Map<String, Object> safeArguments = redactor.redactArguments(invocation.arguments());

        RegisteredTool registered = tools.get(invocation.toolName());
        if (registered == null) {
            return rejectBeforeExecution(invocation, callId, null, safeArguments, ToolCallStatus.DENIED,
                    ToolErrorCode.TOOL_NOT_FOUND, "Tool " + invocation.toolName() + " is not registered");
        }

        ToolDefinition definition = registered.definition();
        if (!scope.canSee(definition.name())) {
            return rejectBeforeExecution(invocation, callId, definition, safeArguments, ToolCallStatus.DENIED,
                    ToolErrorCode.TOOL_NOT_VISIBLE,
                    "Tool " + definition.name() + " is not available to agent " + invocation.agentName());
        }

        Object arguments;
        try {
            arguments = bindAndValidate(definition, invocation.arguments());
        } catch (InvalidToolArgumentsException exception) {
            return rejectBeforeExecution(invocation, callId, definition, safeArguments,
                    ToolCallStatus.INVALID_ARGUMENT, ToolErrorCode.INVALID_ARGUMENT, exception.getMessage());
        }

        appendRequested(invocation, callId, definition, safeArguments);

        ToolPolicyDecision decision =
                policy.decide(new ToolPolicyContext(invocation, definition, scope, profileVersion, projectId));
        if (decision.outcome() == ToolPolicyOutcome.DENY) {
            return finishFailure(invocation, callId, definition, ToolCallStatus.DENIED,
                    decision.errorCode(), decision.reason(), 0L);
        }
        if (decision.outcome() == ToolPolicyOutcome.REQUIRE_APPROVAL) {
            ApprovalDecision approval = requestApproval(invocation, callId, definition, safeArguments, decision);
            if (!approval.approved()) {
                return finishFailure(invocation, callId, definition, ToolCallStatus.DENIED,
                        ToolErrorCode.APPROVAL_REJECTED, approval.reason(), 0L);
            }
        }

        return runProvider(invocation, callId, registered, arguments, projectId);
    }

    /** Stops the tool execution pool when the application shuts down. */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private ToolExecutionResult runProvider(
            ToolInvocation invocation,
            String callId,
            RegisteredTool registered,
            Object arguments,
            Long projectId) {
        ToolDefinition definition = registered.definition();
        ReentrantLock lock = definition.concurrency() == ConcurrencyMode.EXCLUSIVE
                ? exclusiveLocks.computeIfAbsent(
                        invocation.sessionId() + "::" + definition.name(), key -> new ReentrantLock())
                : null;

        long startedAt = System.nanoTime();
        if (lock != null) {
            lock.lock();
        }
        try {
            Future<ToolResult> future = executor.submit(
                    () -> invokeHandler(registered, invocation, callId, arguments, projectId));
            try {
                ToolResult raw = future.get(definition.timeout().toMillis(), TimeUnit.MILLISECONDS);
                ProcessedToolResult processed = resultProcessor.process(raw, definition);
                long durationMs = elapsedMs(startedAt);
                appendFinished(invocation, callId, definition, ToolCallStatus.SUCCESS, null, null,
                        processed.persistSummary(), processed.truncated(), durationMs);
                return new ToolExecutionResult(callId, definition.name(), ToolCallStatus.SUCCESS, null, null,
                        processed.modelSummary(), processed.data(), processed.truncated(), durationMs);
            } catch (TimeoutException exception) {
                future.cancel(true);
                return finishFailure(invocation, callId, definition, ToolCallStatus.TIMEOUT, ToolErrorCode.TIMEOUT,
                        "Tool " + definition.name() + " exceeded " + definition.timeout().toMillis() + " ms",
                        elapsedMs(startedAt));
            } catch (ExecutionException exception) {
                return finishFailure(invocation, callId, definition, ToolCallStatus.PROVIDER_ERROR,
                        providerErrorCode(exception.getCause()),
                        providerMessage(definition, exception.getCause()), elapsedMs(startedAt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return finishFailure(invocation, callId, definition, ToolCallStatus.CANCELLED, ToolErrorCode.ABORTED,
                        "Tool " + definition.name() + " was interrupted", elapsedMs(startedAt));
            }
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ToolResult invokeHandler(
            RegisteredTool registered,
            ToolInvocation invocation,
            String callId,
            Object arguments,
            Long projectId) throws Exception {
        ToolHandler<Object> handler = (ToolHandler<Object>) registered.handler();
        return handler.execute(new ToolExecutionContext<>(
                invocation.sessionId(),
                projectId,
                invocation.turnId(),
                invocation.stepId(),
                invocation.runId(),
                callId,
                invocation.agentName(),
                registered.definition(),
                arguments));
    }

    private ApprovalDecision requestApproval(
            ToolInvocation invocation,
            String callId,
            ToolDefinition definition,
            Map<String, Object> safeArguments,
            ToolPolicyDecision policyDecision) {
        eventStore.append(invocation.sessionId(), AppendEventCommand.ofCall(
                SessionEventType.APPROVAL_REQUESTED, invocation.turnId(), invocation.stepId(),
                invocation.runId(), callId,
                new ApprovalRequestedPayload(
                        invocation.agentName(), definition.name(), policyDecision.reason(), safeArguments)));

        ApprovalDecision approval = approvalService.request(new ApprovalRequest(
                invocation.sessionId(), invocation.turnId(), invocation.agentName(), definition.name(),
                safeArguments, policyDecision.reason()));

        eventStore.append(invocation.sessionId(), AppendEventCommand.ofCall(
                SessionEventType.APPROVAL_RESOLVED, invocation.turnId(), invocation.stepId(),
                invocation.runId(), callId,
                new ApprovalResolvedPayload(
                        definition.name(), approval.approved(), approval.resolvedBy(), approval.reason())));
        return approval;
    }

    private ToolExecutionResult rejectBeforeExecution(
            ToolInvocation invocation,
            String callId,
            ToolDefinition definition,
            Map<String, Object> safeArguments,
            ToolCallStatus status,
            ToolErrorCode errorCode,
            String message) {
        appendRequested(invocation, callId, definition, safeArguments);
        return finishFailure(invocation, callId, definition, status, errorCode, message, 0L);
    }

    private void appendRequested(
            ToolInvocation invocation,
            String callId,
            ToolDefinition definition,
            Map<String, Object> safeArguments) {
        eventStore.append(invocation.sessionId(), AppendEventCommand.ofCall(
                SessionEventType.TOOL_CALL_REQUESTED, invocation.turnId(), invocation.stepId(),
                invocation.runId(), callId,
                new ToolCallRequestedPayload(
                        invocation.agentName(),
                        invocation.toolName(),
                        definition == null ? null : definition.version(),
                        safeArguments,
                        requestSummary(invocation, safeArguments))));
    }

    private ToolExecutionResult finishFailure(
            ToolInvocation invocation,
            String callId,
            ToolDefinition definition,
            ToolCallStatus status,
            ToolErrorCode errorCode,
            String message,
            long durationMs) {
        String safeMessage = redactor.redactText(message);
        appendFinished(invocation, callId, definition, status, errorCode, safeMessage, null, false, durationMs);
        return new ToolExecutionResult(callId, invocation.toolName(), status, errorCode, safeMessage,
                safeMessage, null, false, durationMs);
    }

    private void appendFinished(
            ToolInvocation invocation,
            String callId,
            ToolDefinition definition,
            ToolCallStatus status,
            ToolErrorCode errorCode,
            String message,
            String resultSummary,
            boolean truncated,
            long durationMs) {
        eventStore.append(invocation.sessionId(), AppendEventCommand.ofCall(
                SessionEventType.TOOL_CALL_FINISHED, invocation.turnId(), invocation.stepId(),
                invocation.runId(), callId,
                new ToolCallFinishedPayload(
                        invocation.agentName(),
                        definition == null ? invocation.toolName() : definition.name(),
                        status,
                        errorCode,
                        message,
                        resultSummary,
                        truncated,
                        durationMs)));
    }

    private Object bindAndValidate(ToolDefinition definition, Map<String, Object> rawArguments) {
        Object arguments;
        try {
            arguments = objectMapper.convertValue(rawArguments, definition.argumentType());
        } catch (IllegalArgumentException exception) {
            throw new InvalidToolArgumentsException(
                    "Arguments do not match the input schema of " + definition.name());
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(arguments);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new InvalidToolArgumentsException(detail);
        }
        return arguments;
    }

    private static ToolErrorCode providerErrorCode(Throwable cause) {
        return cause instanceof ToolExecutionException typed ? typed.errorCode() : ToolErrorCode.PROVIDER_ERROR;
    }

    private String providerMessage(ToolDefinition definition, Throwable cause) {
        if (cause instanceof ToolExecutionException typed) {
            return typed.getMessage();
        }
        // An arbitrary provider exception may carry paths or credentials in its message, so only the
        // exception type reaches the model. The full stack trace stays in the server log.
        LOGGER.error("Tool {} failed with an unexpected provider error", definition.name(), cause);
        return "Tool " + definition.name() + " failed with " + cause.getClass().getSimpleName();
    }

    private static long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static String requestSummary(ToolInvocation invocation, Map<String, Object> safeArguments) {
        return invocation.toolName() + "(" + String.join(", ", safeArguments.keySet()) + ")";
    }

    private record RegisteredTool(ToolDefinition definition, ToolHandler<?> handler) {
    }

    /** Signals that arguments failed schema binding or bean validation. */
    private static final class InvalidToolArgumentsException extends RuntimeException {
        private InvalidToolArgumentsException(String message) {
            super(message);
        }
    }
}
