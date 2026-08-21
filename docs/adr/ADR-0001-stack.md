# Architecture Decision Records — AI SOP Helpdesk

## ADR-0001 — Stack: Java + Spring Boot
**Status:** accepted (2026-08-21)
**Context:** Greenfield (`chatbot.git` empty). Must match the user's FinPay stack for
consistency and reuse of AI-design conventions (`LlmPort`/BYOK/off-mode/audit).
**Decision:** Java 21 + Spring Boot 3.3.5 + Gradle 8.10.2 + JPA/Hibernate + Flyway + H2.
**Consequences:** No local JDK → builds run via the `gradle:8.10.2-jdk21` Docker image.
Heavier iteration than a scripted language, but aligned with existing convention and the
later LLM integration work.

## ADR-0002 — Retrieval boundary is pluggable
**Status:** accepted (2026-08-21)
**Context:** Phase 1A needs retrieval with no LLM/vector infra. Future phases want embeddings.
**Decision:** Retrieval is the `LexicalSopRetriever` behind the `SopService.retrieve` use case.
An embeddings/vector implementation can replace it (or run alongside) without changing callers.
**Consequences:** Deterministic, provider-free retrieval now; smooth upgrade path to RAG later.

## ADR-0003 — SOP is a directed graph, not a flat list
**Status:** accepted (2026-08-21)
**Context:** Requirements demand conditional branches (IF offline → network; IF paper jam →
jam procedure) while forbidding the AI from inventing steps.
**Decision:** Each `SopStep` has `defaultNext` plus zero-or-more `SopStepBranch` (branchKey →
gotoStepKey). The execution engine routes; the LLM may only choose among enumerated branch
keys of the current step.
**Consequences:** Deterministic, auditable control flow; the SOP remains the single source of
truth. Branch conditions are human-readable text (the LLM interprets the user reply into a
branch key, the engine validates it).

## ADR-0004 — SOPs persisted in a relational DB, schema owned by Flyway
**Status:** accepted (2026-08-21)
**Context:** SOPs are structured and relational (steps ↔ branches); later phases need
transactional case/audit writes.
**Decision:** JPA entities + Flyway V1 migration. MVP uses H2 (file) for zero-infra local
runs; Postgres is a datasource-URL swap (no code change).
**Consequences:** Strong typing, migrations, and easy local runs. Vector search (if adopted in
1C) will be a separate store, not a replacement for this system of record.
