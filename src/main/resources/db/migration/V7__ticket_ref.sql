-- Phase: real ticket escalation (BRD §6). When an escalated SupportCase is
-- forwarded to the external Helpdesk, store the provider-side reference so the
-- support board can correlate the internal case with the external ticket.
-- When the endpoint is unconfigured the column simply stays NULL (internal-only).

ALTER TABLE support_case ADD COLUMN external_ticket_ref VARCHAR(256);
CREATE INDEX ix_case_external_ref ON support_case (external_ticket_ref);
