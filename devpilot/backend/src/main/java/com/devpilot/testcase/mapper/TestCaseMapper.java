package com.devpilot.testcase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.testcase.model.TestCaseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for {@code test_case}. */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCaseRow> {

    /**
     * Lists the cases of a project, newest first.
     *
     * @param projectId owning project
     * @param offset rows to skip
     * @param limit maximum rows to return
     * @return one page of cases
     */
    @Select("SELECT * FROM test_case WHERE project_id = #{projectId} "
            + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<TestCaseRow> selectByProject(
            @Param("projectId") long projectId, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * Counts the cases of a project.
     *
     * @param projectId owning project
     * @return number of cases
     */
    @Select("SELECT COUNT(*) FROM test_case WHERE project_id = #{projectId}")
    long countByProject(@Param("projectId") long projectId);
}
