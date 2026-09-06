import type { BranchRef, Gender, PersonBadge, RelType } from "@/types/api";

/**
 * Deterministic generator for a large synthetic genealogy graph, used ONLY
 * to give Sprint 2's canvas (F2) a few thousand nodes to actually measure
 * rendering performance against (plan-giai-doan-1 §7 warns: "đo ở Sprint 2,
 * đừng để Sprint 4 mới phát hiện"). This is NOT the backend's ~1,500-person
 * demo generator (plan §10, a Flyway/Spring `demo` profile deliverable) —
 * that seeds real Postgres+AGE data. This one only needs to be internally
 * consistent enough to drive MSW.
 *
 * Simplifications made deliberately (documented, not hidden):
 *  - Only sons continue the bloodline into further generations. Daughters
 *    are recorded fully and equally (BA v2 §12) — full profile, generation,
 *    branch, and a married-in spouse (rể) when applicable — but the tree
 *    does not model her children, since they would belong to her husband's
 *    own dòng họ's phả đồ, which this system is not generating here. This
 *    is a mock-data-volume choice, not client-side kinship inference.
 *  - Married-in spouses (dâu/rể) are leaf individuals; we don't generate a
 *    second family tree hanging off of them.
 *  - Badges (TRUONG_CHI, DICH_TON, CON_NUOI, KE_TU, TUYET_TU) are assigned
 *    by the generator directly (this is server-side authored fixture data,
 *    equivalent to what a real backend would have computed and sent) — this
 *    is not the client inferring kinship at render time, which stays
 *    forbidden everywhere else in this codebase.
 */

export interface RawPerson {
  id: string;
  displayName: string;
  gender: Gender;
  generation: number; // 1-based, 1 = thủy tổ
  isAlive: boolean;
  birthYear?: number | null;
  deathYear?: number | null;
  primaryBranch?: BranchRef | null;
  nativePlace?: string | null;
  badges: PersonBadge[];
}

export interface RawEdge {
  id: string;
  source: string;
  target: string;
  relType: RelType;
  spouseOrder?: number | null;
  validTo?: string | null;
}

export interface GeneratedGraph {
  persons: RawPerson[];
  edges: RawEdge[];
  rootId: string;
}

function mulberry32(seed: number) {
  let a = seed;
  return function random() {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const MALE_GIVEN = [
  "Văn An", "Văn Bình", "Văn Cường", "Văn Dũng", "Văn Đức", "Văn Giang",
  "Văn Hải", "Văn Hùng", "Văn Khôi", "Văn Long", "Văn Minh", "Văn Nam",
  "Văn Phong", "Văn Quang", "Văn Sơn", "Văn Thắng", "Văn Tuấn", "Văn Việt",
];
const FEMALE_GIVEN = [
  "Thị Ánh", "Thị Bích", "Thị Cúc", "Thị Dung", "Thị Hoa", "Thị Hồng",
  "Thị Huệ", "Thị Lan", "Thị Liên", "Thị Mai", "Thị Nga", "Thị Nhung",
  "Thị Phương", "Thị Quyên", "Thị Thảo", "Thị Thu", "Thị Trang", "Thị Xuân",
];
const IN_LAW_SURNAMES = [
  "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý",
];
const PROVINCES = ["Nam Định", "Hà Nội", "Thái Bình", "Hải Phòng", "Hưng Yên", "Ninh Bình"];

const CHI_NAMES = ["Chi Nhất", "Chi Nhị", "Chi Tam", "Chi Tứ"];
const CHI_SLUGS = ["chi_nhat", "chi_nhi", "chi_tam", "chi_tu"];
// Branch ids intentionally match the curated fixtures in src/mocks/data.ts
// ("b-chi1"/"b-chi2" already denote Chi Nhất/Chi Nhị there) so the grafted
// story characters (build-graph.ts) land in a branch consistent with their
// hand-authored PersonDto records.
const CHI_IDS = ["b-chi1", "b-chi2", "b-chi3", "b-chi4"];

interface GenerateOptions {
  seed?: number;
  targetSize?: number; // approximate total node count, including spouses
  rootId?: string;
  rootDisplayName?: string;
  /** Force specific ids/names for the 4 generation-2 chi heads, so the curated
   * story dataset (src/mocks/data.ts) and this generated graph share the
   * same individuals where the two overlap. */
  chiHeadOverrides?: Array<{ id: string; displayName: string } | undefined>;
}

/**
 * Builds one big deterministic descendant tree, ~7 generations deep, 4 chi,
 * branching factor tapering with generation so the total stays near
 * `targetSize`. Re-running with the same seed and targetSize always produces
 * the same graph (important so perf runs are comparable run to run).
 */
export function generateLargeTree(options: GenerateOptions = {}): GeneratedGraph {
  const {
    seed = 20260831,
    targetSize = 3000,
    rootId = "p-001",
    rootDisplayName = "Nguyễn Văn Thủy Tổ",
    chiHeadOverrides = [],
  } = options;

  const rand = mulberry32(seed);
  const persons: RawPerson[] = [];
  const edges: RawEdge[] = [];
  let autoId = 0;
  const nextId = () => `g-${(autoId++).toString(36)}`;

  const pick = <T,>(arr: readonly T[]) => arr[Math.floor(rand() * arr.length)] as T;

  function addPerson(p: RawPerson) {
    persons.push(p);
    return p;
  }

  function addEdge(e: RawEdge) {
    edges.push(e);
  }

  function branchFor(chiIndex: number): BranchRef {
    return {
      id: CHI_IDS[chiIndex] ?? `b-chi${chiIndex + 1}`,
      name: CHI_NAMES[chiIndex] ?? `Chi ${chiIndex + 1}`,
      path: `root.${CHI_SLUGS[chiIndex] ?? `chi_${chiIndex + 1}`}`,
      region: "BAC",
    };
  }

  // Root (thủy tổ) + bà tổ.
  const root = addPerson({
    id: rootId,
    displayName: rootDisplayName,
    gender: "MALE",
    generation: 1,
    isAlive: false,
    birthYear: 1780,
    deathYear: 1852,
    nativePlace: "Nam Định",
    primaryBranch: { id: "b-root", name: "Thủy tổ", path: "root", region: "BAC" },
    badges: ["DECEASED"],
  });
  const rootSpouse = addPerson({
    id: nextId(),
    displayName: `Trần Thị ${pick(["Nguyệt", "Liễu", "Xuân", "Hạnh"])}`,
    gender: "FEMALE",
    generation: 1,
    isAlive: false,
    birthYear: 1783,
    deathYear: 1858,
    primaryBranch: root.primaryBranch,
    badges: ["DAU", "DECEASED"],
  });
  addEdge({ id: nextId(), source: root.id, target: rootSpouse.id, relType: "SPOUSE", spouseOrder: 1 });

  let totalCount = persons.length;

  /** Mỗi chi đúng một Trưởng chi — chức danh dòng tộc, không phải thuộc tính lặp lại được. */
  const chiHeadAssigned = new Set<number>();
  /** Đếm ca kế tự đã tạo, để bảo đảm bộ dữ liệu luôn có ít nhất một ca cho badge. */
  let keTuCount = 0;

  function maybeAlive(generation: number): boolean {
    // Matches plan §10's ~60/40 deceased/living split, skewed so early
    // generations are deceased (ancestors) and late generations are mostly
    // living — exercises the guest privacy-tier hole in every subtree.
    if (generation <= 4) return false;
    if (generation === 5) return rand() < 0.5;
    if (generation === 6) return rand() < 0.85;
    return rand() < 0.95;
  }

  function makeSpouse(ofGeneration: number, badge: "DAU" | "RE", alive: boolean): RawPerson {
    const gender: Gender = badge === "DAU" ? "FEMALE" : "MALE";
    const surname = pick(IN_LAW_SURNAMES);
    const given = gender === "FEMALE" ? pick(FEMALE_GIVEN) : pick(MALE_GIVEN);
    return addPerson({
      id: nextId(),
      displayName: `${surname} ${given}`,
      gender,
      generation: ofGeneration,
      isAlive: alive,
      birthYear: alive ? undefined : 1800 + ofGeneration * 25,
      deathYear: alive ? undefined : 1800 + ofGeneration * 25 + 65,
      badges: alive ? [badge] : [badge, "DECEASED"],
    });
  }

  /**
   * Recursively grows the bloodline through `father` (always male, always
   * the tree-continuing parent in this generator). `chiIndex` threads the
   * branch/chi assignment down; `isEldestLine` steers badge placement so at
   * least one TRUONG_CHI / DICH_TON / TUYET_TU / KE_TU example exists per
   * chi, matching plan §10's minimum fixture set.
   */
  function growLine(
    father: RawPerson,
    chiIndex: number,
    depthRemaining: number,
    isEldestLine: boolean,
    forceMarry = false
  ): void {
    if (depthRemaining <= 0 || totalCount >= targetSize) return;

    const alive = maybeAlive(father.generation);
    // Chi heads (forceMarry=true, see the generation-2 loop below) always
    // marry — they founded an ongoing branch, so treating them as a random
    // 10%-chance tuyệt tự would occasionally delete an entire chi's
    // descendants depending on seed, which defeats "4 chi" as a structural
    // guarantee rather than a probabilistic one.
    const isMarried = forceMarry || (father.generation >= 2 && rand() < 0.9);
    let spouse: RawPerson | undefined;
    if (isMarried) {
      spouse = makeSpouse(father.generation, "DAU", alive);
      totalCount++;
      addEdge({ id: nextId(), source: father.id, target: spouse.id, relType: "SPOUSE", spouseOrder: 1 });
      // ~5% of eligible fathers get a second wife (đa thê fixture, plan §10).
      if (rand() < 0.05) {
        const secondWife = makeSpouse(father.generation, "DAU", alive);
        totalCount++;
        addEdge({ id: nextId(), source: father.id, target: secondWife.id, relType: "SPOUSE", spouseOrder: 2 });
      }
    }

    if (!isMarried || totalCount >= targetSize) {
      if (father.generation >= 4 && !alive) father.badges.push("TUYET_TU");
      return;
    }

    // Historical high-fertility families (6-9 children), so 4 chi over ~5
    // reproducing generations (gen2..gen7) land in the "vài nghìn" range
    // the F2 brief asks for real perf numbers against. `totalCount <
    // targetSize` guards throughout `growLine` clip the natural exponential
    // growth back down to `targetSize` well before it would run away.
    const numChildren = 6 + Math.floor(rand() * 4);
    let sonCount = 0;
    const eldestSonIndex = 0;

    for (let i = 0; i < numChildren && totalCount < targetSize; i++) {
      const gender: Gender = rand() < 0.52 ? "MALE" : "FEMALE";
      const childGeneration = father.generation + 1;
      const childAlive = maybeAlive(childGeneration);
      const isAdopted = rand() < 0.012;
      const given = gender === "MALE" ? pick(MALE_GIVEN) : pick(FEMALE_GIVEN);
      const child = addPerson({
        id: nextId(),
        displayName: `Nguyễn ${given}`,
        gender,
        generation: childGeneration,
        isAlive: childAlive,
        birthYear: childAlive ? undefined : 1800 + childGeneration * 25,
        deathYear: childAlive ? undefined : 1800 + childGeneration * 25 + 60,
        primaryBranch: branchFor(chiIndex),
        badges: childAlive ? [] : ["DECEASED"],
      });
      totalCount++;

      addEdge({
        id: nextId(),
        source: father.id,
        target: child.id,
        relType: isAdopted ? "PARENT_ADOPT" : "PARENT_BIO",
      });
      if (spouse) {
        addEdge({
          id: nextId(),
          source: spouse.id,
          target: child.id,
          relType: isAdopted ? "PARENT_ADOPT" : "PARENT_BIO",
        });
      }
      if (isAdopted) child.badges.push("CON_NUOI");

      const isEldestSonHere = gender === "MALE" && sonCount === 0 && i === eldestSonIndex;
      if (gender === "MALE") sonCount++;

      if (gender === "MALE") {
        const stillEldestLine = isEldestLine && isEldestSonHere;
        if (stillEldestLine && childGeneration === 4) {
          child.badges.push("DICH_TON");
        }
        // Trưởng chi: con trưởng của dòng trưởng trong mỗi chi, mỗi chi đúng một người.
        //
        // Điều kiện cũ `childGeneration === Math.min(6, father.generation + 2)` KHÔNG BAO GIỜ
        // đúng: childGeneration luôn bằng father.generation + 1, nên vế phải chỉ khớp khi
        // father.generation = 5 và người đó vẫn còn trên dòng trưởng — tổ hợp không xảy ra ở độ
        // sâu mà bộ sinh này chạy tới. Kết quả: đo trên 3502 người được sinh ra, TRUONG_CHI = 0,
        // trái với chính lời hứa ghi ở đầu file, và badge "Trưởng chi" không bao giờ xuất hiện
        // khi demo trước Hội đồng Tộc biểu.
        if (stillEldestLine && childGeneration >= 3 && !chiHeadAssigned.has(chiIndex)) {
          chiHeadAssigned.add(chiIndex);
          child.badges.push("TRUONG_CHI");
        }
        growLine(child, chiIndex, depthRemaining - 1, stillEldestLine);
      } else if (rand() < 0.6 && totalCount < targetSize) {
        // Daughters recorded fully & equally (BA v2 §12) — give most of them
        // a husband (rể) too, without extending the tree past that couple
        // (see module doc comment for why).
        const husband = makeSpouse(childGeneration, "RE", childAlive);
        totalCount++;
        addEdge({ id: nextId(), source: husband.id, target: child.id, relType: "SPOUSE", spouseOrder: 1 });
      }
    }

    // Kế tự fixture: a childless, deceased father gets one designated heir
    // (adopted nephew, in a real clan) — modeled here as a synthetic adopted
    // son so canvas has at least one CON_NUOI + KE_TU node to badge.
    // Điều kiện cũ đòi đồng thời: không con trai, cha đã mất, VÀ rand() < 0.15 — ba vế này thực
    // tế không bao giờ cùng đúng, nên KE_TU = 0 trên toàn bộ 3502 người. Bỏ ràng buộc "đã mất"
    // (người còn sống vẫn lập người nối dõi được, và đó mới là ca thường gặp), nâng xác suất, và
    // bảo đảm ít nhất một trường hợp tồn tại để badge "kế tự" luôn demo được.
    const needsHeirFixture = keTuCount === 0 && sonCount === 0;
    if (sonCount === 0 && (needsHeirFixture || rand() < 0.4) && totalCount < targetSize) {
      keTuCount++;
      const heirGeneration = father.generation + 1;
      const heir = addPerson({
        id: nextId(),
        displayName: `Nguyễn ${pick(MALE_GIVEN)}`,
        gender: "MALE",
        generation: heirGeneration,
        isAlive: maybeAlive(heirGeneration),
        primaryBranch: branchFor(chiIndex),
        badges: ["CON_NUOI", "KE_TU"],
      });
      totalCount++;
      addEdge({ id: nextId(), source: father.id, target: heir.id, relType: "PARENT_ADOPT" });
      father.badges.push("TUYET_TU");
    }
  }

  for (let chiIndex = 0; chiIndex < 4; chiIndex++) {
    const override = chiHeadOverrides[chiIndex];
    const chiHead = addPerson({
      id: override?.id ?? nextId(),
      displayName: override?.displayName ?? `Nguyễn ${pick(MALE_GIVEN)}`,
      gender: "MALE",
      generation: 2,
      isAlive: false,
      birthYear: 1805 + chiIndex,
      deathYear: 1878 + chiIndex,
      primaryBranch: branchFor(chiIndex),
      nativePlace: pick(PROVINCES),
      badges: ["DECEASED"],
    });
    totalCount++;
    addEdge({ id: nextId(), source: root.id, target: chiHead.id, relType: "PARENT_BIO" });
    addEdge({ id: nextId(), source: rootSpouse.id, target: chiHead.id, relType: "PARENT_BIO" });
    growLine(chiHead, chiIndex, 5, true, true);
  }

  return { persons, edges, rootId: root.id };
}
