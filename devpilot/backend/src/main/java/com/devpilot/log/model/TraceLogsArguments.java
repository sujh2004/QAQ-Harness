package com.devpilot.log.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Model-facing arguments of the trace lookup tool.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param traceId trace identifier shared by the lines of one request
 * @param limit maximum number of lines, at most 100, defaults to 50
 */
public record TraceLogsArguments(
        @NotNull Long projectId,
        @NotBlank @Size(max = 100) String traceId,
        @Min(1) @Max(100) Integer limit) {

    /** Applies the default limit so a model does not have to supply it. */
    public TraceLogsArguments {
        limit = limit == null ? 50 : limit;
    }
}
