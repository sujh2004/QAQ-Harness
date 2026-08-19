package com.devpilot.log.model;

import java.time.LocalDateTime;

/**
 * One log line as handed to a model.
 *
 * <p>The stack trace is shortened here rather than in the database: an agent needs the first frames
 * to locate a file and line, not the whole trace, and the model context is a scarce resource.
 *
 * @param serviceName emitting service
 * @param level log level
 * @param traceId distributed trace identifier
 * @param logger logger name
 * @param message log message
 * @param exceptionType thrown exception type
 * @param stackTracePreview leading part of the stack trace
 * @param logTime when the line was emitted
 */
public record LogToolEntry(
        String serviceName,
        String level,
        String traceId,
        String logger,
        String message,
        String exceptionType,
        String stackTracePreview,
        LocalDateTime logTime) {

    private static final int STACK_TRACE_PREVIEW_CHARS = 800;

    /**
     * Converts a stored log line, shortening the stack trace.
     *
     * @param entry stored log line
     * @return model-facing representation
     */
    public static LogToolEntry from(LogEntryResponse entry) {
        String trace = entry.stackTrace();
        String preview = trace == null || trace.length() <= STACK_TRACE_PREVIEW_CHARS
                ? trace
                : trace.substring(0, STACK_TRACE_PREVIEW_CHARS) + "…";
        return new LogToolEntry(
                entry.serviceName(),
                entry.level(),
                entry.traceId(),
                entry.logger(),
                entry.message(),
                entry.exceptionType(),
                preview,
                entry.logTime());
    }
}
