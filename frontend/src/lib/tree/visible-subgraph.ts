import type { TreeEdge, TreeNode } from "@/types/api";

export interface VisibleSubgraph {
  nodes: TreeNode[];
  edges: TreeEdge[];
}

/**
 * This is the actual "never render the whole tree eagerly" mechanism (F2
 * brief): a node only enters the rendered graph if every ancestor on its
 * path from `rootId` is currently expanded. Collapsing a branch doesn't
 * discard its fetched data (React Query cache stays warm — re-expanding is
 * instant), it just removes those nodes/edges from what we hand to React
 * Flow, so a collapsed subtree costs zero DOM/SVG regardless of how many
 * thousands of descendants it has.
 *
 * Spouses are a special case: `SPOUSE` edges aren't gated by "is the couple
 * node expanded" (a spouse is not a descendant to lazily reveal), they
 * simply follow whichever partner is already visible — propagated to a
 * fixpoint since a chain of remarriages could in principle need more than
 * one hop.
 */
export function computeVisibleSubgraph(
  nodesById: Map<string, TreeNode>,
  edgesById: Map<string, TreeEdge>,
  rootId: string,
  expandedIds: ReadonlySet<string>
): VisibleSubgraph {
  if (!nodesById.has(rootId)) return { nodes: [], edges: [] };

  const descentOut = new Map<string, TreeEdge[]>();
  // Chiều NGƯỢC của cạnh huyết thống: con -> cha/mẹ.
  //
  // Thiếu bản đồ này thì phép duyệt chỉ đi xuống được, nên mọi node có `depth` ÂM — tức đúng
  // những gì `useTreeCanvas` nạp về khi người dùng chọn "Tổ tiên" hoặc "Cả hai chiều" trên
  // <TreeToolbar> — không bao giờ hiện ra. Triệu chứng: bấm "Tổ tiên" xong chỉ thấy chính mình
  // và vợ/chồng, còn dữ liệu tổ tiên đã tải về thì nằm im.
  const descentIn = new Map<string, TreeEdge[]>();
  const spouseEdges: TreeEdge[] = [];
  for (const e of edgesById.values()) {
    if (e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT") {
      if (!descentOut.has(e.source)) descentOut.set(e.source, []);
      descentOut.get(e.source)!.push(e);
      if (!descentIn.has(e.target)) descentIn.set(e.target, []);
      descentIn.get(e.target)!.push(e);
    } else if (e.relType === "SPOUSE") {
      spouseEdges.push(e);
    }
  }

  const visible = new Set<string>([rootId]);
  const queue: string[] = [rootId];
  while (queue.length > 0) {
    const id = queue.shift()!;
    if (!expandedIds.has(id)) continue;
    for (const e of descentOut.get(id) ?? []) {
      if (nodesById.has(e.target) && !visible.has(e.target)) {
        visible.add(e.target);
        queue.push(e.target);
      }
    }
  }

  // Đi LÊN tổ tiên, tách hẳn khỏi vòng lặp đi xuống và KHÔNG xét `expandedIds`.
  //
  // Nút thu gọn/mở rộng có nghĩa "giấu bớt con cháu của người này" — nó không nói gì về tổ tiên.
  // Gộp chung vào vòng lặp trên thì phép duyệt dừng ngay ở đời cha (vì cha không nằm trong
  // `expandedIds`), và chế độ "Tổ tiên" chỉ hiện được đúng một đời.
  //
  // Chỉ nhận người ĐÃ có trong projection: đây là bước lọc hiển thị, việc nạp thêm dữ liệu thuộc
  // về useTreeCanvas.
  const upward: string[] = [rootId];
  while (upward.length > 0) {
    const id = upward.shift()!;
    for (const e of descentIn.get(id) ?? []) {
      if (nodesById.has(e.source) && !visible.has(e.source)) {
        visible.add(e.source);
        upward.push(e.source);
      }
    }
  }

  let changed = true;
  while (changed) {
    changed = false;
    for (const e of spouseEdges) {
      if (visible.has(e.source) && nodesById.has(e.target) && !visible.has(e.target)) {
        visible.add(e.target);
        changed = true;
      }
      if (visible.has(e.target) && nodesById.has(e.source) && !visible.has(e.source)) {
        visible.add(e.source);
        changed = true;
      }
    }
  }

  const nodes = [...visible]
    .map((id) => nodesById.get(id))
    .filter((n): n is TreeNode => Boolean(n));
  const edges = [...edgesById.values()].filter(
    (e) => visible.has(e.source) && visible.has(e.target)
  );
  return { nodes, edges };
}
