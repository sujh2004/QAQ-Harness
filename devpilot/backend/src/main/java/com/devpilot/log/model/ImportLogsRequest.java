package com.devpilot.log.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for importing log lines.
 *
 * @param entries lines to store, bounded so one request cannot exhaust the database
 */
public record ImportLogsRequest(
        @NotEmpty @Size(max = 1000, message = "at most 1000 entries per request")
        @Valid List<LogEntryRequest> entries) {
}
