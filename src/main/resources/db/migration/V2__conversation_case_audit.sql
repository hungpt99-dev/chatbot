-- Phase 1B: conversation, case tracking, audit.
-- Flyway owns the schema; JPA ddl-auto is none.

CREATE TABLE conversation (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    sop_id              VARCHAR(64),
    current_step_key    VARCHAR(64),
    status              VARCHAR(32)  NOT NULL,
    employee            VARCHAR(256),
    problem_summary     CLOB,
    started_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    resolved_at         TIMESTAMP,
    escalated_at        TIMESTAMP,
    last_user_message   CLOB,
    last_assistant_message CLOB,
    last_intent         VARCHAR(64),
    last_step_result    VARCHAR(32),
    CONSTRAINT pk_conversation PRIMARY KEY (id)
);

CREATE TABLE conversation_message (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT       NOT NULL,
    seq              INT          NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    kind             VARCHAR(32)  NOT NULL,
    content          CLOB         NOT NULL,
    sop_id           VARCHAR(64),
    step_key         VARCHAR(64),
    intent           VARCHAR(64),
    step_result      VARCHAR(32),
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_conversation_message PRIMARY KEY (id),
    CONSTRAINT fk_msg_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE TABLE support_case (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    reference         VARCHAR(64)  NOT NULL,
    conversation_id   BIGINT       NOT NULL,
    employee          VARCHAR(256),
    problem           CLOB,
    sop_id            VARCHAR(64),
    sop_title         VARCHAR(512),
    status            VARCHAR(32)  NOT NULL,
    failed_step_key   VARCHAR(64),
    escalation_reason CLOB,
    started_at        TIMESTAMP    NOT NULL,
    resolved_at       TIMESTAMP,
    escalated_at      TIMESTAMP,
    CONSTRAINT pk_support_case PRIMARY KEY (id),
    CONSTRAINT uq_case_reference UNIQUE (reference),
    CONSTRAINT fk_case_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE TABLE audit_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT,
    sop_id           VARCHAR(64),
    step_key         VARCHAR(64),
    event_type       VARCHAR(64)  NOT NULL,
    detail           CLOB,
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX ix_conversation_sop ON conversation (sop_id);
CREATE INDEX ix_msg_conversation ON conversation_message (conversation_id);
CREATE INDEX ix_case_status ON support_case (status);
CREATE INDEX ix_audit_conversation ON audit_event (conversation_id);
