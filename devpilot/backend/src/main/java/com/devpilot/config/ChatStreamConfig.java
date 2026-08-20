package com.devpilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread pools behind the streaming chat endpoint.
 *
 * <p>Two pools rather than one, because the two jobs fail differently. A chat turn is expensive and
 * bounded work: too many at once means an overloaded model budget, so the pool refuses rather than
 * queues, and the caller is told immediately. Pumping events to a browser is cheap but long-lived:
 * refusing there would drop a connection that costs almost nothing to keep, so that pool has room
 * for readers who are only watching a session someone else started.
 *
 * <p>Both are bounded. An unbounded pool would turn a burst of browser tabs into a thread leak.
 */
@Configuration
public class ChatStreamConfig {

    private static final long IDLE_SECONDS = 60;

    /**
     * Pool running the replay and live pump of open streams.
     *
     * @param appProperties application configuration supplying the streaming limits
     * @return bounded pool sized for readers as well as askers
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService chatStreamExecutor(AppProperties appProperties) {
        int turns = appProperties.runtime().chat().maxConcurrentTurns();
        // Watchers outnumber askers: everyone following a session holds a pump thread while only the
        // one who asked holds a turn thread.
        int size = Math.max(turns * 4, 8);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size, size, IDLE_SECONDS, TimeUnit.SECONDS, new SynchronousQueue<>(),
                namedFactory("chat-stream"), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Pool running agent turns started from the chat endpoint.
     *
     * @param appProperties application configuration supplying the streaming limits
     * @return bounded pool with a small waiting room
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService chatTurnExecutor(AppProperties appProperties) {
        int turns = Math.max(appProperties.runtime().chat().maxConcurrentTurns(), 1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                turns, turns, IDLE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<>(turns),
                namedFactory("chat-turn"), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static java.util.concurrent.ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
