import type {
  PersonSummaryDto,
  TreeEdge,
  TreeNode,
  TreeProjection,
  RelType,
} from "@/types/api";

/**
 * Tiny hand-built genealogies for the layout / visibility unit tests.
 *
 * Deliberately NOT the generated ~4,000-node mock graph: a layout assertion
 * has to name exact ids and exact expected positions, and it must stay
 * readable when it fails. The big graph is used only where volume is the
 * point (performance, truncation).
 */

export function person(id: string, overrides: Partial<PersonSummaryDto> = {}): PersonSummaryDto {
  return {
    id,
    displayName: `Người ${id}`,
    gender: "MALE",
    generation: 1,
    isAlive: false,
    ...overrides,
  };
}

export function node(
  id: string,
  depth: number,
  overrides: Partial<TreeNode> = {}
): TreeNode {
  return {
    id,
    person: person(id, { generation: depth + 1 }),
    depth,
    parentIds: [],
    spouseIds: [],
    childCount: 0,
    hasMoreDescendants: false,
    badges: [],
    ...overrides,
  };
}

export function edge(
  id: string,
  source: string,
  target: string,
  relType: RelType = "PARENT_BIO",
  overrides: Partial<TreeEdge> = {}
): TreeEdge {
  return { id, source, target, relType, ...overrides };
}

export function projection(
  rootId: string,
  nodes: TreeNode[],
  edges: TreeEdge[],
  metaOverrides: Partial<TreeProjection["meta"]> = {}
): TreeProjection {
  return {
    rootId,
    nodes,
    edges,
    meta: {
      depth: 3,
      direction: "DESCENDANTS",
      nodeCount: nodes.length,
      edgeCount: edges.length,
      truncated: false,
      ...metaOverrides,
    },
  };
}

export function toMaps(nodes: TreeNode[], edges: TreeEdge[]) {
  return {
    nodesById: new Map(nodes.map((n) => [n.id, n])),
    edgesById: new Map(edges.map((e) => [e.id, e])),
  };
}

/**
 * Three generations of a paternal line with a spouse, a daughter-in-law and
 * an adopted child:
 *
 *   root ── SPOUSE ── rootWife
 *     ├─ sonA (PARENT_BIO)   ── SPOUSE ── sonAWife (dâu)
 *     │    ├─ grandA1
 *     │    └─ grandA2
 *     ├─ sonB (PARENT_BIO)
 *     └─ adopted (PARENT_ADOPT)
 */
export function threeGenerationFamily() {
  const nodes: TreeNode[] = [
    node("root", 0, { childCount: 3, spouseIds: ["rootWife"] }),
    node("rootWife", 0, { spouseIds: ["root"] }),
    node("sonA", 1, { parentIds: ["root"], childCount: 2, spouseIds: ["sonAWife"] }),
    node("sonAWife", 1, { spouseIds: ["sonA"] }),
    node("sonB", 1, { parentIds: ["root"] }),
    node("adopted", 1, { parentIds: ["root"] }),
    node("grandA1", 2, { parentIds: ["sonA"] }),
    node("grandA2", 2, { parentIds: ["sonA"] }),
  ];
  const edges: TreeEdge[] = [
    edge("e-root-wife", "root", "rootWife", "SPOUSE", { spouseOrder: 1 }),
    edge("e-root-sonA", "root", "sonA"),
    edge("e-root-sonB", "root", "sonB"),
    edge("e-root-adopted", "root", "adopted", "PARENT_ADOPT"),
    edge("e-sonA-wife", "sonA", "sonAWife", "SPOUSE", { spouseOrder: 1 }),
    edge("e-sonA-g1", "sonA", "grandA1"),
    edge("e-sonA-g2", "sonA", "grandA2"),
  ];
  return { nodes, edges, ...toMaps(nodes, edges) };
}
