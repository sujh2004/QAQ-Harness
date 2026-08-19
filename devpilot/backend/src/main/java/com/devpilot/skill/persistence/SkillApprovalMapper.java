package com.devpilot.skill.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for {@code skill_approval}. */
@Mapper
public interface SkillApprovalMapper extends BaseMapper<SkillApprovalRow> {

    /**
     * Finds the decision that applies to one skill in one session.
     *
     * @param sessionId session the decision applies to
     * @param skillId skill the decision applies to
     * @return matching decision, null when nobody has decided yet
     */
    @Select("SELECT * FROM skill_approval WHERE session_id = #{sessionId} AND skill_id = #{skillId}")
    SkillApprovalRow selectDecision(
            @Param("sessionId") String sessionId, @Param("skillId") long skillId);

    /**
     * Lists every decision made in a session.
     *
     * @param sessionId owning session
     * @return decisions in the order they were made
     */
    @Select("SELECT * FROM skill_approval WHERE session_id = #{sessionId} ORDER BY id")
    List<SkillApprovalRow> selectBySession(@Param("sessionId") String sessionId);
}
