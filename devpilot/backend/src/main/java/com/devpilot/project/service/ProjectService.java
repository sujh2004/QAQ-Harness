package com.devpilot.project.service;

import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.api.PageResponse;
import com.devpilot.common.exception.BusinessException;
import com.devpilot.config.AppProperties;
import com.devpilot.project.mapper.ProjectMapper;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.model.ProjectResponse;
import com.devpilot.project.model.ProjectRow;
import com.devpilot.project.model.RepositoryValidationResponse;
import com.devpilot.project.model.UpdateProjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Owns projects and the local repository path each one is bound to.
 *
 * <p>Repository paths are stored as configured and resolved lazily: a relative path is resolved
 * against {@code app.repository.base-dir} so no user home directory is baked into the data.
 */
@Service
public class ProjectService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectMapper projectMapper;
    private final Clock clock;
    private final Path repositoryBaseDir;

    /**
     * Creates the service.
     *
     * @param projectMapper project table access
     * @param clock runtime clock
     * @param appProperties application configuration
     */
    public ProjectService(ProjectMapper projectMapper, Clock clock, AppProperties appProperties) {
        this.projectMapper = projectMapper;
        this.clock = clock;
        String configured = appProperties.repository().baseDir();
        this.repositoryBaseDir = Path.of(
                configured == null || configured.isBlank() ? System.getProperty("user.dir") : configured);
    }

    /**
     * Lists projects newest first.
     *
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of projects
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(int page, int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        List<ProjectResponse> items = projectMapper
                .selectPage((long) effectivePage * effectiveSize, effectiveSize)
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return PageResponse.of(items, projectMapper.countAll(), effectivePage, effectiveSize);
    }

    /**
     * Reads one project.
     *
     * @param id project identity
     * @return the project
     * @throws BusinessException when the project does not exist
     */
    @Transactional(readOnly = true)
    public ProjectResponse get(long id) {
        return ProjectResponse.from(require(id));
    }

    /**
     * Creates a project.
     *
     * @param request project attributes
     * @return the created project
     * @throws BusinessException when the code is already used
     */
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        if (projectMapper.selectByCode(request.code()) != null) {
            throw new BusinessException(
                    ErrorCode.PROJECT_CODE_CONFLICT, "Project code " + request.code() + " is already used");
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
        ProjectRow row = new ProjectRow();
        row.setName(request.name());
        row.setCode(request.code());
        row.setDescription(request.description());
        row.setRepositoryPath(request.repositoryPath());
        row.setDefaultBranch(blankToDefault(request.defaultBranch()));
        row.setStatus(1);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        projectMapper.insert(row);
        return ProjectResponse.from(row);
    }

    /**
     * Updates a project. The code cannot change because it identifies the project elsewhere.
     *
     * @param id project identity
     * @param request new attributes
     * @return the updated project
     * @throws BusinessException when the project does not exist
     */
    @Transactional
    public ProjectResponse update(long id, UpdateProjectRequest request) {
        ProjectRow row = require(id);
        row.setName(request.name());
        row.setDescription(request.description());
        row.setRepositoryPath(request.repositoryPath());
        row.setDefaultBranch(blankToDefault(request.defaultBranch()));
        row.setStatus(request.status() == null ? row.getStatus() : request.status());
        row.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())));
        projectMapper.updateById(row);
        return ProjectResponse.from(row);
    }

    /**
     * Checks whether the configured repository path can be read.
     *
     * @param id project identity
     * @return which checks passed and the absolute path that would be read
     * @throws BusinessException when the project does not exist
     */
    @Transactional(readOnly = true)
    public RepositoryValidationResponse validateRepository(long id) {
        ProjectRow row = require(id);
        Path resolved = resolveRepositoryPath(row.getRepositoryPath());

        boolean exists = Files.exists(resolved);
        boolean directory = exists && Files.isDirectory(resolved);
        boolean readable = directory && Files.isReadable(resolved);
        boolean gitRepository = readable && Files.exists(resolved.resolve(".git"));
        boolean accessible = readable;

        String detail;
        if (!exists) {
            detail = "Path does not exist";
        } else if (!directory) {
            detail = "Path is not a directory";
        } else if (!readable) {
            detail = "Directory is not readable by the backend process";
        } else if (!gitRepository) {
            detail = "Readable directory without a .git entry; code tools will still work";
        } else {
            detail = "Readable Git repository";
        }

        return new RepositoryValidationResponse(
                row.getRepositoryPath(), resolved.toString(), exists, directory, readable,
                gitRepository, accessible, detail);
    }

    /**
     * Resolves a stored repository path to the absolute directory the runtime reads from.
     *
     * @param repositoryPath path as configured on the project
     * @return absolute, normalised path
     */
    public Path resolveRepositoryPath(String repositoryPath) {
        Path configured = Path.of(repositoryPath);
        Path resolved = configured.isAbsolute() ? configured : repositoryBaseDir.resolve(configured);
        return resolved.toAbsolutePath().normalize();
    }

    /**
     * Reads a project row, failing with a stable error when it is missing.
     *
     * @param id project identity
     * @return stored project
     * @throws BusinessException when the project does not exist
     */
    @Transactional(readOnly = true)
    public ProjectRow require(long id) {
        ProjectRow row = projectMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project " + id + " does not exist");
        }
        return row;
    }

    private static String blankToDefault(String defaultBranch) {
        return defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch;
    }
}
