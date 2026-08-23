# AI SOP Helpdesk — Phase 1 Proposal (MVP)

> Product scope: an **AI IT-support assistant for a hotel chain** (front desk,
> guest services, back office, F&B, housekeeping). Employees describe an IT
> problem; the assistant walks them through a curated SOP (PMS login, guest
> Wi-Fi, POS/guest printers, keycard encoder, booking sync, password reset,
> email, VPN, display, network drives) and escalates to a ticket on failure.
>
> Delivery: a **single Spring Boot application** — the chat + operator UI is
> server-rendered static assets inside this repo (`src/main/resources/ui/`).
> **No separate React/Angular SPA**, no Node build, no CORS boundary.
> See ADR-0008 (frontend + domain).
>
> Status: PROPOSAL (later superseded by Phases 1A–1E, which shipped).

---

## 0. Existing architecture inspection (what I found)

**Repository state:** `chatbot.git` exists on GitHub but is **empty** (no commits, no code). A fresh clone succeeds but yields no project files.

So there is **nothing in-repo to reuse** — no backend, frontend, DB, auth, LLM integration, vector store, Docker, or tests exist yet. This is a true greenfield MVP.

External convention sources I *can* mirror (from your other projects, not in-repo):
- **Vocab** (`/root/vocab`): TypeScript, MV3, factory DI, `@/` alias, local-first, thin modules. Good reference if we go TS.
- **FinPay** (`/root/finpay`): AI boundary (`LlmPort`/`AIClient` port, BYOK, **off-mode**, **audit-of-calls-only**), thin controllers, `domain/` vs `infrastructure/`, **legal state-machine transitions**, **append-only audit**, ADRs. Good *design* reference regardless of language.

**Environment facts (this host):**
- Python 3.12.3 + `uv 0.12.4` available natively. ✅
- Node 24 available. Docker 29 available. ✅
- **No local Java, no local Postgres/pgvector, no LLM API key** in env (only `LINEAR_API_KEY`). No local model server.
- SQLite 3.45.1 (bundled) **with FTS5** available → offline-capable retrieval.

---

## 1. Stack options (DECISION NEEDED — see §10)

A) **Python + FastAPI + SQLite** (recommended here): runs natively, fully testable today without an API key (off-mode), fastest MVP, Pydantic gives first-class structured-output validation (a hard requirement).
B) **TypeScript + Node (e.g. Fastify/Express) + SQLite**: mirrors your Vocab TS experience, one language for API + UI, but structured-output validation is slightly more manual.
C) **Java + Spring Boot**: matches FinPay exactly, but no local JDK here (needs Gradle Docker builds), slower iteration, overkill for an MVP.

---

## 2. Domain model

```
SOP
  id, title, description, category,
  problem_description, symptoms[], prerequisites[],
  expected_result, failure_condition, escalation_condition,
  version, created_at, updated_at

SOPStep
  key ("1","2a"), order, sop_id,
  instruction (what the AI says/asks),
  type ∈ {QUESTION, ACTION, CHECK, ESCALATE},
  default_next (step key),
  is_terminal, terminal_kind ∈ {RESOLVE, ESCALATE}

SOPStepBranch   (conditional routing — "IF offline → network")
  step_id, branch_key ("paper_jam"),
  condition_text (human-readable), goto_step_key

Conversation (SOP execution state, one per employee session)
  id, user, problem_text, sop_id,
  status ∈ {INIT, SOP_SELECTED, IN_PROGRESS, AWAITING_USER,
            RESOLVED, ESCALATED, FAILED},
  current_step_key, completed_steps[],
  started_at, updated_at, resolved_at

Case (incident record, auto-created from a conversation)
  id, conversation_id, user, problem, sop_id,
  status ∈ {OPEN, IN_PROGRESS, RESOLVED, ESCALATED},
  started_at, resolved_at,
  failed_step, escalation_reason, escalation_status

AuditEntry (append-only, source of truth for "why did AI say X")
  id, conversation_id, ts, event_type, payload JSON
  # SOP_RETRIEVED / STEP_ASKED / USER_REPLY / STEP_RESULT /
  # STATE_TRANSITION / ESCALATED / RESOLVED
```

---

## 3. SOP representation (directed graph with conditional branches)

Each SOP is a graph, not just a list:
- Linear default path via `default_next`.
- Per-step `branches[]`: the LLM picks a branch key **from the enumerated candidates only**; the engine routes to `goto_step_key`. The LLM never names an arbitrary step.
- Terminal steps (`RESOLVE`/`ESCALATE`) close the flow.
- Steps store `instruction` text; the AI paraphrases/translates it — it does not author new procedure.

```json
{
  "id": "printer-cannot-print",
  "title": "Printer cannot print",
  "category": "IT / Printer",
  "symptoms": ["không in được", "print job stuck", "offline"],
  "steps": [
    {"key":"1","instruction":"Máy in có đang bật nguồn không?","type":"QUESTION","default_next":"2"},
    {"key":"2","instruction":"Kiểm tra kết nối mạng máy in.","type":"CHECK","default_next":"3",
     "branches":[{"branch_key":"offline","condition_text":"printer offline","goto_step_key":"2b"}]},
    {"key":"2b","instruction":"Kiểm tra cáp/router, kết nối lại wifi.","type":"ACTION","default_next":"3"},
    {"key":"3","instruction":"Trên màn hình có lỗi gì?","type":"QUESTION","default_next":"4",
     "branches":[{"branch_key":"paper_jam","condition_text":"paper jam","goto_step_key":"3b"}]},
    {"key":"3b","instruction":"Mở khay giấy sau, gỡ kẹt giấy.","type":"ACTION","default_next":"5"},
    {"key":"7","instruction":"In thử trang test.","type":"CHECK","default_next":"8",
     "branches":[{"branch_key":"still_broken","condition_text":"still not printing","goto_step_key":"8"}]},
    {"key":"8","instruction":"","type":"ESCALATE","is_terminal":true,"terminal_kind":"ESCALATE"}
  ]
}
```

---

## 4. SOP execution state machine (deterministic, OUTSIDE the LLM)

```
 user text ──▶ INIT ──(retrieve + LLM picks from candidates)──▶ SOP_SELECTED
                │  engine loads step[1]
                ▼
        IN_PROGRESS (AWAITING_USER) ──AI asks instruction──▶ AWAITING_USER
                ▲                                                │
                └──────────── user replies ─────────────────────┘
   LLM interprets reply → {result, branchKey}; engine VALIDATES, then:
   PASS/FAIL/INFO ──▶ RESOLVED | ESCALATED | next(default_next) | branch(goto)
```

- **Legal transitions only.** Illegal → rejected, audit-logged, **no state mutation**.
- LLM output validated by schema before any state change. Invalid → safe bounded retry → if still invalid, hold state + ask user to rephrase. **Never mutate on invalid output.**
- AI cannot invent steps: next step always from the SOP graph.

---

## 5. RAG / LLM flow (retrieval is the source of truth)

```
User message
  ▼
[Retrieval] FTS5 lexical search over (title + problem_description + symptoms +
            category + step instructions). → ranked candidate SOP list
            (pluggable interface → embeddings later)
  ▼
[LLM: SOP selection] returns {sopId} chosen FROM candidates only (cannot invent)
  ▼
[Engine] loads SOP, sets current step, builds the "ask this step" prompt
  ▼
[LLM: reply interpretation] returns STRUCTURED { intent, stepResult∈{PASS,FAIL,INFO},
            branchKey?, extractedValue? }
  ▼
[Engine] validate → transition state machine → record audit → produce AI reply
            (paraphrase step instruction; never author new procedure)
  ▼
User
```

Two hard guardrails, both deterministic: retrieval candidates bound the LLM's SOP choice; the SOP graph bounds the next step.
**Off-mode:** when no provider is configured, a deterministic client lets the whole flow + all tests run without a key.

---

## 6. Database schema (SQLite; Postgres-swappable later)

Tables: `sops`, `sop_steps`, `sop_step_branches`, `conversations`,
`conversation_messages`, `step_results`, `cases`, `audit_log`.
- `conversation_messages` = chat transcript.
- `step_results` = per-step outcome trace.
- `audit_log` = append-only decision trace (the "why" answer).
- `cases` = incident snapshot auto-derived on resolve/escalate.

---

## 7. API boundaries (matches the spec)

```
POST   /api/sops                       # create SOP
GET    /api/sops                       # list (filter by category)
GET    /api/sops/{id}                  # get one (+ steps, branches)
PUT    /api/sops/{id}                  # update

POST   /api/conversations              # create (employee + problem text)
GET    /api/conversations/{id}         # full state: sop, current step, history, status
POST   /api/conversations/{id}/messages# user msg → engine → structured AI reply + new state

GET    /api/cases                      # list (filter by status)
GET    /api/cases/{id}                 # one case
GET    /api/audit?conversationId=      # append-only decision trace
```

Every mutation returns the **structured SOP execution state** so UI/tests assert exactly what happened. Thin controllers; domain logic in `domain/`, LLM/DB in `infrastructure/`.

---

## 8. Seed SOPs (8, hotel-IT scoped)

1. Printer cannot print (POS / guest / front-desk printer)  2. Computer has no Internet
3. Guest Wi-Fi cannot connect (captive portal)  4. Cannot login to PMS / company account
5. Password reset  6. Email cannot send  7. VPN cannot connect  8. Monitor / display not working

Hotel-specific extensions planned: keycard/encoder failure, POS terminal down,
booking/reservation sync failure between OTA/channel-manager and PMS.

---

## 9. Implementation plan (small, reviewable commits)

- **1A — Foundation:** repo scaffold, models + CRUD API + seed 8 SOPs. Tests: CRUD + lexical retrieval.
- **1B — Engine + cases:** `Conversation` execution state machine (legal transitions, branching) + case model. Tests: step progression, branching, illegal-transition rejection, resolve, escalate.
- **1C — AI + RAG:** `LlmPort` + off-mode + BYOK client, FTS5 retrieval wired, structured-output validation + safe retry. Tests: invalid LLM output (retry, no mutation), full integration flow (resolve + escalate) in off-mode.
- **1D — UI + audit + escalation:** chat UI (dark, FinPay-style) showing SOP title, step progress, status, "why" audit expander; escalation message + case creation; `/api/audit`.
- **1E — Packaging + docs:** Dockerfile, docker-compose, README, ADRs, full pytest run, final architecture review.

Each phase: run tests → check architecture → document decisions.

---

## 10. Open decision

**Stack:** (A) Python/FastAPI [recommended — native here, fully testable without a key] · (B) TypeScript/Node [mirrors your Vocab experience, one language] · (C) Java/Spring [matches FinPay, slower here].

No code written. Approval to proceed (stack per your choice) requested before implementation.
