"use client";

import { useCallback, useEffect, useMemo, useRef, type FocusEvent } from "react";
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
  MIN_INITIAL_ZOOM,
  prefersReducedMotion,
  withMotionPreference,
} from "@/lib/tree/fit-view";
import { NODE_HEIGHT, NODE_WIDTH } from "@/lib/tree/layout-constants";
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
  /**
   * Người vừa được nhảy tới. Máy quay bay về đúng tấm thẻ của họ khi giá trị này đổi **và** khi
   * tấm thẻ ấy đã có chỗ trong bố cục — không sớm hơn.
   */
  focusId?: string | null;
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
 *  3. The camera is auto-fit once per `(rootId, viewMode)` pair (see the ref
 *     below). Expanding or collapsing a branch re-flows the graph under a
 *     STATIONARY camera — that is what "giữ nguyên vùng đang xem" (keep the
 *     current viewport) means here, and a CSS transition on
 *     `.react-flow__node` (globals.css) animates the position change instead
 *     of snapping. Switching VIEW MODE is deliberately excluded: the three
 *     modes do not share a coordinate space (radial centres the root at the
 *     origin; the other two grow right and down from the top-left corner), so
 *     a stationary camera there means looking at empty coordinates — measured
 *     in the browser as literally zero cards in frame.
 */
export function TreeCanvasInner({
  rootId,
  nodes,
  edges,
  viewMode,
  focusId = null,
  onSelectPerson,
}: TreeCanvasInnerProps) {
  const { fitView, setCenter, getZoom } = useReactFlow();
  const lastFitRootRef = useRef<string | null>(null);
  const lastCenteredFocusRef = useRef<string | null>(null);

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
    // Khoá canh khung gồm CẢ chế độ xem, không chỉ gốc.
    //
    // Ba chế độ dùng ba hệ toạ độ khác hẳn nhau: Phân cấp và Ma trận đời neo vào góc trên-trái và
    // trải sang phải, còn Toả tròn lấy GỐC làm tâm và trải ra bốn phía quanh điểm (0, 0). Giữ
    // nguyên máy quay khi đổi giữa hai họ ấy nghĩa là nhìn vào một vùng toạ độ không còn ai đứng —
    // đo được trên trình duyệt: bấm "Toả tròn" xong canvas còn **0 tấm thẻ** trong khung, trong
    // khi thanh công cụ vẫn báo đủ số người. Người dùng đọc ra là "bấm xong mất hết cây".
    //
    // "Giữ nguyên vùng đang xem" vẫn đúng ở chỗ nó sinh ra để phục vụ: **bung hoặc thu một nhánh**
    // — lúc ấy bố cục dịch đi trong cùng một hệ toạ độ, và giữ máy quay đứng yên là điều đúng.
    const fitKey = `${rootId}:${viewMode}`;
    if (lastFitRootRef.current === fitKey) return;
    const raf = requestAnimationFrame(() => {
      lastFitRootRef.current = fitKey;
      // Hỏi nguyện vọng giảm chuyển động NGAY LÚC canh khung, không phải lúc dựng module: người
      // dùng có thể bật "giảm chuyển động" trong hệ điều hành khi tab đang mở.
      fitView(withMotionPreference(INITIAL_FIT_VIEW_OPTIONS));
    });
    return () => cancelAnimationFrame(raf);
  }, [rootId, viewMode, fitView]);

  /**
   * Đưa máy quay về đúng tấm thẻ vừa được nhảy tới.
   *
   * <p>Ba điều đáng ghi lại:</p>
   *
   * <ol>
   *   <li><b>Chờ tấm thẻ có chỗ trong bố cục.</b> Cú nhảy kéo theo một lượt gọi mạng; trong lúc
   *       chờ thì `positions` chưa có id ấy. Bay tới một toạ độ chưa tồn tại là bay về gốc toạ độ,
   *       tức một canvas trắng. `positions` nằm trong danh sách phụ thuộc nên effect tự chạy lại
   *       khi bố cục đã dựng xong.</li>
   *   <li><b>Không thu nhỏ thêm.</b> `Math.max(getZoom(), MIN_INITIAL_ZOOM)` — người dùng đang
   *       phóng to để đọc thì giữ nguyên mức ấy; đang thu nhỏ dưới sàn dễ đọc thì kéo lên sàn.
   *       Cú nhảy là để ĐỌC ĐƯỢC một cái tên, không phải để ngắm toàn cảnh.</li>
   *   <li><b>Tôn trọng nguyện vọng giảm chuyển động</b>, y như hai phép canh khung kia: đây cũng
   *       là một cú trượt-phóng cả mặt phẳng.</li>
   * </ol>
   */
  useEffect(() => {
    if (!focusId || lastCenteredFocusRef.current === focusId) return;
    const position = positions.get(focusId);
    if (!position) return;
    lastCenteredFocusRef.current = focusId;
    setCenter(position.x + NODE_WIDTH / 2, position.y + NODE_HEIGHT / 2, {
      zoom: Math.max(getZoom(), MIN_INITIAL_ZOOM),
      duration: prefersReducedMotion() ? 0 : 400,
    });
  }, [focusId, positions, setCenter, getZoom]);

  /**
   * **Tiêu điểm bàn phím kéo tấm thẻ vào khung.**
   *
   * React Flow cho mỗi thẻ nhận tiêu điểm được, nhưng nó **không** đưa thẻ ấy vào khung nhìn. Hệ
   * quả đo được: đi bằng Tab trên màn 1440×900, tiêu điểm nhảy sang một tấm thẻ nằm ở x = −111 —
   * ngoài mép trái cửa sổ. Người dùng bàn phím mất dấu hoàn toàn, và phép kiểm C-4.4 báo "phần tử
   * được tiêu điểm bị lớp khác che khuất" vì `elementFromPoint` ở tâm thẻ trả về `null`.
   *
   * <h2>Chỉ kéo khi thẻ THẬT SỰ không nhìn thấy</h2>
   * Bấm chuột vào một tấm thẻ cũng đặt tiêu điểm lên nó. Nếu cứ có tiêu điểm là dời máy quay thì
   * mỗi cú bấm lại giật cả phả đồ một cái — đúng kiểu "giao diện tự ý di chuyển" mà người lớn
   * tuổi khó theo nhất. Nên phép dời chỉ chạy khi tấm thẻ nằm ngoài (hoặc thò ra khỏi) khung.
   */
  const handleFocusWithin = useCallback(
    (event: FocusEvent<HTMLDivElement>) => {
      const card = (event.target as HTMLElement | null)?.closest?.(".react-flow__node");
      const id = card?.getAttribute("data-id");
      if (!id) return;
      const position = positions.get(id);
      if (!position) return;

      const canvas = card!.closest(".react-flow")?.getBoundingClientRect();
      const rect = card!.getBoundingClientRect();
      if (
        canvas &&
        rect.left >= canvas.left &&
        rect.right <= canvas.right &&
        rect.top >= canvas.top &&
        rect.bottom <= canvas.bottom
      ) {
        return;
      }

      // `duration: 0` — TỨC THÌ, không hoạt ảnh, và đây là một quyết định chứ không phải bỏ sót.
      //
      // Hai lý do. Một: người đi bằng Tab đi NHANH; một cú bay 200ms nghĩa là ở nhịp Tab kế tiếp
      // máy quay vẫn đang trên đường tới tấm thẻ trước — vòng tiêu điểm chạy đuổi theo một mục
      // tiêu đang di chuyển. Đo được: phép kiểm C-4.4 vẫn báo "bị che khuất" vì nó đọc vị trí
      // ngay sau phím Tab, lúc thẻ còn chưa tới nơi. Hai: đây là chuyển động KHÔNG do người dùng
      // yêu cầu, tức đúng loại mà `prefers-reduced-motion` nhắm tới — cho nó bằng 0 với mọi người
      // thì không phải nhớ hỏi nguyện vọng ở một chỗ nữa.
      setCenter(position.x + NODE_WIDTH / 2, position.y + NODE_HEIGHT / 2, {
        zoom: getZoom(),
        duration: 0,
      });
    },
    [positions, setCenter, getZoom]
  );

  return (
    // `onFocus` ở đây bắt cả tiêu điểm của thẻ lẫn của nút bung bên trong nó: sự kiện tiêu điểm
    // nổi bọt trong hệ thống sự kiện của React, nên một chỗ nghe là đủ cho hàng nghìn thẻ — thêm
    // một trình nghe vào từng thẻ là đúng thứ đã làm mọi phép tối ưu hoá của React Flow vô nghĩa.
    <div className="h-full w-full" onFocus={handleFocusWithin}>
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
    </div>
  );
}
