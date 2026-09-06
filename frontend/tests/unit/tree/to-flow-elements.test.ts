import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { EdgeProps } from "@xyflow/react";
import {
  auxiliaryStroke,
  lineageEndPersonIds,
  toAuxiliaryEdges,
  toFlowEdges,
  toFlowNodes,
  toJunctionEdges,
  TREE_STROKES,
  type AuxiliaryLinkFlowEdge,
  type FamilyJunctionFlowEdge,
  type StrokeStyle,
} from "@/lib/tree/to-flow-elements";
import type { AuxiliaryLink, FamilyJunction, FamilyUnit } from "@/lib/tree/family-layout-types";
import {
  AuxiliaryLinkEdge,
  FamilyJunctionEdge,
  formatCoord,
  junctionPaths,
  lineageEndMark,
  polylinePath,
} from "@/components/tree/family-edges";
import { NODE_HEIGHT, NODE_WIDTH } from "@/lib/tree/layout-constants";
import { colorTokens } from "@/styles/tokens";
import { edge, node } from "../../setup/tree-fixtures";

describe("toFlowNodes", () => {
  it("maps each TreeNode to a React Flow node of the custom 'person' type", () => {
    const nodes = [node("p-001", 0)];
    const flow = toFlowNodes(nodes, new Map([["p-001", { x: 10, y: 20 }]]));
    expect(flow).toHaveLength(1);
    expect(flow[0]).toMatchObject({ id: "p-001", type: "person", position: { x: 10, y: 20 } });
    expect(flow[0]!.data.treeNode).toBe(nodes[0]);
  });

  it("falls back to the origin when a layout produced no position", () => {
    const flow = toFlowNodes([node("orphan", 0)], new Map());
    expect(flow[0]!.position).toEqual({ x: 0, y: 0 });
  });

  it("offers an expand toggle when more descendants exist beyond what is loaded", () => {
    const flow = toFlowNodes([node("n", 0, { hasMoreDescendants: true, childCount: 0 })], new Map());
    expect(flow[0]!.data.hasLoadableChildren).toBe(true);
  });

  it("offers a toggle for a node whose children are already loaded, so it can be collapsed", () => {
    const flow = toFlowNodes([node("n", 0, { hasMoreDescendants: false, childCount: 3 })], new Map());
    expect(flow[0]!.data.hasLoadableChildren).toBe(true);
  });

  it("offers no toggle for a genuinely childless person (tuyệt tự)", () => {
    const flow = toFlowNodes([node("n", 0, { hasMoreDescendants: false, childCount: 0 })], new Map());
    expect(flow[0]!.data.hasLoadableChildren).toBe(false);
  });

  it("offers no toggle when childCount was not sent and nothing more is known", () => {
    const flow = toFlowNodes(
      [node("n", 0, { hasMoreDescendants: false, childCount: null })],
      new Map()
    );
    expect(flow[0]!.data.hasLoadableChildren).toBe(false);
  });

  it("keeps nodes draggable — kéo-thả is an F2 requirement", () => {
    expect(toFlowNodes([node("n", 0)], new Map())[0]!.draggable).toBe(true);
  });
});

/* ==========================================================================
   Quy ước nét
   ========================================================================== */

/**
 * `TREE_STROKES` khai bằng `as const` nên mỗi nét mang kiểu chữ nghĩa đen; nới về {@link StrokeStyle}
 * để hỏi được "nét này có đứt không" trên cả những mục vốn liền.
 */
function widen(stroke: StrokeStyle): StrokeStyle {
  return stroke;
}

describe("TREE_STROKES — quy ước nét Hội đồng đã duyệt", () => {
  it("lấy màu từ tokens, không viết mã màu cứng", () => {
    expect(TREE_STROKES.bioChild.stroke).toContain(colorTokens.textMuted);
    expect(TREE_STROKES.marriage.stroke).toContain(colorTokens.accent);
    expect(TREE_STROKES.heir.stroke).toContain(colorTokens.primary);
  });

  /**
   * Giao diện tối: token là bảng màu sáng cố định, nên nếu nét được gán thẳng mã màu thì đường
   * huyết thống (#5e564d) đặt trên nền tối gần như biến mất. Cho mọi màu đi qua một biến CSS có
   * giá trị dự phòng thì chỉ cần khai lại biến trong khối `.dark` là đọc được, không phải sửa
   * TypeScript. Đây là điều kiện để nét vẽ đọc được ở CẢ hai giao diện.
   */
  it("cho mọi màu đi qua biến CSS ghi đè được, để đọc được ở giao diện tối", () => {
    for (const [name, stroke] of Object.entries(TREE_STROKES)) {
      expect(stroke.stroke, `nét "${name}" không ghi đè được theo giao diện`).toMatch(
        /^var\(--tree-line-[a-z]+, #[0-9a-f]{6}\)$/i
      );
    }
  });

  it("vẽ thanh hôn phối đậm hơn đường huyết thống, và kế tự mảnh hơn con đẻ", () => {
    expect(TREE_STROKES.marriage.strokeWidth).toBeGreaterThan(TREE_STROKES.bioChild.strokeWidth);
    expect(TREE_STROKES.heir.strokeWidth).toBeLessThan(TREE_STROKES.bioChild.strokeWidth);
  });

  it("giữ nguyên màu hôn phối khi hôn phối đã kết thúc — ly hôn/goá vẫn là sự kiện của phả", () => {
    expect(TREE_STROKES.marriageEnded.stroke).toBe(TREE_STROKES.marriage.stroke);
    expect(TREE_STROKES.marriageEnded.strokeDasharray).toBeDefined();
    expect(widen(TREE_STROKES.marriage).strokeDasharray).toBeUndefined();
  });

  it("vẽ kế tự bằng nét LIỀN màu đỏ trầm, không phải nét đứt kiểu con nuôi", () => {
    // Kế tự là dòng cha–con trên danh nghĩa; vẽ đứt như con nuôi là nói sai về phả.
    expect(widen(TREE_STROKES.heir).strokeDasharray).toBeUndefined();
    expect(TREE_STROKES.heir.stroke).not.toBe(TREE_STROKES.bioChild.stroke);
  });

  it("phân biệt nét đường vẽ vòng với nét con nuôi", () => {
    expect(TREE_STROKES.detour.strokeDasharray).not.toBe(TREE_STROKES.adoptedChild.strokeDasharray);
  });
});

/* ==========================================================================
   Cạnh người–người (chỉ dùng cho tỏa tròn / ma trận đời)
   ========================================================================== */

describe("toFlowEdges", () => {
  it("draws a descent edge top-to-bottom", () => {
    const [e] = toFlowEdges([edge("e1", "cha", "con", "PARENT_BIO")]);
    expect(e).toMatchObject({
      id: "e1",
      source: "cha",
      target: "con",
      sourceHandle: "bottom",
      targetHandle: "top",
      type: "smoothstep",
    });
    expect(e!.style?.strokeDasharray).toBeUndefined();
  });

  /**
   * Hội đồng đã chốt bỏ mũi tên trên đường huyết thống: cây vẽ từ trên xuống nên chiều đã hiển
   * nhiên, còn khi thu nhỏ thì mỗi mũi tên đọng thành một chấm đen làm rối cả phả đồ.
   */
  it("không gắn mũi tên lên bất kỳ đường huyết thống nào", () => {
    for (const e of toFlowEdges([
      edge("d1", "cha", "con", "PARENT_BIO"),
      edge("a1", "cha", "con2", "PARENT_ADOPT"),
      edge("h1", "cha", "con3", "HEIR"),
      edge("s1", "chong", "vo", "SPOUSE"),
    ])) {
      expect(e.markerEnd, `cạnh ${e.id} vẫn còn mũi tên`).toBeUndefined();
      expect(e.markerStart).toBeUndefined();
    }
  });

  /**
   * BUG WATCH — đường vợ chồng chui dưới hai tấm thẻ.
   *
   * Bản cũ ép `sourceHandle: "left"` → `targetHandle: "right"`. Với hai thẻ 208px kề nhau, đoạn
   * đó dài 464px mà 416px nằm DƯỚI hai tấm thẻ; người dùng chỉ còn thấy mẩu 48px ở khe giữa và
   * đọc ra là "hai người này chẳng liên quan gì nhau".
   */
  it("không neo cứng đường vợ chồng vào hai bên trái–phải của thẻ", () => {
    const [e] = toFlowEdges([edge("s1", "chong", "vo", "SPOUSE", { spouseOrder: 1 })]);
    expect(e!.sourceHandle).toBeUndefined();
    expect(e!.targetHandle).toBeUndefined();
    expect(e!.type).toBe("straight");
  });

  it("dashes an adoption edge so con nuôi is visually distinguishable", () => {
    const [e] = toFlowEdges([edge("a1", "cha-nuoi", "con", "PARENT_ADOPT")]);
    expect(e!.style?.strokeDasharray).toBe(TREE_STROKES.adoptedChild.strokeDasharray);
  });

  it("dashes a marriage that has ended (divorce or death of a spouse)", () => {
    const [e] = toFlowEdges([edge("s1", "a", "b", "SPOUSE", { validTo: "1998-03-04" })]);
    expect(e!.style?.strokeDasharray).toBe(TREE_STROKES.marriageEnded.strokeDasharray);
    expect(e!.style?.stroke).toBe(TREE_STROKES.marriage.stroke);
  });

  it("keeps a still-current marriage solid", () => {
    const [e] = toFlowEdges([edge("s1", "a", "b", "SPOUSE", { validTo: null })]);
    expect(e!.style?.strokeDasharray).toBeUndefined();
  });

  it("vẽ kế tự bằng nét mảnh màu đỏ trầm chứ không giống con đẻ", () => {
    const [e] = toFlowEdges([edge("h1", "cha-ke", "con", "HEIR")]);
    expect(e!.style?.stroke).toBe(TREE_STROKES.heir.stroke);
    expect(e!.style?.strokeWidth).toBe(TREE_STROKES.heir.strokeWidth);
  });

  it("carries no inline text label — the dashed stroke is explained once in the legend", () => {
    for (const e of toFlowEdges([
      edge("a", "x", "y", "PARENT_ADOPT"),
      edge("b", "x", "z", "SPOUSE"),
    ])) {
      expect(e.label).toBeUndefined();
    }
  });

  it("returns an empty list for an empty edge set", () => {
    expect(toFlowEdges([])).toEqual([]);
  });
});

/* ==========================================================================
   Đơn vị gia đình: điểm nối, thanh hôn phối, thanh anh em
   ========================================================================== */

function unit(overrides: Partial<FamilyUnit> & Pick<FamilyUnit, "id">): FamilyUnit {
  return {
    partnerIds: [],
    anchorId: overrides.partnerIds?.[0] ?? "",
    spouseOrder: null,
    childIds: [],
    adoptedChildIds: new Set<string>(),
    ended: false,
    ...overrides,
  };
}

function junction(
  overrides: Partial<FamilyJunction> & Pick<FamilyJunction, "unitId">
): FamilyJunction {
  return {
    x: 0,
    y: 0,
    marriageBar: null,
    siblingBar: null,
    stem: null,
    childDrops: [],
    ended: false,
    ...overrides,
  };
}

/**
 * Ông A (0,0) và bà B (256,0) — hai thẻ 208px cách nhau khe 48px. Ba người con ở đời dưới, người
 * con giữa là con nuôi. Toạ độ dưới đây đóng vai kết quả của `layout-family.ts`; tầng vẽ chỉ đọc.
 */
const COUPLE_WITH_THREE_CHILDREN = {
  unit: unit({
    id: "u-A-B",
    partnerIds: ["A", "B"],
    anchorId: "A",
    spouseOrder: 1,
    childIds: ["c1", "c2", "c3"],
    adoptedChildIds: new Set(["c2"]),
  }),
  junction: junction({
    unitId: "u-A-B",
    x: 232,
    y: 48,
    marriageBar: { x1: 208, x2: 256, y: 48 },
    stem: { x: 232, yFrom: 48, yTo: 160 },
    siblingBar: { x1: 60, x2: 404, y: 160 },
    childDrops: [
      { childId: "c1", x: 60, yFrom: 160, yTo: 224, dashed: false },
      { childId: "c2", x: 232, yFrom: 160, yTo: 224, dashed: true },
      { childId: "c3", x: 404, yFrom: 160, yTo: 224, dashed: false },
    ],
  }),
};

describe("junctionPaths", () => {
  it("vẽ thanh hôn phối gọn trong khe giữa hai vợ chồng", () => {
    const paths = junctionPaths(COUPLE_WITH_THREE_CHILDREN.junction);
    expect(paths.marriage).toBe("M 208 48 L 256 48");
  });

  /**
   * Quy ước Hội đồng: con nuôi chỉ đứt Ở ĐOẠN RƠI XUỐNG NGƯỜI CON. Nếu thanh anh em cũng đứt thì
   * hai người con ruột đứng cạnh trông như cũng là con nuôi — sai hẳn về phả.
   */
  it("giữ thanh anh em ở nhóm nét LIỀN dù trong nhà có con nuôi", () => {
    const paths = junctionPaths(COUPLE_WITH_THREE_CHILDREN.junction);
    expect(paths.bloodline).toContain("M 60 160 L 404 160"); // thanh anh em
    expect(paths.adopted).not.toContain("160 L 404 160");
  });

  it("chỉ đưa đoạn rơi của con nuôi vào nhóm nét đứt", () => {
    const paths = junctionPaths(COUPLE_WITH_THREE_CHILDREN.junction);
    expect(paths.adopted).toBe("M 232 160 L 232 224");
    expect(paths.bloodline).toContain("M 60 160 L 60 224");
    expect(paths.bloodline).toContain("M 404 160 L 404 224");
    expect(paths.bloodline).not.toContain("M 232 160 L 232 224");
  });

  it("gộp mọi đoạn cùng quy ước nét vào một chuỗi d, tối đa ba <path> cho mỗi gia đình", () => {
    const paths = junctionPaths(COUPLE_WITH_THREE_CHILDREN.junction);
    // thân nối + thanh anh em + 2 đoạn rơi con đẻ = 4 đoạn trong MỘT chuỗi
    expect(paths.bloodline.match(/M /g)).toHaveLength(4);
    expect(paths.adopted.match(/M /g)).toHaveLength(1);
  });

  /**
   * Cơ chế đã chữa được lỗi "đường nối chui dưới thẻ" là: mọi đoạn đều nằm ngang hoặc thẳng đứng
   * và nằm gọn trong khe dọc giữa hai vợ chồng / dải ngang giữa hai đời. Một đoạn chéo là dấu
   * hiệu tầng vẽ đã tự bịa hình học và có thể cắt ngang một tấm thẻ.
   */
  it("chỉ sinh ra đoạn nằm ngang hoặc thẳng đứng, không đoạn chéo nào", () => {
    const paths = junctionPaths(COUPLE_WITH_THREE_CHILDREN.junction);
    for (const d of [paths.bloodline, paths.adopted, paths.marriage]) {
      for (const seg of d.split("M ").filter(Boolean)) {
        const [x1, y1, , x2, y2] = seg.trim().split(/\s+/).map(Number);
        expect(x1 === x2 || y1 === y2, `đoạn chéo trong "${seg}"`).toBe(true);
      }
    }
  });

  it("vẽ được gia đình một con: không có thanh anh em, chỉ có đoạn rơi thẳng", () => {
    const paths = junctionPaths(
      junction({
        unitId: "u-solo",
        stem: { x: 100, yFrom: 48, yTo: 160 },
        childDrops: [{ childId: "c", x: 100, yFrom: 160, yTo: 224, dashed: false }],
      })
    );
    expect(paths.bloodline).toBe("M 100 48 L 100 160 M 100 160 L 100 224");
    expect(paths.marriage).toBe("");
    expect(paths.adopted).toBe("");
  });

  it("vẽ được cặp vợ chồng chưa có con: chỉ còn thanh hôn phối", () => {
    const paths = junctionPaths(
      junction({ unitId: "u-cap", marriageBar: { x1: 208, x2: 256, y: 48 } })
    );
    expect(paths.marriage).toBe("M 208 48 L 256 48");
    expect(paths.bloodline).toBe("");
  });
});

describe("formatCoord / polylinePath", () => {
  it("làm tròn toạ độ về hai chữ số thập phân để chuỗi d không phình ra", () => {
    expect(formatCoord(232.333333)).toBe("232.33");
    expect(formatCoord(48)).toBe("48");
    expect(formatCoord(-0.001)).toBe("0");
  });

  it("nối các điểm gãy đã tính sẵn thành một đường", () => {
    expect(
      polylinePath([
        { x: 0, y: 0 },
        { x: 0, y: 40 },
        { x: 120, y: 40 },
      ])
    ).toBe("M 0 0 L 0 40 L 120 40");
  });

  it("không vẽ gì khi chưa đủ hai điểm", () => {
    expect(polylinePath([{ x: 1, y: 2 }])).toBe("");
    expect(polylinePath([])).toBe("");
  });
});

describe("toJunctionEdges", () => {
  const { unit: u, junction: j } = COUPLE_WITH_THREE_CHILDREN;

  it("dựng đúng một cạnh cho mỗi đơn vị gia đình và mang theo hình học đã tính sẵn", () => {
    const [e] = toJunctionEdges([j], [u]);
    expect(e).toMatchObject({ id: "fj-u-A-B", type: "family-junction" });
    expect(e!.data!.junction).toBe(j);
  });

  /**
   * Hai đầu neo không quyết định nét vẽ — hình học đã tính sẵn — nhưng React Flow BỎ HẲN cạnh nào
   * có nút nguồn/đích không tồn tại, và `onlyRenderVisibleElements` cắt cạnh theo hình chữ nhật
   * bao hai nút đó. Neo vào người trục và người con cuối để hình bao trùm cả gia đình.
   */
  it("neo cạnh vào người trục và người con cuối để không bị cắt nhầm khi phóng to", () => {
    const [e] = toJunctionEdges([j], [u]);
    expect(e).toMatchObject({ source: "A", target: "c3" });
  });

  it("neo vào bạn đời khi cặp vợ chồng chưa có con", () => {
    const childless = unit({ id: "u-cap", partnerIds: ["A", "B"], anchorId: "A" });
    const [e] = toJunctionEdges(
      [junction({ unitId: "u-cap", marriageBar: { x1: 208, x2: 256, y: 48 } })],
      [childless]
    );
    expect(e).toMatchObject({ source: "A", target: "B" });
  });

  it("bỏ qua đơn vị không có đoạn nào để vẽ (cha/mẹ đơn thân chưa ghi nhận con)", () => {
    const solo = unit({ id: "u-solo", partnerIds: ["A"], anchorId: "A" });
    expect(toJunctionEdges([junction({ unitId: "u-solo" })], [solo])).toEqual([]);
  });

  it("bỏ qua điểm nối không tìm thấy đơn vị gia đình tương ứng", () => {
    expect(toJunctionEdges([j], [])).toEqual([]);
  });

  it("sinh id ổn định từ id đơn vị, không dùng số đếm", () => {
    const first = toJunctionEdges([j], [u]);
    const second = toJunctionEdges([j], [u]);
    expect(first[0]!.id).toBe(second[0]!.id);
  });
});

describe("toAuxiliaryEdges", () => {
  const remarriage: AuxiliaryLink = {
    id: "L1",
    sourceId: "A",
    targetId: "B2",
    kind: "REMARRIAGE",
    waypoints: [
      { x: 100, y: 48 },
      { x: 100, y: 120 },
      { x: 700, y: 120 },
      { x: 700, y: 48 },
    ],
  };

  it("dựng cạnh cho mọi đường vẽ vòng và giữ nguyên các điểm gãy đã tính sẵn", () => {
    const [e] = toAuxiliaryEdges([remarriage]);
    expect(e).toMatchObject({ id: "aux-L1", source: "A", target: "B2", type: "family-auxiliary" });
    expect(e!.data!.link.waypoints).toBe(remarriage.waypoints);
  });

  it("bỏ qua đường chưa đủ hai điểm gãy — không có gì để vẽ", () => {
    expect(toAuxiliaryEdges([{ ...remarriage, waypoints: [{ x: 1, y: 2 }] }])).toEqual([]);
  });

  it("phân biệt được ba loại quan hệ đặc thù bằng nét", () => {
    expect(auxiliaryStroke("HEIR")).toBe(TREE_STROKES.heir);
    expect(auxiliaryStroke("REMARRIAGE")).toBe(TREE_STROKES.detourMarriage);
    expect(auxiliaryStroke("CROSS_BRANCH_PARENT")).toBe(TREE_STROKES.detour);
  });
});

describe("lineageEndPersonIds (tuyệt tự)", () => {
  it("chỉ nhận người đã được backend gắn nhãn tuyệt tự", () => {
    const nodes = [
      node("a", 0, { badges: ["TUYET_TU"] }),
      node("b", 0, { badges: ["DICH_TON"] }),
      node("c", 0),
    ];
    expect(lineageEndPersonIds(nodes)).toEqual(["a"]);
  });

  /**
   * `childCount === 0` KHÔNG có nghĩa là tuyệt tự: nhánh chưa tải hết, hoặc con cái bị lọc mất vì
   * phân tầng riêng tư, cũng cho đúng con số đó. Khép một gạch tuyệt tự dưới thẻ họ là nói sai về
   * phả — và ở chiều ngược lại là suy đoán ra dữ liệu backend đã cố tình không trả về.
   */
  it("không tự suy ra tuyệt tự từ việc chưa thấy người con nào", () => {
    const nodes = [node("chua-tai", 0, { childCount: 0, hasMoreDescendants: true })];
    expect(lineageEndPersonIds(nodes)).toEqual([]);
  });
});

describe("lineageEndMark", () => {
  it("khép một gạch NGẮN, căn giữa, ngay dưới đáy thẻ", () => {
    const bar = lineageEndMark({ x: 100, y: 200 });
    const center = 100 + NODE_WIDTH / 2;
    expect((bar.x1 + bar.x2) / 2).toBe(center);
    expect(bar.x2 - bar.x1).toBeLessThan(NODE_WIDTH / 2);
    expect(bar.y).toBeGreaterThan(200 + NODE_HEIGHT);
  });
});

/* ==========================================================================
   BUG WATCH — lớp cạnh nuốt thao tác chạm vào thẻ nhân khẩu
   ========================================================================== */

/**
 * Bản trước phải dìm cạnh xuống `zIndex: -1` vì lớp cạnh ăn mất cú chạm. Nguyên nhân thật nằm ở
 * hai chỗ khác, và cả hai đều được khoá lại ở đây:
 *
 *  1. `interactionWidth` mặc định 20 ⇒ React Flow vẽ kèm một `<path>` vô hình rộng 20px cho MỖI
 *     cạnh, và `.react-flow__edge { pointer-events: visibleStroke }` khiến nét trong suốt đó vẫn
 *     bắt sự kiện. Đặt 0 thì đường đó không được dựng ra.
 *  2. Cạnh chọn được ⇒ cả nhóm `<g>` bắt sự kiện. `selectable: false` (và canvas không truyền
 *     `onEdgeClick`) khiến React Flow gắn lớp `inactive`, mà biểu định kiểu của thư viện quy định
 *     `.react-flow__edge.inactive { pointer-events: none }`.
 *
 * Đủ hai điều đó thì `zIndex: 0` — lớp bình thường — không còn nguy hiểm.
 */
describe("mọi cạnh phải trơ với thao tác chạm", () => {
  const junctionEdges = toJunctionEdges(
    [COUPLE_WITH_THREE_CHILDREN.junction],
    [COUPLE_WITH_THREE_CHILDREN.unit]
  );
  const auxEdges = toAuxiliaryEdges([
    {
      id: "L1",
      sourceId: "A",
      targetId: "B",
      kind: "HEIR",
      waypoints: [
        { x: 0, y: 0 },
        { x: 0, y: 40 },
      ],
    },
  ]);
  const personEdges = toFlowEdges([
    edge("d1", "cha", "con", "PARENT_BIO"),
    edge("s1", "chong", "vo", "SPOUSE"),
  ]);
  const all = [...junctionEdges, ...auxEdges, ...personEdges];

  it("không dựng vệt bắt sự kiện vô hình quanh bất kỳ cạnh nào", () => {
    for (const e of all) {
      expect(e.interactionWidth, `cạnh ${e.id} vẫn còn vệt bắt sự kiện`).toBe(0);
    }
  });

  it("không cho cạnh nào chọn được hoặc nhận tiêu điểm bàn phím", () => {
    for (const e of all) {
      expect(e.selectable, `cạnh ${e.id} còn chọn được`).toBe(false);
      expect(e.focusable, `cạnh ${e.id} còn nhận tiêu điểm`).toBe(false);
    }
  });

  it("giữ mọi cạnh ở lớp bình thường, không đẩy lên trên lớp thẻ nhân khẩu", () => {
    for (const e of all) {
      expect(e.zIndex ?? 0, `cạnh ${e.id} nằm trên thẻ nhân khẩu`).toBeLessThanOrEqual(0);
    }
  });

  it("không còn dùng mẹo dìm cạnh xuống dưới lớp thẻ nữa", () => {
    // zIndex âm từng là cách chữa duy nhất; nay đã có cách chữa đúng nguyên nhân.
    for (const e of all) {
      expect(e.zIndex).toBe(0);
    }
  });

  /**
   * Lớp phòng vệ thứ ba, kiểm ở đúng cái DOM sẽ dựng ra: từng nét vẽ đều `pointer-events: none`,
   * nên kể cả khi ai đó lỡ bật lại `selectable` thì nét vẫn không đớp được cú chạm nào.
   */
  it("dựng ra nét vẽ không nhận sự kiện chuột/chạm", () => {
    const junctionMarkup = renderToStaticMarkup(
      createElement(
        FamilyJunctionEdge,
        { data: { junction: COUPLE_WITH_THREE_CHILDREN.junction } } as unknown as EdgeProps<FamilyJunctionFlowEdge>
      )
    );
    const auxMarkup = renderToStaticMarkup(
      createElement(
        AuxiliaryLinkEdge,
        {
          data: {
            link: {
              id: "L1",
              sourceId: "A",
              targetId: "B",
              kind: "HEIR",
              waypoints: [
                { x: 0, y: 0 },
                { x: 0, y: 40 },
              ],
            },
          },
        } as unknown as EdgeProps<AuxiliaryLinkFlowEdge>
      )
    );

    for (const markup of [junctionMarkup, auxMarkup]) {
      const paths = markup.match(/<path/g) ?? [];
      expect(paths.length).toBeGreaterThan(0);
      expect(markup.match(/pointer-events:none/g)?.length ?? 0).toBeGreaterThanOrEqual(paths.length);
    }
  });
});
