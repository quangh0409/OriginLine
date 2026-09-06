# Frontend — Cổng Thông Tin Gia Phả Dòng Họ

Next.js (App Router) PWA. Sprint 1 scope: **F0 khung** + **F1 design system**
only — no tree canvas (F2), no real person data screens (F3+). See
`D:\OriginLine\.claude\plan-giai-doan-1.html` §4 for the full workstream list.

## Stack

- Next.js 15 (App Router) + TypeScript + TailwindCSS 3 + Ant Design 5
- `@ant-design/nextjs-registry` for AntD SSR style extraction under App Router
- `@ducanh2912/next-pwa` for the PWA manifest/service worker (App Router-aware fork)
- `next-intl` for vi/en i18n, default `vi`, `localePrefix: "as-needed"` (so `/`
  is Vietnamese, `/en` is English — no forced redirect)
- `@tanstack/react-query` for all server state
- `graphql-request` for the single deep-tree GraphQL endpoint; REST for everything else
- `msw` (Mock Service Worker) so the whole app runs without a backend
- `keycloak-js` wired but **not** initialized this sprint (F8, Sprint 4)

## Getting started

```bash
npm install
cp .env.example .env.local
npm run msw:init      # generates public/mockServiceWorker.js (one-time)
npm run dev:mock       # runs against MSW mocks, no backend needed
# or: npm run dev      # expects a real backend at NEXT_PUBLIC_API_BASE_URL
npm run build
npm run typecheck
npm run lint
```

### Chạy với backend + Keycloak thật (F8)

Đăng nhập thật đã nối xong. Ba điều **bắt buộc**, sai một cái là hỏng cả luồng:

- **Cổng phải là 3000.** Client Keycloak `giapha-frontend` chỉ khai
  `redirectUris = http://localhost:3000/*` và `webOrigins = http://localhost:3000`.
  Đổi cổng ⇒ Keycloak từ chối chuyển hướng về.
- **Dùng `localhost`, không dùng `127.0.0.1`.** Với Keycloak đó là hai origin
  khác nhau và chỉ `localhost` nằm trong `webOrigins`.
- **`NEXT_PUBLIC_API_MOCKING` phải khác `enabled`.** Bật MSW là service worker
  chặn mất chính những lời gọi đang cần đi tới backend; đồng thời cờ
  `AUTH_ENABLED` (xem `src/lib/auth/keycloak.ts`) tắt và luồng Keycloak không
  chạy. Hai cơ chế loại trừ nhau theo thiết kế.

```bash
NEXT_PUBLIC_API_MOCKING=disabled NEXT_PUBLIC_API_BASE_URL=http://localhost:8090 NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8081 NEXT_PUBLIC_DEFAULT_ROOT_ID=3b54e802-a966-5570-b69b-3d01b808b0e3 npx next dev -p 3000
```

`NEXT_PUBLIC_DEFAULT_ROOT_ID` là thủy tổ của bộ dữ liệu demo (Nguyễn Đình
Bách). Bỏ trống thì trang phả đồ rơi về `p-001` — id của bộ giả lập, **không
tồn tại** trong CSDL thật. Chưa có endpoint "thủy tổ của dòng họ" trong hợp
đồng Giai đoạn 1, nên đây tạm là một biến môi trường.

Tài khoản dev: `admin.giapha` / `truongchi` / `thanhvien`, mật khẩu đều
`giapha123`.

**Chế độ khách vẫn mở được ứng dụng.** `keycloak.init()` dùng
`onLoad: "check-sso"` chứ không phải `login-required`, nên người chưa đăng
nhập không bị đá sang trang đăng nhập. Backend hiện đòi token trên mọi
endpoint nhân khẩu (`SecurityConfig` chỉ mở `/api/v1/public/**`, mà nhánh ấy
chưa có controller nào), nên khách thấy lời mời đăng nhập thay vì phả đồ.

### Bộ chuyển vai dev (chỉ ở chế độ MSW)

Khi chạy `npm run dev:mock`, chưa có Keycloak nào để hỏi nên vai trò do bộ
chọn trên thanh đầu trang giả lập, và `src/lib/api/http.ts` chuyển tiếp nó
thành header `x-mock-role` (`guest` | `member` | `branch-head` | `admin`,
mặc định `guest`) — xem `src/lib/api/dev-role.ts`. Header này **không bao giờ**
đi cùng một bản chạy nói chuyện với backend thật: `MOCKING_ENABLED` và
`AUTH_ENABLED` được định nghĩa để loại trừ lẫn nhau, và có test chốt điều đó
(`tests/unit/api/http-auth.test.ts`). Ở bản chạy thật, vai trò đọc từ claim
`realm_access.roles` của JWT (`src/lib/auth/roles.ts`).

## Directory structure

```
src/
  app/
    [locale]/            # next-intl locale segment = the App Router root
      layout.tsx          # <html>/<body>, fonts, providers, i18n messages
      page.tsx             # home page
      not-found.tsx
    providers.tsx          # AntdRegistry > ConfigProvider > ReactQuery > MSW
    globals.css
  components/
    layout/                 # AppShell, Header, LanguageSwitcher
    notifications/           # NotificationBell (header bell + unread badge)
    home/                     # Landing page content
  hooks/                       # React Query hooks, one per resource
  lib/
    api/                        # typed REST client (fetch wrapper + per-resource modules)
    graphql/                     # graphql-request client + deep tree query
    query/                        # QueryClient factory + query key registry
    auth/                          # Keycloak adapter (config only, not initialized)
  mocks/                            # MSW: handlers per endpoint + fixture data
  i18n/                              # next-intl routing/navigation/request config
  styles/                             # color tokens (single source for Tailwind + AntD)
  types/api.ts                        # hand-written API contract types (see below)
worker/index.ts                        # custom service worker source (Web Push handlers),
                                         merged into public/sw.js at build time
messages/{vi,en}.json                   # translation catalogs
```

## Contract status (important for Sprint 2)

`contracts/` (OpenAPI 3.1 + GraphQL SDL, version `1.0.0-sprint1`) landed
partway through this workstream. **`src/types/api.ts` has been reconciled
against it by hand** — type names deliberately mirror the OpenAPI
`components.schemas` names 1:1 (`PersonDto`, `TreeProjection`, `KinshipResult`,
...) so a later `npx openapi-typescript contracts/openapi.yaml` swap is a
low-diff change, not a rewrite. `src/lib/api/*`, `src/mocks/data.ts`, and every
`src/mocks/handlers/*.ts` were updated to match: offset pagination
(`{ items, page: { page, size, totalElements, totalPages, hasNext } }`),
RFC 7807 `Problem.code`-based error branching, `ETag`/`If-Match` optimistic
locking on `/persons/{id}`, the kỵ húy two-call `409 KY_HUY_CONFLICT` →
`confirmTabooOverride` flow, `404` (not `403`) for guests requesting a hidden
living person, and `GET /push/public-key` for VAPID.

**Not yet reconciled / worth re-checking against contracts/ as it evolves:**

- `src/mocks/privacy.ts` is a simplified stand-in for the backend's tier
  logic (branch-scope opt-in, `RESTRICTED` for minors, self-vs-admin T3
  access) — good enough to exercise every `VisibleTier` in mocks, not a spec
  to copy into real code.
- `src/lib/graphql/queries.ts` (`TreeProjectionQuery`) only selects a
  Tier-1-safe field subset of GraphQL's `Person` type for the canvas use
  case; it hasn't been run against a real GraphQL server yet.
- The `CreatePersonRequest`/`UpdatePersonRequest` types are modeled fully,
  but no form (F4) consumes them yet this sprint — first real usage should
  double-check `initialRelationships`/`RelationshipLinkInput.otherPersonRole`
  against a live backend response.
- Keycloak client id `giapha-frontend` (public SPA client, PKCE, no secret)
  is still an assumption — contracts/openapi.yaml documents the JWT shape
  but not the Keycloak client registration; confirm with infra's realm export.

See `contracts/README.md` for the full rulebook (especially §7, "điểm BE và
FE dễ hiểu sai nhau") before changing anything in `src/lib/api` or `src/mocks`.

## Privacy tiers — how the mocks model them

Trên bản chạy MSW (`npm run dev:mock`) không có Keycloak, nên handler đọc
header **phi tiêu chuẩn** `x-mock-role` (`guest` | `member` | `branch-head` |
`admin`, mặc định `guest`) để quyết định trả gì — xem
`src/mocks/handlers/role.ts`. Header này không tồn tại trên backend thật và
`src/lib/api/http.ts` chỉ gắn nó khi `MOCKING_ENABLED`; ở bản chạy thật
(`AUTH_ENABLED`) chỗ đó là `Authorization: Bearer <JWT>` và
`PrivacyTierService` của backend là nguồn chân lý duy nhất.

Cả nhánh giả lập này vẫn còn sống có lý do: nó là thứ duy nhất cho phép chạy
`npm run dev:mock` và toàn bộ 461 test của Vitest mà không cần dựng Keycloak.
Xoá nó là mất luôn khả năng phát triển ngoại tuyến — chỉ xoá khi có một bộ
đồ giả cho Keycloak thay thế.

The default (`guest`, no header) intentionally matches the legal default:
**no living person is returned at all.** `src/mocks/data.ts` keeps two
representations of the one living-person fixture — a Tier-1-only summary and
a full record — so member/admin roles can be exercised without inventing a
"blurred" placeholder for guests. Components must never render a placeholder
implying hidden data exists; the correct rendering of a hidden field is to not
render it.

## PWA / Web Push scaffolding

- `public/manifest.json` — **icons in `public/icons/` are 1x1 placeholder
  PNGs**, not real artwork. Replace before any real Lighthouse PWA/installability
  check; the declared manifest sizes (192/512) do not match the actual pixel
  dimensions of the placeholder files.
- `worker/index.ts` — custom service worker source with `push` and
  `notificationclick` listeners already implemented against a guessed payload
  shape (`{ title, body, url, notificationId }`). Gets merged into the
  generated `public/sw.js` by `@ducanh2912/next-pwa` (`customWorkerDir: "worker"`
  in `next.config.js`). Sprint 4 (F7) adds the subscription flow
  (`pushManager.subscribe` → `POST /api/v1/push/subscriptions`) and should
  reconcile the payload shape with the backend's actual `WebPushAdapter` output.
- The service worker is disabled in `next dev` by default (set
  `NEXT_PUBLIC_ENABLE_PWA_DEV=true` to test it locally) and always enabled in
  production builds.

## Tests

```bash
npm run test        # vitest (unit + component), 461 tests / 36 files
npm run typecheck
npm run lint
npx playwright test # e2e trên MSW, 45 tests; tự dựng `npm run dev:mock` ở cổng 3100
```

### E2E đối chứng hệ thống thật — `npm run e2e:real`

`e2e/real-auth/keycloak-login.spec.ts` là bài kiểm DUY NHẤT đi hết vòng
giao diện → Keycloak → backend → PostgreSQL: mở `/vi/tree` với tư cách khách,
bấm Đăng nhập, điền form Keycloak thật, quay lại và khẳng định phả đồ hiện
nhân khẩu thật từ CSDL, gọi `/api/v1/notifications` (điểm cuối cần quyền) và
đòi 200 chứ không 401, rồi đăng xuất về trạng thái khách sạch.

Nó **cố ý phụ thuộc hạ tầng sống** nên nằm ở một cấu hình Playwright riêng
(`playwright.real.config.ts`, cổng 3000, MSW tắt) và bị `testIgnore` loại khỏi
bộ chạy trên MSW. **Không chạy được trong CI** cho tới khi CI dựng được
Keycloak + backend + Postgres.

```bash
cd infra && docker compose up -d --wait postgres rabbitmq redis minio
# backend chạy ở 8090 (8080 đang bị Apache/XAMPP chiếm), Keycloak ở 8081
cd frontend && npm run e2e:real
```

Ghi đè được bằng biến môi trường: `E2E_API_BASE_URL`, `E2E_KEYCLOAK_URL`,
`E2E_KEYCLOAK_REALM`, `E2E_KEYCLOAK_CLIENT_ID`, `E2E_DEFAULT_ROOT_ID`,
`E2E_USERNAME`, `E2E_PASSWORD`. Ảnh chụp màn hình từng bước lưu ở
`.playwright-mcp/real-auth/`.

Hai cái bẫy đã gặp:

- `Could not find the module ... in the React Client Manifest` là `.next` cũ —
  `rm -rf .next` rồi chạy lại.
- Máy chủ dev sập kèm `spawn node.exe ENOENT` là cạn tài nguyên — dọn tiến
  trình `node` thừa rồi chạy lại.

### Measuring NFR-1 ("cây render dưới 2s")

`e2e/tree-performance.spec.ts` runs against `next dev`, where the un-minified
bundle, HMR and React StrictMode's double render add seconds that do not exist
in production — so on the dev build it only enforces a wide regression ceiling.
The NFR itself is measured against a production build:

```bash
node e2e/tools/stub-api.mjs                                   # fake API on :3200
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:3200 npm run build
PORT=3300 npm run start
node e2e/tools/prod-perf.mjs                                  # medians over 5-7 runs
```

The stub server exists because MSW is hard-disabled in production builds
(`NODE_ENV === "production"` in `src/mocks/msw-provider.tsx`), so a production
page has no mocks to talk to. It replays a `/tree` projection captured from the
same mock graph (`e2e/tools/tree-p-001.json`), which keeps the measurement
comparable with the dev-build numbers.

Last measured 2026-09-05 (median of 7 runs): desktop 1440x900 —
navigation-to-first-card **807 ms**, render half **709 ms**; Pixel 5 —
**750 ms** / **620 ms**. Budget is 2000 ms, so **NFR-1 passes**.

## What Sprint 2 (F2 canvas) needs to know

- `useTree` (`src/hooks/use-tree.ts`) / `treeApi.getTree` cover the flat REST
  `TreeProjection` (`nodes[]` + `edges[]`, React-Flow-ready); the GraphQL
  deep-query path is drafted in `src/lib/graphql/queries.ts`
  (`TreeProjectionQuery`) but untested against a real server.
- `TreeNode.hasMoreDescendants` (+ `childCount`) is the lazy-load signal —
  call `/tree?rootId={thatNodeId}` again to expand. **Always check
  `TreeProjection.meta.truncated`** before assuming a fetched branch is
  complete; a truncated response is not an error, just incomplete.
- `TreeNode.depth` can be **negative** when `direction = ANCESTORS`/`BOTH` —
  negative means an elder generation, not an error value.
- `RelType` is directional (`from -> to` via `TreeEdge.source`/`target`);
  `PARENT_BIO`/`PARENT_ADOPT` point parent→child. Rendering the edge
  reversed flips the whole tree visually.
- Dâu/rể are **not** a `RelType` — don't invent one. They're derived
  (`SPOUSE` + bloodline) and already surfaced via `TreeNode.badges`
  (`DAU`/`RE`/`DICH_TON`/`THUA_TU`/`KE_TU`/`CON_NUOI`/`TUYET_TU`/`TRUONG_CHI`/`DECEASED`).
- Do not add kinship-title computation to the canvas — `danh xưng` always
  comes from `useKinship` (`GET /kinship`); tree nodes intentionally carry no
  title field.
- Merging multiple `/tree` calls (one per expanded branch) into one canvas
  graph requires de-duplicating `nodes`/`edges` by `id` — the same node can
  legitimately appear in more than one projection response.
