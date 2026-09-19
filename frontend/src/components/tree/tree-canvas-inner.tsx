"use client";

import { useEffect, useMemo, useRef } from "react";
import { Background, Controls, MiniMap, ReactFlow, useReactFlow } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import treeCanvas from "./tree-canvas.module.css";
import { PersonNode } from "./person-node";
import { familyEdgeTypes, LineageEndMarkLayer } from "./family-edges";
import {
  lineageEndPersonIds,
  toAuxiliaryEdges,
  toFlowEdges,
  toFlowNodes,
  toJunctionEdges,
  type PersonNodeData,
} from "@/lib/tree/to-flow-elements";
import { buildFamilyUnits } from "@/lib/tree/family-units";
import { layoutFamily } from "@/lib/tree/layout-family";
import {
  CANVAS_MAX_ZOOM,
  CANVAS_MIN_ZOOM,
  INITIAL_FIT_VIEW_OPTIONS,
  withMotionPreference,
} from "@/lib/tree/fit-view";
import { layoutHierarchical } from "@/lib/tree/layout-hierarchical";
import { layoutMatrix } from "@/lib/tree/layout-matrix";
import { layoutRadial } from "@/lib/tree/layout-radial";
import { colorVars } from "@/styles/tokens";
import type { TreeEdge, TreeNode } from "@/types/api";
import type { TreeViewMode } from "@/types/tree-ui";

const nodeTypes = { person: PersonNode };

export interface TreeCanvasInnerProps {
  rootId: string;
  nodes: TreeNode[];
  edges: TreeEdge[];
  viewMode: TreeViewMode;
  /** Node tap/click -> open the F3 profile panel. Expand/collapse is unaffected:
   * the toggle button in PersonNode calls stopPropagation, so it never selects. */
  onSelectPerson?: (personId: string) => void;
}

/**
 * The actual `<ReactFlow>` mount. Perf-relevant choices, in order of
 * measured impact (see frontend README perf notes for numbers):
 *  1. `onlyRenderVisibleElements` — React Flow culls nodes/edges outside the
 *     current viewport from the DOM, on top of our own structural
 *     virtualization (collapsed branches never even reach this component —
 *     see useTreeCanvas/computeVisibleSubgraph).
 *  2. Layout is computed once per (nodes, edges, viewMode) via useMemo, not
 *     per render — dagre/d3 layout cost dominates at a few thousand nodes,
 *     re-running it on every pan/zoom would be the actual perf cliff, not
 *     React Flow's own rendering.
 *  3. The camera is only auto-fit the first time a given `rootId` renders
 *     (see the ref below) — switching view mode or expanding a branch
 *     re-flows the graph under a STATIONARY camera, which is what "giữ
 *     nguyên vùng đang xem" (keep the current viewport) means here; a CSS
 *     transition on `.react-flow__node` (globals.css) animates the position
 *     change instead of snapping.
 */
export function TreeCanvasInner({
  rootId,
  nodes,
  edges,
  viewMode,
  onSelectPerson,
}: TreeCanvasInnerProps) {
  const { fitView } = useReactFlow();
  const lastFitRootRef = useRef<string | null>(null);

  // Chế độ Phân cấp dựng theo ĐƠN VỊ GIA ĐÌNH; hai chế độ kia giữ nguyên lối cũ.
  //
  // Toả tròn là các nan quạt và Ma trận đời ghim hàng theo đời — cả hai không có khái niệm "cặp vợ
  // chồng đứng cạnh nhau" nên không dùng được thanh hôn phối. Chúng vẫn nối người–người qua
  // `toFlowEdges`, vốn đã bỏ neo trái–phải cố định nên cũng hết bị thẻ che.
  const family = useMemo(() => {
    if (viewMode !== "hierarchical") return null;
    const units = buildFamilyUnits(nodes, edges);
    return { units, layout: layoutFamily(nodes, units, edges) };
  }, [nodes, edges, viewMode]);

  const positions = useMemo(() => {
    if (family) return family.layout.positions;
    if (viewMode === "radial") return layoutRadial(nodes, edges, rootId);
    if (viewMode === "matrix") return layoutMatrix(nodes, edges);
    return layoutHierarchical(nodes, edges);
  }, [family, nodes, edges, viewMode, rootId]);

  const flowNodes = useMemo(() => toFlowNodes(nodes, positions), [nodes, positions]);

  const flowEdges = useMemo(() => {
    if (!family) return toFlowEdges(edges);
    // Xương sống (thanh hôn phối + thanh anh em + đoạn xuống con) và các cạnh không vừa khuôn
    // (tái hôn, cha/mẹ từ chi khác, kế tự). Không cạnh nào được rơi rụng — người tái hôn thuộc hai
    // đơn vị cùng lúc, đánh mất một đơn vị là làm bà hai của cụ tổ biến mất khỏi phả.
    return [
      ...toJunctionEdges(family.layout.junctions, family.units),
      ...toAuxiliaryEdges(family.layout.auxiliaryLinks),
    ];
  }, [family, edges]);

  const lineageEndIds = useMemo(
    () => (family ? lineageEndPersonIds(nodes) : []),
    [family, nodes]
  );

  // Đánh dấu "đã fit" BÊN TRONG rAF, không phải trước nó.
  //
  // `reactStrictMode: true` (next.config.js) khiến effect chạy hai lượt ở môi trường phát triển.
  // Bản trước gán ref rồi mới lên lịch rAF: lượt 1 gán ref và đặt lịch, cleanup huỷ ngay rAF đó;
  // lượt 2 thấy ref đã khớp nên return sớm — kết quả là fitView KHÔNG BAO GIỜ chạy khi dev.
  // Trên máy để bàn còn may nhờ khung nhìn rộng nên vẫn thấy vài thẻ; trên điện thoại thì canvas
  // mở ra trắng trơn trong khi thanh công cụ vẫn báo "47 nhân khẩu đang hiển thị".
  useEffect(() => {
    if (lastFitRootRef.current === rootId) return;
    const raf = requestAnimationFrame(() => {
      lastFitRootRef.current = rootId;
      // Hỏi nguyện vọng giảm chuyển động NGAY LÚC canh khung, không phải lúc dựng module: người
      // dùng có thể bật "giảm chuyển động" trong hệ điều hành khi tab đang mở.
      fitView(withMotionPreference(INITIAL_FIT_VIEW_OPTIONS));
    });
    return () => cancelAnimationFrame(raf);
  }, [rootId, fitView]);

  return (
    <ReactFlow
      // Lớp này mang hai bản vá cho CSS của thư viện: cỡ nút điều khiển, và vòng tiêu điểm bàn
      // phím trên thẻ nhân khẩu (mặc định của React Flow chỉ đạt 1,07:1 trên nền kem).
      className={treeCanvas.canvas}
      nodes={flowNodes}
      edges={flowEdges}
      nodeTypes={nodeTypes}
      edgeTypes={familyEdgeTypes}
      onlyRenderVisibleElements
      onNodeClick={(_, node) => onSelectPerson?.(node.id)}
      minZoom={CANVAS_MIN_ZOOM}
      maxZoom={CANVAS_MAX_ZOOM}
      proOptions={{ hideAttribution: true }}
      defaultEdgeOptions={{ focusable: false }}
    >
      <Background gap={24} color={colorVars.border} />
      {/* Gạch khép nhánh cho người tuyệt tự. Vẽ thành một lớp riêng chứ không nhét vào từng thẻ:
          nó thuộc về hệ toạ độ canvas, phải trượt và phóng cùng cây. */}
      <LineageEndMarkLayer positions={positions} personIds={lineageEndIds} />
      {/* Ba lớp phủ phải nằm ở ba góc khác nhau, nếu không sẽ có cái bị che và không bấm được:
          Controls trên-phải · MiniMap dưới-phải (mặc định) · TreeLegend dưới-trái.
          Trước đây Controls dùng mặc định (dưới-trái) và bị <TreeLegend> phủ kín — legend là
          `!absolute !bottom-3 !left-3 !z-10`, cao hơn z-index 5 của Controls. Chuột lăn vẫn zoom
          được nên lỗi lọt qua mọi lần thử trên máy để bàn, nhưng người dùng cảm ứng thì mất hẳn
          đường phóng to / thu nhỏ / vừa khung. */}
      {/* Nút phóng to / thu nhỏ / vừa khung của React Flow mặc định là 26×26px — không bấm nổi
          trên điện thoại. Nới lên 44×44px trong tree-canvas.module.css (ở đó có ghi vì sao phải là
          CSS Module chứ không phải một lớp Tailwind). */}
      <Controls showInteractive={false} position="top-right" className={treeCanvas.controls} />
      {/* Bản đồ thu nhỏ chỉ có ích trên màn hình rộng. Trên Pixel 5 nó là một khối 200x150 chiếm
          17% canvas (391x453), lại còn chồng lên thẻ chú giải ở góc dưới-trái, và vì nó nhận sự
          kiện chuột nên nó nuốt luôn thao tác chạm vào những thẻ nhân khẩu nằm dưới. Ẩn dưới `md`:
          trên điện thoại người dùng điều hướng bằng cử chỉ vuốt/chụm, không bằng bản đồ tí hon. */}
      <MiniMap
        pannable
        zoomable
        className="!hidden !bg-bg-card md:!block"
        maskColor="rgb(var(--rgb-primary) / 0.08)"
        nodeColor={(n) => {
          const data = n.data as PersonNodeData | undefined;
          return data?.treeNode.person.isAlive ? colorVars.primary : colorVars.borderDark;
        }}
      />
    </ReactFlow>
  );
}
