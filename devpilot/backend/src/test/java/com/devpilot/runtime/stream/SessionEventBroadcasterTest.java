package com.devpilot.runtime.stream;

import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.payload.UserMessagePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: a subscriber only sees its own session, a slow subscriber is dropped rather than
 * allowed to stall the thread that produced the event, and closing a stream releases it.
 */
class SessionEventBroadcasterTest {

    private final SessionEventBroadcaster broadcaster = new SessionEventBroadcaster();

    @Test
    void deliversOnlyTheEventsOfTheSubscribedSession() throws Exception {
        try (SessionEventBroadcaster.Subscription mine = broadcaster.subscribe("session_a", 16);
                SessionEventBroadcaster.Subscription other = broadcaster.subscribe("session_b", 16)) {

            broadcaster.onEventsCommitted("session_a", List.of(event("session_a", 1)));

            assertThat(poll(mine)).isNotNull().extracting(SessionEvent::seq).isEqualTo(1L);
            assertThat(poll(other)).isNull();
        }
    }

    @Test
    void deliversToEveryStreamWatchingTheSameSession() throws Exception {
        try (SessionEventBroadcaster.Subscription first = broadcaster.subscribe("session_a", 16);
                SessionEventBroadcaster.Subscription second = broadcaster.subscribe("session_a", 16)) {

            broadcaster.onEventsCommitted("session_a", List.of(event("session_a", 7)));

            assertThat(poll(first)).isNotNull();
            assertThat(poll(second)).isNotNull();
        }
    }

    @Test
    void dropsEventsInsteadOfBlockingTheProducer() throws Exception {
        try (SessionEventBroadcaster.Subscription slow = broadcaster.subscribe("session_a", 2)) {
            // Publishing more than the queue holds must return immediately: the alternative is
            // stalling the agent thread that just committed the fact.
            broadcaster.onEventsCommitted("session_a", List.of(
                    event("session_a", 1), event("session_a", 2), event("session_a", 3)));

            assertThat(slow.overflowed()).isTrue();
            // What was dropped is still in the event log; the client recovers it by reconnecting.
            assertThat(poll(slow)).isNotNull();
        }
    }

    @Test
    void anOverflowedSubscriptionStopsAccumulating() throws Exception {
        try (SessionEventBroadcaster.Subscription slow = broadcaster.subscribe("session_a", 1)) {
            broadcaster.onEventsCommitted("session_a", List.of(event("session_a", 1), event("session_a", 2)));
            assertThat(slow.overflowed()).isTrue();

            poll(slow);
            broadcaster.onEventsCommitted("session_a", List.of(event("session_a", 3)));

            // Once a stream is doomed to reconnect, queueing more for it is wasted memory.
            assertThat(poll(slow)).isNull();
        }
    }

    @Test
    void closingReleasesTheSubscription() {
        SessionEventBroadcaster.Subscription subscription = broadcaster.subscribe("session_a", 4);
        assertThat(broadcaster.subscriberCount("session_a")).isEqualTo(1);

        subscription.close();

        assertThat(broadcaster.subscriberCount("session_a")).isZero();
        // Publishing to a session nobody watches must not fail.
        broadcaster.onEventsCommitted("session_a", List.of(event("session_a", 1)));
    }

    private static SessionEvent poll(SessionEventBroadcaster.Subscription subscription)
            throws InterruptedException {
        return subscription.poll(50, TimeUnit.MILLISECONDS);
    }

    private static SessionEvent event(String sessionId, long seq) {
        return new SessionEvent(seq, sessionId, seq, SessionEventType.USER_MESSAGE, 1,
                "turn_1", null, null, null, new UserMessagePayload("测试"), Instant.EPOCH);
    }
}
