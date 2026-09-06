import type { TreeEdge, TreeNode } from "@/types/api";
import { layoutHierarchical, type NodePosition } from "./layout-hierarchical";
import { MATRIX_COL_GAP, MATRIX_ROW_HEIGHT, NODE_WIDTH } from "./layout-constants";

/**
 * "Ma trận thế hệ" — every generation pinned to its own strict horizontal
 * row (unlike the hierarchical view, where dagre can put same-generation
 * nodes at slightly different y if a spouse edge pulls rank). Column order
 * within a row reuses dagre's crossing-minimized x-order from the
 * hierarchical layout rather than re-deriving one from scratch — dagre
 * already solved "keep related people near each other", we just re-flatten
 * its y-axis into fixed generation bands and snap x to a column grid.
 *
 * `TreeNode.depth` can be negative (ANCESTORS/BOTH direction) — rows are
 * ordered by depth value directly, so ancestor generations naturally land
 * above depth 0 without any special-casing here.
 */
export function layoutMatrix(nodes: TreeNode[], edges: TreeEdge[]): Map<string, NodePosition> {
  const hierPositions = layoutHierarchical(nodes, edges);

  const byDepth = new Map<number, TreeNode[]>();
  for (const n of nodes) {
    if (!byDepth.has(n.depth)) byDepth.set(n.depth, []);
    byDepth.get(n.depth)!.push(n);
  }

  const positions = new Map<string, NodePosition>();
  const colWidth = NODE_WIDTH + MATRIX_COL_GAP;
  const sortedDepths = [...byDepth.keys()].sort((a, b) => a - b);

  for (const depth of sortedDepths) {
    const row = byDepth.get(depth)!;
    row.sort((a, b) => (hierPositions.get(a.id)?.x ?? 0) - (hierPositions.get(b.id)?.x ?? 0));
    row.forEach((n, i) => {
      positions.set(n.id, { x: i * colWidth, y: depth * MATRIX_ROW_HEIGHT });
    });
  }

  return positions;
}
