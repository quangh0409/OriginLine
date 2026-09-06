"use client";

import { useCallback, useMemo, useState } from "react";
import { useQueries } from "@tanstack/react-query";
import { treeApi } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";
import { mergeProjections } from "@/lib/tree/merge-projections";
import { computeVisibleSubgraph } from "@/lib/tree/visible-subgraph";
import type { TreeDirection } from "@/types/api";

export interface UseTreeCanvasOptions {
  rootId: string;
  /** Depth fetched from `rootId` on first load. Kept small + a lazy prefetch
   * of one extra ring (see module doc) rather than the contract max (10). */
  initialDepth?: number;
  /** Depth fetched when the user expands a node that ran out of loaded data. */
  expandDepth?: number;
  maxNodesPerFetch?: number;
  direction?: TreeDirection;
  includeSpouses?: boolean;
}

/**
 * Owns every stateful piece of the canvas that ISN'T "how to draw pixels":
 * which branches have been fetched (React Query, one query per expanded
 * root — never one query for the whole tree), which are currently expanded
 * in the UI, and the resulting visible node/edge set after collapsing.
 *
 * Lazy-loading strategy: the initial fetch requests `initialDepth` (default
 * 2) generations from `rootId`, but only `rootId` itself starts "expanded" —
 * so the second of those two fetched generations is already sitting in the
 * React Query cache, ready to render the instant a user expands into it,
 * with no network round-trip. Expanding a node that has run out of
 * pre-fetched data (its `hasMoreDescendants` is still true beyond what's
 * loaded) issues exactly one more `/tree?rootId=<thatNode>` call — the tree
 * is NEVER fetched in full, matching the F2 brief's #1 requirement.
 */
export function useTreeCanvas({
  rootId,
  initialDepth = 2,
  expandDepth = 2,
  maxNodesPerFetch = 300,
  direction = "DESCENDANTS",
  includeSpouses = true,
}: UseTreeCanvasOptions) {
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set([rootId]));
  const [fetchedIds, setFetchedIds] = useState<string[]>(() => [rootId]);
  const [resetKey, setResetKey] = useState(`${rootId}:${direction}`);

  const nextResetKey = `${rootId}:${direction}`;
  if (nextResetKey !== resetKey) {
    // Root or direction changed underneath us (toolbar toggle) — this is a
    // different query space, not an incremental extension of what's loaded,
    // so start over rather than mixing DESCENDANTS/ANCESTORS graphs.
    setResetKey(nextResetKey);
    setExpandedIds(new Set([rootId]));
    setFetchedIds([rootId]);
  }

  const results = useQueries({
    queries: fetchedIds.map((branchRootId) => {
      const depth = branchRootId === rootId ? initialDepth : expandDepth;
      return {
        queryKey: queryKeys.treeBranch(branchRootId, depth, direction, maxNodesPerFetch),
        queryFn: () =>
          treeApi.getTree({
            rootId: branchRootId,
            depth,
            direction,
            includeSpouses,
            maxNodes: maxNodesPerFetch,
          }),
        staleTime: 60_000,
        retry: (failureCount: number, error: unknown) => {
          if (error instanceof ApiError && error.status === 404) return false;
          return failureCount < 2;
        },
      };
    }),
  });

  const loadingIds = useMemo(() => {
    const ids = new Set<string>();
    results.forEach((r, i) => {
      if (r.isFetching) ids.add(fetchedIds[i]!);
    });
    return ids;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results, fetchedIds]);

  const merged = useMemo(
    () => mergeProjections(results.map((r) => r.data)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [results]
  );

  const visible = useMemo(
    () => computeVisibleSubgraph(merged.nodesById, merged.edgesById, rootId, expandedIds),
    [merged, expandedIds, rootId]
  );

  const expand = useCallback(
    (nodeId: string) => {
      setExpandedIds((prev) => (prev.has(nodeId) ? prev : new Set(prev).add(nodeId)));
      setFetchedIds((prev) => {
        if (prev.includes(nodeId)) return prev;
        const node = merged.nodesById.get(nodeId);
        // Only issue a new fetch if this node's descendants genuinely
        // aren't fully loaded yet — many expand clicks are "free" because
        // initialDepth already prefetched one ring further than what's shown.
        if (!node?.hasMoreDescendants) return prev;
        return [...prev, nodeId];
      });
    },
    [merged]
  );

  const collapse = useCallback(
    (nodeId: string) => {
      if (nodeId === rootId) return;
      setExpandedIds((prev) => {
        if (!prev.has(nodeId)) return prev;
        const next = new Set(prev);
        next.delete(nodeId);
        return next;
      });
    },
    [rootId]
  );

  const toggle = useCallback(
    (nodeId: string) => {
      if (expandedIds.has(nodeId)) collapse(nodeId);
      else expand(nodeId);
    },
    [expandedIds, expand, collapse]
  );

  const rootQuery = results[0];
  const notFound = rootQuery?.error instanceof ApiError && rootQuery.error.status === 404;

  return {
    nodes: visible.nodes,
    edges: visible.edges,
    expandedIds,
    loadingIds,
    expand,
    collapse,
    toggle,
    isLoadingInitial: rootQuery?.isLoading ?? false,
    isFetchingAny: results.some((r) => r.isFetching),
    error: notFound ? null : (results.find((r) => r.error)?.error ?? null),
    /** Guest-hidden-root (or a truly missing id) — render "not found", never a generic error. */
    rootNotFound: notFound,
    truncated: merged.truncated,
    truncatedNodeIds: merged.truncatedNodeIds,
    loadedCount: merged.nodesById.size,
  };
}
