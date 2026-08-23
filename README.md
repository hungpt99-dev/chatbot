# AI SOP Helpdesk — Phase 1 (MVP)

Internal AI assistant that helps **hotel-chain** employees (front desk, guest
services, back office, F&B, housekeeping) resolve common IT problems by following
**predefined SOPs** (Standard Operating Procedures). The AI is *constrained* by the
SOP corpus — it never invents steps. This is the greenfield `chatbot` repository.

> Scope + delivery decisions: product domain is a **hotel chain's IT support**; the
> assistant is delivered as a **single Spring Boot app** — the chat + operator UI is
> server-rendered static assets in this repo. **No separate React/Angular SPA.**
> See `docs/adr/ADR-0008-frontend-and-domain.md`.

> Status: **Phase 1A complete** (SOP model, CRUD, lexical retrieval, 8 seed SOPs, tests).
> **Phase 1B complete** (conversation flow, deterministic execution engine, case tracking, audit, 37 tests green).
> **Phase 1C complete** (BYOK LLM integration via `LlmPort`, branch-key validation, off-mode fallback, 41 tests green).
> **Phase 1D complete** (self-contained chat UI served from the same app: employee chat + operator case board, 41 tests green).
> **Phase 1E complete** (operator runbook, `GET /api/health`, expanded parser + UI tests; **50 tests green**; Phase 1 MVP done).

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
records it. The LLM (Phase 1C) implements `LlmPort` and replaces `OfflineInterpreter`
without touching the engine — see `docs/adr/ADR-0005-engine.md` and `docs/adr/ADR-0006-llm.md`.

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

# With an LLM key (BYOK; empty key ⇒ off-mode, deterministic interpreter only)
HELPDESK_LLM_API_KEY=sk-... docker compose up --build
```

## UI (Phase 1D)
A self-contained chat UI is served from the same Spring app — no extra server, no CORS.
```
docker compose up --build
# open http://localhost:8088/   (host 8088 -> container 8080)
```
- Left: employee chat — describe the problem, then answer the SOP's step-by-step
  questions; the assistant advances the SOP and shows the current step. Resolves or
  escalates with a case created automatically.
- Right: operator board — live list of cases (filter by status), click for detail
  (reference, SOP, employee, failed step, escalation reason, timestamps).

Static assets live in `src/main/resources/ui/` (`index.html`, `app.js`, `styles.css`),
wired by `WebConfig` (`/` and `/ui/**`). The UI only sends `{ "message": "..." }`; the
deterministic engine remains the sole state authority.

## LLM integration (Phase 1C)
The LLM is a drop-in interpreter behind `LlmPort` (`OpenAiCompatibleLlmPort`, OpenAI
`/chat/completions`). Configure via `helpdesk.llm.*` (`base-url`, `api-key` from
`HELPDESK_LLM_API_KEY`, `model`, `system-prompt`). The app **validates** the model's
`branchKey` against the current step and only honors `RESOLVE` at a terminal step; any
provider error degrades to the offline interpreter. No key ⇒ fully runnable off-mode.

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
| GET | `/api/health` | Liveness + mode (`{status, llmConfigured, mode}`) |
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
- `docs/PHASE_1C.md` — BYOK LLM integration.
- `docs/PHASE_1D.md` — chat UI.
- `docs/PHASE_1E.md` — operator docs, runbook, expanded tests.
- `docs/OPERATOR.md` — runbook: run, health/mode, config, daily checks, troubleshooting.
- `docs/adr/` — Architecture Decision Records (ADR-0001 stack, ADR-0005 engine, ADR-0006 LLM, ADR-0007 DTO enums).

## Guardrails (carried into later phases)
The AI must NOT invent SOP steps, skip required steps, execute commands, claim resolution
without evidence, modify infra autonomously, or expose raw SOP JSON to employees. The SOP is
the source of truth; all decisions must be traceable to a SOP step (auditability).
