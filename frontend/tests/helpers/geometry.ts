import type {
  AuxiliaryLink,
  ChildDrop,
  FamilyJunction,
  FamilyLayout,
  FamilyUnit,
  HorizontalBar,
} from "@/lib/tree/family-layout-types";
import { NODE_HEIGHT, NODE_WIDTH } from "@/lib/tree/layout-constants";
import type { TreeEdge } from "@/types/api";

/**
 * Bộ dụng cụ hình học dùng chung cho phả đồ dựng theo ĐƠN VỊ GIA ĐÌNH.
 *
 * <p>Người dùng nói một câu rất cụ thể: "các đường nối liên kết đang bị che bởi các ô tên, nhìn
 * không được tường minh". Tệp này biến câu đó thành thứ máy kiểm được: một đoạn đường nối
 * <b>không được đi xuyên qua</b> hình chữ nhật 208×96 của bất kỳ tấm thẻ nhân khẩu nào. Mọi hàm ở
 * đây thuần tuý hình học — không React, không DOM — nên chạy được ở tầng unit lẫn dùng lại làm
 * chuẩn so sánh cho phép đo trên trình duyệt thật.</p>
 *
 * <p>Các hàm kiểm bất biến đều trả về <b>danh sách mô tả vi phạm</b> chứ không tự ném lỗi: test chỉ
 * việc {@code expect(...).toEqual([])} và khi đỏ thì thông báo đã nói thẳng đoạn nào cắt thẻ nào,
 * ở toạ độ nào — thứ duy nhất giúp sửa được mà không phải mở trình duyệt lên đoán.</p>
 */

export interface Point {
  readonly x: number;
  readonly y: number;
}

export interface Rect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export interface Segment {
  readonly x1: number;
  readonly y1: number;
  readonly x2: number;
  readonly y2: number;
}

/** Đoạn thẳng kèm tên gọi để thông báo lỗi đọc được (vd "u-01 · thanh hôn phối"). */
export interface LabeledSegment extends Segment {
  readonly label: string;
}

export interface LabeledRect extends Rect {
  /** id nhân khẩu của tấm thẻ. */
  readonly id: string;
}

/** Sai số coi hai số là bằng nhau khi xét đoạn thẳng đứng / nằm ngang. */
export const AXIS_EPS = 0.01;

/**
 * Dung sai khi xét "đoạn có chui vào thẻ không", tính bằng px.
 *
 * <p>Nét vẽ có bề dày và đầu mút đoạn rơi CỐ Ý chạm mép thẻ (bắt đầu ở đáy thẻ cha, kết thúc ở đỉnh
 * thẻ con), nên chạm mép không phải vi phạm. Nhưng 1px là dung sai của nét vẽ, không phải giấy
 * phép: đi sâu quá 1px vào trong thẻ vẫn là vi phạm, vì đó đúng là hiện tượng người dùng phàn nàn.</p>
 */
export const CARD_TOLERANCE_PX = 1;

/* ------------------------------------------------------------------ *
 * Hình học nguyên thuỷ
 * ------------------------------------------------------------------ */

/** Hình chữ nhật của một tấm thẻ, từ vị trí GÓC TRÊN-TRÁI (đúng quy ước của FamilyLayout). */
export function cardRect(
  position: Point,
  width: number = NODE_WIDTH,
  height: number = NODE_HEIGHT
): Rect {
  return { x: position.x, y: position.y, width, height };
}

/** Thu hình chữ nhật vào trong mỗi phía {@code amount} px. */
export function insetRect(rect: Rect, amount: number): Rect {
  return {
    x: rect.x + amount,
    y: rect.y + amount,
    width: rect.width - 2 * amount,
    height: rect.height - 2 * amount,
  };
}

/**
 * Hai hình chữ nhật có chồng lấn phần RUỘT không. Chạm mép (chồng lấn 0px) không tính là chồng —
 * hai thẻ kề sát nhau vẫn đọc được, chỉ đè lên nhau mới che chữ.
 */
export function rectsOverlap(a: Rect, b: Rect, tolerance = 0): boolean {
  const overlapX = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
  const overlapY = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y);
  return overlapX > tolerance && overlapY > tolerance;
}

/** Diện tích chồng lấn, dùng cho thông báo lỗi. */
export function overlapArea(a: Rect, b: Rect): number {
  const overlapX = Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x));
  const overlapY = Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y));
  return overlapX * overlapY;
}

export type Orientation = "HORIZONTAL" | "VERTICAL" | "POINT" | "DIAGONAL";

export function orientationOf(segment: Segment, eps = AXIS_EPS): Orientation {
  const dx = Math.abs(segment.x2 - segment.x1);
  const dy = Math.abs(segment.y2 - segment.y1);
  if (dx <= eps && dy <= eps) return "POINT";
  if (dy <= eps) return "HORIZONTAL";
  if (dx <= eps) return "VERTICAL";
  return "DIAGONAL";
}

/** Thẳng đứng hoặc nằm ngang — phả đồ mới không có đường chéo. */
export function isAxisAligned(segment: Segment, eps = AXIS_EPS): boolean {
  return orientationOf(segment, eps) !== "DIAGONAL";
}

export function isVertical(segment: Segment, eps = AXIS_EPS): boolean {
  return orientationOf(segment, eps) === "VERTICAL";
}

export function isHorizontal(segment: Segment, eps = AXIS_EPS): boolean {
  return orientationOf(segment, eps) === "HORIZONTAL";
}

export function segmentLength(segment: Segment): number {
  return Math.hypot(segment.x2 - segment.x1, segment.y2 - segment.y1);
}

/**
 * Chiều dài phần đoạn thẳng nằm trong RUỘT hình chữ nhật (đã thu vào {@code tolerance} px).
 *
 * <p>Cắt đoạn theo thuật toán Liang–Barsky nên đúng cho cả đoạn chéo — nếu tầng bố cục lỡ sinh ra
 * đường chéo thì bất biến "không cắt thẻ" vẫn đo được, không phải chờ sửa xong mới kiểm được.</p>
 */
export function segmentRectPenetration(
  segment: Segment,
  rect: Rect,
  tolerance = CARD_TOLERANCE_PX
): number {
  const inner = insetRect(rect, tolerance);
  if (inner.width <= 0 || inner.height <= 0) return 0;

  const dx = segment.x2 - segment.x1;
  const dy = segment.y2 - segment.y1;
  const clips: readonly (readonly [number, number])[] = [
    [-dx, segment.x1 - inner.x],
    [dx, inner.x + inner.width - segment.x1],
    [-dy, segment.y1 - inner.y],
    [dy, inner.y + inner.height - segment.y1],
  ];

  let t0 = 0;
  let t1 = 1;
  for (const [p, q] of clips) {
    if (p === 0) {
      // Song song với cạnh này: nằm ngoài dải thì không thể cắt.
      if (q < 0) return 0;
      continue;
    }
    const r = q / p;
    if (p < 0) {
      if (r > t1) return 0;
      if (r > t0) t0 = r;
    } else {
      if (r < t0) return 0;
      if (r < t1) t1 = r;
    }
  }
  if (t1 <= t0) return 0;
  return (t1 - t0) * Math.hypot(dx, dy);
}

/**
 * Đoạn có đi XUYÊN QUA vùng thẻ không. Chạm mép / kết thúc đúng trên mép ⇒ false (hợp lệ, đó là
 * cách đoạn rơi neo vào thẻ con); ăn sâu vào trong quá {@link CARD_TOLERANCE_PX} ⇒ true.
 */
export function segmentIntersectsRect(
  segment: Segment,
  rect: Rect,
  tolerance = CARD_TOLERANCE_PX
): boolean {
  return segmentRectPenetration(segment, rect, tolerance) > tolerance;
}

/** Điểm nằm hẳn trong ruột hình chữ nhật (chạm mép không tính). */
export function pointInRect(point: Point, rect: Rect, tolerance = CARD_TOLERANCE_PX): boolean {
  const inner = insetRect(rect, tolerance);
  return (
    point.x > inner.x &&
    point.x < inner.x + inner.width &&
    point.y > inner.y &&
    point.y < inner.y + inner.height
  );
}

const round = (n: number): number => Math.round(n * 100) / 100;

export function formatSegment(segment: Segment): string {
  return `(${round(segment.x1)},${round(segment.y1)})→(${round(segment.x2)},${round(segment.y2)})`;
}

export function formatRect(rect: Rect): string {
  return `[${round(rect.x)},${round(rect.y)} ${round(rect.width)}×${round(rect.height)}]`;
}

/* ------------------------------------------------------------------ *
 * Bóc hình học ra khỏi FamilyLayout
 * ------------------------------------------------------------------ */

export function barToSegment(bar: HorizontalBar): Segment {
  return { x1: bar.x1, y1: bar.y, x2: bar.x2, y2: bar.y };
}

export function dropToSegment(drop: ChildDrop): Segment {
  return { x1: drop.x, y1: drop.yFrom, x2: drop.x, y2: drop.yTo };
}

/** Mọi đoạn thẳng một FamilyJunction sinh ra, kèm nhãn. */
export function junctionSegments(junction: FamilyJunction): LabeledSegment[] {
  const out: LabeledSegment[] = [];
  if (junction.marriageBar) {
    out.push({
      ...barToSegment(junction.marriageBar),
      label: `${junction.unitId} · thanh hôn phối`,
    });
  }
  if (junction.stem) {
    out.push({
      x1: junction.stem.x,
      y1: junction.stem.yFrom,
      x2: junction.stem.x,
      y2: junction.stem.yTo,
      label: `${junction.unitId} · đoạn dọc xuống thanh anh em`,
    });
  }
  if (junction.siblingBar) {
    out.push({ ...barToSegment(junction.siblingBar), label: `${junction.unitId} · thanh anh em` });
  }
  for (const drop of junction.childDrops) {
    out.push({ ...dropToSegment(drop), label: `${junction.unitId} · đoạn rơi tới ${drop.childId}` });
  }
  return out;
}

/** Các đoạn gãy khúc của một đường phụ. */
export function auxiliaryLinkSegments(link: AuxiliaryLink): LabeledSegment[] {
  const out: LabeledSegment[] = [];
  for (let i = 0; i + 1 < link.waypoints.length; i += 1) {
    const a = link.waypoints[i];
    const b = link.waypoints[i + 1];
    if (!a || !b) continue;
    out.push({
      x1: a.x,
      y1: a.y,
      x2: b.x,
      y2: b.y,
      label: `đường phụ ${link.id} (${link.kind}) · đoạn ${i + 1}`,
    });
  }
  return out;
}

/** TOÀN BỘ đoạn thẳng của một bố cục — thanh hôn phối, đoạn dọc, thanh anh em, đoạn rơi, đường phụ. */
export function allSegments(layout: FamilyLayout): LabeledSegment[] {
  return [
    ...layout.junctions.flatMap(junctionSegments),
    ...layout.auxiliaryLinks.flatMap(auxiliaryLinkSegments),
  ];
}

/** Hình chữ nhật của mọi tấm thẻ trong bố cục. */
export function allCardRects(
  layout: FamilyLayout,
  width: number = NODE_WIDTH,
  height: number = NODE_HEIGHT
): LabeledRect[] {
  return [...layout.positions.entries()].map(([id, position]) => ({
    id,
    ...cardRect(position, width, height),
  }));
}

/* ------------------------------------------------------------------ *
 * Bất biến 1 — không đoạn nào cắt qua vùng thẻ
 * ------------------------------------------------------------------ */

export function findSegmentsCrossingCards(
  layout: FamilyLayout,
  tolerance = CARD_TOLERANCE_PX
): string[] {
  const cards = allCardRects(layout);
  const violations: string[] = [];
  for (const segment of allSegments(layout)) {
    for (const card of cards) {
      const depth = segmentRectPenetration(segment, card, tolerance);
      if (depth > tolerance) {
        violations.push(
          `${segment.label} ${formatSegment(segment)} cắt qua thẻ ${card.id} ${formatRect(card)} ` +
            `— chui vào ${round(depth)}px`
        );
      }
    }
  }
  return violations;
}

/* ------------------------------------------------------------------ *
 * Bất biến 2 — không thẻ nào chồng thẻ nào
 * ------------------------------------------------------------------ */

export function findOverlappingCards(layout: FamilyLayout): string[] {
  const cards = allCardRects(layout);
  const violations: string[] = [];
  for (let i = 0; i < cards.length; i += 1) {
    for (let j = i + 1; j < cards.length; j += 1) {
      const a = cards[i];
      const b = cards[j];
      if (!a || !b) continue;
      if (rectsOverlap(a, b)) {
        violations.push(
          `thẻ ${a.id} ${formatRect(a)} chồng lên thẻ ${b.id} ${formatRect(b)} ` +
            `— ${round(overlapArea(a, b))}px²`
        );
      }
    }
  }
  return violations;
}

/* ------------------------------------------------------------------ *
 * Bất biến 3 — mọi đoạn đều thẳng đứng hoặc nằm ngang
 * ------------------------------------------------------------------ */

export function findDiagonalSegments(layout: FamilyLayout, eps = AXIS_EPS): string[] {
  return allSegments(layout)
    .filter((segment) => !isAxisAligned(segment, eps))
    .map((segment) => `${segment.label} ${formatSegment(segment)} là đường chéo`);
}

/* ------------------------------------------------------------------ *
 * Bất biến 4 — đoạn rơi đi qua khe giữa hai vợ chồng
 * ------------------------------------------------------------------ */

/** Khe dọc giữa hai tấm thẻ của một cặp; null khi hai thẻ chồng lên nhau theo phương ngang. */
export function coupleCorridor(a: Rect, b: Rect): { left: number; right: number } | null {
  const [leftCard, rightCard] = a.x <= b.x ? [a, b] : [b, a];
  const left = leftCard.x + leftCard.width;
  const right = rightCard.x;
  if (right < left) return null;
  return { left, right };
}

/**
 * Đoạn dọc từ điểm nối phải nằm trong khe giữa hai vợ chồng (đơn thân: trong bề ngang thẻ của
 * chính người đó). Đây là cơ chế khiến đường nối không bao giờ gặp thẻ — đi lệch ra ngoài khe là
 * quay lại đúng lỗi cũ, chỉ chưa lộ ra vì hàng đó tình cờ chưa có thẻ nào.
 */
export function findDropsOutsideCoupleCorridor(
  layout: FamilyLayout,
  units: readonly FamilyUnit[],
  tolerance = CARD_TOLERANCE_PX
): string[] {
  const violations: string[] = [];
  const unitById = new Map(units.map((u) => [u.id, u]));

  for (const junction of layout.junctions) {
    const unit = unitById.get(junction.unitId);
    if (!unit) {
      violations.push(`điểm nối ${junction.unitId} không ứng với đơn vị gia đình nào`);
      continue;
    }
    const rects = unit.partnerIds
      .map((id) => {
        const position = layout.positions.get(id);
        return position ? cardRect(position) : null;
      })
      .filter((r): r is Rect => r !== null);

    if (rects.length !== unit.partnerIds.length) {
      violations.push(`đơn vị ${unit.id} có bạn đời chưa được xếp chỗ`);
      continue;
    }

    let corridor: { left: number; right: number } | null;
    let corridorLabel: string;
    const first = rects[0];
    const second = rects[1];
    if (rects.length === 1 && first) {
      corridor = { left: first.x, right: first.x + first.width };
      corridorLabel = "bề ngang thẻ (cha/mẹ đơn thân)";
    } else if (rects.length === 2 && first && second) {
      corridor = coupleCorridor(first, second);
      corridorLabel = "khe giữa hai vợ chồng";
    } else {
      violations.push(
        `đơn vị ${unit.id} có ${rects.length} bạn đời — một đơn vị chỉ được 1 hoặc 2`
      );
      continue;
    }

    if (!corridor) {
      violations.push(
        `đơn vị ${unit.id} không có khe nào giữa hai thẻ vợ chồng (hai thẻ chồng nhau?)`
      );
      continue;
    }
    const bounds = corridor;

    const within = (value: number): boolean =>
      value >= bounds.left - tolerance && value <= bounds.right + tolerance;

    if (!within(junction.x)) {
      violations.push(
        `điểm nối ${unit.id} ở x=${round(junction.x)} nằm ngoài ${corridorLabel} ` +
          `[${round(bounds.left)},${round(bounds.right)}]`
      );
    }
    if (junction.stem && !within(junction.stem.x)) {
      violations.push(
        `đoạn dọc của ${unit.id} ở x=${round(junction.stem.x)} nằm ngoài ${corridorLabel} ` +
          `[${round(bounds.left)},${round(bounds.right)}]`
      );
    }
    if (junction.marriageBar) {
      const bar = junction.marriageBar;
      if (!within(bar.x1) || !within(bar.x2)) {
        violations.push(
          `thanh hôn phối của ${unit.id} [${round(bar.x1)},${round(bar.x2)}] tràn ra ngoài ` +
            `${corridorLabel} [${round(bounds.left)},${round(bounds.right)}]`
        );
      }
    }
  }
  return violations;
}

/* ------------------------------------------------------------------ *
 * Bất biến 5 — không cạnh nào bị đánh rơi
 * ------------------------------------------------------------------ */

const pairKey = (a: string, b: string): string => [a, b].sort().join("~");

/**
 * Mọi quan hệ trong dữ liệu vào phải XUẤT HIỆN Ở ĐẦU RA — trong hình học của một FamilyJunction
 * hoặc trong một AuxiliaryLink.
 *
 * <p>Ca then chốt là người TÁI HÔN: họ thuộc hai đơn vị cùng lúc, và một thư viện cây thuần sẽ
 * lặng lẽ giữ lại đúng một chỗ đứng rồi đánh rơi cuộc hôn phối kia. Vài tuần sau sẽ có người mở
 * phả ra và thấy bà hai của cụ tổ biến mất — không có thông báo lỗi nào, chỉ có một gia đình bị
 * xoá khỏi lịch sử.</p>
 *
 * <p>Cạnh HEIR (đích tôn / thừa tự / kế tự) KHÔNG kiểm ở đây: nó được thể hiện bằng huy hiệu trên
 * thẻ (xem {@code lib/tree/badges.ts}), không nhất thiết phải là một đường kẻ.</p>
 */
export function findDroppedRelationships(
  edges: readonly TreeEdge[],
  units: readonly FamilyUnit[],
  layout: FamilyLayout
): string[] {
  const junctionByUnit = new Map(layout.junctions.map((j) => [j.unitId, j]));
  const auxPairs = new Set(layout.auxiliaryLinks.map((l) => pairKey(l.sourceId, l.targetId)));
  const violations: string[] = [];

  for (const edge of edges) {
    if (edge.relType === "HEIR") continue;
    const key = pairKey(edge.source, edge.target);

    if (edge.relType === "SPOUSE") {
      const unit = units.find(
        (u) => u.partnerIds.includes(edge.source) && u.partnerIds.includes(edge.target)
      );
      const junction = unit ? junctionByUnit.get(unit.id) : undefined;
      const drawnAsUnit = Boolean(junction && junction.marriageBar);
      if (!drawnAsUnit && !auxPairs.has(key)) {
        const why = unit
          ? junction
            ? `đơn vị ${unit.id} có điểm nối nhưng không có thanh hôn phối`
            : `đơn vị ${unit.id} không được xếp chỗ`
          : "không đơn vị nào chứa cả hai người";
        violations.push(
          `hôn phối ${edge.source}–${edge.target} (${edge.id}) biến mất khỏi cây: ${why}, ` +
            "và cũng không có đường phụ nào nối họ"
        );
      }
      continue;
    }

    // PARENT_BIO / PARENT_ADOPT: source là cha/mẹ, target là con.
    const unit = units.find(
      (u) => u.partnerIds.includes(edge.source) && u.childIds.includes(edge.target)
    );
    const junction = unit ? junctionByUnit.get(unit.id) : undefined;
    const hasDrop = Boolean(junction?.childDrops.some((d) => d.childId === edge.target));
    if (!hasDrop && !auxPairs.has(key)) {
      const why = unit
        ? `đơn vị ${unit.id} không có đoạn rơi tới ${edge.target}`
        : "không đơn vị nào ghi nhận cặp cha/mẹ–con này";
      violations.push(
        `quan hệ ${edge.relType} ${edge.source}→${edge.target} (${edge.id}) biến mất khỏi cây: ` +
          `${why}, và cũng không có đường phụ nào nối họ`
      );
    }
  }
  return violations;
}

/* ------------------------------------------------------------------ *
 * Bất biến phụ — nét đứt phải đúng ngữ nghĩa
 * ------------------------------------------------------------------ */

/**
 * Con nuôi phải là nét ĐỨT ở đoạn rơi xuống chính người con đó, và chỉ ở đó — không được đứt cả
 * thanh anh em (làm thế thì các con ruột cùng nhà cũng hoá thành con nuôi trên hình).
 */
export function findAdoptionDrawingErrors(
  units: readonly FamilyUnit[],
  layout: FamilyLayout
): string[] {
  const violations: string[] = [];
  const junctionByUnit = new Map(layout.junctions.map((j) => [j.unitId, j]));
  for (const unit of units) {
    const junction = junctionByUnit.get(unit.id);
    if (!junction) continue;
    for (const drop of junction.childDrops) {
      const shouldBeDashed = unit.adoptedChildIds.has(drop.childId);
      if (shouldBeDashed !== drop.dashed) {
        violations.push(
          shouldBeDashed
            ? `con nuôi ${drop.childId} của ${unit.id} được vẽ nét liền như con ruột`
            : `con ruột ${drop.childId} của ${unit.id} bị vẽ nét đứt như con nuôi`
        );
      }
    }
  }
  return violations;
}

/**
 * Hôn phối đã kết thúc (ly hôn / goá) vẫn phải nằm trên cây, và phải mang cờ {@code ended}.
 *
 * <p>Một đơn vị KHÔNG có điểm nối chỉ được tha khi cuộc hôn phối ấy đã được vẽ bằng đường phụ —
 * đó là lối thoát hợp lệ cho bà vợ thứ ba (không đứng kề ông được) hay người bạn đời ở chi khác.
 * Không có cả hai thì đơn vị đã bị đánh rơi khỏi đầu ra.</p>
 */
export function findEndedMarriageDrawingErrors(
  units: readonly FamilyUnit[],
  layout: FamilyLayout
): string[] {
  const violations: string[] = [];
  const junctionByUnit = new Map(layout.junctions.map((j) => [j.unitId, j]));
  const auxPairs = new Set(layout.auxiliaryLinks.map((l) => pairKey(l.sourceId, l.targetId)));
  for (const unit of units) {
    const junction = junctionByUnit.get(unit.id);
    if (!junction) {
      const [a, b] = unit.partnerIds;
      const drawnAsAux = Boolean(a && b && auxPairs.has(pairKey(a, b)));
      if (!drawnAsAux) {
        violations.push(
          `đơn vị ${unit.id} không có điểm nối nào trên bố cục, cũng không có đường phụ thay thế`
        );
      }
      continue;
    }
    if (junction.ended !== unit.ended) {
      violations.push(
        `đơn vị ${unit.id}: hôn phối ${unit.ended ? "đã kết thúc" : "còn hiệu lực"} nhưng điểm nối ` +
          `ghi ended=${junction.ended}`
      );
    }
  }
  return violations;
}

/* ------------------------------------------------------------------ *
 * Chạy trọn bộ
 * ------------------------------------------------------------------ */

export interface LayoutUnderTest {
  readonly layout: FamilyLayout;
  readonly units: readonly FamilyUnit[];
  /** Quan hệ đầu vào — nguồn sự thật cho bất biến "không cạnh nào bị đánh rơi". */
  readonly edges: readonly TreeEdge[];
}

export interface InvariantReport {
  readonly segmentsCrossingCards: string[];
  readonly overlappingCards: string[];
  readonly diagonalSegments: string[];
  readonly dropsOutsideCorridor: string[];
  readonly droppedRelationships: string[];
  readonly adoptionDrawing: string[];
  readonly endedMarriageDrawing: string[];
}

export function runGeometryInvariants(subject: LayoutUnderTest): InvariantReport {
  return {
    segmentsCrossingCards: findSegmentsCrossingCards(subject.layout),
    overlappingCards: findOverlappingCards(subject.layout),
    diagonalSegments: findDiagonalSegments(subject.layout),
    dropsOutsideCorridor: findDropsOutsideCoupleCorridor(subject.layout, subject.units),
    droppedRelationships: findDroppedRelationships(subject.edges, subject.units, subject.layout),
    adoptionDrawing: findAdoptionDrawingErrors(subject.units, subject.layout),
    endedMarriageDrawing: findEndedMarriageDrawingErrors(subject.units, subject.layout),
  };
}
