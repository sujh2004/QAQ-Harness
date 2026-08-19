package com.devpilot.agent.tool.test;

import com.devpilot.agent.tool.AgentToolSupport;
import com.devpilot.runtime.tool.ConcurrencyMode;
import com.devpilot.runtime.tool.SideEffectLevel;
import com.devpilot.runtime.tool.ToolDefinition;
import com.devpilot.runtime.tool.ToolDisplayIntent;
import com.devpilot.runtime.tool.ToolExecutionContext;
import com.devpilot.runtime.tool.ToolHandler;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolRegistry;
import com.devpilot.runtime.tool.ToolResult;
import com.devpilot.testcase.model.SaveTestCasesArguments;
import com.devpilot.testcase.model.TestCaseResponse;
import com.devpilot.testcase.service.TestCaseService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes test case authoring as a model-visible tool.
 *
 * <p>This is the only tool in the MVP that writes. It is declared {@link SideEffectLevel#MUTATING},
 * so the policy refuses it unless the deployment lists it in
 * {@code app.runtime.tool.mutating-allow-list} and the calling agent's profile allows mutation. Two
 * gates, both outside the model's reach.
 */
@Component
public class TestTools {

    /** Name of the test case writer. */
    public static final String SAVE_TEST_CASES = "saveTestCases";

    private static final String VERSION = "1";

    private final ToolRegistry toolRegistry;
    private final TestCaseService testCaseService;

    /**
     * Creates the contributor.
     *
     * @param toolRegistry registry the tool is published to
     * @param testCaseService test case capability
     */
    public TestTools(ToolRegistry toolRegistry, TestCaseService testCaseService) {
        this.toolRegistry = toolRegistry;
        this.testCaseService = testCaseService;
    }

    /** Publishes the test tool once the registry is available. */
    @PostConstruct
    public void register() {
        toolRegistry.register(definition(), (ToolHandler<SaveTestCasesArguments>) this::saveTestCases);
    }

    private ToolResult saveTestCases(ToolExecutionContext<SaveTestCasesArguments> context) {
        SaveTestCasesArguments arguments = context.arguments();
        AgentToolSupport.requireSameProject(context, arguments.projectId());

        List<TestCaseResponse> saved = testCaseService.save(new SaveTestCasesArguments(
                arguments.projectId(),
                // The session is decided by the runtime, not by whatever the model claims.
                context.sessionId(),
                arguments.cases()));

        StringBuilder summary = new StringBuilder("Saved ").append(saved.size()).append(" test case(s):");
        for (TestCaseResponse testCase : saved) {
            summary.append("\n- #").append(testCase.id())
                    .append(' ').append(testCase.priority() == null ? "P?" : testCase.priority())
                    .append("  ").append(testCase.title());
        }
        return ToolResult.of(saved, saved.size(), summary.toString());
    }

    private static ToolDefinition definition() {
        Map<String, Object> testCase = new LinkedHashMap<>();
        testCase.put("type", "object");
        testCase.put("properties", Map.of(
                "title", AgentToolSupport.field("string", "What the case verifies"),
                "priority", AgentToolSupport.field("string", "One of P0, P1, P2, P3"),
                "precondition", AgentToolSupport.field("string", "State the case assumes"),
                "steps", Map.of(
                        "type", "array",
                        "description", "Ordered steps",
                        "items", Map.of("type", "string")),
                "expectedResult", AgentToolSupport.field("string", "What should happen")));
        testCase.put("required", List.of("title", "steps", "expectedResult"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("projectId", AgentToolSupport.field("integer", "Project the cases belong to"));
        properties.put("cases", Map.of(
                "type", "array",
                "description", "At most 20 cases per call",
                "items", Map.copyOf(testCase)));

        return ToolDefinition.builder(SAVE_TEST_CASES, SaveTestCasesArguments.class)
                .version(VERSION)
                .description("Persist the structured test cases you designed so the team can review "
                        + "and run them. Only call this once the cases are final.")
                .inputSchema(AgentToolSupport.objectSchema(properties, List.of("projectId", "cases")))
                .sideEffect(SideEffectLevel.MUTATING)
                .concurrency(ConcurrencyMode.EXCLUSIVE)
                .timeout(Duration.ofSeconds(15))
                .maxResultItems(20)
                .maxResultBytes(32_768)
                .requiredPermission(ToolPermission.TEST_CASE_WRITE)
                .displayIntent(ToolDisplayIntent.GENERIC)
                .build();
    }
}
