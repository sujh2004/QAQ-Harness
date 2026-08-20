package com.devpilot.knowledge.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for {@code knowledge_document}. */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentRow> {

    /**
     * Lists the documents of a project, newest first.
     *
     * @param projectId owning project
     * @return imported documents
     */
    @Select("SELECT * FROM knowledge_document WHERE project_id = #{projectId} ORDER BY id DESC")
    List<KnowledgeDocumentRow> selectByProject(@Param("projectId") long projectId);

    /**
     * Removes every document of a project.
     *
     * @param projectId owning project
     * @return number of removed rows
     */
    @Delete("DELETE FROM knowledge_document WHERE project_id = #{projectId}")
    int deleteByProject(@Param("projectId") long projectId);
}
