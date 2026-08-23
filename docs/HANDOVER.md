# Handover — AI IT Support Assistant (`ai-helpdesk`)

**Repo:** `git@github.com:hungpt99-dev/chatbot.git` (branch `main`)
**Stack:** Java 21 · Spring Boot 3.3.5 · Gradle 8.10.2 (build via `gradle:8.10.2-jdk21` Docker image — no local JDK) · H2 (file) / Postgres (datasource swap, no code change) · Flyway · JPA/Hibernate · JUnit 5
**Last commit:** `bd57f80` — all BRD features merged, full `gradle test` green, live boot + API self-test passed.

---

## 1. What this is
An internal **AI IT-support assistant for a hotel chain**. Employees describe an IT problem; the assistant walks them through a **predefined SOP** (directed graph of steps) modeled in the KB. The AI is *constrained* by the SOP corpus and never invents steps. Single Spring Boot app, **no separate React SPA** (server-rendered static UI under `src/main/resources/ui/`).

It implements the **AI IT Support Assistant BRD** (KB search, multilingual, screenshot, ticket escalation, conversation history, admin management, SSO/RBAC/audit/HTTPS, KPI targets).

## 2. BRD coverage matrix
| BRD § | Requirement | Status | Notes |
|---|---|---|---|
| 1–3 | Objective / problem / chat solution | ✅ | SOP-guided chat, `ConversationService` |
| 4 | NL chat | ✅ | `ConversationController` |
| 4 | RAG KB search (vector) | ✅ | `VectorRetrieverAdapter` (in-process embeddings) + `LexicalSopRetriever`; mode LEXICAL/VECTOR/HYBRID |
| 4 | Multilingual | ✅ | `TranslationPort`, `lang` threaded through replies (degrade if unconfigured) |
| 4 | Screenshot / vision | ✅ | `VisionPort`; attachment persisted; degrades when unconfigured |
| 4 | Ticket escalation | ✅ | `TicketPort` → auto-ticket on `ESCALATE`; `externalTicketRef` stored |
| 4 | Conversation history | ✅ | `ConversationMessage` per `conversation_id` |
| 4 | Admin management | ✅ | Hotel/SOP CRUD APIs |
| 5 | KB upload → indexed (semantic) | ✅ | PDF/DOCX/FAQ/SOP parsed+chunked; **embedded + lexically indexed** (`document_embedding`, V9); vector/lexical/hybrid doc retrieval |
| 6 | Escalation workflow | ✅ | Auto-ticket on unresolved → `SupportCase.externalTicketRef` |
| 7 | SSO / OIDC | ✅ | `SecurityConfig` OIDC login; **off by default** (`helpdesk.security.enabled=false`) |
| 7 | AD authentication | ⚠️ Partial | Generic OIDC `roles-claim` mapping; no AD-specific connector |
| 7 | RBAC | ✅ | `@PreAuthorize` IT_ADMIN vs EMPLOYEE (`MethodSecurityConfig`) |
| 7 | Audit logging | ✅ | `AuditEvent` entity + repository |
| 7 | HTTPS | ✅ | TLS template in `application.yml` (commented) |
| 7 | Activity tracking | ✅ | Audit events + KPI meters |
| 8 | KPI targets (50%/70%/<5s) | ✅ *instrumented* | `MeterRegistry` timers/counters + actuator `/metrics`; targets are operational outcomes |

**Genuinely partial:** §7 AD-specific auth (generic OIDC only). Everything else is implemented.

## 3. Architecture (enforced boundaries — see `AGENTS.md`)
```
web (Controllers + DTOs)           ← HTTP boundary, validation, status codes
   ↓
application (ConversationService, SopService, HotelService, DocumentIngestionService)  ← use cases
   ↓
domain / engine (SopExecutionEngine, LlmPort, OfflineInterpreter, ResponseComposer)
        retrieval (LexicalSopRetriever, VectorRetrieverAdapter, VectorDocumentRetriever, EmbeddingService)
        model / repository
   ↓
infrastructure (llm/*, seed/*, document/*)   ← provider/IO details only
```
- **`SopExecutionEngine` is the single state authority.** LLM only proposes `branchKey`/`intent`; engine validates + applies. Do not move state logic into services/controllers.
- **Retrieval seam:** `LexicalOrVectorRetrievalStrategy` switches backend by `helpdesk.retrieval.mode`. Same seam for SOP and document corpora.
- **Ports (additive, per BRD):** `LlmPort`, `VectorRetrieverPort`, `TranslationPort`, `VisionPort`, `TicketPort`. Swap implementations behind these without touching callers.
- **Embeddings are provider-free** (hashing-trick vector in `EmbeddingService`). Swapping in a real model = one-class change behind `EmbeddingService`.

## 4. Multi-tenancy
`hotel_id` column on `sop`/`conversation`/`support_case`/`audit_event`/`document`/`document_chunk`/`document_embedding`/`sop_embedding`. Per-hotel SOP instances; hotel context supplied **per API request** (no auth yet by default). All retrieval filters on `hotel_id` — never crosses tenant boundaries.

## 5. How to build / test / run
```bash
# No local JDK needed — use the Gradle image:
docker run --rm -v "$PWD":/work -w /work -v gradle-cache-chatbot:/root/.gradle \
  gradle:8.10.2-jdk21 gradle test --no-daemon

# Boot (port 9090 host -> 8080 container, k8s/FinPay uses 8080):
docker run -d --rm -p 9090:8080 -v "$PWD":/work -w /work -v gradle-cache-chatbot:/root/.gradle \
  gradle:8.10.2-jdk21 gradle bootRun --no-daemon -x test

# Live smoke test:
curl http://localhost:9090/api/health
curl -X POST http://localhost:9090/api/conversations -H 'Content-Type: application/json' \
  -d '{"hotelId":"grand-hotel-saigon","employee":"amy","problem":"máy in không in được"}'
```
- **Tests use the `test` profile** (`application-test.yml`): **Flyway disabled**, Hibernate `create-drop` builds schema from entities. This is why schema bugs are masked in tests — **always also boot once with Flyway enabled** before declaring done.
- Seed hotels: `grand-hotel-saigon` (11 SOPs), `seaside-resort-danang` (12). Printer SOP code: `printer-cannot-print`.

## 6. Key APIs (all carry `hotelId`)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/conversations` | start chat |
| POST | `/api/conversations/{id}/messages` (JSON) | send text message |
| POST | `/api/conversations/{id}/messages` (multipart `image`) | send + screenshot |
| GET | `/api/conversations/{id}` | history |
| GET/POST | `/api/sops?hotelId=` | list / create SOP (admin) |
| GET | `/api/cases?hotelId=` | escalated cases |
| POST | `/api/admin/documents?hotelId=` (multipart `file`) | upload KB doc |
| GET | `/api/admin/documents/search?hotelId=&q=` | semantic + lexical doc search |
| GET | `/api/health`, `/actuator/metrics` | health + KPI |

Note: `@PreAuthorize` on admin SOP + document endpoints enforces RBAC **only when `helpdesk.security.enabled=true`**.

## 7. Coding standards (MUST follow — see `docs/ONBOARDING.md`)
- **Explicit imports only. NO fully-qualified class names (FQCN)** in code or javadoc. No wildcard imports.
- **Lombok** used per project convention (`@Getter @Setter @NoArgsConstructor` on entities; `@Slf4j` for logging).
- **Constructor injection.** `@Autowired` only allowed when a class has >1 constructor (must be imported, never FQCN).
- No new technologies/layers beyond what exists (AGENTS.md §1). RAG/vector work reuses `EmbeddingService`.
- Agents must verify with a **live boot + API self-test**, not just JUnit (mocks hide wiring/Flyway issues).

## 8. Known gotchas / lessons
- **`test` profile disables Flyway** → schema bugs (e.g. duplicate migration versions) pass tests but break production boot. Always boot with Flyway on before "done". *(This bit us: two `V8__` migrations existed; renumbered doc-embeddings to V9.)*
- **Embedding model is provider-free**, not semantic in the LLM sense. Fine for demo; swap `EmbeddingService` for real embeddings to improve recall.
- Orca (`/root/.local/bin/orca`) agent runs **wedge on `orca worktree create --agent opencode`** (blocks on an fs-permission prompt) and **`opencode run` is one-shot** (exits after one turn). Use **`opencode --auto`** (persistent loop) for full completion, and fix `cd` arg-leak bugs in any driver script (`cd "$dir" && ...` not `cd "$dir $prompt"`).
- The 7 BRD feature branches were machine-generated by Orca and left **non-compiling** in places; they were fixed and merged to `main` with integration-conflict resolution. The feature worktrees under `/root/orca/workspaces/chatbot/` are stale — delete them.

## 9. Outstanding / suggested next steps
1. **Live self-test on a real Postgres** swap (datasource URL only) to confirm Flyway + `CLOB`/`BLOB` columns port.
2. Swap `EmbeddingService` for a real embedding model (single-class change) if semantic recall needs improving.
3. AD-specific auth connector if §7 AD (not just generic OIDC) is required.
4. Real OIDC IdP config + HTTPS enable for production (env vars, no code change).
5. Wire KPI meters to a dashboard; set the BRD §8 target alerts.

---
*Generated 2026-08-23. Verified: `gradle test` BUILD SUCCESSFUL; live boot migrated to Flyway v9; conversation→step-advance→case-escalation and KB upload→semantic-search flows confirmed against the running API.*
