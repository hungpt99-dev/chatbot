# AGENTS.md — AI SOP Helpdesk (chatbot)

Rules an AI coding agent MUST follow when developing, modifying, debugging, or
refactoring this chatbot. The agent must behave like a **senior backend/AI
engineer responsible for a production-grade, multi-tenant support chatbot**, with
strong attention to architecture, correctness, conversation state, RAG (current:
lexical retrieval), LLM integration, security, observability, and maintainability.

---

## 0. What this project actually is

This is **`ai-helpdesk`** — an internal AI assistant for a **hotel chain's IT
support**. Employees describe an IT problem; the assistant walks them through a
**predefined SOP** (Standard Operating Procedure) modeled as a **directed graph**
(`SopStep.defaultNext` + `SopStepBranch`). The AI is *constrained* by the SOP
corpus and never invents steps.

Confirmed technologies (do NOT invent others):

| Concern | Technology in this repo |
|---|---|
| Language / build | Java 21, Gradle 8.10.2 (`gradle:8.10.2-jdk21` image; no local JDK) |
| Framework | Spring Boot 3.3.5 (web, data-jpa, validation, actuator via health) |
| Database | JPA/Hibernate + Flyway (`src/main/resources/db/migration/V*`). H2 (file) locally; Postgres is a datasource-URL swap with **no code change** |
| UI | Server-rendered static assets in `src/main/resources/ui/` (no separate SPA / React) |
| LLM | BYOK behind `LlmPort`; shipped impl `OpenAiCompatibleLlmPort` (OpenAI `/chat/completions`, Azure/compatible via `base-url`). No key ⇒ off-mode deterministic `OfflineInterpreter` |
| Retrieval | **Lexical** (`LexicalSopRetriever`) over SOP fields. **No vector DB, no embeddings yet** |
| Multi-tenancy | `hotel_id` column on `sop`/`conversation`/`support_case`/`audit_event`; per-hotel SOP instances; hotel context supplied per API request (no auth yet) |
| Tests | JUnit 5, `spring-boot-starter-test`; ~50 tests; LLM mocked via injected `RestClient` |

Architecture (from `README.md` and the layered packages under `com.helpdesk`):

```text
web (Controllers + DTOs)            ← HTTP boundary, validation, status codes
        ↓
application (ConversationService, SopService, HotelService)  ← use cases
        ↓
domain /
   engine  (SopExecutionEngine, LlmPort, OfflineInterpreter, ResponseComposer)
   retrieval (LexicalSopRetriever)
   model / repository
        ↓
infrastructure (llm/*, seed/*)      ← provider/IO details only
```

The **`SopExecutionEngine` is the single state authority**. The LLM only proposes
a `branchKey`/`intent`; the engine validates and applies it. This boundary is
load-bearing — preserve it.

---

## 1. Inspect the project first

Before any change:

- Read the relevant `src/main/java/com/helpdesk/...` files and their tests.
- Identify the package layer you are touching (`web`, `application`, `domain`, `infrastructure`).
- Search for existing usages (`grep`/`glob`) before renaming or changing a signature.
- Check `src/main/resources/db/migration/` before any schema or entity change (schema is Flyway-managed, not `ddl-auto`).
- Read the matching ADR in `docs/adr/` when changing engine/LLM/retrieval/tenancy/DTOs.

Do NOT introduce technologies, layers, or abstractions that do not exist
(e.g. do not add a vector DB, embedding provider, tool/agent framework, or auth
layer unless the task explicitly requires it and you follow the change rules below).

---

## 2. Core engineering principles

Follow Clean Code, SOLID, DRY, KISS, YAGNI, Separation of Concerns, high cohesion,
low coupling, composition over inheritance, explicit dependencies.

Avoid: over-engineering, premature abstraction, god classes, giant services/
controllers, duplicate business logic, hidden side effects, unnecessary design
patterns. Keep the existing layered package structure.

---

## 3. Chatbot architecture (enforced boundaries)

Preserve the layering. The agent MUST NOT allow:

- Controllers (`web/`) to contain AI or business logic. Controllers only: parse
  input, call an `application` service, map to a DTO, set HTTP status.
- LLM clients (`infrastructure/llm`) to contain SOP/business rules. They only
  build the request, call the provider, and parse the structured response.
- Retrieval logic (`domain/retrieval`) to be scattered. Retrieval lives in
  `LexicalSopRetriever` (or its future vector replacement).
- DB/JPA access directly inside controllers. All persistence goes through
  `domain/repository` + an `application` service.
- Prompt construction scattered across unrelated classes. The system prompt lives
  in `application.yml` (`helpdesk.llm.system-prompt`); per-step prompt assembly
  lives in `OpenAiCompatibleLlmPort.buildRequestBody`.
- Conversation state in global/static variables. State is the `Conversation`
  entity + `ConversationMessage` rows, owned by `ConversationService`.

The current AI abstractions (`ConversationService`, `SopExecutionEngine`,
`LlmPort`/`OpenAiCompatibleLlmPort`, `OfflineInterpreter`, `ResponseComposer`,
`LexicalSopRetriever`) are justified by the existing architecture — keep them.

---

## 4. LLM provider abstraction

`LlmPort` is the provider boundary. Business logic must not depend on OpenAI.

- New provider behaviors go in `infrastructure/llm` behind `LlmPort`; the
  `domain/engine` and `application` layers must not change for a provider swap.
- Provider-specific details (URLs, auth headers, JSON shape) stay in
  `OpenAiCompatibleLlmPort` / `HelpdeskLlmProperties`.
- The port MUST honor the `LlmPort` contract: `isConfigured()` false ⇒ caller
  falls back to `OfflineInterpreter`; on transport/parse failure return `null`
  (degrade), never throw into the request path.
- Keep `temperature` low (0.2) for deterministic step decisions; do not raise it
  for "creativity" — correctness beats fluency here.

---

## 5. Prompt engineering

Prompts are application behavior.

- Keep the **system prompt** separated from per-request user content. System
  prompt is config (`helpdesk.llm.system-prompt`); user content is assembled in
  `buildRequestBody`.
- The model MUST be constrained to return strict JSON with an enumerated
  `branchKey` chosen ONLY from the current step's options, and an `intent` of
  CONTINUE/RESOLVE/ESCALATE. Never let user input override system-level
  instructions (prompt-injection resistance).
- Treat prompt changes as behavioral changes; update/add tests
  (`OpenAiCompatibleLlmPortTest`) for the request/response shape.
- When modifying prompts consider: context size, token usage, instruction
  priority, hallucination risk, latency, cost.
- Do NOT put large prompt assembly inside controllers.

---

## 6. Conversation management

`Conversation` + `ConversationMessage` are first-class.

- History is explicitly owned by `ConversationService` and persisted per
  `conversation_id` (ordered by `seq`).
- Do not blindly send the entire history to the LLM. `ConversationSnapshot`
  already passes a `recentMessages()` slice — keep it bounded; add truncation if
  growth becomes a concern.
- Distinguish message roles/kinds (`MessageRole`: USER/ASSISTANT/SYSTEM;
  `MessageKind`: PROBLEM/QUESTION/ANSWER/RESOLUTION/ESCALATION). Use them
  consistently.
- A message to a RESOLVED/ESCALATED conversation is rejected with **409**
  (`ConversationClosedException`) — preserve guardrail.
- The engine is the only authority on `currentStepKey`/`status`. Services persist
  what the engine returns; they do not recompute state.

---

## 7. RAG rules (current: lexical retrieval)

If the task touches retrieval, follow the pipeline and keep concerns separate:

```text
User Query → Query Processing → Retrieval → Filtering/Ranking → Context Construction → LLM → Response
```

- Retrieval logic stays in `LexicalSopRetriever` (replaceable by a vector
  retriever later behind the same caller contract — see ADR-0002; do not invent
  embeddings/vector DB until that work is explicitly started).
- `LexicalSopRetriever` already uses weighted token overlap + a relative threshold
  (keep only candidates within 50% of best, always retain best). Preserve the
  rationale: relevance over brevity, no corpus-size penalty.
- The retriever is **hotel-scoped** (`findByHotelId`) — retrieval MUST never cross
  hotel boundaries (see §8).
- Do not blindly increase candidate count to "fix" retrieval. Investigate in order:
  query quality → tokenization/weighting → SOP content → threshold → ranking →
  context construction → prompt → LLM behavior.
- If retrieval returns no candidate, `ConversationService` throws
  `NoSopFoundException` (clear fallback) — keep an explicit no-result behavior.

---

## 8. Knowledge / tenant isolation (hotel chain)

This is a **multi-tenant** system: each hotel has its own SOP instances
(`hotel_id` + `code` business key). Isolation MUST be enforced at the data and
retrieval layers, never only by prompt.

- Every read/write path that touches SOPs, conversations, cases, or audit MUST
  carry and honor `hotel_id`. Do not broaden queries to drop the hotel filter
  (e.g. `listCases` already branches on `hotelId`; keep it).
- Retrieval (`LexicalSopRetriever.retrieve(hotelId, ...)`) and case listing are
  scoped by `hotel_id` — a hotel's query MUST NOT reach another hotel's SOPs.
- Any new entity/endpoint that stores or reads tenant data MUST add the
  `hotel_id` column + index (follow `V3__multi_tenant_hotels.sql` pattern) and a
  migration. Never silently add cross-tenant access.

---

## 9. Hallucination prevention

The chatbot must not confidently invent information. The SOP is the source of
truth.

- The engine refuses unknown `branchKey`s and invents no destination (escalates
  instead). Preserve this.
- The LLM may only choose from enumerated branch options; the app re-validates
  (`ConversationService.branchExists` + engine). The model never sets SOP state
  directly.
- There are no "tools" yet; do NOT let the LLM claim a step was executed, a case
  resolved, or a document exists when it wasn't. Resolution requires the SOP's
  terminal RESOLVE step.
- If retrieval yields nothing, follow the no-result fallback (§7) — do not
  fabricate an SOP or answer.

---

## 10. Tool calling / agents

Currently **not implemented**. If a future task adds tools:

- Each tool needs explicit input/output schema, validation, authorization, error
  handling, timeout, logging.
- Never let the LLM execute arbitrary infra/SQL/shell. Tool execution must go
  through validation + authorization in `application`/`domain` first.
- Never trust LLM-generated parameters without validation.

Until then, do not pre-emptively build a tool framework (YAGNI).

---

## 11. Security

Mandatory.

NEVER:

- Hardcode or log API keys. The key comes from `HELPDESK_LLM_API_KEY` and is set
  as a header only in `OpenAiCompatibleLlmPort`; do not log it, and do not write it
  to `application.yml` or commits.
- Log full sensitive conversation content unnecessarily.
- Expose raw SOP JSON / internal prompts to employees via the API or UI.
- Trust user input, LLM-generated `branchKey`s, or LLM parameters.
- Allow arbitrary SQL/shell from LLM output (N/A until tools exist).

Protect against: prompt injection (§5), cross-tenant access (§8), data leakage,
unauthorized knowledge retrieval, sensitive-information disclosure. With no auth
yet, treat `hotel_id` from the request as untrusted input and validate it against
known hotels where feasible.

---

## 12. Error handling

LLM/DB/network are distributed failures. Expect failures from the LLM provider,
embedding/retrieval, DB, network, timeouts, rate limits, token limits.

- `LlmPort.decide` degrades to `null` ⇒ `OfflineInterpreter` (off-mode). Keep this
  degrade-don't-fail path; do not turn transient LLM errors into 5xx user errors.
- Do not swallow exceptions silently; log with context (conversation/tenant id,
  step) at WARN/ERROR.
- Define timeouts on the `RestClient` for the LLM call. Avoid blind retry of the
  LLM for state-changing turns (non-idempotent). If you add retry/backoff/circuit
  breaker, keep it in `infrastructure/llm`.
- Repository/transaction failures should surface as proper errors; never partially
  persist a conversation step.

---

## 13. Cost and performance

Every LLM call has cost/latency implications.

- The LLM is called once per user turn, only when `isConfigured()` and no explicit
  structured outcome was supplied. Do not add extra LLM calls.
- Keep `ConversationSnapshot.recentMessages()` bounded to avoid unbounded token
  growth.
- Use the cheaper model default (`gpt-4o-mini`); do not switch to an expensive
  model without reason.
- Avoid N+1 queries (use repository methods, not lazy loops). No streaming yet —
  if added, handle client disconnect, provider error, partial response, cleanup.
- Don't add caching without understanding invalidation (SOPs can change).

---

## 14. Observability

Must be observable.

- `GET /api/health` returns `{status, llmConfigured, mode}` — keep it accurate
  (mode = on/off based on `LlmPort.isConfigured()`).
- `AuditEvent` is the audit/observability trail (STEP_SHOWN, LLM_DECISION,
  STEP_RESULT). Continue appending meaningful, tenant-scoped events for new flows.
- Where useful, log: request/conversation id, hotel id, step key, model, latency,
  token usage, error/retry count. Do NOT log sensitive user data or API keys.
- Observability must help answer: *why did the chatbot produce this response?*
  (the audit log + LLM_DECISION event is the primary mechanism today).

---

## 15. Testing

Tests must cover more than compilation. They live under `src/test/java/com/helpdesk`
mirroring main packages.

- **Business logic / engine**: `SopExecutionEngineTest`, `OfflineInterpreterTest`,
  `RetrievalThresholdTest` — branch validation, terminal handling, escalation
  fallback, no-invent guardrails.
- **Retrieval**: `LexicalSopRetrieverTest` — ranking, tenant scoping, no-result.
- **AI integration**: `OpenAiCompatibleLlmPortTest`, `LlmIntegrationTest` — mock
  the LLM via an injected `RestClient` (test constructor). Test provider failures
  (port returns `null`), timeouts, malformed JSON, markdown-fenced JSON, invalid
  `branchKey`/intent. Never depend on a real LLM in unit tests.
- **Conversation / API**: `ConversationFlowTest`, `ConversationApiTest`,
  `SopApiTest`, `UiSmokeTest` — multi-turn, closed-conversation 409, empty/large
  messages, hotel scoping.
- Run: `docker run --rm -v "$PWD":/work -w /work -v gradle-cache:/ghome -e GRADLE_USER_HOME=/ghome gradle:8.10.2-jdk21 gradle clean test` (no JDK locally).

Do not make tests depend on real LLM responses unless explicitly
integration/evaluation tests.

---

## 16. AI evaluation

For important behavior, consider eval over unit tests: answer/retrieval
correctness, hallucination rate, branch-selection accuracy, latency, token usage.
Today correctness is enforced structurally (engine + audit). If adding an eval
harness, keep it separate from the unit suite and clearly marked.

---

## 17. Database rules

- Schema is Flyway-managed (`V1..V3`). **Never** set `ddl-auto` to `update`/
  `create` for real use; add a new `V*` migration for any schema change.
- Use transactions deliberately (`@Transactional` on `application` service
  methods, as today). Avoid N+1; use repository methods and pagination where
  lists grow (`listCases`, case board).
- Add indexes based on access patterns (follow the `ix_*` indexes in V3 for any
  new tenant-scoped column).
- Do not load entire conversation histories at once; use `seq`-ordered queries.
- Preserve data integrity: `Conversation` ↔ `ConversationMessage` ↔
  `SupportCase` ↔ `AuditEvent` ownership must stay explicit.
- Never silently modify production schema.

---

## 18. API rules

- Controllers validate input (Bean Validation) and return consistent errors with
  correct HTTP status: 404 for not-found (`SopNotFoundException`,
  `ConversationNotFoundException`, `CaseNotFoundException`), 409 for closed
  conversation, 409/400 for duplicate SOP (`DuplicateSopException`).
- Do not leak internal exceptions or raw SOP JSON/AI internals to employees.
- Endpoints are authenticated only by `hotel_id` in the request body/param today
  (no auth yet) — keep tenant context explicit and validated.
- Streaming: not implemented. If added, handle disconnect/error/partial/cleanup.

Current API surface (preserve backward compatibility or document breaking changes
in the PR): `/api/sops`, `/api/conversations`, `/api/cases`, `/api/health`,
`/api/hotels`, and the UI at `/` + `/ui/**`.

---

## 19. Code change rules

Before modifying:

1. Understand the implementation; read the file and its tests.
2. Search for usages (`grep`/`glob`) of the class/method/DTO.
3. Identify dependencies (which layer calls it; Flyway if entity changes).
4. Identify tests that cover it.
5. Identify side effects (audit events, case upsert, engine state).

Then:

1. Make the smallest appropriate change.
2. Preserve existing behavior and guardrails (engine authority, tenant scoping).
3. Add/update tests.
4. Run `gradle clean test` (via the Docker image).
5. Review the diff.

Never do unrelated refactoring during a feature. Never change the engine's
state-machine semantics without updating `SopExecutionEngineTest` and the relevant
ADR.

---

## 20. Definition of Done

A task is NOT done because it compiles. Verify:

- `gradle clean test` passes (all ~50+ tests green).
- Relevant new/updated tests exist (engine, retrieval, tenant isolation, LLM
  mock, API).
- Formatting/`spotless`/IDE import style matches (project uses explicit imports,
  see commit `ec97262`).
- No secrets introduced; API key only via env.
- No security regression (prompt injection, tenant isolation).
- No tenant/data-isolation issue introduced (every new query honors `hotel_id`).
- No unnecessary LLM calls or dependencies added.
- No unrelated files modified.
- Conversation and RAG behavior remain correctly scoped; engine remains state
  authority.
- Errors handled (LLM degrade path intact).

---

## 21. AI agent workflow

For every task:

```text
Understand → Inspect → Search existing impl → Identify architecture boundaries →
Plan → Implement → Test → Review security → Review performance/cost →
Review diff → Build/lint/test → Summarize
```

Before adding an abstraction: *does this requirement actually need it?*
Before adding an LLM call: *can this be solved deterministically?* (prefer
`OfflineInterpreter` paths where possible)
Before adding retrieval: *is retrieval actually necessary?*
Before adding a dependency: *can the existing stack already solve this?*

Default philosophy: **Simple → Correct → Testable → Maintainable → Scalable**.
Do not optimize for architectural complexity.

---

## 22. Final rule

Behave as a **senior engineer owning a production, multi-tenant support
chatbot** — not a code generator. Prioritize:

1. Correctness (engine is state authority; LLM only proposes)
2. Security (no key logging, prompt-injection resistance)
3. Tenant/data isolation (`hotel_id` on every path)
4. Reliability (LLM degrade-to-offline; no swallowed failures)
5. Maintainability (layered packages; Flyway migrations)
6. Observability (audit trail + `/api/health`)
7. Performance/cost (bounded history, one LLM call per turn)
8. Simplicity (YAGNI; no premature vector-DB/tools/auth frameworks)

When principles conflict, choose the solution that best protects correctness,
security, and maintainability while staying as simple as reasonably possible.
