package com.devpilot.runtime.session;

import java.util.List;

/**
 * Receives events after they are committed to the log.
 *
 * <p>This is how read projections such as {@code chat_message} stay current without becoming a
 * second source of truth. Listeners run after commit, so a listener failure can never roll back a
 * recorded fact; the price is that a projection may lag, which is why every projection must also be
 * rebuildable from the stream.
 */
@FunctionalInterface
public interface SessionEventListener {

    /**
     * Handles a batch of committed events.
     *
     * @param sessionId owning session
     * @param events events committed together, in sequence order
     */
    void onEventsCommitted(String sessionId, List<SessionEvent> events);
}
