package com.devpilot.chat.model;

import java.time.LocalDateTime;

/**
 * One message of the chat timeline.
 *
 * @param sessionId owning session
 * @param seq sequence number of the event this message was projected from
 * @param role who produced the message
 * @param content message text
 * @param createdAt when the source event happened
 */
public record MessageResponse(
        String sessionId, long seq, String role, String content, LocalDateTime createdAt) {

    /**
     * Converts a projection row.
     *
     * @param row projected message
     * @return API representation
     */
    public static MessageResponse from(ChatMessageRow row) {
        return new MessageResponse(
                row.getSessionId(), row.getSourceSeq(), row.getRole(), row.getContent(), row.getCreatedAt());
    }
}
