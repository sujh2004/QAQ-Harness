package com.devpilot.chat;

import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnStatus;
import com.devpilot.runtime.projection.SessionProjection;
import com.devpilot.runtime.stream.SessionEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract: creating a session opens a replayable event stream, and the session read APIs are
 * projections of that stream.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private ProjectService projectService;

    private long projectId;

    @BeforeEach
    void createProject() {
        projectId = projectService.create(new CreateProjectRequest(
                        "session-test", "sess-" + UUID.randomUUID().toString().substring(0, 8),
                        null, "/srv/repos/session-test", null))
                .id();
    }

    @Test
    void createsASessionWithAPinnedRuntimeConfiguration() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/sessions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(org.hamcrest.Matchers.startsWith("session_")))
                .andExpect(jsonPath("$.data.title").value("新对话"))
                .andExpect(jsonPath("$.data.projectId").value(projectId));

        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest("排查 500"));
        SessionProjection projection = lifecycleService.project(session.sessionId());

        assertThat(projection.title()).isEqualTo("排查 500");
        assertThat(projection.projectId()).isEqualTo(projectId);
        assertThat(projection.profileVersion()).isEqualTo("standard@1");
        assertThat(projection.lastSeq()).isEqualTo(1L);
    }

    @Test
    void listsTheSessionsOfAProject() throws Exception {
        chatSessionService.create(projectId, new CreateSessionRequest("第一个会话"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/sessions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("第一个会话"));
    }

    @Test
    void replaysEventsAfterAGivenSequenceNumber() {
        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest(null));
        String turnId = lifecycleService.startTurn(session.sessionId(), "USER", "问题");

        List<SessionEventEnvelope> all = chatSessionService.events(session.sessionId(), 0, 500);
        List<SessionEventEnvelope> tail = chatSessionService.events(session.sessionId(), 1, 500);

        assertThat(all).hasSize(3);
        assertThat(all.getFirst().eventType()).isEqualTo("session_created");
        assertThat(tail).hasSize(2);
        assertThat(tail).allSatisfy(envelope -> {
            assertThat(envelope.seq()).isGreaterThan(1L);
            assertThat(envelope.turnId()).isEqualTo(turnId);
        });
    }

    @Test
    void cancellingATurnIsIdempotentOverHttp() throws Exception {
        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest(null));
        String turnId = lifecycleService.startTurn(session.sessionId(), "USER", "问题");

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/turns/{turnId}/cancel",
                        session.sessionId(), turnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(TurnStatus.CANCELLED.name()))
                .andExpect(jsonPath("$.data.endReason").value("ABORTED_BY_USER"));

        long seqAfterCancel = lifecycleService.project(session.sessionId()).lastSeq();

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/turns/{turnId}/cancel",
                        session.sessionId(), turnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(TurnStatus.CANCELLED.name()));

        assertThat(lifecycleService.project(session.sessionId()).lastSeq()).isEqualTo(seqAfterCancel);
    }

    @Test
    void reportsAnUnknownSessionAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{sessionId}/messages", "session_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40402));
    }

    @Test
    void reportsAnEmptyAgentRunTreeBeforeAnyAgentExists() throws Exception {
        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest(null));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/runs", session.sessionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
