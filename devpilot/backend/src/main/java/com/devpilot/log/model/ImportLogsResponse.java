package com.devpilot.log.model;

/**
 * Outcome of a log import.
 *
 * @param imported number of stored lines
 */
public record ImportLogsResponse(int imported) {
}
