package com.devpilot.log.model;

import java.time.LocalDateTime;

/** One aggregated error group. */
public class ErrorSummaryRow {

    private String serviceName;
    private String exceptionType;
    private Long occurrences;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private Long sampleId;

    /** @return emitting service */
    public String getServiceName() {
        return serviceName;
    }

    /** @param serviceName emitting service */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /** @return thrown exception type */
    public String getExceptionType() {
        return exceptionType;
    }

    /** @param exceptionType thrown exception type */
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    /** @return how often the group occurred in the window */
    public Long getOccurrences() {
        return occurrences;
    }

    /** @param occurrences how often the group occurred in the window */
    public void setOccurrences(Long occurrences) {
        this.occurrences = occurrences;
    }

    /** @return first occurrence in the window */
    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    /** @param firstSeen first occurrence in the window */
    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    /** @return last occurrence in the window */
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    /** @param lastSeen last occurrence in the window */
    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    /** @return id of one representative line of the group */
    public Long getSampleId() {
        return sampleId;
    }

    /** @param sampleId id of one representative line of the group */
    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }
}
