package com.devpilot.runtime.session.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Database row of {@code session_stream}: the sequence allocator and pinned runtime metadata of one
 * session.
 *
 * <p>{@code nextSeq} is reserved under a row lock, which is what keeps concurrent appends from
 * sharing a sequence number.
 */
@TableName("session_stream")
public class SessionStreamRow {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    private Long nextSeq;
    private String status;
    private String profileVersion;
    private String capabilitySnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** @return session identifier */
    public String getSessionId() {
        return sessionId;
    }

    /** @param sessionId session identifier */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** @return next unassigned sequence number */
    public Long getNextSeq() {
        return nextSeq;
    }

    /** @param nextSeq next unassigned sequence number */
    public void setNextSeq(Long nextSeq) {
        this.nextSeq = nextSeq;
    }

    /** @return stream status */
    public String getStatus() {
        return status;
    }

    /** @param status stream status */
    public void setStatus(String status) {
        this.status = status;
    }

    /** @return agent profile version pinned for this session */
    public String getProfileVersion() {
        return profileVersion;
    }

    /** @param profileVersion agent profile version pinned for this session */
    public void setProfileVersion(String profileVersion) {
        this.profileVersion = profileVersion;
    }

    /** @return serialized capability set available to this session */
    public String getCapabilitySnapshot() {
        return capabilitySnapshot;
    }

    /** @param capabilitySnapshot serialized capability set available to this session */
    public void setCapabilitySnapshot(String capabilitySnapshot) {
        this.capabilitySnapshot = capabilitySnapshot;
    }

    /** @return UTC creation time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt UTC creation time */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** @return UTC time of the last sequence reservation */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @param updatedAt UTC time of the last sequence reservation */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
