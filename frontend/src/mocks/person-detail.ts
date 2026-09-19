import type {
  DateDual,
  PersonDto,
  PersonName,
  PersonSummaryDto,
} from "@/types/api";
import { findPersonMock } from "./data";
import { preProjectionMeta } from "./privacy";
import { getMockGraph } from "./tree-graph/build-graph";
import type { RawPerson } from "./tree-graph/generate-large-tree";

/**
 * Bridges `/persons/{id}` to the ~3,500-node generated graph that backs
 * `/tree`.
 *
 * Before this file, only the 7 hand-authored fixtures in src/mocks/data.ts had
 * a full `PersonDto`, so clicking any other node on the canvas answered 404 —
 * which made F3 (person profile) untestable at scale and, worse, looked
 * exactly like the privacy 404 a guest gets, hiding real bugs behind a correct
 * behaviour. Here every graph person gets a deterministic full-fidelity
 * profile; the curated fixtures still win by id so the "story" characters keep
 * their hand-written detail.
 *
 * Everything below is fake backend data. The lunar dates in particular are
 * FABRICATED, not converted — a mock is allowed to make them up, the client is
 * not allowed to compute them (contracts/README §7.8).
 */

/** Deterministic per-id hash so a given person looks identical on every run. */
function hash(id: string): number {
  let h = 2166136261;
  for (let i = 0; i < id.length; i += 1) {
    h ^= id.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967296;
}

function pick<T>(list: readonly [T, ...T[]], seed: number): T {
  return list[Math.floor(seed * list.length) % list.length] ?? list[0];
}

const TU_NAMES: readonly [string, ...string[]] = ["Đức Thành", "Minh Đạo", "Chính Trực", "Trung Tín", "Hiếu Nghĩa", "Quang Minh"];
const HIEU_NAMES: readonly [string, ...string[]] = ["Tùng Hiên", "Cúc Trai", "Mai Đình", "Trúc Lâm"];
const THUY_NAMES: readonly [string, ...string[]] = ["Trung Hậu Công", "Đoan Chính Công", "Từ Ái Phu Nhân", "Thuần Hậu Công"];
const PHAP_DANH: readonly [string, ...string[]] = ["Thích Minh Tâm", "Diệu Thiện", "Thiện Nhân"];
const HAN_NOM: readonly [string, ...string[]] = ["阮文", "陳氏", "黎文", "范氏", "阮氏"];
const OCCUPATIONS: readonly [string, ...string[]] = [
  "Nông dân", "Thầy đồ", "Thợ mộc", "Lương y", "Giáo viên",
  "Kỹ sư", "Công nhân", "Bộ đội", "Kinh doanh",
];
const PROVINCES: readonly [string, ...string[]] = ["Nam Định", "Hà Nội", "Thái Bình", "Hải Phòng", "Hưng Yên", "Ninh Bình"];

/** Fabricated lunar date, roughly offset from the solar one. Never a conversion. */
function fakeDual(year: number, seed: number): DateDual {
  const month = 1 + Math.floor(seed * 12) % 12;
  const day = 1 + Math.floor(seed * 28) % 28;
  const lunarMonth = ((month + 10) % 12) + 1;
  const lunarDay = ((day + 18) % 28) + 1;
  return {
    solar: `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
    lunar: {
      year: lunarMonth > month ? year - 1 : year,
      month: lunarMonth,
      day: lunarDay,
      // ~1 in 12 gets a leap month, so the UI's leap-month marker is
      // reachable in manual testing instead of theoretical.
      leap: Math.floor(seed * 1000) % 12 === 0,
      canChi: null,
    },
    precision: "DAY",
  };
}

function buildNames(raw: RawPerson, seed: number): PersonName[] {
  const names: PersonName[] = [
    {
      nameType: "HUY",
      fullName: raw.displayName,
      nameHanNom: seed > 0.55 ? `${pick(HAN_NOM, seed)}${raw.gender === "MALE" ? "公" : "娘"}` : null,
      isPrimary: true,
    },
  ];
  if (seed > 0.35) {
    names.push({ nameType: "TU", fullName: pick(TU_NAMES, seed), isPrimary: false });
  }
  if (seed > 0.75) {
    names.push({ nameType: "HIEU", fullName: pick(HIEU_NAMES, seed * 3), isPrimary: false });
  }
  if (!raw.isAlive && seed > 0.6) {
    names.push({ nameType: "THUY", fullName: pick(THUY_NAMES, seed * 7), isPrimary: false });
  }
  if (seed > 0.92) {
    names.push({ nameType: "PHAP_DANH", fullName: pick(PHAP_DANH, seed * 11), isPrimary: false });
  }
  if (raw.isAlive) {
    names.push({ nameType: "THUONG_GOI", fullName: raw.displayName, isPrimary: false });
  }
  return names;
}

/**
 * Full-fidelity PersonDto for a generated graph node. "Full fidelity" means
 * pre-privacy-filter: `projectPersonForRole` (src/mocks/privacy.ts) is what
 * strips it down per caller, exactly as the backend's PrivacyTierFilter does.
 */
export function synthesizePersonDto(raw: RawPerson): PersonDto {
  const seed = hash(raw.id);

  return {
    id: raw.id,
    names: buildNames(raw, seed),
    displayName: raw.displayName,
    gender: raw.gender,
    generation: raw.generation,
    isAlive: raw.isAlive,
    birth: raw.birthYear ? fakeDual(raw.birthYear, seed) : null,
    death: !raw.isAlive && raw.deathYear ? fakeDual(raw.deathYear, seed * 1.7 % 1) : null,
    nativePlace: raw.nativePlace ?? pick(PROVINCES, seed),
    currentPlaceProvince: raw.isAlive ? pick(PROVINCES, seed * 5) : null,
    currentPlaceFull: raw.isAlive ? `Số ${1 + Math.floor(seed * 200)}, ${pick(PROVINCES, seed * 5)}` : null,
    occupation: pick(OCCUPATIONS, seed * 2),
    biography:
      seed > 0.8
        ? `Ghi chép trong gia phả: đời thứ ${raw.generation}, thuộc ${raw.primaryBranch?.name ?? "dòng chính"}.`
        : null,
    primaryBranch: raw.primaryBranch ?? null,
    contact: raw.isAlive
      ? {
          phone: `+84 9${String(Math.floor(seed * 100000000)).padStart(8, "0")}`,
          email: null,
          zaloId: null,
        }
      : null,
    version: 1,
    // Bản ghi "đầy đủ", chưa gắn với người gọi nào — ba cờ quyền chưa có câu
    // trả lời ở đây. `projectPersonForRole` dựng lại `meta` cho từng người gọi.
    meta: preProjectionMeta(raw.isAlive ? "T3" : "PUBLIC"),
  };
}

/**
 * Resolve any person id the app can reach: curated fixture first (they carry
 * hand-authored detail the generator can't), then the generated graph.
 */
export function resolvePersonMock(id: string): PersonDto | undefined {
  const curated = findPersonMock(id);
  if (curated) return curated;
  const raw = getMockGraph().personsById.get(id);
  return raw ? synthesizePersonDto(raw) : undefined;
}

/** True for any id the mock backend knows about at all (fixture or graph). */
export function personExistsMock(id: string): boolean {
  return resolvePersonMock(id) !== undefined;
}

/** Is this person alive? Answered without building the whole DTO. */
export function isAliveMock(id: string): boolean | undefined {
  const raw = getMockGraph().personsById.get(id);
  if (raw) return raw.isAlive;
  return findPersonMock(id)?.isAlive;
}

/** Tier-1-safe summary used by `/persons/search`. */
export function toSummaryMock(person: PersonDto): PersonSummaryDto {
  const solarYear = (d: DateDual | null | undefined) =>
    d?.solar ? Number(d.solar.slice(0, 4)) : undefined;

  return {
    id: person.id,
    displayName: person.displayName ?? person.names.find((n) => n.isPrimary)?.fullName ?? "(?)",
    nameHanNom: person.names.find((n) => n.isPrimary)?.nameHanNom ?? undefined,
    gender: person.gender,
    generation: person.generation,
    isAlive: person.isAlive,
    birthYear: solarYear(person.birth),
    deathYear: solarYear(person.death),
    primaryBranch: person.primaryBranch ?? undefined,
    nativePlace: person.nativePlace ?? undefined,
  };
}
