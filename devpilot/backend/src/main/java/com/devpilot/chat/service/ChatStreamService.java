package com.devpilot.chat.service;

import com.devpilot.agent.runtime.AgentRuntime;
import com.devpilot.agent.runtime.AgentTurnRequest;
import com.devpilot.agent.runtime.AgentTurnResult;
import com.devpilot.chat.model.ChatStreamRequest;
import com.devpilot.chat.model.CreateSessionRequest;
import com.devpilot.chat.model.SessionResponse;
import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.exception.BusinessException;
import com.devpilot.config.AppProperties;
import com.devpilot.runtime.lifecycle.RunStatus;
import com.devpilot.runtime.lifecycle.SessionLifecycleService;
import com.devpilot.runtime.lifecycle.TurnEndReason;
import com.devpilot.runtime.projection.TurnView;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.stream.SessionEventBroadcaster;
import com.devpilot.runtime.stream.SessionEventSseCodec;
import com.devpilot.runtime.stream.SseFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Drives one streaming chat connection.
 *
 * <p>SSE here is a projection of the committed event log, never a second channel. Every frame comes
 * from an event that is already durable, and its sequence number is the SSE {@code id}, so a client
 * that reconnects with {@code Last-Event-ID} receives exactly what it missed — no more, no less.
 *
 * <p>The order of operations is the whole design:
 *
 * <ol>
 *   <li>subscribe to the live feed <em>first</em>, so nothing committed from now on can be missed;
 *   <li>replay committed events after the client's last seen sequence number;
 *   <li>switch to the live feed, dropping anything already sent.
 * </ol>
 *
 * <p>Subscribing before replaying closes the window where an event lands between the query and the
 * subscription. Sending strictly increasing sequence numbers makes the client's job idempotent by
 * construction rather than by convention.
 */
@Service
public class ChatStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamService.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final ChatSessionService chatSessionService;
    private final SessionLifecycleService lifecycleService;
    private final SessionEventStore eventStore;
    private final SessionEventBroadcaster broadcaster;
    private final SessionEventSseCodec sseCodec;
    private final AgentRuntime agentRuntime;
    private final ExecutorService streamExecutor;
    private final ExecutorService turnExecutor;
    private final AppProperties.RuntimeSettings.Chat settings;

    /**
     * Creates the service.
     *
     * @param chatSessionService session lookup and creation
     * @param lifecycleService turn lifecycle driver
     * @param eventStore committed event log, used for replay
     * @param broadcaster live event feed
     * @param sseCodec encoder shared by replay and live frames
     * @param agentRuntime agent loop
     * @param streamExecutor pool running the replay and pump of open streams
     * @param turnExecutor pool running agent turns
     * @param appProperties application configuration supplying the streaming limits
     */
    public ChatStreamService(
            ChatSessionService chatSessionService,
            SessionLifecycleService lifecycleService,
            SessionEventStore eventStore,
            SessionEventBroadcaster broadcaster,
            SessionEventSseCodec sseCodec,
            AgentRuntime agentRuntime,
            @org.springframework.beans.factory.annotation.Qualifier("chatStreamExecutor")
            ExecutorService chatStreamExecutor,
            @org.springframework.beans.factory.annotation.Qualifier("chatTurnExecutor")
            ExecutorService chatTurnExecutor,
            AppProperties appProperties) {
        this.chatSessionService = chatSessionService;
        this.lifecycleService = lifecycleService;
        this.eventStore = eventStore;
        this.broadcaster = broadcaster;
        this.sseCodec = sseCodec;
        this.agentRuntime = agentRuntime;
        this.streamExecutor = chatStreamExecutor;
        this.turnExecutor = chatTurnExecutor;
        this.settings = appProperties.runtime().chat();
    }

    /**
     * Opens a stream, optionally starting a turn.
     *
     * @param request project, session and question
     * @param lastEventId sequence number the client already has, zero for a fresh stream
     * @return emitter the container writes to the client
     */
    public SseEmitter open(ChatStreamRequest request, long lastEventId) {
        SessionResponse session = resolveSession(request);
        String sessionId = session.sessionId();

        SseEmitter emitter = new SseEmitter(settings.streamTimeout().toMillis());
        SessionEventBroadcaster.Subscription subscription =
                broadcaster.subscribe(sessionId, settings.queueCapacity());

        // The container reports the client leaving on any of these; the subscription must go with it
        // or a closed browser tab would keep a queue filling forever.
        emitter.onCompletion(subscription::close);
        emitter.onTimeout(subscription::close);
        emitter.onError(error -> subscription.close());

        try {
            streamExecutor.execute(() -> pump(emitter, subscription, session, request, lastEventId));
        } catch (RejectedExecutionException exception) {
            subscription.close();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Too many open chat streams; retry in a moment");
        }
        return emitter;
    }

    /**
     * Resolves the session a stream runs in and proves it belongs to the requested project.
     *
     * @param request incoming request
     * @return the session to stream
     */
    private SessionResponse resolveSession(ChatStreamRequest request) {
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            return chatSessionService.create(request.projectId(), new CreateSessionRequest(null));
        }
        SessionResponse session = chatSessionService.get(request.sessionId());
        if (!session.projectId().equals(request.projectId())) {
            // Cross-project access is refused here rather than deeper down, because everything below
            // this point trusts the session to decide what the agent may read.
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND,
                    "Session " + request.sessionId() + " does not belong to project " + request.projectId());
        }
        return session;
    }

    private void pump(
            SseEmitter emitter,
            SessionEventBroadcaster.Subscription subscription,
            SessionResponse session,
            ChatStreamRequest request,
            long lastEventId) {

        String sessionId = session.sessionId();
        long lastSentSeq = Math.max(lastEventId, 0);
        try {
            String targetTurnId = request.startsTurn()
                    ? startTurn(session, request)
                    : lifecycleService.project(sessionId).activeTurn().map(TurnView::turnId).orElse(null);

            Replayed replayed = replay(emitter, sessionId, lastSentSeq, targetTurnId);
            lastSentSeq = replayed.lastSeq();
            if (targetTurnId == null || replayed.followedTurnEnded()) {
                // Nothing is left to wait for: either the client only asked to catch up, or the turn
                // it follows already finished within what was just replayed.
                emitter.complete();
                return;
            }

            follow(emitter, subscription, targetTurnId, lastSentSeq);
            emitter.complete();
        } catch (IOException exception) {
            // A browser that navigated away is the normal end of a stream, not a failure.
            LOGGER.debug("Chat stream of session {} closed by the client", sessionId);
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            LOGGER.error("Chat stream of session {} failed", sessionId, exception);
            emitter.completeWithError(exception);
        } finally {
            subscription.close();
        }
    }

    /**
     * Opens the turn and hands the agent loop to its own pool.
     *
     * <p>The turn is started on this thread so the stream knows which turn to follow before it
     * begins reading; the agent runs elsewhere so a long answer never blocks the pump.
     *
     * @param session session the turn belongs to
     * @param request incoming request carrying the question
     * @return identity of the started turn
     */
    private String startTurn(SessionResponse session, ChatStreamRequest request) {
        String turnId = lifecycleService.startTurn(session.sessionId(), "USER", request.message());
        try {
            turnExecutor.execute(() -> runTurn(session, turnId, request.message()));
        } catch (RejectedExecutionException exception) {
            // Refusing loudly beats queueing without bound: the turn is closed as failed, and the
            // client sees a turn_ended frame rather than a stream that never produces anything.
            lifecycleService.endTurn(session.sessionId(), turnId, TurnEndReason.FAILED,
                    "Too many concurrent chat turns; retry in a moment");
        }
        return turnId;
    }

    private void runTurn(SessionResponse session, String turnId, String message) {
        try {
            AgentTurnResult result = agentRuntime.runTurn(AgentTurnRequest.of(
                    session.sessionId(), session.projectId(), turnId, settings.agent(), message));
            lifecycleService.endTurn(
                    session.sessionId(),
                    turnId,
                    result.status() == RunStatus.COMPLETED ? TurnEndReason.COMPLETED : TurnEndReason.FAILED,
                    result.status() == RunStatus.COMPLETED ? "answered" : String.valueOf(result.errorMessage()));
        } catch (RuntimeException exception) {
            LOGGER.error("Chat turn {} of session {} failed", turnId, session.sessionId(), exception);
            // The turn must close whatever happened, or restart recovery would have to clean it up.
            endTurnQuietly(session.sessionId(), turnId, exception);
        }
    }

    private void endTurnQuietly(String sessionId, String turnId, RuntimeException cause) {
        try {
            lifecycleService.endTurn(sessionId, turnId, TurnEndReason.FAILED, String.valueOf(cause.getMessage()));
        } catch (RuntimeException exception) {
            LOGGER.error("Turn {} of session {} could not be closed", turnId, sessionId, exception);
        }
    }

    /**
     * Sends every committed event the client has not seen.
     *
     * <p>Whether the followed turn ended is decided here rather than from a projection: the
     * subscription was opened before this read, so an event missing from the replay is guaranteed to
     * arrive on the live feed. Asking a projection instead would let a turn that ended between the
     * read and the question close the stream with its {@code turn_ended} frame still undelivered.
     *
     * @param emitter open stream
     * @param sessionId session being streamed
     * @param afterSeq exclusive lower bound
     * @param followedTurnId turn the stream follows, may be null
     * @return how far the replay got and whether it already contained the end of that turn
     * @throws IOException when the client is gone
     */
    private Replayed replay(SseEmitter emitter, String sessionId, long afterSeq, String followedTurnId)
            throws IOException {
        long cursor = afterSeq;
        boolean turnEnded = false;
        while (true) {
            List<SessionEvent> page = eventStore.readAfter(sessionId, cursor, settings.replayLimit());
            for (SessionEvent event : page) {
                send(emitter, event);
                cursor = event.seq();
                if (event.eventType() == SessionEventType.TURN_ENDED
                        && event.turnId() != null && event.turnId().equals(followedTurnId)) {
                    turnEnded = true;
                }
            }
            if (page.size() < settings.replayLimit()) {
                return new Replayed(cursor, turnEnded);
            }
        }
    }

    /**
     * How far a replay got.
     *
     * @param lastSeq sequence number of the last event sent
     * @param followedTurnEnded whether the replay already carried the end of the followed turn
     */
    private record Replayed(long lastSeq, boolean followedTurnEnded) {
    }

    /**
     * Streams live events until the followed turn ends.
     *
     * @param emitter open stream
     * @param subscription live feed
     * @param turnId turn whose end closes the stream
     * @param alreadySentSeq highest sequence number already written
     * @throws IOException when the client is gone
     * @throws InterruptedException when the pump is interrupted
     */
    private void follow(
            SseEmitter emitter,
            SessionEventBroadcaster.Subscription subscription,
            String turnId,
            long alreadySentSeq) throws IOException, InterruptedException {

        long lastSentSeq = alreadySentSeq;
        while (true) {
            if (subscription.overflowed()) {
                // Ending the stream is the recovery: the client reconnects with Last-Event-ID and
                // replays what it missed from the log, which still holds every event.
                LOGGER.info("Ending stream of session {} early so the client can replay from seq {}",
                        subscription.sessionId(), lastSentSeq);
                return;
            }

            SessionEvent event = subscription.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
            if (event == null) {
                // A comment keeps proxies from closing an idle connection and surfaces a dead client
                // as an IOException here rather than as a stream that never ends.
                emitter.send(SseEmitter.event().comment("keep-alive"));
                continue;
            }
            if (event.seq() <= lastSentSeq) {
                // Already delivered during replay: the client must never render an event twice.
                continue;
            }

            send(emitter, event);
            lastSentSeq = event.seq();

            if (event.eventType() == SessionEventType.TURN_ENDED && turnId.equals(event.turnId())) {
                return;
            }
        }
    }

    private void send(SseEmitter emitter, SessionEvent event) throws IOException {
        SseFrame frame = sseCodec.encode(event);
        emitter.send(SseEmitter.event()
                .id(frame.id())
                .name(frame.event())
                .data(frame.data()));
    }
}
