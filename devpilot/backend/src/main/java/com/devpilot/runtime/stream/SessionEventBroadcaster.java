package com.devpilot.runtime.stream;

import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fans committed events out to the streams currently watching a session.
 *
 * <p>This is the live half of the SSE contract; the replay half is {@code readAfter} on the event
 * store. Both encode the same committed events, so a client cannot tell from a frame whether it
 * arrived live or after a reconnect — which is what makes {@code Last-Event-ID} recovery exact.
 *
 * <p>Delivery must never slow down or fail the append path: this listener runs after commit, on the
 * thread that produced the fact. Each subscription therefore holds a bounded queue and a full queue
 * is <em>not</em> waited on. The subscription is marked overflowed and closed instead, and the
 * client reconnects with {@code Last-Event-ID} to collect exactly what it missed. A slow browser
 * costs that browser a reconnect, never the agent its progress.
 *
 * <p>Subscriptions are per-process. A multi-instance deployment needs a shared bus (Redis pub/sub)
 * between the store and this class; the client protocol does not change, because replay already
 * covers anything the live path drops.
 */
@Component
public class SessionEventBroadcaster implements SessionEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionEventBroadcaster.class);

    private final Map<String, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void onEventsCommitted(String sessionId, List<SessionEvent> events) {
        Set<Subscription> watchers = subscriptions.get(sessionId);
        if (watchers == null || watchers.isEmpty()) {
            return;
        }
        for (Subscription subscription : watchers) {
            subscription.offer(events);
        }
    }

    /**
     * Starts watching one session.
     *
     * <p>Callers subscribe <em>before</em> reading the committed history they want to replay, and
     * then drop queued events they have already sent. Subscribing first closes the window in which
     * an event is committed after the replay query and before the live stream exists.
     *
     * @param sessionId session to watch
     * @param queueCapacity how many events may wait for a slow client before it is told to reconnect
     * @return an open subscription; close it when the stream ends
     */
    public Subscription subscribe(String sessionId, int queueCapacity) {
        Subscription subscription = new Subscription(sessionId, queueCapacity, this::unsubscribe);
        subscriptions.computeIfAbsent(sessionId, id -> new CopyOnWriteArraySet<>()).add(subscription);
        return subscription;
    }

    /**
     * Reports how many streams are watching a session, for tests and diagnostics.
     *
     * @param sessionId session to inspect
     * @return number of open subscriptions
     */
    public int subscriberCount(String sessionId) {
        Set<Subscription> watchers = subscriptions.get(sessionId);
        return watchers == null ? 0 : watchers.size();
    }

    private void unsubscribe(Subscription subscription) {
        subscriptions.computeIfPresent(subscription.sessionId(), (id, watchers) -> {
            watchers.remove(subscription);
            return watchers.isEmpty() ? null : watchers;
        });
    }

    /** One stream's view of a session's live events. */
    public static final class Subscription implements AutoCloseable {

        private final String sessionId;
        private final BlockingQueue<SessionEvent> queue;
        private final java.util.function.Consumer<Subscription> onClose;
        private final AtomicBoolean overflowed = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Subscription(
                String sessionId, int queueCapacity, java.util.function.Consumer<Subscription> onClose) {
            this.sessionId = sessionId;
            this.queue = new ArrayBlockingQueue<>(Math.max(queueCapacity, 1));
            this.onClose = onClose;
        }

        /** @return session this subscription watches */
        public String sessionId() {
            return sessionId;
        }

        /**
         * Reports whether the client fell too far behind to be served live.
         *
         * <p>An overflowed subscription is not a lost event: the events are still in the log, and
         * the client recovers them by reconnecting with {@code Last-Event-ID}.
         *
         * @return whether events were dropped
         */
        public boolean overflowed() {
            return overflowed.get();
        }

        /**
         * Waits for the next live event.
         *
         * @param timeout how long to wait
         * @param unit unit of the timeout
         * @return the next event, or null when the wait elapsed
         * @throws InterruptedException when the waiting thread is interrupted
         */
        public SessionEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        private void offer(List<SessionEvent> events) {
            if (closed.get() || overflowed.get()) {
                return;
            }
            for (SessionEvent event : events) {
                if (!queue.offer(event)) {
                    // Dropping is deliberate: blocking here would stall the agent that produced the
                    // event. The client is told to reconnect and replay instead.
                    overflowed.set(true);
                    LOGGER.warn("SSE subscriber of session {} fell behind at seq {}; it must reconnect "
                            + "with Last-Event-ID", sessionId, event.seq());
                    return;
                }
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                onClose.accept(this);
                queue.clear();
            }
        }
    }
}
