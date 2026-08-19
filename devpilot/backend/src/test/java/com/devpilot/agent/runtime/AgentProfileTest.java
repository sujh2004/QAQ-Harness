package com.devpilot.agent.runtime;

import com.devpilot.agent.config.AgentDefinition;
import com.devpilot.agent.tool.code.CodeSearchTools;
import com.devpilot.agent.tool.logs.LogTools;
import com.devpilot.agent.tool.test.TestTools;
import com.devpilot.runtime.tool.ToolPermission;
import com.devpilot.runtime.tool.ToolScope;
import com.devpilot.runtime.tool.ToolScopeViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: each specialist agent sees only the tools its profile grants, and only the test agent
 * may reach the one tool that writes.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentProfileTest {

    @Autowired
    private AgentRegistry agentRegistry;

    @Test
    void codeAgentSeesCodeToolsOnly() {
        ToolScope scope = agentRegistry.scopeOf(agentRegistry.require("code_agent"));

        assertThat(scope.visibleTools()).containsExactlyInAnyOrder(
                CodeSearchTools.LIST_FILES, CodeSearchTools.SEARCH_CODE, CodeSearchTools.READ_CODE_FILE);
        assertThat(scope.grantedPermissions()).containsExactly(ToolPermission.CODE_READ);
        assertThat(scope.allowMutating()).isFalse();
    }

    @Test
    void logAgentSeesLogToolsOnly() {
        ToolScope scope = agentRegistry.scopeOf(agentRegistry.require("log_agent"));

        assertThat(scope.visibleTools()).containsExactlyInAnyOrder(
                LogTools.SEARCH_LOGS, LogTools.GET_LOG_BY_TRACE_ID, LogTools.GET_RECENT_ERROR_SUMMARY);
        assertThat(scope.canSee(CodeSearchTools.SEARCH_CODE)).isFalse();
        assertThat(scope.canSee(TestTools.SAVE_TEST_CASES)).isFalse();
    }

    @Test
    void onlyTheTestAgentCanEvenSeeTheWriteTool() {
        assertThat(agentRegistry.scopeOf(agentRegistry.require("test_agent"))
                .canSee(TestTools.SAVE_TEST_CASES)).isTrue();

        for (String readOnlyAgent : new String[] {"debug_agent", "code_agent", "log_agent"}) {
            assertThat(agentRegistry.scopeOf(agentRegistry.require(readOnlyAgent))
                    .canSee(TestTools.SAVE_TEST_CASES))
                    .as("%s must not see the write tool", readOnlyAgent)
                    .isFalse();
            assertThat(agentRegistry.scopeOf(agentRegistry.require(readOnlyAgent)).allowMutating())
                    .as("%s must not be allowed to mutate", readOnlyAgent)
                    .isFalse();
        }
    }

    @Test
    void everyAgentHasAPersonaAndAdvertisableTools() {
        for (String agentName : new String[] {"debug_agent", "code_agent", "log_agent", "test_agent"}) {
            AgentDefinition agent = agentRegistry.require(agentName);
            assertThat(agentRegistry.systemPrompt(agent)).isNotBlank();
            assertThat(agent.maxSteps()).isPositive();
            assertThat(agentRegistry.toolSpecs(agentRegistry.scopeOf(agent)))
                    .as("%s advertises tools with schemas", agentName)
                    .isNotEmpty()
                    .allSatisfy(spec -> {
                        assertThat(spec.name()).isNotBlank();
                        assertThat(spec.description()).isNotBlank();
                        assertThat(spec.inputSchema()).containsKey("properties");
                    });
        }
    }

    @Test
    void aProfileThatAsksForAnUnknownToolIsRejected() {
        ToolScope application = agentRegistry.scopeOf(agentRegistry.require("debug_agent"));
        ToolScope widened = new ToolScope(
                Set.of("dropDatabase"), Set.of(ToolPermission.CODE_READ), false);

        assertThatThrownBy(() -> widened.requireNarrowerThan(application))
                .isInstanceOf(ToolScopeViolationException.class)
                .hasMessageContaining("dropDatabase");
    }
}
