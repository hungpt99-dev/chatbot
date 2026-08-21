# Phase 1B — Conversation, Case Tracking & Deterministic SOP Execution

Status: COMPLETE (built, 37 tests green, verified end-to-end at runtime)
Stack: Java 21 + Spring Boot 3.3.5 + Gradle (gradle:8.10.2-jdk21) + H2 + Flyway

## What this phase delivers

The AI SOP Helpdesk now carries a user through a Standard Operating Procedure
conversationally, and either **resolves** the issue or **escalates** it to a human
support case — with every consequential action recorded in an audit trail.

The execution layer is **deterministic and app-controlled**. The AI (today an offline
keyword interpreter, in 1C an LLM) is never allowed to mutate SOP state directly. It
only proposes an *outcome* (`CONTINUE` / `RESOLVE` / `ESCALATE` + an optional branch
key); the `SopExecutionEngine` validates and applies it. This is the central guardrail
of the whole product: the AI cannot jump to arbitrary steps, cannot invent steps, and
cannot declare a problem resolved unless the SOP's own terminal step says so.

## Architecture

```
Problem text
   │
   ▼
LexicalSopRetriever  ──►  Sop (best match)          [reused from 1A]
   │
   ▼
ConversationService.startConversation()
   │  • persists Conversation (sopId, currentStepKey, status)
   │  • persists first USER + ASSISTANT messages
   │  • writes AuditEvent(RETRIEVAL / STEP_SHOWN)
   ▼
ConversationService.sendMessage()
   │  • employee reply persisted
   │  • outcome = explicit stepResult (Phase 1C) OR OfflineInterpreter.interpret(text)
   │  • SopExecutionEngine.advance(sop, currentStep, outcome)   ◄── DETERMINISTIC
   │       – resolves branch by branchKey (guardrail: unknown key refused)
   │       – follows defaultNext when no branch key
   │       – terminal step + RESOLVE/ESCALATE ⇒ conversation over
   │  • assistant reply composed (ResponseComposer)
   │  • on RESOLVED/ESCALATED ⇒ SupportCase created (caseRef = CASE-<id>)
   │  • every step persisted + audited
   ▼
GET /api/conversations/{id}   full thread + state
GET /api/cases                 support board (RESOLVED / ESCALATED)
GET /api/cases/{reference}     case detail
GET /api/conversations/{id}/audit
```

### Why the engine is a pure function
`SopExecutionEngine.advance(SopResponse, StepDto, StepOutcome) -> EngineResult` holds
no state and performs no I/O. All persistence lives in `ConversationService`. This makes
the branching/guardrail logic trivially unit-testable without a database (see
`SopExecutionEngineTest`), and means the LLM integration in Phase 1C only has to produce
a `StepOutcome` — the routing contract does not change.

### Offline interpreter (pre-LLM fallback)
`OfflineInterpreter` maps free text to an outcome when no LLM key is configured:
keyword hits (e.g. "không", "lỗi", "kẹt") ⇒ `ESCALATE`/`CONTINUE`; "ok"/"xong"/"được"
⇒ positive. This keeps the backend fully runnable and testable with zero external
dependencies, satisfying the "off-mode-first" principle from the task spec.

## Data model (new tables, Flyway V2)
- `conversation` — one row per helpdesk session: sop_id, current_step_key,
  completed_step_keys, status, failed_step, escalation_reason, employee_id.
- `conversation_message` — append-only thread: seq, role (USER/ASSISTANT/SYSTEM),
  kind (PROBLEM/QUESTION/ANSWER/GUIDANCE/RESOLUTION/ESCALATION), content, step_key,
  outcome.
- `support_case` — derived from a resolved/escalated conversation: reference
  (`CASE-<n>`), status (OPEN/IN_PROGRESS/RESOLVED/ESCALATED), sop_id, conversation_id,
  problem, failed_step, escalation_reason.
- `audit_event` — append-only: type, conversation_id, sop_id, step_key, detail,
  created_at.

## API surface (Phase 1B)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/conversations` | start: `{employee, problem}` → retrieves SOP, begins step 1 |
| POST | `/api/conversations/{id}/messages` | `{message}` or `{stepResult, branchKey}` → advance |
| GET  | `/api/conversations/{id}` | full thread + current state |
| GET  | `/api/conversations/{id}/audit` | audit events for the conversation |
| GET  | `/api/cases` | board; `?status=RESOLVED\|ESCALATED` filter |
| GET  | `/api/cases/{reference}` | case detail |

Guardrails enforced at the boundary:
- A message to a RESOLVED/ESCALATED conversation is rejected with **409**.
- An `outcome` with an unknown `branchKey` is refused by the engine (no state jump);
  the conversation stays on the current step.

## Verification
- `gradle clean test` → **37 tests green** (engine branching/resolution/escalation/
  guardrails, conversation flow with persistence, case creation, retrieval threshold,
  SOP CRUD, seed loading).
- Runtime smoke test (Docker, port 8088): printer problem → retrieved
  `printer-cannot-print` → walked steps 1→6 → RESOLVED + case `CASE-000001`; second
  run escalated at the driver step → ESCALATED + case `CASE-000004` with reason;
  closed-conversation message rejected with 409.

## Out of scope (next phases)
- 1C: LLM integration (`LlmPort`) replacing `OfflineInterpreter`; structured
  `stepResult` validation; BYOK key storage.
- 1D: chat UI.
- 1E: operator docs, runbook, expanded tests.
