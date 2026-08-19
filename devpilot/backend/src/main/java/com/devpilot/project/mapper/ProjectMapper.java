package com.devpilot.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.project.model.ProjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for {@code dev_project}. */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectRow> {

    /**
     * Lists projects newest first.
     *
     * @param offset rows to skip
     * @param limit maximum rows to return
     * @return one page of projects
     */
    @Select("SELECT * FROM dev_project ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ProjectRow> selectPage(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * Counts all projects.
     *
     * @return number of projects
     */
    @Select("SELECT COUNT(*) FROM dev_project")
    long countAll();

    /**
     * Finds a project by its unique code.
     *
     * @param code project code
     * @return matching project, null when the code is unused
     */
    @Select("SELECT * FROM dev_project WHERE code = #{code}")
    ProjectRow selectByCode(@Param("code") String code);
}
