package com.devpilot.chat.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Database row of {@code chat_message}.
 *
 * <p>Rows are a projection of the {@code user_message} and {@code assistant_message} events.
 * {@code sourceSeq} names the event a row came from, which is what makes the projection idempotent
 * and rebuildable.
 */
@TableName("chat_message")
public class ChatMessageRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long sourceSeq;
    private String role;
    private String content;
    private LocalDateTime createdAt;

    /** @return row identity */
    public Long getId() {
        return id;
    }

    /** @param id row identity */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return owning session */
    public String getSessionId() {
        return sessionId;
    }

    /** @param sessionId owning session */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** @return sequence number of the source event */
    public Long getSourceSeq() {
        return sourceSeq;
    }

    /** @param sourceSeq sequence number of the source event */
    public void setSourceSeq(Long sourceSeq) {
        this.sourceSeq = sourceSeq;
    }

    /** @return who produced the message */
    public String getRole() {
        return role;
    }

    /** @param role who produced the message */
    public void setRole(String role) {
        this.role = role;
    }

    /** @return message text */
    public String getContent() {
        return content;
    }

    /** @param content message text */
    public void setContent(String content) {
        this.content = content;
    }

    /** @return when the source event happened */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt when the source event happened */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
