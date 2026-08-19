-- DevPilot schema (MySQL 8.0+).
-- Phase 1 introduces the runtime tables, Phase 2 the product tables.
-- Keep this file in sync with backend/src/test/resources/db/schema-h2.sql.

CREATE TABLE IF NOT EXISTS session_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    seq BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    turn_id VARCHAR(64),
    step_id VARCHAR(64),
    run_id VARCHAR(64),
    call_id VARCHAR(64),
    payload_json JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_session_seq (session_id, seq),
    INDEX idx_session_turn (session_id, turn_id),
    INDEX idx_session_type (session_id, event_type)
);

-- Sequence allocator and runtime metadata for one session event stream.
-- next_seq is reserved under a row lock (SELECT ... FOR UPDATE) so concurrent
-- appends to the same session never receive the same seq.
CREATE TABLE IF NOT EXISTS session_stream (
    session_id VARCHAR(64) PRIMARY KEY,
    next_seq BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    profile_version VARCHAR(64) NOT NULL,
    capability_snapshot JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS dev_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    repository_path VARCHAR(500) NOT NULL,
    default_branch VARCHAR(100) DEFAULT 'main',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    level VARCHAR(20) NOT NULL,
    trace_id VARCHAR(100),
    logger VARCHAR(255),
    message TEXT NOT NULL,
    exception_type VARCHAR(255),
    stack_trace MEDIUMTEXT,
    log_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project_time (project_id, log_time),
    INDEX idx_project_service (project_id, service_name),
    INDEX idx_trace (trace_id),
    INDEX idx_level (level)
);

CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_updated (project_id, updated_at)
);

-- Read projection of the user_message and assistant_message events, kept for efficient paging.
-- source_seq ties each row to the event it came from, which makes the projection idempotent and
-- rebuildable; session_event remains the only source of truth.
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    source_seq BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_message_source (session_id, source_seq),
    INDEX idx_session_id (session_id)
);
