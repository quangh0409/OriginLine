import { gql } from "graphql-request";

/**
 * Deep genealogy tree query, written against `contracts/schema.graphqls`
 * (Query.tree -> TreeProjection). Kept deliberately shallow on `Person`
 * fields for the canvas use case (F2, Sprint 2) — pull only what's needed to
 * draw a node; a click-through to the full profile should call
 * `GET /api/v1/persons/{id}` (or a separate, richer GraphQL query) instead of
 * fattening this one.
 *
 * Two transport differences from the REST /tree endpoint (contracts/README §7.1):
 *  - Hidden/absent fields come back as `null` here, not omitted as in REST.
 *  - `TreeNode.person` is the full `Person` type (privacy-filtered per field
 *    via `@privacyTier`), not the REST `PersonSummaryDto` — so requesting a
 *    Tier-2/3 field here will silently null it out rather than 404ing.
 */
export const TREE_PROJECTION_QUERY = gql`
  query TreeProjectionQuery(
    $rootId: UUID!
    $depth: Int
    $direction: TreeDirection
    $includeSpouses: Boolean
    $maxNodes: Int
  ) {
    tree(
      rootId: $rootId
      depth: $depth
      direction: $direction
      includeSpouses: $includeSpouses
      maxNodes: $maxNodes
    ) {
      root {
        id
        displayName
      }
      nodes {
        id
        depth
        parentIds
        spouseIds
        childCount
        hasMoreDescendants
        badges
        person {
          id
          displayName
          generation
          gender
          isAlive
          branch {
            id
            name
          }
        }
      }
      edges {
        id
        source
        target
        relType
        heirKind
        spouseOrder
        validTo
      }
      meta {
        depth
        direction
        nodeCount
        edgeCount
        truncated
        truncatedNodeIds
        generatedAt
        fromCache
      }
    }
  }
`;

export interface TreeProjectionVariables {
  rootId: string;
  depth?: number;
  direction?: "DESCENDANTS" | "ANCESTORS" | "BOTH";
  includeSpouses?: boolean;
  maxNodes?: number;
}

/**
 * Chi/ngành tree — the option source for the F6 branch filter.
 *
 * Deliberately GraphQL: `contracts/openapi.yaml` has no `/branches` REST
 * endpoint, while `contracts/schema.graphqls` exposes `Query.branches(rootPath)`.
 * Hard-coding a branch list on the client would be a second source of truth
 * for something the clan council edits, so we ask the server.
 *
 * `memberCount` is documented as ALREADY privacy-filtered — a guest's count
 * never includes living people. Render it as-is; never compare it against a
 * result count to infer how many rows were withheld.
 */
export const BRANCHES_QUERY = gql`
  query BranchesQuery($rootPath: String) {
    branches(rootPath: $rootPath) {
      id
      name
      path
      region
      memberCount
    }
  }
`;

export interface BranchesVariables {
  rootPath?: string;
}

export interface BranchOption {
  id: string;
  /** Accented display name. Never use `path` for display. */
  name: string;
  /** ltree path — used only for sorting into hierarchy order. */
  path: string;
  region?: "BAC" | "TRUNG" | "NAM" | null;
  memberCount: number;
}

export interface BranchesQueryData {
  branches: BranchOption[];
}
