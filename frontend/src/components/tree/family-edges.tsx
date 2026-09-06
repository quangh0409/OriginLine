"use client";

import type { CSSProperties } from "react";
import type { EdgeProps, EdgeTypes } from "@xyflow/react";
import { ViewportPortal } from "@xyflow/react";
import type { ChildDrop, FamilyJunction, HorizontalBar } from "@/lib/tree/family-layout-types";
import type { NodePosition } from "@/lib/tree/layout-hierarchical";
import {
  AUXILIARY_LINK_EDGE_TYPE,
  auxiliaryStroke,
  FAMILY_JUNCTION_EDGE_TYPE,
  TREE_STROKES,
  type AuxiliaryLinkFlowEdge,
  type FamilyJunctionFlowEdge,
  type StrokeStyle,
} from "@/lib/tree/to-flow-elements";
import { NODE_HEIGHT, NODE_WIDTH } from "@/lib/tree/layout-constants";

/**
 * Lớp vẽ của phả đồ dựng theo ĐƠN VỊ GIA ĐÌNH.
 *
 * Tệp này KHÔNG tính hình học. Mọi toạ độ — thanh hôn phối, thân nối, thanh anh em, đoạn rơi
 * xuống từng người con, các điểm gãy của đường vẽ vòng — đã được `layout-family.ts` tính sẵn và
 * đi vào đây qua {@link FamilyJunction} / `AuxiliaryLink`. Việc duy nhất ở đây là biến
 * những con số đó thành `<path>`, theo đúng quy ước nét trong `TREE_STROKES`.
 *
 * Nhờ tách như vậy, bố cục kiểm thử được mà không cần trình duyệt, còn tầng vẽ kiểm thử được
 * bằng cách so chuỗi `d` — không có chỗ nào để hai bên nói khác nhau.
 */

/* ==========================================================================
   Dựng chuỗi đường vẽ (thuần, không React — kiểm thử trực tiếp được)
   ========================================================================== */

/**
 * Làm tròn về tối đa 2 chữ số thập phân. Toạ độ bố cục hay ra số lẻ dài (chia đôi khe, chia đều
 * thanh anh em); giữ nguyên thì mỗi `d` phình lên vài trăm ký tự, nhân với hàng nghìn gia đình là
 * cả megabyte DOM cho một thứ không ai nhìn ra khác biệt.
 */
export function formatCoord(value: number): string {
  const rounded = Math.round(value * 100) / 100;
  return Object.is(rounded, -0) ? "0" : String(rounded);
}

/** Đoạn nằm ngang: thanh hôn phối hoặc thanh anh em. */
export function horizontalBarPath(bar: HorizontalBar): string {
  return `M ${formatCoord(bar.x1)} ${formatCoord(bar.y)} L ${formatCoord(bar.x2)} ${formatCoord(bar.y)}`;
}

/** Đoạn thẳng đứng: thân nối từ điểm nối xuống, hoặc đoạn rơi xuống một người con. */
export function verticalSegmentPath(x: number, yFrom: number, yTo: number): string {
  return `M ${formatCoord(x)} ${formatCoord(yFrom)} L ${formatCoord(x)} ${formatCoord(yTo)}`;
}

/** Đường gãy khúc theo các điểm đã tính sẵn. Dưới 2 điểm thì không có gì để vẽ. */
export function polylinePath(points: readonly { readonly x: number; readonly y: number }[]): string {
  if (points.length < 2) return "";
  return points
    .map((p, i) => `${i === 0 ? "M" : "L"} ${formatCoord(p.x)} ${formatCoord(p.y)}`)
    .join(" ");
}

function dropPath(drop: ChildDrop): string {
  return verticalSegmentPath(drop.x, drop.yFrom, drop.yTo);
}

/** Ba chuỗi `d` của một đơn vị gia đình, tách theo đúng ba quy ước nét khác nhau. */
export interface JunctionPathSet {
  /** Thân nối + thanh anh em + đoạn rơi xuống con đẻ — nét liền. */
  readonly bloodline: string;
  /** CHỈ các đoạn rơi xuống con nuôi — nét đứt. */
  readonly adopted: string;
  /** Thanh hôn phối — hổ phách; nét đứt khi hôn phối đã kết thúc. */
  readonly marriage: string;
}

/**
 * Gom các đoạn cùng quy ước nét vào chung một chuỗi `d`.
 *
 * Một gia đình 5 con cần 8 đoạn; vẽ mỗi đoạn một `<path>` thì phả đồ vài nghìn nhân khẩu sinh ra
 * hàng chục nghìn phần tử SVG. Gộp lại còn nhiều nhất ba `<path>` cho mỗi gia đình. Mẫu nét đứt
 * bắt đầu lại ở từng lệnh `M`, nên gộp không làm sai nhịp đứt của đoạn nào.
 *
 * Thanh anh em LUÔN nằm ở nhóm nét liền, kể cả khi trong nhà có con nuôi: đứt cả thanh anh em sẽ
 * khiến anh chị em ruột trông như cũng là con nuôi.
 */
export function junctionPaths(junction: FamilyJunction): JunctionPathSet {
  const solid: string[] = [];
  const dashed: string[] = [];

  if (junction.stem) {
    solid.push(verticalSegmentPath(junction.stem.x, junction.stem.yFrom, junction.stem.yTo));
  }
  if (junction.siblingBar) {
    solid.push(horizontalBarPath(junction.siblingBar));
  }
  for (const drop of junction.childDrops) {
    (drop.dashed ? dashed : solid).push(dropPath(drop));
  }

  return {
    bloodline: solid.join(" "),
    adopted: dashed.join(" "),
    marriage: junction.marriageBar ? horizontalBarPath(junction.marriageBar) : "",
  };
}

/* ==========================================================================
   Thành phần vẽ
   ========================================================================== */

/**
 * `pointer-events: none` trên mọi nét là lớp phòng vệ THỨ BA, sau `interactionWidth: 0` và
 * `selectable: false` (xem `INERT_EDGE` trong to-flow-elements.ts). Ba lớp đó là lý do đường nối
 * được đưa lên lớp bình thường mà thẻ nhân khẩu vẫn nhận đủ cú chạm của nó.
 */
const INERT_STROKE: CSSProperties = { fill: "none", pointerEvents: "none" };

function strokeProps(style: StrokeStyle): CSSProperties {
  return { ...INERT_STROKE, ...style };
}

/**
 * Một đơn vị gia đình = một cạnh React Flow.
 *
 * Bỏ qua hoàn toàn `sourceX/sourceY/targetX/targetY` mà React Flow truyền vào: đó là toạ độ suy
 * từ neo của hai thẻ đầu–cuối, còn hình cần vẽ là hình chữ H của cả gia đình. Hai đầu neo chỉ
 * dùng cho việc cắt bớt phần ngoài khung nhìn.
 */
export function FamilyJunctionEdge({ data }: EdgeProps<FamilyJunctionFlowEdge>) {
  const junction = data?.junction;
  if (!junction) return null;

  const paths = junctionPaths(junction);
  const marriageStroke = junction.ended ? TREE_STROKES.marriageEnded : TREE_STROKES.marriage;

  return (
    <g data-testid="family-junction" data-unit-id={junction.unitId} style={{ pointerEvents: "none" }}>
      {paths.marriage && (
        <path
          d={paths.marriage}
          data-testid="family-junction-marriage"
          data-ended={junction.ended ? "true" : "false"}
          style={strokeProps(marriageStroke)}
          strokeLinecap="round"
        />
      )}
      {paths.bloodline && (
        <path
          d={paths.bloodline}
          data-testid="family-junction-bloodline"
          style={strokeProps(TREE_STROKES.bioChild)}
          strokeLinecap="butt"
          // Mọi đoạn ở đây đều thẳng đứng hoặc nằm ngang, nên khử răng cưa chỉ làm nét 1.75px nhoè
          // thành hai nửa mờ khi thu nhỏ. `crispEdges` giữ nét sắc — đọc được ở cả nền kem lẫn nền tối.
          shapeRendering="crispEdges"
        />
      )}
      {paths.adopted && (
        <path
          d={paths.adopted}
          data-testid="family-junction-adopted"
          style={strokeProps(TREE_STROKES.adoptedChild)}
          strokeLinecap="butt"
          shapeRendering="crispEdges"
        />
      )}
    </g>
  );
}

/**
 * Đường vẽ vòng: tái hôn, con nuôi đến từ chi khác, kế tự.
 *
 * Cây gia đình thuần giả định mỗi người có đúng một chỗ đứng, nhưng người tái hôn thuộc hai đơn
 * vị cùng lúc. Những cạnh đó không được phép biến mất khỏi phả — chúng đi vòng theo hành lang
 * giữa hai đời, và mang nét riêng để không bị đọc nhầm là xương sống của cây.
 */
export function AuxiliaryLinkEdge({ data }: EdgeProps<AuxiliaryLinkFlowEdge>) {
  const link = data?.link;
  if (!link) return null;

  const d = polylinePath(link.waypoints);
  if (!d) return null;

  return (
    <path
      d={d}
      data-testid="family-auxiliary"
      data-link-kind={link.kind}
      style={strokeProps(auxiliaryStroke(link.kind))}
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  );
}

/** Đăng ký với `<ReactFlow edgeTypes={familyEdgeTypes}>`. */
export const familyEdgeTypes: EdgeTypes = {
  [FAMILY_JUNCTION_EDGE_TYPE]: FamilyJunctionEdge,
  [AUXILIARY_LINK_EDGE_TYPE]: AuxiliaryLinkEdge,
};

/* ==========================================================================
   Tuyệt tự — gạch khép dưới đáy thẻ
   ========================================================================== */

/** Bề ngang gạch tuyệt tự: đủ ngắn để đọc ra là "khép lại", không thành một thanh anh em giả. */
export const LINEAGE_END_MARK_WIDTH = 28;
/** Khoảng hở giữa đáy thẻ và gạch khép, để gạch không dính vào viền thẻ. */
export const LINEAGE_END_MARK_GAP = 6;

/**
 * Gạch ngang ngắn khép dưới đáy thẻ của người tuyệt tự — dòng dừng ở đây, không có đoạn rơi nào
 * đi tiếp xuống dưới.
 */
export function lineageEndMark(pos: NodePosition): HorizontalBar {
  const centerX = pos.x + NODE_WIDTH / 2;
  return {
    x1: centerX - LINEAGE_END_MARK_WIDTH / 2,
    x2: centerX + LINEAGE_END_MARK_WIDTH / 2,
    y: pos.y + NODE_HEIGHT + LINEAGE_END_MARK_GAP,
  };
}

export interface LineageEndMarksProps {
  /** id nhân khẩu → góc trên-trái của thẻ, lấy thẳng từ tầng bố cục. */
  positions: Map<string, NodePosition>;
  /** Những người backend gắn nhãn `TUYET_TU` — xem `lineageEndPersonIds`. */
  personIds: readonly string[];
}

/**
 * Nhóm `<g>` thuần, không phụ thuộc ngữ cảnh React Flow, nên kiểm thử được một mình.
 */
export function LineageEndMarks({ positions, personIds }: LineageEndMarksProps) {
  return (
    <g style={{ pointerEvents: "none" }}>
      {personIds.map((id) => {
        const pos = positions.get(id);
        if (!pos) return null;
        const bar = lineageEndMark(pos);
        return (
          <path
            key={id}
            d={horizontalBarPath(bar)}
            data-testid="tuyet-tu-mark"
            data-person-id={id}
            style={strokeProps(TREE_STROKES.lineageEnd)}
            strokeLinecap="round"
          />
        );
      })}
    </g>
  );
}

/**
 * Đặt các gạch tuyệt tự vào trong khung nhìn của React Flow để chúng trôi theo phóng/kéo.
 *
 * `<ViewportPortal>` đưa nội dung vào `.react-flow__viewport-portal`, mà thẻ này nằm SAU
 * `<NodeRenderer>` trong DOM — tức là nội dung ở đây vẽ ĐÈ lên thẻ nhân khẩu. Với gạch tuyệt tự
 * thì vô hại vì nó nằm dưới đáy thẻ, nhưng `pointer-events: none` ở đây là BẮT BUỘC chứ không
 * phải cho gọn: thiếu nó thì một lớp phủ toàn canvas sẽ nuốt mọi cú chạm vào nhân khẩu.
 */
export function LineageEndMarkLayer(props: LineageEndMarksProps) {
  if (props.personIds.length === 0) return null;

  return (
    <ViewportPortal>
      <svg
        data-testid="tuyet-tu-layer"
        style={{ position: "absolute", left: 0, top: 0, overflow: "visible", pointerEvents: "none" }}
        width={0}
        height={0}
        aria-hidden
        focusable="false"
      >
        <LineageEndMarks {...props} />
      </svg>
    </ViewportPortal>
  );
}
