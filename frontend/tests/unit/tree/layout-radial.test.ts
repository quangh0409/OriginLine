import { describe, expect, it } from "vitest";
import { layoutRadial } from "@/lib/tree/layout-radial";
import { NODE_WIDTH, RADIAL_RADIUS_STEP } from "@/lib/tree/layout-constants";
import { edge, node, threeGenerationFamily } from "../../setup/tree-fixtures";

const radiusOf = (p: { x: number; y: number }) => Math.hypot(p.x, p.y);

describe("layoutRadial — tỏa tròn, thủy tổ at the centre", () => {
  it("puts the root at the exact centre", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutRadial(nodes, edges, "root");
    expect(p.get("root")!.x).toBeCloseTo(0, 9);
    expect(p.get("root")!.y).toBeCloseTo(0, 9);
  });

  it("places each generation on its own concentric ring", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutRadial(nodes, edges, "root");
    for (const id of ["sonA", "sonB", "adopted"]) {
      expect(radiusOf(p.get(id)!)).toBeCloseTo(RADIAL_RADIUS_STEP, 6);
    }
    for (const id of ["grandA1", "grandA2"]) {
      expect(radiusOf(p.get(id)!)).toBeCloseTo(2 * RADIAL_RADIUS_STEP, 6);
    }
  });

  it("positions every node in the visible set — nothing silently collapses onto the centre", () => {
    // A node with no position falls back to {0,0} in toFlowNodes, stacking it
    // invisibly on top of the thủy tổ. That reads as "the person vanished".
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutRadial(nodes, edges, "root");
    for (const n of nodes) {
      expect(p.has(n.id), `no radial position for ${n.id}`).toBe(true);
      if (n.id !== "root") {
        expect(radiusOf(p.get(n.id)!), `${n.id} collapsed onto the centre`).toBeGreaterThan(0);
      }
    }
  });

  it("parks a married-in spouse as a satellite beside their partner, not on a ring slot", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutRadial(nodes, edges, "root");
    const sonA = p.get("sonA")!;
    const wife = p.get("sonAWife")!;
    expect(wife.y).toBeCloseTo(sonA.y, 6);
    expect(wife.x - sonA.x).toBeCloseTo(NODE_WIDTH * 0.7, 6);
  });

  it("gives the root's own spouse a satellite slot too", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutRadial(nodes, edges, "root");
    const wife = p.get("rootWife")!;
    expect(wife.y).toBeCloseTo(0, 6);
    expect(wife.x).toBeCloseTo(NODE_WIDTH * 0.7, 6);
  });

  it("returns an empty map when the root is not in the loaded node set", () => {
    const { nodes, edges } = threeGenerationFamily();
    expect(layoutRadial(nodes, edges, "khong-ton-tai").size).toBe(0);
  });

  it("attaches an adopted child to exactly one radial parent without duplicating it", () => {
    // Two qualifying parents (biological + adoptive) is a display
    // simplification, but the child must still appear exactly once.
    const nodes = [
      node("cha-de", 0, { childCount: 1 }),
      node("cha-nuoi", 0, { childCount: 1 }),
      node("con", 1, { parentIds: ["cha-de", "cha-nuoi"] }),
    ];
    const edges = [
      edge("bio", "cha-de", "con", "PARENT_BIO"),
      edge("adopt", "cha-nuoi", "con", "PARENT_ADOPT"),
    ];
    const p = layoutRadial(nodes, edges, "cha-de");
    expect(p.get("con")).toBeDefined();
    expect(radiusOf(p.get("con")!)).toBeCloseTo(RADIAL_RADIUS_STEP, 6);
  });

  it("radiates upward through parent edges when the root sits at depth 0 of an ancestor fetch", () => {
    // direction=ANCESTORS gives negative depths; the rings must grow outward
    // going UP the lineage rather than leaving ancestors unplaced.
    const nodes = [
      node("toi", 0, { parentIds: ["cha"] }),
      node("cha", -1, { parentIds: ["ong"], childCount: 1 }),
      node("ong", -2, { childCount: 1 }),
    ];
    const edges = [edge("e1", "cha", "toi"), edge("e2", "ong", "cha")];
    const p = layoutRadial(nodes, edges, "toi");
    expect(radiusOf(p.get("cha")!)).toBeCloseTo(RADIAL_RADIUS_STEP, 6);
    expect(radiusOf(p.get("ong")!)).toBeCloseTo(2 * RADIAL_RADIUS_STEP, 6);
  });

  it("is deterministic across runs", () => {
    const { nodes, edges } = threeGenerationFamily();
    const a = layoutRadial(nodes, edges, "root");
    const b = layoutRadial(nodes, edges, "root");
    for (const [id, pos] of a) {
      expect(b.get(id)!.x).toBeCloseTo(pos.x, 9);
      expect(b.get(id)!.y).toBeCloseTo(pos.y, 9);
    }
  });

  it("spreads siblings around the circle instead of stacking them on one angle", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutRadial(nodes, edges, "root");
    const angles = ["sonA", "sonB", "adopted"].map((id) => {
      const pos = p.get(id)!;
      return Math.atan2(pos.y, pos.x).toFixed(6);
    });
    expect(new Set(angles).size).toBe(3);
  });
});
