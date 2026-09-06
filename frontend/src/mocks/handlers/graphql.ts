import { graphql, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { canSeeLivingPersons, resolveMockRole } from "./role";
import type { RawPerson } from "@/mocks/tree-graph/generate-large-tree";
import type { BranchesVariables, TreeProjectionVariables } from "@/lib/graphql/queries";

// POST /api/v1/graphql, per contracts/openapi.yaml — a single non-standard
// endpoint path, so we use graphql.link() rather than the default /graphql
// interceptor. Operation name must match src/lib/graphql/queries.ts.
const giaPhaGraphql = graphql.link(`${API_BASE_URL}/api/v1/graphql`);

export const graphqlHandlers = [
  giaPhaGraphql.query<Record<string, unknown>, TreeProjectionVariables>(
    "TreeProjectionQuery",
    ({ variables, request }) => {
      const role = resolveMockRole(request);
      const graph = getMockGraph();
      const isVisible = (person: RawPerson) => person.isAlive === false || canSeeLivingPersons(role);

      const rootId = variables.rootId || graph.rootId;
      const rootPerson = graph.personsById.get(rootId);
      if (!rootPerson || !isVisible(rootPerson)) {
        // Mirrors REST's guest-hidden-living-person 404: GraphQL's equivalent
        // is `tree: null` (contracts/README §7 point 2 / schema.graphqls
        // module doc) — same non-disclosure, different transport.
        return HttpResponse.json({ data: { tree: null } });
      }

      const projection = queryTreeProjection(graph, {
        rootId,
        depth: variables.depth,
        direction: variables.direction,
        includeSpouses: variables.includeSpouses,
        maxNodes: variables.maxNodes,
        isVisible,
      });

      // Note the GraphQL/REST divergence (contracts/README §7.1): hidden
      // fields are `null` here, never omitted — this query only selects
      // Tier-1-safe `Person` fields (see queries.ts), so there is nothing
      // further to null out per node beyond the whole-node exclusion above.
      return HttpResponse.json({
        data: {
          tree: projection && {
            root: { id: rootId, displayName: rootPerson.displayName },
            nodes: projection.nodes.map((n) => ({
              id: n.id,
              depth: n.depth,
              parentIds: n.parentIds,
              spouseIds: n.spouseIds,
              childCount: n.childCount ?? 0,
              hasMoreDescendants: n.hasMoreDescendants,
              badges: n.badges ?? [],
              person: {
                id: n.person.id,
                displayName: n.person.displayName,
                generation: n.person.generation ?? null,
                gender: n.person.gender ?? null,
                isAlive: n.person.isAlive,
                branch: n.person.primaryBranch
                  ? { id: n.person.primaryBranch.id, name: n.person.primaryBranch.name }
                  : null,
              },
            })),
            edges: projection.edges,
            meta: projection.meta,
          },
        },
      });
    }
  ),

  /**
   * `Query.branches(rootPath)` — the option source for the F6 chi/ngành
   * filter. Derived from the generated graph rather than a separate fixture
   * so the filter can never offer a branch that no person belongs to.
   *
   * `memberCount` is privacy-filtered BEFORE counting, exactly as the
   * contract promises: a guest's count excludes living people, so it can't
   * be diffed against a member's count to discover how many living members
   * a chi has.
   */
  giaPhaGraphql.query<Record<string, unknown>, BranchesVariables>(
    "BranchesQuery",
    ({ variables, request }) => {
      const role = resolveMockRole(request);
      const graph = getMockGraph();
      const rootPath = variables.rootPath;

      const byId = new Map<string, { id: string; name: string; path: string; region: string | null; memberCount: number }>();
      for (const person of graph.personsById.values()) {
        const branch = person.primaryBranch;
        if (!branch) continue;
        if (rootPath && branch.path !== rootPath && !branch.path.startsWith(`${rootPath}.`)) continue;
        const existing = byId.get(branch.id);
        const counts = person.isAlive === false || canSeeLivingPersons(role);
        if (existing) {
          if (counts) existing.memberCount += 1;
        } else {
          byId.set(branch.id, {
            id: branch.id,
            name: branch.name,
            path: branch.path,
            region: branch.region ?? null,
            memberCount: counts ? 1 : 0,
          });
        }
      }

      return HttpResponse.json({
        data: {
          branches: [...byId.values()].sort((a, b) => a.path.localeCompare(b.path)),
        },
      });
    }
  ),
];
