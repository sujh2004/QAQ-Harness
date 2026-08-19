package com.devpilot.chat.service;

import com.devpilot.chat.mapper.ChatMessageMapper;
import com.devpilot.chat.model.ChatMessageRow;
import com.devpilot.runtime.projection.MessageRole;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventListener;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.payload.AssistantMessagePayload;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Keeps {@code chat_message} in step with the message events of a session.
 *
 * <p>The table exists only so the chat timeline can be paged efficiently. It is never written by
 * business code: every row comes from a {@code user_message} or {@code assistant_message} event and
 * carries the sequence number it was derived from, so {@link #rebuild(String)} can always
 * reconstruct it from the log.
 */
@Component
public class ChatMessageProjection implements SessionEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageProjection.class);

    private final ChatMessageMapper messageMapper;
    private final SessionEventStore eventStore;

    /**
     * Creates the projection.
     *
     * @param messageMapper projection table access
     * @param eventStore event log, read when the projection is rebuilt
     */
    public ChatMessageProjection(ChatMessageMapper messageMapper, SessionEventStore eventStore) {
        this.messageMapper = messageMapper;
        this.eventStore = eventStore;
    }

    @Override
    @Transactional
    public void onEventsCommitted(String sessionId, List<SessionEvent> events) {
        apply(sessionId, events);
    }

    /**
     * Rebuilds the projection of one session from its event log.
     *
     * @param sessionId owning session
     * @return number of projected messages
     */
    @Transactional
    public int rebuild(String sessionId) {
        int removed = messageMapper.deleteBySession(sessionId);
        int projected = apply(sessionId, eventStore.readAll(sessionId));
        LOGGER.info("Rebuilt chat_message for session {}: replaced {} row(s) with {}",
                sessionId, removed, projected);
        return projected;
    }

    private int apply(String sessionId, List<SessionEvent> events) {
        int projected = 0;
        for (SessionEvent event : events) {
            switch (event.eventType()) {
                case USER_MESSAGE -> projected += insert(sessionId, event, MessageRole.USER,
                        event.payloadAs(UserMessagePayload.class).content());
                case ASSISTANT_MESSAGE -> projected += insert(sessionId, event, MessageRole.ASSISTANT,
                        event.payloadAs(AssistantMessagePayload.class).content());
                default -> {
                    // Everything else stays in the event log only; the timeline projects messages.
                }
            }
        }
        return projected;
    }

    private int insert(String sessionId, SessionEvent event, MessageRole role, String content) {
        ChatMessageRow row = new ChatMessageRow();
        row.setSessionId(sessionId);
        row.setSourceSeq(event.seq());
        row.setRole(role.name());
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
        try {
            messageMapper.insert(row);
            return 1;
        } catch (DuplicateKeyException alreadyProjected) {
            // The unique key on (session_id, source_seq) makes re-delivery and rebuild idempotent.
            return 0;
        }
    }
}
