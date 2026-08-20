package com.devpilot.chat;

import com.devpilot.agent.runtime.ScriptedModelGateway;
import com.devpilot.agent.tool.logs.LogTools;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.log.model.ImportLogsRequest;
import com.devpilot.log.model.LogEntryRequest;
import com.devpilot.log.service.LogService;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract: the chat stream is a projection of the committed event log — every frame carries the
 * sequence number of a durable event, a reconnect with {@code Last-Event-ID} returns exactly what
 * was missed, and a session may only be streamed by the project that owns it.
 */
@SpringBootTest(properties = {
        // The supervisor would need a second scripted agent; routing itself is covered by
        // SupervisorRoutingTest, so this test drives the streaming contract through one specialist.
        "app.runtime.chat.agent=log_agent"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatStreamTest {

    private static final LocalDateTime INCIDENT = LocalDateTime.of(2026, 8, 16, 10, 31, 2);
    private static final long STREAM_TIMEOUT_MS = 30_000;

    /** Replaces the model provider with a script the test controls. */
    @TestConfiguration
    static class ScriptedModelConfiguration {

        /** @return scripted gateway shared by the test */
        @Bean
        @Primary
        ScriptedModelGateway scriptedModelGateway() {
            return new ScriptedModelGateway();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScriptedModelGateway model;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private LogService logService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SessionLifecycleService lifecycleService;

    private long projectId;

    @BeforeEach
    void seedProject() {
        model.reset();
        projectId = projectService.create(new CreateProjectRequest(
                        "chat-stream", "chat-" + UUID.randomUUID().toString().substring(0, 8),
                        null, "/srv/repos/chat", null))
                .id();
        logService.importLogs(projectId, new ImportLogsRequest(List.of(
                new LogEntryRequest("order-service", "ERROR", "t-1001", "com.demo.order.OrderService",
                        "coupon is null", "java.lang.NullPointerException", null, INCIDENT))));
    }

    @Test
    void streamsTheEventsOfATurnWithTheirSequenceNumbers() throws Exception {
        model.enqueueToolCall(LogTools.SEARCH_LOGS, Map.of("level", "ERROR"));
        model.enqueueAnswer("最近有 1 条 NullPointerException。");

        List<Frame> frames = stream(null, "最近有哪些 ERROR？", null);

        assertThat(types(frames)).containsSubsequence(
                "turn_started", "user_message", "agent_started",
                "tool_call_requested", "tool_call_finished",
                "assistant_message", "agent_finished", "turn_ended");
        // The id is the event sequence number, which is what makes Last-Event-ID exact.
        assertThat(frames).allSatisfy(frame -> assertThat(frame.id()).isNotBlank());
        assertThat(seqs(frames)).isSorted();
        assertThat(seqs(frames)).doesNotHaveDuplicates();
        assertThat(frames.getFirst().payload().get("sessionId").asText()).startsWith("session_");
    }

    @Test
    void closesTheTurnItOpened() throws Exception {
        model.enqueueAnswer("没有需要排查的问题。");

        List<Frame> frames = stream(null, "一切正常吗？", null);
        String sessionId = frames.getFirst().payload().get("sessionId").asText();

        assertThat(lifecycleService.project(sessionId).activeTurn()).isEmpty();
        assertThat(lifecycleService.project(sessionId).turns()).singleElement()
                .satisfies(turn -> assertThat(turn.status()).isEqualTo(TurnStatus.COMPLETED));
    }

    @Test
    void reconnectingWithLastEventIdReplaysOnlyWhatWasMissed() throws Exception {
        model.enqueueAnswer("第一次回答。");
        List<Frame> first = stream(null, "第一个问题", null);
        String sessionId = first.getFirst().payload().get("sessionId").asText();
        long half = seqs(first).get(first.size() / 2);

        // Attaching with no message must not start a second turn, only catch the client up.
        List<Frame> replayed = stream(sessionId, null, half);

        assertThat(seqs(replayed)).allMatch(seq -> seq > half);
        assertThat(seqs(replayed)).containsExactlyElementsOf(
                seqs(first).stream().filter(seq -> seq > half).toList());
        assertThat(lifecycleService.project(sessionId).turns()).hasSize(1);
    }

    @Test
    void attachingWithoutLastEventIdReplaysTheWholeSession() throws Exception {
        model.enqueueAnswer("回答。");
        List<Frame> first = stream(null, "问题", null);
        String sessionId = first.getFirst().payload().get("sessionId").asText();

        List<Frame> replayed = stream(sessionId, null, null);

        // session_created precedes the turn, so a full attach sees strictly more than the turn did.
        assertThat(types(replayed)).startsWith("session_created");
        assertThat(seqs(replayed)).containsAll(seqs(first));
        assertThat(seqs(replayed)).doesNotHaveDuplicates();
    }

    @Test
    void refusesASessionThatBelongsToAnotherProject() throws Exception {
        SessionResponse session = chatSessionService.create(projectId, null);
        long otherProject = projectService.create(new CreateProjectRequest(
                        "other", "other-" + UUID.randomUUID().toString().substring(0, 8),
                        null, "/srv/repos/other", null))
                .id();

        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", otherProject,
                                "sessionId", session.sessionId(),
                                "message", "偷看别的项目"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesAnUnknownSession() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "sessionId", "session_does_not_exist",
                                "message", "你好"))))
                .andExpect(status().isNotFound());
    }

    /**
     * Opens one stream and reads it to completion.
     *
     * <p>A streaming response writes into the original response as it goes, so the frames are read
     * from there once the emitter completes rather than from a second dispatch.
     *
     * @param sessionId session to continue, null to open a new one
     * @param message question to ask, null to attach without starting a turn
     * @param lastEventId value of the {@code Last-Event-ID} header, null to omit it
     * @return frames the server wrote, in order
     */
    private List<Frame> stream(String sessionId, String message, Long lastEventId) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("projectId", projectId);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }
        if (message != null) {
            body.put("message", message);
        }

        var request = post("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (lastEventId != null) {
            request = request.header("Last-Event-ID", lastEventId);
        }

        MvcResult started = mockMvc.perform(request)
                .andExpect(request().asyncStarted())
                .andReturn();
        // Blocks until the emitter completes, which happens when the followed turn ends.
        started.getAsyncResult(STREAM_TIMEOUT_MS);

        assertThat(started.getResponse().getStatus()).isEqualTo(200);
        assertThat(started.getResponse().getContentType()).startsWith("text/event-stream");
        return parse(started.getResponse().getContentAsString());
    }

    /**
     * Parses an SSE body into frames.
     *
     * @param body raw stream text
     * @return frames in order, comments ignored
     */
    private List<Frame> parse(String body) throws Exception {
        List<Frame> frames = new ArrayList<>();
        String id = null;
        String event = null;
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                if (event != null && !data.isEmpty()) {
                    frames.add(new Frame(id, event, objectMapper.readTree(data.toString())));
                }
                id = null;
                event = null;
                data.setLength(0);
            } else if (trimmed.startsWith("id:")) {
                id = trimmed.substring(3).strip();
            } else if (trimmed.startsWith("event:")) {
                event = trimmed.substring(6).strip();
            } else if (trimmed.startsWith("data:")) {
                data.append(trimmed.substring(5).strip());
            }
            // Lines starting with ":" are keep-alive comments and carry no event.
        }
        if (event != null && !data.isEmpty()) {
            frames.add(new Frame(id, event, objectMapper.readTree(data.toString())));
        }
        return frames;
    }

    private static List<String> types(List<Frame> frames) {
        return frames.stream().map(Frame::event).toList();
    }

    private static List<Long> seqs(List<Frame> frames) {
        return frames.stream().map(frame -> frame.payload().get("seq").asLong()).toList();
    }

    /**
     * One parsed SSE frame.
     *
     * @param id value of the SSE id field
     * @param event value of the SSE event field
     * @param payload decoded envelope
     */
    private record Frame(String id, String event, JsonNode payload) {
    }
}
