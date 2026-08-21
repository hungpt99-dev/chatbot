# Operator Runbook — AI SOP Helpdesk

Internal IT support assistant. Employees describe a problem; the assistant walks them
through the matching SOP step by step and either **resolves** or **escalates** the case.
This runbook is for the person who runs and monitors the service.

## What it is (one paragraph)
A Spring Boot service (Java 21) with a lexical SOP retriever, a **deterministic** SOP
execution engine, conversation + case tracking, an audit trail, an optional BYOK LLM
interpreter (OpenAI-compatible), and a built-in chat UI. The AI never changes SOP state
directly — it only proposes the next step; the engine validates and applies it.

## Run it
```bash
# Offline mode (no LLM; deterministic keyword interpreter)
docker compose up --build
#   -> UI + API on http://localhost:8088/

# With an LLM key (BYOK; provider-agnostic, OpenAI-compatible)
HELPDESK_LLM_API_KEY=sk-... HELPDESK_LLM_BASE_URL=https://api.openai.com/v1 \
  HELPDESK_LLM_MODEL=gpt-4o-mini docker compose up --build
```
Data persists in the `helpdesk-data` volume (H2 file). Change the host port in
`docker-compose.yml` if 8088 is taken.

## Health & mode
```
GET /api/health   -> {"status":"UP","llmConfigured":true|false,"mode":"online|offline"}
```
- `mode: offline` ⇒ no API key ⇒ the app uses the built-in keyword interpreter. Fully
  functional; just less conversational.
- `mode: online`  ⇒ key present ⇒ the LLM interprets employee messages. The engine still
  validates every decision (see Guardrails).

## Configuration (env / application.yml `helpdesk.llm.*`)
| Var | Default | Purpose |
|---|---|---|
| `HELPDESK_LLM_API_KEY` | _(empty)_ | Provider key. Empty ⇒ offline. Never commit a real key. |
| `HELPDESK_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI, Azure OpenAI, or any compatible gateway. |
| `HELPDESK_LLM_MODEL` | `gpt-4o-mini` | Model id. |
| `HELPDESK_SEED_ENABLED` | `true` | Load the 8 seed SOPs on first start. |
| `SPRING_DATASOURCE_URL` | see compose | H2 file location. |

## Daily checks
1. `GET /api/health` returns `UP`.
2. `GET /api/cases` — glance at open/escalated cases; assign escalated ones to IT.
3. `GET /api/sops` — confirm the 8 seed SOPs are present.

## Common operations
- **List open work:** `GET /api/cases?status=ESCALATED`
- **Inspect a case:** `GET /api/cases/{reference}` (reference like `CASE-000001`).
- **Read the audit trail for a conversation:** `GET /api/conversations/{id}/audit`.
- **Add / edit an SOP:** `POST /api/sops`, `PUT /api/sops/{id}`, or edit the JSON under
  `src/main/resources/sop/` and re-seed. Each step's `branches[].branchKey` enumerates
  the *only* choices the engine will accept.

## Guardrails (why the AI can't go rogue)
- The AI may only pick a `branchKey` enumerated on the current step; anything else is
  ignored and the engine uses the default next step.
- A problem is marked **RESOLVED** only when the SOP's own terminal RESOLVE step is
  reached (evidence required). **ESCALATE** is honored at any step (handing off to a
  human is always safe).
- If no valid path forward exists, the flow **escalates** rather than inventing a step.
- A message to a resolved/escalated conversation is rejected with **409**.

## Troubleshooting
| Symptom | Cause / fix |
|---|---|
| UI loads but answers are robotic | Offline mode (no key). Set `HELPDESK_LLM_API_KEY` and restart. |
| LLM answers but steps feel wrong | Check the SOP `branches`/`defaultNext` graph; the engine follows it strictly. |
| `422` on start | No SOP matched the problem. Broaden symptoms/keywords or add an SOP. |
| `409` after resolving | Expected — the conversation is closed; start a new one. |
| App won't start (port) | Another process holds 8080/8088; `docker compose` host port or `SERVER_PORT`. |
| Data looks stale across restarts in dev | Using H2 file volume; ensure the volume is mounted (see compose). |

## Security notes
- The LLM key is BYOK via env — never stored in the repo or config files.
- No auth on the API/UI yet; intended for deployment inside a trusted corporate network.
  Add an authenticating reverse proxy before any wider exposure.
- Logs may contain employee free-text (the problem/answers). Treat as internal PII.
