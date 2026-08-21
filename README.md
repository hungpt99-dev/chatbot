# AI SOP Helpdesk — Phase 1 (MVP)

Internal AI assistant that helps employees resolve common IT/support problems by following
**predefined SOPs** (Standard Operating Procedures). The AI is *constrained* by the SOP
corpus — it never invents steps. This is the greenfield `chatbot` repository.

> Status: **Phase 1A complete** (SOP model, CRUD, lexical retrieval, 8 seed SOPs, tests).
> **Phase 1B complete** (conversation flow, deterministic execution engine, case tracking, audit, 37 tests green).
> Phases 1C–1E follow (LLM/RAG, chat UI, packaging/docs).

## Architecture
```
Employee problem
   └─► LexicalSopRetriever  ──► best SOP            (reused from 1A)
   └─► ConversationService  ──► Conversation + Messages
            └─► OfflineInterpreter  ──► StepOutcome (CONTINUE/RESOLVE/ESCALATE + branchKey)
            └─► SopExecutionEngine.advance(...)      ◄── DETERMINISTIC, app-controlled
            └─► SupportCase on RESOLVE/ESCALATE + append-only AuditEvent
```
The SOP is a **directed graph** (`SopStep.defaultNext` + `SopStepBranch` conditions).
The AI only *proposes* an outcome (branch key); the engine *decides* the next step and
records it. The LLM (Phase 1C) will replace `OfflineInterpreter` without touching the
engine — see `docs/adr/ADR-0005-engine.md`.

## Stack
- Java 21 · Spring Boot 3.3.5 · Gradle 8.10.2 (built via the `gradle:8.10.2-jdk21` Docker
  image — there is no local JDK in this environment).
- JPA/Hibernate + Flyway (schema V1 + V2) over H2 (file) for zero-infra local runs;
  Postgres is a datasource-URL swap with no code change.

## Run locally
```bash
# Tests (no DB/LLM needed) — GRADLE_USER_HOME pointed at a plain dir to avoid the
# image's /root/.gradle symlink shadowing.
docker run --rm -v "$PWD":/work -w /work -v gradle-cache:/ghome -e GRADLE_USER_HOME=/ghome \
  gradle:8.10.2-jdk21 gradle clean test

# Build + run the app (jar is named ai-helpdesk-0.1.0.jar)
docker compose up --build
# → http://localhost:8088/api/sops   (host 8088 → container 8080; change in docker-compose.yml if busy)
```

## API
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/sops` | Create a SOP |
| GET | `/api/sops` | List (optional `?category=`) |
| GET | `/api/sops/{id}` | Get one (steps + branches) |
| PUT | `/api/sops/{id}` | Update |
| GET | `/api/sops/search?q=` | Lexical retrieval (candidate SOPs) |
| POST | `/api/conversations` | Start: `{employee, problem}` → retrieves SOP, begins step 1 |
| POST | `/api/conversations/{id}/messages` | `{message}` or `{stepResult, branchKey}` → advance |
| GET | `/api/conversations/{id}` | Full thread + current state |
| GET | `/api/conversations/{id}/audit` | Audit events for the conversation |
| GET | `/api/cases` | Support board (`?status=RESOLVED\|ESCALATED`) |
| GET | `/api/cases/{reference}` | Case detail |

Guardrails enforced at the boundary: a message to a RESOLVED/ESCALATED conversation is
rejected with **409**; an outcome with an unknown `branchKey` is refused by the engine
(no state jump).

## Seed SOPs (8)
printer-cannot-print · computer-no-internet · wifi-cannot-connect · cannot-login ·
password-reset · email-cannot-send · vpn-cannot-connect · monitor-not-working

## Docs
- `docs/PROPOSAL.md` — full architecture proposal (domain model, state machine, RAG flow, API).
- `docs/PHASE_1A.md` — what shipped in Phase 1A + decisions.
- `docs/PHASE_1B.md` — conversation, execution engine, case tracking, audit.
- `docs/adr/` — Architecture Decision Records (ADR-0001 stack, ADR-0005 engine).

## Guardrails (carried into later phases)
The AI must NOT invent SOP steps, skip required steps, execute commands, claim resolution
without evidence, modify infra autonomously, or expose raw SOP JSON to employees. The SOP is
the source of truth; all decisions must be traceable to a SOP step (auditability).
