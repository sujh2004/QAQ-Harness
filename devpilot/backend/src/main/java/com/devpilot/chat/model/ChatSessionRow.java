package com.devpilot.chat.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Database row of {@code chat_session}. */
@TableName("chat_session")
public class ChatSessionRow {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private Long projectId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** @return session identifier, shared with the runtime event stream */
    public String getId() {
        return id;
    }

    /** @param id session identifier */
    public void setId(String id) {
        this.id = id;
    }

    /** @return owning project */
    public Long getProjectId() {
        return projectId;
    }

    /** @param projectId owning project */
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    /** @return human-readable title */
    public String getTitle() {
        return title;
    }

    /** @param title human-readable title */
    public void setTitle(String title) {
        this.title = title;
    }

    /** @return creation time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt creation time */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** @return last update time */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @param updatedAt last update time */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
