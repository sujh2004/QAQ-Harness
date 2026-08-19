package com.devpilot.runtime.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: an inner scope may only take capabilities away. A profile can hide a tool the project
 * allows, but it can never re-enable one the project withheld.
 */
class ToolScopeTest {

    private static final ToolScope PROJECT = ToolScope.readOnly(
            Set.of("searchCode", "readCodeFile", "searchLogs"),
            Set.of(ToolPermission.CODE_READ, ToolPermission.LOG_READ));

    @Test
    void narrowingKeepsOnlyWhatBothLevelsGrant() {
        ToolScope agent = ToolScope.readOnly(
                Set.of("searchCode", "saveTestCases"),
                Set.of(ToolPermission.CODE_READ, ToolPermission.TEST_CASE_WRITE));

        ToolScope effective = PROJECT.narrow(agent);

        assertThat(effective.visibleTools()).containsExactly("searchCode");
        assertThat(effective.grantedPermissions()).containsExactly(ToolPermission.CODE_READ);
        assertThat(effective.canSee("saveTestCases")).isFalse();
        assertThat(effective.holds(ToolPermission.TEST_CASE_WRITE)).isFalse();
    }

    @Test
    void narrowingCannotReEnableMutatingTools() {
        ToolScope agent = new ToolScope(Set.of("searchCode"), Set.of(ToolPermission.CODE_READ), true);

        assertThat(PROJECT.narrow(agent).allowMutating()).isFalse();
    }

    @Test
    void acceptsAScopeThatOnlyRemovesCapabilities() {
        ToolScope agent = ToolScope.readOnly(Set.of("searchCode"), Set.of(ToolPermission.CODE_READ));

        agent.requireNarrowerThan(PROJECT);
    }

    @Test
    void rejectsAScopeThatAddsATool() {
        ToolScope agent = ToolScope.readOnly(
                Set.of("searchCode", "saveTestCases"), Set.of(ToolPermission.CODE_READ));

        assertThatThrownBy(() -> agent.requireNarrowerThan(PROJECT))
                .isInstanceOf(ToolScopeViolationException.class)
                .hasMessageContaining("saveTestCases");
    }

    @Test
    void rejectsAScopeThatAddsAPermission() {
        ToolScope agent = ToolScope.readOnly(
                Set.of("searchCode"), Set.of(ToolPermission.CODE_READ, ToolPermission.KNOWLEDGE_INDEX_WRITE));

        assertThatThrownBy(() -> agent.requireNarrowerThan(PROJECT))
                .isInstanceOf(ToolScopeViolationException.class)
                .hasMessageContaining("KNOWLEDGE_INDEX_WRITE");
    }

    @Test
    void rejectsAScopeThatTurnsMutationBackOn() {
        ToolScope agent = new ToolScope(Set.of("searchCode"), Set.of(ToolPermission.CODE_READ), true);

        assertThatThrownBy(() -> agent.requireNarrowerThan(PROJECT))
                .isInstanceOf(ToolScopeViolationException.class)
                .hasMessageContaining("mutating");
    }
}
