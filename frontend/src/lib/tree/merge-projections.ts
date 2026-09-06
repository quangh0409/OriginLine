import type { TreeEdge, TreeNode, TreeProjection } from "@/types/api";

/**
 * Combines however many `/tree` responses the canvas has fetched so far (one
 * per expanded branch, per README "What Sprint 2 needs to know") into a
 * single deduped graph. The same node/edge id can legitimately appear in
 * more than one projection (e.g. a shared spouse reached from two different
 * expand actions) — last write wins, which is fine since a re-fetched node's
 * fields (like `hasMoreDescendants`) only get MORE accurate over time, never
 * contradictory.
 */
export interface MergedTree {
  nodesById: Map<string, TreeNode>;
  edgesById: Map<string, TreeEdge>;
  /** True if ANY merged fetch was cut off by maxNodes — a volume/perf signal,
   * never a privacy one (a guest's tree can have gaps without this ever
   * being true; see contracts/README §7.3). */
  truncated: boolean;
  truncatedNodeIds: Set<string>;
}

export function mergeProjections(projections: Array<TreeProjection | undefined>): MergedTree {
  const nodesById = new Map<string, TreeNode>();
  const edgesById = new Map<string, TreeEdge>();
  let truncated = false;
  const truncatedNodeIds = new Set<string>();

  for (const proj of projections) {
    if (!proj) continue;
    for (const n of proj.nodes) nodesById.set(n.id, n);
    for (const e of proj.edges) edgesById.set(e.id, e);
    if (proj.meta.truncated) {
      truncated = true;
      for (const id of proj.meta.truncatedNodeIds ?? []) truncatedNodeIds.add(id);
    }
  }

  return { nodesById, edgesById, truncated, truncatedNodeIds };
}
