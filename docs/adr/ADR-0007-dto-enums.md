# ADR-0007 — DTOs carry domain enums, not stringly-typed `.name()` values

Status: ACCEPTED

## Context
A source review (post-1E) found the web DTO layer was throwing away domain enums and
serializing `String` versions of them:

- `MessageDto.role` / `MessageDto.kind` were `String` (built via `m.getRole().name()`,
  `m.getKind().name()`) even though `MessageRole` and `MessageKind` enums already existed.
- `StepResultDto.result` was a bare `String` (CONTINUE/RESOLVE/ESCALATE); the concept had
  no enum at all, so the UI and service compared magic strings (`m.role === 'user'`,
  `"RESOLVED".equals(...)`).
- `ConversationResponse.status`, `CaseSummary.status`, `CaseDetail.status` were `String`
  instead of the existing `ConversationStatus` enum.

This is the classic stringly-typed DTO anti-pattern: it loses compile-time safety,
forces every consumer to re-parse/branch on magic strings, and desyncs silently from the
domain.

Note: `SopResponse` was already correct (it used `StepType`, `TerminalKind` enums), so the
project has a clear idiom — the request/response DTOs simply hadn't been held to it.

## Decision
- DTOs that mirror a domain enum MUST use the enum type directly. Spring/Jackson serializes
  enums as their `name()` on the wire, so the JSON contract is unchanged (e.g. `"status":
  "IN_PROGRESS"`), but consumers get a typed value instead of a `String`.
- Introduce `StepResult` enum (CONTINUE / RESOLVE / ESCALATE) for the AI's step conclusion;
  it is the single source of truth for that concept (used by `StepResultDto`; the engine's
  `StepOutcome` keeps `String` at its boundary and is converted via `.name()`).
- Collapse `StepResultDto`'s redundant `intent` field (a duplicate of `result`) — one typed
  `result: StepResult` field.
- The UI must branch on the typed fields (`m.kind === 'SYSTEM'`, `m.role === 'USER'`), not
  on `role === 'system'` (there is no "system" role; system notes are `MessageKind.SYSTEM`).

## Consequences
- Compile-time safety: an invalid status/role/kind/result no longer compiles.
- No behaviour change on the wire (enum name serialization is identical to the old string).
- The UI `renderMessage` was updated to key off `kind` (SYSTEM) then `role` (USER/ASSISTANT).

## Files changed
- `domain/engine/StepResult.java` (new enum)
- `web/dto/MessageDto.java`, `StepResultDto.java`, `ConversationResponse.java`,
  `CaseSummary.java`, `CaseDetail.java`
- `application/ConversationService.java` (boundary: `messagesOf` uses enum types;
  `StepResultDto.result().name()` → engine)
- `ui/app.js` (`renderMessage` keys off `kind`/`role` enums)
- tests updated to construct `StepResultDto(StepResult.X, ...)` and assert `ConversationStatus.X`
