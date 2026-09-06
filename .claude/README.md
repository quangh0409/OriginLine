# .claude — OriginLine project configuration

Adapted from a personal Claude Code toolkit for **this** project's stack
(Java 21 + Spring Boot 3.x + React/React Flow + PostgreSQL/Apache AGE + RabbitMQ +
Redis + MinIO + Keycloak).
The macOS-only hooks, `$HOME`-global paths, and NestJS/yarn assumptions from the
original kit were intentionally dropped.

## Layout
- `settings.json` — permissions (Maven/Gradle, npm, docker, git, psql), a
  PreToolUse hook that blocks writing secrets, and `acceptEdits` default mode.
- `agents/` — project-specific subagents, in two layers:
  - **Role layer** (SDLC ownership): `po` (sonnet) scope & priority · `ba` (opus)
    requirements & domain · `pm` (sonnet) schedule & risk · `dev` (opus)
    implementation · `test` (sonnet) quality gate.
  - **Specialist layer** (technical depth): `backend-engineer` (opus) Spring Boot,
    JPA/Postgres, Keycloak, RabbitMQ · `graph-engineer` (opus) Apache AGE, Cypher,
    LCA + danh xưng rule engine · `frontend-engineer` (sonnet) React/Next.js PWA +
    React Flow tree canvas · `code-reviewer` (opus) correctness + architecture audit.
  - **Superseded, still on disk:** `planner` (now `pm` + `dev`) and `qa-tester`
    (now `test`).
- `memory/` — persistent architectural decisions and knowledge.
- `commands/` — project slash commands (add as needed).

## Delegation guide

Route by lifecycle stage first, then by technology:

- What to build / priority / MVP boundary → **po**.
- What it precisely means, FR/NFR, domain & legal constraints → **ba**.
- When it lands, dependencies, risk, phase readiness → **pm**.
- Build it → **dev**, which escalates for depth: tree / kinship / Cypher →
  **graph-engineer** · server APIs, auth, DB, queues → **backend-engineer** ·
  UI and the phả đồ canvas → **frontend-engineer**.
- Verify it → **test**. Before a PR → **code-reviewer**.
- A vague multi-domain feature goes `po` → `ba` → `pm` before any code.

Subagents cannot call other subagents — the main session does the routing.

The authoritative architecture and domain rules live in the root `CLAUDE.md`,
`BA_Gia_Pha_Dong_Ho_v2.html` (BA v2.0 baseline) and `TDD_Gia_Pha_Dong_Ho.html` (TDD v1.0).
`Spec_Du_An_Gia_Pha_Dong_Ho.html` is the **superseded** original spec — where it disagrees,
BA v2 / TDD win. Keep generated docs in `.claude/`, not the repo root.
