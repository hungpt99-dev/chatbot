# AI SOP Helpdesk — Phase 1 (MVP)

Internal AI assistant that helps employees resolve common IT/support problems by following
**predefined SOPs** (Standard Operating Procedures). The AI is *constrained* by the SOP
corpus — it never invents steps. This is the greenfield `chatbot` repository.

> Status: **Phase 1A complete** (SOP model, CRUD, lexical retrieval, 8 seed SOPs, tests).
> Phases 1B–1E follow (execution engine, LLM/RAG, chat UI, packaging/docs).

## Architecture (so far)
```
Employee problem
   └─► GET /api/sops/search?q=...   (lexical retrieval, pluggable boundary)
   └─► SOP CRUD  POST/GET/PUT /api/sops[/:id]
   └─► SOP corpus loaded from src/main/resources/sop/*.json (seeded on boot)
```
The SOP is a **directed graph** (`SopStep.defaultNext` + `SopStepBranch` conditions) so
conditional branches work deterministically; the LLM (Phase 1C) may only choose among
enumerated branch keys.

## Stack
- Java 21 · Spring Boot 3.3.5 · Gradle 8.10.2 (built via the `gradle:8.10.2-jdk21` Docker
  image — there is no local JDK in this environment).
- JPA/Hibernate + Flyway (schema V1) over H2 (file) for zero-infra local runs; Postgres is a
  datasource-URL swap with no code change.

## Run locally
```bash
# Tests (no DB/LLM needed)
docker run --rm -v "$PWD":/work -w /work -v gradle-cache:/root/.gradle \
  gradle:8.10.2-jdk21 gradle clean test

# App
docker compose up --build
# → http://localhost:8088/api/sops   (host 8088 is mapped to container 8080;
#   if 8088 is busy, edit the port in docker-compose.yml)
```

## API (Phase 1A)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/sops` | Create a SOP |
| GET | `/api/sops` | List (optional `?category=`) |
| GET | `/api/sops/{id}` | Get one (with steps + branches) |
| PUT | `/api/sops/{id}` | Update |
| GET | `/api/sops/search?q=` | Lexical retrieval (candidate SOPs) |

## Seed SOPs (8)
printer-cannot-print · computer-no-internet · wifi-cannot-connect · cannot-login ·
password-reset · email-cannot-send · vpn-cannot-connect · monitor-not-working

## Docs
- `docs/PROPOSAL.md` — full architecture proposal (domain model, state machine, RAG flow, API).
- `docs/PHASE_1A.md` — what shipped in Phase 1A + decisions.
- `docs/adr/` — Architecture Decision Records (ADR-0001..0004).

## Guardrails (carried into later phases)
The AI must NOT invent SOP steps, skip required steps, execute commands, claim resolution
without evidence, modify infra autonomously, or expose raw SOP JSON to employees. The SOP is
the source of truth; all decisions must be traceable to a SOP step (auditability).
