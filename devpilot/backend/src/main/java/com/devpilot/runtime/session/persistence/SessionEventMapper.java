package com.devpilot.runtime.session.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Data access for the append-only {@code session_event} table. */
@Mapper
public interface SessionEventMapper extends BaseMapper<SessionEventRow> {

    /**
     * Reads a whole stream in sequence order.
     *
     * @param sessionId owning session
     * @return rows ordered by sequence number
     */
    @Select("SELECT * FROM session_event WHERE session_id = #{sessionId} ORDER BY seq")
    List<SessionEventRow> selectBySession(@Param("sessionId") String sessionId);

    /**
     * Reads a page of a stream, used for replay and SSE reconnection.
     *
     * @param sessionId owning session
     * @param afterSeq exclusive lower bound
     * @param limit maximum number of rows
     * @return rows ordered by sequence number
     */
    @Select("SELECT * FROM session_event WHERE session_id = #{sessionId} AND seq > #{afterSeq} "
            + "ORDER BY seq LIMIT #{limit}")
    List<SessionEventRow> selectAfterSeq(
            @Param("sessionId") String sessionId, @Param("afterSeq") long afterSeq, @Param("limit") int limit);

    /**
     * Finds sessions holding a turn that was started but never ended.
     *
     * @return session identifiers with at least one open turn
     */
    @Select("""
            SELECT DISTINCT started.session_id
            FROM session_event started
            WHERE started.event_type = 'turn_started'
              AND NOT EXISTS (
                  SELECT 1 FROM session_event ended
                  WHERE ended.session_id = started.session_id
                    AND ended.turn_id = started.turn_id
                    AND ended.event_type = 'turn_ended')
            """)
    List<String> selectSessionsWithOpenTurns();
}
