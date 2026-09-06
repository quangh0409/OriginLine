import { describe, expect, it } from "vitest";
import { layoutFamily, type LayoutEdge, type LayoutPerson } from "@/lib/tree/layout-family";
import type { FamilyLayout, FamilyUnit } from "@/lib/tree/family-layout-types";
import {
  COUPLE_GAP,
  FAMILY_GAP,
  FAMILY_RANK_SEP,
  HIERARCHICAL_NODE_SEP,
  NODE_HEIGHT,
  NODE_WIDTH,
  SIBLING_BAR_STAGGER,
} from "@/lib/tree/layout-constants";

/**
 * Test hình học cho bố cục phả đồ theo đơn vị gia đình.
 *
 * <p>Yêu cầu gốc của người trong họ là "đường nối đừng chui dưới thẻ nữa". Câu đó được biến thành
 * hai điều kiện máy kiểm được, chạy trên MỌI ca kiểm thử trong tệp này:</p>
 *
 * <ol>
 *   <li>{@link expectNoCardOverlap} — không thẻ 208×96 nào chồng lên thẻ nào.</li>
 *   <li>{@link expectNoLineCrossesCard} — không đoạn đường nào cắt qua lòng một tấm thẻ.</li>
 * </ol>
 *
 * <p>Cộng thêm {@link expectNoRelationLost}: cạnh nào không vẽ được theo khuôn cây thì PHẢI xuất
 * hiện trong {@code auxiliaryLinks}. Không có ca nào được phép làm biến mất một cuộc hôn phối hay
 * một người con khỏi phả đồ.</p>
 */

/* -------------------------------------------------------------------------- */
/* Dựng dữ liệu vào bằng tay — KHÔNG phụ thuộc cài đặt của tầng suy đơn vị.   */
/* -------------------------------------------------------------------------- */

function person(id: string, depth: number): LayoutPerson {
  return { id, depth };
}

function unit(
  id: string,
  partnerIds: string[],
  childIds: string[],
  extra: {
    anchorId?: string;
    spouseOrder?: number | null;
    adopted?: string[];
    ended?: boolean;
  } = {}
): FamilyUnit {
  return {
    id,
    partnerIds,
    anchorId: extra.anchorId ?? partnerIds[0]!,
    spouseOrder: extra.spouseOrder ?? null,
    childIds,
    adoptedChildIds: new Set(extra.adopted ?? []),
    ended: extra.ended ?? false,
  };
}

/* -------------------------------------------------------------------------- */
/* Bộ kiểm hình học                                                            */
/* -------------------------------------------------------------------------- */

/** Sai số cho phép: mọi đoạn được PHÉP chạm đúng mép thẻ (đó là chỗ nó cắm vào). */
const TOUCH = 0.5;

interface Rect {
  readonly id: string;
  readonly x1: number;
  readonly y1: number;
  readonly x2: number;
  readonly y2: number;
}

interface Seg {
  readonly label: string;
  readonly x1: number;
  readonly y1: number;
  readonly x2: number;
  readonly y2: number;
}

function cardsOf(layout: FamilyLayout): Rect[] {
  return [...layout.positions.entries()].map(([id, p]) => ({
    id,
    x1: p.x,
    y1: p.y,
    x2: p.x + NODE_WIDTH,
    y2: p.y + NODE_HEIGHT,
  }));
}

/** Mọi đoạn thẳng mà tầng vẽ sẽ đưa lên canvas — thanh, cuống, đường rơi, và đường phụ. */
function segmentsOf(layout: FamilyLayout): Seg[] {
  const segs: Seg[] = [];
  for (const j of layout.junctions) {
    if (j.marriageBar) {
      segs.push({
        label: `marriageBar[${j.unitId}]`,
        x1: j.marriageBar.x1,
        y1: j.marriageBar.y,
        x2: j.marriageBar.x2,
        y2: j.marriageBar.y,
      });
    }
    if (j.siblingBar) {
      segs.push({
        label: `siblingBar[${j.unitId}]`,
        x1: j.siblingBar.x1,
        y1: j.siblingBar.y,
        x2: j.siblingBar.x2,
        y2: j.siblingBar.y,
      });
    }
    if (j.stem) {
      segs.push({
        label: `stem[${j.unitId}]`,
        x1: j.stem.x,
        y1: j.stem.yFrom,
        x2: j.stem.x,
        y2: j.stem.yTo,
      });
    }
    for (const d of j.childDrops) {
      segs.push({
        label: `drop[${j.unitId}->${d.childId}]`,
        x1: d.x,
        y1: d.yFrom,
        x2: d.x,
        y2: d.yTo,
      });
    }
  }
  for (const l of layout.auxiliaryLinks) {
    for (let i = 1; i < l.waypoints.length; i += 1) {
      const a = l.waypoints[i - 1]!;
      const b = l.waypoints[i]!;
      segs.push({ label: `aux[${l.id}]#${i}`, x1: a.x, y1: a.y, x2: b.x, y2: b.y });
    }
  }
  return segs;
}

/** (a) Không thẻ nào chồng lên thẻ nào. */
function expectNoCardOverlap(layout: FamilyLayout): void {
  const cards = cardsOf(layout);
  const clashes: string[] = [];
  for (let i = 0; i < cards.length; i += 1) {
    for (let k = i + 1; k < cards.length; k += 1) {
      const a = cards[i]!;
      const b = cards[k]!;
      if (a.x1 < b.x2 - TOUCH && a.x2 > b.x1 + TOUCH && a.y1 < b.y2 - TOUCH && a.y2 > b.y1 + TOUCH) {
        clashes.push(`${a.id}@(${a.x1},${a.y1}) chồng lên ${b.id}@(${b.x1},${b.y1})`);
      }
    }
  }
  expect(clashes, `thẻ chồng nhau:\n${clashes.join("\n")}`).toEqual([]);
}

/**
 * (b) Không đoạn đường nào cắt qua lòng một tấm thẻ.
 *
 * <p>Mọi đoạn ở đây đều nằm ngang hoặc thẳng đứng, nên bao chữ nhật của đoạn CHÍNH LÀ đoạn đó —
 * phép giao hình chữ nhật vì thế là phép kiểm chính xác, không xấp xỉ.</p>
 */
function expectNoLineCrossesCard(layout: FamilyLayout): void {
  const cards = cardsOf(layout);
  const hits: string[] = [];
  for (const s of segmentsOf(layout)) {
    const sx1 = Math.min(s.x1, s.x2);
    const sx2 = Math.max(s.x1, s.x2);
    const sy1 = Math.min(s.y1, s.y2);
    const sy2 = Math.max(s.y1, s.y2);
    for (const c of cards) {
      if (sx1 < c.x2 - TOUCH && sx2 > c.x1 + TOUCH && sy1 < c.y2 - TOUCH && sy2 > c.y1 + TOUCH) {
        hits.push(`${s.label} (${sx1},${sy1})-(${sx2},${sy2}) xuyên qua thẻ ${c.id}`);
      }
    }
  }
  expect(hits, `đoạn cắt qua thẻ:\n${hits.slice(0, 12).join("\n")}`).toEqual([]);
}

/** Không đoạn chéo — mọi đoạn chỉ thẳng đứng hoặc nằm ngang. */
function expectOrthogonal(layout: FamilyLayout): void {
  const diagonals = segmentsOf(layout)
    .filter((s) => Math.abs(s.x1 - s.x2) > TOUCH && Math.abs(s.y1 - s.y2) > TOUCH)
    .map((s) => `${s.label} (${s.x1},${s.y1})-(${s.x2},${s.y2})`);
  expect(diagonals, `đoạn chéo:\n${diagonals.join("\n")}`).toEqual([]);
}

/** Không cạnh nào bị đánh rơi: hoặc vẽ theo khuôn cây, hoặc nằm trong auxiliaryLinks. */
function expectNoRelationLost(
  layout: FamilyLayout,
  units: readonly FamilyUnit[],
  persons: readonly LayoutPerson[],
  edges: readonly LayoutEdge[] = []
): void {
  const known = new Set(persons.map((p) => p.id));
  const byUnit = new Map(layout.junctions.map((j) => [j.unitId, j]));
  const missing: string[] = [];

  // Cạnh thô: mọi cạnh của phép chiếu phải hiện diện ở đâu đó trên canvas.
  for (const e of edges) {
    if (!known.has(e.source) || !known.has(e.target) || e.source === e.target) continue;
    const auxed = layout.auxiliaryLinks.some(
      (l) =>
        (l.sourceId === e.source && l.targetId === e.target) ||
        (l.sourceId === e.target && l.targetId === e.source)
    );
    if (auxed) continue;
    if (e.relType === "SPOUSE") {
      const inline = units.some((u) => {
        const ps = u.partnerIds.filter((p) => known.has(p));
        return (
          ps.includes(e.source) && ps.includes(e.target) && byUnit.get(u.id)?.marriageBar != null
        );
      });
      if (!inline) missing.push(`cạnh SPOUSE ${e.source}–${e.target} biến mất`);
      continue;
    }
    if (e.relType === "HEIR") {
      missing.push(`cạnh HEIR ${e.source}→${e.target} biến mất`);
      continue;
    }
    // Một cặp cha mẹ chỉ cần MỘT đường xuống đứa con: hoặc cuống theo khuôn cây, hoặc một đường
    // phụ từ một trong hai người. Vẽ song song hai đường từ ông và từ bà là thừa, không phải đủ.
    const drawnForCouple = units.some((u) => {
      const ps = u.partnerIds.filter((p) => known.has(p));
      if (!ps.includes(e.source) || !u.childIds.includes(e.target)) return false;
      const hasDrop = (byUnit.get(u.id)?.childDrops ?? []).some((d) => d.childId === e.target);
      const hasAux = layout.auxiliaryLinks.some(
        (l) => l.targetId === e.target && ps.includes(l.sourceId)
      );
      return hasDrop || hasAux;
    });
    if (!drawnForCouple) missing.push(`cạnh ${e.relType} ${e.source}→${e.target} biến mất`);
  }

  for (const u of units) {
    const partners = u.partnerIds.filter((p) => known.has(p));
    if (partners.length === 0) continue;

    if (partners.length >= 2) {
      const a = partners[0]!;
      const b = partners[1]!;
      const drawnInline = byUnit.get(u.id)?.marriageBar != null;
      const drawnAux = layout.auxiliaryLinks.some(
        (l) =>
          (l.sourceId === a && l.targetId === b) || (l.sourceId === b && l.targetId === a)
      );
      if (!drawnInline && !drawnAux) missing.push(`hôn phối ${a}–${b} (${u.id}) biến mất`);
    }

    for (const child of u.childIds) {
      if (!known.has(child)) continue;
      const drawnDrop = byUnit.get(u.id)?.childDrops.some((d) => d.childId === child) ?? false;
      const drawnAux = layout.auxiliaryLinks.some(
        (l) => l.targetId === child && partners.includes(l.sourceId)
      );
      if (!drawnDrop && !drawnAux) missing.push(`quan hệ cha mẹ–con ${u.id}→${child} biến mất`);
    }
  }
  expect(missing, missing.join("\n")).toEqual([]);
}

/** Chạy trọn bộ bất biến — mọi ca kiểm thử đều phải đi qua đây. */
function expectHealthy(
  layout: FamilyLayout,
  units: readonly FamilyUnit[],
  persons: readonly LayoutPerson[],
  edges: readonly LayoutEdge[] = []
): void {
  for (const p of persons) {
    expect(layout.positions.has(p.id), `thiếu vị trí cho ${p.id}`).toBe(true);
    const pos = layout.positions.get(p.id)!;
    expect(Number.isFinite(pos.x) && Number.isFinite(pos.y), `toạ độ hỏng ở ${p.id}`).toBe(true);
  }
  expectNoCardOverlap(layout);
  expectNoLineCrossesCard(layout);
  expectOrthogonal(layout);
  expectNoRelationLost(layout, units, persons, edges);
}

function centerX(layout: FamilyLayout, id: string): number {
  return layout.positions.get(id)!.x + NODE_WIDTH / 2;
}

/* -------------------------------------------------------------------------- */
/* Bộ dữ liệu mẫu                                                              */
/* -------------------------------------------------------------------------- */

/** Một cặp vợ chồng, ba người con — ca cơ bản nhất của phả đồ. */
function simpleFamily(): { persons: LayoutPerson[]; units: FamilyUnit[] } {
  return {
    persons: [
      person("ong", 0),
      person("ba", 0),
      person("con1", 1),
      person("con2", 1),
      person("con3", 1),
    ],
    units: [unit("u1", ["ong", "ba"], ["con1", "con2", "con3"], { spouseOrder: 1 })],
  };
}

/** Ba đời, đời giữa có gia đình riêng. */
function threeGenerations(): { persons: LayoutPerson[]; units: FamilyUnit[] } {
  return {
    persons: [
      person("to", 0),
      person("toBa", 0),
      person("truong", 1),
      person("dau", 1),
      person("thu", 1),
      person("chau1", 2),
      person("chau2", 2),
    ],
    units: [
      unit("u1", ["to", "toBa"], ["truong", "thu"], { spouseOrder: 1 }),
      unit("u2", ["truong", "dau"], ["chau1", "chau2"], { spouseOrder: 1 }),
    ],
  };
}

/** Đa thê: cụ ông ba bà, mỗi bà hai con. */
function polygamyThreeWives(): { persons: LayoutPerson[]; units: FamilyUnit[] } {
  return {
    persons: [
      person("cu", 0),
      person("ba1", 0),
      person("ba2", 0),
      person("ba3", 0),
      person("a1", 1),
      person("a2", 1),
      person("b1", 1),
      person("b2", 1),
      person("c1", 1),
      person("c2", 1),
    ],
    units: [
      unit("u1", ["cu", "ba1"], ["a1", "a2"], { anchorId: "cu", spouseOrder: 1 }),
      unit("u2", ["cu", "ba2"], ["b1", "b2"], { anchorId: "cu", spouseOrder: 2 }),
      unit("u3", ["cu", "ba3"], ["c1", "c2"], { anchorId: "cu", spouseOrder: 3 }),
    ],
  };
}

/**
 * Tái hôn kiểu khó: bà Mai thuộc hai đơn vị, và ông chồng sau cũng có hai bà.
 *
 * <p>Cả hai đều đa hôn nên không ai "nhường" chỗ được — đúng ca mà cây gia đình thuần không xếp
 * nổi, và là lý do {@code auxiliaryLinks} tồn tại.</p>
 */
function remarriageBothSides(): { persons: LayoutPerson[]; units: FamilyUnit[] } {
  return {
    persons: [
      person("chongTruoc", 0),
      person("mai", 0),
      person("chongSau", 0),
      person("baHai", 0),
      person("conMai1", 1),
      person("conChung", 1),
      person("conBaHai", 1),
    ],
    units: [
      unit("uA", ["chongTruoc", "mai"], ["conMai1"], {
        anchorId: "mai",
        spouseOrder: 1,
        ended: true,
      }),
      unit("uB", ["chongSau", "mai"], ["conChung"], { anchorId: "chongSau", spouseOrder: 2 }),
      unit("uC", ["chongSau", "baHai"], ["conBaHai"], { anchorId: "chongSau", spouseOrder: 1 }),
    ],
  };
}

/* -------------------------------------------------------------------------- */
/* Bộ sinh dòng họ lớn (tất định) cho ca tải nặng                              */
/* -------------------------------------------------------------------------- */

function seeded(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1103515245) + 12345) >>> 0;
    return s / 4294967296;
  };
}

/**
 * Sinh một dòng họ tất định tới xấp xỉ {@code targetPersons} nhân khẩu.
 *
 * <p>Duyệt theo bề rộng nên số đời tăng đều thay vì có một nhánh chạy sâu hun hút — giống hình dạng
 * gia phả thật hơn, và là ca khó hơn cho phép xếp chỗ vì mỗi đời đều rộng.</p>
 */
function generateClan(
  targetPersons: number,
  seed: number
): { persons: LayoutPerson[]; units: FamilyUnit[] } {
  const rand = seeded(seed);
  const persons: LayoutPerson[] = [];
  const units: FamilyUnit[] = [];
  const depths = new Map<string, number>();
  let counter = 0;
  const make = (depth: number): string => {
    const id = `p${counter}`;
    counter += 1;
    depths.set(id, depth);
    persons.push(person(id, depth));
    return id;
  };

  const queue = [make(0)];
  let head = 0;
  while (head < queue.length && persons.length < targetPersons) {
    const parent = queue[head]!;
    head += 1;
    const generation = depths.get(parent)!;
    // Khoảng 10% cụ ông ba bà, 15% hai bà — đủ để ca đa thê xuất hiện dày trong dữ liệu tải nặng.
    const wives = rand() < 0.1 ? 3 : rand() < 0.25 ? 2 : 1;
    for (let w = 0; w < wives; w += 1) {
      const spouse = make(generation);
      const childIds: string[] = [];
      const kids = 1 + Math.floor(rand() * 4);
      for (let k = 0; k < kids; k += 1) {
        const child = make(generation + 1);
        childIds.push(child);
        // Chỉ một phần con cái lập gia đình — phần còn lại là lá, giống phả thật.
        if (rand() < 0.6) queue.push(child);
      }
      const adopted = rand() < 0.12 ? [childIds[0]!] : [];
      units.push(
        unit(`u${units.length}`, [parent, spouse], childIds, {
          anchorId: parent,
          spouseOrder: w + 1,
          adopted,
          ended: rand() < 0.08,
        })
      );
    }
  }
  return { persons, units };
}

/* -------------------------------------------------------------------------- */
/* Kiểm thử                                                                    */
/* -------------------------------------------------------------------------- */

describe("layoutFamily — vợ chồng và khe hôn phối", () => {
  it("đặt vợ chồng cạnh nhau, cách nhau đúng COUPLE_GAP", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);

    const ong = layout.positions.get("ong")!;
    const ba = layout.positions.get("ba")!;
    expect(ong.y).toBe(ba.y);
    expect(Math.abs(centerX(layout, "ong") - centerX(layout, "ba"))).toBe(NODE_WIDTH + COUPLE_GAP);
    expectHealthy(layout, units, persons);
  });

  it("thanh hôn phối nằm gọn trong khe, ở giữa chiều cao thẻ", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    const j = layout.junctions.find((x) => x.unitId === "u1")!;

    expect(j.marriageBar).not.toBeNull();
    const bar = j.marriageBar!;
    expect(bar.x2 - bar.x1).toBe(COUPLE_GAP);
    expect(bar.y).toBe(layout.positions.get("ong")!.y + NODE_HEIGHT / 2);

    // Thanh phải nằm ĐÚNG giữa hai tấm thẻ, chạm mép mỗi bên.
    const leftCard = Math.min(layout.positions.get("ong")!.x, layout.positions.get("ba")!.x);
    expect(bar.x1).toBe(leftCard + NODE_WIDTH);
    expect(bar.x2).toBe(leftCard + NODE_WIDTH + COUPLE_GAP);
  });

  it("đường rơi xuống con đi qua đúng giữa khe vợ chồng — cơ chế cốt lõi", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    const j = layout.junctions.find((x) => x.unitId === "u1")!;

    const bar = j.marriageBar!;
    expect(j.x).toBe((bar.x1 + bar.x2) / 2);
    expect(j.stem!.x).toBe(j.x);

    // Đoạn rơi phải nằm hẳn giữa hai thẻ, không mép nào lấn sang.
    const ong = layout.positions.get("ong")!;
    const ba = layout.positions.get("ba")!;
    const left = Math.min(ong.x, ba.x) + NODE_WIDTH;
    expect(j.x).toBeGreaterThan(left);
    expect(j.x).toBeLessThan(left + COUPLE_GAP);
  });

  it("giữ cờ 'hôn phối đã kết thúc' để tầng vẽ kẻ nét đứt", () => {
    const { persons, units } = remarriageBothSides();
    const layout = layoutFamily(persons, units);
    expect(layout.junctions.find((j) => j.unitId === "uA")!.ended).toBe(true);
    expect(layout.junctions.find((j) => j.unitId === "uC")!.ended).toBe(false);
  });
});

describe("layoutFamily — căn giữa cha mẹ và con", () => {
  it("căn nhóm con vào chính giữa dưới cặp cha mẹ", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    const j = layout.junctions.find((x) => x.unitId === "u1")!;

    const first = centerX(layout, "con1");
    const last = centerX(layout, "con3");
    expect((first + last) / 2).toBeCloseTo(j.x, 6);
    expect(centerX(layout, "con2")).toBeCloseTo(j.x, 6);
  });

  it("thanh anh em phủ đúng từ thẻ con đầu tới thẻ con cuối và chứa cuống", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    const j = layout.junctions.find((x) => x.unitId === "u1")!;
    const bar = j.siblingBar!;

    expect(bar.x1).toBeCloseTo(centerX(layout, "con1"), 6);
    expect(bar.x2).toBeCloseTo(centerX(layout, "con3"), 6);
    expect(j.stem!.x).toBeGreaterThanOrEqual(bar.x1);
    expect(j.stem!.x).toBeLessThanOrEqual(bar.x2);
    expect(j.stem!.yTo).toBe(bar.y);
  });

  it("thanh anh em nằm trong hành lang giữa hai đời, không chạm hàng thẻ nào", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    const bar = layout.junctions.find((x) => x.unitId === "u1")!.siblingBar!;
    const parentBottom = layout.positions.get("ong")!.y + NODE_HEIGHT;
    const childTop = layout.positions.get("con1")!.y;

    expect(bar.y).toBeGreaterThan(parentBottom);
    expect(bar.y).toBeLessThan(childTop);
    expect(childTop - parentBottom).toBe(FAMILY_RANK_SEP);
  });

  it("một con duy nhất thì rơi thẳng, không cần thanh anh em", () => {
    const persons = [person("cha", 0), person("me", 0), person("con", 1)];
    const units = [unit("u1", ["cha", "me"], ["con"], { spouseOrder: 1 })];
    const layout = layoutFamily(persons, units);
    const j = layout.junctions[0]!;

    expect(j.siblingBar).toBeNull();
    expect(j.stem).toBeNull();
    expect(j.childDrops).toHaveLength(1);
    expect(j.childDrops[0]!.x).toBeCloseTo(j.x, 6);
    expect(j.childDrops[0]!.x).toBeCloseTo(centerX(layout, "con"), 6);
    expect(j.childDrops[0]!.yFrom).toBe(j.y);
    expectHealthy(layout, units, persons);
  });

  it("dựng được ba đời chồng lên nhau, mỗi đời đúng một hàng", () => {
    const { persons, units } = threeGenerations();
    const layout = layoutFamily(persons, units);
    const rowOf = (id: string): number => layout.positions.get(id)!.y;

    expect(rowOf("to")).toBe(rowOf("toBa"));
    expect(rowOf("truong")).toBe(rowOf("dau"));
    expect(rowOf("truong")).toBe(rowOf("thu"));
    expect(rowOf("chau1")).toBe(rowOf("chau2"));
    expect(rowOf("truong") - rowOf("to")).toBe(NODE_HEIGHT + FAMILY_RANK_SEP);
    expect(rowOf("chau1") - rowOf("truong")).toBe(NODE_HEIGHT + FAMILY_RANK_SEP);
    expectHealthy(layout, units, persons);
  });
});

describe("layoutFamily — ranh giới nhóm gia đình", () => {
  it("tách nhóm gia đình bằng FAMILY_GAP, rộng hơn hẳn khe anh em ruột", () => {
    // "truong" đã có vợ con nên là một nhóm gia đình; "thu" đứng một mình.
    const { persons, units } = threeGenerations();
    const layout = layoutFamily(persons, units);

    const truongRight = layout.positions.get("truong")!.x + NODE_WIDTH;
    const dauRight = layout.positions.get("dau")!.x + NODE_WIDTH;
    const groupRight = Math.max(truongRight, dauRight);
    const thuLeft = layout.positions.get("thu")!.x;

    expect(thuLeft - groupRight).toBeGreaterThanOrEqual(FAMILY_GAP);
    expect(FAMILY_GAP).toBeGreaterThan(HIERARCHICAL_NODE_SEP);
  });

  it("anh em ruột chưa lập gia đình chỉ cách nhau khe anh em", () => {
    const { persons, units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    const gap =
      layout.positions.get("con2")!.x - (layout.positions.get("con1")!.x + NODE_WIDTH);
    expect(gap).toBe(HIERARCHICAL_NODE_SEP);
  });
});

describe("layoutFamily — đa thê (ca ba bà)", () => {
  it("để cụ ông đứng giữa, các bà toả ra hai bên, bà cả gần nhất", () => {
    const { persons, units } = polygamyThreeWives();
    const layout = layoutFamily(persons, units);

    const cu = centerX(layout, "cu");
    const d1 = Math.abs(centerX(layout, "ba1") - cu);
    const d2 = Math.abs(centerX(layout, "ba2") - cu);
    const d3 = Math.abs(centerX(layout, "ba3") - cu);

    expect(d1).toBe(NODE_WIDTH + COUPLE_GAP);
    expect(d2).toBe(NODE_WIDTH + COUPLE_GAP);
    expect(d3).toBe(2 * (NODE_WIDTH + COUPLE_GAP));
    // Toả HAI BÊN: bà cả và bà hai phải nằm khác phía so với người trục.
    expect(Math.sign(centerX(layout, "ba1") - cu)).toBe(-Math.sign(centerX(layout, "ba2") - cu));
    // Người trục đứng giữa chứ không dạt về một đầu.
    expect(cu).toBeGreaterThan(Math.min(centerX(layout, "ba1"), centerX(layout, "ba2")));
    expect(cu).toBeLessThan(Math.max(centerX(layout, "ba1"), centerX(layout, "ba2")));

    expectHealthy(layout, units, persons);
  });

  it("cho mỗi bà một thanh anh em riêng, đặt lệch tầng theo SIBLING_BAR_STAGGER", () => {
    const { persons, units } = polygamyThreeWives();
    const layout = layoutFamily(persons, units);

    const bars = ["u1", "u2", "u3"].map(
      (id) => layout.junctions.find((j) => j.unitId === id)!.siblingBar!
    );
    for (const b of bars) expect(b).not.toBeNull();

    const ys = bars.map((b) => b.y).sort((a, b) => a - b);
    expect(new Set(ys).size).toBe(3);
    expect(ys[1]! - ys[0]!).toBeCloseTo(SIBLING_BAR_STAGGER, 6);
    expect(ys[2]! - ys[1]!).toBeCloseTo(SIBLING_BAR_STAGGER, 6);

    // Cả ba thanh vẫn phải nằm trong hành lang, không thanh nào đè lên hàng thẻ đời sau.
    const rowBottom = layout.positions.get("cu")!.y + NODE_HEIGHT;
    const childTop = layout.positions.get("a1")!.y;
    for (const y of ys) {
      expect(y).toBeGreaterThan(rowBottom);
      expect(y).toBeLessThan(childTop);
    }
  });

  it("giữ con của bà nào dưới bà đó, các nhóm con không lẫn vào nhau", () => {
    const { persons, units } = polygamyThreeWives();
    const layout = layoutFamily(persons, units);

    const group = (ids: string[]): { lo: number; hi: number } => ({
      lo: Math.min(...ids.map((i) => layout.positions.get(i)!.x)),
      hi: Math.max(...ids.map((i) => layout.positions.get(i)!.x + NODE_WIDTH)),
    });
    const gA = group(["a1", "a2"]);
    const gB = group(["b1", "b2"]);
    const gC = group(["c1", "c2"]);

    const spans = [gA, gB, gC].sort((x, y) => x.lo - y.lo);
    expect(spans[1]!.lo - spans[0]!.hi).toBeGreaterThanOrEqual(FAMILY_GAP);
    expect(spans[2]!.lo - spans[1]!.hi).toBeGreaterThanOrEqual(FAMILY_GAP);
  });

  it("bà thứ ba không đứng sát được thì trả về đường phụ, không đánh rơi hôn phối", () => {
    const { persons, units } = polygamyThreeWives();
    const layout = layoutFamily(persons, units);

    // Hai bà sát người trục vẽ được thanh hôn phối thật.
    expect(layout.junctions.find((j) => j.unitId === "u1")!.marriageBar).not.toBeNull();
    expect(layout.junctions.find((j) => j.unitId === "u2")!.marriageBar).not.toBeNull();
    // Bà thứ ba thì không — và phải xuất hiện nguyên vẹn ở đường phụ.
    expect(layout.junctions.find((j) => j.unitId === "u3")!.marriageBar).toBeNull();

    const link = layout.auxiliaryLinks.find(
      (l) =>
        (l.sourceId === "cu" && l.targetId === "ba3") ||
        (l.sourceId === "ba3" && l.targetId === "cu")
    );
    expect(link, "hôn phối cụ ông – bà ba biến mất khỏi cây").toBeDefined();
    expect(link!.kind).toBe("REMARRIAGE");
    expect(link!.waypoints.length).toBeGreaterThanOrEqual(2);
  });
});

describe("layoutFamily — tái hôn: một người thuộc hai đơn vị gia đình", () => {
  it("không đánh rơi cạnh nào khi bà Mai thuộc cả hai cuộc hôn phối", () => {
    const { persons, units } = remarriageBothSides();
    const layout = layoutFamily(persons, units);
    expectHealthy(layout, units, persons);
  });

  it("chỉ cấp đúng MỘT chỗ đứng cho người tái hôn", () => {
    const { persons, units } = remarriageBothSides();
    const layout = layoutFamily(persons, units);
    expect(layout.positions.size).toBe(persons.length);
    expect(layout.positions.get("mai")).toBeDefined();
  });

  it("cuộc hôn phối không xếp cạnh được thì đi thành đường phụ có waypoints sẵn", () => {
    const { persons, units } = remarriageBothSides();
    const layout = layoutFamily(persons, units);

    const inlineUnits = layout.junctions.filter((j) => j.marriageBar !== null).map((j) => j.unitId);
    const auxPairs = layout.auxiliaryLinks
      .filter((l) => l.kind === "REMARRIAGE")
      .map((l) => [l.sourceId, l.targetId].sort().join("–"));

    // Bà Mai chỉ đứng cạnh được MỘT trong hai ông; ông kia phải có đường phụ.
    expect(inlineUnits.length).toBeLessThan(3);
    expect(auxPairs.length).toBeGreaterThan(0);
    for (const l of layout.auxiliaryLinks) {
      expect(l.waypoints.length).toBeGreaterThanOrEqual(2);
    }
  });

  it("con của cuộc hôn phối bị đẩy sang đường phụ vẫn treo dưới cha mẹ", () => {
    const { persons, units } = remarriageBothSides();
    const layout = layoutFamily(persons, units);
    for (const childId of ["conMai1", "conChung", "conBaHai"]) {
      const hangs = layout.junctions.some((j) =>
        j.childDrops.some((d) => d.childId === childId)
      );
      const auxed = layout.auxiliaryLinks.some((l) => l.targetId === childId);
      expect(hangs || auxed, `${childId} rơi khỏi cây`).toBe(true);
      // Con luôn ở đúng một đời dưới đơn vị sinh ra nó.
      expect(layout.positions.get(childId)!.y).toBeGreaterThan(layout.positions.get("mai")!.y);
    }
  });
});

describe("layoutFamily — con nuôi và cha mẹ chéo chi", () => {
  it("đánh dấu nét đứt cho đoạn rơi xuống con nuôi, KHÔNG đứt cả thanh anh em", () => {
    const persons = [person("cha", 0), person("me", 0), person("ruot", 1), person("nuoi", 1)];
    const units = [
      unit("u1", ["cha", "me"], ["ruot", "nuoi"], { spouseOrder: 1, adopted: ["nuoi"] }),
    ];
    const layout = layoutFamily(persons, units);
    const j = layout.junctions[0]!;

    expect(j.childDrops.find((d) => d.childId === "nuoi")!.dashed).toBe(true);
    expect(j.childDrops.find((d) => d.childId === "ruot")!.dashed).toBe(false);
    expect(j.siblingBar).not.toBeNull();
    expectHealthy(layout, units, persons);
  });

  it("con có hai bộ cha mẹ thì treo vào cha mẹ đẻ, bộ còn lại thành đường phụ", () => {
    const persons = [
      person("chaDe", 0),
      person("meDe", 0),
      person("chaNuoi", 0),
      person("meNuoi", 0),
      person("be", 1),
      person("anhNuoi", 1),
    ];
    const units = [
      unit("uDe", ["chaDe", "meDe"], ["be"], { spouseOrder: 1 }),
      unit("uNuoi", ["chaNuoi", "meNuoi"], ["anhNuoi", "be"], {
        spouseOrder: 1,
        adopted: ["be"],
      }),
    ];
    const layout = layoutFamily(persons, units);

    const de = layout.junctions.find((j) => j.unitId === "uDe")!;
    const nuoi = layout.junctions.find((j) => j.unitId === "uNuoi")!;
    expect(de.childDrops.map((d) => d.childId)).toContain("be");
    expect(nuoi.childDrops.map((d) => d.childId)).not.toContain("be");

    const link = layout.auxiliaryLinks.find((l) => l.targetId === "be");
    expect(link, "quan hệ cha mẹ nuôi biến mất").toBeDefined();
    expect(link!.kind).toBe("CROSS_BRANCH_PARENT");
    expectHealthy(layout, units, persons);
  });

});

describe("layoutFamily — cạnh thô không lọt vào khuôn đơn vị gia đình", () => {
  it("vẽ cạnh HEIR (kế tự/thừa tự) thành đường phụ, đi vòng chứ không cắt qua đời giữa", () => {
    const { persons, units } = threeGenerations();
    const edges: LayoutEdge[] = [{ source: "to", target: "chau1", relType: "HEIR" }];
    const layout = layoutFamily(persons, units, edges);

    const heir = layout.auxiliaryLinks.find((l) => l.kind === "HEIR");
    expect(heir, "cạnh HEIR biến mất khỏi cây").toBeDefined();
    expect(heir!.sourceId).toBe("to");
    expect(heir!.targetId).toBe("chau1");
    // Cách hai đời ⇒ phải vòng ra làn ngoài rìa; bất biến hình học bắt được nếu nó cắt qua thẻ.
    expectHealthy(layout, units, persons, edges);
  });

  it("giữ được người cha thứ ba: khuôn chỉ chứa hai bạn đời, người dôi ra thành đường phụ", () => {
    const persons = [
      person("chaDe", 0),
      person("meDe", 0),
      person("chaNuoi", 0),
      person("be", 1),
    ];
    const units = [unit("uDe", ["chaDe", "meDe"], ["be"], { spouseOrder: 1 })];
    const edges: LayoutEdge[] = [
      { source: "chaDe", target: "be", relType: "PARENT_BIO" },
      { source: "meDe", target: "be", relType: "PARENT_BIO" },
      { source: "chaNuoi", target: "be", relType: "PARENT_ADOPT" },
      { source: "chaDe", target: "meDe", relType: "SPOUSE" },
    ];
    const layout = layoutFamily(persons, units, edges);

    const link = layout.auxiliaryLinks.find((l) => l.sourceId === "chaNuoi");
    expect(link, "người cha nuôi ở chi khác biến mất khỏi cây").toBeDefined();
    expect(link!.kind).toBe("CROSS_BRANCH_PARENT");
    expect(link!.targetId).toBe("be");
    expectHealthy(layout, units, persons, edges);
  });

  it("không vẽ chồng hai đường phụ song song từ ông và từ bà xuống cùng một đứa con", () => {
    const persons = [
      person("chaDe", 0),
      person("meDe", 0),
      person("chaNuoi", 0),
      person("meNuoi", 0),
      person("be", 1),
      person("anhNuoi", 1),
    ];
    const units = [
      unit("uDe", ["chaDe", "meDe"], ["be"], { spouseOrder: 1 }),
      unit("uNuoi", ["chaNuoi", "meNuoi"], ["anhNuoi", "be"], {
        spouseOrder: 1,
        adopted: ["be"],
      }),
    ];
    const edges: LayoutEdge[] = [
      { source: "chaDe", target: "be", relType: "PARENT_BIO" },
      { source: "meDe", target: "be", relType: "PARENT_BIO" },
      { source: "chaNuoi", target: "be", relType: "PARENT_ADOPT" },
      { source: "meNuoi", target: "be", relType: "PARENT_ADOPT" },
    ];
    const layout = layoutFamily(persons, units, edges);

    expect(layout.auxiliaryLinks.filter((l) => l.targetId === "be")).toHaveLength(1);
    expectHealthy(layout, units, persons, edges);
  });

  it("giữ hôn phối nối sang nhánh khác mà tầng suy đơn vị không dựng thành đơn vị nào", () => {
    const persons = [person("a", 0), person("b", 0), person("con", 1)];
    const units = [unit("u1", ["a"], ["con"])];
    const edges: LayoutEdge[] = [{ source: "a", target: "b", relType: "SPOUSE" }];
    const layout = layoutFamily(persons, units, edges);

    const link = layout.auxiliaryLinks.find((l) => l.kind === "REMARRIAGE");
    expect(link, "cạnh SPOUSE lạc khuôn biến mất").toBeDefined();
    expectHealthy(layout, units, persons, edges);
  });

  it("không nhân đôi đường phụ khi cạnh thô lặp lại quan hệ đã vẽ theo khuôn cây", () => {
    const { persons, units } = simpleFamily();
    const edges: LayoutEdge[] = [
      { source: "ong", target: "ba", relType: "SPOUSE" },
      { source: "ong", target: "con1", relType: "PARENT_BIO" },
      { source: "ba", target: "con1", relType: "PARENT_BIO" },
    ];
    const layout = layoutFamily(persons, units, edges);
    expect(layout.auxiliaryLinks).toEqual([]);
    expectHealthy(layout, units, persons, edges);
  });
});

describe("layoutFamily — cha/mẹ đơn thân và dữ liệu khuyết", () => {
  it("xếp được cha đơn thân: điểm nối ngay dưới thẻ, không có thanh hôn phối", () => {
    const persons = [person("cha", 0), person("con1", 1), person("con2", 1)];
    const units = [unit("u1", ["cha"], ["con1", "con2"])];
    const layout = layoutFamily(persons, units);
    const j = layout.junctions[0]!;

    expect(j.marriageBar).toBeNull();
    expect(j.y).toBe(layout.positions.get("cha")!.y + NODE_HEIGHT);
    expect(j.x).toBeCloseTo(centerX(layout, "cha"), 6);
    expectHealthy(layout, units, persons);
  });

  it("hôn phối chưa có con vẫn được thanh hôn phối, không thanh anh em, không cuống", () => {
    const persons = [person("chong", 0), person("vo", 0)];
    const units = [unit("u1", ["chong", "vo"], [], { spouseOrder: 1 })];
    const layout = layoutFamily(persons, units);
    const j = layout.junctions[0]!;

    expect(j.marriageBar).not.toBeNull();
    expect(j.siblingBar).toBeNull();
    expect(j.stem).toBeNull();
    expect(j.childDrops).toEqual([]);
    expectHealthy(layout, units, persons);
  });

  it("xếp được cặp sinh ra HAI đơn vị: một hôn phối không con và một 'cha đơn thân' mang con", () => {
    // Xảy ra khi người con chỉ có cạnh từ cha; tầng suy đơn vị cố ý không gán bừa người mẹ, vì
    // gán sang bà vợ là bịa ra quan hệ mẹ–con.
    const persons = [person("cha", 0), person("vo", 0), person("con1", 1), person("con2", 1)];
    const units = [
      unit("uHon", ["cha", "vo"], [], { spouseOrder: 1 }),
      unit("uDon", ["cha"], ["con1", "con2"]),
    ];
    const layout = layoutFamily(persons, units);

    const hon = layout.junctions.find((j) => j.unitId === "uHon")!;
    const don = layout.junctions.find((j) => j.unitId === "uDon")!;
    expect(hon.marriageBar).not.toBeNull();
    expect(hon.childDrops).toEqual([]);
    expect(don.marriageBar).toBeNull();
    expect(don.childDrops.map((d) => d.childId)).toEqual(["con1", "con2"]);
    // Điểm nối của đơn vị đơn thân phải nằm dưới thẻ người cha, không phải dưới khe vợ chồng.
    expect(don.y).toBe(layout.positions.get("cha")!.y + NODE_HEIGHT);
    expect(don.x).toBeGreaterThanOrEqual(layout.positions.get("cha")!.x);
    expect(don.x).toBeLessThanOrEqual(layout.positions.get("cha")!.x + NODE_WIDTH);
    expectHealthy(layout, units, persons);
  });

  it("bỏ qua thành viên đã bị lọc theo tầng riêng tư mà không vỡ bố cục", () => {
    // "meAn" không nằm trong danh sách nhân khẩu (backend đã ẩn người còn sống).
    const persons = [person("cha", 0), person("con", 1)];
    const units = [unit("u1", ["cha", "meAn"], ["con", "conAn"], { spouseOrder: 1 })];
    const layout = layoutFamily(persons, units);

    expect(layout.positions.size).toBe(2);
    expect(layout.positions.has("meAn")).toBe(false);
    // Không được bịa ra chỗ trống ám chỉ có người bị ẩn.
    expect(layout.junctions[0]!.marriageBar).toBeNull();
    expect(layout.auxiliaryLinks.every((l) => l.targetId !== "conAn")).toBe(true);
    expectHealthy(layout, units, persons);
  });

  it("vẫn cấp chỗ đứng cho người không thuộc đơn vị gia đình nào", () => {
    const persons = [person("le", 0), ...simpleFamily().persons];
    const { units } = simpleFamily();
    const layout = layoutFamily(persons, units);
    expect(layout.positions.has("le")).toBe(true);
    expectHealthy(layout, units, persons);
  });

  it("không gãy khi danh sách rỗng", () => {
    const layout = layoutFamily([], []);
    expect(layout.positions.size).toBe(0);
    expect(layout.junctions).toEqual([]);
    expect(layout.auxiliaryLinks).toEqual([]);
  });

  it("chịu được dữ liệu vòng mà không đệ quy vô tận", () => {
    const persons = [person("a", 0), person("b", 1)];
    const units = [unit("u1", ["a"], ["b"]), unit("u2", ["b"], ["a"])];
    const layout = layoutFamily(persons, units);
    expect(layout.positions.size).toBe(2);
  });

  it("cho ra cùng một kết quả với cùng một đầu vào (ổn định giữa các lần dựng lại)", () => {
    const { persons, units } = polygamyThreeWives();
    const a = layoutFamily(persons, units);
    const b = layoutFamily(persons, units);
    expect([...a.positions.entries()]).toEqual([...b.positions.entries()]);
    expect(a.junctions).toEqual(b.junctions);
    expect(a.auxiliaryLinks).toEqual(b.auxiliaryLinks);
  });
});

describe("layoutFamily — chế độ xem tổ tiên (depth âm)", () => {
  it("xếp tổ tiên lên phía trên gốc truy vấn", () => {
    const persons = [
      person("ongNoi", -2),
      person("baNoi", -2),
      person("cha", -1),
      person("me", -1),
      person("toi", 0),
    ];
    const units = [
      unit("u1", ["ongNoi", "baNoi"], ["cha"], { spouseOrder: 1 }),
      unit("u2", ["cha", "me"], ["toi"], { spouseOrder: 1 }),
    ];
    const layout = layoutFamily(persons, units);

    expect(layout.positions.get("cha")!.y).toBeGreaterThan(layout.positions.get("ongNoi")!.y);
    expect(layout.positions.get("toi")!.y).toBeGreaterThan(layout.positions.get("cha")!.y);
    // Toạ độ đã được dời về góc phần tư dương để tầng vẽ và phép canh khung đọc cho dễ.
    for (const p of layout.positions.values()) {
      expect(p.x).toBeGreaterThanOrEqual(0);
      expect(p.y).toBeGreaterThanOrEqual(0);
    }
    expectHealthy(layout, units, persons);
  });
});

describe("layoutFamily — dòng họ lớn", () => {
  it("giữ nguyên bất biến hình học trên một dòng họ sinh ngẫu nhiên nhiều đời", () => {
    const { persons, units } = generateClan(300, 20260906);
    expect(persons.length).toBeGreaterThan(300);
    const layout = layoutFamily(persons, units);
    expectHealthy(layout, units, persons);
  });

  it("giữ nguyên bất biến trên năm hạt giống khác nhau", () => {
    for (const seed of [1, 7, 42, 1975, 987654321]) {
      const { persons, units } = generateClan(200, seed);
      const layout = layoutFamily(persons, units);
      expectHealthy(layout, units, persons);
    }
  });

  it("xử lý được quy mô thật (>1500 nhân khẩu) trong một khung hình", () => {
    const { persons, units } = generateClan(1506, 1506);
    expect(persons.length).toBeGreaterThan(1500);

    const started = performance.now();
    const layout = layoutFamily(persons, units);
    const elapsed = performance.now() - started;

    expect(layout.positions.size).toBe(persons.length);
    // Ngưỡng rộng rãi cho máy CI chậm; mục đích là bắt hồi quy về độ phức tạp, không phải đo benchmark.
    expect(elapsed).toBeLessThan(2000);
    expectNoCardOverlap(layout);
    expectNoLineCrossesCard(layout);
    expectOrthogonal(layout);
  });
});
