import dagre from "dagre";
import type { TreeEdge, TreeNode } from "@/types/api";
import { HIERARCHICAL_NODE_SEP, HIERARCHICAL_RANK_SEP, NODE_HEIGHT, NODE_WIDTH } from "./layout-constants";

export interface NodePosition {
  x: number;
  y: number;
}

/**
 * Traditional top-down phả đồ layout. `dagre` ranks nodes by longest path,
 * which naturally matches `TreeNode.depth` (generation) for PARENT_BIO/
 * PARENT_ADOPT edges. SPOUSE edges are fed in too with `minlen: 0` so dagre
 * tries to keep a couple on the same rank and close together — dagre has no
 * real concept of "this pair is a family unit", so this is a heuristic, not
 * an exact placement guarantee; occasional visual crowding around large
 * polygamous families (spouseOrder 2/3) is a known limitation, not a bug.
 */
export function layoutHierarchical(nodes: TreeNode[], edges: TreeEdge[]): Map<string, NodePosition> {
  const g = new dagre.graphlib.Graph();
  g.setGraph({
    rankdir: "TB",
    nodesep: HIERARCHICAL_NODE_SEP,
    ranksep: HIERARCHICAL_RANK_SEP,
    marginx: 24,
    marginy: 24,
  });
  g.setDefaultEdgeLabel(() => ({}));

  for (const n of nodes) {
    g.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  }
  for (const e of edges) {
    if (!g.hasNode(e.source) || !g.hasNode(e.target)) continue;
    if (e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT") {
      g.setEdge(e.source, e.target);
    } else if (e.relType === "SPOUSE") {
      g.setEdge(e.source, e.target, { minlen: 0, weight: 6 });
    }
  }

  dagre.layout(g);

  const positions = new Map<string, NodePosition>();
  for (const n of nodes) {
    const gn = g.node(n.id);
    positions.set(n.id, { x: gn?.x ?? 0, y: gn?.y ?? 0 });
  }

  alignSpouses(nodes, edges, positions);
  spreadOverlappingRows(positions);
  return positions;
}

/**
 * Kéo vợ/chồng về đúng hàng của bạn đời.
 *
 * <p>{@code minlen: 0, weight: 6} ở trên là <b>gợi ý</b>, không phải bảo đảm: dagre 0.8.5 vẫn xếp
 * hai người thành hai hàng cách nhau một bậc. Trên phả đồ thì bà tổ bị vẽ thấp hơn ông tổ nửa thế
 * hệ — nhìn là thấy sai ngay, và sai theo kiểu khiến người trong họ mất tin vào cả sơ đồ. Chế độ
 * ma trận không dính vì nó ghim hàng theo {@code depth}.</p>
 *
 * <p>Neo là người có {@code y} nhỏ hơn (ở trên) trong mỗi cặp, rồi lan truyền cho tới khi ổn định
 * — cần lặp vì đa thê tạo chuỗi nhiều người nối nhau qua chung một người chồng. Chỉ đổi {@code y},
 * giữ nguyên {@code x} để không phá thứ tự trái–phải mà dagre đã sắp.</p>
 */
function alignSpouses(
  nodes: { id: string }[],
  edges: { source: string; target: string; relType: string }[],
  positions: Map<string, NodePosition>
): void {
  const known = new Set(nodes.map((n) => n.id));
  const spouseEdges = edges.filter(
    (e) => e.relType === "SPOUSE" && known.has(e.source) && known.has(e.target)
  );
  if (spouseEdges.length === 0) return;

  // Trần lặp: chuỗi vợ/chồng dài nhất không thể vượt quá số cạnh, nên đây là chốt chặn an toàn
  // chứ không phải giới hạn nghiệp vụ.
  for (let pass = 0; pass <= spouseEdges.length; pass++) {
    let changed = false;
    for (const e of spouseEdges) {
      const a = positions.get(e.source);
      const b = positions.get(e.target);
      if (!a || !b || a.y === b.y) continue;
      const top = Math.min(a.y, b.y);
      positions.set(e.source, { x: a.x, y: top });
      positions.set(e.target, { x: b.x, y: top });
      changed = true;
    }
    if (!changed) return;
  }
}

/**
 * Khoảng cách tâm–tâm tối thiểu giữa hai thẻ nằm cùng một hàng. Đúng bằng thứ dagre tự dùng
 * ({@code nodesep} cộng bề rộng thẻ), nên hàng nào dagre đã xếp đúng thì hàm dưới không đụng tới.
 */
const MIN_CENTER_GAP = NODE_WIDTH + HIERARCHICAL_NODE_SEP;

/**
 * Giãn các thẻ bị chồng lên nhau trên cùng một hàng.
 *
 * <p>Bắt buộc phải chạy SAU {@link alignSpouses}. Kéo một người vợ lên hàng của chồng chỉ sửa
 * {@code y}, trong khi {@code x} thì giữ nguyên chỗ dagre đã xếp ở hàng DƯỚI — mà cạnh SPOUSE lại
 * kéo hai vợ chồng về gần nhau theo chiều ngang, nên sau khi nâng lên hai thẻ nằm gần như đè khít
 * lên nhau. Trên phả đồ thật của bộ dữ liệu giả, 16 trên 31 nút bị chồng, trong đó thẻ bà tổ phủ
 * kín thẻ Thủy Tổ: không bấm được vào nút mở rộng, không mở được hồ sơ, và trên điện thoại thì
 * nhìn như canvas bị lỗi.</p>
 *
 * <p>Cách làm: gom theo {@code y}, sắp theo {@code x} (hoà thì theo id để kết quả tất định), đẩy
 * sang phải cho đủ khoảng cách, rồi dời cả hàng lại để tâm hàng không đổi — như vậy hàng vẫn cân
 * so với đời trên và đời dưới, các nét nối không bị lệch hẳn sang một bên. Chỉ đổi {@code x};
 * {@code y} là đời, không được phép suy suyển.</p>
 */
function spreadOverlappingRows(positions: Map<string, NodePosition>): void {
  const rows = new Map<number, string[]>();
  for (const [id, p] of positions) {
    const row = rows.get(p.y);
    if (row) row.push(id);
    else rows.set(p.y, [id]);
  }

  for (const ids of rows.values()) {
    if (ids.length < 2) continue;

    ids.sort((a, b) => {
      const dx = positions.get(a)!.x - positions.get(b)!.x;
      return dx !== 0 ? dx : a.localeCompare(b);
    });

    const before = ids.reduce((sum, id) => sum + positions.get(id)!.x, 0) / ids.length;

    let previous = positions.get(ids[0]!)!.x;
    for (let i = 1; i < ids.length; i++) {
      const current = positions.get(ids[i]!)!;
      // Trừ hao 0.5px cho sai số dấu phẩy động của dagre, tránh đẩy oan một hàng vốn đã đúng.
      const x = current.x - previous < MIN_CENTER_GAP - 0.5 ? previous + MIN_CENTER_GAP : current.x;
      positions.set(ids[i]!, { x, y: current.y });
      previous = x;
    }

    const after = ids.reduce((sum, id) => sum + positions.get(id)!.x, 0) / ids.length;
    const shift = before - after;
    if (shift === 0) continue;
    for (const id of ids) {
      const p = positions.get(id)!;
      positions.set(id, { x: p.x + shift, y: p.y });
    }
  }
}
