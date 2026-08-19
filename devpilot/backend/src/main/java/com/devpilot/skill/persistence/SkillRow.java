package com.devpilot.skill.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Database row of {@code skill}: one installed package. */
@TableName("skill")
public class SkillRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String skillKey;
    private String name;
    private String version;
    private String description;
    private String runtime;
    private String entrypoint;
    private String argsSchema;
    private String sourceUrl;
    private String checksum;
    private String installPath;
    private String status;
    private LocalDateTime installedAt;
    private LocalDateTime updatedAt;

    /** @return stable identifier */
    public String getSkillKey() {
        return skillKey;
    }

    /** @param skillKey stable identifier */
    public void setSkillKey(String skillKey) {
        this.skillKey = skillKey;
    }
    /** @return human-readable name */
    public String getName() {
        return name;
    }

    /** @param name human-readable name */
    public void setName(String name) {
        this.name = name;
    }
    /** @return package version */
    public String getVersion() {
        return version;
    }

    /** @param version package version */
    public void setVersion(String version) {
        this.version = version;
    }
    /** @return what the skill does */
    public String getDescription() {
        return description;
    }

    /** @param description what the skill does */
    public void setDescription(String description) {
        this.description = description;
    }
    /** @return runtime name */
    public String getRuntime() {
        return runtime;
    }

    /** @param runtime runtime name */
    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }
    /** @return script path inside the package */
    public String getEntrypoint() {
        return entrypoint;
    }

    /** @param entrypoint script path inside the package */
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }
    /** @return serialized JSON Schema of the arguments */
    public String getArgsSchema() {
        return argsSchema;
    }

    /** @param argsSchema serialized JSON Schema of the arguments */
    public void setArgsSchema(String argsSchema) {
        this.argsSchema = argsSchema;
    }
    /** @return marketplace the package came from */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /** @param sourceUrl marketplace the package came from */
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
    /** @return SHA-256 of the installed content */
    public String getChecksum() {
        return checksum;
    }

    /** @param checksum SHA-256 of the installed content */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    /** @return directory the package was written to */
    public String getInstallPath() {
        return installPath;
    }

    /** @param installPath directory the package was written to */
    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }
    /** @return INSTALLED or DISABLED */
    public String getStatus() {
        return status;
    }

    /** @param status INSTALLED or DISABLED */
    public void setStatus(String status) {
        this.status = status;
    }
    /** @return installation time */
    public LocalDateTime getInstalledAt() {
        return installedAt;
    }

    /** @param installedAt installation time */
    public void setInstalledAt(LocalDateTime installedAt) {
        this.installedAt = installedAt;
    }
    /** @return last update time */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @param updatedAt last update time */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** @return row identity */
    public Long getId() {
        return id;
    }

    /** @param id row identity */
    public void setId(Long id) {
        this.id = id;
    }
}
