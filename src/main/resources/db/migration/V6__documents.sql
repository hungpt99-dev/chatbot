-- Phase 1H (BRD section 5): KB document ingestion corpus. IT admins upload
-- PDF/DOCX/FAQ files; they are parsed, chunked, and indexed into the retrieval
-- corpus. This is the document analogue of the SOP tables. Multi-tenant (ADR-0008):
-- every row carries hotel_id plus an index so document retrieval can never cross
-- tenant boundaries.

CREATE TABLE document (
    id           VARCHAR(64)  NOT NULL,
    hotel_id     VARCHAR(64)  NOT NULL,
    filename     VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    chunk_count  INT          NOT NULL DEFAULT 0,
    uploaded_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_document PRIMARY KEY (id)
);

CREATE TABLE document_chunk (
    id              VARCHAR(64)  NOT NULL,
    document_id     VARCHAR(64)  NOT NULL,
    hotel_id        VARCHAR(64)  NOT NULL,
    chunk_index     INT          NOT NULL,
    source_filename VARCHAR(512),
    content         CLOB         NOT NULL,
    CONSTRAINT pk_document_chunk PRIMARY KEY (id),
    CONSTRAINT fk_doc_chunk_document FOREIGN KEY (document_id) REFERENCES document (id)
);

CREATE INDEX ix_document_hotel ON document (hotel_id);
CREATE INDEX ix_doc_chunk_hotel ON document_chunk (hotel_id);
CREATE INDEX ix_doc_chunk_document ON document_chunk (document_id);
