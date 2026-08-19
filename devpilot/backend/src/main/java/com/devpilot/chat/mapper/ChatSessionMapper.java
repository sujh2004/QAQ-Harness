package com.devpilot.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.chat.model.ChatSessionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for {@code chat_session}. */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionRow> {

    /**
     * Lists the sessions of a project, most recently updated first.
     *
     * @param projectId owning project
     * @param offset rows to skip
     * @param limit maximum rows to return
     * @return one page of sessions
     */
    @Select("SELECT * FROM chat_session WHERE project_id = #{projectId} "
            + "ORDER BY updated_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ChatSessionRow> selectByProject(
            @Param("projectId") long projectId, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * Counts the sessions of a project.
     *
     * @param projectId owning project
     * @return number of sessions
     */
    @Select("SELECT COUNT(*) FROM chat_session WHERE project_id = #{projectId}")
    long countByProject(@Param("projectId") long projectId);
}
