package com.devpilot.agent.runtime;

import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.model.ModelMessage;
import com.devpilot.runtime.model.ModelToolCall;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.payload.AgentStartedPayload;
import com.devpilot.runtime.session.payload.AssistantMessagePayload;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.devpilot.runtime.session.payload.ToolCallRequestedPayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the model conversation from committed events.
 *
 * <p>This is what "model-visible means logged" means in practice: the history handed to a model is
 * derived from the event log, never from memory a crash could lose. Anything the model has not seen
 * recorded, it does not see at all.
 *
 * <p>A tool exchange is replayed as the provider expects it: the assistant turn that asked for the
 * call, then the result under the same {@code callId}.
 *
 * <p>Context is scoped to the agent run, which is what makes delegation work. An outermost agent
 * sees the conversation so it remembers what was discussed. A specialist that a supervisor
 * delegated to sees only the task it was given plus the evidence it gathered itself — sibling
 * specialists' output would otherwise read as work this agent had already done.
 */
@Component
public class ModelHistoryProjector {

    /**
     * Projects the conversation one agent run should see.
     *
     * @param systemPrompt persona of the running agent
     * @param events committed events of the session, in sequence order
     * @param turnId turn the run belongs to
     * @param runId the agent run, null to project the whole turn
     * @return model messages in conversation order
     */
    public List<ModelMessage> project(
            String systemPrompt, List<SessionEvent> events, String turnId, String runId) {
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(ModelMessage.system(systemPrompt));

        String delegatedTask = delegatedTaskOf(events, runId);
        if (delegatedTask != null) {
            messages.add(ModelMessage.user(delegatedTask));
        }

        for (SessionEvent event : events) {
            switch (event.eventType()) {
                case USER_MESSAGE -> {
                    if (delegatedTask == null) {
                        messages.add(ModelMessage.user(
                                event.payloadAs(UserMessagePayload.class).content()));
                    }
                }
                case ASSISTANT_MESSAGE -> {
                    if (delegatedTask == null) {
                        messages.add(ModelMessage.assistant(
                                event.payloadAs(AssistantMessagePayload.class).content()));
                    }
                }
                case TOOL_CALL_REQUESTED -> {
                    if (belongsToRun(event, turnId, runId)) {
                        ToolCallRequestedPayload payload = event.payloadAs(ToolCallRequestedPayload.class);
                        messages.add(ModelMessage.assistantToolCall(new ModelToolCall(
                                event.callId(), payload.toolName(), payload.arguments())));
                    }
                }
                case TOOL_CALL_FINISHED -> {
                    if (belongsToRun(event, turnId, runId)) {
                        ToolCallFinishedPayload payload = event.payloadAs(ToolCallFinishedPayload.class);
                        messages.add(ModelMessage.tool(
                                payload.toolName(), event.callId(), observationOf(payload)));
                    }
                }
                default -> {
                    // Lifecycle, delta and approval events carry no model-visible content.
                }
            }
        }
        return List.copyOf(messages);
    }

    /**
     * Finds the task a supervisor handed to this run, if it was delegated at all.
     *
     * <p>The task is read from the {@code agent_started} event rather than from the caller, so what
     * the specialist sees is exactly what the audit trail recorded.
     *
     * @param events committed events
     * @param runId the agent run
     * @return delegated task, null when this run is not a delegation
     */
    private static String delegatedTaskOf(List<SessionEvent> events, String runId) {
        if (runId == null) {
            return null;
        }
        return events.stream()
                .filter(event -> event.eventType() == SessionEventType.AGENT_STARTED)
                .filter(event -> runId.equals(event.runId()))
                .map(event -> event.payloadAs(AgentStartedPayload.class))
                .filter(payload -> payload.parentRunId() != null)
                .map(AgentStartedPayload::inputSummary)
                .findFirst()
                .orElse(null);
    }

    private static boolean belongsToRun(SessionEvent event, String turnId, String runId) {
        if (!turnId.equals(event.turnId())) {
            return false;
        }
        return runId == null || Objects.equals(runId, event.runId());
    }

    private static String observationOf(ToolCallFinishedPayload payload) {
        if (payload.status() == ToolCallStatus.SUCCESS) {
            return payload.resultSummary() == null ? "(tool returned no result)" : payload.resultSummary();
        }
        return "调用未成功（" + payload.errorCode() + "）：" + payload.message();
    }
}
