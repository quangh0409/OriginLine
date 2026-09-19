/**
 * Typed shapes for the backend API, hand-transcribed from the now-authoritative
 * contract at `D:\OriginLine\contracts\openapi.yaml` (+ `schema.graphqls` for the
 * GraphQL transport). Names deliberately mirror the OpenAPI `components.schemas`
 * names 1:1 (PersonDto, TreeProjection, KinshipResult, ...) so that running
 *
 *   npx openapi-typescript contracts/openapi.yaml -o src/types/api.generated.ts
 *
 * later is a low-diff swap rather than a rewrite. This file was reconciled
 * against contract version `1.0.0-sprint1` (contracts/README.md §8 changelog).
 * If contracts/ changes, re-diff against this file — see contracts/README.md
 * §1 "đổi contract phải báo cả hai bên" before assuming a mismatch is a bug here.
 *
 * ---------------------------------------------------------------------------
 * Privacy tiers (BA v2 §10 / Nghị định 13/2023) — READ BEFORE RENDERING:
 *
 * REST: a field the caller isn't allowed to see is OMITTED from the JSON
 * entirely (not `null`, not `""`). Every optional (`?`) field below reflects
 * that — omission is the correct, final rendering, and "no data" vs. "no
 * permission" are deliberately indistinguishable. Never render a placeholder
 * implying hidden data exists.
 *
 * GraphQL: the same hidden fields come back as `null` instead (GraphQL has no
 * "absent field" concept) — same semantics, different transport. See
 * src/lib/graphql for the GraphQL-side types where this matters.
 *
 * Guests never receive a living person at all: `GET /persons/{id}` for a
 * hidden living person is `404`, not `403` (existence itself is not
 * disclosed). `/tree`, `/persons/search`, `/events` silently exclude living
 * persons for guests — the tree a guest sees can legitimately have holes.
 * ---------------------------------------------------------------------------
 */

// ============================================================================
// SHARED ENUMS
// ============================================================================

export type Gender = "MALE" | "FEMALE" | "UNKNOWN";

/** Multi-layer Vietnamese naming (BA v2 domain rule — never collapse to one name). */
export type NameType = "HUY" | "TU" | "HIEU" | "THUY" | "THUONG_GOI" | "PHAP_DANH";

/**
 * Mức chia sẻ của MỘT nhóm trường, do **chính chủ thể** chọn
 * (contracts/openapi.yaml → `ShareScope`).
 *
 *  - `PRIVATE` — **Riêng tư**: chỉ chính chủ và Hội đồng Tộc biểu / `ADMIN`.
 *    KHÔNG phải "chỉ mình tôi" — và giao diện phải nói đúng điều đó ngay cạnh
 *    công tắc, vì hệ quả là Hội đồng đọc được số điện thoại của người đã chọn
 *    mức này.
 *  - `BRANCH` — **Cùng chi**: thành viên đã đăng nhập nằm trong phạm vi `ltree`
 *    của chi/ngành.
 *  - `CLAN` — **Cả họ xem**: mọi thành viên đã đăng nhập.
 *
 * **Mặc định là KÍN**: thiếu lựa chọn ⇒ `PRIVATE`. Không giá trị nào mang
 * nghĩa "theo mặc định hệ thống".
 *
 * Đây là **ý chí của chủ thể, không phải kết quả cuối**. Ba luật luôn thắng và
 * nằm ngoài tay người dùng: khách vãng lai không thấy bất kỳ người còn sống nào
 * kể cả khi chọn `CLAN`; trẻ vị thành niên ẩn tối đa bất kể chọn gì; người đã
 * khuất công khai.
 */
export type ShareScope = "PRIVATE" | "BRANCH" | "CLAN";

/**
 * Năm nhóm trường, mỗi nhóm một mức độc lập
 * (contracts/openapi.yaml → `PrivacySettings`).
 *
 * **Ai đọc được khối này:** chỉ **chính chủ** và `ADMIN`. Với mọi người gọi
 * khác nó **vắng mặt hoàn toàn** khỏi JSON — biết người khác đang siết quyền
 * riêng tư cũng là một dạng rò rỉ.
 *
 * **Ngữ nghĩa khi ghi:**
 *  - `POST /persons` — nhóm vắng mặt ⇒ `PRIVATE`.
 *  - `PATCH /persons/{id}` — **hợp nhất, không thay thế** (khác hẳn `names` và
 *    `attributes`): nhóm vắng mặt giữ nguyên mức hiện có, nên giao diện năm
 *    công tắc chỉ cần gửi đúng công tắc vừa gạt. Đóng cả năm thì đưa
 *    `"privacy"` vào `clearFields`.
 *
 * `contact` là **một** công tắc cho cả điện thoại + email + Zalo. Ba giá trị ấy
 * dẫn tới cùng một con người; tách lẻ chỉ tạo ảo giác kiểm soát.
 */
export interface PrivacySettings {
  occupation: ShareScope;
  residenceProvince: ShareScope;
  residenceFull: ShareScope;
  contact: ShareScope;
  birthDetailAndPhoto: ShareScope;
}

/** Khoá của năm nhóm, theo thứ tự hiển thị: từ ít nhạy cảm tới nhạy cảm nhất. */
export const PRIVACY_GROUPS = [
  "occupation",
  "residenceProvince",
  "residenceFull",
  "contact",
  "birthDetailAndPhoto",
] as const satisfies readonly (keyof PrivacySettings)[];

export type PrivacyGroup = (typeof PRIVACY_GROUPS)[number];

/** Ba mức, theo thứ tự mở dần. Giao diện vẽ theo đúng thứ tự này. */
export const SHARE_SCOPES = ["CLAN", "BRANCH", "PRIVATE"] as const satisfies readonly ShareScope[];

/** What tier the CURRENT caller received for this profile — never which fields are hidden. */
export type VisibleTier = "PUBLIC" | "T1" | "T2" | "T3";

/** Technical role from the JWT — separate from clan titles (Tộc trưởng, Trưởng chi). */
export type Role = "ADMIN" | "COUNCIL" | "BRANCH_HEAD" | "MEMBER" | "GUEST";

/** Region drives which REGION-scope kinship rule set applies. Phase 1: only BAC is seeded. */
export type Region = "BAC" | "TRUNG" | "NAM";

/**
 * Edge type, direction is always `from -> to`. Dâu/rể are NOT their own RelType —
 * they're derived from SPOUSE + bloodline and surface via `PersonBadge`/`side: IN_LAW`.
 */
export type RelType = "PARENT_BIO" | "PARENT_ADOPT" | "SPOUSE" | "HEIR";

export type HeirKind = "DICH_TON" | "THUA_TU" | "KE_TU";

export type RelationSide = "PATERNAL" | "MATERNAL" | "BLOOD" | "IN_LAW";

export type TreeDirection = "DESCENDANTS" | "ANCESTORS" | "BOTH";

export type RuleScope = "DEFAULT" | "REGION" | "CLAN" | "BRANCH";

/**
 * Loại lễ (FR-2.3). **Song ánh 12 giá trị** với `ck_event_type` từ V10 —
 * trước đó bốn loại khác nhau cùng đi ra dây dưới một mã `KHAC`, nên qua API
 * một buổi họp họ không phân biệt được với một đám cưới.
 *
 * Hai mã cũ **đổi nghĩa**, không chỉ là thêm mã mới:
 *  - `MUNG_THO` nay chỉ là mừng thọ **bậc cao niên**; sinh nhật thường đã tách
 *    ra thành `SINH_NHAT`.
 *  - `KHAC` nay chỉ là "loại khác"; nó không còn gộp khánh thành / họp họ /
 *    cưới hỏi.
 *
 * Hệ quả cho giao diện: lọc theo `MUNG_THO` hoặc `KHAC` trả **ít dòng hơn**
 * trước — đúng thiết kế, không phải mất dữ liệu.
 *
 * `SINH_NHAT` là sinh nhật của một người **còn sống**, nên nó chịu phân tầng
 * riêng tư y như ngày sinh: chỉ hiện với người mà chủ thể đã mở nhóm
 * `birthDetailAndPhoto`.
 */
export type EventType =
  | "GIO_TO"
  | "GIO_HO"
  | "GIO_CHI"
  | "GIO_THUONG"
  | "TIEU_TUONG"
  | "DAI_TUONG"
  | "CHAP_MA"
  | "MUNG_THO"
  | "SINH_NHAT"
  | "KHANH_THANH"
  | "HOP_HO"
  | "CUOI_HOI"
  | "KHAC";

export type NotificationCategory = "GIO_REMINDER" | "EVENT" | "CHANGE_REQUEST" | "SYSTEM";

/** Precomputed by the backend so canvas/UI never infers these from raw relations. */
export type PersonBadge =
  | "DICH_TON"
  | "THUA_TU"
  | "KE_TU"
  | "CON_NUOI"
  | "DAU"
  | "RE"
  | "TUYET_TU"
  | "TRUONG_CHI"
  | "DECEASED";

export type DatePrecision = "DAY" | "MONTH" | "YEAR" | "UNKNOWN";

// ============================================================================
// DATES — dual calendar, backend-computed only
// ============================================================================

export interface LunarDate {
  year: number;
  month: number;
  day: number;
  /** Ignoring this is the #1 silent bug source (giỗ off by a whole month). */
  leap: boolean;
  canChi?: string | null;
  yearLabel?: string | null;
}

/** Never compute solar<->lunar client-side. `death.lunar` is the giỗ source of truth. */
export interface DateDual {
  solar?: string | null; // YYYY-MM-DD
  lunar?: LunarDate | null;
  precision: DatePrecision;
}

// ============================================================================
// NAMES
// ============================================================================

export interface PersonName {
  id?: string;
  nameType: NameType;
  fullName: string;
  nameHanNom?: string | null;
  /** Server-generated (unaccented, for search). Read-only — sending this is ignored. */
  nameUnaccented?: string | null;
  isPrimary: boolean;
  note?: string | null;
}

export interface PersonNameInput {
  nameType: NameType;
  fullName: string;
  nameHanNom?: string | null;
  isPrimary?: boolean;
  note?: string | null;
}

// ============================================================================
// BRANCH (chi/ngành/cành/nhánh)
// ============================================================================

export interface BranchRef {
  id: string;
  /** Accented display name — do NOT use for ltree paths. */
  name: string;
  /** ltree path, unaccented slug. Not for display. */
  path: string;
  region?: Region | null;
}

// ============================================================================
// PERSON
// ============================================================================

export interface ContactInfo {
  // Tier 3 in full — this whole object is absent (REST) / null (GraphQL) unless
  // caller is self, ADMIN, or explicitly opted in (privacyLevel).
  phone?: string | null;
  email?: string | null;
  zaloId?: string | null;
}

export interface PersonAccessMeta {
  visibleTier: VisibleTier;
  canEdit: boolean;
  canDelete: boolean;
  /** Always false in Phase 1 — correction-request approval flow is Phase 2. */
  canRequestCorrection: boolean;
  isSelf?: boolean;
  callerRole?: Role;
}

export interface RelationshipDto {
  id: string;
  fromPersonId: string;
  toPersonId: string;
  relType: RelType;
  heirKind?: HeirKind | null;
  /** Only meaningful for SPOUSE. 1 = first/senior wife or husband. */
  spouseOrder?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  note?: string | null;
  /**
   * Tóm tắt của **đầu kia** xét theo hồ sơ đang xem, đã qua đúng bộ lọc phân
   * tầng riêng tư của người gọi.
   *
   * Ba điều phải nhớ khi đọc trường này:
   *
   * 1. **Nó nằm ngoài `required`.** Lối ra nào không có khái niệm "hồ sơ đang
   *    xem" (GraphQL) thì không gửi nó. Mọi chỗ đọc phải có đường lùi, chứ
   *    không được coi nó luôn có.
   * 2. **Nó không bao giờ là một tóm tắt "đã che".** Cạnh nào có đầu kia
   *    không hiển thị được thì **cả cạnh** đã bị loại ở máy chủ. Vì vậy không
   *    được dựng giao diện cho trạng thái "có quan hệ nhưng không rõ với ai" —
   *    trạng thái ấy không tồn tại trên dây, và dựng nó lên là tự tay đếm hộ
   *    người xem số quan hệ đang bị giấu.
   * 3. **Nó không mang `badges`.** `PersonSummaryDto` không có trường ấy;
   *    nhãn dâu/rể/đích tôn vẫn chỉ đến từ `TreeNode.badges`.
   */
  otherPerson?: PersonSummaryDto | null;
}

/**
 * Full profile, ALREADY privacy-filtered by the backend. Every field beyond
 * `id`/`isAlive`/`names`/`meta` may be legitimately absent — see the module
 * doc comment above. Tier map (BA v2 §10):
 *   T1: id, names, generation, gender, isAlive, primaryBranch, meta
 *   T2: + birth (year only), occupation, currentPlaceProvince, nativePlace
 *   T3: + contact, birth (full), currentPlaceFull, avatarUrl, attributes, biography
 */
export interface PersonDto {
  id: string;
  names: PersonName[];
  displayName?: string;
  gender?: Gender;
  generation?: number | null;
  isAlive: boolean;
  /** Only ADMIN/COUNCIL ever see this field at all. */
  isDeleted?: boolean;
  birth?: DateDual | null;
  death?: DateDual | null;
  nativePlace?: string | null;
  currentPlaceProvince?: string | null;
  currentPlaceFull?: string | null;
  occupation?: string | null;
  biography?: string | null;
  /** Presigned MinIO URL, time-limited — never a blob. */
  avatarUrl?: string | null;
  primaryBranch?: BranchRef | null;
  contact?: ContactInfo | null;
  attributes?: Record<string, unknown> | null;
  /**
   * Bản đồng thuận riêng tư của chủ thể — năm nhóm, năm mức độc lập.
   *
   * **Chỉ chính chủ và `ADMIN` nhận được khối này**; với vai khác nó vắng mặt
   * hoàn toàn, vì biết người khác đang siết quyền riêng tư cũng là rò rỉ. Vì
   * vậy `privacy !== undefined` là phép kiểm "tôi có quyền chỉnh mức ở đây
   * không" đáng tin hơn mọi phép suy từ vai.
   */
  privacy?: PrivacySettings | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  /** Optimistic-lock version; mirrored in the `ETag` response header. */
  version?: number | null;
  relationships?: RelationshipDto[];
  meta: PersonAccessMeta;
}

/** Safe-for-lists shape (search results, tree nodes) — Tier 1 or below only. */
export interface PersonSummaryDto {
  id: string;
  displayName: string;
  nameHanNom?: string | null;
  gender?: Gender;
  generation?: number | null;
  isAlive: boolean;
  birthYear?: number | null;
  deathYear?: number | null;
  primaryBranch?: BranchRef | null;
  nativePlace?: string | null;
  avatarUrl?: string | null;
  /** Only present in /persons/search results. */
  matchedNameType?: NameType | null;
}

// ============================================================================
// PAGINATION — offset-based, shared by every list endpoint (contracts/README §3)
// ============================================================================

export interface PageMeta {
  page: number; // zero-based
  size: number;
  totalElements: number; // already privacy-filtered
  totalPages: number;
  hasNext: boolean;
  sort?: string | null;
}

export interface PersonSummaryPage {
  items: PersonSummaryDto[];
  page: PageMeta;
}

// ============================================================================
// CREATE / UPDATE REQUESTS
// ============================================================================

export interface RelationshipLinkInput {
  relType: RelType;
  otherPersonId: string;
  /** Which end of `from -> to` the OTHER (already-existing) person occupies. */
  otherPersonRole: "SOURCE" | "TARGET";
  heirKind?: HeirKind | null;
  spouseOrder?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  note?: string | null;
}

export interface CreatePersonRequest {
  names: PersonNameInput[];
  gender: Gender;
  isAlive: boolean;
  birth?: DateDual | null;
  death?: DateDual | null;
  nativePlace?: string | null;
  currentPlaceProvince?: string | null;
  currentPlaceFull?: string | null;
  occupation?: string | null;
  biography?: string | null;
  primaryBranchId?: string | null;
  contact?: ContactInfo | null;
  attributes?: Record<string, unknown> | null;
  /** Nhóm vắng mặt ⇒ `PRIVATE`. Hệ thống không tự mở hộ ai bao giờ. */
  privacy?: Partial<PrivacySettings> | null;
  initialRelationships?: RelationshipLinkInput[];
  /**
   * Taboo-name (kỵ húy) two-call flow (FR-1.6, contracts/README §7.9):
   * the FIRST call must omit this (or send false). If the backend returns
   * 409 KY_HUY_CONFLICT, show `conflicts[]` to the user, then resend the
   * IDENTICAL payload with this set to true. Never default this to true.
   */
  confirmTabooOverride?: boolean;
  confirmDuplicateOverride?: boolean;
  note?: string | null;
}

/**
 * Absent field = keep as-is. `null` is deliberately NOT "clear this field" —
 * use `clearFields` for that (contracts/README §5.2: avoids the
 * null-vs-absent ambiguity across JSON client libraries).
 */
export interface UpdatePersonRequest {
  names?: PersonNameInput[]; // if sent, REPLACES the whole list
  gender?: Gender;
  isAlive?: boolean;
  isDeleted?: boolean; // ADMIN/COUNCIL only; false = restore
  birth?: DateDual;
  death?: DateDual;
  nativePlace?: string;
  currentPlaceProvince?: string;
  currentPlaceFull?: string;
  occupation?: string;
  biography?: string;
  primaryBranchId?: string;
  contact?: ContactInfo;
  attributes?: Record<string, unknown>;
  /**
   * **HỢP NHẤT, không thay thế** — và đây là ngoại lệ so với `names` /
   * `attributes` ngay bên trên, nên rất dễ dựng sai. Nhóm vắng mặt giữ nguyên
   * mức hiện có, nên chỉ cần gửi đúng công tắc vừa gạt. Đóng cả năm nhóm thì
   * đưa `"privacy"` vào `clearFields`, đừng gửi một object năm khoá `PRIVATE`.
   */
  privacy?: Partial<PrivacySettings>;
  clearFields?: string[];
  confirmTabooOverride?: boolean;
  note?: string;
}

/** Phase 2 shape, not active in Phase 1 (MEMBER gets 403 instead of 202). Kept for forward-compat. */
export interface ChangeRequestAccepted {
  changeRequestId: string;
  status: "PENDING";
  targetPersonId?: string | null;
  submittedAt: string;
  message?: string;
}

// ============================================================================
// TREE PROJECTION (REST /tree — flat, for React Flow)
// ============================================================================

export interface TreeNode {
  id: string; // == person.id
  person: PersonSummaryDto;
  /** 0 = root, positive = descendant generations, NEGATIVE = ancestor generations. */
  depth: number;
  parentIds: string[];
  spouseIds: string[];
  /** Total children in DB (post-filter), including not-yet-loaded ones. */
  childCount?: number | null;
  /** true => more descendants exist beyond depth/maxNodes; fetch deeper via rootId=this id. */
  hasMoreDescendants: boolean;
  badges?: PersonBadge[];
}

export interface TreeEdge {
  id: string;
  source: string; // `from`
  target: string; // `to`
  relType: RelType;
  heirKind?: HeirKind | null;
  spouseOrder?: number | null;
  /** Present => relationship has ended (divorce/death) — canvas should draw it dashed. */
  validTo?: string | null;
}

export interface TreeMeta {
  depth: number;
  direction: TreeDirection;
  nodeCount: number;
  edgeCount?: number;
  /** true => response was cut off at maxNodes. MUST be handled, never assume a complete tree. */
  truncated: boolean;
  truncatedNodeIds?: string[];
  generatedAt?: string;
  fromCache?: boolean;
}

export interface TreeProjection {
  rootId: string;
  nodes: TreeNode[];
  /** Only contains edges where BOTH ends are in `nodes` — a privacy-filtered end means no edge. */
  edges: TreeEdge[];
  meta: TreeMeta;
}

// ============================================================================
// KINSHIP (danh xưng)
// ============================================================================

export type KinshipStatus = "RESOLVED" | "NO_COMMON_ANCESTOR" | "NO_MATCHING_RULE" | "SELF";

export interface RelationFacts {
  /** to vs. from: negative = `to` is an elder generation, 0 = same, positive = junior. */
  genDelta: number;
  side: RelationSide;
  targetGender?: Gender;
  /** Same-generation elder/junior (bác vs. chú). null = birth order unknown. */
  isElder?: boolean | null;
  throughMarriage?: boolean;
  throughAdoption?: boolean;
  /**
   * Collateral degree = min(dist_a, dist_b), straight from the LCA result.
   * 0 = trực hệ (one is the other's ancestor) · 1 = ruột · 2 = họ · >=3 = họ xa.
   * Mirrors `kinship_rule.collateral_degree` (migration V3).
   */
  collateralDegree?: number | null;
}

export interface LcaInfo {
  personId: string;
  /** Replaced with a generic label if the LCA is a hidden living person. */
  displayName?: string | null;
  generation?: number | null;
  distanceFrom: number;
  distanceTo: number;
}

export interface KinshipPathStep {
  personId: string;
  /** Generic placeholder (e.g. "—") for a hidden living person — the STEP stays, name doesn't. */
  displayName?: string | null;
  generation?: number | null;
  direction: "SELF" | "UP" | "DOWN" | "ACROSS";
  viaRelType?: RelType | null;
}

/**
 * `status !== RESOLVED` is NOT an error — render per-status messaging, never
 * a generic error banner (contracts/README §7.7).
 */
export interface KinshipResult {
  status: KinshipStatus;
  fromPersonId: string;
  toPersonId: string;
  /** The title `from` uses to address `to`. Absent unless status = RESOLVED. Always Vietnamese. */
  title?: string | null;
  reciprocalTitle?: string | null;
  /** Rough English gloss for the EN UI — reference only, not a 1:1 translation. */
  titleEn?: string | null;
  facts?: RelationFacts | null;
  lca?: LcaInfo | null;
  path?: KinshipPathStep[];
  ruleSetId?: string | null;
  ruleSetScope?: RuleScope | null;
  ruleId?: string | null;
  ruleSetChain?: RuleScope[];
  cached?: boolean;
}

export interface KinshipRuleDto {
  id?: string;
  genDelta: number;
  side: RelationSide;
  gender?: Gender | null;
  isElder?: boolean | null;
  /** Matches one collateral degree; null = any. 0 = trực hệ · 1 = ruột · 2 = họ. */
  collateralDegree?: number | null;
  throughMarriage?: boolean | null;
  title: string;
  reciprocalTitle?: string | null;
  priority?: number;
  note?: string | null;
}

export interface KinshipRuleSetDto {
  id: string;
  scope: RuleScope;
  name?: string | null;
  region?: Region | null;
  clanId?: string | null;
  branchId?: string | null;
  parentRuleSetId?: string | null;
  /** DEFAULT (miền Bắc, system-seeded) is immutable: PUT -> 403 SYSTEM_RULE_SET_IMMUTABLE. */
  isSystemDefault?: boolean;
  rules: KinshipRuleDto[];
  version: number;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface KinshipRuleSetPage {
  items: KinshipRuleSetDto[];
  page: PageMeta;
}

export interface EffectiveKinshipRuleSet {
  branchId: string;
  chain: Array<{ ruleSetId: string; scope: RuleScope; name?: string | null }>;
  rules: Array<
    KinshipRuleDto & {
      inheritedFromScope?: RuleScope;
      inheritedFromRuleSetId?: string;
      overridden?: boolean;
    }
  >;
}

export interface KinshipRuleSetUpdateRequest {
  /** Omit to create a new set (201); provide to replace an existing one (200). */
  ruleSetId?: string | null;
  scope: RuleScope;
  name?: string | null;
  region?: Region | null; // required if scope = REGION
  clanId?: string | null; // required if scope = CLAN
  branchId?: string | null; // required if scope = BRANCH
  parentRuleSetId?: string | null;
  /** REPLACES the entire rule list for this set. */
  rules: KinshipRuleDto[];
  /** Required when updating an existing set; wrong value -> 409 OPTIMISTIC_LOCK_CONFLICT. */
  expectedVersion?: number | null;
  note?: string | null;
}

// ============================================================================
// EVENTS (giỗ / chạp mả / lễ)
// ============================================================================

export interface EventDto {
  id: string;
  eventType: EventType;
  /** Pre-rendered by the backend per Accept-Language — never concatenate client-side. */
  title?: string | null;
  person?: PersonSummaryDto | null;
  lunarDate: LunarDate;
  /** Backend-computed (Hồ Ngọc Đức, GMT+7). Never compute this client-side. */
  nextOccurrenceSolar?: string | null;
  nextOccurrenceLunarYear?: number | null;
  daysUntil?: number | null;
  targetBranch?: BranchRef | null;
  isClanLevel: boolean;
  reminderOffsets?: number[]; // default [7, 3, 1]
  graveId?: string | null;
  note?: string | null;
}

export interface EventPage {
  items: EventDto[];
  page: PageMeta;
}

// ============================================================================
// NOTIFICATIONS (in-app inbox)
// ============================================================================

export interface NotificationDto {
  id: string;
  category: NotificationCategory;
  title: string;
  /** Never contains Tier-3 data — may show on a locked screen. */
  body?: string | null;
  eventId?: string | null;
  personId?: string | null;
  deepLink?: string | null;
  createdAt: string;
  isRead: boolean;
  readAt?: string | null;
  reminderOffsetDays?: number | null;
}

export interface NotificationPage {
  items: NotificationDto[];
  page: PageMeta;
  /** Total unread across the WHOLE inbox, filter/page independent — this is the bell badge count. */
  unreadCount: number;
}

export interface NotificationReadResult {
  id: string;
  isRead: boolean;
  /** First-read timestamp; idempotent — repeat calls do not change this. */
  readAt: string;
  unreadCount: number;
}

// ============================================================================
// WEB PUSH (VAPID)
// ============================================================================

export interface VapidPublicKey {
  /** base64url, no padding — pass straight into PushManager.subscribe({ applicationServerKey }). */
  publicKey: string;
}

export interface PushSubscriptionCreateRequest {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
  expirationTime?: number | null;
  userAgent?: string | null;
  locale?: "vi" | "en" | null;
}

export interface PushSubscriptionDto {
  id: string;
  endpoint: string;
  userAgent?: string | null;
  locale?: string | null;
  createdAt: string;
  lastUsedAt?: string | null;
  isCurrentDevice?: boolean;
}

// ============================================================================
// GRAPHQL TRANSPORT ENVELOPE (POST /api/v1/graphql)
// ============================================================================

export interface GraphQLRequestBody {
  query: string;
  operationName?: string | null;
  variables?: Record<string, unknown> | null;
}

export interface GraphQLErrorItem {
  message: string;
  path?: unknown[];
  locations?: Array<{ line: number; column: number }>;
  extensions?: {
    code?: ProblemCode;
    status?: number;
    traceId?: string;
  };
}

export interface GraphQLResponseBody<TData = Record<string, unknown>> {
  data?: TData | null;
  errors?: GraphQLErrorItem[];
}

// ============================================================================
// ERRORS — RFC 7807, contracts/README §3: branch on `code`, never on `title`/`detail`
// ============================================================================

/** CLOSED list — adding a value is a breaking contract change (contracts/README §1). */
export type ProblemCode =
  | "VALIDATION_FAILED"
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "BRANCH_SCOPE_VIOLATION"
  | "NOT_FOUND"
  | "KY_HUY_CONFLICT"
  | "DUPLICATE_PERSON_SUSPECTED"
  | "RELATIONSHIP_CYCLE"
  | "INVALID_RELATIONSHIP"
  | "PERSON_ALREADY_DELETED"
  | "OPTIMISTIC_LOCK_CONFLICT"
  | "PRECONDITION_REQUIRED"
  | "SYSTEM_RULE_SET_IMMUTABLE"
  | "RULE_SET_CYCLE"
  | "DUPLICATE_RULE"
  | "LUNAR_CONVERSION_FAILED"
  | "DEPTH_LIMIT_EXCEEDED"
  | "QUERY_TOO_COMPLEX"
  // --- membership / luồng duyệt yêu cầu sửa (contracts/openapi.yaml) --------
  | "CHANGE_REQUEST_CLOSED"
  | "SELF_REVIEW_FORBIDDEN"
  | "ACCOUNT_NOT_PROVISIONED"
  | "ACCOUNT_NOT_ACTIVE"
  | "INVALID_ROLE_ASSIGNMENT"
  /** Đã đăng nhập nhưng tài khoản chưa ghép với nhân khẩu nào — hộp thư rỗng vì lý do này
   * phải nói rõ ra, chứ không phải một màn hình trắng. */
  | "ACCOUNT_NOT_LINKED"
  // --- lời mời vào phả (contracts/openapi.yaml, nhóm `invitations`) ----------
  // Bốn cách một mã mời hỏng, và chúng KHÔNG gộp được: mỗi ca dẫn tới một câu
  // chữ và một lối đi tiếp khác nhau (xem src/lib/api/invitation.ts). Ca thứ tư
  // — mã không khớp lời mời nào — dùng lại `NOT_FOUND` ở trên thay vì có mã
  // riêng, vì nó không mang thêm nghĩa nào.
  | "INVITATION_EXPIRED"
  | "INVITATION_ALREADY_USED"
  | "INVITATION_REVOKED"
  /** Nhân khẩu mà lời mời trỏ tới đã bị một tài khoản khác nhận. `422`. */
  | "PERSON_ALREADY_LINKED"
  /** Tài khoản đang gọi đã gắn một nhân khẩu KHÁC. `422`. */
  | "ACCOUNT_ALREADY_LINKED"
  /**
   * Liên kết đặt mật khẩu hết hạn, sai chữ ký, hoặc **đã dùng rồi**. `410`.
   *
   * Hạn là 30 phút, nên đây là ca **bình thường** — một cụ có thể để tin nhắn tới hôm sau mới mở.
   * Tính một lần neo vào trạng thái thật ("tài khoản này đã có mật khẩu chưa"), không vào một cờ
   * trong cơ sở dữ liệu: một cờ sót lại là một lối đổi mật khẩu của người khác.
   */
  | "SET_PASSWORD_LINK_INVALID"
  /**
   * Không nói chuyện được với Keycloak. `503`.
   *
   * Với luồng mời, thao tác ghi chỉ diễn ra **sau** khi tài khoản đã tạo xong, nên **mã mời chưa
   * bị dùng** và người dùng thử lại được — câu chữ phải nói đúng điều đó.
   */
  | "IDENTITY_PROVIDER_UNAVAILABLE"
  // --- events / notification ------------------------------------------------
  | "REMINDER_GENERATION_IN_PROGRESS"
  /** Máy chủ chưa cấu hình khoá VAPID: giao diện phải nói thẳng thay vì để người dùng bấm vào
   * một công tắc chắc chắn hỏng. */
  | "WEBPUSH_NOT_CONFIGURED"
  // --- nhập liệu ban đầu / data import (contracts/openapi.yaml ProblemCode) --
  // Mười bốn mã này nằm trong CÙNG một enum đóng với phần trên, không phải một
  // danh sách riêng: `src/lib/api/data-import.ts` chỉ thu hẹp kiểu này lại.
  /** Không phải `.xlsx` thật — nhận diện bằng chữ ký tệp, không bằng đuôi. 400. */
  | "IMP_BAD_FORMAT"
  | "IMP_CORRUPT_FILE"
  /** DOCTYPE / thực thể ngoài XML / zip bomb. 400. */
  | "IMP_UNSAFE_FILE"
  /** Tệp đọc được nhưng cấu trúc sai — 422, không phải 400: việc cần làm khác hẳn. */
  | "IMP_MISSING_SHEET"
  | "IMP_MISSING_COLUMN"
  | "IMP_TOO_MANY_ROWS"
  /** Vượt trần 10 MB. **413**, không phải 400. */
  | "IMP_FILE_TOO_LARGE"
  /**
   * Chi này đã có một lô **đã ghi vào phả** mang đúng mã băm ấy — 409 kèm
   * `overridable: true`, `overrideField: "force"`. Không phải "tệp trùng": tải
   * lại một tệp giống hệt để *kiểm* thì không bị chặn.
   */
  | "IMP_ALREADY_COMMITTED"
  | "IMP_BLOCKING_ISSUES_PRESENT"
  | "IMP_WARNINGS_NOT_ACKNOWLEDGED"
  | "IMP_DUPLICATES_UNDECIDED"
  | "IMP_BATCH_CLOSED"
  /** Bước ghi dừng lại **trước khi ghi**; chi tiết ở `GET .../issues`, không ở thân lỗi. */
  | "IMP_COMMIT_BLOCKED"
  /**
   * Không gỡ được lô vì **đã có người khác động vào** dữ liệu lô ấy sinh ra —
   * điều kiện thật là `person.version` tại lúc ghi, không phải một cửa sổ thời
   * gian. Thân lỗi mang `blockers[]` tiếng Việt.
   */
  | "IMP_ROLLBACK_REFUSED"
  | "RATE_LIMITED"
  | "INTERNAL_ERROR";

export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code: ProblemCode;
  traceId?: string;
  timestamp?: string;
}

export interface ValidationFieldError {
  field: string;
  message: string;
  /** Deliberately omitted for Tier-3 data — never echo sensitive rejected values back. */
  rejectedValue?: unknown;
}

export interface ValidationProblem extends Problem {
  errors?: ValidationFieldError[];
}

/**
 * Một bậc trên có **tên húy** trùng với tên đang định đặt (FR-1.6).
 *
 * <h2>Vì sao ở đây không có tên, không có đời thứ của bậc trên</h2>
 * Truy vấn dò kỵ húy chọn bậc trên **theo đời thứ** (`generation <` đời của
 * người mới) — không theo sống/mất, không theo chi, và không có ngưỡng điểm
 * nào. "Bậc trên" vì thế hoàn toàn có thể là một ông bác **còn sống ở một chi
 * khác** mà người đang thêm nhân khẩu không có quyền biết gì về họ, kể cả việc
 * họ tồn tại. Thân lỗi `409` đi thẳng ra HTTP, **không** đi qua bộ lọc phân
 * tầng riêng tư, nên nó không được phép chở một giá trị đọc từ phả.
 *
 * Bốn trường đã **bị bỏ có chủ ý** (đừng thêm lại): `ancestorDisplayName`,
 * `ancestorGeneration`, `tabooName`, `relationHint` — chuỗi cuối nhúng chính
 * đời thứ của bậc trên, còn `tabooName` là `person_name.full_name` đọc từ phả
 * chứ không phải ô người dùng vừa gõ (với `UNACCENTED` nó phát ra bản **có
 * dấu** mà người gọi chưa từng biết).
 *
 * Nạp danh tính bằng `GET /api/v1/persons/{ancestorPersonId}` — đó là nơi bộ
 * lọc phân tầng riêng tư thật sự chạy, và `404` ở đó là **ca bình thường**.
 */
export interface TabooConflict {
  /** Khoá là **thứ duy nhất** nói về bậc trên. */
  ancestorPersonId: string;
  matchedNameType: NameType;
  /** Mức khớp của **ô người dùng vừa nhập** — không phải giá trị bên kia. */
  matchKind: "EXACT" | "GIVEN_NAME" | "UNACCENTED";
}

/**
 * Tín hiệu góp vào `DuplicateCandidate.score`.
 *
 * Chúng chỉ nói **trường nào của chính người dùng vừa nhập đã khớp**, nên
 * chúng được phép có mặt trong thân lỗi 409.
 */
export type DuplicateSignal =
  | "TEN_TRUNG_CO_DAU"
  | "TEN_TRUNG_KHONG_DAU"
  | "GIO_TRUNG_KHIT"
  | "NAM_SINH_KHOP"
  | "NAM_SINH_LECH_IT"
  | "NAM_MAT_KHOP"
  | "CUNG_CHI"
  | "CUNG_DOI"
  | "KHAC_DOI"
  | "CUNG_NGUYEN_QUAN"
  | "KHAC_GIOI";

/**
 * Một nhân khẩu **có thể đã có sẵn** trùng với người đang định thêm
 * (`DUPLICATE_PERSON_SUSPECTED`). Cảnh báo, không phải lệnh cấm: gửi lại kèm
 * `confirmDuplicateOverride` để vẫn ghi. Mảng giữ **thứ tự giảm dần theo
 * `score`**.
 *
 * Cùng một ranh giới như {@link TabooConflict}: bộ dò quét **toàn dòng họ** và
 * không biết người gọi là ai, nên bốn trường `displayName`, `generation`,
 * `branchId`, `matchedName` **bị bỏ có chủ ý**. `score` / `signals` / `hint` ở
 * lại vì chúng trả lời "vì sao nghi" mà không nói "người ấy là ai" — cắt nốt
 * chúng thì người nhập mất khả năng đối chiếu và sẽ bấm ghi đè theo phản xạ.
 */
export interface DuplicateCandidate {
  /**
   * Hồ sơ đã có trong CSDL. **`null` là ca bình thường**: ứng viên có thể là
   * một dòng **chưa được ghi** trong cùng lô nhập liệu, khi ấy định danh duy
   * nhất là {@link DuplicateCandidate.ref} và **không có gì để `GET`**.
   */
  personId: string | null;
  /** Mã tham chiếu dòng trong lô nhập — dữ liệu của chính người nhập. */
  ref?: string | null;
  /** Điểm nghi ngờ 0–100+. Không có ngưỡng nào được contract bảo đảm. */
  score: number;
  signals: DuplicateSignal[];
  /** Chỉ để hiển thị — **đừng phân tích chuỗi này**. Cần logic thì đọc `signals`. */
  hint?: string | null;
}

export interface ConflictProblem extends Problem {
  /** true => resend with `overrideField` set to keep going; false => hard conflict, reload. */
  overridable?: boolean;
  overrideField?: string; // e.g. "confirmTabooOverride"
  /**
   * `KY_HUY_CONFLICT` ⇒ `TabooConflict[]`; `DUPLICATE_PERSON_SUSPECTED` ⇒
   * `DuplicateCandidate[]`. Phân loại bằng {@link isTabooConflict} —
   * **không** bằng `problem.code`, vì cùng một kiểu thân lỗi phục vụ cả hai.
   */
  conflicts?: (TabooConflict | DuplicateCandidate)[];
}

/** Phân biệt hai nhánh của `ConflictProblem.conflicts` theo khoá riêng của chúng. */
export function isTabooConflict(
  conflict: TabooConflict | DuplicateCandidate
): conflict is TabooConflict {
  return "ancestorPersonId" in conflict;
}

export function isDuplicateCandidate(
  conflict: TabooConflict | DuplicateCandidate
): conflict is DuplicateCandidate {
  return !isTabooConflict(conflict);
}
