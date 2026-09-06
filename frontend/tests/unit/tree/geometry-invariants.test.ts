import { describe, expect, it } from "vitest";
import type {
  AuxiliaryLink,
  ChildDrop,
  FamilyJunction,
  FamilyLayout,
  FamilyUnit,
} from "@/lib/tree/family-layout-types";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { buildFamilyUnits } from "@/lib/tree/family-units";
import { layoutFamily } from "@/lib/tree/layout-family";
import type { NodePosition } from "@/lib/tree/layout-hierarchical";
import {
  COUPLE_GAP,
  FAMILY_RANK_SEP,
  HIERARCHICAL_NODE_SEP,
  NODE_HEIGHT,
  NODE_WIDTH,
} from "@/lib/tree/layout-constants";
import type { TreeEdge, TreeNode } from "@/types/api";
import { edge, node } from "../../setup/tree-fixtures";
import {
  allCardRects,
  allSegments,
  cardRect,
  findAdoptionDrawingErrors,
  findDiagonalSegments,
  findDropsOutsideCoupleCorridor,
  findDroppedRelationships,
  findEndedMarriageDrawingErrors,
  findOverlappingCards,
  findSegmentsCrossingCards,
  isAxisAligned,
  orientationOf,
  pointInRect,
  rectsOverlap,
  runGeometryInvariants,
  segmentIntersectsRect,
  segmentRectPenetration,
  type LayoutUnderTest,
} from "../../helpers/geometry";

/**
 * Bất biến hình học của phả đồ dựng theo ĐƠN VỊ GIA ĐÌNH.
 *
 * <p>Người dùng phàn nàn: "các đường nối liên kết đang bị che bởi các ô tên, nhìn không được tường
 * minh". Tệp này là bản dịch câu đó sang thứ máy kiểm được, và mục tiêu là câu đó KHÔNG BAO GIỜ
 * đúng trở lại.</p>
 *
 * <h2>Vì sao có cả bố cục dựng tay</h2>
 *
 * <p>Bộ test này được viết song song với {@code family-units.ts} / {@code layout-family.ts}, khi
 * chúng chưa tồn tại. Nên nó làm hai việc tách bạch:</p>
 *
 * <ol>
 *   <li><b>Bố cục dựng tay</b> — mỗi kịch bản gia phả được xếp chỗ bằng toạ độ tính tay theo đúng
 *       quy tắc trong javadoc của {@code family-layout-types.ts}. Đây là "bản mẫu vàng": nó chứng
 *       minh bộ bất biến là thoả mãn được, và nó nói rõ một phả đồ ĐÚNG thì hình học trông ra sao.</li>
 *   <li><b>Kiểm ngược</b> — dựng lại đúng lỗi cũ (đường vợ chồng người–người dài 464px, đường
 *       huyết thống chéo) rồi khẳng định bộ kiểm BẮT được. Không có phần này thì một hàm kiểm hỏng
 *       sẽ khiến mọi bất biến xanh một cách vô nghĩa.</li>
 * </ol>
 *
 * <p>Cuối tệp, CHÍNH bộ bất biến ấy được chĩa vào cài đặt thật ({@code buildFamilyUnits} +
 * {@code layoutFamily}) trên cả tám kịch bản, và thêm một lát cắt vài trăm nhân khẩu lấy từ đồ thị
 * giả lập — tức đúng dữ liệu mà màn hình phả đồ đang chạy trên đó.</p>
 */

const W = NODE_WIDTH; // 208
const H = NODE_HEIGHT; // 96
/** Khoảng cách giữa hai đời, tính từ đỉnh thẻ đời trên tới đỉnh thẻ đời dưới. */
const ROW_PITCH = H + FAMILY_RANK_SEP; // 224
/** Bước ngang giữa hai anh em ruột kề nhau. */
const SIBLING_PITCH = W + HIERARCHICAL_NODE_SEP; // 240

const rowY = (generation: number): number => generation * ROW_PITCH;
const centerOf = (x: number): number => x + W / 2;

/* ------------------------------------------------------------------ *
 * Dựng bố cục mẫu bằng tay
 * ------------------------------------------------------------------ */

interface ChildPlan {
  readonly id: string;
  /** x góc trái của thẻ người con — CỐ Ý ghi tay, để mỗi kịch bản là một bản mẫu đọc được. */
  readonly x: number;
  readonly adopted?: boolean;
}

interface FamilyPlan {
  readonly id: string;
  /** Bạn đời theo thứ tự TRÁI→PHẢI trên màn hình; 1 người = cha/mẹ đơn thân. */
  readonly partners: readonly string[];
  readonly anchorId?: string;
  readonly spouseOrder?: number | null;
  readonly generation: number;
  /** x góc trái của thẻ người đứng ngoài cùng bên trái. */
  readonly x: number;
  readonly children?: readonly ChildPlan[];
  readonly ended?: boolean;
  /** Độ lệch tầng của thanh anh em — để phân biệt con bà nào khi đa thê. */
  readonly barStagger?: number;
}

interface PlannedFamily {
  readonly positions: readonly (readonly [string, NodePosition])[];
  readonly unit: FamilyUnit;
  readonly junction: FamilyJunction;
}

/**
 * Xếp chỗ một đơn vị gia đình theo đúng quy tắc đã chốt:
 * thanh hôn phối nằm gọn trong khe {@link COUPLE_GAP} giữa hai thẻ · điểm nối ở giữa thanh ấy ·
 * đoạn dọc rơi xuống thanh anh em đặt giữa hai đời · đoạn rơi cuối cùng kết thúc ĐÚNG mép trên
 * thẻ con.
 */
function planFamily(plan: FamilyPlan): PlannedFamily {
  const y = rowY(plan.generation);
  const children = plan.children ?? [];
  const childTop = rowY(plan.generation + 1);

  const positions: (readonly [string, NodePosition])[] = plan.partners.map((id, index) => [
    id,
    { x: plan.x + index * (W + COUPLE_GAP), y },
  ]);
  for (const child of children) positions.push([child.id, { x: child.x, y: childTop }]);

  let junctionX: number;
  let junctionY: number;
  let marriageBar: FamilyJunction["marriageBar"] = null;
  if (plan.partners.length === 2) {
    const gapLeft = plan.x + W;
    const gapRight = plan.x + W + COUPLE_GAP;
    marriageBar = { x1: gapLeft, x2: gapRight, y: y + H / 2 };
    junctionX = (gapLeft + gapRight) / 2;
    junctionY = y + H / 2;
  } else {
    // Cha/mẹ đơn thân: điểm nối ngay DƯỚI thẻ, không có thanh hôn phối.
    junctionX = plan.x + W / 2;
    junctionY = y + H;
  }

  const barY = y + H + FAMILY_RANK_SEP / 2 + (plan.barStagger ?? 0);
  const childCenters = children.map((c) => centerOf(c.x));
  const onlyChildIsUnderJunction =
    children.length === 1 && Math.abs((childCenters[0] ?? 0) - junctionX) < 0.001;

  let siblingBar: FamilyJunction["siblingBar"] = null;
  let stem: FamilyJunction["stem"] = null;
  let childDrops: ChildDrop[] = [];

  if (children.length === 0) {
    // Tuyệt tự / chưa có con: chỉ còn thanh hôn phối.
  } else if (onlyChildIsUnderJunction) {
    // Con một: không cần thanh anh em, rơi thẳng một mạch từ điểm nối xuống đỉnh thẻ con.
    const only = children[0]!;
    childDrops = [
      { childId: only.id, x: junctionX, yFrom: junctionY, yTo: childTop, dashed: Boolean(only.adopted) },
    ];
  } else {
    const spanLeft = Math.min(junctionX, ...childCenters);
    const spanRight = Math.max(junctionX, ...childCenters);
    siblingBar = { x1: spanLeft, x2: spanRight, y: barY };
    stem = { x: junctionX, yFrom: junctionY, yTo: barY };
    childDrops = children.map((child) => ({
      childId: child.id,
      x: centerOf(child.x),
      yFrom: barY,
      yTo: childTop,
      dashed: Boolean(child.adopted),
    }));
  }

  const unit: FamilyUnit = {
    id: plan.id,
    partnerIds: plan.partners,
    anchorId: plan.anchorId ?? plan.partners[0]!,
    spouseOrder: plan.spouseOrder ?? null,
    childIds: children.map((c) => c.id),
    adoptedChildIds: new Set(children.filter((c) => c.adopted).map((c) => c.id)),
    ended: Boolean(plan.ended),
  };

  const junction: FamilyJunction = {
    unitId: plan.id,
    x: junctionX,
    y: junctionY,
    marriageBar,
    siblingBar,
    stem,
    childDrops,
    ended: Boolean(plan.ended),
  };

  return { positions, unit, junction };
}

interface AssembleExtras {
  readonly positions?: readonly (readonly [string, NodePosition])[];
  readonly units?: readonly FamilyUnit[];
  readonly auxiliaryLinks?: readonly AuxiliaryLink[];
}

function assemble(
  plans: readonly FamilyPlan[],
  extras: AssembleExtras = {}
): { layout: FamilyLayout; units: FamilyUnit[] } {
  const planned = plans.map(planFamily);
  const positions = new Map<string, NodePosition>();
  for (const family of planned) {
    for (const [id, position] of family.positions) positions.set(id, position);
  }
  for (const [id, position] of extras.positions ?? []) positions.set(id, position);

  return {
    layout: {
      positions,
      junctions: planned.map((f) => f.junction),
      auxiliaryLinks: extras.auxiliaryLinks ?? [],
    },
    units: [...planned.map((f) => f.unit), ...(extras.units ?? [])],
  };
}

/** Cặp cạnh PARENT_BIO từ cả cha lẫn mẹ — gia phả này ghi mẹ ngang hàng với cha (BA v2 §12). */
function parentEdges(parents: readonly string[], childId: string, relType: TreeEdge["relType"] = "PARENT_BIO"): TreeEdge[] {
  return parents.map((parentId) => edge(`e-${parentId}-${childId}`, parentId, childId, relType));
}

function spouseEdge(a: string, b: string, overrides: Partial<TreeEdge> = {}): TreeEdge {
  return edge(`e-${a}-${b}`, a, b, "SPOUSE", overrides);
}

/* ------------------------------------------------------------------ *
 * Các kịch bản gia phả
 * ------------------------------------------------------------------ */

interface Scenario extends LayoutUnderTest {
  readonly name: string;
}

/** 1 · Cặp vợ chồng có ba người con — hình dạng thường gặp nhất trong phả đồ. */
function coupleWithThreeChildren(): Scenario {
  const junctionX = centerOf(0) + (W + COUPLE_GAP) / 2; // 232
  const { layout, units } = assemble([
    {
      id: "u-vc",
      partners: ["ong", "ba"],
      anchorId: "ong",
      spouseOrder: 1,
      generation: 0,
      x: 0,
      children: [
        { id: "con-1", x: junctionX - W / 2 - SIBLING_PITCH },
        { id: "con-2", x: junctionX - W / 2 },
        { id: "con-3", x: junctionX - W / 2 + SIBLING_PITCH },
      ],
    },
  ]);
  return {
    name: "cặp vợ chồng ba con",
    layout,
    units,
    edges: [
      spouseEdge("ong", "ba", { spouseOrder: 1 }),
      ...parentEdges(["ong", "ba"], "con-1"),
      ...parentEdges(["ong", "ba"], "con-2"),
      ...parentEdges(["ong", "ba"], "con-3"),
    ],
  };
}

/**
 * 2 · Đa thê ba vợ. Ông trục đứng GIỮA, bà cả sang phải, bà hai sang trái.
 *
 * <p>Chỉ hai bà đứng kề ông được — bà ba buộc phải đi bằng ĐƯỜNG PHỤ chạy trong hành lang phía
 * trên hàng, chứ không được biến mất. Đây chính là ca mà một thư viện cây thuần sẽ đánh rơi.</p>
 */
function polygamyThreeWives(): Scenario {
  const xBaHai = 0;
  const xOng = xBaHai + W + COUPLE_GAP; // 256
  const xBaCa = xOng + W + COUPLE_GAP; // 512
  const xBaBa = xBaCa + W + COUPLE_GAP; // 768
  const junctionBaHai = xBaHai + W + COUPLE_GAP / 2; // 232
  const junctionBaCa = xOng + W + COUPLE_GAP / 2; // 488

  const { layout, units } = assemble(
    [
      {
        id: "u-ba-hai",
        partners: ["ba-hai", "ong"],
        anchorId: "ong",
        spouseOrder: 2,
        generation: 0,
        x: xBaHai,
        children: [{ id: "con-ba-hai", x: junctionBaHai - W / 2 }],
      },
      {
        id: "u-ba-ca",
        partners: ["ong", "ba-ca"],
        anchorId: "ong",
        spouseOrder: 1,
        generation: 0,
        x: xOng,
        children: [
          { id: "con-ca-1", x: junctionBaCa - W / 2 + SIBLING_PITCH / 2 },
          { id: "con-ca-2", x: junctionBaCa - W / 2 + SIBLING_PITCH / 2 + SIBLING_PITCH },
        ],
      },
    ],
    {
      positions: [["ba-ba", { x: xBaBa, y: 0 }]],
      units: [
        {
          id: "u-ba-ba",
          partnerIds: ["ong", "ba-ba"],
          anchorId: "ong",
          spouseOrder: 3,
          childIds: [],
          adoptedChildIds: new Set<string>(),
          ended: false,
        },
      ],
      auxiliaryLinks: [
        {
          id: "aux-ba-ba",
          sourceId: "ong",
          targetId: "ba-ba",
          kind: "REMARRIAGE",
          // Hành lang phía TRÊN hàng: lên khỏi đỉnh thẻ ông, chạy ngang, rồi xuống đỉnh thẻ bà ba.
          waypoints: [
            { x: centerOf(xOng), y: 0 },
            { x: centerOf(xOng), y: -40 },
            { x: centerOf(xBaBa), y: -40 },
            { x: centerOf(xBaBa), y: 0 },
          ],
        },
      ],
    }
  );

  return {
    name: "đa thê ba vợ",
    layout,
    units,
    edges: [
      spouseEdge("ong", "ba-ca", { spouseOrder: 1 }),
      spouseEdge("ba-hai", "ong", { spouseOrder: 2 }),
      spouseEdge("ong", "ba-ba", { spouseOrder: 3 }),
      ...parentEdges(["ba-hai", "ong"], "con-ba-hai"),
      ...parentEdges(["ong", "ba-ca"], "con-ca-1"),
      ...parentEdges(["ong", "ba-ca"], "con-ca-2"),
    ],
  };
}

/** 3 · Cha đơn thân — gia phả cổ rất hay ghi người con mà không rõ mẹ. */
function singleFather(): Scenario {
  const junctionX = centerOf(0); // 104
  const { layout, units } = assemble([
    {
      id: "u-don-than",
      partners: ["cha"],
      generation: 0,
      x: 0,
      children: [
        { id: "con-a", x: junctionX - W / 2 - SIBLING_PITCH / 2 },
        { id: "con-b", x: junctionX - W / 2 + SIBLING_PITCH / 2 },
      ],
    },
  ]);
  return {
    name: "cha đơn thân",
    layout,
    units,
    edges: [...parentEdges(["cha"], "con-a"), ...parentEdges(["cha"], "con-b")],
  };
}

/** 4 · Con nuôi — nét đứt CHỈ ở đoạn rơi xuống người con ấy, không đứt cả thanh anh em. */
function adoptedChild(): Scenario {
  const junctionX = 232;
  const { layout, units } = assemble([
    {
      id: "u-con-nuoi",
      partners: ["cha", "me"],
      generation: 0,
      x: 0,
      children: [
        { id: "con-ruot", x: junctionX - W / 2 - SIBLING_PITCH / 2 },
        { id: "con-nuoi", x: junctionX - W / 2 + SIBLING_PITCH / 2, adopted: true },
      ],
    },
  ]);
  return {
    name: "con nuôi",
    layout,
    units,
    edges: [
      spouseEdge("cha", "me"),
      ...parentEdges(["cha", "me"], "con-ruot"),
      ...parentEdges(["cha", "me"], "con-nuoi", "PARENT_ADOPT"),
    ],
  };
}

/** 5 · Hôn phối đã ly hôn — vẫn ở trên cây, thanh hôn phối nét đứt. */
function divorcedCouple(): Scenario {
  const junctionX = 232;
  const { layout, units } = assemble([
    {
      id: "u-ly-hon",
      partners: ["chong", "vo"],
      generation: 0,
      x: 0,
      ended: true,
      children: [{ id: "con-chung", x: junctionX - W / 2 }],
    },
  ]);
  return {
    name: "hôn phối đã ly hôn",
    layout,
    units,
    edges: [
      spouseEdge("chong", "vo", { validTo: "1998-04-02" }),
      ...parentEdges(["chong", "vo"], "con-chung"),
    ],
  };
}

/**
 * 6 · TÁI HÔN — ca then chốt.
 *
 * <p>Người ở giữa thuộc BA đơn vị cùng lúc: cuộc hôn phối đã kết thúc bên trái, cuộc hôn phối hiện
 * tại bên phải, và một người bạn đời ở chi khác không đứng kề được nên đi bằng đường phụ. Cả ba
 * phải còn nguyên trên cây — mất một cái là vài tuần nữa có người mở phả ra và thấy bà hai của cụ
 * tổ biến mất.</p>
 */
function remarriage(): Scenario {
  const xVoCu = 0;
  const xOng = xVoCu + W + COUPLE_GAP; // 256
  // Vợ sau đứng ngay bên phải ông (x = 512) — do planFamily tự xếp từ x của đơn vị.
  const xVoXa = 900;
  const junctionCu = xVoCu + W + COUPLE_GAP / 2; // 232
  const junctionSau = xOng + W + COUPLE_GAP / 2; // 488

  const { layout, units } = assemble(
    [
      {
        id: "u-hon-truoc",
        partners: ["vo-cu", "ong"],
        anchorId: "ong",
        spouseOrder: 1,
        generation: 0,
        x: xVoCu,
        ended: true,
        children: [{ id: "con-doi-truoc", x: junctionCu - W / 2 }],
      },
      {
        id: "u-hon-sau",
        partners: ["ong", "vo-sau"],
        anchorId: "ong",
        spouseOrder: 2,
        generation: 0,
        x: xOng,
        children: [
          { id: "con-doi-sau-1", x: junctionSau - W / 2 },
          { id: "con-doi-sau-2", x: junctionSau - W / 2 + SIBLING_PITCH },
        ],
      },
    ],
    {
      positions: [["vo-xa", { x: xVoXa, y: 0 }]],
      units: [
        {
          id: "u-hon-chi-khac",
          partnerIds: ["ong", "vo-xa"],
          anchorId: "ong",
          spouseOrder: 3,
          childIds: [],
          adoptedChildIds: new Set<string>(),
          ended: false,
        },
      ],
      auxiliaryLinks: [
        {
          id: "aux-vo-xa",
          sourceId: "ong",
          targetId: "vo-xa",
          kind: "REMARRIAGE",
          waypoints: [
            { x: centerOf(xOng), y: 0 },
            { x: centerOf(xOng), y: -48 },
            { x: centerOf(xVoXa), y: -48 },
            { x: centerOf(xVoXa), y: 0 },
          ],
        },
      ],
    }
  );

  return {
    name: "tái hôn (người thuộc nhiều đơn vị)",
    layout,
    units,
    edges: [
      spouseEdge("vo-cu", "ong", { spouseOrder: 1, validTo: "1990-01-01" }),
      spouseEdge("ong", "vo-sau", { spouseOrder: 2 }),
      spouseEdge("ong", "vo-xa", { spouseOrder: 3 }),
      ...parentEdges(["vo-cu", "ong"], "con-doi-truoc"),
      ...parentEdges(["ong", "vo-sau"], "con-doi-sau-1"),
      ...parentEdges(["ong", "vo-sau"], "con-doi-sau-2"),
    ],
  };
}

/** 7 · Con một — không có thanh anh em, rơi thẳng một mạch. */
function onlyChild(): Scenario {
  const junctionX = 232;
  const { layout, units } = assemble([
    {
      id: "u-con-mot",
      partners: ["cha", "me"],
      generation: 0,
      x: 0,
      children: [{ id: "con-duy-nhat", x: junctionX - W / 2 }],
    },
  ]);
  return {
    name: "con một (không có thanh anh em)",
    layout,
    units,
    edges: [spouseEdge("cha", "me"), ...parentEdges(["cha", "me"], "con-duy-nhat")],
  };
}

/** 8 · Cây bốn đời — đời nào cũng phải giữ nguyên bất biến, không chỉ đời đầu. */
function fourGenerations(): Scenario {
  const junction0 = 232;
  const xConTruong = junction0 - W / 2 - SIBLING_PITCH / 2; // 8
  const xConThu = junction0 - W / 2 + SIBLING_PITCH / 2; // 248
  const junction1 = xConThu + W + COUPLE_GAP / 2; // 480
  const xChau = junction1 - W / 2; // 376
  const junction2 = xChau + W + COUPLE_GAP / 2; // 608
  const xChat = junction2 - W / 2; // 504

  const { layout, units } = assemble([
    {
      id: "u-doi-1",
      partners: ["cu-ong", "cu-ba"],
      generation: 0,
      x: 0,
      children: [
        { id: "con-truong", x: xConTruong },
        { id: "con-thu", x: xConThu },
      ],
    },
    {
      id: "u-doi-2",
      partners: ["con-thu", "dau-doi-2"],
      generation: 1,
      x: xConThu,
      children: [{ id: "chau", x: xChau }],
    },
    {
      id: "u-doi-3",
      partners: ["chau", "dau-doi-3"],
      generation: 2,
      x: xChau,
      children: [{ id: "chat", x: xChat }],
    },
  ]);

  return {
    name: "cây bốn đời",
    layout,
    units,
    edges: [
      spouseEdge("cu-ong", "cu-ba"),
      ...parentEdges(["cu-ong", "cu-ba"], "con-truong"),
      ...parentEdges(["cu-ong", "cu-ba"], "con-thu"),
      spouseEdge("con-thu", "dau-doi-2"),
      ...parentEdges(["con-thu", "dau-doi-2"], "chau"),
      spouseEdge("chau", "dau-doi-3"),
      ...parentEdges(["chau", "dau-doi-3"], "chat"),
    ],
  };
}

const scenarios: readonly Scenario[] = [
  coupleWithThreeChildren(),
  polygamyThreeWives(),
  singleFather(),
  adoptedChild(),
  divorcedCouple(),
  remarriage(),
  onlyChild(),
  fourGenerations(),
];

/* ------------------------------------------------------------------ *
 * Bộ dụng cụ hình học tự kiểm
 * ------------------------------------------------------------------ */

describe("bộ dụng cụ hình học", () => {
  const card = cardRect({ x: 0, y: 0 }); // [0,0 208×96]

  it("không coi đoạn rơi kết thúc đúng mép trên thẻ là cắt qua thẻ", () => {
    const drop = { x1: 104, y1: -60, x2: 104, y2: 0 };
    expect(segmentIntersectsRect(drop, card)).toBe(false);
    expect(segmentRectPenetration(drop, card)).toBe(0);
  });

  it("không coi đoạn rơi bắt đầu đúng mép dưới thẻ là cắt qua thẻ", () => {
    expect(segmentIntersectsRect({ x1: 104, y1: H, x2: 104, y2: H + 64 }, card)).toBe(false);
  });

  it("coi đoạn đi xuyên giữa thẻ là cắt qua thẻ, và đo được nó chui vào bao nhiêu", () => {
    const across = { x1: -50, y1: 48, x2: 260, y2: 48 };
    expect(segmentIntersectsRect(across, card)).toBe(true);
    expect(segmentRectPenetration(across, card)).toBeCloseTo(W - 2, 5);
  });

  it("không coi đoạn nằm sát mép thẻ là cắt qua thẻ (nét vẽ có bề dày)", () => {
    expect(segmentIntersectsRect({ x1: -50, y1: 0, x2: 260, y2: 0 }, card)).toBe(false);
    expect(segmentIntersectsRect({ x1: 0, y1: -50, x2: 0, y2: 150 }, card)).toBe(false);
  });

  it("vẫn bắt được đoạn CHÉO cắt qua thẻ — bất biến không phụ thuộc vào việc đã sửa xong đường chéo hay chưa", () => {
    expect(segmentIntersectsRect({ x1: -20, y1: -20, x2: 120, y2: 80 }, card)).toBe(true);
  });

  it("phân biệt đoạn ngang, đoạn dọc và đường chéo", () => {
    expect(orientationOf({ x1: 0, y1: 10, x2: 100, y2: 10 })).toBe("HORIZONTAL");
    expect(orientationOf({ x1: 5, y1: 0, x2: 5, y2: 100 })).toBe("VERTICAL");
    expect(orientationOf({ x1: 0, y1: 0, x2: 100, y2: 100 })).toBe("DIAGONAL");
    expect(isAxisAligned({ x1: 0, y1: 0, x2: 100, y2: 100 })).toBe(false);
  });

  it("coi hai thẻ cách nhau đúng khe vợ chồng là KHÔNG chồng nhau", () => {
    expect(rectsOverlap(card, cardRect({ x: W + COUPLE_GAP, y: 0 }))).toBe(false);
    expect(rectsOverlap(card, cardRect({ x: W, y: 0 }))).toBe(false); // chạm mép
    expect(rectsOverlap(card, cardRect({ x: W - 1, y: 0 }))).toBe(true);
  });

  it("coi điểm nằm trên mép thẻ là ngoài thẻ, điểm giữa thẻ là trong thẻ", () => {
    expect(pointInRect({ x: 104, y: 0 }, card)).toBe(false);
    expect(pointInRect({ x: 104, y: 48 }, card)).toBe(true);
  });
});

/* ------------------------------------------------------------------ *
 * Kiểm ngược: bộ bất biến phải BẮT được đúng lỗi cũ
 * ------------------------------------------------------------------ */

describe("bộ bất biến bắt được đúng lỗi người dùng phàn nàn", () => {
  /**
   * Dựng lại nguyên văn lỗi cũ: đường vợ chồng đi từ neo TRÁI người này sang neo PHẢI người kia,
   * dài 464px mà 416px nằm khuất dưới hai tấm thẻ. Người dùng chỉ thấy một mẩu 48px ở khe giữa.
   */
  it("bắt được đường vợ chồng người–người kiểu cũ chui dưới cả hai tấm thẻ", () => {
    const layout: FamilyLayout = {
      positions: new Map([
        ["ong", { x: 0, y: 0 }],
        ["ba", { x: W + COUPLE_GAP, y: 0 }],
      ]),
      junctions: [
        {
          unitId: "u-cu",
          x: 232,
          y: 48,
          // Neo trái thẻ ông (x=0) → neo phải thẻ bà (x=464): đúng đường 464px của bản cũ.
          marriageBar: { x1: 0, x2: W + COUPLE_GAP + W, y: H / 2 },
          siblingBar: null,
          stem: null,
          childDrops: [],
          ended: false,
        },
      ],
      auxiliaryLinks: [],
    };

    const violations = findSegmentsCrossingCards(layout);
    expect(violations).toHaveLength(2);
    expect(violations.join(" | ")).toContain("thanh hôn phối");
    expect(violations.join(" | ")).toContain("ong");
    expect(violations.join(" | ")).toContain("ba");
  });

  it("bắt được đường huyết thống CHÉO kiểu cũ (mỗi cha mẹ tự kẻ một đường tới từng con)", () => {
    const layout: FamilyLayout = {
      positions: new Map([
        ["cha", { x: 0, y: 0 }],
        ["con", { x: -240, y: ROW_PITCH }],
      ]),
      junctions: [
        {
          unitId: "u-cheo",
          x: 104,
          y: H,
          marriageBar: null,
          siblingBar: null,
          stem: null,
          // Từ đáy thẻ cha thẳng tới đỉnh thẻ con: một đường chéo.
          childDrops: [{ childId: "con", x: 104, yFrom: H, yTo: ROW_PITCH, dashed: false }],
          ended: false,
        },
      ],
      auxiliaryLinks: [],
    };
    // ChildDrop không biểu diễn nổi đường chéo (x là một số duy nhất) — nên lỗi cũ chỉ có thể tái
    // xuất qua waypoints của đường phụ. Kiểm ở đó.
    const withDiagonal: FamilyLayout = {
      ...layout,
      auxiliaryLinks: [
        {
          id: "aux-cheo",
          sourceId: "cha",
          targetId: "con",
          kind: "CROSS_BRANCH_PARENT",
          waypoints: [
            { x: 104, y: H },
            { x: -136, y: ROW_PITCH },
          ],
        },
      ],
    };
    expect(findDiagonalSegments(layout)).toEqual([]);
    expect(findDiagonalSegments(withDiagonal)).toHaveLength(1);
    expect(findDiagonalSegments(withDiagonal)[0]).toContain("đường chéo");
  });

  it("bắt được thanh anh em quét ngang qua một tấm thẻ của gia đình bên cạnh", () => {
    const base = coupleWithThreeChildren();
    const barY = H + FAMILY_RANK_SEP / 2; // 160
    const positions = new Map(base.layout.positions);
    // Một tấm thẻ bị xếp đúng vào dải ngang mà thanh anh em đang chạy qua.
    positions.set("nguoi-chen-ngang", { x: 100, y: barY - H / 2 });
    const violations = findSegmentsCrossingCards({ ...base.layout, positions });
    expect(violations.join(" | ")).toContain("nguoi-chen-ngang");
    expect(violations.join(" | ")).toContain("thanh anh em");
  });

  it("bắt được hai tấm thẻ đè lên nhau", () => {
    const layout: FamilyLayout = {
      positions: new Map([
        ["a", { x: 0, y: 0 }],
        ["b", { x: 40, y: 20 }],
      ]),
      junctions: [],
      auxiliaryLinks: [],
    };
    expect(findOverlappingCards(layout)).toHaveLength(1);
    expect(findOverlappingCards(layout)[0]).toContain("chồng lên");
  });

  it("bắt được đoạn dọc đi lệch ra ngoài khe giữa hai vợ chồng", () => {
    const base = onlyChild();
    const junction = base.layout.junctions[0]!;
    const lech: FamilyLayout = {
      ...base.layout,
      junctions: [
        {
          ...junction,
          x: 60, // rơi vào giữa thẻ người chồng thay vì vào khe
          childDrops: junction.childDrops.map((d) => ({ ...d, x: 60 })),
        },
      ],
    };
    const violations = findDropsOutsideCoupleCorridor(lech, base.units);
    expect(violations.join(" | ")).toContain("nằm ngoài khe giữa hai vợ chồng");
    // Và nó đi xuyên thẳng qua tấm thẻ — đúng lỗi người dùng thấy.
    expect(findSegmentsCrossingCards(lech).length).toBeGreaterThan(0);
  });

  it("bắt được cuộc hôn phối bị đánh rơi khi người tái hôn chỉ được giữ lại một chỗ đứng", () => {
    const base = remarriage();
    // Mô phỏng một tầng bố cục "cây thuần": giữ đúng cuộc hôn phối đầu, bỏ hẳn cuộc sau.
    const cutDown: FamilyLayout = {
      positions: base.layout.positions,
      junctions: base.layout.junctions.filter((j) => j.unitId === "u-hon-truoc"),
      auxiliaryLinks: [],
    };
    const violations = findDroppedRelationships(base.edges, base.units, cutDown);
    const joined = violations.join(" | ");
    expect(joined).toContain("vo-sau");
    expect(joined).toContain("vo-xa");
    expect(joined).toContain("biến mất khỏi cây");
    // Con của đời sau cũng mất theo — nói rõ ra, đừng để lặng lẽ.
    expect(joined).toContain("con-doi-sau-1");
  });

  it("bắt được con nuôi bị vẽ nét liền và con ruột bị vẽ nét đứt", () => {
    const base = adoptedChild();
    const junction = base.layout.junctions[0]!;
    const flipped: FamilyLayout = {
      ...base.layout,
      junctions: [
        { ...junction, childDrops: junction.childDrops.map((d) => ({ ...d, dashed: !d.dashed })) },
      ],
    };
    const violations = findAdoptionDrawingErrors(base.units, flipped);
    expect(violations).toHaveLength(2);
    expect(violations.join(" | ")).toContain("con nuôi con-nuoi");
    expect(violations.join(" | ")).toContain("con ruột con-ruot");
  });

  it("bắt được hôn phối đã ly hôn nhưng điểm nối quên cờ ended", () => {
    const base = divorcedCouple();
    const junction = base.layout.junctions[0]!;
    const forgotten: FamilyLayout = {
      ...base.layout,
      junctions: [{ ...junction, ended: false }],
    };
    expect(findEndedMarriageDrawingErrors(base.units, forgotten)).toHaveLength(1);
  });
});

/* ------------------------------------------------------------------ *
 * Bất biến trên từng kịch bản gia phả
 * ------------------------------------------------------------------ */

describe.each(scenarios.map((s) => [s.name, s] as const))("bố cục mẫu — %s", (_name, scenario) => {
  it("bố cục có thật (không xanh vì rỗng)", () => {
    expect(scenario.layout.positions.size).toBeGreaterThanOrEqual(2);
    expect(allSegments(scenario.layout).length).toBeGreaterThan(0);
    expect(scenario.edges.length).toBeGreaterThan(0);
    expect(allCardRects(scenario.layout).length).toBe(scenario.layout.positions.size);
  });

  it("bất biến 1 · không đoạn nối nào cắt qua vùng thẻ", () => {
    const violations = findSegmentsCrossingCards(scenario.layout);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("bất biến 2 · không thẻ nào chồng thẻ nào", () => {
    const violations = findOverlappingCards(scenario.layout);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("bất biến 3 · mọi đoạn đều thẳng đứng hoặc nằm ngang", () => {
    const violations = findDiagonalSegments(scenario.layout);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("bất biến 4 · đoạn dọc đi qua khe giữa hai vợ chồng", () => {
    const violations = findDropsOutsideCoupleCorridor(scenario.layout, scenario.units);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("bất biến 5 · không cạnh nào bị đánh rơi", () => {
    const violations = findDroppedRelationships(scenario.edges, scenario.units, scenario.layout);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("nét đứt đúng ngữ nghĩa · con nuôi và hôn phối đã kết thúc", () => {
    const report = runGeometryInvariants(scenario);
    expect(report.adoptionDrawing, report.adoptionDrawing.join("\n")).toEqual([]);
    expect(report.endedMarriageDrawing, report.endedMarriageDrawing.join("\n")).toEqual([]);
  });
});

/* ------------------------------------------------------------------ *
 * Vài khẳng định riêng cho từng ca khó
 * ------------------------------------------------------------------ */

describe("chi tiết từng ca khó", () => {
  it("con một thì KHÔNG dựng thanh anh em, chỉ một đoạn rơi thẳng", () => {
    const junction = onlyChild().layout.junctions[0]!;
    expect(junction.siblingBar).toBeNull();
    expect(junction.stem).toBeNull();
    expect(junction.childDrops).toHaveLength(1);
    const drop = junction.childDrops[0]!;
    expect(drop.x).toBe(junction.x);
    expect(drop.yTo).toBe(ROW_PITCH);
  });

  it("cả ba cuộc hôn phối của người tái hôn đều còn trên cây", () => {
    const scenario = remarriage();
    const marriages = scenario.edges.filter((e) => e.relType === "SPOUSE");
    expect(marriages).toHaveLength(3);
    expect(findDroppedRelationships(marriages, scenario.units, scenario.layout)).toEqual([]);
    // Hai cuộc kề nhau vẽ bằng thanh hôn phối, cuộc ở chi khác vẽ bằng đường phụ.
    expect(scenario.layout.junctions.filter((j) => j.marriageBar !== null)).toHaveLength(2);
    expect(scenario.layout.auxiliaryLinks).toHaveLength(1);
  });

  it("đa thê: ông trục đứng giữa hai bà kề, bà thứ ba đi bằng đường phụ chứ không biến mất", () => {
    const scenario = polygamyThreeWives();
    const ong = scenario.layout.positions.get("ong")!;
    const baHai = scenario.layout.positions.get("ba-hai")!;
    const baCa = scenario.layout.positions.get("ba-ca")!;
    expect(baHai.x).toBeLessThan(ong.x);
    expect(baCa.x).toBeGreaterThan(ong.x);
    expect(baCa.x - (ong.x + W)).toBe(COUPLE_GAP);
    expect(ong.x - (baHai.x + W)).toBe(COUPLE_GAP);
    expect(scenario.layout.auxiliaryLinks.map((l) => l.targetId)).toContain("ba-ba");
    expect(findDroppedRelationships(scenario.edges, scenario.units, scenario.layout)).toEqual([]);
  });

  it("cha đơn thân: điểm nối nằm ngay dưới thẻ, trong bề ngang thẻ của chính người ấy", () => {
    const scenario = singleFather();
    const junction = scenario.layout.junctions[0]!;
    const cha = scenario.layout.positions.get("cha")!;
    expect(junction.marriageBar).toBeNull();
    expect(junction.y).toBe(cha.y + H);
    expect(junction.x).toBeGreaterThanOrEqual(cha.x);
    expect(junction.x).toBeLessThanOrEqual(cha.x + W);
  });

  it("cây bốn đời: bốn hàng thẻ phân biệt, và bất biến giữ ở mọi đời", () => {
    const scenario = fourGenerations();
    const rows = new Set([...scenario.layout.positions.values()].map((p) => p.y));
    expect([...rows].sort((a, b) => a - b)).toEqual([0, ROW_PITCH, ROW_PITCH * 2, ROW_PITCH * 3]);
    const report = runGeometryInvariants(scenario);
    for (const [key, violations] of Object.entries(report)) {
      expect(violations, `${key}: ${violations.join("\n")}`).toEqual([]);
    }
  });

  it("mọi thanh hôn phối đều nằm gọn trong khe COUPLE_GAP, không dài hơn", () => {
    for (const scenario of scenarios) {
      for (const junction of scenario.layout.junctions) {
        if (!junction.marriageBar) continue;
        const width = junction.marriageBar.x2 - junction.marriageBar.x1;
        expect(width, `${scenario.name} · ${junction.unitId}`).toBeLessThanOrEqual(COUPLE_GAP);
        expect(width, `${scenario.name} · ${junction.unitId}`).toBeGreaterThan(0);
      }
    }
  });
});

/* ------------------------------------------------------------------ *
 * Chĩa CHÍNH bộ bất biến ấy vào cài đặt thật, khi nó đã có
 * ------------------------------------------------------------------ */

/** Dựng TreeNode/TreeEdge từ một kịch bản, để gọi được cài đặt thật. */
function graphFor(scenario: Scenario): { nodes: TreeNode[]; edges: TreeEdge[] } {
  const nodes = [...scenario.layout.positions.entries()].map(([id, position]) => {
    const depth = Math.round(position.y / ROW_PITCH);
    const parentIds = scenario.edges
      .filter((e) => e.target === id && e.relType !== "SPOUSE" && e.relType !== "HEIR")
      .map((e) => e.source);
    const spouseIds = scenario.edges
      .filter((e) => e.relType === "SPOUSE" && (e.source === id || e.target === id))
      .map((e) => (e.source === id ? e.target : e.source));
    const childCount = scenario.edges.filter(
      (e) => e.source === id && (e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT")
    ).length;
    return node(id, depth, { parentIds, spouseIds, childCount });
  });
  return { nodes, edges: [...scenario.edges] };
}

/**
 * CÙNG bộ bất biến ấy, lần này chĩa vào cài đặt thật.
 *
 * <p>Bố cục dựng tay ở trên chỉ chứng minh bộ bất biến là thoả mãn được. Phần dưới đây mới là phần
 * bảo vệ người dùng: {@code buildFamilyUnits} + {@code layoutFamily} thật phải giữ đủ năm bất biến
 * trên chính tám kịch bản khó ấy — đa thê, con nuôi, ly hôn, tái hôn, con một, cây bốn đời.</p>
 */
describe("cài đặt thật · buildFamilyUnits + layoutFamily", () => {
  it.each(scenarios.map((s) => [s.name, s] as const))(
    "%s giữ đủ năm bất biến",
    (_name, scenario) => {
      const { nodes, edges } = graphFor(scenario);
      const units = buildFamilyUnits(nodes, edges);
      const layout = layoutFamily(nodes, units, edges);
      const report = runGeometryInvariants({ layout, units, edges });

      expect(layout.positions.size, "layoutFamily không xếp chỗ cho ai").toBe(nodes.length);
      expect(report.segmentsCrossingCards, report.segmentsCrossingCards.join("\n")).toEqual([]);
      expect(report.overlappingCards, report.overlappingCards.join("\n")).toEqual([]);
      expect(report.diagonalSegments, report.diagonalSegments.join("\n")).toEqual([]);
      expect(report.dropsOutsideCorridor, report.dropsOutsideCorridor.join("\n")).toEqual([]);
      expect(report.droppedRelationships, report.droppedRelationships.join("\n")).toEqual([]);
      expect(report.adoptionDrawing, report.adoptionDrawing.join("\n")).toEqual([]);
      expect(report.endedMarriageDrawing, report.endedMarriageDrawing.join("\n")).toEqual([]);
    }
  );

  it("không đánh rơi cuộc hôn phối nào của người tái hôn", () => {
    const { nodes, edges } = graphFor(remarriage());
    const units = buildFamilyUnits(nodes, edges);
    const layout = layoutFamily(nodes, units, edges);

    const marriages = edges.filter((e) => e.relType === "SPOUSE");
    expect(marriages).toHaveLength(3);
    const violations = findDroppedRelationships(marriages, units, layout);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("không đánh rơi bà vợ nào của ông đa thê ba vợ", () => {
    const { nodes, edges } = graphFor(polygamyThreeWives());
    const units = buildFamilyUnits(nodes, edges);
    const layout = layoutFamily(nodes, units, edges);

    const violations = findDroppedRelationships(edges, units, layout);
    expect(violations, violations.join("\n")).toEqual([]);
  });

  /**
   * Tám kịch bản ở trên là gia phả dựng tay — sạch sẽ, mỗi ca một ý. Đồ thị giả lập thì không:
   * ~3.400 nhân khẩu, 7 đời, 4 chi, có cả những ca kỳ quặc mà không ai ngồi nghĩ ra được. Đây
   * cũng ĐÚNG dữ liệu mà màn hình phả đồ đang chạy trên đó, nên bất biến xanh ở đây thì e2e mới
   * có cửa xanh.
   */
  it("giữ đủ bất biến trên một lát cắt thật của đồ thị giả lập (~vài trăm nhân khẩu)", () => {
    const projection = queryTreeProjection(getMockGraph(), {
      rootId: "p-001",
      depth: 6,
      direction: "DESCENDANTS",
      includeSpouses: true,
      maxNodes: 400,
      isVisible: () => true,
    });
    expect(projection, "không truy được lát cắt nào từ đồ thị giả lập").not.toBeNull();
    const { nodes, edges } = projection!;
    expect(nodes.length, "lát cắt quá nhỏ để nói lên điều gì").toBeGreaterThan(100);

    const units = buildFamilyUnits(nodes, edges);
    const layout = layoutFamily(nodes, units, edges);
    expect(units.length).toBeGreaterThan(10);
    expect(allSegments(layout).length).toBeGreaterThan(100);

    const report = runGeometryInvariants({ layout, units, edges });
    expect(report.segmentsCrossingCards.slice(0, 10), report.segmentsCrossingCards.join("\n")).toEqual([]);
    expect(report.overlappingCards.slice(0, 10), report.overlappingCards.join("\n")).toEqual([]);
    expect(report.diagonalSegments.slice(0, 10), report.diagonalSegments.join("\n")).toEqual([]);
    expect(report.dropsOutsideCorridor.slice(0, 10), report.dropsOutsideCorridor.join("\n")).toEqual([]);
    expect(report.droppedRelationships.slice(0, 10), report.droppedRelationships.join("\n")).toEqual([]);
    expect(report.adoptionDrawing.slice(0, 10), report.adoptionDrawing.join("\n")).toEqual([]);
    expect(report.endedMarriageDrawing.slice(0, 10), report.endedMarriageDrawing.join("\n")).toEqual([]);
  });

  it("bố cục ổn định: dựng lại hai lần cho ra đúng một kết quả", () => {
    const { nodes, edges } = graphFor(fourGenerations());
    const first = layoutFamily(nodes, buildFamilyUnits(nodes, edges), edges);
    const second = layoutFamily(nodes, buildFamilyUnits(nodes, edges), edges);
    expect([...second.positions.entries()]).toEqual([...first.positions.entries()]);
    expect(second.junctions).toEqual(first.junctions);
  });
});
