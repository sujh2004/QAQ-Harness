package com.devpilot.log.controller;

import com.devpilot.common.api.PageResponse;
import com.devpilot.common.api.Result;
import com.devpilot.log.model.ErrorSummaryResponse;
import com.devpilot.log.model.ImportLogsRequest;
import com.devpilot.log.model.ImportLogsResponse;
import com.devpilot.log.model.LogEntryResponse;
import com.devpilot.log.service.LogService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** System log endpoints of one project. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/logs")
public class LogController {

    private final LogService logService;

    /**
     * Creates the controller.
     *
     * @param logService log application service
     */
    public LogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * Searches logs newest first.
     *
     * @param projectId owning project
     * @param serviceName emitting service, omit to ignore
     * @param level log level, omit to ignore
     * @param keyword substring of the message or exception type, omit to ignore
     * @param traceId trace identifier, omit to ignore
     * @param startTime inclusive lower bound, omit to ignore
     * @param endTime inclusive upper bound, omit to ignore
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of matching log lines
     */
    @GetMapping
    public Result<PageResponse<LogEntryResponse>> search(
            @PathVariable long projectId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(logService.search(
                projectId, serviceName, level, keyword, traceId, startTime, endTime, page, size));
    }

    /**
     * Groups the errors of the recent past.
     *
     * @param projectId owning project
     * @param hours size of the window in hours
     * @return error groups ordered by occurrence count
     */
    @GetMapping("/error-summary")
    public Result<List<ErrorSummaryResponse>> errorSummary(
            @PathVariable long projectId, @RequestParam(defaultValue = "24") int hours) {
        return Result.success(logService.summarizeErrors(projectId, hours));
    }

    /**
     * Stores log lines for a project.
     *
     * @param projectId owning project
     * @param request lines to store
     * @return how many lines were stored
     */
    @PostMapping("/import")
    public Result<ImportLogsResponse> importLogs(
            @PathVariable long projectId, @Valid @RequestBody ImportLogsRequest request) {
        return Result.success(new ImportLogsResponse(logService.importLogs(projectId, request)));
    }
}
