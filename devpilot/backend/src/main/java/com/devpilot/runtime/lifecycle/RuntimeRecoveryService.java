package com.devpilot.runtime.lifecycle;

import com.devpilot.config.AppProperties;
import com.devpilot.runtime.projection.TurnView;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.UnsupportedEventSchemaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Closes lifecycle state that a previous process left open.
 *
 * <p>A turn that was running when the service stopped has no way to continue, so recovery ends it
 * with {@link TurnEndReason#ABORTED_BY_RESTART} instead of leaving the UI showing work that nobody
 * is doing. Closing a turn also closes the tool calls, agent runs and steps it left open.
 */
@Component
public class RuntimeRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeRecoveryService.class);
    private static final String DETAIL = "Closed by restart recovery";

    private final SessionEventStore eventStore;
    private final SessionLifecycleService lifecycleService;
    private final boolean enabledOnStartup;

    /**
     * Creates the recovery service.
     *
     * @param eventStore append-only event storage
     * @param lifecycleService lifecycle driver used to append the closing events
     * @param appProperties application configuration
     */
    public RuntimeRecoveryService(
            SessionEventStore eventStore,
            SessionLifecycleService lifecycleService,
            AppProperties appProperties) {
        this.eventStore = eventStore;
        this.lifecycleService = lifecycleService;
        this.enabledOnStartup = appProperties.runtime().recovery().enabled();
    }

    /** Runs recovery once the application is ready, unless it is disabled by configuration. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!enabledOnStartup) {
            LOGGER.info("Runtime recovery is disabled; dangling turns will not be closed automatically");
            return;
        }
        RuntimeRecoveryReport report = recover();
        LOGGER.info("Runtime recovery finished: scanned {} session(s), closed {} turn(s), {} unreadable",
                report.scannedSessions(), report.closedTurns(), report.failedSessions());
    }

    /**
     * Closes every turn that was started but never ended.
     *
     * <p>A session whose stream this build cannot decode is reported rather than silently skipped:
     * an unknown critical event means the recovered state would not match what the model saw.
     *
     * @return what the pass scanned and closed
     */
    public RuntimeRecoveryReport recover() {
        List<String> sessionIds = eventStore.findSessionsWithOpenTurns();
        int closedTurns = 0;
        int failedSessions = 0;

        for (String sessionId : sessionIds) {
            try {
                List<TurnView> openTurns = lifecycleService.project(sessionId).turns().stream()
                        .filter(turn -> !turn.status().terminal())
                        .toList();
                for (TurnView turn : openTurns) {
                    lifecycleService.endTurn(sessionId, turn.turnId(), TurnEndReason.ABORTED_BY_RESTART, DETAIL);
                    closedTurns++;
                }
            } catch (UnsupportedEventSchemaException exception) {
                failedSessions++;
                LOGGER.error("Cannot recover session {}: its event stream uses a schema this build does not "
                        + "understand", sessionId, exception);
            }
        }
        return new RuntimeRecoveryReport(sessionIds.size(), closedTurns, failedSessions);
    }
}
