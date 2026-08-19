package com.devpilot.project.controller;

import com.devpilot.common.api.PageResponse;
import com.devpilot.common.api.Result;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.model.ProjectResponse;
import com.devpilot.project.model.RepositoryValidationResponse;
import com.devpilot.project.model.UpdateProjectRequest;
import com.devpilot.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project management endpoints. */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Creates the controller.
     *
     * @param projectService project application service
     */
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Lists projects newest first.
     *
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of projects
     */
    @GetMapping
    public Result<PageResponse<ProjectResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(projectService.list(page, size));
    }

    /**
     * Creates a project.
     *
     * @param request project attributes
     * @return the created project
     */
    @PostMapping
    public Result<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return Result.success(projectService.create(request));
    }

    /**
     * Reads one project.
     *
     * @param id project identity
     * @return the project
     */
    @GetMapping("/{id}")
    public Result<ProjectResponse> get(@PathVariable long id) {
        return Result.success(projectService.get(id));
    }

    /**
     * Updates a project.
     *
     * @param id project identity
     * @param request new attributes
     * @return the updated project
     */
    @PutMapping("/{id}")
    public Result<ProjectResponse> update(
            @PathVariable long id, @Valid @RequestBody UpdateProjectRequest request) {
        return Result.success(projectService.update(id, request));
    }

    /**
     * Checks whether the configured repository path can be read.
     *
     * @param id project identity
     * @return which checks passed and the absolute path that would be read
     */
    @PostMapping("/{id}/validate-repository")
    public Result<RepositoryValidationResponse> validateRepository(@PathVariable long id) {
        return Result.success(projectService.validateRepository(id));
    }
}
