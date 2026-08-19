package com.devpilot.runtime.tool;

import com.devpilot.runtime.lifecycle.ToolErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Tools that exist only for the runtime contract tests.
 *
 * <p>They are deliberately kept in the test source tree: the production build ships no tool until
 * Phase 3 adds the real code and log providers.
 */
final class FakeTools {

    static final String ECHO = "fakeEcho";
    static final String SLOW = "fakeSlow";
    static final String FAILING = "fakeFailing";
    static final String TYPED_FAILING = "fakeTypedFailing";
    static final String MUTATING = "fakeSaveNotes";
    static final String APPROVAL_REQUIRED = "fakeApprovalRequired";
    static final String BULK = "fakeBulkSearch";
    static final String OVERSIZED = "fakeOversized";
    static final String LEAKY = "fakeLeakyRead";

    private FakeTools() {
    }

    /** Arguments of the echo tool. */
    record EchoArguments(@NotBlank String message, @Min(1) @Max(10) int repeat) {
    }

    /** Arguments of the slow tool. */
    record DelayArguments(@Min(0) long delayMs) {
    }

    /** Arguments of the tools that only need a free-text note. */
    record NoteArguments(@NotBlank String note) {
    }

    /** Arguments of the tools that produce a configurable amount of output. */
    record CountArguments(@Min(1) @Max(10_000) int count) {
    }

    /**
     * Registers every fake tool.
     *
     * @param registry registry under test
     */
    static void registerAll(ToolRegistry registry) {
        registry.register(echoDefinition(), (ToolHandler<EchoArguments>) context -> {
            EchoArguments arguments = context.arguments();
            List<String> lines = IntStream.range(0, arguments.repeat())
                    .mapToObj(index -> arguments.message() + "#" + index)
                    .toList();
            return ToolResult.of(lines, lines.size(), "Echoed " + lines.size() + " line(s)");
        });

        registry.register(
                ToolDefinition.builder(SLOW, DelayArguments.class)
                        .description("Sleeps to exercise the timeout path")
                        .requiredPermission(ToolPermission.CODE_READ)
                        .timeout(Duration.ofMillis(100))
                        .build(),
                (ToolHandler<DelayArguments>) context -> {
                    Thread.sleep(context.arguments().delayMs());
                    return ToolResult.of(List.of("done"), 1, "Slept");
                });

        registry.register(
                ToolDefinition.builder(FAILING, NoteArguments.class)
                        .description("Throws an untyped provider exception")
                        .requiredPermission(ToolPermission.CODE_READ)
                        .build(),
                (ToolHandler<NoteArguments>) context -> {
                    throw new IllegalStateException("cannot read D:/secrets/application-prod.yml");
                });

        registry.register(
                ToolDefinition.builder(TYPED_FAILING, NoteArguments.class)
                        .description("Throws a typed provider exception with a safe message")
                        .requiredPermission(ToolPermission.CODE_READ)
                        .build(),
                (ToolHandler<NoteArguments>) context -> {
                    throw new ToolExecutionException(
                            ToolErrorCode.PROVIDER_ERROR, "Configured repository path cannot be accessed");
                });

        registry.register(
                ToolDefinition.builder(MUTATING, NoteArguments.class)
                        .description("Writes state and must be refused by default")
                        .sideEffect(SideEffectLevel.MUTATING)
                        .concurrency(ConcurrencyMode.EXCLUSIVE)
                        .requiredPermission(ToolPermission.TEST_CASE_WRITE)
                        .build(),
                (ToolHandler<NoteArguments>) context ->
                        ToolResult.of(List.of(context.arguments().note()), 1, "Saved 1 note"));

        registry.register(
                ToolDefinition.builder(APPROVAL_REQUIRED, NoteArguments.class)
                        .description("Needs human approval before it runs")
                        .requiredPermission(ToolPermission.CODE_READ)
                        .requiresApproval(true)
                        .build(),
                (ToolHandler<NoteArguments>) context -> ToolResult.of(List.of("approved"), 1, "Ran"));

        registry.register(
                ToolDefinition.builder(BULK, CountArguments.class)
                        .description("Returns more items than the declared limit")
                        .requiredPermission(ToolPermission.LOG_READ)
                        .maxResultItems(5)
                        .displayIntent(ToolDisplayIntent.SEARCH)
                        .build(),
                (ToolHandler<CountArguments>) context -> {
                    List<String> items = IntStream.range(0, context.arguments().count())
                            .mapToObj(index -> "row-" + index)
                            .toList();
                    return ToolResult.of(items, items.size(), "Found " + items.size() + " row(s)");
                });

        registry.register(
                ToolDefinition.builder(OVERSIZED, CountArguments.class)
                        .description("Returns a payload larger than the declared byte budget")
                        .requiredPermission(ToolPermission.LOG_READ)
                        .maxResultBytes(128)
                        .build(),
                (ToolHandler<CountArguments>) context ->
                        ToolResult.of("x".repeat(context.arguments().count()), 1, "Produced a large document"));

        registry.register(
                ToolDefinition.builder(LEAKY, NoteArguments.class)
                        .description("Returns credential-shaped content that must be redacted")
                        .requiredPermission(ToolPermission.CODE_READ)
                        .displayIntent(ToolDisplayIntent.READ)
                        .build(),
                (ToolHandler<NoteArguments>) context -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("apiKey", "sk-0123456789abcdefghij");
                    data.put("header", "Authorization: Bearer 0123456789abcdefghij");
                    data.put("note", context.arguments().note());
                    return ToolResult.of(data, 1, "Read Authorization: Bearer 0123456789abcdefghij");
                });
    }

    /**
     * Declaration of the echo tool, also used to build isolated registries.
     *
     * @return echo tool declaration
     */
    static ToolDefinition echoDefinition() {
        return ToolDefinition.builder(ECHO, EchoArguments.class)
                .description("Repeats a message, used to exercise the happy path")
                .requiredPermission(ToolPermission.CODE_READ)
                .maxResultItems(10)
                .displayIntent(ToolDisplayIntent.SEARCH)
                .build();
    }
}
