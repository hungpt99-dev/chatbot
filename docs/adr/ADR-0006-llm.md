# ADR-0006: BYOK LLM, provider-agnostic port, never trusted with state

Status: ACCEPTED
Date: 2026-08-21
Phase: 1C (LLM integration)

## Context
Phase 1C introduces an LLM to interpret employee messages. Two risks must be designed
out: (1) the LLM must not become the authority over SOP state — that would defeat the
whole guardrail model from ADR-0005; (2) the provider must be swappable and the key must
never live in the repo.

## Decision
- Introduce a provider boundary `LlmPort` (`decide(snapshot, message) -> LlmStepDecision`)
  plus `ConversationSnapshot` (read-only context: SOP id/title, current step with its
  enumerated branch options, recent thread). Ship `OpenAiCompatibleLlmPort` (OpenAI
  `/chat/completions` via Spring `RestClient`); other vendors implement the same interface.
- BYOK: the API key is read from the environment (`HELPDESK_LLM_API_KEY`), wired through
  `helpdesk.llm.*` `@ConfigurationProperties`. Empty key ⇒ `isConfigured()==false` ⇒ the
  app runs in off-mode with the deterministic `OfflineInterpreter`.
- The LLM is an *interpreter only*. `ConversationService` re-validates its output: a
  `branchKey` is accepted only if enumerated on the current step; `RESOLVE` is honored
  only at a terminal RESOLVE step; `ESCALATE` anywhere is safe. The `SopExecutionEngine`
  remains the sole state authority (unchanged from 1B).
- Degradation: any provider/parse error returns `null` and the turn falls back to offline
  interpretation, so the backend is always runnable with no external dependency.

## Consequences
- Good: provider swap = new `LlmPort` bean, zero changes to the engine or persistence.
- Good: no key in repo; off-mode is the safe default and the test profile needs no key.
- Good: the LLM cannot jump steps or fake resolution — the same auditable guardrails as 1B
  apply, now explicitly re-validated at the boundary.
- Trade-off: we cannot exercise a live LLM call in CI (no key); the integration is verified
  at the boundary via contract tests with a mock port, plus offline-mode runtime checks.

## Alternatives considered
- **Call the LLM directly from the controller and trust its step choice.** Rejected: breaks
  ADR-0005, no guardrails, no auditability, no off-mode.
- **Vendor SDK as a hard dependency.** Rejected: locks the provider; the REST/`RestClient`
  approach keeps the boundary open and the dependency surface small.
- **Store the key in the repo / a config file.** Rejected: secret-in-repo anti-pattern;
  env-var BYOK only.
