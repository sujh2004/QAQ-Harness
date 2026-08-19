package com.devpilot.runtime.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A model provider that replays a fixed script.
 *
 * <p>It lets the runtime contract tests exercise the streaming and tool-calling paths without an
 * API key, so continuous integration does not depend on a live vendor.
 */
public final class FakeModelGateway implements ModelGateway {

    private static final String PROVIDER = "fake";

    private final List<String> textDeltas;
    private final List<ModelToolCall> toolCalls;
    private final List<ModelRequest> receivedRequests = new ArrayList<>();

    /**
     * Creates the provider.
     *
     * @param textDeltas fragments to emit in order
     * @param toolCalls tool calls to emit after the text
     */
    public FakeModelGateway(List<String> textDeltas, List<ModelToolCall> toolCalls) {
        this.textDeltas = List.copyOf(textDeltas);
        this.toolCalls = List.copyOf(toolCalls);
    }

    @Override
    public ModelResponse call(ModelRequest request) {
        receivedRequests.add(request);
        return response(request);
    }

    @Override
    public ModelResponse stream(ModelRequest request, ModelStreamListener listener) {
        receivedRequests.add(request);
        textDeltas.forEach(listener::onTextDelta);
        toolCalls.forEach(listener::onToolCall);
        ModelResponse response = response(request);
        listener.onCompleted(response);
        return response;
    }

    /** @return every request this provider received, in order */
    public List<ModelRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }

    private ModelResponse response(ModelRequest request) {
        ModelCallMetadata metadata = new ModelCallMetadata(
                PROVIDER,
                request.modelRoute(),
                "fake-request-" + receivedRequests.size(),
                1L,
                2L,
                12,
                8,
                toolCalls.isEmpty() ? "stop" : "tool_calls");
        return new ModelResponse(String.join("", textDeltas), toolCalls, metadata);
    }
}
