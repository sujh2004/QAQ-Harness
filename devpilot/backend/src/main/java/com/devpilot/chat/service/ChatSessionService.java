package com.devpilot.chat.service;

import com.devpilot.chat.mapper.ChatMessageMapper;
import com.devpilot.chat.mapper.ChatSessionMapper;
import com.devpilot.chat.model.AgentRunResponse;
import com.devpilot.chat.model.ChatSessionRow;
import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.model.MessageResponse;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.chat.model.TurnResponse;
import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.api.PageResponse;
import com.devpilot.common.exception.BusinessException;
import com.devpilot.config.AppProperties;
import com.devpilot.project.service.ProjectService;
import com.devpilot.runtime.lifecycle.RuntimeIds;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionStreamDescriptor;
import com.devpilot.runtime.stream.SessionEventEnvelope;
import com.devpilot.runtime.tool.ToolDefinition;
import com.devpilot.runtime.tool.ToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Owns chat sessions and the read views built on their event streams.
 *
 * <p>Creating a session opens both the business row and the runtime event stream in one commit, so
 * a session that exists in the project always has a replayable log. Messages, agent runs and raw
 * events are all read from that log rather than from separately maintained state.
 */
@Service
public class ChatSessionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVENT_PAGE = 500;
    private static final String DEFAULT_TITLE = "新对话";

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ProjectService projectService;
    private final SessionLifecycleService lifecycleService;
    private final SessionEventStore eventStore;
    private final ToolRegistry toolRegistry;
    private final Clock clock;
    private final String profileVersion;

    /**
     * Creates the service.
     *
     * @param sessionMapper session table access
     * @param messageMapper message projection access
     * @param projectService project lookup
     * @param lifecycleService runtime lifecycle driver
     * @param eventStore append-only event storage
     * @param toolRegistry registry used to snapshot the capability set of a new session
     * @param clock runtime clock
     * @param appProperties application configuration
     */
    public ChatSessionService(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            ProjectService projectService,
            SessionLifecycleService lifecycleService,
            SessionEventStore eventStore,
            ToolRegistry toolRegistry,
            Clock clock,
            AppProperties appProperties) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.projectService = projectService;
        this.lifecycleService = lifecycleService;
        this.eventStore = eventStore;
        this.toolRegistry = toolRegistry;
        this.clock = clock;
        this.profileVersion = appProperties.runtime().profile().version();
    }

    /**
     * Opens a session and its runtime event stream.
     *
     * @param projectId owning project
     * @param request optional title
     * @return the created session
     */
    @Transactional
    public SessionResponse create(long projectId, CreateSessionRequest request) {
        projectService.require(projectId);

        String sessionId = RuntimeIds.newSessionId();
        String title = request == null || request.title() == null || request.title().isBlank()
                ? DEFAULT_TITLE
                : request.title().trim();
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));

        ChatSessionRow row = new ChatSessionRow();
        row.setId(sessionId);
        row.setProjectId(projectId);
        row.setTitle(title);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        sessionMapper.insert(row);

        lifecycleService.createSession(new SessionStreamDescriptor(
                sessionId, projectId, profileVersion, capabilitySnapshot(), title));

        return SessionResponse.from(row);
    }

    /**
     * Reads one session.
     *
     * @param sessionId session identity
     * @return the session
     * @throws BusinessException when the session does not exist
     */
    @Transactional(readOnly = true)
    public SessionResponse get(String sessionId) {
        return SessionResponse.from(requireSession(sessionId));
    }

    /**
     * Lists the sessions of a project, most recently updated first.
     *
     * @param projectId owning project
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of sessions
     */
    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> list(long projectId, int page, int size) {
        projectService.require(projectId);
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        List<SessionResponse> items = sessionMapper
                .selectByProject(projectId, (long) effectivePage * effectiveSize, effectiveSize)
                .stream()
                .map(SessionResponse::from)
                .toList();
        return PageResponse.of(items, sessionMapper.countByProject(projectId), effectivePage, effectiveSize);
    }

    /**
     * Reads the chat timeline from the message projection.
     *
     * @param sessionId owning session
     * @param page zero-based page index
     * @param size page size, capped at 100
     * @return one page of messages in timeline order
     */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> messages(String sessionId, int page, int size) {
        requireSession(sessionId);
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        List<MessageResponse> items = messageMapper
                .selectBySession(sessionId, (long) effectivePage * effectiveSize, effectiveSize)
                .stream()
                .map(MessageResponse::from)
                .toList();
        return PageResponse.of(
                items, messageMapper.countBySession(sessionId), effectivePage, effectiveSize);
    }

    /**
     * Reads the agent run tree of a session, projected from its event log.
     *
     * @param sessionId owning session
     * @return runs in start order
     */
    @Transactional(readOnly = true)
    public List<AgentRunResponse> runs(String sessionId) {
        requireSession(sessionId);
        return lifecycleService.project(sessionId).runs().stream()
                .map(AgentRunResponse::from)
                .toList();
    }

    /**
     * Replays committed events, which is what an SSE client uses after reconnecting with
     * {@code Last-Event-ID}.
     *
     * @param sessionId owning session
     * @param afterSeq exclusive lower bound
     * @param limit maximum number of events, capped at 500
     * @return committed events in sequence order
     */
    @Transactional(readOnly = true)
    public List<SessionEventEnvelope> events(String sessionId, long afterSeq, int limit) {
        requireSession(sessionId);
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_EVENT_PAGE);
        return eventStore.readAfter(sessionId, Math.max(afterSeq, 0L), effectiveLimit).stream()
                .map(SessionEventEnvelope::from)
                .toList();
    }

    /**
     * Cancels a turn. Cancelling an already finished turn reports its existing outcome.
     *
     * @param sessionId owning session
     * @param turnId turn to cancel
     * @return state of the turn after the call
     */
    @Transactional
    public TurnResponse cancelTurn(String sessionId, String turnId) {
        requireSession(sessionId);
        return TurnResponse.from(lifecycleService.cancelTurn(sessionId, turnId));
    }

    private List<String> capabilitySnapshot() {
        return toolRegistry.registeredTools().stream().map(ToolDefinition::name).toList();
    }

    private ChatSessionRow requireSession(String sessionId) {
        ChatSessionRow row = sessionMapper.selectById(sessionId);
        if (row == null) {
            throw new BusinessException(
                    ErrorCode.SESSION_NOT_FOUND, "Session " + sessionId + " does not exist");
        }
        return row;
    }
}
