package com.devpilot.agent.controller;

import com.devpilot.agent.runtime.AgentRuntime;
import com.devpilot.agent.runtime.AgentTurnRequest;
import com.devpilot.agent.runtime.AgentTurnResult;
import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.common.api.Result;
import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs one specialist agent directly, for development and acceptance testing.
 *
 * <p>Restricted to the development and demo profiles: a production deployment reaches agents
 * through the supervised chat flow, not by naming one. The endpoint owns the whole turn — it opens
 * it, runs the agent and closes it — so a manual probe cannot leave a session half-open.
 */
@RestController
@RequestMapping("/api/v1/debug/agents")
@Profile({"dev", "demo"})
public class AgentDebugController {

    private final AgentRuntime agentRuntime;
    private final ChatSessionService chatSessionService;
    private final SessionLifecycleService lifecycleService;

    /**
     * Creates the controller.
     *
     * @param agentRuntime agent loop
     * @param chatSessionService session service, used when the caller supplies no session
     * @param lifecycleService lifecycle driver
     */
    public AgentDebugController(
            AgentRuntime agentRuntime,
            ChatSessionService chatSessionService,
            SessionLifecycleService lifecycleService) {
        this.agentRuntime = agentRuntime;
        this.chatSessionService = chatSessionService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Runs one agent over a single question.
     *
     * @param agentName agent declared by the active profile, for example {@code log_agent}
     * @param request project, question and optional session
     * @return what the agent produced, plus the session it ran in
     */
    @PostMapping("/{agentName}")
    public Result<AgentDebugResponse> run(
            @PathVariable String agentName, @Valid @RequestBody AgentDebugRequest request) {

        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? chatSessionService.create(
                        request.projectId(), new CreateSessionRequest("debug " + agentName)).sessionId()
                : request.sessionId();

        String turnId = lifecycleService.startTurn(sessionId, "DEBUG", request.message());
        AgentTurnResult result = agentRuntime.runTurn(new AgentTurnRequest(
                sessionId, request.projectId(), turnId, agentName, request.message()));

        lifecycleService.endTurn(
                sessionId,
                turnId,
                result.status() == RunStatus.COMPLETED ? TurnEndReason.COMPLETED : TurnEndReason.FAILED,
                result.status() == RunStatus.COMPLETED ? "answered" : String.valueOf(result.errorMessage()));

        return Result.success(new AgentDebugResponse(
                sessionId, turnId, result.runId(), result.status(),
                result.finalMessage(), result.errorMessage()));
    }

    /**
     * Request body of a manual agent run.
     *
     * @param projectId project the agent may read
     * @param message the question
     * @param sessionId existing session to continue, omit to open a new one
     */
    public record AgentDebugRequest(
            @jakarta.validation.constraints.NotNull Long projectId,
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 64) String sessionId) {
    }

    /**
     * Result of a manual agent run.
     *
     * @param sessionId session the run happened in
     * @param turnId turn the run happened in
     * @param runId agent run identifier
     * @param status terminal run status
     * @param answer visible answer, null when the run failed
     * @param error safe failure message, null when the run succeeded
     */
    public record AgentDebugResponse(
            String sessionId,
            String turnId,
            String runId,
            RunStatus status,
            String answer,
            String error) {
    }
}
