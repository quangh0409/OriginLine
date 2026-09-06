import { apiFetch } from "./http";
import type { TreeDirection, TreeProjection } from "@/types/api";

export interface GetTreeParams {
  rootId: string;
  depth?: number; // default 3, max 10
  direction?: TreeDirection; // default DESCENDANTS
  includeSpouses?: boolean; // default true
  includeDeleted?: boolean; // ADMIN/COUNCIL only
  maxNodes?: number; // default 500, max 2000
}

export const treeApi = {
  /**
   * Flat REST projection (`nodes[]` + `edges[]`), cached server-side with an
   * ETag. Always check `meta.truncated` — a truncated response is NOT a
   * complete tree, and the guest-visible tree can have legitimate holes
   * where a living person was filtered out (never treat a missing edge as a
   * data bug). Deep client-shaped nested queries should prefer the GraphQL
   * client (src/lib/graphql) instead of chaining many of these calls.
   */
  getTree: ({ rootId, depth = 3, ...rest }: GetTreeParams) =>
    apiFetch<TreeProjection>("/api/v1/tree", {
      query: { rootId, depth, ...rest },
    }),
};
