package com.devpilot.agent.tool;

import com.devpilot.agent.tool.code.CodeSearchTools;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.code.model.CodeMatch;
import com.devpilot.code.model.ReadCodeFileResult;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.ToolCallStatus;
import com.devpilot.runtime.lifecycle.ToolErrorCode;
import com.devpilot.runtime.projection.ToolCallView;
import com.devpilot.runtime.tool.ToolExecutionResult;
import com.devpilot.runtime.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * Contract: the code tools reach the real demo repository through the execution pipeline, respect
 * the repository boundary and leave a complete audit trail.
 */
@SpringBootTest
@ActiveProfiles("test")
class CodeToolTest {

    private static final String DEMO_REPOSITORY = "../demo-project/order-demo";
    private static final String ORDER_SERVICE = "src/main/java/com/demo/order/OrderService.java";

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    private long projectId;
    private String sessionId;
    private String turnId;

    @BeforeEach
    void openSession() {
        projectId = AgentToolFixtures.newProject(projectService, DEMO_REPOSITORY);
        String[] ids = AgentToolFixtures.newSessionTurn(chatSessionService, lifecycleService, projectId);
        sessionId = ids[0];
        turnId = ids[1];
    }

    @Test
    void searchCodeFindsTheRealDemoFileAndLineNumber() {
        ToolExecutionResult result = invoke(CodeSearchTools.SEARCH_CODE,
                Map.of("projectId", projectId, "keyword", "createOrder", "filePattern", "*.java"));

        assertThat(result.successful()).isTrue();
        assertThat(result.data()).asInstanceOf(LIST).isNotEmpty();

        @SuppressWarnings("unchecked")
        List<CodeMatch> matches = (List<CodeMatch>) result.data();
        assertThat(matches).anySatisfy(match -> {
            assertThat(match.filePath()).isEqualTo(ORDER_SERVICE);
            assertThat(match.lineNumber()).isPositive();
            assertThat(match.lineText()).contains("createOrder");
        });
        assertThat(result.modelSummary()).contains("createOrder");
    }

    @Test
    void readCodeFileReturnsTheKnownDefectAtLine86() {
        ToolExecutionResult result = invoke(CodeSearchTools.READ_CODE_FILE,
                Map.of("projectId", projectId, "relativePath", ORDER_SERVICE,
                        "startLine", 84, "endLine", 88));

        assertThat(result.successful()).isTrue();
        ReadCodeFileResult file = (ReadCodeFileResult) result.data();

        assertThat(file.startLine()).isEqualTo(84);
        // The demo incident log points at OrderService.java:86, so line 86 must be the unguarded call.
        assertThat(file.lines().get(86 - 84)).contains("coupon.getDiscountAmount()");
    }

    @Test
    void listFilesShowsTheDemoSourcesButNotTheFakeSecrets() {
        ToolExecutionResult result = invoke(CodeSearchTools.LIST_FILES,
                Map.of("projectId", projectId, "maxDepth", 10, "limit", 200));

        assertThat(result.successful()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) result.data();
        assertThat(files).contains(ORDER_SERVICE);
        assertThat(files).noneSatisfy(path -> assertThat(path).endsWith(".env"));
        assertThat(files).noneSatisfy(path -> assertThat(path).endsWith("application-prod.yml"));
    }

    @Test
    void refusesToReadOutsideTheRepositoryAndStillRecordsTheAttempt() {
        ToolExecutionResult result = invoke(CodeSearchTools.READ_CODE_FILE,
                Map.of("projectId", projectId, "relativePath", "../../../../etc/passwd",
                        "startLine", 1, "endLine", 10));

        // A refused escape is a denial, not a malfunction — the status has to say which, because it
        // is what tells the model whether retrying differently could ever work.
        assertThat(result.status()).isEqualTo(ToolCallStatus.DENIED);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);

        ToolCallView audit = AgentToolFixtures.auditOf(lifecycleService, sessionId, result.callId());
        assertThat(audit.status().terminal()).isTrue();
        assertThat(audit.toolName()).isEqualTo(CodeSearchTools.READ_CODE_FILE);
    }

    @Test
    void reportsAPathThatDoesNotExistAsABadArgument() {
        // Models guess module names. Recording the guess as a provider error would claim the
        // platform broke, and would read that way in the audit trail forever.
        ToolExecutionResult result = invoke(CodeSearchTools.LIST_FILES,
                Map.of("projectId", projectId, "relativePath", "order-service/src/main/resources"));

        assertThat(result.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.INVALID_ARGUMENT);
        assertThat(result.message()).contains("does not exist");
    }

    @Test
    void refusesTheSensitiveFileBlacklist() {
        ToolExecutionResult result = invoke(CodeSearchTools.READ_CODE_FILE,
                Map.of("projectId", projectId,
                        "relativePath", "src/main/resources/application-prod.yml",
                        "startLine", 1, "endLine", 20));

        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
        assertThat(result.message()).contains("blacklist");
    }

    @Test
    void refusesArgumentsThatNameAnotherProject() {
        long otherProject = AgentToolFixtures.newProject(projectService, DEMO_REPOSITORY);

        ToolExecutionResult result = invoke(CodeSearchTools.SEARCH_CODE,
                Map.of("projectId", otherProject, "keyword", "createOrder"));

        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.PERMISSION_DENIED);
        assertThat(result.message()).contains(String.valueOf(projectId));
    }

    @Test
    void rejectsArgumentsOutsideTheDeclaredBounds() {
        ToolExecutionResult result = invoke(CodeSearchTools.SEARCH_CODE,
                Map.of("projectId", projectId, "keyword", "createOrder", "limit", 5_000));

        assertThat(result.status()).isEqualTo(ToolCallStatus.INVALID_ARGUMENT);
        assertThat(result.message()).contains("limit");
    }

    @Test
    void everyCallLeavesAMatchingRequestedAndFinishedPair() {
        invoke(CodeSearchTools.SEARCH_CODE, Map.of("projectId", projectId, "keyword", "createOrder"));
        invoke(CodeSearchTools.READ_CODE_FILE, Map.of("projectId", projectId,
                "relativePath", "../secrets.txt", "startLine", 1, "endLine", 5));
        invoke(CodeSearchTools.LIST_FILES, Map.of("projectId", projectId));

        assertThat(lifecycleService.project(sessionId).openToolCalls(turnId)).isEmpty();
        assertThat(lifecycleService.project(sessionId).toolCalls()).hasSize(3);
    }

    private ToolExecutionResult invoke(String toolName, Map<String, Object> arguments) {
        return AgentToolFixtures.invoke(
                toolRegistry, sessionId, turnId, projectId, toolName, arguments);
    }
}
