package com.devpilot.skill.controller;

import com.devpilot.agent.tool.skill.SkillTools;
import com.devpilot.common.api.Result;
import com.devpilot.skill.model.SkillPackage;
import com.devpilot.skill.model.SkillResponse;
import com.devpilot.skill.service.SkillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Skill marketplace and installation endpoints.
 *
 * <p>Every state change here is a human action on purpose. No agent can browse, install, enable or
 * approve a skill — those are exactly the decisions that stand between a marketplace listing and
 * downloaded code running on this machine.
 */
@RestController
@RequestMapping("/api/v1")
public class SkillController {

    private final SkillService skillService;
    private final SkillTools skillTools;

    /**
     * Creates the controller.
     *
     * @param skillService skill application service
     * @param skillTools tool publisher, so a newly installed skill becomes visible immediately
     */
    public SkillController(SkillService skillService, SkillTools skillTools) {
        this.skillService = skillService;
        this.skillTools = skillTools;
    }

    /**
     * Reads the marketplace catalogue. Nothing is installed by browsing.
     *
     * @return available packages
     */
    @GetMapping("/skills/marketplace")
    public Result<List<SkillPackage>> marketplace() {
        return Result.success(skillService.browse());
    }

    /**
     * Lists installed skills.
     *
     * @return installed skills
     */
    @GetMapping("/skills")
    public Result<List<SkillResponse>> installed() {
        return Result.success(skillService.installed());
    }

    /**
     * Installs a package from the marketplace.
     *
     * @param request package to install
     * @return the installed skill
     */
    @PostMapping("/skills")
    public Result<SkillResponse> install(@Valid @RequestBody InstallSkillRequest request) {
        SkillResponse installed = skillService.install(request.skillKey());
        skillTools.publish(skillService.require(request.skillKey()));
        return Result.success(installed);
    }

    /**
     * Removes an installed skill and its files.
     *
     * @param skillKey package identifier
     * @return empty success response
     */
    @DeleteMapping("/skills/{skillKey}")
    public Result<Void> uninstall(@PathVariable String skillKey) {
        skillService.uninstall(skillKey);
        return Result.success(null);
    }

    /**
     * Lists the skills enabled for a project.
     *
     * @param projectId owning project
     * @return enabled skills
     */
    @GetMapping("/projects/{projectId}/skills")
    public Result<List<SkillResponse>> enabled(@PathVariable long projectId) {
        return Result.success(skillService.enabledFor(projectId));
    }

    /**
     * Enables or disables a skill for a project.
     *
     * @param projectId owning project
     * @param request skill and desired state
     * @return skills enabled after the change
     */
    @PostMapping("/projects/{projectId}/skills")
    public Result<List<SkillResponse>> setEnabled(
            @PathVariable long projectId, @Valid @RequestBody EnableSkillRequest request) {
        skillService.setEnabled(projectId, request.skillKey(), Boolean.TRUE.equals(request.enabled()));
        return Result.success(skillService.enabledFor(projectId));
    }

    /**
     * Records a decision about running a skill in one session.
     *
     * @param sessionId session the decision applies to
     * @param request skill and decision
     * @return empty success response
     */
    @PostMapping("/sessions/{sessionId}/skill-approvals")
    public Result<Void> approve(
            @PathVariable String sessionId, @Valid @RequestBody ApproveSkillRequest request) {
        skillService.decide(
                sessionId,
                request.skillKey(),
                Boolean.TRUE.equals(request.approved()),
                request.decidedBy() == null ? "USER" : request.decidedBy(),
                request.reason());
        return Result.success(null);
    }

    /**
     * Request body for installing a package.
     *
     * @param skillKey package identifier
     */
    public record InstallSkillRequest(@NotBlank @Size(max = 100) String skillKey) {
    }

    /**
     * Request body for enabling a skill in a project.
     *
     * @param skillKey package identifier
     * @param enabled whether the skill should be available
     */
    public record EnableSkillRequest(
            @NotBlank @Size(max = 100) String skillKey, @NotNull Boolean enabled) {
    }

    /**
     * Request body for approving a skill in a session.
     *
     * @param skillKey package identifier
     * @param approved whether execution is allowed
     * @param decidedBy who decided
     * @param reason safe explanation
     */
    public record ApproveSkillRequest(
            @NotBlank @Size(max = 100) String skillKey,
            @NotNull Boolean approved,
            @Size(max = 100) String decidedBy,
            @Size(max = 500) String reason) {
    }
}
