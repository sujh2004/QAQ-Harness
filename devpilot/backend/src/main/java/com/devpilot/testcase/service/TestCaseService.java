package com.devpilot.testcase.service;

import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.api.PageResponse;
import com.devpilot.common.exception.BusinessException;
import com.devpilot.project.service.ProjectService;
import com.devpilot.testcase.mapper.TestCaseMapper;
import com.devpilot.testcase.model.SaveTestCasesArguments;
import com.devpilot.testcase.model.TestCaseResponse;
import com.devpilot.testcase.model.TestCaseRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Stores and reads the test cases an agent designed.
 *
 * <p>This is the one place in the MVP where an agent changes state, so the write path stays narrow:
 * cases are always attributed to a project and, when known, to the session that produced them.
 */
@Service
public class TestCaseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String AGENT_SOURCE = "AGENT";

    private final TestCaseMapper testCaseMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param testCaseMapper test case table access
     * @param projectService project lookup
     * @param objectMapper shared JSON mapper used for the step list
     * @param clock runtime clock
     */
    public TestCaseService(
            TestCaseMapper testCaseMapper,
            ProjectService projectService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.testCaseMapper = testCaseMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Stores the cases of one tool call.
     *
     * @param arguments validated cases
     * @return the stored cases
     */
    @Transactional
    public List<TestCaseResponse> save(SaveTestCasesArguments arguments) {
        projectService.require(arguments.projectId());
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));

        return arguments.cases().stream().map(input -> {
            TestCaseRow row = new TestCaseRow();
            row.setProjectId(arguments.projectId());
            row.setSessionId(arguments.sourceSessionId());
            row.setTitle(input.title());
            row.setPriority(input.priority());
            row.setPrecondition(input.precondition());
            row.setStepsJson(writeSteps(input.steps()));
            row.setExpectedResult(input.expectedResult());
            row.setSource(AGENT_SOURCE);
            row.setCreatedAt(now);
            testCaseMapper.insert(row);
            return toResponse(row);
        }).toList();
    }

    /**
     * Lists the cases of a project, newest first.
     *
     * @param projectId owning project
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of cases
     */
    @Transactional(readOnly = true)
    public PageResponse<TestCaseResponse> list(long projectId, int page, int size) {
        projectService.require(projectId);
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        List<TestCaseResponse> items = testCaseMapper
                .selectByProject(projectId, (long) effectivePage * effectiveSize, effectiveSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(
                items, testCaseMapper.countByProject(projectId), effectivePage, effectiveSize);
    }

    /**
     * Reads one case.
     *
     * @param id case identity
     * @return the case
     */
    @Transactional(readOnly = true)
    public TestCaseResponse get(long id) {
        return toResponse(require(id));
    }

    /**
     * Deletes one case.
     *
     * @param id case identity
     */
    @Transactional
    public void delete(long id) {
        require(id);
        testCaseMapper.deleteById(id);
    }

    private TestCaseRow require(long id) {
        TestCaseRow row = testCaseMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Test case " + id + " does not exist");
        }
        return row;
    }

    private TestCaseResponse toResponse(TestCaseRow row) {
        return new TestCaseResponse(
                row.getId(),
                row.getProjectId(),
                row.getSessionId(),
                row.getTitle(),
                row.getPriority(),
                row.getPrecondition(),
                readSteps(row.getStepsJson()),
                row.getExpectedResult(),
                row.getSource(),
                row.getCreatedAt());
    }

    private String writeSteps(List<String> steps) {
        try {
            return objectMapper.writeValueAsString(steps == null ? List.of() : steps);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Test case steps cannot be serialized", exception);
        }
    }

    private List<String> readSteps(String stepsJson) {
        if (stepsJson == null || stepsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stepsJson, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            // A row written by an older schema should not break the whole listing.
            return List.of(stepsJson);
        }
    }
}
