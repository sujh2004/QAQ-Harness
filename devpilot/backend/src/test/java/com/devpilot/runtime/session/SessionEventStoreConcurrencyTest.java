package com.devpilot.runtime.session;

import com.devpilot.runtime.lifecycle.RuntimeIds;
import com.devpilot.runtime.session.payload.RuntimeErrorPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract: concurrent appends to one session never share a sequence number, and the resulting
 * stream is gap-free.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionEventStoreConcurrencyTest {

    private static final int THREADS = 8;
    private static final int APPENDS_PER_THREAD = 50;

    @Autowired
    private SessionEventStore eventStore;

    @Test
    void concurrentAppendsProduceUniqueConsecutiveSequenceNumbers() throws Exception {
        String sessionId = newStream();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(THREADS);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int thread = 0; thread < THREADS; thread++) {
                int id = thread;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int index = 0; index < APPENDS_PER_THREAD; index++) {
                            eventStore.append(sessionId, AppendEventCommand.ofSession(
                                    SessionEventType.RUNTIME_ERROR,
                                    new RuntimeErrorPayload("CONTENTION", "worker " + id + " item " + index, "TEST")));
                        }
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failure.get()).isNull();

        List<SessionEvent> events = eventStore.readAll(sessionId);
        // One seed event opened the stream, then every worker appended its own batch.
        int expected = 1 + THREADS * APPENDS_PER_THREAD;
        assertThat(events).hasSize(expected);

        Set<Long> sequences = events.stream().map(SessionEvent::seq).collect(java.util.stream.Collectors.toSet());
        assertThat(sequences).hasSize(expected);
        assertThat(events.getFirst().seq()).isEqualTo(1L);
        assertThat(events.getLast().seq()).isEqualTo(expected);
    }

    @Test
    void appendingToAnUnknownStreamIsRejected() {
        assertThatThrownBy(() -> eventStore.append("session_missing", AppendEventCommand.ofSession(
                SessionEventType.RUNTIME_ERROR, new RuntimeErrorPayload("X", "y", "TEST"))))
                .isInstanceOf(SessionStreamNotFoundException.class);
    }

    @Test
    void openingTheSameStreamTwiceIsRejected() {
        String sessionId = newStream();
        assertThatThrownBy(() -> eventStore.createStream(descriptor(sessionId)))
                .isInstanceOf(SessionStreamAlreadyExistsException.class);
    }

    private String newStream() {
        String sessionId = RuntimeIds.newSessionId();
        eventStore.createStream(descriptor(sessionId));
        eventStore.append(sessionId, AppendEventCommand.ofSession(
                SessionEventType.RUNTIME_ERROR, new RuntimeErrorPayload("SEED", "stream opened", "TEST")));
        return sessionId;
    }

    private static SessionStreamDescriptor descriptor(String sessionId) {
        return new SessionStreamDescriptor(sessionId, 1L, "standard@1", List.of("code", "log"), "concurrency");
    }
}
