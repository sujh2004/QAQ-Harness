package com.devpilot.skill.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Database row of {@code skill_approval}: one human decision to let a skill run in a session. */
@TableName("skill_approval")
public class SkillApprovalRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long skillId;
    private Integer approved;
    private String decidedBy;
    private String reason;
    private LocalDateTime decidedAt;

    /** @return session the decision applies to */
    public String getSessionId() {
        return sessionId;
    }

    /** @param sessionId session the decision applies to */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    /** @return skill the decision applies to */
    public Long getSkillId() {
        return skillId;
    }

    /** @param skillId skill the decision applies to */
    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }
    /** @return 1 when execution was allowed */
    public Integer getApproved() {
        return approved;
    }

    /** @param approved 1 when execution was allowed */
    public void setApproved(Integer approved) {
        this.approved = approved;
    }
    /** @return who decided */
    public String getDecidedBy() {
        return decidedBy;
    }

    /** @param decidedBy who decided */
    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }
    /** @return safe explanation of the decision */
    public String getReason() {
        return reason;
    }

    /** @param reason safe explanation of the decision */
    public void setReason(String reason) {
        this.reason = reason;
    }
    /** @return decision time */
    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    /** @param decidedAt decision time */
    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    /** @return row identity */
    public Long getId() {
        return id;
    }

    /** @param id row identity */
    public void setId(Long id) {
        this.id = id;
    }
}
