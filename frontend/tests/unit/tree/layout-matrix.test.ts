import { describe, expect, it } from "vitest";
import { layoutMatrix } from "@/lib/tree/layout-matrix";
import { layoutHierarchical } from "@/lib/tree/layout-hierarchical";
import { MATRIX_COL_GAP, MATRIX_ROW_HEIGHT, NODE_WIDTH } from "@/lib/tree/layout-constants";
import { edge, node, threeGenerationFamily } from "../../setup/tree-fixtures";

const COL_WIDTH = NODE_WIDTH + MATRIX_COL_GAP;

describe("layoutMatrix — ma trận thế hệ", () => {
  it("pins every node of one generation to exactly the same row", () => {
    // This is the entire point of the view mode: unlike the hierarchical
    // layout (where a spouse edge can drag a rank), a generation is a band.
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutMatrix(nodes, edges);
    const gen1Y = ["sonA", "sonAWife", "sonB", "adopted"].map((id) => p.get(id)!.y);
    expect(new Set(gen1Y).size).toBe(1);
    expect(gen1Y[0]).toBe(1 * MATRIX_ROW_HEIGHT);
  });

  it("derives the row from TreeNode.depth, not from the fetch order", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutMatrix(nodes, edges);
    for (const n of nodes) {
      expect(p.get(n.id)!.y, `row for ${n.id}`).toBe(n.depth * MATRIX_ROW_HEIGHT);
    }
  });

  it("places ancestor generations (negative depth) above the root", () => {
    // direction=ANCESTORS/BOTH yields depth < 0; those must land above y=0.
    const nodes = [
      node("ong", -1, { childCount: 1 }),
      node("cha", 0, { parentIds: ["ong"], childCount: 1 }),
      node("con", 1, { parentIds: ["cha"] }),
    ];
    const edges = [edge("e1", "ong", "cha"), edge("e2", "cha", "con")];
    const p = layoutMatrix(nodes, edges);
    expect(p.get("ong")!.y).toBeLessThan(p.get("cha")!.y);
    expect(p.get("ong")!.y).toBe(-MATRIX_ROW_HEIGHT);
    expect(p.get("cha")!.y).toBe(0);
  });

  it("snaps columns to a fixed grid starting at zero, with no overlap", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutMatrix(nodes, edges);
    const row1 = nodes.filter((n) => n.depth === 1).map((n) => p.get(n.id)!.x).sort((a, b) => a - b);
    expect(row1[0]).toBe(0);
    row1.forEach((x, i) => expect(x).toBe(i * COL_WIDTH));
    // Grid spacing must exceed the card width or cards would touch.
    expect(COL_WIDTH).toBeGreaterThan(NODE_WIDTH);
  });

  it("orders a row by the crossing-minimised order dagre already solved", () => {
    const { nodes, edges } = threeGenerationFamily();
    const hier = layoutHierarchical(nodes, edges);
    const matrix = layoutMatrix(nodes, edges);

    const row1 = nodes.filter((n) => n.depth === 1).map((n) => n.id);
    const byHier = [...row1].sort((a, b) => hier.get(a)!.x - hier.get(b)!.x);
    const byMatrix = [...row1].sort((a, b) => matrix.get(a)!.x - matrix.get(b)!.x);
    expect(byMatrix).toEqual(byHier);
  });

  it("does not mutate the caller's node array while sorting rows", () => {
    const { nodes, edges } = threeGenerationFamily();
    const before = nodes.map((n) => n.id);
    layoutMatrix(nodes, edges);
    expect(nodes.map((n) => n.id)).toEqual(before);
  });

  it("returns an empty map for an empty graph", () => {
    expect(layoutMatrix([], []).size).toBe(0);
  });

  it("is deterministic across runs", () => {
    const { nodes, edges } = threeGenerationFamily();
    const a = layoutMatrix(nodes, edges);
    const b = layoutMatrix(nodes, edges);
    for (const [id, pos] of a) expect(b.get(id)).toEqual(pos);
  });
});
