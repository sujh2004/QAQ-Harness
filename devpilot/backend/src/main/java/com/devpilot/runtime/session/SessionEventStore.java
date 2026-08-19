package com.devpilot.runtime.session;

import java.util.List;

/**
 * Append-only access to session event streams.
 *
 * <p>This is the single source of run-time truth. Chat history, agent trees, tool call records and
 * SSE frames are projections of what is stored here, so anything that will become visible to the
 * model must be appended first.
 */
public interface SessionEventStore {

    /**
     * Opens a stream and freezes its runtime metadata.
     *
     * @param descriptor session identity and pinned configuration
     * @throws SessionStreamAlreadyExistsException when the session already has a stream
     */
    void createStream(SessionStreamDescriptor descriptor);

    /**
     * Appends events atomically, assigning consecutive sequence numbers.
     *
     * <p>Implementations must serialise allocation per session so concurrent callers never receive
     * the same {@code seq}.
     *
     * @param sessionId target session
     * @param commands events to append, in order
     * @return committed events with their assigned sequence numbers
     * @throws SessionStreamNotFoundException when the session has no stream
     */
    List<SessionEvent> append(String sessionId, List<AppendEventCommand> commands);

    /**
     * Appends a single event.
     *
     * @param sessionId target session
     * @param command event to append
     * @return committed event
     * @throws SessionStreamNotFoundException when the session has no stream
     */
    default SessionEvent append(String sessionId, AppendEventCommand command) {
        return append(sessionId, List.of(command)).getFirst();
    }

    /**
     * Reads the whole stream in sequence order.
     *
     * @param sessionId target session
     * @return committed events ordered by sequence number
     */
    List<SessionEvent> readAll(String sessionId);

    /**
     * Reads a page of the stream, used for replay and SSE reconnection.
     *
     * @param sessionId target session
     * @param afterSeq exclusive lower bound, zero to start from the beginning
     * @param limit maximum number of events to return
     * @return committed events ordered by sequence number
     */
    List<SessionEvent> readAfter(String sessionId, long afterSeq, int limit);

    /**
     * Reports whether a stream exists.
     *
     * @param sessionId target session
     * @return whether the session has a stream
     */
    boolean streamExists(String sessionId);

    /**
     * Finds sessions that contain a turn which was started but never ended, so restart recovery can
     * close them instead of pretending the work is still running.
     *
     * @return session identifiers with at least one open turn
     */
    List<String> findSessionsWithOpenTurns();
}
