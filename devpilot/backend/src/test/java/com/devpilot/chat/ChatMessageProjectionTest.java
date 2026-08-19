package com.devpilot.chat;

import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.model.MessageResponse;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.chat.service.ChatMessageProjection;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.projection.MessageRole;
import com.devpilot.runtime.projection.MessageView;
import com.devpilot.runtime.projection.SessionProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: {@code chat_message} is a read projection of the event log, not a second source of
 * truth, and it can be rebuilt from the log at any time.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatMessageProjectionTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageProjection chatMessageProjection;

    @Autowired
    private SessionLifecycleService lifecycleService;

    @Autowired
    private ProjectService projectService;

    private String sessionId;

    @BeforeEach
    void openSession() {
        long projectId = projectService.create(new CreateProjectRequest(
                        "chat-test", "chat-" + UUID.randomUUID().toString().substring(0, 8),
                        null, "/srv/repos/chat-test", null))
                .id();
        SessionResponse session = chatSessionService.create(projectId, new CreateSessionRequest("故障排查"));
        sessionId = session.sessionId();
    }

    @Test
    void projectsUserAndAssistantMessagesAsTheyAreRecorded() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "订单服务为什么 500？");
        lifecycleService.recordAssistantMessage(
                sessionId, turnId, null, "supervisor", "优惠券服务返回 null，导致空指针。");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        List<MessageResponse> messages = chatSessionService.messages(sessionId, 0, 50).items();

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().role()).isEqualTo(MessageRole.USER.name());
        assertThat(messages.getFirst().content()).isEqualTo("订单服务为什么 500？");
        assertThat(messages.getLast().role()).isEqualTo(MessageRole.ASSISTANT.name());
        assertThat(messages.getLast().content()).isEqualTo("优惠券服务返回 null，导致空指针。");
        assertThat(messages.getFirst().seq()).isLessThan(messages.getLast().seq());
    }

    @Test
    void agreesWithTheProjectionRebuiltFromTheEventLog() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "第一个问题");
        lifecycleService.recordAssistantMessage(sessionId, turnId, null, "supervisor", "第一个回答");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        SessionProjection fromEvents = lifecycleService.project(sessionId);
        List<MessageResponse> fromTable = chatSessionService.messages(sessionId, 0, 50).items();

        assertThat(fromTable)
                .extracting(MessageResponse::seq, MessageResponse::content)
                .containsExactlyElementsOf(fromEvents.messages().stream()
                        .map(message -> org.assertj.core.groups.Tuple.tuple(
                                message.seq(), message.content()))
                        .toList());
    }

    @Test
    void rebuildRestoresTheSameTimeline() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "问题");
        lifecycleService.recordAssistantMessage(sessionId, turnId, null, "supervisor", "回答");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");
        List<MessageResponse> before = chatSessionService.messages(sessionId, 0, 50).items();

        int projected = chatMessageProjection.rebuild(sessionId);

        assertThat(projected).isEqualTo(before.size());
        assertThat(chatSessionService.messages(sessionId, 0, 50).items())
                .extracting(MessageResponse::seq, MessageResponse::role, MessageResponse::content)
                .containsExactlyElementsOf(before.stream()
                        .map(message -> org.assertj.core.groups.Tuple.tuple(
                                message.seq(), message.role(), message.content()))
                        .toList());
    }

    @Test
    void rebuildIsIdempotent() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "问题");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        chatMessageProjection.rebuild(sessionId);
        chatMessageProjection.rebuild(sessionId);

        assertThat(chatSessionService.messages(sessionId, 0, 50).total()).isEqualTo(1);
    }

    @Test
    void doesNotProjectDeltasOrLifecycleEvents() {
        String turnId = lifecycleService.startTurn(sessionId, "USER", "问题");
        lifecycleService.recordAssistantDelta(sessionId, turnId, null, "部分");
        lifecycleService.recordAssistantDelta(sessionId, turnId, null, "回答");
        lifecycleService.recordAssistantMessage(sessionId, turnId, null, "supervisor", "部分回答");
        lifecycleService.endTurn(sessionId, turnId, TurnEndReason.COMPLETED, "answered");

        assertThat(chatSessionService.messages(sessionId, 0, 50).total()).isEqualTo(2);
        assertThat(lifecycleService.project(sessionId).messages())
                .extracting(MessageView::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
    }
}
