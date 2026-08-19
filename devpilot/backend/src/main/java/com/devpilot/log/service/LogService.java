package com.devpilot.log.service;

import com.devpilot.common.api.PageResponse;
import com.devpilot.log.mapper.SystemLogMapper;
import com.devpilot.log.model.ErrorSummaryResponse;
import com.devpilot.log.model.ImportLogsRequest;
import com.devpilot.log.model.LogEntryRequest;
import com.devpilot.log.model.LogEntryResponse;
import com.devpilot.log.model.SystemLogRow;
import com.devpilot.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Queries and imports system logs.
 *
 * <p>Every query is scoped to one project and bounded: a caller cannot ask for more than
 * {@link #MAX_PAGE_SIZE} lines, which is also the ceiling the log tool will inherit in Phase 3.
 */
@Service
public class LogService {

    /** Largest page a caller may request. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_SUMMARY_GROUPS = 20;
    private static final int MAX_SUMMARY_HOURS = 24 * 30;

    private final SystemLogMapper systemLogMapper;
    private final ProjectService projectService;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param systemLogMapper log table access
     * @param projectService project lookup, used to reject unknown projects
     * @param clock runtime clock
     */
    public LogService(SystemLogMapper systemLogMapper, ProjectService projectService, Clock clock) {
        this.systemLogMapper = systemLogMapper;
        this.projectService = projectService;
        this.clock = clock;
    }

    /**
     * Searches logs of one project.
     *
     * @param projectId owning project
     * @param serviceName emitting service, null to ignore
     * @param level log level, null to ignore
     * @param keyword substring of the message or exception type, null to ignore
     * @param traceId trace identifier, null to ignore
     * @param startTime inclusive lower bound, null to ignore
     * @param endTime inclusive upper bound, null to ignore
     * @param page zero-based page index
     * @param size page size, capped at {@link #MAX_PAGE_SIZE}
     * @return one page of matching log lines, newest first
     */
    @Transactional(readOnly = true)
    public PageResponse<LogEntryResponse> search(
            long projectId,
            String serviceName,
            String level,
            String keyword,
            String traceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size) {
        projectService.require(projectId);

        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        String normalizedLevel = blankToNull(level) == null ? null : level.trim().toUpperCase();

        List<LogEntryResponse> items = systemLogMapper.search(
                        projectId,
                        blankToNull(serviceName),
                        normalizedLevel,
                        blankToNull(traceId),
                        blankToNull(keyword),
                        startTime,
                        endTime,
                        (long) effectivePage * effectiveSize,
                        effectiveSize)
                .stream()
                .map(LogEntryResponse::from)
                .toList();

        long total = systemLogMapper.count(
                projectId,
                blankToNull(serviceName),
                normalizedLevel,
                blankToNull(traceId),
                blankToNull(keyword),
                startTime,
                endTime);

        return PageResponse.of(items, total, effectivePage, effectiveSize);
    }

    /**
     * Groups the errors of the recent past so a reader sees the shape of a problem before its
     * individual lines.
     *
     * @param projectId owning project
     * @param hours size of the window in hours, capped at 30 days
     * @return error groups ordered by occurrence count
     */
    @Transactional(readOnly = true)
    public List<ErrorSummaryResponse> summarizeErrors(long projectId, int hours) {
        projectService.require(projectId);
        int effectiveHours = Math.min(Math.max(hours, 1), MAX_SUMMARY_HOURS);
        LocalDateTime since = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()))
                .minusHours(effectiveHours);
        return systemLogMapper.summarizeErrors(projectId, since, MAX_SUMMARY_GROUPS).stream()
                .map(row -> ErrorSummaryResponse.from(row, sampleMessage(row.getSampleId())))
                .toList();
    }

    private String sampleMessage(Long sampleId) {
        if (sampleId == null) {
            return null;
        }
        SystemLogRow row = systemLogMapper.selectById(sampleId);
        return row == null ? null : row.getMessage();
    }

    /**
     * Stores log lines for a project.
     *
     * @param projectId owning project
     * @param request lines to store
     * @return how many lines were stored
     */
    @Transactional
    public int importLogs(long projectId, ImportLogsRequest request) {
        projectService.require(projectId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
        for (LogEntryRequest entry : request.entries()) {
            SystemLogRow row = new SystemLogRow();
            row.setProjectId(projectId);
            row.setServiceName(entry.serviceName());
            row.setLevel(entry.level().toUpperCase());
            row.setTraceId(entry.traceId());
            row.setLogger(entry.logger());
            row.setMessage(entry.message());
            row.setExceptionType(entry.exceptionType());
            row.setStackTrace(entry.stackTrace());
            row.setLogTime(entry.logTime());
            row.setCreatedAt(now);
            systemLogMapper.insert(row);
        }
        return request.entries().size();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
