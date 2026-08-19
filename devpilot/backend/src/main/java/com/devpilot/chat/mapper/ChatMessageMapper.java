package com.devpilot.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.chat.model.ChatMessageRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for the {@code chat_message} read projection. */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageRow> {

    /**
     * Lists the messages of a session in timeline order.
     *
     * @param sessionId owning session
     * @param offset rows to skip
     * @param limit maximum rows to return
     * @return one page of messages
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} "
            + "ORDER BY source_seq LIMIT #{limit} OFFSET #{offset}")
    List<ChatMessageRow> selectBySession(
            @Param("sessionId") String sessionId, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * Counts the messages of a session.
     *
     * @param sessionId owning session
     * @return number of messages
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE session_id = #{sessionId}")
    long countBySession(@Param("sessionId") String sessionId);

    /**
     * Drops the projection of a session so it can be rebuilt from the event log.
     *
     * @param sessionId owning session
     * @return number of removed rows
     */
    @Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    int deleteBySession(@Param("sessionId") String sessionId);
}
