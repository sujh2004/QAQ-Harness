package com.devpilot.skill.service;

import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.exception.BusinessException;
import com.devpilot.project.service.ProjectService;
import com.devpilot.skill.SkillSource;
import com.devpilot.skill.SkillSourceException;
import com.devpilot.skill.model.SkillPackage;
import com.devpilot.skill.model.SkillResponse;
import com.devpilot.skill.persistence.SkillApprovalMapper;
import com.devpilot.skill.persistence.SkillApprovalRow;
import com.devpilot.skill.persistence.SkillMapper;
import com.devpilot.skill.persistence.SkillRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Owns installed skills, which projects they are enabled in, and who approved running them.
 *
 * <p>Three separate human decisions stand between a marketplace listing and a script running:
 * somebody installs it, somebody enables it for a project, and somebody approves it for a session.
 * An agent can trigger none of them.
 */
@Service
public class SkillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillService.class);
    private static final String INSTALLED = "INSTALLED";

    private final SkillSource skillSource;
    private final SkillInstaller installer;
    private final SkillMapper skillMapper;
    private final SkillApprovalMapper approvalMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param skillSource marketplace
     * @param installer package installer
     * @param skillMapper skill table access
     * @param approvalMapper approval table access
     * @param projectService project lookup
     * @param objectMapper shared JSON mapper
     * @param clock runtime clock
     */
    public SkillService(
            SkillSource skillSource,
            SkillInstaller installer,
            SkillMapper skillMapper,
            SkillApprovalMapper approvalMapper,
            ProjectService projectService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.skillSource = skillSource;
        this.installer = installer;
        this.skillMapper = skillMapper;
        this.approvalMapper = approvalMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Reads what the marketplace offers. Nothing is installed by browsing.
     *
     * @return available packages
     */
    public List<SkillPackage> browse() {
        return skillSource.catalogue();
    }

    /**
     * Installs a package from the marketplace. This is a human action; no agent can reach it.
     *
     * @param skillKey package identifier
     * @return the installed skill
     */
    @Transactional
    public SkillResponse install(String skillKey) {
        SkillPackage skillPackage = skillSource.fetch(skillKey);
        SkillInstaller.Installed installed = installer.install(skillPackage);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));

        SkillRow existing = skillMapper.selectByKey(skillKey);
        SkillRow row = existing == null ? new SkillRow() : existing;
        row.setSkillKey(skillPackage.key());
        row.setName(skillPackage.name());
        row.setVersion(skillPackage.version());
        row.setDescription(skillPackage.description());
        row.setRuntime(skillPackage.runtime().toUpperCase(java.util.Locale.ROOT));
        row.setEntrypoint(skillPackage.entrypoint());
        row.setArgsSchema(writeSchema(skillPackage));
        row.setSourceUrl(skillSource.origin());
        row.setChecksum(installed.checksum());
        row.setInstallPath(installed.packageRoot().toString());
        row.setStatus(INSTALLED);
        row.setUpdatedAt(now);
        if (existing == null) {
            row.setInstalledAt(now);
            skillMapper.insert(row);
        } else {
            skillMapper.updateById(row);
        }
        return toResponse(row);
    }

    /**
     * Removes an installed skill and its files.
     *
     * @param skillKey package identifier
     */
    @Transactional
    public void uninstall(String skillKey) {
        SkillRow row = require(skillKey);
        installer.uninstall(Path.of(row.getInstallPath()));
        skillMapper.deleteById(row.getId());
        LOGGER.info("Uninstalled skill {}", skillKey);
    }

    /**
     * Lists installed skills.
     *
     * @return installed skills, newest first
     */
    public List<SkillResponse> installed() {
        return skillMapper.selectAll().stream().map(SkillService::toResponse).toList();
    }

    /**
     * Lists the skills enabled for a project.
     *
     * @param projectId owning project
     * @return enabled skills
     */
    public List<SkillResponse> enabledFor(long projectId) {
        return skillMapper.selectEnabledForProject(projectId).stream()
                .map(SkillService::toResponse)
                .toList();
    }

    /**
     * Enables or disables a skill for a project.
     *
     * @param projectId owning project
     * @param skillKey package identifier
     * @param enabled whether the skill should be available
     */
    @Transactional
    public void setEnabled(long projectId, String skillKey, boolean enabled) {
        projectService.require(projectId);
        SkillRow row = require(skillKey);
        skillMapper.disableForProject(projectId, row.getId());
        if (enabled) {
            skillMapper.enableForProject(projectId, row.getId());
        }
    }

    /**
     * Records a human decision about running a skill in one session.
     *
     * @param sessionId session the decision applies to
     * @param skillKey package identifier
     * @param approved whether execution is allowed
     * @param decidedBy who decided
     * @param reason safe explanation
     */
    @Transactional
    public void decide(
            String sessionId, String skillKey, boolean approved, String decidedBy, String reason) {
        SkillRow skill = require(skillKey);
        SkillApprovalRow existing = approvalMapper.selectDecision(sessionId, skill.getId());
        SkillApprovalRow row = existing == null ? new SkillApprovalRow() : existing;
        row.setSessionId(sessionId);
        row.setSkillId(skill.getId());
        row.setApproved(approved ? 1 : 0);
        row.setDecidedBy(decidedBy);
        row.setReason(reason);
        row.setDecidedAt(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())));
        if (existing == null) {
            approvalMapper.insert(row);
        } else {
            approvalMapper.updateById(row);
        }
        LOGGER.info("Skill {} {} for session {} by {}",
                skillKey, approved ? "approved" : "refused", sessionId, decidedBy);
    }

    /**
     * Reports whether a skill may run in a session.
     *
     * @param sessionId session the call belongs to
     * @param skillKey package identifier
     * @return whether a human approved it
     */
    public boolean isApproved(String sessionId, String skillKey) {
        SkillRow skill = skillMapper.selectByKey(skillKey);
        if (skill == null) {
            return false;
        }
        SkillApprovalRow decision = approvalMapper.selectDecision(sessionId, skill.getId());
        return decision != null && Integer.valueOf(1).equals(decision.getApproved());
    }

    /**
     * Reads an installed skill.
     *
     * @param skillKey package identifier
     * @return stored skill
     */
    public SkillRow require(String skillKey) {
        SkillRow row = skillMapper.selectByKey(skillKey);
        if (row == null) {
            throw new BusinessException(
                    ErrorCode.PROJECT_NOT_FOUND, "Skill " + skillKey + " is not installed");
        }
        return row;
    }

    private String writeSchema(SkillPackage skillPackage) {
        try {
            return objectMapper.writeValueAsString(skillPackage.argsSchema());
        } catch (JsonProcessingException exception) {
            throw new SkillSourceException("Skill argument schema cannot be stored");
        }
    }

    private static SkillResponse toResponse(SkillRow row) {
        return new SkillResponse(
                row.getId(), row.getSkillKey(), row.getName(), row.getVersion(), row.getDescription(),
                row.getRuntime(), row.getEntrypoint(), row.getSourceUrl(), row.getChecksum(),
                row.getStatus(), row.getInstalledAt());
    }
}
