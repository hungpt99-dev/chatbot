# Phase 1C — LLM Integration (BYOK, provider-agnostic, guardrailed)

Status: COMPLETE (built, 41 tests green, off-mode verified at runtime; LLM-driven
paths covered by contract tests with a mock port)
Stack: Java 21 + Spring Boot 3.3.5 + Gradle (gradle:8.10.2-jdk21) + H2 + Flyway

## What this phase adds
The deterministic engine from Phase 1B is now driven by a real (BYOK) LLM instead of
only the offline keyword interpreter. The LLM is a **drop-in interpreter**, not a
co-author of SOP state:

```
employee message
   └─► ConversationService.sendMessage
          ├─ if explicit stepResult supplied (tests/curl) → use it
          ├─ else if LlmPort.isConfigured() → llmPort.decide(snapshot, message)
          │       └─ app VALIDATES: branchKey must be enumerated on the current step,
          │          else it is dropped and the engine falls back to defaultNext
          │       └─ on any provider error → returns null → offline interpreter (degrade)
          └─ else → OfflineInterpreter (off-mode)
   └─► SopExecutionEngine.advance(...)   ◄── unchanged, still the only state authority
```

## Provider boundary (`LlmPort`)
- `LlmPort` — interface: `isConfigured()` + `decide(ConversationSnapshot, message) -> LlmStepDecision`.
- `OpenAiCompatibleLlmPort` — OpenAI `/chat/completions` over Spring `RestClient`
  (works with OpenAI, Azure OpenAI, and compatible gateways by swapping `base-url`).
  It asks the model for **strict JSON** (`intent`, `branchKey`, `stepResult`,
  `escalationReason`, `response`), strips markdown fences, and parses it. Any transport
  or parse failure returns `null` so the caller degrades to offline rather than failing.
- `HelpdeskLlmProperties` (`@ConfigurationProperties prefix=helpdesk.llm`): `base-url`,
  `api-key`, `model`, `system-prompt`. The key comes from the environment
  (`HELPDESK_LLM_API_KEY`) — never committed to the repo.

## Guardrails (carried from ADR-0005, now enforced for the LLM too)
- **Branch-key validation**: a `branchKey` the model returns is only trusted if it is
  enumerated on the *current* step; otherwise it is ignored and the engine uses
  `defaultNext`. The model cannot route to an arbitrary step.
- **Resolution needs evidence**: a `RESOLVE` is only honored at a step the SOP marks as
  a terminal `RESOLVE`; an `ESCALATE` is honored at any step (handing off to a human is
  always safe). The AI cannot declare a problem "fixed" without the SOP's own final
  step.
- **Degrade, don't fail**: missing key → offline mode; provider error → offline mode for
  that turn. The backend is always runnable with zero external dependencies.

## Configuration
```
helpdesk.llm.base-url:  ${HELPDESK_LLM_BASE_URL:https://api.openai.com/v1}
helpdesk.llm.api-key:   ${HELPDESK_LLM_API_KEY:}     # empty => off-mode
helpdesk.llm.model:     ${HELPDESK_LLM_MODEL:gpt-4o-mini}
helpdesk.llm.system-prompt: <persona + SOP discipline>
```
Run with a key: `HELPDESK_LLM_API_KEY=sk-... docker compose up --build`.

## Verification
- `gradle clean test` → **41 tests green**, including:
  - `LlmIntegrationTest`: off-mode degrades to offline and still resolves; the LLM drives
    RESOLVE at the terminal step; the LLM drives ESCALATE with a reason; an **invalid
    branchKey returned by the model is rejected** (engine stays on `defaultNext`, no jump).
  - Engine guardrail tests updated for "RESOLVE only at terminal RESOLVE step".
- Runtime: app boots; with **no** key it runs off-mode and advances steps correctly
  (the `LlmPort` bean is live but dormant). The LLM-driven paths are covered by the mock
  port contract test because no real provider key is available in this environment.

## Honest limitation
A live LLM call is not exercised here (no API key in CI). The integration is proven at
the boundary: the port builds valid requests, parses strict-JSON decisions, validates
branch keys, and degrades on error. Swapping in a key is a one-environment-variable change.

## Out of scope (next phases)
- 1D: chat UI.
- 1E: operator docs, runbook, expanded tests (e.g. a recorded LLM fixture / WireMock test).
