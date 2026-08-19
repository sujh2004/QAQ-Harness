package com.devpilot.log.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Model-facing arguments of the recent error summary tool.
 *
 * @param projectId owning project; must match the project of the calling session
 * @param hours size of the window in hours, defaults to 24
 */
public record ErrorSummaryArguments(Long projectId, @Min(1) @Max(720) Integer hours) {

    /** Applies the default window so a model does not have to supply it. */
    public ErrorSummaryArguments {
        hours = hours == null ? 24 : hours;
    }
}
