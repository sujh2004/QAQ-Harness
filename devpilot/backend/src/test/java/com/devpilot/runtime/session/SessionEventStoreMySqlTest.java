package com.devpilot.runtime.session;

import com.devpilot.runtime.RuntimeTestFixtures;
import com.devpilot.runtime.lifecycle.RuntimeIds;
import com.devpilot.runtime.session.payload.RuntimeErrorPayload;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sequence-allocation contract against a real MySQL 8, using the canonical
 * {@code sql/schema.sql}.
 *
 * <p>H2 approximates InnoDB row locking well enough for the everyday build, so this test is tagged
 * {@code mysql} and excluded by default. Run it with {@code mvn test -Pmysql-it} when Docker is
 * available.
 */
@SpringBootTest
@ActiveProfiles("mysql-it")
@Testcontainers
@Tag("mysql")
class SessionEventStoreMySqlTest {

    private static final int THREADS = 8;
    private static final int APPENDS_PER_THREAD = 50;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("devpilot")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(canonicalSchema()), "/docker-entrypoint-initdb.d/schema.sql");

    @Autowired
    private SessionEventStore eventStore;

    @Test
    void concurrentAppendsProduceUniqueConsecutiveSequenceNumbers() throws Exception {
        String sessionId = RuntimeIds.newSessionId();
        eventStore.createStream(RuntimeTestFixtures.descriptor(sessionId, "mysql-concurrency"));

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
            assertThat(finished.await(120, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failure.get()).isNull();

        List<SessionEvent> events = eventStore.readAll(sessionId);
        int expected = THREADS * APPENDS_PER_THREAD;
        assertThat(events).hasSize(expected);
        Set<Long> sequences = events.stream().map(SessionEvent::seq).collect(Collectors.toSet());
        assertThat(sequences).hasSize(expected);
        assertThat(events.getLast().seq()).isEqualTo(expected);
    }

    private static String canonicalSchema() {
        return Path.of("..", "sql", "schema.sql").toAbsolutePath().normalize().toString();
    }
}
