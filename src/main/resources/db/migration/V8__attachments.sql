-- Phase 4: screenshot / vision attachments.
-- An employee may attach a screenshot to a conversation message; the assistant
-- analyzes it via VisionPort. Attachments are tenant-scoped by hotel_id and
-- parented to a conversation (matching the existing conversation_message model).

CREATE TABLE message_attachment (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT       NOT NULL,
    hotel_id         VARCHAR(64),
    content_type     VARCHAR(128),
    file_name        VARCHAR(256),
    data             BLOB,
    seq              INT,
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_message_attachment PRIMARY KEY (id),
    CONSTRAINT fk_attachment_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE INDEX ix_attachment_conversation ON message_attachment (conversation_id);
CREATE INDEX ix_attachment_hotel ON message_attachment (hotel_id);
