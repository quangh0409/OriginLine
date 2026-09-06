import { useQuery } from "@tanstack/react-query";
import { kinshipApi } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";

/**
 * Resolves danh xưng between two persons. Always server-derived — see
 * CLAUDE.md "never recompute kinship on the client". A resolved `status`
 * other than "RESOLVED" is not a fetch error; render it via KinshipResult.status.
 */
export function useKinship(fromId: string | undefined, toId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.kinship(fromId ?? "", toId ?? ""),
    queryFn: () => kinshipApi.resolve(fromId as string, toId as string),
    enabled: Boolean(fromId && toId),
  });
}

export function useEffectiveKinshipRules(branchId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.kinshipRules(branchId ?? ""),
    queryFn: () => kinshipApi.getEffectiveRuleSet(branchId as string),
    enabled: Boolean(branchId),
  });
}
