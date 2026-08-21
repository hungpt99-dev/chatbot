# Phase 1D — Chat UI (operator + employee chat, case board)

Status: COMPLETE (built, 41 tests green, UI served from the same Spring app, full
resolution + escalation + 409 guardrail verified at runtime)
Stack: Java 21 + Spring Boot 3.3.5 + Gradle (gradle:8.10.2-jdk21) + H2 + Flyway

## What this phase adds
A self-contained chat UI so employees can actually talk to the helpdesk and operators
can watch cases — all from the **same Docker image**, no extra web server, no CORS.

- `src/main/resources/ui/index.html` — two-pane layout.
- `src/main/resources/ui/app.js` — vanilla JS; calls the existing REST API.
- `src/main/resources/ui/styles.css` — dark, terminal-friendly theme.
- `WebConfig` — serves `ui/` at `/` and `/ui/**` (view-controller forwards `/` to
  `index.html`). Same-origin ⇒ no CORS configuration needed.

## Layout
- **Left — Chat**: start form (employee name + problem) → SOP retrieved → step-by-step
  Q&A. Each assistant turn shows the instruction and the current step key; the status
  badge flips to RESOLVED / ESCALATED when the conversation closes, and a system line
  points the operator to the case board.
- **Right — Operator board**: live list of `SupportCase`s (`GET /api/cases?status=`),
  filter by status, click to open a case detail (reference, SOP, employee, problem,
  failed step, escalation reason, timestamps).

## Wiring (how the UI drives the engine)
The UI only ever sends `{ "message": "..." }`. It never sends `stepResult`/branch keys —
the AI (LLM or offline interpreter) proposes the outcome and the deterministic engine
stays the sole state authority (unchanged from 1B/1C). When a conversation closes, the
send box hides and a further send returns **409**, which the UI surfaces as a friendly
"start a new conversation" note.

## Runtime verification
Built the jar, started the app on :8088, and drove a full session as the UI would:
- `GET /` → 200 (serves index.html); `/ui/app.js`, `/ui/styles.css` → 200.
- Start "Máy in không in được" → SOP `printer-cannot-print`, step 1.
- Walked 1→…→7, answered "đã in được, ok" → branch `ok` → terminal step 9 → **RESOLVED**,
  `resolvedAt` set, case created on the board.
- Post-close `POST /messages` → **409** (guardrail the UI relies on).

## Notes / honest limitations
- Single static bundle; no build step (no npm). Served by Spring directly.
- The status pill is cosmetic ("ready"); the backend does not currently expose the
  off/online mode via an endpoint, so the UI cannot show "LLM on/off" definitively.
  (Could add `GET /api/health` reporting `llmConfigured`.)
- No auth on the UI/API yet — appropriate for an internal MVP behind a corporate network;
  auth is an explicit later-phase concern.

## Out of scope (next)
- 1E: operator docs, runbook, expanded UI tests (e.g. Playwright against a test container).
