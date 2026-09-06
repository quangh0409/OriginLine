import { useQuery } from "@tanstack/react-query";
import { treeApi, type GetTreeParams } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";

/**
 * Shallow/initial tree fetch via REST. Sprint 2's canvas layers branch-level
 * lazy loading (collapse/expand via `hasMoreDescendants`) on top of this —
 * this hook intentionally never walks the whole tree eagerly. Always check
 * `data.meta.truncated` before assuming the projection is complete.
 */
export function useTree(params: GetTreeParams | undefined) {
  return useQuery({
    queryKey: queryKeys.tree(params?.rootId ?? "", params?.depth ?? 3),
    queryFn: () => treeApi.getTree(params as GetTreeParams),
    enabled: Boolean(params?.rootId),
  });
}
