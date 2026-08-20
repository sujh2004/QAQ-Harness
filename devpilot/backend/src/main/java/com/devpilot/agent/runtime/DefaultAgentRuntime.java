package com.devpilot.agent.runtime;

import com.devpilot.agent.config.AgentDefinition;
import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.StepStatus;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.model.ModelCallException;
import com.devpilot.runtime.model.ModelGateway;
import com.devpilot.runtime.model.ModelMessage;
import com.devpilot.runtime.model.ModelRequest;
import com.devpilot.runtime.model.ModelResponse;
import com.devpilot.runtime.model.ModelToolCall;
import com.devpilot.runtime.projection.TurnView;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.tool.ToolInvocation;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The agent loop: think, call tools, observe, answer.
 *
 * <p>The loop is DevPilot's own rather than a framework's, because every boundary it crosses has to
 * become an event. Each iteration opens a step, projects the model history from committed events,
 * makes at most one model request and runs whatever tools the model asked for through
 * {@link ToolRegistry}. Nothing reaches a provider directly and nothing enters the next model
 * request that was not recorded first.
 *
 * <p>A model provider is only ever reached through {@link ModelGateway}, so swapping vendors does
 * not touch this class, the prompts or the tools.
 */
@Service
public class DefaultAgentRuntime implements AgentRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAgentRuntime.class);
    private static final int SUMMARY_LIMIT = 200;

    private final AgentRegistry agentRegistry;
    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final SessionLifecycleService lifecycleService;
    private final SessionEventStore eventStore;
    private final ModelHistoryProjector historyProjector;

    /**
     * Creates the runtime.
     *
     * @param agentRegistry agent composition
     * @param modelGateway model provider slot
     * @param toolRegistry tool execution pipeline
     * @param lifecycleService lifecycle driver
     * @param eventStore committed event log
     * @param historyProjector model history projection
     */
    public DefaultAgentRuntime(
            AgentRegistry agentRegistry,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            SessionLifecycleService lifecycleService,
            SessionEventStore eventStore,
            ModelHistoryProjector historyProjector) {
        this.agentRegistry = agentRegistry;
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.lifecycleService = lifecycleService;
        this.eventStore = eventStore;
        this.historyProjector = historyProjector;
    }

    @Override
    public AgentTurnResult runTurn(AgentTurnRequest request) {
        AgentDefinition agent = agentRegistry.require(request.agentName());
        if (turnIsClosed(request)) {
            // Cancelled before the agent even started; doing the work would waste a model call on an
            // answer nobody will see.
            return new AgentTurnResult(null, RunStatus.CANCELLED, null, "Turn is no longer running");
        }

        ToolScope scope = agentRegistry.scopeOf(agent, request.projectId());
        String systemPrompt = agentRegistry.systemPrompt(agent);
        String profileVersion = agentRegistry.profileVersion();

        String runId = lifecycleService.startAgentRun(
                request.sessionId(), request.turnId(), null, request.parentRunId(),
                agent.name(), agent.displayName(), request.userMessage());

        try {
            return loop(request, agent, scope, systemPrompt, profileVersion, runId);
        } catch (ModelCallException exception) {
            return fail(request, runId, "MODEL_CALL_FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Agent {} failed in session {}", agent.name(), request.sessionId(), exception);
            return fail(request, runId, "AGENT_EXECUTION_ERROR",
                    "Agent " + agent.name() + " failed with " + exception.getClass().getSimpleName());
        }
    }

    private AgentTurnResult loop(
            AgentTurnRequest request,
            AgentDefinition agent,
            ToolScope scope,
            String systemPrompt,
            String profileVersion,
            String runId) {

        for (int step = 0; step < agent.maxSteps(); step++) {
            if (turnIsClosed(request)) {
                // The user cancelled, or recovery closed the turn. Stop instead of doing work whose
                // result nobody will ever see.
                return new AgentTurnResult(runId, RunStatus.CANCELLED, null, "Turn is no longer running");
            }

            String stepId = lifecycleService.startStep(
                    request.sessionId(), request.turnId(), runId, "model request " + (step + 1));

            List<ModelMessage> history = historyProjector.project(
                    systemPrompt, eventStore.readAll(request.sessionId()), request.turnId(), runId);
            ModelResponse response = modelGateway.call(new ModelRequest(
                    agent.modelRoute(), history, agentRegistry.toolSpecs(scope), null, null, null));

            if (response.requestsTools()) {
                for (ModelToolCall toolCall : response.toolCalls()) {
                    toolRegistry.execute(
                            new ToolInvocation(
                                    request.sessionId(), request.turnId(), stepId, runId,
                                    agent.name(), toolCall.toolName(), toolCall.arguments()),
                            scope, profileVersion, request.projectId());
                }
                lifecycleService.endStep(request.sessionId(), stepId, StepStatus.COMPLETED,
                        "collected " + response.toolCalls().size() + " tool result(s)");
                continue;
            }

            String answer = response.content() == null ? "" : response.content();
            lifecycleService.recordAssistantMessage(
                    request.sessionId(), request.turnId(), stepId, runId, agent.name(), answer);
            lifecycleService.endStep(
                    request.sessionId(), stepId, StepStatus.COMPLETED, "answer produced");
            lifecycleService.finishAgentRun(
                    request.sessionId(), runId, RunStatus.COMPLETED, summarize(answer), null);
            return new AgentTurnResult(runId, RunStatus.COMPLETED, answer, null);
        }

        String message = "Agent " + agent.name() + " reached its limit of " + agent.maxSteps()
                + " model requests without producing an answer";
        return fail(request, runId, "AGENT_EXECUTION_ERROR", message);
    }

    private boolean turnIsClosed(AgentTurnRequest request) {
        return lifecycleService.project(request.sessionId())
                .turn(request.turnId())
                .map(TurnView::status)
                .map(status -> status.terminal())
                .orElse(true);
    }

    private AgentTurnResult fail(
            AgentTurnRequest request, String runId, String errorCode, String message) {
        lifecycleService.recordRuntimeError(
                request.sessionId(), request.turnId(), errorCode, message, "AGENT");
        lifecycleService.finishAgentRun(request.sessionId(), runId, RunStatus.FAILED, null, message);
        return new AgentTurnResult(runId, RunStatus.FAILED, null, message);
    }

    private static String summarize(String answer) {
        String collapsed = answer.replace('\n', ' ').strip();
        return collapsed.length() <= SUMMARY_LIMIT
                ? collapsed
                : collapsed.substring(0, SUMMARY_LIMIT) + "…";
    }
}
