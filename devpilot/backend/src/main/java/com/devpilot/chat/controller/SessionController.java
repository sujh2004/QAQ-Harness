package com.devpilot.chat.controller;

import com.devpilot.chat.model.AgentRunResponse;
import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.model.MessageResponse;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.chat.model.TurnResponse;
import com.devpilot.chat.service.ChatSessionService;
import com.devpilot.common.api.PageResponse;
import com.devpilot.common.api.Result;
import com.devpilot.runtime.stream.SessionEventEnvelope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Session endpoints.
 *
 * <p>Streaming chat is added in a later phase; what exists here is session management plus the read
 * views of the event log the UI needs to render and resume a conversation.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final ChatSessionService chatSessionService;

    /**
     * Creates the controller.
     *
     * @param chatSessionService session application service
     */
    public SessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * Opens a session in a project.
     *
     * @param projectId owning project
     * @param request optional title
     * @return the created session
     */
    @PostMapping("/projects/{projectId}/sessions")
    public Result<SessionResponse> create(
            @PathVariable long projectId,
            @Valid @RequestBody(required = false) CreateSessionRequest request) {
        return Result.success(chatSessionService.create(projectId, request));
    }

    /**
     * Lists the sessions of a project.
     *
     * @param projectId owning project
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of sessions
     */
    @GetMapping("/projects/{projectId}/sessions")
    public Result<PageResponse<SessionResponse>> list(
            @PathVariable long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatSessionService.list(projectId, page, size));
    }

    /**
     * Reads the chat timeline of a session.
     *
     * @param sessionId owning session
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of messages
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<PageResponse<MessageResponse>> messages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.success(chatSessionService.messages(sessionId, page, size));
    }

    /**
     * Reads the agent run tree of a session.
     *
     * @param sessionId owning session
     * @return runs in start order
     */
    @GetMapping("/sessions/{sessionId}/runs")
    public Result<List<AgentRunResponse>> runs(@PathVariable String sessionId) {
        return Result.success(chatSessionService.runs(sessionId));
    }

    /**
     * Replays committed events, used to resume after a dropped stream.
     *
     * @param sessionId owning session
     * @param afterSeq exclusive lower bound
     * @param limit maximum number of events, capped at 500
     * @return committed events in sequence order
     */
    @GetMapping("/sessions/{sessionId}/events")
    public Result<List<SessionEventEnvelope>> events(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "500") int limit) {
        return Result.success(chatSessionService.events(sessionId, afterSeq, limit));
    }

    /**
     * Cancels a turn. The call is idempotent.
     *
     * @param sessionId owning session
     * @param turnId turn to cancel
     * @return state of the turn after the call
     */
    @PostMapping("/sessions/{sessionId}/turns/{turnId}/cancel")
    public Result<TurnResponse> cancelTurn(
            @PathVariable String sessionId, @PathVariable String turnId) {
        return Result.success(chatSessionService.cancelTurn(sessionId, turnId));
    }
}
