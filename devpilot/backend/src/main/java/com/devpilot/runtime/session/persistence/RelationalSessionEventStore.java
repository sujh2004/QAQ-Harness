package com.devpilot.runtime.session.persistence;

import com.devpilot.runtime.session.AppendEventCommand;
import com.devpilot.runtime.session.SessionEvent;
import com.devpilot.runtime.session.SessionEventListener;
import com.devpilot.runtime.session.SessionEventPayloadCodec;
import com.devpilot.runtime.session.SessionEventStore;
import com.devpilot.runtime.session.SessionEventType;
import com.devpilot.runtime.session.SessionStreamAlreadyExistsException;
import com.devpilot.runtime.session.SessionStreamDescriptor;
import com.devpilot.runtime.session.SessionStreamNotFoundException;
import com.devpilot.runtime.session.UnsupportedEventSchemaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Relational implementation of the session event store, used against MySQL in production and H2 in
 * the contract tests.
 *
 * <p>Sequence numbers come from a row lock on {@code session_stream}: the append transaction locks
 * the stream row, reserves a contiguous block and only then inserts. The unique key
 * {@code uk_session_seq} is the second line of defence, not the allocation strategy.
 */
@Repository
public class RelationalSessionEventStore implements SessionEventStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelationalSessionEventStore.class);
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final SessionEventMapper eventMapper;
    private final SessionStreamMapper streamMapper;
    private final SessionEventPayloadCodec payloadCodec;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ObjectProvider<SessionEventListener> listeners;

    /**
     * Creates the store.
     *
     * @param eventMapper event table access
     * @param streamMapper stream table access
     * @param payloadCodec payload serializer
     * @param objectMapper shared JSON mapper, used for the capability snapshot
     * @param clock runtime clock
     * @param listeners projection listeners, resolved lazily so a projection may depend on this
     *     store without creating a construction cycle
     */
    public RelationalSessionEventStore(
            SessionEventMapper eventMapper,
            SessionStreamMapper streamMapper,
            SessionEventPayloadCodec payloadCodec,
            ObjectMapper objectMapper,
            Clock clock,
            ObjectProvider<SessionEventListener> listeners) {
        this.eventMapper = eventMapper;
        this.streamMapper = streamMapper;
        this.payloadCodec = payloadCodec;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.listeners = listeners;
    }

    @Override
    @Transactional
    public void createStream(SessionStreamDescriptor descriptor) {
        LocalDateTime now = utcNow();
        SessionStreamRow row = new SessionStreamRow();
        row.setSessionId(descriptor.sessionId());
        row.setNextSeq(1L);
        row.setStatus(ACTIVE_STATUS);
        row.setProfileVersion(descriptor.profileVersion());
        row.setCapabilitySnapshot(writeCapabilities(descriptor.capabilities()));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            streamMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new SessionStreamAlreadyExistsException(descriptor.sessionId());
        }
    }

    @Override
    @Transactional
    public List<SessionEvent> append(String sessionId, List<AppendEventCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }
        Long baseSeq = streamMapper.lockNextSeq(sessionId);
        if (baseSeq == null) {
            throw new SessionStreamNotFoundException(sessionId);
        }

        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime occurredAtUtc = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        streamMapper.advanceNextSeq(sessionId, baseSeq + commands.size(), occurredAtUtc);

        List<SessionEvent> committed = new ArrayList<>(commands.size());
        long seq = baseSeq;
        for (AppendEventCommand command : commands) {
            SessionEventType eventType = command.eventType();
            SessionEventRow row = new SessionEventRow();
            row.setSessionId(sessionId);
            row.setSeq(seq);
            row.setEventType(eventType.wireName());
            row.setSchemaVersion(eventType.currentSchemaVersion());
            row.setTurnId(command.turnId());
            row.setStepId(command.stepId());
            row.setRunId(command.runId());
            row.setCallId(command.callId());
            row.setPayloadJson(payloadCodec.serialize(command.payload()));
            row.setOccurredAt(occurredAtUtc);
            eventMapper.insert(row);

            committed.add(new SessionEvent(
                    row.getId(),
                    sessionId,
                    seq,
                    eventType,
                    eventType.currentSchemaVersion(),
                    command.turnId(),
                    command.stepId(),
                    command.runId(),
                    command.callId(),
                    command.payload(),
                    occurredAt));
            seq++;
        }
        List<SessionEvent> result = List.copyOf(committed);
        publishAfterCommit(sessionId, result);
        return result;
    }

    /**
     * Notifies projection listeners once the events are durable.
     *
     * <p>A listener that fails is logged rather than allowed to roll back the append: the log is the
     * source of truth and every projection can be rebuilt from it.
     *
     * @param sessionId owning session
     * @param events committed events
     */
    private void publishAfterCommit(String sessionId, List<SessionEvent> events) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyListeners(sessionId, events);
                }
            });
        } else {
            notifyListeners(sessionId, events);
        }
    }

    private void notifyListeners(String sessionId, List<SessionEvent> events) {
        listeners.forEach(listener -> {
            try {
                listener.onEventsCommitted(sessionId, events);
            } catch (RuntimeException exception) {
                LOGGER.error("Projection {} failed for session {}; rebuild it from the event log",
                        listener.getClass().getSimpleName(), sessionId, exception);
            }
        });
    }

    @Override
    @Transactional
    public SessionEvent append(String sessionId, AppendEventCommand command) {
        // Overridden so the transaction actually starts: a default interface method is invoked on the
        // target rather than the proxy, which would leave the row lock without a surrounding
        // transaction and break sequence allocation.
        return append(sessionId, List.of(command)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionEvent> readAll(String sessionId) {
        return toDomain(eventMapper.selectBySession(sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionEvent> readAfter(String sessionId, long afterSeq, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return toDomain(eventMapper.selectAfterSeq(sessionId, afterSeq, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean streamExists(String sessionId) {
        return streamMapper.selectById(sessionId) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findSessionsWithOpenTurns() {
        return List.copyOf(eventMapper.selectSessionsWithOpenTurns());
    }

    private List<SessionEvent> toDomain(List<SessionEventRow> rows) {
        List<SessionEvent> events = new ArrayList<>(rows.size());
        for (SessionEventRow row : rows) {
            SessionEventType eventType = SessionEventType.fromWireName(row.getEventType())
                    .orElseThrow(() -> new UnsupportedEventSchemaException(
                            "Unknown event type '" + row.getEventType() + "' at " + row.getSessionId()
                                    + "#" + row.getSeq()));
            events.add(new SessionEvent(
                    row.getId(),
                    row.getSessionId(),
                    row.getSeq(),
                    eventType,
                    row.getSchemaVersion(),
                    row.getTurnId(),
                    row.getStepId(),
                    row.getRunId(),
                    row.getCallId(),
                    payloadCodec.deserialize(row.getEventType(), row.getSchemaVersion(), row.getPayloadJson()),
                    row.getOccurredAt().toInstant(ZoneOffset.UTC)));
        }
        return List.copyOf(events);
    }

    private String writeCapabilities(List<String> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities == null ? List.of() : capabilities);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Capability snapshot cannot be serialized", exception);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }
}
