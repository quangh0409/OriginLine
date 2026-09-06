import type { Edge, Node } from "@xyflow/react";
import type { TreeEdge, TreeNode } from "@/types/api";
import type { NodePosition } from "./layout-hierarchical";
import type { AuxiliaryLink, FamilyJunction, FamilyUnit } from "./family-layout-types";
import { colorTokens } from "@/styles/tokens";

export interface PersonNodeData extends Record<string, unknown> {
  treeNode: TreeNode;
  hasLoadableChildren: boolean;
}

/**
 * Converts the canvas's already-filtered (visible) TreeNode/TreeEdge set into
 * React Flow's node/edge shape. Layout-agnostic — takes a precomputed
 * position map so the same converter serves all three view modes.
 */
export function toFlowNodes(nodes: TreeNode[], positions: Map<string, NodePosition>): Node<PersonNodeData>[] {
  return nodes.map((treeNode) => {
    const pos = positions.get(treeNode.id) ?? { x: 0, y: 0 };
    return {
      id: treeNode.id,
      type: "person",
      position: pos,
      data: {
        treeNode,
        hasLoadableChildren: treeNode.hasMoreDescendants || (treeNode.childCount ?? 0) > 0,
      },
      draggable: true,
    };
  });
}

/* ==========================================================================
   QUY ƯỚC NÉT — Hội đồng Tộc biểu đã duyệt
   ========================================================================== */

export interface StrokeStyle {
  readonly stroke: string;
  readonly strokeWidth: number;
  /** Không đặt ⇒ nét liền. */
  readonly strokeDasharray?: string;
}

/**
 * Mỗi màu đi qua một biến CSS có giá trị dự phòng lấy từ {@link colorTokens}. Không viết mã màu
 * cứng ở đây, và cũng không khoá cứng bảng màu sáng: khi giao diện tối được bật (tailwind
 * `darkMode: "class"`), chỉ cần khai lại ba biến `--tree-line-*` trong khối `.dark` là toàn bộ
 * phả đồ đọc được, không phải sửa một dòng TypeScript nào. Chừng nào chưa khai, trình duyệt dùng
 * đúng token của giao diện sáng.
 */
const LINE_COLOR = {
  /** `--muc-nhat` — đường huyết thống. */
  descent: `var(--tree-line-descent, ${colorTokens.textMuted})`,
  /** Hổ phách — hôn phối. */
  marriage: `var(--tree-line-marriage, ${colorTokens.accent})`,
  /** Đỏ trầm — kế tự / thừa tự. */
  heir: `var(--tree-line-heir, ${colorTokens.primary})`,
} as const;

/** Nét đứt của con nuôi và của hôn phối đã kết thúc dùng chung một mẫu; màu mới là thứ phân biệt. */
const DASH_BROKEN = "6 4";
/** Nét chấm — dành riêng cho đường vẽ vòng, để không lẫn với "con nuôi / đã kết thúc". */
const DASH_DETOUR = "2 5";

/**
 * Bảng quy ước nét dùng chung cho CẢ canvas lẫn `<TreeLegend>`. Chú giải đọc thẳng bảng này thay
 * vì tự vẽ lại mẫu nét, nên chú giải không thể nói sai về thứ đang hiển thị trên phả đồ.
 */
export const TREE_STROKES = {
  /** Con đẻ — nét liền, màu mực nhạt. */
  bioChild: { stroke: LINE_COLOR.descent, strokeWidth: 1.75 },
  /**
   * Con nuôi — nét đứt CHỈ ở đoạn rơi xuống người con. Thanh anh em vẫn liền, nếu không thì anh
   * chị em ruột của người con nuôi cũng trông như con nuôi.
   */
  adoptedChild: { stroke: LINE_COLOR.descent, strokeWidth: 1.75, strokeDasharray: DASH_BROKEN },
  /** Thanh hôn phối — hổ phách, đậm hơn đường huyết thống. */
  marriage: { stroke: LINE_COLOR.marriage, strokeWidth: 2.5 },
  /** Ly hôn / goá — vẫn là sự kiện của phả, nên giữ nguyên màu, chỉ đổi sang nét đứt. */
  marriageEnded: { stroke: LINE_COLOR.marriage, strokeWidth: 2.5, strokeDasharray: DASH_BROKEN },
  /** Kế tự / thừa tự — dòng cha–con TRÊN DANH NGHĨA: mảnh hơn con đẻ và mang màu đỏ trầm. */
  heir: { stroke: LINE_COLOR.heir, strokeWidth: 1.25 },
  /** Tuyệt tự — một gạch ngang ngắn khép dưới đáy thẻ. */
  lineageEnd: { stroke: LINE_COLOR.descent, strokeWidth: 2 },
  /** Đường vẽ vòng: con nuôi đến từ chi khác. */
  detour: { stroke: LINE_COLOR.descent, strokeWidth: 1.25, strokeDasharray: DASH_DETOUR },
  /** Đường vẽ vòng mang nghĩa hôn phối (tái hôn) — vẫn hổ phách để đọc ra là hôn phối. */
  detourMarriage: { stroke: LINE_COLOR.marriage, strokeWidth: 1.25, strokeDasharray: DASH_DETOUR },
} as const satisfies Record<string, StrokeStyle>;

export type TreeStrokeName = keyof typeof TREE_STROKES;

/** Tên kiểu cạnh đăng ký trong `familyEdgeTypes` (xem components/tree/family-edges.tsx). */
export const FAMILY_JUNCTION_EDGE_TYPE = "family-junction";
export const AUXILIARY_LINK_EDGE_TYPE = "family-auxiliary";

export interface FamilyJunctionEdgeData extends Record<string, unknown> {
  junction: FamilyJunction;
}

export interface AuxiliaryLinkEdgeData extends Record<string, unknown> {
  link: AuxiliaryLink;
}

export type FamilyJunctionFlowEdge = Edge<FamilyJunctionEdgeData, typeof FAMILY_JUNCTION_EDGE_TYPE>;
export type AuxiliaryLinkFlowEdge = Edge<AuxiliaryLinkEdgeData, typeof AUXILIARY_LINK_EDGE_TYPE>;

/**
 * Thuộc tính bắt buộc cho MỌI cạnh của phả đồ — đây là thứ cho phép bỏ mẹo `zIndex: -1` cũ.
 *
 * Trước đây cạnh bị dìm xuống dưới thẻ vì lớp cạnh nuốt thao tác chạm. Nguyên nhân thật không
 * phải thứ tự lớp mà là hai thứ trong React Flow:
 *
 * 1. `interactionWidth` mặc định 20 khiến mỗi cạnh vẽ kèm một `<path>` vô hình
 *    (`strokeOpacity: 0`) rộng 20px. Vệt đó vẫn bắt sự kiện vì `.react-flow__edge` dùng
 *    `pointer-events: visibleStroke` — trong suốt vẫn được coi là "có nét". Đặt 0 thì React Flow
 *    KHÔNG dựng đường đó nữa (`interactionWidth ? jsx("path", …) : null`).
 * 2. Cạnh chọn được thì cả nhóm `<g>` bắt sự kiện. Đặt `selectable: false` và không truyền
 *    `onEdgeClick` thì React Flow gắn lớp `inactive`, mà biểu định kiểu của thư viện quy định
 *    `.react-flow__edge.inactive { pointer-events: none }`.
 *
 * Hai điều đó cộng lại: lớp cạnh không còn ô cửa nào để nuốt cú chạm, nên `zIndex: 0` — lớp bình
 * thường — là an toàn. Thẻ nhân khẩu vẫn nằm trên cạnh vì `<EdgeRenderer>` đứng TRƯỚC
 * `<NodeRenderer>` trong DOM và cả hai cùng z-index 0.
 */
const INERT_EDGE = {
  selectable: false,
  focusable: false,
  interactionWidth: 0,
  zIndex: 0,
} as const;

function strokeFor(e: TreeEdge): StrokeStyle {
  if (e.relType === "SPOUSE") return e.validTo ? TREE_STROKES.marriageEnded : TREE_STROKES.marriage;
  if (e.relType === "HEIR") return TREE_STROKES.heir;
  if (e.relType === "PARENT_ADOPT") return TREE_STROKES.adoptedChild;
  return TREE_STROKES.bioChild;
}

/**
 * Cạnh nối THẲNG người với người — chỉ dùng cho hai chế độ xem không có đơn vị gia đình (tỏa tròn
 * và ma trận đời). Chế độ phân cấp vẽ bằng {@link toJunctionEdges} + {@link toAuxiliaryEdges}.
 *
 * Neo trái–phải cố định đã bị bỏ. Bản cũ ép đường vợ chồng đi từ neo TRÁI của người này sang neo
 * PHẢI của người kia; với hai thẻ 208px kề nhau, đường dài 464px mà 416px chui dưới hai tấm thẻ,
 * người dùng chỉ còn thấy mẩu 48px ở khe giữa. Nay cạnh hôn phối không khai neo nào: React Flow
 * dùng neo mặc định của thẻ, nên đoạn vẽ ra là đoạn ngắn giữa hai thẻ chứ không xuyên qua chúng.
 *
 * Không còn mũi tên trên đường huyết thống: cây vẽ từ trên xuống nên chiều đã hiển nhiên, mũi tên
 * chỉ đọng thành chấm đen gây nhiễu khi thu nhỏ.
 */
export function toFlowEdges(edges: TreeEdge[]): Edge[] {
  return edges.map((e) => {
    const isSpouse = e.relType === "SPOUSE";
    return {
      id: e.id,
      source: e.source,
      target: e.target,
      // Hôn phối không neo cố định (xem javadoc); huyết thống/kế tự đi từ đáy cha xuống đỉnh con.
      ...(isSpouse ? {} : { sourceHandle: "bottom", targetHandle: "top" }),
      type: isSpouse ? "straight" : "smoothstep",
      animated: false,
      style: { ...strokeFor(e) },
      // Không nhãn chữ ở tầng này (sẽ phải i18n ngoài component); quy ước nét được giải thích một
      // lần duy nhất trong <TreeLegend>.
      ...INERT_EDGE,
    };
  });
}

/** Đơn vị gia đình có đoạn nào để vẽ hay không. */
function junctionHasGeometry(j: FamilyJunction): boolean {
  return Boolean(j.marriageBar) || Boolean(j.stem) || Boolean(j.siblingBar) || j.childDrops.length > 0;
}

/**
 * Chọn hai đầu neo cho cạnh đơn vị gia đình.
 *
 * Hình học đã tính sẵn trong {@link FamilyJunction} nên hai đầu này KHÔNG quyết định nét vẽ. Chúng
 * chỉ phục vụ hai việc của React Flow: cạnh có nút nguồn/đích không tồn tại thì bị bỏ hẳn (xem
 * `EdgeWrapper`), và `onlyRenderVisibleElements` cắt cạnh theo hình chữ nhật bao hai nút đó. Vì
 * vậy chọn cặp xa nhau nhất — người trục và người con cuối — để hình bao phủ trọn đơn vị gia
 * đình, tránh cảnh phóng to vào một người con thì thanh anh em biến mất.
 */
function junctionAnchors(unit: FamilyUnit): { source: string; target: string } | null {
  const source = unit.anchorId || unit.partnerIds[0];
  if (!source) return null;
  const lastChild = unit.childIds.length > 0 ? unit.childIds[unit.childIds.length - 1] : undefined;
  const otherPartner = unit.partnerIds.find((id) => id !== source);
  return { source, target: lastChild ?? otherPartner ?? source };
}

/**
 * Biến hình học đã tính sẵn của tầng bố cục thành cạnh React Flow. KHÔNG tính toạ độ ở đây — mọi
 * con số đến từ `layout-family.ts`; tầng này chỉ đóng gói lại.
 */
export function toJunctionEdges(
  junctions: readonly FamilyJunction[],
  units: readonly FamilyUnit[]
): FamilyJunctionFlowEdge[] {
  const unitById = new Map(units.map((u) => [u.id, u]));
  const out: FamilyJunctionFlowEdge[] = [];

  for (const junction of junctions) {
    // Đơn vị không có đoạn nào để vẽ (cha/mẹ đơn thân chưa ghi nhận con) thì không dựng cạnh —
    // một cạnh rỗng vẫn tốn một <svg> cho mỗi gia đình, nhân với hàng nghìn nút thì thấy ngay.
    if (!junctionHasGeometry(junction)) continue;
    const unit = unitById.get(junction.unitId);
    if (!unit) continue;
    const anchors = junctionAnchors(unit);
    if (!anchors) continue;

    out.push({
      id: `fj-${junction.unitId}`,
      source: anchors.source,
      target: anchors.target,
      type: FAMILY_JUNCTION_EDGE_TYPE,
      data: { junction },
      ...INERT_EDGE,
    });
  }

  return out;
}

/**
 * Cạnh không vừa khuôn cây gia đình — tái hôn, con nuôi đến từ chi khác, kế tự. Vẽ theo
 * `waypoints` đã tính sẵn, nét riêng để không lẫn với xương sống của cây.
 */
export function toAuxiliaryEdges(links: readonly AuxiliaryLink[]): AuxiliaryLinkFlowEdge[] {
  return links
    .filter((link) => link.waypoints.length >= 2)
    .map((link) => ({
      id: `aux-${link.id}`,
      source: link.sourceId,
      target: link.targetId,
      type: AUXILIARY_LINK_EDGE_TYPE,
      data: { link },
      ...INERT_EDGE,
    }));
}

/** Quy ước nét cho từng loại đường vẽ vòng. */
export function auxiliaryStroke(kind: AuxiliaryLink["kind"]): StrokeStyle {
  if (kind === "HEIR") return TREE_STROKES.heir;
  if (kind === "REMARRIAGE") return TREE_STROKES.detourMarriage;
  return TREE_STROKES.detour;
}

/**
 * Những người được backend gắn nhãn tuyệt tự. Đọc thẳng `badges` chứ KHÔNG suy ra từ
 * `childCount === 0`: một người chưa tải hết hậu duệ, hoặc bị lọc mất con vì phân tầng riêng tư,
 * cũng có childCount 0 — khép một gạch tuyệt tự dưới thẻ họ là nói sai về phả.
 */
export function lineageEndPersonIds(nodes: readonly TreeNode[]): string[] {
  return nodes.filter((n) => (n.badges ?? []).includes("TUYET_TU")).map((n) => n.id);
}
