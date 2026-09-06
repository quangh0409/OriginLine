import { describe, expect, it } from "vitest";
import { buildFamilyUnits, familyUnitId } from "@/lib/tree/family-units";
import type { FamilyUnit } from "@/lib/tree/family-layout-types";
import type { Gender, TreeEdge, TreeNode } from "@/types/api";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { edge, node, person, threeGenerationFamily } from "../../setup/tree-fixtures";

/**
 * Bảy ca của gia phả Việt mà một thư viện vẽ cây có sẵn sẽ làm sai: đa thê, cha/mẹ đơn thân, con
 * nuôi, hôn phối đã kết thúc, tái hôn, cha mẹ không thành cặp, người chưa có gia đình riêng.
 */

/** Nhân khẩu gọn: giới tính và năm sinh là hai thứ duy nhất phép suy đơn vị gia đình quan tâm. */
function p(
  id: string,
  depth: number,
  opts: { gender?: Gender; birthYear?: number } = {}
): TreeNode {
  return node(id, depth, {
    person: person(id, {
      gender: opts.gender ?? "MALE",
      generation: depth + 1,
      birthYear: opts.birthYear,
    }),
  });
}

const unitOf = (units: FamilyUnit[], ...memberIds: string[]): FamilyUnit => {
  const found = units.find((u) => u.id === familyUnitId(memberIds));
  if (!found) {
    throw new Error(
      `Không tìm thấy đơn vị của [${memberIds.join(", ")}]; đang có: ${units.map((u) => u.id).join(" | ")}`
    );
  }
  return found;
};

// -------------------------------------------------------------------------------------------
// Ca 1 — đa thê
// -------------------------------------------------------------------------------------------
describe("đa thê: một chồng nhiều vợ ⇒ mỗi bà một đơn vị, con ai về đơn vị nấy", () => {
  const nodes: TreeNode[] = [
    p("ong", 0),
    p("vo1", 0, { gender: "FEMALE" }),
    p("vo2", 0, { gender: "FEMALE" }),
    p("con1a", 1, { birthYear: 1901 }),
    p("con1b", 1, { birthYear: 1904 }),
    p("con2a", 1, { birthYear: 1910 }),
  ];
  const edges: TreeEdge[] = [
    edge("m1", "ong", "vo1", "SPOUSE", { spouseOrder: 1 }),
    edge("m2", "ong", "vo2", "SPOUSE", { spouseOrder: 2 }),
    edge("e1", "ong", "con1a"),
    edge("e2", "vo1", "con1a"),
    edge("e3", "ong", "con1b"),
    edge("e4", "vo1", "con1b"),
    edge("e5", "ong", "con2a"),
    edge("e6", "vo2", "con2a"),
  ];

  it("tách thành hai đơn vị, không gộp con của hai bà vào một chỗ", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(units).toHaveLength(2);
    expect(unitOf(units, "ong", "vo1").childIds).toEqual(["con1a", "con1b"]);
    expect(unitOf(units, "ong", "vo2").childIds).toEqual(["con2a"]);
  });

  it("người trục của cả hai đơn vị là người chồng (Hội đồng chốt: chồng đứng giữa)", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(units.map((u) => u.anchorId)).toEqual(["ong", "ong"]);
  });

  it("giữ nguyên spouseOrder để bố cục xếp các bà theo thứ bậc", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(unitOf(units, "ong", "vo1").spouseOrder).toBe(1);
    expect(unitOf(units, "ong", "vo2").spouseOrder).toBe(2);
  });
});

// -------------------------------------------------------------------------------------------
// Ca 2 — cha/mẹ đơn thân
// -------------------------------------------------------------------------------------------
describe("cha/mẹ đơn thân: gia phả cổ ghi con mà không rõ mẹ — dữ liệu hợp lệ, không phải lỗi", () => {
  it("sinh ra đơn vị một người, spouseOrder null, chưa kết thúc", () => {
    const nodes = [p("cha", 0), p("con", 1)];
    const units = buildFamilyUnits(nodes, [edge("e1", "cha", "con")]);
    expect(units).toHaveLength(1);
    expect(units[0]!.partnerIds).toEqual(["cha"]);
    expect(units[0]!.anchorId).toBe("cha");
    expect(units[0]!.spouseOrder).toBeNull();
    expect(units[0]!.ended).toBe(false);
    expect(units[0]!.childIds).toEqual(["con"]);
  });

  it("KHÔNG tự gán đứa con chỉ biết cha sang bà vợ đang đứng cạnh", () => {
    // Bịa ra quan hệ mẹ–con là sai với con của bà vợ khác, sai với con riêng, và là suy đoán về
    // người mà backend cố tình che. Thà thêm một đơn vị đơn thân còn hơn khẳng định sai.
    const nodes = [p("cha", 0), p("vo", 0, { gender: "FEMALE" }), p("con", 1)];
    const edges = [
      edge("m1", "cha", "vo", "SPOUSE", { spouseOrder: 1 }),
      edge("e1", "cha", "con"),
    ];
    const units = buildFamilyUnits(nodes, edges);
    expect(units).toHaveLength(2);
    expect(unitOf(units, "cha", "vo").childIds).toEqual([]);
    expect(unitOf(units, "cha").childIds).toEqual(["con"]);
  });

  it("mẹ đơn thân cũng vậy — không có gì bắt người trục phải là nam", () => {
    const nodes = [p("me", 0, { gender: "FEMALE" }), p("con", 1)];
    const units = buildFamilyUnits(nodes, [edge("e1", "me", "con")]);
    expect(units[0]!.partnerIds).toEqual(["me"]);
    expect(units[0]!.anchorId).toBe("me");
  });
});

// -------------------------------------------------------------------------------------------
// Ca 3 — con nuôi
// -------------------------------------------------------------------------------------------
describe("con nuôi: vẫn là con của gia đình đó, chỉ khác cách vẽ", () => {
  it("nằm trong childIds VÀ trong adoptedChildIds", () => {
    const nodes = [
      p("cha", 0),
      p("me", 0, { gender: "FEMALE" }),
      p("conDe", 1, { birthYear: 1950 }),
      p("conNuoi", 1, { birthYear: 1955 }),
    ];
    const edges = [
      edge("m1", "cha", "me", "SPOUSE", { spouseOrder: 1 }),
      edge("e1", "cha", "conDe"),
      edge("e2", "me", "conDe"),
      edge("e3", "cha", "conNuoi", "PARENT_ADOPT"),
      edge("e4", "me", "conNuoi", "PARENT_ADOPT"),
    ];
    const unit = unitOf(buildFamilyUnits(nodes, edges), "cha", "me");
    expect(unit.childIds).toEqual(["conDe", "conNuoi"]);
    expect([...unit.adoptedChildIds]).toEqual(["conNuoi"]);
    expect(unit.adoptedChildIds.has("conDe")).toBe(false);
  });

  it("con riêng của chồng được mẹ kế nhận nuôi vẫn tính là con nuôi của cặp", () => {
    const nodes = [p("cha", 0), p("meKe", 0, { gender: "FEMALE" }), p("conRieng", 1)];
    const edges = [
      edge("m1", "cha", "meKe", "SPOUSE", { spouseOrder: 2 }),
      edge("e1", "cha", "conRieng"), // con đẻ của chồng
      edge("e2", "meKe", "conRieng", "PARENT_ADOPT"), // mẹ kế nhận nuôi
    ];
    const unit = unitOf(buildFamilyUnits(nodes, edges), "cha", "meKe");
    expect(unit.childIds).toEqual(["conRieng"]);
    expect(unit.adoptedChildIds.has("conRieng")).toBe(true);
  });
});

// -------------------------------------------------------------------------------------------
// Ca 4 — hôn phối đã kết thúc
// -------------------------------------------------------------------------------------------
describe("hôn phối đã kết thúc: vẫn phải nằm trên cây", () => {
  const nodes = [p("chong", 0), p("vo", 0, { gender: "FEMALE" }), p("con", 1)];
  const edges = [
    edge("m1", "chong", "vo", "SPOUSE", { spouseOrder: 1, validTo: "1975-04-30" }),
    edge("e1", "chong", "con"),
    edge("e2", "vo", "con"),
  ];

  it("đánh dấu ended nhưng giữ nguyên đơn vị và con cái", () => {
    const unit = unitOf(buildFamilyUnits(nodes, edges), "chong", "vo");
    expect(unit.ended).toBe(true);
    expect(unit.childIds).toEqual(["con"]);
  });

  it("validTo rỗng không tính là đã kết thúc", () => {
    const units = buildFamilyUnits(nodes, [
      edge("m1", "chong", "vo", "SPOUSE", { spouseOrder: 1, validTo: "  " }),
    ]);
    expect(unitOf(units, "chong", "vo").ended).toBe(false);
  });

  it("ly hôn rồi tái hợp đúng người cũ ⇒ vẫn đang tiếp diễn, không phải đã kết thúc", () => {
    const units = buildFamilyUnits(nodes, [
      edge("m1", "chong", "vo", "SPOUSE", { spouseOrder: 1, validTo: "1975-04-30" }),
      edge("m2", "chong", "vo", "SPOUSE", { spouseOrder: 1 }),
    ]);
    expect(unitOf(units, "chong", "vo").ended).toBe(false);
  });
});

// -------------------------------------------------------------------------------------------
// Ca 5 — tái hôn: một người ở hai đơn vị cùng lúc
// -------------------------------------------------------------------------------------------
describe("tái hôn: cùng lúc thuộc hai đơn vị — không được nuốt mất cái nào", () => {
  const nodes: TreeNode[] = [
    p("ba", 0, { gender: "FEMALE" }),
    p("chongTruoc", 0),
    p("chongSau", 0),
    p("conTruoc", 1),
    p("conSau", 1),
  ];
  const edges: TreeEdge[] = [
    edge("m1", "chongTruoc", "ba", "SPOUSE", { spouseOrder: 1, validTo: "1960-01-01" }),
    edge("m2", "chongSau", "ba", "SPOUSE", { spouseOrder: 1 }),
    edge("e1", "chongTruoc", "conTruoc"),
    edge("e2", "ba", "conTruoc"),
    edge("e3", "chongSau", "conSau"),
    edge("e4", "ba", "conSau"),
  ];

  it("trả về đủ cả hai đơn vị và người tái hôn có mặt ở cả hai", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(units).toHaveLength(2);
    const inBoth = units.filter((u) => u.partnerIds.includes("ba"));
    expect(inBoth).toHaveLength(2);
  });

  it("con của cuộc hôn phối nào ở lại đúng cuộc hôn phối đó", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(unitOf(units, "ba", "chongTruoc").childIds).toEqual(["conTruoc"]);
    expect(unitOf(units, "ba", "chongSau").childIds).toEqual(["conSau"]);
  });

  it("chỉ cuộc hôn phối trước bị đánh dấu ended", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(unitOf(units, "ba", "chongTruoc").ended).toBe(true);
    expect(unitOf(units, "ba", "chongSau").ended).toBe(false);
  });

  it("người có nhiều bạn đời làm trục của cả hai đơn vị", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(units.map((u) => u.anchorId)).toEqual(["ba", "ba"]);
  });
});

// -------------------------------------------------------------------------------------------
// Ca 6 — cha mẹ không tạo thành cặp
// -------------------------------------------------------------------------------------------
describe("cha mẹ không có cạnh SPOUSE: vẫn phải có chỗ", () => {
  it("ghép thành một đơn vị đồng sinh thành, spouseOrder null, ended false", () => {
    const nodes = [p("cha", 0), p("me", 0, { gender: "FEMALE" }), p("con", 1)];
    const edges = [edge("e1", "cha", "con"), edge("e2", "me", "con")];
    const units = buildFamilyUnits(nodes, edges);
    expect(units).toHaveLength(1);
    expect(units[0]!.partnerIds).toEqual(["cha", "me"]);
    expect(units[0]!.spouseOrder).toBeNull();
    expect(units[0]!.ended).toBe(false);
    expect(units[0]!.childIds).toEqual(["con"]);
  });

  it("khi cha có sẵn hôn phối với người khác, đứa con vẫn về đúng cặp sinh thành của nó", () => {
    const nodes = [
      p("cha", 0),
      p("voCa", 0, { gender: "FEMALE" }),
      p("meRuot", 0, { gender: "FEMALE" }),
      p("con", 1),
    ];
    const edges = [
      edge("m1", "cha", "voCa", "SPOUSE", { spouseOrder: 1 }),
      edge("e1", "cha", "con"),
      edge("e2", "meRuot", "con"),
    ];
    const units = buildFamilyUnits(nodes, edges);
    expect(unitOf(units, "cha", "meRuot").childIds).toEqual(["con"]);
    expect(unitOf(units, "cha", "voCa").childIds).toEqual([]);
  });

  it("cha mẹ đẻ được ưu tiên hơn cha/mẹ nuôi khi đứa con có ba người cha mẹ", () => {
    const nodes = [
      p("chaDe", 0),
      p("meDe", 0, { gender: "FEMALE" }),
      p("chaNuoi", 0),
      p("con", 1),
    ];
    const edges = [
      edge("e1", "chaDe", "con"),
      edge("e2", "meDe", "con"),
      edge("e3", "chaNuoi", "con", "PARENT_ADOPT"),
    ];
    const units = buildFamilyUnits(nodes, edges);
    // Mỗi đứa con chỉ đứng ở MỘT chỗ; liên kết cha nuôi dôi ra là việc của đường phụ
    // (AuxiliaryLink kind CROSS_BRANCH_PARENT) ở tầng bố cục.
    expect(unitOf(units, "chaDe", "meDe").childIds).toEqual(["con"]);
    expect(units.every((u) => !u.partnerIds.includes("chaNuoi") || u.childIds.length === 0)).toBe(
      true
    );
  });
});

// -------------------------------------------------------------------------------------------
// Ca 7 — người chưa có gia đình riêng
// -------------------------------------------------------------------------------------------
describe("người không thuộc đơn vị nào", () => {
  it("không sinh đơn vị và không ném lỗi", () => {
    const units = buildFamilyUnits([p("motMinh", 0)], []);
    expect(units).toEqual([]);
  });

  it("projection rỗng trả về mảng rỗng", () => {
    expect(buildFamilyUnits([], [])).toEqual([]);
  });

  it("hôn phối chưa có con vẫn là một đơn vị (phải có thanh hôn phối để vẽ)", () => {
    const nodes = [p("chong", 0), p("vo", 0, { gender: "FEMALE" })];
    const units = buildFamilyUnits(nodes, [edge("m1", "chong", "vo", "SPOUSE", { spouseOrder: 1 })]);
    expect(units).toHaveLength(1);
    expect(units[0]!.childIds).toEqual([]);
  });
});

// -------------------------------------------------------------------------------------------
// Dữ liệu khuyết — cây tải theo lô nên id trỏ ra ngoài projection là chuyện thường
// -------------------------------------------------------------------------------------------
describe("chịu được dữ liệu khuyết", () => {
  it("bỏ qua cạnh trỏ tới người không có trong nodes", () => {
    const nodes = [p("cha", 0), p("con", 1)];
    const edges = [
      edge("e1", "cha", "con"),
      edge("e2", "cha", "chuaTai"),
      edge("m1", "cha", "vơChuaTai", "SPOUSE", { spouseOrder: 1 }),
    ];
    const units = buildFamilyUnits(nodes, edges);
    expect(units).toHaveLength(1);
    expect(units[0]!.partnerIds).toEqual(["cha"]);
    expect(units[0]!.childIds).toEqual(["con"]);
  });

  it("bỏ qua parentIds/spouseIds trỏ ra ngoài projection", () => {
    const nodes = [
      node("cha", 0, { spouseIds: ["khongCoTrongLo"] }),
      node("con", 1, { parentIds: ["cha", "meChuaTai"] }),
    ];
    const units = buildFamilyUnits(nodes, []);
    expect(units).toHaveLength(1);
    expect(units[0]!.partnerIds).toEqual(["cha"]);
    expect(units[0]!.childIds).toEqual(["con"]);
  });

  it("dựng lại được hôn phối từ spouseIds khi projection thiếu cạnh SPOUSE", () => {
    const nodes = [
      node("chong", 0, { spouseIds: ["vo"] }),
      node("vo", 0, { spouseIds: ["chong"] }),
    ];
    const units = buildFamilyUnits(nodes, []);
    expect(units).toHaveLength(1);
    expect(units[0]!.partnerIds).toEqual(["chong", "vo"]);
    expect(units[0]!.spouseOrder).toBeNull();
  });

  it("bỏ qua cạnh tự nối và không nhân bản đơn vị khi cạnh SPOUSE bị lặp hai chiều", () => {
    const nodes = [p("a", 0), p("b", 0, { gender: "FEMALE" })];
    const edges = [
      edge("self", "a", "a", "SPOUSE", { spouseOrder: 1 }),
      edge("m1", "a", "b", "SPOUSE", { spouseOrder: 1 }),
      edge("m2", "b", "a", "SPOUSE", { spouseOrder: 1 }),
    ];
    expect(buildFamilyUnits(nodes, edges)).toHaveLength(1);
  });

  it("bỏ qua cạnh HEIR — thừa tự không phải hôn phối cũng không phải sinh thành", () => {
    const nodes = [p("bac", 0), p("chauThuaTu", 1)];
    const edges = [edge("h1", "bac", "chauThuaTu", "HEIR", { heirKind: "THUA_TU" })];
    expect(buildFamilyUnits(nodes, edges)).toEqual([]);
  });
});

// -------------------------------------------------------------------------------------------
// Id ổn định + thứ tự tất định
// -------------------------------------------------------------------------------------------
describe("id đơn vị ổn định giữa các lần dựng lại", () => {
  const { nodes, edges } = threeGenerationFamily();

  it("suy từ id thành viên, không dùng số đếm", () => {
    const units = buildFamilyUnits(nodes, edges);
    for (const u of units) {
      expect(u.id).toBe(familyUnitId(u.partnerIds));
      expect(u.id).not.toMatch(/\d+$/);
    }
  });

  it("đảo thứ tự nodes/edges không làm đổi id (React Flow dùng id này làm khoá)", () => {
    const a = buildFamilyUnits(nodes, edges);
    const b = buildFamilyUnits([...nodes].reverse(), [...edges].reverse());
    expect(new Set(b.map((u) => u.id))).toEqual(new Set(a.map((u) => u.id)));
    for (const u of a) {
      const same = b.find((x) => x.id === u.id)!;
      expect([...same.childIds].sort()).toEqual([...u.childIds].sort());
    }
  });

  it("hai lần gọi liên tiếp cho kết quả giống hệt nhau", () => {
    expect(buildFamilyUnits(nodes, edges)).toEqual(buildFamilyUnits(nodes, edges));
  });
});

// -------------------------------------------------------------------------------------------
// Thứ tự con
// -------------------------------------------------------------------------------------------
describe("thứ tự con", () => {
  const nodes = [
    p("cha", 0),
    p("me", 0, { gender: "FEMALE" }),
    p("thu", 1, { birthYear: 1930 }),
    p("truong", 1, { birthYear: 1925 }),
    p("ut", 1, { birthYear: 1940 }),
  ];
  const spouse = edge("m1", "cha", "me", "SPOUSE", { spouseOrder: 1 });
  const childEdges = ["thu", "truong", "ut"].flatMap((c, i) => [
    edge(`f${i}`, "cha", c),
    edge(`m${i}`, "me", c),
  ]);

  it("sắp theo năm sinh khi mọi đứa con đều biết năm sinh", () => {
    const unit = unitOf(buildFamilyUnits(nodes, [spouse, ...childEdges]), "cha", "me");
    expect(unit.childIds).toEqual(["truong", "thu", "ut"]);
  });

  it("khuyết năm sinh (người sống bị che tầng T1) ⇒ giữ thứ tự backend trả về", () => {
    const mixed = nodes.map((n) =>
      n.id === "ut" ? p("ut", 1) : n
    );
    const unit = unitOf(buildFamilyUnits(mixed, [spouse, ...childEdges]), "cha", "me");
    expect(unit.childIds).toEqual(["thu", "truong", "ut"]);
  });
});

// -------------------------------------------------------------------------------------------
// Bất biến trên một cây trộn nhiều ca
// -------------------------------------------------------------------------------------------
describe("bất biến trên phả đồ trộn đa thê + tái hôn + con nuôi", () => {
  const nodes: TreeNode[] = [
    p("to", 0),
    p("baCa", 0, { gender: "FEMALE" }),
    p("baHai", 0, { gender: "FEMALE" }),
    p("conCa", 1, { birthYear: 1900 }),
    p("conHai", 1, { birthYear: 1908 }),
    p("conNuoi", 1, { birthYear: 1912 }),
    p("dau", 1, { gender: "FEMALE" }),
    p("chau", 2),
  ];
  const edges: TreeEdge[] = [
    edge("m1", "to", "baCa", "SPOUSE", { spouseOrder: 1, validTo: "1930-01-01" }),
    edge("m2", "to", "baHai", "SPOUSE", { spouseOrder: 2 }),
    edge("m3", "conCa", "dau", "SPOUSE", { spouseOrder: 1 }),
    edge("e1", "to", "conCa"),
    edge("e2", "baCa", "conCa"),
    edge("e3", "to", "conHai"),
    edge("e4", "baHai", "conHai"),
    edge("e5", "to", "conNuoi", "PARENT_ADOPT"),
    edge("e6", "baHai", "conNuoi", "PARENT_ADOPT"),
    edge("e7", "conCa", "chau"),
    edge("e8", "dau", "chau"),
  ];

  it("mỗi người con thuộc đúng một đơn vị", () => {
    const units = buildFamilyUnits(nodes, edges);
    const seen = new Map<string, number>();
    for (const u of units) {
      for (const c of u.childIds) seen.set(c, (seen.get(c) ?? 0) + 1);
    }
    expect([...seen.values()].every((n) => n === 1)).toBe(true);
    expect([...seen.keys()].sort()).toEqual(["chau", "conCa", "conHai", "conNuoi"]);
  });

  it("dựng đủ ba đơn vị và người trục đời con là người trong họ, không phải nàng dâu", () => {
    const units = buildFamilyUnits(nodes, edges);
    expect(units.map((u) => u.id).sort()).toEqual(
      [
        familyUnitId(["to", "baCa"]),
        familyUnitId(["to", "baHai"]),
        familyUnitId(["conCa", "dau"]),
      ].sort()
    );
    expect(unitOf(units, "conCa", "dau").anchorId).toBe("conCa");
  });

  it("con nuôi của bà hai nằm trong childIds của đúng bà hai", () => {
    const units = buildFamilyUnits(nodes, edges);
    const unit = unitOf(units, "to", "baHai");
    expect(unit.childIds).toEqual(["conHai", "conNuoi"]);
    expect([...unit.adoptedChildIds]).toEqual(["conNuoi"]);
  });

  it("adoptedChildIds luôn là tập con của childIds", () => {
    for (const u of buildFamilyUnits(nodes, edges)) {
      for (const id of u.adoptedChildIds) expect(u.childIds).toContain(id);
    }
  });

  it("mọi partnerIds/anchorId/childIds đều là người có thật trong projection", () => {
    const ids = new Set(nodes.map((n) => n.id));
    for (const u of buildFamilyUnits(nodes, edges)) {
      expect(u.partnerIds.length).toBeGreaterThanOrEqual(1);
      expect(u.partnerIds.length).toBeLessThanOrEqual(2);
      expect(u.partnerIds.every((id) => ids.has(id))).toBe(true);
      expect(u.partnerIds).toContain(u.anchorId);
      expect(u.childIds.every((id) => ids.has(id))).toBe(true);
    }
  });
});

// -------------------------------------------------------------------------------------------
// Quy mô thật — chạy trên phả đồ sinh sẵn (~4.000 nhân khẩu) chứ không chỉ fixture dăm người
// -------------------------------------------------------------------------------------------
describe("phả đồ quy mô thật", () => {
  const projection = queryTreeProjection(getMockGraph(), {
    rootId: getMockGraph().rootId,
    depth: 10,
    direction: "DESCENDANTS",
    maxNodes: 2000,
    isVisible: () => true,
  })!;

  it("dựng được đơn vị cho một lô lớn mà không ném lỗi", () => {
    const units = buildFamilyUnits(projection.nodes, projection.edges);
    expect(projection.nodes.length).toBeGreaterThan(500);
    expect(units.length).toBeGreaterThan(100);
  });

  it("id đơn vị là duy nhất và luôn suy được từ thành viên", () => {
    const units = buildFamilyUnits(projection.nodes, projection.edges);
    const ids = units.map((u) => u.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(units.every((u) => u.id === familyUnitId(u.partnerIds))).toBe(true);
  });

  it("mỗi người con vẫn thuộc đúng một đơn vị ở quy mô này", () => {
    const units = buildFamilyUnits(projection.nodes, projection.edges);
    const seen = new Set<string>();
    for (const u of units) {
      for (const c of u.childIds) {
        expect(seen.has(c), `${c} nằm ở hai đơn vị`).toBe(false);
        seen.add(c);
      }
    }
  });

  it("có thật ca đa thê: người trục của nhiều đơn vị cùng lúc", () => {
    const units = buildFamilyUnits(projection.nodes, projection.edges);
    const perAnchor = new Map<string, number>();
    for (const u of units) perAnchor.set(u.anchorId, (perAnchor.get(u.anchorId) ?? 0) + 1);
    expect([...perAnchor.values()].some((n) => n >= 2)).toBe(true);
  });

  it("có thật con nuôi và không đứa nào lọt ra ngoài childIds", () => {
    const units = buildFamilyUnits(projection.nodes, projection.edges);
    expect(units.some((u) => u.adoptedChildIds.size > 0)).toBe(true);
    for (const u of units) {
      for (const id of u.adoptedChildIds) expect(u.childIds).toContain(id);
    }
  });
});
