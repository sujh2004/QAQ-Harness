package com.devpilot.runtime.session.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Database row of {@code session_event}.
 *
 * <p>{@code occurredAt} is stored in UTC so the stored ordering does not depend on the server time
 * zone. Rows are inserted once and never updated.
 */
@TableName("session_event")
public class SessionEventRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long seq;
    private String eventType;
    private Integer schemaVersion;
    private String turnId;
    private String stepId;
    private String runId;
    private String callId;
    private String payloadJson;
    private LocalDateTime occurredAt;

    /** @return database identity */
    public Long getId() {
        return id;
    }

    /** @param id database identity */
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

    /** @return position inside the session stream */
    public Long getSeq() {
        return seq;
    }

    /** @param seq position inside the session stream */
    public void setSeq(Long seq) {
        this.seq = seq;
    }

    /** @return wire name of the event type */
    public String getEventType() {
        return eventType;
    }

    /** @param eventType wire name of the event type */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /** @return payload schema version */
    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    /** @param schemaVersion payload schema version */
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /** @return owning turn */
    public String getTurnId() {
        return turnId;
    }

    /** @param turnId owning turn */
    public void setTurnId(String turnId) {
        this.turnId = turnId;
    }

    /** @return owning step */
    public String getStepId() {
        return stepId;
    }

    /** @param stepId owning step */
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    /** @return owning agent run */
    public String getRunId() {
        return runId;
    }

    /** @param runId owning agent run */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /** @return owning tool call */
    public String getCallId() {
        return callId;
    }

    /** @param callId owning tool call */
    public void setCallId(String callId) {
        this.callId = callId;
    }

    /** @return serialized payload */
    public String getPayloadJson() {
        return payloadJson;
    }

    /** @param payloadJson serialized payload */
    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    /** @return UTC time the fact happened */
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    /** @param occurredAt UTC time the fact happened */
    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
