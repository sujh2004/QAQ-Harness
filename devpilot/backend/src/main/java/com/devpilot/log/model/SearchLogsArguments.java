package com.devpilot.log.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Model-facing arguments of the log search tool.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param serviceName emitting service, omit to search every service
 * @param level log level, omit to search every level
 * @param keyword substring of the message or exception type, omit to ignore
 * @param startTime inclusive lower bound, omit to ignore
 * @param endTime inclusive upper bound, omit to ignore
 * @param limit maximum number of lines, at most 100, defaults to 50
 */
public record SearchLogsArguments(
        @NotNull Long projectId,
        @Size(max = 100) String serviceName,
        @Pattern(regexp = "(?i)TRACE|DEBUG|INFO|WARN|ERROR|FATAL",
                message = "must be one of TRACE, DEBUG, INFO, WARN, ERROR, FATAL") String level,
        @Size(max = 200) String keyword,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @Min(1) @Max(100) Integer limit) {

    /** Applies the default limit so a model does not have to supply it. */
    public SearchLogsArguments {
        limit = limit == null ? 50 : limit;
    }
}
