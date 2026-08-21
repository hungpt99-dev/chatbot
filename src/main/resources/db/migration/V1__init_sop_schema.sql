-- Phase 1A: SOP domain schema (RDBMS; H2 in dev, Postgres later).
-- Flyway owns the schema; JPA ddl-auto is set to none.

CREATE TABLE sop (
    id                  VARCHAR(64)  NOT NULL,
    title               VARCHAR(512) NOT NULL,
    description         VARCHAR(2000),
    category            VARCHAR(256),
    problem_description CLOB,
    symptoms            CLOB,
    prerequisites       CLOB,
    expected_result     CLOB,
    failure_condition   CLOB,
    escalation_condition CLOB,
    version             INT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sop PRIMARY KEY (id)
);

CREATE TABLE sop_step (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    sop_id        VARCHAR(64)  NOT NULL,
    step_key      VARCHAR(64)  NOT NULL,
    step_order    INT          NOT NULL,
    instruction   CLOB,
    type          VARCHAR(32)  NOT NULL,
    default_next  VARCHAR(64),
    is_terminal   BOOLEAN      NOT NULL DEFAULT FALSE,
    terminal_kind VARCHAR(32),
    CONSTRAINT pk_sop_step PRIMARY KEY (id),
    CONSTRAINT fk_sop_step_sop FOREIGN KEY (sop_id) REFERENCES sop (id),
    CONSTRAINT uq_sop_step UNIQUE (sop_id, step_key)
);

CREATE TABLE sop_step_branch (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    step_id        BIGINT      NOT NULL,
    branch_key     VARCHAR(64) NOT NULL,
    condition_text VARCHAR(512),
    goto_step_key  VARCHAR(64) NOT NULL,
    CONSTRAINT pk_sop_step_branch PRIMARY KEY (id),
    CONSTRAINT fk_branch_step FOREIGN KEY (step_id) REFERENCES sop_step (id)
);

CREATE INDEX ix_sop_step_sop ON sop_step (sop_id);
CREATE INDEX ix_branch_step ON sop_step_branch (step_id);
