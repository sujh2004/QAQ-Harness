package com.devpilot.log.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * One log line to import.
 *
 * @param serviceName emitting service
 * @param level log level
 * @param traceId distributed trace identifier
 * @param logger logger name
 * @param message log message
 * @param exceptionType thrown exception type
 * @param stackTrace captured stack trace
 * @param logTime when the line was emitted
 */
public record LogEntryRequest(
        @NotBlank @Size(max = 100) String serviceName,
        @NotBlank @Pattern(regexp = "TRACE|DEBUG|INFO|WARN|ERROR|FATAL",
                message = "must be one of TRACE, DEBUG, INFO, WARN, ERROR, FATAL") String level,
        @Size(max = 100) String traceId,
        @Size(max = 255) String logger,
        @NotBlank String message,
        @Size(max = 255) String exceptionType,
        String stackTrace,
        @NotNull LocalDateTime logTime) {
}
