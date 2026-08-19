package com.devpilot.agent.runtime;

import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.model.ModelMessage;
import com.devpilot.runtime.model.ModelToolCall;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.payload.AssistantMessagePayload;
import com.devpilot.runtime.session.payload.ToolCallFinishedPayload;
import com.devpilot.runtime.session.payload.ToolCallRequestedPayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the model conversation from committed events.
 *
 * <p>This is what "model-visible means logged" means in practice: the history handed to a model is
 * derived from the event log, never from memory a crash could lose. Anything the model has not seen
 * recorded, it does not see at all.
 *
 * <p>A tool exchange is replayed as the provider expects it: the assistant turn that asked for the
 * call, then the result under the same {@code callId}. Both halves come from the event pair, so the
 * conversation a provider receives is exactly the conversation the audit trail shows.
 *
 * <p>Context policy: user and assistant messages of the whole session are replayed so the agent
 * remembers the conversation, while tool exchanges are limited to the current turn — stale tool
 * output from earlier questions is rarely useful and would crowd out the evidence that matters.
 */
@Component
public class ModelHistoryProjector {

    /**
     * Projects a model conversation.
     *
     * @param systemPrompt persona of the running agent
     * @param events committed events of the session, in sequence order
     * @param currentTurnId turn whose tool exchanges are included
     * @return model messages in conversation order
     */
    public List<ModelMessage> project(String systemPrompt, List<SessionEvent> events, String currentTurnId) {
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(ModelMessage.system(systemPrompt));

        for (SessionEvent event : events) {
            switch (event.eventType()) {
                case USER_MESSAGE -> messages.add(
                        ModelMessage.user(event.payloadAs(UserMessagePayload.class).content()));
                case ASSISTANT_MESSAGE -> messages.add(
                        ModelMessage.assistant(event.payloadAs(AssistantMessagePayload.class).content()));
                case TOOL_CALL_REQUESTED -> {
                    if (currentTurnId.equals(event.turnId())) {
                        ToolCallRequestedPayload payload = event.payloadAs(ToolCallRequestedPayload.class);
                        messages.add(ModelMessage.assistantToolCall(new ModelToolCall(
                                event.callId(), payload.toolName(), payload.arguments())));
                    }
                }
                case TOOL_CALL_FINISHED -> {
                    if (currentTurnId.equals(event.turnId())) {
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

    private static String observationOf(ToolCallFinishedPayload payload) {
        if (payload.status() == ToolCallStatus.SUCCESS) {
            return payload.resultSummary() == null ? "(tool returned no result)" : payload.resultSummary();
        }
        return "调用未成功（" + payload.errorCode() + "）：" + payload.message();
    }
}
