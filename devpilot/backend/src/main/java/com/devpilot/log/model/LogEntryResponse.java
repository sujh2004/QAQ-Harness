package com.devpilot.log.model;

import java.time.LocalDateTime;

/**
 * One log line as returned by the API.
 *
 * @param id row identity
 * @param projectId owning project
 * @param serviceName emitting service
 * @param level log level
 * @param traceId distributed trace identifier
 * @param logger logger name
 * @param message log message
 * @param exceptionType thrown exception type
 * @param stackTrace captured stack trace
 * @param logTime when the line was emitted
 */
public record LogEntryResponse(
        Long id,
        Long projectId,
        String serviceName,
        String level,
        String traceId,
        String logger,
        String message,
        String exceptionType,
        String stackTrace,
        LocalDateTime logTime) {

    /**
     * Converts a database row.
     *
     * @param row stored log line
     * @return API representation
     */
    public static LogEntryResponse from(SystemLogRow row) {
        return new LogEntryResponse(
                row.getId(),
                row.getProjectId(),
                row.getServiceName(),
                row.getLevel(),
                row.getTraceId(),
                row.getLogger(),
                row.getMessage(),
                row.getExceptionType(),
                row.getStackTrace(),
                row.getLogTime());
    }
}
