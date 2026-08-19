-- H2 variant of sql/schema.sql, used by the backend contract tests (MODE=MySQL).
-- JSON columns become CLOB because H2 requires explicit JSON literals.
-- Keep this file in sync with sql/schema.sql.

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
    payload_json CLOB NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_session_seq UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_session_turn ON session_event (session_id, turn_id);
CREATE INDEX IF NOT EXISTS idx_session_type ON session_event (session_id, event_type);

CREATE TABLE IF NOT EXISTS session_stream (
    session_id VARCHAR(64) PRIMARY KEY,
    next_seq BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    profile_version VARCHAR(64) NOT NULL,
    capability_snapshot CLOB NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS dev_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    repository_path VARCHAR(500) NOT NULL,
    default_branch VARCHAR(100) DEFAULT 'main',
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    level VARCHAR(20) NOT NULL,
    trace_id VARCHAR(100),
    logger VARCHAR(255),
    message CLOB NOT NULL,
    exception_type VARCHAR(255),
    stack_trace CLOB,
    log_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_time ON system_log (project_id, log_time);
CREATE INDEX IF NOT EXISTS idx_project_service ON system_log (project_id, service_name);
CREATE INDEX IF NOT EXISTS idx_trace ON system_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_level ON system_log (level);

CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_updated ON chat_session (project_id, updated_at);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    source_seq BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content CLOB NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_message_source UNIQUE (session_id, source_seq)
);

CREATE INDEX IF NOT EXISTS idx_session_id ON chat_message (session_id);

CREATE TABLE IF NOT EXISTS test_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    session_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    priority VARCHAR(20),
    precondition CLOB,
    steps_json CLOB,
    expected_result CLOB,
    source VARCHAR(30) DEFAULT 'AGENT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_test_case_project ON test_case (project_id);
CREATE INDEX IF NOT EXISTS idx_test_case_session ON test_case (session_id);
