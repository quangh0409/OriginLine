# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

**Phase 1 code-complete; bulk onboarding and per-field privacy landed on top of it.**
All backend workstreams and frontend screens are implemented and tested, plus the
`dataimport` pipeline (Excel → staging → validation → commit → rollback) and the `V8`
per-field privacy model. See "Implementation status" below for the measured numbers.

Three things block progress and **none of them are code**: the Zalo Official Account
needs a legal entity to register it; the 34-province catalogue needs the statutory text
(a wrong code makes the population report wrong *silently*); and the Hội đồng Tộc biểu
owes rulings — deletion rights for dâu/rể after divorce (**must be settled before a real
clan's data is imported**, since changing the rule after 1,500 people are in is no longer
a code change), the Dâu/Rể badge colours, whether the council may read a `PRIVATE`
phone number, and whether a living person's Hán-Nôm name stays visible (today it is).
The plan calls installing CI the fourth such item; that one is now done.

The documentation set holds the full BA → design paper trail for
**"Hệ Thống Quản Lý Gia Phả & Cổng Thông Tin Dòng Họ"** (a family-genealogy
management system and clan information portal):

| File | Role |
|---|---|
| `BA_Gia_Pha_Dong_Ho_v2.html` | **BA v2.0 — the baseline requirements.** Bilingual VI–EN. FR/NFR codes, stakeholders, data model, resolved decisions. |
| `TDD_Gia_Pha_Dong_Ho.html` | **TDD v1.0 — the technical design.** Package structure, data dictionary, AGE graph model, class design, sequence flows, implementation checklist. |
| `Danh_Gia_Gop_Y_Spec_Gia_Pha.html` | Critique of the original spec that drove v2 (rationale for the architecture changes). |
| `Spec_Du_An_Gia_Pha_Dong_Ho.html` | **Superseded** original spec (v1). Historical context only — where it conflicts with BA v2 / TDD, v2 wins. |
| `.claude/plan-giai-doan-1.html` | **Phase 1 implementation plan.** Backend workstreams W0–W7, frontend F0–F9, 4-sprint schedule, exit criteria, lunar-fixture spec (§9), demo-data spec (§10). |
| `README.md` | How to bring the stack up from a clean machine; ports, dev accounts, and the **Apache AGE session traps** (`LOAD 'age'` + `search_path`). Read §4 before writing Cypher. |
| `AGENTS.md` | Agent routing: role layer (`po`/`ba`/`pm`/`dev`/`test`) and specialist layer (`backend-engineer`/`graph-engineer`/`frontend-engineer`/`code-reviewer`). |

**Precedence when documents disagree: TDD v1.0 → BA v2.0 → review → original spec.**

The documents are in Vietnamese and domain-heavy (Vietnamese kinship, lunar calendar,
ancestral rites). Preserve Vietnamese domain terminology in code/comments where it
carries meaning that doesn't translate cleanly (e.g. `danh xưng`, `chi/ngành/cành/nhánh`,
`kỵ húy`, `đích tôn`).

## Target architecture (BA v2 §9 + TDD)

Modular monolith, DDD, **single primary datastore**:

- **Frontend:** React / Next.js as a **PWA** (diaspora members are mobile-first),
  + React Flow / D3.js for the genealogy tree canvas (zoom, drag, collapse/expand,
  lazy loading across thousands of nodes). TailwindCSS / Ant Design, warm traditional
  palette. Bilingual VI–EN UI.
- **Backend:** Java 21 + Spring Boot 3.x, modular monolith of DDD bounded contexts,
  each **Hexagonal** (domain / application / infrastructure / api). Dependencies flow
  one way: `api → application → domain`; infrastructure implements ports the domain
  declares. **Domain classes are plain POJOs — no Spring/JPA annotations.**
- **PostgreSQL 16 + Apache AGE — the single store.** The genealogy tree is a graph
  inside AGE (graph name `giapha_graph`), queried with **Cypher running inside
  Postgres**; LCA is an AGE Cypher query. Relational tables hold everything else, with
  JSONB for flexible per-person attributes, `ltree` for branch hierarchy/RBAC scope,
  and full-text search with `unaccent` for Vietnamese name lookup. ACID throughout.
- **Async — RabbitMQ + Spring Scheduler:** queues outbound `giỗ` (death-anniversary)
  reminders. Retry + **dead-letter queue** so notifications aren't lost when the
  gateway fails; consumers idempotent; the web request path never blocks on a send.
- **Cache — Redis:** cached tree projections and sessions. (Redis is *not* the search
  engine — search is Postgres FTS.)
- **Object storage — MinIO** (self-hosted, S3-compatible): portraits, stele rubbings,
  grave photos, scanned paper genealogies. **Never store media blobs in the database.**
- **Auth — Keycloak** (OAuth2/OIDC) + social login (Google, Zalo). The backend
  consumes JWTs and maps `keycloak_sub → app_user → person`.
- **Observability:** OpenTelemetry + Prometheus + Grafana + Loki.
- **API:** GraphQL for deep nested tree queries (clients choose depth); REST for the
  rest. Errors as RFC 7807 Problem Details; URL versioning `/api/v1`.
- **Lunar calendar:** the **Hồ Ngọc Đức algorithm** for lunar↔solar conversion at
  GMT+7, with correct handling of leap months and solar terms (`tiết khí`). Package it
  as a standalone tested service — it serves both reminders and `sao hạn` lookups.

### Why not Neo4j

The original spec prescribed Neo4j. **BA v2 §12 reversed that decision:** at a scale of
tens of thousands of nodes the "recursive CTE will time out" argument doesn't hold, and
a second database means extra ops, backup, sync, and licensing cost. Apache AGE gives
Cypher and LCA inside the one Postgres instance. **Neo4j remains only a documented
fallback** if advanced graph algorithms (GDS) are ever genuinely needed. Keep module
boundaries clean so the graph adapter could be swapped.

## Bounded contexts (TDD §2)

Root package `vn.giapha`. Package by feature first, layer second.

`genealogy` (Person, Relationship, Branch, PersonName) · `kinship` (KinshipRuleSet,
KinshipResolver, LcaService) · `calendar` (LunarConverter, SolarTerm) · `events`
(Event, ReminderPlan, ReminderJob) · `notification` (Channel, Provider port,
NotificationLog) · `membership` (AppUser, Role, BranchAssignment, ChangeRequest) ·
`heritage` (HeritageItem, Hall, Grave, MediaAsset) · `fund` (FundAccount, Donation) ·
`reporting` (read models) · `audit` (AuditLog) · `shared` (VOs, security, exceptions).

Contexts talk through public application services or internal domain events — never
by reaching into another context's repository.

## Core domain rules to respect

Non-obvious constraints that any implementation must honor:

- **Soft delete only** for genealogy nodes. Never hard-delete a person — it breaks
  tree links. Deletion is a flag; the node stays in the graph.
- **Audit versioning:** record edit history for genealogy and core data (who changed
  what, when, before/after).
- **Branch-scoped RBAC is first-class.** Roles: System Admin (technical, global) ·
  Hội đồng Tộc biểu / Tộc trưởng (whole clan) · Trưởng Chi/Ngành (assigned branch
  **only**) · Member (scoped read, own profile, correction requests) · Guest (public
  info only). Authorization checks must test the `ltree` branch scope, not just the
  role. **Clan titles (Tộc trưởng, Trưởng chi — by lineage/đích tôn) are separate from
  the technical admin role**; one person may hold both.
- **Kinship rules are data, not code (FR-1.3a).** The `danh xưng` rule engine is
  configurable per clan and region (Bắc/Trung/Nam), stored in `kinship_rule_set` /
  `kinship_rule` with `DEFAULT → REGION → CLAN → BRANCH` inheritance and override,
  editable by the clan council. Never hard-code kinship titles.
- **Complex kinship:** the model must represent polygamy/polyandry (multiple spouses,
  with `spouse_order`), adopted vs. biological children, in-laws (`dâu`/`rể`),
  remarriage/step-children, lineage discontinuity (`tuyệt tự`/`kế tự`), and heirship
  (`đích tôn`/`thừa tự`).
- **Multi-layer names:** a person has many names — `HUY` (taboo), `TU`, `HIEU`, `THUY`
  (posthumous), `THUONG_GOI`, `PHAP_DANH` — plus optional Hán-Nôm and a generated
  unaccented column for search.
- **Kỵ húy warning (FR-1.6):** warn when a new name collides with an ancestor's taboo
  name; allow override with confirmation.
- **Daughters and the maternal line are recorded fully and equally with sons** — this
  is the system default, a resolved decision in BA v2 §12.
- **Lunar-date reminders** fire at 7 / 3 / 1 days before an event, targeted to members
  of the same branch (`nhánh`/`chi`). `death_lunar` is the source of truth for `giỗ`.
- **Privacy tiers (PDPD / Nghị định 13/2023) — BA v2 §10.** Deceased persons are
  public. Living persons are hidden by default and revealed in tiers: T1 name +
  generation + core relations (logged-in members; **guests see no living person**);
  T2 birth year, occupation, province (same branch or scoped); T3 phone, email, full
  address/DOB, photos (self + admin + explicit opt-in only). Minors maximally hidden.
  Lawful erasure is served by **anonymization, not deletion** — strip Tier-3 data,
  keep the lineage node so the tree doesn't break.
- **Graph/relational consistency:** AGE edges are the source of truth for relationships;
  the `relationship` table mirrors them for FK constraints, audit, and plain SQL.
  Both are written **in the same transaction**.

## Roadmap phasing (BA v2 §11)

1. **Core & tree + MVP** — Postgres+AGE schema, LCA & `danh xưng` rule engine,
   multi-layer person profiles, interactive tree UI, `giỗ` reminders **via Zalo only**.
   Ends in a genuinely usable MVP.
2. **RBAC & automation** — branch-scoped RBAC, correction-request approval workflow,
   SMS + Web Push via RabbitMQ, `kỵ húy`, GEDCOM import/export.
3. **Heritage, graves & fund** — heritage library, từ đường, grave records (GPS), clan
   fund & donation ledger, newsletter, hall of fame, `sao hạn` / `văn khấn` utilities.
4. **Hardening & handover** — load testing on large trees, observability, security &
   PDPD review, training, handover.

**Later phase — AI/LLM layer** (explicitly out of scope for phases 1–4, but keep the
architecture open for it): natural-language kinship lookup, Hán-Nôm OCR, ritual-text
generation, duplicate detection on bulk import.

TDD §12 recommends this demo-first build order: **docker-compose infra → Flyway
migrations → AGE adapter → genealogy → kinship → tree UI**, then lunar calendar +
reminders, then RBAC.

## Build commands

> ⚠️ **JDK 21 is required and is not installed on the current dev machine** — `java`
> on PATH is 1.8.0_202 and `JAVA_HOME` points at it, so `mvnw` fails with a wall of
> "illegal start of expression" errors on Java 21 `switch` expressions. That is a
> toolchain problem, not a code problem. Install Temurin/Zulu 21 and repoint
> `JAVA_HOME` before trusting any local build result.

Infrastructure must be up before the backend will start (`README.md` has the full
walkthrough and the dev credentials):

```bash
cd infra && docker compose up -d --wait postgres rabbitmq redis minio
```

| Where | Command | Notes |
|---|---|---|
| `backend/` | `./mvnw spring-boot:run` | Windows: `.\mvnw.cmd`. Health: <http://localhost:8080/actuator/health> |
| `backend/` | `./mvnw test` | JUnit 5; Testcontainers config in `TestcontainersConfiguration.java` |
| `backend/` | `./mvnw -o compile` | Offline compile — the fast inner-loop check |
| `frontend/` | `npm run dev:mock` | Runs against MSW mocks, no backend needed |
| `frontend/` | `npm run typecheck` / `npm run lint` | Both must be green |

Flyway migrations live in `backend/src/main/resources/db/migration` and own the entire
schema. `infra/init/` only installs extensions at `initdb` time — do not put tables there.

## Implementation status (Phase 1)

Track against the W/F workstreams in `.claude/plan-giai-doan-1.html`.

- **Backend W0–W7 all implemented and tested.** W0 infra + shared kernel + `config/` ·
  W1 Flyway `V1..V7` + `R__seed_kinship_rules_default.sql` · W2 `genealogy` end to end ·
  W3 `kinship` (resolver, `RuleSetJpaRepository` inheritance merge) · W4 `calendar`
  (Hồ Ngọc Đức converter, solar terms, can/chi) · W5 `events` + `notification`
  (RabbitMQ topology, DLQ, retry, idempotent consumers, reminder scheduler, Web Push,
  plus `POST /api/v1/admin/reminders/{generate,dispatch}` so the giỗ pipeline can be
  driven without waiting for the 01:30 job) · W6 `membership` + `audit` (branch-scoped
  RBAC, change-request approval, lawful erasure by anonymization) · W7 tree projection
  + Redis cache + unaccented FTS.
- **Frontend F0–F9 implemented.** Shell, design tokens, phả đồ canvas, person profile,
  edit form with kỵ húy dialog, danh xưng lookup, search, events + notification centre,
  auth & guest mode, PWA.
- **`dataimport` (bulk Excel onboarding) is implemented end to end** — a clan cannot be
  entered one person at a time, so this is what makes the system usable at all. Reader
  (POI event API, `.xlsx` only, detected by file signature) → staging tables → 15 blocking
  rules + 11 warning rules → duplicate/kỵ-húy scan reusing `genealogy`'s scorers → commit
  into the phả in one all-or-nothing transaction (AGE edges and `relationship` rows
  together, topologically ordered by đời) → rollback. Flyway now runs `V1..V13`.
  Per-branch Excel templates are generated from the same `ImportColumn`/`MarriageColumn`
  enums the reader consumes, so generator and reader cannot drift.
- **Privacy is per-field-group (`V8`), not one enum per person.** Five groups
  (`occupation`, `residenceProvince`, `residenceFull`, `contact`, `birthDetailAndPhoto`)
  × three scopes (`PRIVATE`/`BRANCH`/`CLAN`) in one JSONB column, default-closed so a new
  group needs no migration and has no exposure window. `PrivacyLevel` is gone from the
  contract. `VisibleTier.atLeast()` is **deleted** and `tierFor()` is replaced by
  `Optional<PersonVisibility>` — "guest looking at a living person" is no longer a
  representable state, so the old unwritten "remember to call `canSee()` first" rule is
  now held by the compiler. `meta.visibleTier` is a summary derived from the filter
  result; never branch on it to predict which fields are present.
- **Test status.** Backend `mvn test` → **2304 tests, 0 failures, 0 errors, 0 skipped**,
  including Testcontainers integration tests against real Postgres + AGE — among them the
  AGE-edge/`relationship` same-transaction invariant (written together, rolled back
  together), the per-field privacy matrix (5 groups × 3 scopes × 4 viewer kinds), the
  legacy-privacy migration proving it discloses nothing new, and the full import pipeline
  driven **only through HTTP** (no test writes to a table to get past a gate).
  Frontend: **1297 vitest** with **4 deliberate reds** (2 Dâu/Rể badge hexes and 2
  `accent` contrast cases, all awaiting a Hội đồng ruling — CI pins this set exactly, so
  an extra red *or* one of these turning green fails the gate), `tsc` and `next lint`
  clean, `next build` green, **134 Playwright E2E** across desktop-chromium and Pixel 5.
- **Cross-context calls go through named interfaces, per type, never per package.**
  `genealogy` exposes exactly 11 types in 3 facades — `"do-trung"` (duplicate/taboo
  screening), `"ghi-pha"` (bulk write), `"loc-rieng-tu"` (privacy-filtered disclosure) —
  plus the `domain.event` package. `genealogy.application` itself is **not** a named
  interface: `Person`, `PersonRepository` and `PrivacyTierService` stay inside. Widen this
  surface only with a reason you can state; each widening is individually reasonable and
  together they erase the boundary.
- **CI exists** (`.github/workflows/ci.yml`): four gates — backend `mvn test`, frontend
  static (`typecheck` + `lint` + vitest against a pinned expected-failure list),
  **`next build` as its own gate**, and E2E as a 9-way matrix, one spec file per job.
- **NFR-1 met.** Navigation → first person card, median of 7 runs on a production build:
  807 ms desktop / 750 ms Pixel 5, against a 2000 ms budget. Measure it on
  `next build && next start` only — a dev bundle reports 3000–4000 ms and means nothing.
- **Not started:** `heritage` / `fund` / `reporting` — Phase 3 by design. Those packages
  hold only `package-info.java`.

### Traps that cost real time here — read before debugging

- **`git stash` cannot produce a baseline in this tree, and Docker cannot run beside
  `next dev`.** Most of the recent work is *untracked*, so stashing tracked paths reverts
  `messages/*.json` to HEAD while leaving the components that read those keys — the app
  then fails to compile translations and Playwright dies on a `webServer` timeout rather
  than on a test assertion. Use a separate worktree or a scratch checkout. Separately, the
  dev box has ~14 GB and the Docker infra (5 containers) plus a `next dev` that grows to
  ~2.9 GB do not fit together: `docker compose stop` before a long frontend run, and start
  it again before `mvn test` (Testcontainers needs it).

- **`next dev` is the only environment the E2E suite can run in, and it hides a
  release-blocking class of bug.** The PWA service worker is disabled in dev but active
  in a production build, where it claims the scope MSW needs — run the suite against
  `next start` and 92 of 134 tests fail with no data, not because anything is broken.
  So `next build` is never exercised by any test, and a missing `<Suspense>` around
  `useSearchParams()` in a header component (present on *every* page) sat in main
  undetected through several "all green" waves. `next build` must be its own required
  gate even though no test runs on it; the error it prints names an innocent page
  (`/vi/notifications`), never the component at fault.
- **The full Playwright suite cannot run in one process on this machine.** `next dev`
  grows to ~2.9 GB as it compiles routes across ~134 tests and the OS kills the run —
  it is not flakiness and not contention. Run one spec file per invocation, killing
  stray `node.exe` between them: all 9 files pass individually (134 total, exit 0 each).
  Also kill stray node first — a leftover dev server holds port 3100 and Playwright
  reports only `Process from config.webServer exited early`, and delete `.next` when
  switching between a production build and dev, or `next dev` will not start.

- **Testcontainers could not see Docker** on this machine for two stacked reasons: TC
  1.19.8 predates `docker context` (it only probes the default named pipe), and Docker
  Engine 29 sets `MinAPIVersion=1.44`, which makes docker-java 3.3.6 get an HTTP 400 that
  TC reports as "Could not find a valid Docker environment". Patched in the test tree via
  a ServiceLoader-registered strategy. A skipped `GiaPhaApplicationTests` was never
  evidence that the machine lacked Docker.
- **`JsonbAttributeConverter` used to map `null → "{}"`.** One converter serves three
  columns with different constraints; that mapping is right for `attributes` (NOT NULL)
  and fatal for the nullable `birth_lunar` / `death_lunar`, so `POST /persons` could not
  add a single living person. Keep null as null in both directions.
- **`VisibleTier.atLeast()` returns `true` for every comparison when the tier is
  `PUBLIC`.** Deceased persons are always `PUBLIC`, so `tier.atLeast(T3)` is a trap —
  compare exactly (`tier == T3`) whenever the question is "may this caller see Tier-3
  data", or guests read a living relative's phone number off an ancestor's profile.
- **`Map.copyOf` throws NPE on null values.** It silently broke the whole change-request
  flow, because a freshly submitted request always has `reviewerId == null`.

