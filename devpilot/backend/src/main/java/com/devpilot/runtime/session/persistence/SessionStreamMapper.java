package com.devpilot.runtime.session.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Data access for {@code session_stream}, the per-session sequence allocator. */
@Mapper
public interface SessionStreamMapper extends BaseMapper<SessionStreamRow> {

    /**
     * Locks the stream row and returns the next unassigned sequence number.
     *
     * <p>The row lock is held until the surrounding transaction commits, which serialises sequence
     * allocation for the session. This replaces reading {@code MAX(seq)} without a lock.
     *
     * @param sessionId owning session
     * @return next unassigned sequence number, null when the stream does not exist
     */
    @Select("SELECT next_seq FROM session_stream WHERE session_id = #{sessionId} FOR UPDATE")
    Long lockNextSeq(@Param("sessionId") String sessionId);

    /**
     * Advances the allocator after a batch of events reserved their positions.
     *
     * @param sessionId owning session
     * @param nextSeq first sequence number the next append may use
     * @param updatedAt UTC time of this reservation
     * @return number of updated rows
     */
    @Update("UPDATE session_stream SET next_seq = #{nextSeq}, updated_at = #{updatedAt} "
            + "WHERE session_id = #{sessionId}")
    int advanceNextSeq(
            @Param("sessionId") String sessionId,
            @Param("nextSeq") long nextSeq,
            @Param("updatedAt") LocalDateTime updatedAt);
}
