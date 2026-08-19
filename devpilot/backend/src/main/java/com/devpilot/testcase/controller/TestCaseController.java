package com.devpilot.testcase.controller;

import com.devpilot.common.api.PageResponse;
import com.devpilot.common.api.Result;
import com.devpilot.testcase.model.TestCaseResponse;
import com.devpilot.testcase.service.TestCaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test case endpoints. */
@RestController
@RequestMapping("/api/v1")
public class TestCaseController {

    private final TestCaseService testCaseService;

    /**
     * Creates the controller.
     *
     * @param testCaseService test case application service
     */
    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    /**
     * Lists the cases of a project.
     *
     * @param projectId owning project
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of cases
     */
    @GetMapping("/projects/{projectId}/test-cases")
    public Result<PageResponse<TestCaseResponse>> list(
            @PathVariable long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(testCaseService.list(projectId, page, size));
    }

    /**
     * Reads one case.
     *
     * @param id case identity
     * @return the case
     */
    @GetMapping("/test-cases/{id}")
    public Result<TestCaseResponse> get(@PathVariable long id) {
        return Result.success(testCaseService.get(id));
    }

    /**
     * Deletes one case.
     *
     * @param id case identity
     * @return empty success response
     */
    @DeleteMapping("/test-cases/{id}")
    public Result<Void> delete(@PathVariable long id) {
        testCaseService.delete(id);
        return Result.success(null);
    }
}
