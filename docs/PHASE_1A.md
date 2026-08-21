# Phase 1A — Foundation

## What shipped
- Spring Boot 3.3.5 + Java 21 (Gradle 8.10.2, built via the Gradle Docker image since no
  local JDK is present — same approach as FinPay).
- SOP domain model (`Sop` → `SopStep` → `SopStepBranch`) persisted via JPA/Hibernate over
  H2 (file), schema owned by Flyway (V1 migration).
- SOP CRUD API: `POST/GET /api/sops`, `GET /api/sops/{id}`, `PUT /api/sops/{id}`, plus a
  lexical retrieval endpoint `GET /api/sops/search?q=...`.
- Lexical (keyword) retriever with weighted field scoring + a 50%-of-best relative threshold
  so the LLM (Phase 1C) receives a tight candidate set, not the whole corpus.
- 8 seed SOPs (printer, internet, wifi, login, password-reset, email, vpn, monitor), each a
  directed graph with conditional branches; seeded idempotently on startup.
- Tests: 14 passing — CRUD, validation, duplicate/404 handling, lexical retrieval, threshold
  pruning, and seed loading.

## Key decisions (ADR)
- **RDBMS + Flyway over document store**: SOPs are structured, relational (steps↔branches),
  and need transactional CRUD + audit trails later. Postgres is a one-line swap (change the
  datasource URL); MVP uses H2 for zero-infra local runs.
- **Retrieval is a pluggable boundary** (`LexicalSopRetriever`) so an embeddings/vector
  retriever can drop in for Phase 1C without touching callers (ADR-0002).
- **SOP as a graph, not a list**: `default_next` + per-step `branches` give deterministic
  conditional routing the LLM cannot escape (guardrail: LLM picks only enumerated branch keys).
- **No LLM yet by design**: retrieval/CRUD are provider-free so the MVP runs and is tested
  without an API key. The AI layer lands in Phase 1C behind the same boundaries.

## How to run
- Tests: `docker run --rm -v $PWD:/work -w /work -v gradle-cache:/root/.gradle gradle:8.10.2-jdk21 gradle clean test`
- App: `docker run --rm -p 8080:8080 -v $PWD:/work -w /work gradle:8.10.2-jdk21 gradle bootJar`
  then `java -jar build/libs/*.jar` (or use `docker-compose up`).
- API base: `http://localhost:8080/api/sops`

## Next
- Phase 1B: Conversation + Case models + deterministic SOP Execution Engine (state machine,
  branching, legal transitions).
