package com.devpilot.runtime.model.provider;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.model.ModelCallException;
import com.devpilot.runtime.model.ModelCallMetadata;
import com.devpilot.runtime.model.ModelGateway;
import com.devpilot.runtime.model.ModelMessage;
import com.devpilot.runtime.model.ModelRequest;
import com.devpilot.runtime.model.ModelResponse;
import com.devpilot.runtime.model.ModelStreamListener;
import com.devpilot.runtime.model.ModelToolCall;
import com.devpilot.runtime.model.ModelToolSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reaches a language model through Spring AI.
 *
 * <p>This is the provider side of {@link ModelGateway}: whichever Spring AI {@code ChatModel} bean
 * a deployment supplies — DashScope through {@code spring-ai-alibaba-starter-dashscope}, or another
 * vendor — becomes DevPilot's model. Nothing above this class knows which one it is.
 *
 * <p>Internal tool execution is switched off deliberately. Spring AI is asked to publish the tool
 * schemas and to report which tools the model wants, but never to run them: running them here would
 * bypass DevPilot's scope resolution, authorisation, timeouts, result limits and the paired
 * {@code tool_call_requested} / {@code tool_call_finished} events.
 */
@Component
public class SpringAiModelGateway implements ModelGateway {

    private static final String DEFAULT_ROUTE = "chat.default";

    private final ObjectProvider<ChatModel> chatModels;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    /**
     * Creates the gateway.
     *
     * @param chatModels chat model bean, resolved lazily so the application still starts without a
     *     configured provider
     * @param objectMapper shared JSON mapper used for tool schemas and arguments
     * @param appProperties application configuration supplying the model names
     */
    public SpringAiModelGateway(
            ObjectProvider<ChatModel> chatModels, ObjectMapper objectMapper, AppProperties appProperties) {
        this.chatModels = chatModels;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public ModelResponse call(ModelRequest request) {
        ChatModel chatModel = chatModels.getIfAvailable();
        if (chatModel == null) {
            throw new ModelCallException("No chat model is configured. Set DASHSCOPE_API_KEY "
                    + "(see .env.example) to enable agent runs.");
        }

        long startedAt = System.nanoTime();
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(toSpringMessages(request), toOptions(request)));
        } catch (RuntimeException exception) {
            throw new ModelCallException(
                    "Model route " + request.modelRoute() + " failed with "
                            + exception.getClass().getSimpleName(), exception);
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        Generation generation = response.getResult();
        AssistantMessage output = generation == null ? null : generation.getOutput();
        String content = output == null || output.getText() == null ? "" : output.getText();

        return new ModelResponse(content, toDevPilotToolCalls(output), metadataOf(response, durationMs));
    }

    @Override
    public ModelResponse stream(ModelRequest request, ModelStreamListener listener) {
        // Token streaming arrives with the SSE chat endpoint. Until then the whole answer is
        // delivered as one delta, which keeps the listener contract honest rather than pretending
        // to stream.
        ModelResponse response = call(request);
        if (!response.content().isEmpty()) {
            listener.onTextDelta(response.content());
        }
        response.toolCalls().forEach(listener::onToolCall);
        listener.onCompleted(response);
        return response;
    }

    private List<Message> toSpringMessages(ModelRequest request) {
        List<Message> messages = new ArrayList<>(request.messages().size());
        for (ModelMessage message : request.messages()) {
            String text = message.content() == null ? "" : message.content();
            switch (message.role()) {
                case SYSTEM -> messages.add(new SystemMessage(text));
                case USER -> messages.add(new UserMessage(text));
                case ASSISTANT -> messages.add(message.toolCalls().isEmpty()
                        ? new AssistantMessage(text)
                        : AssistantMessage.builder()
                                .content(text)
                                .toolCalls(message.toolCalls().stream()
                                        .map(this::toSpringToolCall)
                                        .toList())
                                .build());
                case TOOL -> messages.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                message.callId(), message.name(), text)))
                        .build());
            }
        }
        return messages;
    }

    private AssistantMessage.ToolCall toSpringToolCall(ModelToolCall toolCall) {
        return new AssistantMessage.ToolCall(
                toolCall.callId(), "function", toolCall.toolName(), writeJson(toolCall.arguments()));
    }

    private DefaultToolCallingChatOptions toOptions(ModelRequest request) {
        DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
        options.setModel(resolveModel(request.modelRoute()));
        options.setTemperature(request.temperature());
        options.setMaxTokens(request.maxOutputTokens());
        options.setToolCallbacks(toCallbacks(request.tools()));
        // DevPilot runs its own tool pipeline; the provider must only report what the model wants.
        options.setInternalToolExecutionEnabled(false);
        return options;
    }

    private List<ToolCallback> toCallbacks(List<ModelToolSpec> specs) {
        return specs.stream()
                .map(spec -> (ToolCallback) new SchemaOnlyToolCallback(
                        spec.name(), spec.description(), writeJson(spec.inputSchema())))
                .toList();
    }

    private String resolveModel(String modelRoute) {
        if (modelRoute == null || modelRoute.isBlank() || DEFAULT_ROUTE.equals(modelRoute)) {
            return appProperties.ai().chatModel();
        }
        // An unrecognised route is treated as a literal model name so a profile can pin a specific
        // model without a code change.
        return modelRoute;
    }

    private List<ModelToolCall> toDevPilotToolCalls(AssistantMessage output) {
        if (output == null || !output.hasToolCalls()) {
            return List.of();
        }
        return output.getToolCalls().stream()
                .map(call -> new ModelToolCall(call.id(), call.name(), readArguments(call.arguments())))
                .toList();
    }

    private Map<String, Object> readArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new ModelCallException("Model returned tool arguments that are not valid JSON");
        }
    }

    private static ModelCallMetadata metadataOf(ChatResponse response, long durationMs) {
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        Generation generation = response.getResult();
        String finishReason = generation == null || generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();

        return new ModelCallMetadata(
                "spring-ai",
                metadata == null ? null : metadata.getModel(),
                metadata == null ? null : metadata.getId(),
                0L,
                durationMs,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                finishReason);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ModelCallException("Tool schema or arguments cannot be serialized");
        }
    }
}
