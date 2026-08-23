-- Phase 1F: multi-tenant (hotel chain). Each property is different but shares
-- common SOPs. Decision (ADR-0008): per-hotel SOP instances, each fully its own,
-- tagged with hotel_id; corporate "shared" SOPs are replicated per hotel on seed.
-- Hotel context is supplied explicitly per API request (no auth yet).
--
-- SOP id stays VARCHAR(64) PK but is now composed as hotelId + ":" + code so the
-- primary key is stable across H2 and Postgres without an ALTER of the PK. A
-- separate (hotel_id, code) unique constraint preserves the business key.

CREATE TABLE hotel (
    id          VARCHAR(64)  NOT NULL,
    name        VARCHAR(256) NOT NULL,
    location    VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_hotel PRIMARY KEY (id)
);

ALTER TABLE sop ADD COLUMN hotel_id VARCHAR(64) NOT NULL DEFAULT 'shared';
ALTER TABLE sop ADD COLUMN code     VARCHAR(64) NOT NULL DEFAULT 'unknown';
ALTER TABLE sop ADD CONSTRAINT uq_sop_hotel_code UNIQUE (hotel_id, code);

ALTER TABLE conversation   ADD COLUMN hotel_id VARCHAR(64);
ALTER TABLE support_case   ADD COLUMN hotel_id VARCHAR(64);
ALTER TABLE audit_event    ADD COLUMN hotel_id VARCHAR(64);

CREATE INDEX ix_sop_hotel ON sop (hotel_id);
CREATE INDEX ix_conversation_hotel ON conversation (hotel_id);
CREATE INDEX ix_case_hotel ON support_case (hotel_id);
CREATE INDEX ix_audit_hotel ON audit_event (hotel_id);
