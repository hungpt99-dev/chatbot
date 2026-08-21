# ADR-0005: Deterministic, app-controlled SOP execution engine

Status: ACCEPTED
Date: 2026-08-21
Phase: 1B (Conversation, Case Tracking & Execution)

## Context
The product promises an AI that *guides employees through SOPs*, not an AI that acts
autonomously. The risk we must design out: an LLM (Phase 1C) that "helpfully" jumps to
an arbitrary step, silently skips steps, or declares a problem fixed without following
the SOP's own terminal resolution. The task spec guardrails forbid exactly this:
no fake resolution without evidence, no invented policy, no autonomous state changes.

## Decision
Separate the system into two layers with a hard boundary:

1. **AI / interpretation layer** (`OfflineInterpreter` now, `LlmPort` in 1C): turns a
   user message into a *proposal* — a `StepOutcome` of `CONTINUE | RESOLVE | ESCALATE`
   plus an optional `branchKey`. It holds **no SOP state** and never persists anything.

2. **Deterministic execution layer** (`SopExecutionEngine`): a pure function
   `advance(SopResponse, StepDto, StepOutcome) -> EngineResult`. It is the *only* place
   that decides the next step. It:
   - resolves a branch only by an enumerated `branchKey` that exists on the current step
     (unknown key ⇒ refused, conversation stays put);
   - falls back to `defaultNext` when no branch key is supplied;
   - honours terminal steps: a `RESOLVE`/`ESCALATE` only takes effect at a step whose
     `terminalKind` says so;
   - never invents steps or reorders the SOP.

The AI may *recommend*; the app *decides and records*. Every transition is persisted by
`ConversationService` and written to an append-only `audit_event` table.

## Consequences
- Good: the routing contract is LLM-agnostic. Phase 1C swaps the interpreter without
  touching the engine or any persistence. Guardrails are enforced in one reviewed place.
- Good: the engine is unit-testable with no database (see `SopExecutionEngineTest`),
  which is how we prove branching / resolution / escalation / guardrails deterministically.
- Good: works today with zero external dependencies (offline interpreter), so the whole
  backend runs and is testable without an API key — matching the "off-mode-first" principle.
- Trade-off: the AI is deliberately not allowed to "be creative"; when the SOP does not
  cover a situation it must escalate rather than improvise. This is the intended behaviour.

## Alternatives considered
- **Let the LLM emit the next step key directly and trust it.** Rejected: violates the
  core guardrail and is untestable/non-deterministic.
- **Encode the flow in the LLM prompt only, no engine.** Rejected: no auditability, no
  guardrails, no offline mode.
- **Use a generic workflow engine (e.g. BPMN).** Rejected for MVP: SOPs are small,
  branch-key graphs; a hand-rolled pure function is simpler and fully covered by tests.
