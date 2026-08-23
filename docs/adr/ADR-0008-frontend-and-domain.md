# ADR-0008 — Frontend delivery model and product domain

**Status:** accepted (2026-08-21)
**Context:** The BRD ("AI IT Support Assistant", Java/Spring) specified a React or
Angular frontend and a generic enterprise IT scope. Two product decisions were
made after the BRD review that change both the delivery model and the seed content.

## Decision 1 — Single Spring Boot app; no separate SPA framework
The UI is delivered as **server-rendered / static assets inside this repository**
(`src/main/resources/ui/`, wired by `WebConfig`), exactly as Phase 1D shipped.
There is **no separate React/Angular application**, no Node build step, and no CORS
boundary between UI and API.

- **Rationale:** the assistant is a single internal tool; a separate SPA adds a
  second build pipeline, a deployment unit, and a CORS surface for no product gain.
  Spring Boot already serves the static chat + operator UI with zero extra infra.
- **Consequence:** any future UI work stays in `ui/*.html|css|js` and is served by
  the same jar. If a heavier interactive UI is ever needed, it can still be added
  later behind `WebConfig` without changing the API contract. The BRD's
  "React or Angular" line is explicitly overridden by this decision.

## Decision 2 — Domain is a hotel chain's IT support
The assistant targets **hotel-chain IT** (front-desk, guest services, back-office,
F&B, housekeeping), not generic enterprise IT. Seed SOPs and retrieval must cover
the hotel-specific failure modes the BRD lists plus the chain's daily load:

- **PMS** (property management system) login / session / sync failures
- **Guest Wi-Fi** onboarding and captive-portal issues
- **POS / guest printers** (F&B, front desk, banquet) and receipt/kitchen printers
- **Keycard / encoder** failures (room access)
- **Booking / reservation sync** between OTA, channel manager, and PMS
- Plus the BRD's generic items that still apply: password reset, email send,
  VPN, monitor/display, mapped network drives

- **Rationale:** the BRD's example problems (PMS login, printers, Wi-Fi, VPN,
  password reset, mapped drives) are themselves hotel-IT shaped. Scoping to a
  hotel chain makes the seed corpus, symptom vocabulary, and escalation routing
  concrete and demonstrable.
- **Consequence:** seed SOPs are re-expressed for hotel context; new hotel SOPs
  are added under `src/main/resources/sop/`. The deterministic SOP-execution
  engine, `LlmPort`, conversation/case/audit model, and escalation flow are
  unchanged — only the corpus and product framing change.

## Decision 3 — Multi-tenant: per-hotel SOP instances, hotelId on every request
The chain is a **group of hotels that are individually different but share common
SOPs** ("design the database and code exactly to this requirement"). The data model
is therefore multi-tenant:

- A `hotel` table (properties in the chain) with `hotel_id`, `name`, region, etc.
- `sop`, `conversation`, `support_case`, and `audit_event` all carry a `hotel_id`.
- **SOP model = per-hotel instances (Q1 = B).** A corporate "shared" SOP is
  replicated per hotel at seed time; each hotel owns its copy and may override or
  localize it independently. There is no inheritance — a hotel's SOP is fully its
  own row with `code` unique within `(hotel_id, code)`. The SOP primary key is the
  composite `hotel_id + ":" + code` (`sop.id`), which keeps the PK stable across
  H2 and Postgres without an `ALTER TABLE ... ALTER PRIMARY KEY`.
- **Request routing = explicit `hotelId` (Q2 = A).** Every API call that scopes
  data carries `hotelId` (query param for reads, body field on `ConversationRequest`
  / SOP create). Retrieval, conversation ownership, cases, and audit are all scoped
  by `hotelId`. This works with no auth today; when SSO/AD lands, it supplies the
  `hotelId` from the user's property instead of the UI selector.

- **Rationale:** "each hotel different but some shared" maps directly to per-hotel
  SOP rows + a shared seed set, rather than a single global corpus or a complex
  inheritance tree. Explicit `hotelId` is the simplest correct tenancy boundary and
  avoids implicit session/threadlocal coupling.
- **Consequence:** all repositories expose `findByHotelId*` queries; `SopService`,
  `ConversationService`, and the controllers thread `hotelId` through; the UI has a
  hotel selector whose choice is sent on every request. `SopResponse.id()` returns
  the human `code` (composite `sop.id` is an internal key), so lookups use
  `SopService.load(hotelId, code)`, never `loadById(code)`.

## Supersedes
- BRD "Recommended Technology Stack → Frontend: React or Angular" is overridden by
  Decision 1.
- PROPOSAL.md §1 (stack options) and §8 (generic seed SOPs) are updated to match.
- The single-tenant assumption in early Phase 1A/B/C design notes is overridden by
  Decision 3 (multi-tenant `hotel_id` on every entity).
