import type {
  EventDto,
  KinshipRuleSetDto,
  NotificationDto,
  PersonDto,
  PersonSummaryDto,
} from "@/types/api";

/**
 * Small, hand-authored, deterministic mock dataset for local FE development,
 * shaped to match `contracts/openapi.yaml` component schemas exactly
 * (PersonDto, TreeNode, EventDto, ...) as of contract version
 * `1.0.0-sprint1`.
 *
 * NOT the same thing as the backend's ~1,500-person demo generator (plan
 * §10) — that is a backend/W1 deliverable seeding real Postgres+AGE data.
 * This file only exists so MSW can serve plausible responses while the real
 * API doesn't exist yet. Kept intentionally small (one lineage, a few
 * generations, two chi) but includes at least one of each privacy state.
 */

// ---- Deceased persons (PUBLIC tier — full fidelity, no filtering) --------

export const THUY_TO: PersonDto = {
  id: "p-001",
  isAlive: false,
  generation: 1,
  gender: "MALE",
  displayName: "Nguyễn Văn Thủy Tổ",
  names: [
    { nameType: "HUY", fullName: "Nguyễn Văn Tổ", isPrimary: true },
    { nameType: "TU", fullName: "Đức Thành", isPrimary: false },
    { nameType: "THUY", fullName: "Trung Hậu Công", isPrimary: false },
  ],
  birth: {
    solar: "1780-02-10",
    lunar: { year: 1780, month: 1, day: 6, leap: false, canChi: "Canh Tý" },
    precision: "DAY",
  },
  death: {
    solar: "1852-11-03",
    lunar: { year: 1852, month: 9, day: 22, leap: false, canChi: "Nhâm Tý" },
    precision: "DAY",
  },
  nativePlace: "Nam Định",
  primaryBranch: { id: "b-root", name: "Thủy tổ", path: "root", region: "BAC" },
  meta: { visibleTier: "PUBLIC", canEdit: false, canDelete: false, canRequestCorrection: false },
};

const chi1Ancestor: PersonDto = {
  id: "p-010",
  isAlive: false,
  generation: 2,
  gender: "MALE",
  displayName: "Nguyễn Văn Hiển",
  names: [
    { nameType: "HUY", fullName: "Nguyễn Văn Hiển", isPrimary: true },
    { nameType: "TU", fullName: "Minh Đạo", isPrimary: false },
  ],
  birth: { solar: "1805-05-01", lunar: { year: 1805, month: 3, day: 13, leap: false }, precision: "DAY" },
  death: { solar: "1878-01-20", lunar: { year: 1877, month: 12, day: 15, leap: false }, precision: "DAY" },
  primaryBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  meta: { visibleTier: "PUBLIC", canEdit: false, canDelete: false, canRequestCorrection: false },
};

const chi2Ancestor: PersonDto = {
  id: "p-011",
  isAlive: false,
  generation: 2,
  gender: "MALE",
  displayName: "Nguyễn Văn Hoà",
  names: [{ nameType: "HUY", fullName: "Nguyễn Văn Hoà", isPrimary: true }],
  birth: { solar: "1808-08-12", lunar: { year: 1808, month: 6, day: 22, leap: false }, precision: "DAY" },
  death: { solar: "1881-04-02", lunar: { year: 1881, month: 3, day: 4, leap: false }, precision: "DAY" },
  primaryBranch: { id: "b-chi2", name: "Chi Nhị", path: "root.chi_nhi", region: "BAC" },
  meta: { visibleTier: "PUBLIC", canEdit: false, canDelete: false, canRequestCorrection: false },
};

// A daughter recorded fully & equally (BA v2 §12 resolved decision).
const chi1Daughter: PersonDto = {
  id: "p-020",
  isAlive: false,
  generation: 3,
  gender: "FEMALE",
  displayName: "Nguyễn Thị Ngọc",
  names: [{ nameType: "HUY", fullName: "Nguyễn Thị Ngọc", isPrimary: true }],
  birth: { solar: "1830-01-15", lunar: { year: 1829, month: 12, day: 2, leap: false }, precision: "DAY" },
  death: { solar: "1901-06-06", lunar: { year: 1901, month: 4, day: 20, leap: false }, precision: "DAY" },
  primaryBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  meta: { visibleTier: "PUBLIC", canEdit: false, canDelete: false, canRequestCorrection: false },
};

// Son-in-law (rể) married into the family — recorded per FR-1.2. Badge `RE`
// on the tree node is backend-computed, never inferred client-side.
const chi1SonInLaw: PersonDto = {
  id: "p-021",
  isAlive: false,
  generation: 3,
  gender: "MALE",
  displayName: "Trần Văn Khoa",
  names: [{ nameType: "HUY", fullName: "Trần Văn Khoa", isPrimary: true }],
  primaryBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  meta: { visibleTier: "PUBLIC", canEdit: false, canDelete: false, canRequestCorrection: false },
};

// ---- Living persons — full-fidelity "master" records ----------------------
// Handlers project these down per mock role via src/mocks/privacy.ts; a
// guest must never receive either of these (see handlers/persons.ts).

export const LIVING_MEMBER_FULL: PersonDto = {
  id: "p-100",
  isAlive: true,
  generation: 5,
  gender: "MALE",
  displayName: "Nguyễn Văn An",
  names: [{ nameType: "THUONG_GOI", fullName: "Nguyễn Văn An", isPrimary: true }],
  birth: { solar: "1990-07-20", precision: "DAY" },
  occupation: "Kỹ sư phần mềm",
  currentPlaceProvince: "Hà Nội",
  nativePlace: "Nam Định",
  contact: { phone: "+84 912 345 678", email: "an.nguyen@example.com" },
  primaryBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  meta: { visibleTier: "T3", canEdit: false, canDelete: false, canRequestCorrection: false },
};

// Maximally hidden by default per BA v2 §10 — RESTRICTED privacy level.
export const LIVING_MINOR_FULL: PersonDto = {
  id: "p-101",
  isAlive: true,
  generation: 6,
  gender: "FEMALE",
  displayName: "Nguyễn Thị Bé",
  names: [{ nameType: "THUONG_GOI", fullName: "Nguyễn Thị Bé", isPrimary: true }],
  primaryBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  privacyLevel: "RESTRICTED",
  meta: { visibleTier: "T1", canEdit: false, canDelete: false, canRequestCorrection: false },
};

export const ALL_PERSONS: PersonDto[] = [
  THUY_TO,
  chi1Ancestor,
  chi2Ancestor,
  chi1Daughter,
  chi1SonInLaw,
  LIVING_MEMBER_FULL,
  LIVING_MINOR_FULL,
];

export const LIVING_PERSON_IDS = new Set([LIVING_MEMBER_FULL.id, LIVING_MINOR_FULL.id]);

export function findPersonMock(id: string): PersonDto | undefined {
  return ALL_PERSONS.find((p) => p.id === id);
}

function toSummary(p: PersonDto): PersonSummaryDto {
  return {
    id: p.id,
    displayName: p.displayName ?? p.names.find((n) => n.isPrimary)?.fullName ?? "(?)",
    gender: p.gender,
    generation: p.generation,
    isAlive: p.isAlive,
    // Solar year, not lunar: `PersonSummaryDto.birthYear`/`deathYear` are the
    // years a list UI prints next to a name, and those are Gregorian. The
    // lunar year can differ by one around Tết, which used to make a card and
    // its own profile page disagree.
    birthYear: p.birth?.solar ? Number(p.birth.solar.slice(0, 4)) : undefined,
    deathYear: p.death?.solar ? Number(p.death.solar.slice(0, 4)) : undefined,
    primaryBranch: p.primaryBranch,
    nativePlace: p.nativePlace,
  };
}

// ---- Tree (REST TreeNode/TreeEdge shape) -----------------------------------
//
// The actual `/tree` + GraphQL `tree` mock resolvers do NOT use static
// TreeNode/TreeEdge fixtures anymore (Sprint 1 had ~5 hand-authored nodes,
// nowhere near enough to exercise lazy-loading or measure canvas
// performance). See src/mocks/tree-graph/ — it builds a ~4,000-node
// generated graph and grafts THUY_TO/chi1Ancestor/chi2Ancestor/
// chi1Daughter/chi1SonInLaw/LIVING_MEMBER_FULL/LIVING_MINOR_FULL (the
// PersonDto fixtures above) into it BY ID, so `/persons/{id}` (this file)
// and `/tree` (tree-graph/) agree on who these people are.

// ---- Kinship rules (DEFAULT = miền Bắc, per plan §1 decision #6) -----------
//
// FIXTURE DATA, NOT AN APPROVED RULE SET. contracts/README §6.2 is explicit
// that the actual danh xưng values must be signed off by the Hội đồng Tộc
// biểu — "sai danh xưng là lỗi mất mặt với dòng họ, không phải bug kỹ thuật".
// These exist so F5 has something plausible to resolve against; the real set
// is seeded by the backend (migration V3) and edited via PUT /kinship-rules.
//
// Coverage is deliberately INCOMPLETE: nothing is defined for
// `collateralDegree >= 3` (họ xa), so a distant pair returns
// `status = NO_MATCHING_RULE` and the "bộ luật chưa phủ trường hợp này" UI
// path stays reachable in manual testing (contracts/README §2 + §7.7).

export const DEFAULT_KINSHIP_RULE_SET: KinshipRuleSetDto = {
  id: "krs-default-bac",
  scope: "DEFAULT",
  name: "Mặc định miền Bắc",
  region: "BAC",
  isSystemDefault: true,
  version: 1,
  rules: [
    // --- Trực hệ (collateralDegree 0): one is the other's ancestor ---------
    { id: "kr-100", genDelta: -1, side: "PATERNAL", collateralDegree: 0, gender: "MALE", title: "Cha", reciprocalTitle: "Con" },
    { id: "kr-101", genDelta: -1, side: "PATERNAL", collateralDegree: 0, gender: "FEMALE", title: "Mẹ", reciprocalTitle: "Con" },
    { id: "kr-102", genDelta: -2, side: "PATERNAL", collateralDegree: 0, gender: "MALE", title: "Ông nội", reciprocalTitle: "Cháu" },
    { id: "kr-103", genDelta: -2, side: "PATERNAL", collateralDegree: 0, gender: "FEMALE", title: "Bà nội", reciprocalTitle: "Cháu" },
    { id: "kr-104", genDelta: -3, side: "PATERNAL", collateralDegree: 0, gender: "MALE", title: "Cụ ông", reciprocalTitle: "Chắt" },
    { id: "kr-105", genDelta: -3, side: "PATERNAL", collateralDegree: 0, gender: "FEMALE", title: "Cụ bà", reciprocalTitle: "Chắt" },
    { id: "kr-106", genDelta: -4, side: "PATERNAL", collateralDegree: 0, title: "Kỵ", reciprocalTitle: "Chút" },
    { id: "kr-107", genDelta: -5, side: "PATERNAL", collateralDegree: 0, title: "Tổ", reciprocalTitle: "Hậu duệ" },
    { id: "kr-108", genDelta: -6, side: "PATERNAL", collateralDegree: 0, title: "Tiên tổ", reciprocalTitle: "Hậu duệ" },
    { id: "kr-110", genDelta: 1, side: "PATERNAL", collateralDegree: 0, gender: "MALE", title: "Con trai" },
    { id: "kr-111", genDelta: 1, side: "PATERNAL", collateralDegree: 0, gender: "FEMALE", title: "Con gái" },
    { id: "kr-112", genDelta: 2, side: "PATERNAL", collateralDegree: 0, title: "Cháu" },
    { id: "kr-113", genDelta: 3, side: "PATERNAL", collateralDegree: 0, title: "Chắt" },
    { id: "kr-114", genDelta: 4, side: "PATERNAL", collateralDegree: 0, title: "Chút" },
    { id: "kr-115", genDelta: 5, side: "PATERNAL", collateralDegree: 0, title: "Hậu duệ" },
    { id: "kr-116", genDelta: 6, side: "PATERNAL", collateralDegree: 0, title: "Hậu duệ" },

    // --- Bàng hệ bậc 1 — "ruột" (children of the same ancestor) -----------
    { id: "kr-200", genDelta: 0, side: "PATERNAL", collateralDegree: 1, gender: "MALE", isElder: true, title: "Anh ruột", reciprocalTitle: "Em" },
    { id: "kr-201", genDelta: 0, side: "PATERNAL", collateralDegree: 1, gender: "MALE", isElder: false, title: "Em trai ruột", reciprocalTitle: "Anh" },
    { id: "kr-202", genDelta: 0, side: "PATERNAL", collateralDegree: 1, gender: "FEMALE", isElder: true, title: "Chị ruột", reciprocalTitle: "Em" },
    { id: "kr-203", genDelta: 0, side: "PATERNAL", collateralDegree: 1, gender: "FEMALE", isElder: false, title: "Em gái ruột", reciprocalTitle: "Chị" },
    // Birth order genuinely unknown — the clan book hedges rather than guesses.
    { id: "kr-204", genDelta: 0, side: "PATERNAL", collateralDegree: 1, gender: "MALE", priority: 90, title: "Anh/em trai ruột", note: "Chưa rõ thứ tự sinh" },
    { id: "kr-205", genDelta: 0, side: "PATERNAL", collateralDegree: 1, gender: "FEMALE", priority: 90, title: "Chị/em gái ruột", note: "Chưa rõ thứ tự sinh" },
    { id: "kr-206", genDelta: -1, side: "PATERNAL", collateralDegree: 1, gender: "MALE", isElder: true, title: "Bác ruột", reciprocalTitle: "Cháu" },
    { id: "kr-207", genDelta: -1, side: "PATERNAL", collateralDegree: 1, gender: "MALE", isElder: false, title: "Chú ruột", reciprocalTitle: "Cháu" },
    { id: "kr-208", genDelta: -1, side: "PATERNAL", collateralDegree: 1, gender: "FEMALE", title: "Cô ruột", reciprocalTitle: "Cháu" },
    { id: "kr-209", genDelta: 1, side: "PATERNAL", collateralDegree: 1, gender: "MALE", title: "Cháu trai (ruột)", reciprocalTitle: "Bác/Chú/Cô" },
    { id: "kr-210", genDelta: 1, side: "PATERNAL", collateralDegree: 1, gender: "FEMALE", title: "Cháu gái (ruột)", reciprocalTitle: "Bác/Chú/Cô" },
    { id: "kr-211", genDelta: -2, side: "PATERNAL", collateralDegree: 1, gender: "MALE", title: "Ông bác/Ông chú", reciprocalTitle: "Cháu" },
    { id: "kr-212", genDelta: -2, side: "PATERNAL", collateralDegree: 1, gender: "FEMALE", title: "Bà cô", reciprocalTitle: "Cháu" },
    { id: "kr-213", genDelta: 2, side: "PATERNAL", collateralDegree: 1, title: "Cháu (bàng hệ)" },

    // --- Bàng hệ bậc 2 — "họ" (con chú con bác) ---------------------------
    { id: "kr-300", genDelta: 0, side: "PATERNAL", collateralDegree: 2, gender: "MALE", isElder: true, title: "Anh họ", reciprocalTitle: "Em" },
    { id: "kr-301", genDelta: 0, side: "PATERNAL", collateralDegree: 2, gender: "MALE", isElder: false, title: "Em trai họ", reciprocalTitle: "Anh" },
    { id: "kr-302", genDelta: 0, side: "PATERNAL", collateralDegree: 2, gender: "FEMALE", isElder: true, title: "Chị họ", reciprocalTitle: "Em" },
    { id: "kr-303", genDelta: 0, side: "PATERNAL", collateralDegree: 2, gender: "FEMALE", isElder: false, title: "Em gái họ", reciprocalTitle: "Chị" },
    { id: "kr-304", genDelta: -1, side: "PATERNAL", collateralDegree: 2, gender: "MALE", isElder: true, title: "Bác họ", reciprocalTitle: "Cháu" },
    { id: "kr-305", genDelta: -1, side: "PATERNAL", collateralDegree: 2, gender: "MALE", isElder: false, title: "Chú họ", reciprocalTitle: "Cháu" },
    { id: "kr-306", genDelta: -1, side: "PATERNAL", collateralDegree: 2, gender: "FEMALE", title: "Cô họ", reciprocalTitle: "Cháu" },
    { id: "kr-307", genDelta: 1, side: "PATERNAL", collateralDegree: 2, title: "Cháu họ", reciprocalTitle: "Bác/Chú/Cô họ" },
    { id: "kr-308", genDelta: -2, side: "PATERNAL", collateralDegree: 2, title: "Ông/Bà họ", reciprocalTitle: "Cháu" },
    { id: "kr-309", genDelta: 2, side: "PATERNAL", collateralDegree: 2, title: "Cháu họ" },

    // --- Bên ngoại (MATERNAL) — kept from the Sprint-1 seed ---------------
    { id: "kr-004", genDelta: -1, side: "MATERNAL", collateralDegree: 1, gender: "MALE", title: "Cậu", reciprocalTitle: "Cháu" },
    { id: "kr-005", genDelta: -1, side: "MATERNAL", collateralDegree: 1, gender: "FEMALE", title: "Dì", reciprocalTitle: "Cháu" },
    { id: "kr-007", genDelta: -2, side: "MATERNAL", collateralDegree: 0, gender: "MALE", title: "Ông ngoại", reciprocalTitle: "Cháu" },
    { id: "kr-008", genDelta: -2, side: "MATERNAL", collateralDegree: 0, gender: "FEMALE", title: "Bà ngoại", reciprocalTitle: "Cháu" },

    // --- Qua hôn nhân (dâu/rể) — side IN_LAW, throughMarriage -------------
    // `collateralDegree: null` = "any degree", so these also catch the direct
    // spouse case (where the degree is undefined); the degree-specific rules
    // below outrank them because the engine prefers more constrained rules.
    { id: "kr-400", genDelta: 0, side: "IN_LAW", throughMarriage: true, gender: "MALE", priority: 90, title: "Chồng", reciprocalTitle: "Vợ" },
    { id: "kr-401", genDelta: 0, side: "IN_LAW", throughMarriage: true, gender: "FEMALE", priority: 90, title: "Vợ", reciprocalTitle: "Chồng" },
    { id: "kr-402", genDelta: 0, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "FEMALE", isElder: true, title: "Chị dâu", reciprocalTitle: "Em" },
    { id: "kr-403", genDelta: 0, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "FEMALE", isElder: false, title: "Em dâu", reciprocalTitle: "Anh/Chị" },
    { id: "kr-404", genDelta: 0, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "MALE", isElder: true, title: "Anh rể", reciprocalTitle: "Em" },
    { id: "kr-405", genDelta: 0, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "MALE", isElder: false, title: "Em rể", reciprocalTitle: "Anh/Chị" },
    { id: "kr-406", genDelta: 0, side: "IN_LAW", throughMarriage: true, collateralDegree: 2, gender: "FEMALE", title: "Chị/em dâu họ", reciprocalTitle: "Anh/Chị/Em" },
    { id: "kr-407", genDelta: 0, side: "IN_LAW", throughMarriage: true, collateralDegree: 2, gender: "MALE", title: "Anh/em rể họ", reciprocalTitle: "Anh/Chị/Em" },
    { id: "kr-408", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "FEMALE", isElder: true, title: "Bác gái", reciprocalTitle: "Cháu" },
    { id: "kr-409", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "FEMALE", isElder: false, title: "Thím", reciprocalTitle: "Cháu" },
    { id: "kr-410", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "MALE", title: "Dượng", reciprocalTitle: "Cháu" },
    { id: "kr-411", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 2, gender: "FEMALE", title: "Bác gái/Thím họ", reciprocalTitle: "Cháu" },
    { id: "kr-412", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 2, gender: "MALE", title: "Dượng họ", reciprocalTitle: "Cháu" },
    // Parents-in-law. A rule keys on the gender of the person being ADDRESSED,
    // so it cannot know whether this is the speaker's wife's father or
    // husband's father — hence the hedged title, same as elsewhere when the
    // data cannot decide.
    { id: "kr-419", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 0, gender: "MALE", title: "Cha vợ/Cha chồng", reciprocalTitle: "Con" },
    { id: "kr-420", genDelta: -1, side: "IN_LAW", throughMarriage: true, collateralDegree: 0, gender: "FEMALE", title: "Mẹ vợ/Mẹ chồng", reciprocalTitle: "Con" },
    { id: "kr-421", genDelta: -2, side: "IN_LAW", throughMarriage: true, collateralDegree: 0, title: "Ông/Bà (bên vợ/chồng)", reciprocalTitle: "Cháu" },
    { id: "kr-413", genDelta: 1, side: "IN_LAW", throughMarriage: true, collateralDegree: 0, gender: "FEMALE", title: "Con dâu", reciprocalTitle: "Cha/Mẹ chồng" },
    { id: "kr-414", genDelta: 1, side: "IN_LAW", throughMarriage: true, collateralDegree: 0, gender: "MALE", title: "Con rể", reciprocalTitle: "Cha/Mẹ vợ" },
    { id: "kr-415", genDelta: 1, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "FEMALE", title: "Cháu dâu", reciprocalTitle: "Bác/Chú/Cô" },
    { id: "kr-416", genDelta: 1, side: "IN_LAW", throughMarriage: true, collateralDegree: 1, gender: "MALE", title: "Cháu rể", reciprocalTitle: "Bác/Chú/Cô" },
    { id: "kr-417", genDelta: 2, side: "IN_LAW", throughMarriage: true, title: "Cháu dâu/rể" },
    { id: "kr-418", genDelta: -2, side: "IN_LAW", throughMarriage: true, title: "Ông/Bà (bên thông gia)", reciprocalTitle: "Cháu" },
  ],
};

// ---- Events / notifications -------------------------------------------------

export const EVENTS_MOCK: EventDto[] = [
  {
    id: "ev-001",
    eventType: "GIO_TO",
    title: "Giỗ Thủy tổ Nguyễn Văn Thủy Tổ",
    person: toSummary(THUY_TO),
    lunarDate: { year: 1852, month: 9, day: 22, leap: false },
    nextOccurrenceSolar: "2026-11-01",
    daysUntil: 62,
    isClanLevel: true,
    reminderOffsets: [7, 3, 1],
  },
  {
    id: "ev-002",
    eventType: "GIO_CHI",
    title: "Giỗ cụ Nguyễn Văn Hiển",
    person: toSummary(chi1Ancestor),
    lunarDate: { year: 1877, month: 12, day: 15, leap: false },
    nextOccurrenceSolar: "2027-01-21",
    daysUntil: 143,
    targetBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
    isClanLevel: false,
    reminderOffsets: [7, 3, 1],
  },
];

export const NOTIFICATIONS_MOCK: NotificationDto[] = [
  {
    id: "nt-001",
    category: "GIO_REMINDER",
    title: "Còn 3 ngày tới giỗ Thủy tổ",
    body: "Giỗ Thủy tổ Nguyễn Văn Thủy Tổ (22/9 âm lịch) còn 3 ngày nữa.",
    eventId: "ev-001",
    deepLink: "/events/ev-001",
    createdAt: "2026-08-29T01:00:00+07:00",
    isRead: false,
    readAt: null,
    reminderOffsetDays: 3,
  },
  {
    id: "nt-002",
    category: "SYSTEM",
    title: "Yêu cầu đính chính đã được duyệt",
    body: "Trưởng chi đã duyệt cập nhật hồ sơ của bạn.",
    createdAt: "2026-08-20T09:00:00+07:00",
    isRead: true,
    readAt: "2026-08-21T03:00:00+07:00",
  },
];
