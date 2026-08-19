package com.devpilot.skill.service;

import com.devpilot.agent.tool.skill.SkillTools;
import com.devpilot.runtime.approval.ApprovalDecision;
import com.devpilot.runtime.approval.ApprovalRequest;
import com.devpilot.runtime.approval.ApprovalService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Decides approval requests, consulting the recorded human decision for skills.
 *
 * <p>Skills are the only capability with a real approval path, because they are the only one that
 * runs downloaded code. Everything else stays refused: the MVP is read-only, and an unrecognised
 * request for permission is not a reason to grant it.
 */
@Component
@Primary
public class SkillAwareApprovalService implements ApprovalService {

    private static final String RESOLVER = "POLICY";

    private final SkillService skillService;

    /**
     * Creates the approval service.
     *
     * @param skillService source of recorded skill decisions
     */
    public SkillAwareApprovalService(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public ApprovalDecision request(ApprovalRequest request) {
        String skillKey = SkillTools.skillKeyOf(request.toolName());
        if (skillKey == null) {
            return ApprovalDecision.rejected(
                    RESOLVER,
                    "The MVP runs read-only; " + request.toolName() + " has no approval path");
        }

        if (skillService.isApproved(request.sessionId(), skillKey)) {
            return ApprovalDecision.approved(
                    "USER", "Skill " + skillKey + " was approved for this session");
        }
        return ApprovalDecision.rejected(
                RESOLVER,
                "Skill " + skillKey + " has not been approved for this session. Approve it at "
                        + "POST /api/v1/sessions/" + request.sessionId() + "/skill-approvals "
                        + "before it can run.");
    }
}
