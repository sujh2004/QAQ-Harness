package com.devpilot.project.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Database row of {@code dev_project}. */
@TableName("dev_project")
public class ProjectRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;
    private String code;
    private String description;
    private String repositoryPath;
    private String defaultBranch;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** @return project identity */
    public Long getId() {
        return id;
    }

    /** @param id project identity */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return display name */
    public String getName() {
        return name;
    }

    /** @param name display name */
    public void setName(String name) {
        this.name = name;
    }

    /** @return unique short code */
    public String getCode() {
        return code;
    }

    /** @param code unique short code */
    public void setCode(String code) {
        this.code = code;
    }

    /** @return description */
    public String getDescription() {
        return description;
    }

    /** @param description description */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return configured local repository path */
    public String getRepositoryPath() {
        return repositoryPath;
    }

    /** @param repositoryPath configured local repository path */
    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    /** @return default branch name */
    public String getDefaultBranch() {
        return defaultBranch;
    }

    /** @param defaultBranch default branch name */
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    /** @return 1 for active, 0 for archived */
    public Integer getStatus() {
        return status;
    }

    /** @param status 1 for active, 0 for archived */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /** @return creation time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt creation time */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** @return last update time */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @param updatedAt last update time */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
