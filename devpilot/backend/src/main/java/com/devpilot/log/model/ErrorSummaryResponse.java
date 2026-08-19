package com.devpilot.log.model;

import java.time.LocalDateTime;

/**
 * One group of recent errors.
 *
 * @param serviceName emitting service
 * @param exceptionType thrown exception type, null when the line carried none
 * @param occurrences how often the group occurred in the window
 * @param firstSeen first occurrence in the window
 * @param lastSeen last occurrence in the window
 * @param sampleMessage one representative message from the group
 */
public record ErrorSummaryResponse(
        String serviceName,
        String exceptionType,
        long occurrences,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        String sampleMessage) {

    /**
     * Converts an aggregation row.
     *
     * @param row aggregated group
     * @param sampleMessage message of the representative line, read separately
     * @return API representation
     */
    public static ErrorSummaryResponse from(ErrorSummaryRow row, String sampleMessage) {
        return new ErrorSummaryResponse(
                row.getServiceName(),
                row.getExceptionType(),
                row.getOccurrences() == null ? 0L : row.getOccurrences(),
                row.getFirstSeen(),
                row.getLastSeen(),
                sampleMessage);
    }
}
