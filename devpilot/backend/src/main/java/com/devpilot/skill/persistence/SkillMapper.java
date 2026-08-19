package com.devpilot.skill.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for installed skills and the projects they are enabled in. */
@Mapper
public interface SkillMapper extends BaseMapper<SkillRow> {

    /**
     * Lists every installed skill.
     *
     * @return installed skills, newest first
     */
    @Select("SELECT * FROM skill ORDER BY id DESC")
    List<SkillRow> selectAll();

    /**
     * Finds an installed skill by its marketplace key.
     *
     * @param skillKey package identifier
     * @return matching skill, null when it is not installed
     */
    @Select("SELECT * FROM skill WHERE skill_key = #{skillKey}")
    SkillRow selectByKey(@Param("skillKey") String skillKey);

    /**
     * Lists the skills enabled for a project.
     *
     * @param projectId owning project
     * @return enabled skills
     */
    @Select("""
            SELECT s.* FROM skill s
            JOIN project_skill ps ON ps.skill_id = s.id
            WHERE ps.project_id = #{projectId} AND ps.enabled = 1 AND s.status = 'INSTALLED'
            ORDER BY s.skill_key
            """)
    List<SkillRow> selectEnabledForProject(@Param("projectId") long projectId);

    /**
     * Enables a skill for a project.
     *
     * @param projectId owning project
     * @param skillId skill to enable
     * @return number of inserted rows
     */
    @Insert("INSERT INTO project_skill (project_id, skill_id, enabled, created_at) "
            + "VALUES (#{projectId}, #{skillId}, 1, CURRENT_TIMESTAMP)")
    int enableForProject(@Param("projectId") long projectId, @Param("skillId") long skillId);

    /**
     * Disables a skill for a project.
     *
     * @param projectId owning project
     * @param skillId skill to disable
     * @return number of removed rows
     */
    @Delete("DELETE FROM project_skill WHERE project_id = #{projectId} AND skill_id = #{skillId}")
    int disableForProject(@Param("projectId") long projectId, @Param("skillId") long skillId);

    /**
     * Reports whether a skill is enabled for a project.
     *
     * @param projectId owning project
     * @param skillId skill to check
     * @return number of matching rows
     */
    @Select("SELECT COUNT(*) FROM project_skill "
            + "WHERE project_id = #{projectId} AND skill_id = #{skillId} AND enabled = 1")
    int countEnabled(@Param("projectId") long projectId, @Param("skillId") long skillId);
}
