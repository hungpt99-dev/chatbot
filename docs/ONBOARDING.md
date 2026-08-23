# Onboarding — AI IT Support Assistant (`ai-helpdesk`)

> A guide for new engineers joining this codebase. Read `AGENTS.md` first — it is
> the **authoritative rulebook** for what this project is and what you may/may not
> change. This file is the human-friendly companion.

---

## 1. What this project is

An internal **AI IT Support Assistant** for a **hotel chain**. An employee
describes an IT problem (e.g. "printer won't print"); the assistant walks them
through a **predefined SOP** (Standard Operating Procedure) modeled as a
**directed graph** of steps and branches, and escalates to a Helpdesk ticket when
the SOP is exhausted.

This is the Java/Spring Boot implementation of the **AI IT Support Assistant BRD**
(see `docs/BRD.md` if present, or the requirements summary in §6). The retrieval
layer, LLM, and escalation are all designed to be **swapped without touching
business logic** (see §4).

### Confirmed tech (do NOT invent others)
| Concern | Choice |
|---|---|
| Language / build | Java 21, Gradle 8.10.2 (build via `gradle:8.10.2-jdk21` Docker image — **no local JDK**) |
| Framework | Spring Boot 3.3.5 (web, data-jpa, validation, actuator) |
| DB | JPA/Hibernate + **Flyway** (`src/main/resources/db/migration/V*`). H2 locally; Postgres = datasource-URL swap, **no code change** |
| UI | Server-rendered static assets in `src/main/resources/ui/` (no SPA/React) |
| LLM | BYOK behind `LlmPort`; shipped `OpenAiCompatibleLlmPort`. No key ⇒ off-mode `OfflineInterpreter` |
| Retrieval | **Lexical** today (`LexicalSopRetriever`). Vector/RAG is scaffolded but not yet wired (see §6) |
| Multi-tenancy | `hotel_id` column on `sop`/`conversation`/`support_case`/`audit_event`; hotel supplied per request |
| Tests | JUnit 5 + `spring-boot-starter-test`; LLM mocked via injected `RestClient` |

---

## 2. Architecture (the load-bearing boundary)

```
web (Controllers + DTOs)            ← HTTP boundary only: parse, call service, map DTO, status
        ↓
application (ConversationService, SopService, HotelService)  ← use cases
        ↓
domain /
   engine  (SopExecutionEngine, LlmPort, OfflineInterpreter, ResponseComposer)
   retrieval (LexicalSopRetriever)
   model / repository
        ↓
infrastructure (llm/*, seed/*)      ← provider / IO details only
```

**The `SopExecutionEngine` is the single source of truth for conversation state.**
The LLM only *proposes* a `branchKey`/`intent`; the engine *validates and applies*
it. Never let a controller or the LLM recompute `currentStepKey`/`status` — the
service persists what the engine returns.

### Package map
- `com.helpdesk.web` — controllers + DTOs (+ `web/dto`, `web/exception`)
- `com.helpdesk.application` — use cases (`*Service`)
- `com.helpdesk.domain` — `engine/`, `retrieval/`, `model/`, `repository/`
- `com.helpdesk.infrastructure` — `llm/`, `seed/`

---

## 3. Build, test, run

```bash
# Build + test (no local JDK; uses Docker image)
docker run --rm -v "$PWD":/work -w /work -v gradle-cache-chatbot:/root/.gradle \
  gradle:8.10.2-jdk21 gradle test --no-daemon

# Run locally (8080 is often taken by other services; map to 9090)
docker run --rm -v "$PWD":/work -w /work -v gradle-cache-chatbot:/root/.gradle \
  -p 9090:8080 gradle:8.10.2-jdk21 gradle bootRun --no-daemon
```

- Health: `GET /api/health`
- Swagger-free; endpoints under `/api/*` (see controllers).
- Tests run against an in-memory H2 profile; Flyway migrations apply on boot.

### Coding standards (enforced)
- **Explicit imports only.** Never use a fully-qualified class name (FQCN) in code
  or javadoc `{@link}`. Wrong: `new com.helpdesk.web.dto.SopResponse(...)`.
  Right: `import ...SopResponse;` then `new SopResponse(...)`.
- **Constructor injection** for Spring beans (no field `@Autowired` except the
  required one on a multi-constructor class).
- **Lombok** (`@Getter @Setter @NoArgsConstructor`) on entities; records for DTOs.
- No wildcard imports.

---

## 4. The "Port" pattern (how to add capabilities)

This codebase extends behavior by adding a **port** (interface in `domain`) + an
**adapter** (impl in `infrastructure`), wired by Spring. Existing ports:

- `LlmPort` — LLM provider boundary (`OpenAiCompatibleLlmPort` is the BYOK impl).
- `VectorRetrieverPort` — retrieval boundary (`VectorRetrieverAdapter` is a stub
  awaiting a real vector backend).

**To add a new integration (ticket system, vision, translation, SSO):** define the
interface in `domain`, implement it in `infrastructure`, inject it into the
`application` service. Do **not** scatter provider logic into controllers or the
engine. See `ADR-0002` (retrieval) and `ADR-0008` (frontend/domain) in `docs/adr/`.

---

## 5. Conversation & escalation flow

1. `POST /api/conversations` `{hotelId, employee, problem}` → lexical retrieval
   picks the best SOP → assistant shows step 1.
2. Each `POST /api/conversations/{id}/messages` → engine advances the step graph.
3. On `ESCALATE` terminal → `ConversationService.upsertCase` auto-creates a
   `SupportCase` (`CASE-xxxx`) and the conversation is closed (further messages →
   `409 ConversationClosedException`).

---

## 6. BRD coverage (what's done vs. open)

Implementing the **AI IT Support Assistant BRD**. Current state:

| BRD requirement | Status |
|---|---|
| NL chat, conversation history | ✅ done |
| RAG / vector KB search | ⚠️ scaffolded (port + strategy), adapter stub only |
| KB document upload (PDF/DOCX/FAQ → vector) | ❌ open |
| Ticket escalation (auto-create) | ⚠️ internal `SupportCase` only; no external Helpdesk forward |
| Screenshot / vision analysis | ❌ open |
| Multilingual (i18n) | ❌ open |
| SSO / AD / RBAC / HTTPS | ❌ open |
| Audit logging | ✅ done (`AuditEvent`) |
| KPI telemetry (resolution rate, latency) | ❌ open |

The open items are tracked as parallel workstreams and executed via Orca agent
worktrees (see `docs/ORCA.md`). Each maps to a new **port** per §4.

---

## 7. Working with Orca (agent worktrees)

We use Orca (`/root/.local/bin/orca`) to run coding agents in isolated git
worktrees so features don't collide on `main`.

```bash
# repo already registered; create a feature worktree + agent
orca worktree create --name <feature> --agent opencode --base-branch main \
  --prompt "<self-contained task, referencing AGENTS.md + the port pattern>"

# monitor
orca worktree ps
orca terminal list --worktree name:<feature>
```

**Lessons (from real runs):**
- The `opencode` agent prompts for "Access external directory?" and **wedges** the
  prompt submission. Launch opencode with `--auto` to auto-approve filesystem
  access, otherwise the worker stalls.
- Workers that stall are completed inline in their worktree, then committed.
- Merge each finished worktree into `main` via `git`, run the full `gradle test`
  suite, then push. Never merge without a green suite.

---

## 8. First-week checklist
- [ ] Read `AGENTS.md`, `README.md`, `docs/adr/*`, this file.
- [ ] `docker ... gradle test` is green on a fresh clone.
- [ ] Trace one conversation end-to-end: `ConversationController` →
      `ConversationService` → `SopExecutionEngine` → `LlmPort`.
- [ ] Add a small test for a behavior you touched; confirm it runs.
- [ ] Pick one open BRD item (§6), implement it behind a new port (§4), get a
      green suite, open a worktree/PR.
