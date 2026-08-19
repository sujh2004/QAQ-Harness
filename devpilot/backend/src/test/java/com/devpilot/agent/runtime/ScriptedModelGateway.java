package com.devpilot.agent.runtime;

import com.devpilot.runtime.model.ModelCallException;
import com.devpilot.runtime.model.ModelCallMetadata;
import com.devpilot.runtime.model.ModelGateway;
import com.devpilot.runtime.model.ModelRequest;
import com.devpilot.runtime.model.ModelResponse;
import com.devpilot.runtime.model.ModelStreamListener;
import com.devpilot.runtime.model.ModelToolCall;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A model provider that replays a script the test writes.
 *
 * <p>It exists so the agent loop can be verified end to end — tool selection, history projection,
 * step budgeting, failure handling — without an API key or a live vendor.
 */
public class ScriptedModelGateway implements ModelGateway {

    private final Deque<Supplier<ModelResponse>> script = new ArrayDeque<>();
    private final List<ModelRequest> received = new ArrayList<>();

    /** Clears the script and the recorded requests. */
    public void reset() {
        script.clear();
        received.clear();
    }

    /**
     * Queues a response asking for one tool.
     *
     * @param toolName tool the model asks for
     * @param arguments arguments the model supplies
     */
    public void enqueueToolCall(String toolName, Map<String, Object> arguments) {
        script.add(() -> new ModelResponse(
                "",
                List.of(new ModelToolCall("call_" + script.size(), toolName, arguments)),
                metadata("tool_calls")));
    }

    /**
     * Queues a final answer.
     *
     * @param content visible answer text
     */
    public void enqueueAnswer(String content) {
        script.add(() -> new ModelResponse(content, List.of(), metadata("stop")));
    }

    /**
     * Queues a provider failure.
     *
     * @param message safe failure message
     */
    public void enqueueFailure(String message) {
        script.add(() -> {
            throw new ModelCallException(message);
        });
    }

    /** @return every request the agent loop made, in order */
    public List<ModelRequest> received() {
        return List.copyOf(received);
    }

    @Override
    public ModelResponse call(ModelRequest request) {
        received.add(request);
        Supplier<ModelResponse> next = script.poll();
        if (next == null) {
            throw new IllegalStateException(
                    "The agent asked for more model responses than the test scripted");
        }
        return next.get();
    }

    @Override
    public ModelResponse stream(ModelRequest request, ModelStreamListener listener) {
        ModelResponse response = call(request);
        if (response.content() != null && !response.content().isEmpty()) {
            listener.onTextDelta(response.content());
        }
        response.toolCalls().forEach(listener::onToolCall);
        listener.onCompleted(response);
        return response;
    }

    private static ModelCallMetadata metadata(String finishReason) {
        return new ModelCallMetadata("scripted", "chat.default", "scripted-request", 1L, 2L, 10, 5,
                finishReason);
    }
}
