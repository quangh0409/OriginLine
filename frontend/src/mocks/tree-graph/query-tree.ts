import type { PersonSummaryDto, TreeDirection, TreeEdge, TreeNode, TreeProjection } from "@/types/api";
import type { MockGraph } from "./build-graph";
import type { RawPerson } from "./generate-large-tree";

export interface QueryTreeParams {
  rootId: string;
  depth?: number; // magnitude, clamped to [1, 10] — sign comes from `direction`
  direction?: TreeDirection;
  includeSpouses?: boolean;
  maxNodes?: number; // clamped to [1, 2000]
  /** Mock stand-in for the backend's PrivacyTierFilter (W6) — guest -> false for any living person. */
  isVisible: (person: RawPerson) => boolean;
}

function toSummary(p: RawPerson): PersonSummaryDto {
  return {
    id: p.id,
    displayName: p.displayName,
    gender: p.gender,
    generation: p.generation,
    isAlive: p.isAlive,
    birthYear: p.birthYear ?? undefined,
    deathYear: p.deathYear ?? undefined,
    primaryBranch: p.primaryBranch ?? undefined,
    nativePlace: p.nativePlace ?? undefined,
  };
}

const clamp = (n: number, min: number, max: number) => Math.min(Math.max(n, min), max);

/**
 * BFS-based `/tree` resolver over the in-memory mock graph — this is the
 * mock's stand-in for the backend's AGE Cypher traversal (W7). It honors
 * every contract behavior the curated 5-node fixture in Sprint 1 could not
 * actually exercise:
 *  - real lazy loading (`hasMoreDescendants`/`childCount` reflect the FULL
 *    graph, not just what's returned — expanding a branch re-queries with
 *    `rootId` = that node's id, exactly like the real API);
 *  - `meta.truncated` fires for real once the visible node count exceeds
 *    `maxNodes`, with `truncatedNodeIds` naming which returned nodes have
 *    children cut off by the budget (as opposed to a normal depth boundary);
 *  - a guest's tree can come back with real holes: BFS keeps walking through
 *    an invisible living node to reach a visible descendant beyond it, but
 *    that living node (and any edge touching it) is dropped from the
 *    response — contracts/README §7.3's "cây có thể đứt đoạn một cách hợp
 *    lệ", not a data bug.
 *
 * `parentIds`/`spouseIds` are filtered to visible ids only — unlike the
 * hint in the doc comment on `TreeNode` (which allows referencing a
 * not-yet-loaded but visible ancestor as a lazy-load hook), this mock never
 * lists the id of someone the caller isn't allowed to see, so as not to leak
 * mere existence via an id the client could probe.
 */
export function queryTreeProjection(graph: MockGraph, params: QueryTreeParams): TreeProjection | null {
  const rootPerson = graph.personsById.get(params.rootId);
  if (!rootPerson) return null;

  const depthLimit = clamp(params.depth ?? 3, 1, 10);
  const direction: TreeDirection = params.direction ?? "DESCENDANTS";
  const includeSpouses = params.includeSpouses ?? true;
  const maxNodes = clamp(params.maxNodes ?? 500, 1, 2000);
  // Safety valve only — keeps a pathological depth+size combination from
  // walking the whole ~4,000-node mock graph before we even get to apply
  // maxNodes. Not a contract concept, purely an implementation guard.
  const explorationCap = maxNodes * 4;

  const depthById = new Map<string, number>([[params.rootId, 0]]);
  const order: string[] = [params.rootId];
  const queue: Array<{ id: string; depth: number }> = [{ id: params.rootId, depth: 0 }];

  while (queue.length > 0 && order.length < explorationCap) {
    const current = queue.shift()!;
    const atBoundary = Math.abs(current.depth) >= depthLimit;

    if (!atBoundary) {
      if (direction === "DESCENDANTS" || direction === "BOTH") {
        for (const childId of graph.childrenOf.get(current.id) ?? []) {
          if (depthById.has(childId)) continue;
          depthById.set(childId, current.depth + 1);
          order.push(childId);
          queue.push({ id: childId, depth: current.depth + 1 });
        }
      }
      if (direction === "ANCESTORS" || direction === "BOTH") {
        for (const parentId of graph.parentsOf.get(current.id) ?? []) {
          if (depthById.has(parentId)) continue;
          depthById.set(parentId, current.depth - 1);
          order.push(parentId);
          queue.push({ id: parentId, depth: current.depth - 1 });
        }
      }
    }

    if (includeSpouses) {
      for (const spouseId of graph.spousesOf.get(current.id) ?? []) {
        if (depthById.has(spouseId)) continue;
        // Spouse shares the couple's generation; deliberately not pushed to
        // `queue` — we don't auto-traverse a spouse's own ancestor/other
        // line from here (that's a separate expand-this-node action).
        depthById.set(spouseId, current.depth);
        order.push(spouseId);
      }
    }
  }

  const visibleOrder = order.filter((id) => params.isVisible(graph.personsById.get(id)!));
  const truncated = visibleOrder.length > maxNodes;
  const includedVisible = truncated ? visibleOrder.slice(0, maxNodes) : visibleOrder;
  const dropped = truncated ? visibleOrder.slice(maxNodes) : [];
  const includedSet = new Set(includedVisible);

  const truncatedNodeIds = new Set<string>();
  if (truncated) {
    for (const droppedId of dropped) {
      for (const parentId of graph.parentsOf.get(droppedId) ?? []) {
        if (includedSet.has(parentId)) truncatedNodeIds.add(parentId);
      }
      for (const childId of graph.childrenOf.get(droppedId) ?? []) {
        if (includedSet.has(childId)) truncatedNodeIds.add(childId);
      }
    }
  }

  const nodes: TreeNode[] = includedVisible.map((id) => {
    const person = graph.personsById.get(id)!;
    const realChildren = graph.childrenOf.get(id) ?? [];
    const includedChildren = realChildren.filter((c) => includedSet.has(c));
    const parentIds = (graph.parentsOf.get(id) ?? []).filter((p) =>
      params.isVisible(graph.personsById.get(p)!)
    );
    // Visible spouses are listed even if not yet loaded into this response
    // (same lazy-load-hint convention as parentIds) — includedSet is always
    // a subset of "visible", so this is just "is this spouse visible at all".
    const spouseIds = (graph.spousesOf.get(id) ?? []).filter((s) =>
      params.isVisible(graph.personsById.get(s)!)
    );

    return {
      id,
      person: toSummary(person),
      depth: depthById.get(id) ?? 0,
      parentIds,
      spouseIds,
      childCount: realChildren.length,
      hasMoreDescendants: realChildren.length > includedChildren.length,
      badges: person.badges,
    };
  });

  const edges: TreeEdge[] = graph.edges
    .filter((e) => includedSet.has(e.source) && includedSet.has(e.target))
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      relType: e.relType,
      spouseOrder: e.spouseOrder ?? null,
      validTo: e.validTo ?? null,
    }));

  const projection: TreeProjection = {
    rootId: params.rootId,
    nodes,
    edges,
    meta: {
      depth: depthLimit,
      direction,
      nodeCount: nodes.length,
      edgeCount: edges.length,
      truncated,
      truncatedNodeIds: [...truncatedNodeIds],
      generatedAt: new Date().toISOString(),
      fromCache: false,
    },
  };
  return projection;
}
