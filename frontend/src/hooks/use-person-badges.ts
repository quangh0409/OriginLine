import { useQuery } from "@tanstack/react-query";
import { treeApi } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";
import type { PersonBadge } from "@/types/api";

/**
 * Backend-computed badges (dâu/rể · con nuôi · đích tôn · trưởng chi · tuyệt
 * tự) for a single person.
 *
 * CONTRACT GAP: `PersonDto` has no `badges` field — only `TreeNode` does
 * (contracts/openapi.yaml). Since dâu/rể must never be inferred client-side
 * (contracts/README §7.6: "Dâu/rể không phải cạnh... đã có trong `badges`"),
 * the profile reads them off a one-node `/tree` projection instead of
 * deriving them from `relationships`. That keeps the rule "badges are
 * server-authored" intact at the cost of one extra request.
 *
 * Proposed fix for the contract: add `badges: [PersonBadge]` to `PersonDto`
 * and delete this hook. Until then, a failure here is silent — badges are
 * supporting detail and must never take the profile down with them.
 */
export function usePersonBadges(personId: string | undefined) {
  return useQuery<PersonBadge[]>({
    queryKey: queryKeys.personBadges(personId ?? ""),
    queryFn: async () => {
      const projection = await treeApi.getTree({
        rootId: personId as string,
        depth: 1,
        maxNodes: 1,
        includeSpouses: false,
      });
      return projection.nodes.find((node) => node.id === personId)?.badges ?? [];
    },
    enabled: Boolean(personId),
    retry: false,
    staleTime: 5 * 60_000,
  });
}
