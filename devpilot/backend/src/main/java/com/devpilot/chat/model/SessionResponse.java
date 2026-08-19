package com.devpilot.chat.model;

import java.time.LocalDateTime;

/**
 * Session as returned by the API.
 *
 * @param sessionId session identifier, shared with the runtime event stream
 * @param projectId owning project
 * @param title human-readable title
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record SessionResponse(
        String sessionId, Long projectId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {

    /**
     * Converts a database row.
     *
     * @param row stored session
     * @return API representation
     */
    public static SessionResponse from(ChatSessionRow row) {
        return new SessionResponse(
                row.getId(), row.getProjectId(), row.getTitle(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
