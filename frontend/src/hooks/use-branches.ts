import { useQuery } from "@tanstack/react-query";
import { graphqlClient } from "@/lib/graphql/client";
import {
  BRANCHES_QUERY,
  type BranchOption,
  type BranchesQueryData,
  type BranchesVariables,
} from "@/lib/graphql/queries";
import { queryKeys } from "@/lib/query/keys";

/**
 * The clan's chi/ngành list, used to populate the F6 branch filter (and any
 * later branch-scoped picker). Goes over GraphQL because there is no REST
 * `/branches` in the contract — see BRANCHES_QUERY for the rationale.
 *
 * Branches change about once a decade, so this is cached hard: refetching it
 * on every keystroke of a search box would be pure waste.
 */
export function useBranches(rootPath?: string) {
  return useQuery<BranchOption[]>({
    queryKey: queryKeys.branches(rootPath),
    queryFn: async () => {
      const data = await graphqlClient.request<BranchesQueryData, BranchesVariables>(
        BRANCHES_QUERY,
        { rootPath }
      );
      // ltree path order == hierarchy order, so a plain string sort already
      // groups children under their parent without a tree walk.
      return [...data.branches].sort((a, b) => a.path.localeCompare(b.path));
    },
    staleTime: 10 * 60_000,
  });
}

/** Indentation depth of a branch in the chi/ngành hierarchy, from its ltree path. */
export function branchDepth(branch: BranchOption): number {
  return branch.path.split(".").length - 1;
}
