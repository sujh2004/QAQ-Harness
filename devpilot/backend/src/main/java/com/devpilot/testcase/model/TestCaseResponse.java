package com.devpilot.testcase.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A stored test case as returned by the API.
 *
 * @param id row identity
 * @param projectId owning project
 * @param sessionId session the case was generated in
 * @param title what the case verifies
 * @param priority P0 to P3
 * @param precondition state the case assumes
 * @param steps ordered steps
 * @param expectedResult what should happen
 * @param source who produced the case
 * @param createdAt creation time
 */
public record TestCaseResponse(
        Long id,
        Long projectId,
        String sessionId,
        String title,
        String priority,
        String precondition,
        List<String> steps,
        String expectedResult,
        String source,
        LocalDateTime createdAt) {
}
