package com.devpilot.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.log.model.ErrorSummaryRow;
import com.devpilot.log.model.SystemLogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for {@code system_log}.
 *
 * <p>Filters are passed as individual named parameters rather than a wrapper object so the dynamic
 * SQL never has to reflect over a record accessor.
 */
@Mapper
public interface SystemLogMapper extends BaseMapper<SystemLogRow> {

    /** Shared WHERE clause of the search and count statements. */
    String FILTERS = """
            WHERE project_id = #{projectId}
            <if test="serviceName != null">AND service_name = #{serviceName}</if>
            <if test="level != null">AND level = #{level}</if>
            <if test="traceId != null">AND trace_id = #{traceId}</if>
            <if test="keyword != null">
              AND (message LIKE CONCAT('%', #{keyword}, '%')
                OR exception_type LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="startTime != null">AND log_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND log_time &lt;= #{endTime}</if>
            """;

    /**
     * Searches logs newest first.
     *
     * @param projectId owning project
     * @param serviceName emitting service, null to ignore
     * @param level log level, null to ignore
     * @param traceId trace identifier, null to ignore
     * @param keyword substring of the message or exception type, null to ignore
     * @param startTime inclusive lower bound, null to ignore
     * @param endTime inclusive upper bound, null to ignore
     * @param offset rows to skip
     * @param limit maximum rows to return
     * @return one page of matching log lines
     */
    @Select("<script>SELECT * FROM system_log " + FILTERS
            + " ORDER BY log_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<SystemLogRow> search(
            @Param("projectId") long projectId,
            @Param("serviceName") String serviceName,
            @Param("level") String level,
            @Param("traceId") String traceId,
            @Param("keyword") String keyword,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /**
     * Counts matching logs.
     *
     * @param projectId owning project
     * @param serviceName emitting service, null to ignore
     * @param level log level, null to ignore
     * @param traceId trace identifier, null to ignore
     * @param keyword substring of the message or exception type, null to ignore
     * @param startTime inclusive lower bound, null to ignore
     * @param endTime inclusive upper bound, null to ignore
     * @return number of matching log lines
     */
    @Select("<script>SELECT COUNT(*) FROM system_log " + FILTERS + "</script>")
    long count(
            @Param("projectId") long projectId,
            @Param("serviceName") String serviceName,
            @Param("level") String level,
            @Param("traceId") String traceId,
            @Param("keyword") String keyword,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Groups recent errors so a reader does not have to page through every line.
     *
     * <p>Only the id of a representative line is aggregated; its text is read separately, because
     * aggregating over a large-object column is not portable.
     *
     * @param projectId owning project
     * @param since inclusive lower bound of the window
     * @param limit maximum number of groups
     * @return error groups ordered by occurrence count
     */
    @Select("""
            SELECT service_name, exception_type, COUNT(*) AS occurrences,
                   MIN(log_time) AS first_seen, MAX(log_time) AS last_seen,
                   MIN(id) AS sample_id
            FROM system_log
            WHERE project_id = #{projectId} AND level = 'ERROR' AND log_time >= #{since}
            GROUP BY service_name, exception_type
            ORDER BY occurrences DESC, last_seen DESC
            LIMIT #{limit}
            """)
    List<ErrorSummaryRow> summarizeErrors(
            @Param("projectId") long projectId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit);
}
