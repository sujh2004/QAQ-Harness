package com.devpilot.runtime.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: the model gateway can be driven without an API key, and every provider must report the
 * observability fields the runtime records.
 */
class FakeModelGatewayContractTest {

    private static final ModelRequest REQUEST = new ModelRequest(
            "chat.default",
            List.of(ModelMessage.system("You analyse DevOps incidents."),
                    ModelMessage.user("why does order-service return 500?")),
            List.of(new ModelToolSpec("searchLogs", "Search system logs", Map.of("type", "object"))),
            0.2,
            512,
            "DASHSCOPE_API_KEY");

    @Test
    void streamsTextThenToolCallsThenTheFinalAnswer() {
        ModelToolCall toolCall = new ModelToolCall("call_1", "searchLogs", Map.of("level", "ERROR"));
        FakeModelGateway gateway = new FakeModelGateway(List.of("根据日志", "分析，"), List.of(toolCall));
        RecordingListener listener = new RecordingListener();

        ModelResponse response = gateway.stream(REQUEST, listener);

        assertThat(listener.events).containsExactly(
                "delta:根据日志", "delta:分析，", "tool:searchLogs", "completed");
        assertThat(response.content()).isEqualTo("根据日志分析，");
        assertThat(response.requestsTools()).isTrue();
        assertThat(response.toolCalls()).containsExactly(toolCall);
    }

    @Test
    void reportsTheObservabilityFieldsTheRuntimeRecords() {
        FakeModelGateway gateway = new FakeModelGateway(List.of("done"), List.of());

        ModelCallMetadata metadata = gateway.call(REQUEST).metadata();

        assertThat(metadata.provider()).isEqualTo("fake");
        assertThat(metadata.model()).isEqualTo("chat.default");
        assertThat(metadata.requestId()).isNotBlank();
        assertThat(metadata.totalDurationMs()).isPositive();
        assertThat(metadata.promptTokens()).isNotNull();
        assertThat(metadata.completionTokens()).isNotNull();
        assertThat(metadata.finishReason()).isEqualTo("stop");
    }

    @Test
    void carriesOnlyTheCredentialNameNotTheCredential() {
        FakeModelGateway gateway = new FakeModelGateway(List.of("done"), List.of());

        gateway.call(REQUEST);

        assertThat(gateway.receivedRequests()).singleElement()
                .satisfies(request -> assertThat(request.credentialRef()).isEqualTo("DASHSCOPE_API_KEY"));
    }

    private static final class RecordingListener implements ModelStreamListener {

        private final List<String> events = new ArrayList<>();

        @Override
        public void onTextDelta(String delta) {
            events.add("delta:" + delta);
        }

        @Override
        public void onToolCall(ModelToolCall toolCall) {
            events.add("tool:" + toolCall.toolName());
        }

        @Override
        public void onCompleted(ModelResponse response) {
            events.add("completed");
        }
    }
}
