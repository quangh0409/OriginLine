import { describe, expect, it } from "vitest";
import { computeVisibleSubgraph } from "@/lib/tree/visible-subgraph";
import { edge, node, threeGenerationFamily, toMaps } from "../../setup/tree-fixtures";

const ids = (nodes: { id: string }[]) => nodes.map((n) => n.id).sort();

describe("computeVisibleSubgraph — the lazy-render / collapse mechanism", () => {
  it("shows only the root when nothing is expanded", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", new Set());
    // The root's own spouse still rides along: a spouse is not a descendant
    // waiting to be revealed.
    expect(ids(visible.nodes)).toEqual(["root", "rootWife"]);
  });

  it("reveals one generation per expanded ancestor, never the whole tree", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", new Set(["root"]));
    expect(ids(visible.nodes)).toEqual([
      "adopted",
      "root",
      "rootWife",
      "sonA",
      "sonAWife",
      "sonB",
    ]);
    expect(visible.nodes.map((n) => n.id)).not.toContain("grandA1");
  });

  it("reveals grandchildren only once their own parent is expanded too", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const visible = computeVisibleSubgraph(
      nodesById,
      edgesById,
      "root",
      new Set(["root", "sonA"])
    );
    expect(ids(visible.nodes)).toContain("grandA1");
    expect(ids(visible.nodes)).toContain("grandA2");
  });

  it("hides an entire subtree when a mid-level branch is collapsed", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const expanded = computeVisibleSubgraph(nodesById, edgesById, "root", new Set(["root", "sonA"]));
    const collapsed = computeVisibleSubgraph(nodesById, edgesById, "root", new Set(["root"]));
    expect(expanded.nodes.length - collapsed.nodes.length).toBe(2);
    expect(collapsed.nodes.map((n) => n.id)).not.toContain("grandA1");
  });

  it("follows an adopted child exactly like a biological one", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", new Set(["root"]));
    expect(visible.nodes.map((n) => n.id)).toContain("adopted");
  });

  it("emits only edges whose BOTH ends are visible", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", new Set(["root"]));
    const visibleIds = new Set(visible.nodes.map((n) => n.id));
    for (const e of visible.edges) {
      expect(visibleIds.has(e.source), `edge ${e.id} source dangling`).toBe(true);
      expect(visibleIds.has(e.target), `edge ${e.id} target dangling`).toBe(true);
    }
    expect(visible.edges.map((e) => e.id)).not.toContain("e-sonA-g1");
  });

  it("propagates spouse visibility to a fixpoint across a remarriage chain", () => {
    // A -> B (first marriage), B -> C (remarriage), C -> D. Reaching A must
    // pull in the whole chain, which takes more than one hop.
    const nodes = [node("A", 0), node("B", 0), node("C", 0), node("D", 0)];
    const edges = [
      edge("s1", "A", "B", "SPOUSE", { spouseOrder: 1, validTo: "1980-01-01" }),
      edge("s2", "B", "C", "SPOUSE", { spouseOrder: 2 }),
      edge("s3", "C", "D", "SPOUSE", { spouseOrder: 1 }),
    ];
    const { nodesById, edgesById } = toMaps(nodes, edges);
    const visible = computeVisibleSubgraph(nodesById, edgesById, "A", new Set(["A"]));
    expect(ids(visible.nodes)).toEqual(["A", "B", "C", "D"]);
  });

  it("returns nothing when the root id is not in the loaded projection", () => {
    const { nodesById, edgesById } = threeGenerationFamily();
    const visible = computeVisibleSubgraph(nodesById, edgesById, "khong-co", new Set(["khong-co"]));
    expect(visible.nodes).toEqual([]);
    expect(visible.edges).toEqual([]);
  });

  it("ignores an edge pointing at a node that has not been fetched yet", () => {
    const { nodes, edges } = threeGenerationFamily();
    const withDangling = [...edges, edge("dangling", "root", "chua-tai")];
    const { nodesById, edgesById } = toMaps(nodes, withDangling);
    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", new Set(["root"]));
    expect(visible.nodes.map((n) => n.id)).not.toContain("chua-tai");
    expect(visible.edges.map((e) => e.id)).not.toContain("dangling");
  });

  it("does not loop forever on a cyclic spouse graph", () => {
    const nodes = [node("X", 0), node("Y", 0)];
    const edges = [
      edge("s1", "X", "Y", "SPOUSE"),
      edge("s2", "Y", "X", "SPOUSE"),
    ];
    const { nodesById, edgesById } = toMaps(nodes, edges);
    const visible = computeVisibleSubgraph(nodesById, edgesById, "X", new Set(["X"]));
    expect(ids(visible.nodes)).toEqual(["X", "Y"]);
  });

  /**
   * BUG WATCH — direction toggle.
   *
   * <TreeToolbar> offers "Tổ tiên" (ANCESTORS) and "Cả hai chiều" (BOTH),
   * which make useTreeCanvas fetch a projection whose nodes have NEGATIVE
   * depth (ancestors of the root). This function only ever walks descent
   * edges DOWNWARD from the root, so those ancestors can never become
   * visible: switching to "Tổ tiên" renders the root and its spouse alone.
   */
  it("shows fetched ancestor generations when the projection was fetched upward", () => {
    const nodes = [
      node("ong", -2, { childCount: 1 }),
      node("cha", -1, { parentIds: ["ong"], childCount: 1 }),
      node("toi", 0, { parentIds: ["cha"] }),
    ];
    const edges = [edge("e1", "ong", "cha"), edge("e2", "cha", "toi")];
    const { nodesById, edgesById } = toMaps(nodes, edges);
    const visible = computeVisibleSubgraph(nodesById, edgesById, "toi", new Set(["toi"]));
    expect(ids(visible.nodes)).toEqual(["cha", "ong", "toi"]);
  });
});
