import { hierarchy, tree as d3tree } from "d3-hierarchy";
import type { TreeEdge, TreeNode } from "@/types/api";
import type { NodePosition } from "./layout-hierarchical";
import { NODE_WIDTH, RADIAL_RADIUS_STEP } from "./layout-constants";

interface RadialTreeDatum {
  id: string;
  children: RadialTreeDatum[];
}

/**
 * "Tỏa tròn" — thủy tổ (or whichever node is the canvas's current rootId) at
 * the center, generations radiating outward as concentric rings.
 *
 * d3-hierarchy needs a strict single-parent tree, but the genealogy graph
 * isn't one (multiple parents via adoption, spouses, remarriage). We
 * collapse it to a spanning tree first: each non-root node's radial parent
 * is the FIRST neighbor found (via BFS from root) one ring further in —
 * for `depth >= 0` that means a PARENT_BIO/PARENT_ADOPT child edge, for
 * `depth < 0` (ANCESTORS direction) it means walking a parent edge upward.
 * A node with two qualifying parents (e.g. adopted + biological both
 * present) simply attaches to whichever is discovered first — this is a
 * DISPLAY simplification for this one view mode, not a change to the
 * underlying relationship data, and never affects hierarchical/matrix mode
 * or any kinship computation (which never happens client-side regardless).
 * Married-in spouses with no radial-tree parent of their own are placed as
 * a satellite just outside their partner's position instead of getting a
 * ring slot.
 */
export function layoutRadial(nodes: TreeNode[], edges: TreeEdge[], rootId: string): Map<string, NodePosition> {
  const positions = new Map<string, NodePosition>();
  const nodesById = new Map(nodes.map((n) => [n.id, n]));
  if (!nodesById.has(rootId)) return positions;

  const descentEdges = edges.filter((e) => e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT");
  const childrenOf = new Map<string, string[]>();
  const parentsOf = new Map<string, string[]>();
  for (const e of descentEdges) {
    if (!childrenOf.has(e.source)) childrenOf.set(e.source, []);
    childrenOf.get(e.source)!.push(e.target);
    if (!parentsOf.has(e.target)) parentsOf.set(e.target, []);
    parentsOf.get(e.target)!.push(e.source);
  }

  function outwardNeighbors(id: string): string[] {
    const node = nodesById.get(id)!;
    const isRoot = id === rootId;
    const descendants = (childrenOf.get(id) ?? []).filter(
      (c) => nodesById.has(c) && nodesById.get(c)!.depth === node.depth + 1
    );
    const ancestors = (parentsOf.get(id) ?? []).filter(
      (p) => nodesById.has(p) && nodesById.get(p)!.depth === node.depth - 1
    );
    if (isRoot) return [...descendants, ...ancestors];
    return node.depth >= 0 ? descendants : ancestors;
  }

  const visited = new Set<string>([rootId]);
  function buildSubtree(id: string): RadialTreeDatum {
    const kids: RadialTreeDatum[] = [];
    for (const neighborId of outwardNeighbors(id)) {
      if (visited.has(neighborId)) continue;
      visited.add(neighborId);
      kids.push(buildSubtree(neighborId));
    }
    return { id, children: kids };
  }

  const root = hierarchy(buildSubtree(rootId));
  const layout = d3tree<RadialTreeDatum>().size([2 * Math.PI, 1]).separation((a, b) => {
    if (a.depth === 0) return 1;
    return (a.parent === b.parent ? 1 : 2) / a.depth;
  });
  layout(root);

  root.each((d) => {
    const radius = d.depth * RADIAL_RADIUS_STEP;
    // d3's `x` is the angle in a radial layout. It is typed optional because
    // d3-hierarchy only assigns it once a layout has run — which it has, on
    // the line above — so an unlaid node collapses harmlessly onto angle 0.
    const angle = (d.x ?? 0) - Math.PI / 2;
    positions.set(d.data.id, {
      x: radius * Math.cos(angle),
      y: radius * Math.sin(angle),
    });
  });

  // Satellite placement for spouses that never got a ring slot (didn't
  // continue the tree in this direction — the common case for a married-in
  // dâu/rể).
  const spouseSatelliteOffset = NODE_WIDTH * 0.7;
  for (const e of edges) {
    if (e.relType !== "SPOUSE") continue;
    const [a, b] = [e.source, e.target];
    const aPos = positions.get(a);
    const bPos = positions.get(b);
    if (aPos && !bPos && nodesById.has(b)) {
      positions.set(b, { x: aPos.x + spouseSatelliteOffset, y: aPos.y });
    } else if (bPos && !aPos && nodesById.has(a)) {
      positions.set(a, { x: bPos.x + spouseSatelliteOffset, y: bPos.y });
    }
  }

  return positions;
}
