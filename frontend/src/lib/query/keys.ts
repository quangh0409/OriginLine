/**
 * Centralized React Query key factory — keeps invalidation consistent across
 * hooks instead of ad-hoc string arrays scattered through components.
 */
export const queryKeys = {
  person: (id: string) => ["person", id] as const,
  personSearch: (paramsKey: string) => ["person-search", paramsKey] as const,
  /** Mức chia sẻ theo nhóm trường của MỘT người (src/lib/api/privacy-settings.ts). */
  privacySettings: (personId: string) => ["privacy-settings", personId] as const,
  /** Danh bạ dòng họ. `paramsKey` gói cả bộ lọc + trang, như `personSearch`. */
  directory: (paramsKey: string) => ["directory", paramsKey] as const,
  /** Backend-computed PersonBadge[] for one person — see src/hooks/use-person-badges.ts
   * for why this comes off the /tree projection rather than /persons/{id}. */
  personBadges: (id: string) => ["person-badges", id] as const,
  tree: (rootId: string, depth: number) => ["tree", rootId, depth] as const,
  /** Used by the canvas's per-branch lazy-load fetches (src/hooks/use-tree-canvas.ts) —
   * distinct from `tree` above because the canvas also varies direction/maxNodes,
   * which would otherwise collide with (and invalidate) unrelated single-branch fetches. */
  treeBranch: (
    rootId: string,
    depth: number,
    direction: string,
    maxNodes: number,
    /**
     * `member` | `public` — bản công khai chỉ gồm người đã khuất, nên nó KHÔNG
     * phải cùng một dữ liệu với bản thành viên. Thiếu mảnh khoá này thì cây
     * thưa mà một người đọc lúc chưa đăng nhập sẽ được dùng lại y nguyên sau
     * khi họ đăng nhập xong.
     */
    audience: string
  ) => ["tree-branch", rootId, depth, direction, maxNodes, audience] as const,
  kinship: (fromId: string, toId: string) =>
    ["kinship", fromId, toId] as const,
  kinshipRules: (branchId: string) => ["kinship-rules", branchId] as const,
  events: () => ["events"] as const,
  /** Một sự kiện đơn (F7 form sửa) — tách khỏi `events()` vì đây giữ cả `ETag`. */
  event: (id: string) => ["event", id] as const,
  notifications: () => ["notifications"] as const,
  /** Chi/ngành tree, used by the F6 search filter. Read via GraphQL
   * (`Query.branches`) because the REST contract has no /branches endpoint. */
  branches: (rootPath: string | undefined) => ["branches", rootPath ?? "root"] as const,
  /** Web Push registration state for THIS device (browser permission +
   * PushManager subscription), not a server list — the contract exposes no
   * "list my subscriptions" endpoint. See src/lib/push/subscription-store.ts. */
  pushSubscription: () => ["push-subscription"] as const,
  pushPublicKey: () => ["push-public-key"] as const,
};
