# Phase 1E — Operator docs, runbook, expanded tests

Status: COMPLETE (built, **50 tests green**, runtime-verified; operator runbook + phase docs)
Stack: Java 21 + Spring Boot 3.3.5 + Gradle (gradle:8.10.2-jdk21) + H2 + Flyway

## What this phase adds
Closes the gaps called out at the end of 1C/1D:

1. **Operator runbook** — `docs/OPERATOR.md`: how to run, health/mode, configuration,
   daily checks, common operations, guardrails, troubleshooting, security notes.
2. **Real LLM-client tests** — `OpenAiCompatibleLlmPortTest` exercises the actual response
   parsing (strict-JSON, markdown-fence stripping, empty-choices, malformed→throw) plus the
   `isConfigured()` and transport-error→null degradation contract. This covers the wire/parse
   logic that 1C deliberately left to a mock port (no live key).
3. **UI smoke test** — `UiSmokeTest` (`@SpringBootTest` RANDOM_PORT) verifies the app
   actually serves the chat UI (`/` → HTML, `/ui/app.js` + `/ui/styles.css` → 200) and the
   new `/api/health` endpoint reports `mode`/`llmConfigured`.
4. **`GET /api/health`** — `HealthController` exposes `status`, `llmConfigured`, and `mode`
   (`online`/`offline`) so the UI/ops can show LLM status without leaking the secret. The UI
   now reads it to set the mode pill (`LLM online` / `offline`).

## Test count
- 1A: 14 → 1B: 37 → 1C: 41 → **1E: 50** (added `OpenAiCompatibleLlmPortTest` 6 cases +
  `UiSmokeTest` 3 cases; engine/contract tests realigned in prior phases).

## Runtime verification
- `gradle clean test` → **50 tests green**.
- Built jar, ran on :8088: `GET /api/health` → `{"mode":"offline","status":"UP",
  "llmConfigured":false}`; `GET /` → HTML; `/ui/app.js` + `/ui/styles.css` → 200.

## Honest scope note
A live LLM call is still not exercised (no key in CI). `OpenAiCompatibleLlmPortTest` tests
the parsing/transport-degradation logic directly, which is the model-dependent risk surface;
the HTTP transport itself is standard RestClient boilerplate. Swapping in a key remains a
one-environment-variable change.

## Overall Phase 1 status
- 1A ✅ SOP model, CRUD, lexical retrieval, 8 seeds, Docker.
- 1B ✅ Conversation flow, deterministic execution engine, case tracking, audit.
- 1C ✅ BYOK LLM via `LlmPort`, branch-key validation, off-mode fallback.
- 1D ✅ Self-contained chat UI (employee chat + operator case board).
- 1E ✅ Operator runbook, `GET /api/health`, expanded parser + UI tests.
Phase 1 (MVP) is complete.
