import { describe, expect, it } from "vitest";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";

/**
 * Guards the assumptions every other test and every manual demo makes about
 * the generated mock graph. If the generator's seed or shape changes, these
 * fail here with a clear message instead of failing as a confusing "element
 * not found" three suites away.
 */
describe("mock graph fixtures the test suite relies on", () => {
  const graph = getMockGraph();

  it("is big enough to be worth measuring against (plan §10: >= 1500 persons, >= 7 đời)", () => {
    expect(graph.personsById.size).toBeGreaterThanOrEqual(1500);
    const maxGeneration = Math.max(
      ...[...graph.personsById.values()].map((p) => p.generation)
    );
    expect(maxGeneration).toBeGreaterThanOrEqual(7);
  });

  it("roots at p-001 with the thủy tổ's display name", () => {
    expect(graph.rootId).toBe("p-001");
    expect(graph.personsById.get("p-001")?.displayName).toBe("Nguyễn Văn Thủy Tổ");
    expect(graph.personsById.get("p-001")?.isAlive).toBe(false);
  });

  it("keeps the curated story fixtures reachable and correctly alive/deceased", () => {
    expect(graph.personsById.get("p-010")?.isAlive).toBe(false);
    expect(graph.personsById.get("p-100")?.isAlive).toBe(true);
    expect(graph.personsById.get("p-101")?.isAlive).toBe(true);
  });

  it("connects every curated fixture to the root by real edges, not as an island", () => {
    for (const id of ["p-010", "p-011", "p-020", "p-021", "p-100", "p-101"]) {
      const linked =
        (graph.parentsOf.get(id)?.length ?? 0) +
        (graph.childrenOf.get(id)?.length ?? 0) +
        (graph.spousesOf.get(id)?.length ?? 0);
      expect(linked, `${id} is not connected to anything`).toBeGreaterThan(0);
    }
  });

  it("contains living people, so the guest-visibility rule is actually exercised", () => {
    const living = [...graph.personsById.values()].filter((p) => p.isAlive);
    expect(living.length).toBeGreaterThan(50);
  });

  it("records daughters as fully as sons (BA v2 §12)", () => {
    const women = [...graph.personsById.values()].filter((p) => p.gender === "FEMALE");
    expect(women.length).toBeGreaterThan(100);
    expect(women.every((p) => p.generation > 0 && p.displayName.length > 0)).toBe(true);
  });

  it("models adoption, in-laws, heirship and polygamy, not just a clean male line", () => {
    const badges = new Set([...graph.personsById.values()].flatMap((p) => p.badges));
    for (const expected of ["CON_NUOI", "DAU", "RE", "DICH_TON", "TUYET_TU"]) {
      expect([...badges], `mock graph has no ${expected}`).toContain(expected);
    }
    expect(graph.edges.some((e) => e.relType === "PARENT_ADOPT")).toBe(true);
    expect(graph.edges.some((e) => (e.spouseOrder ?? 0) > 1)).toBe(true);
  });

  /**
   * BUG WATCH - two badges are unreachable in the generated mock graph.
   *
   * generate-large-tree.ts promises "at least one TRUONG_CHI / DICH_TON /
   * TUYET_TU / KE_TU example exists", but produces neither TRUONG_CHI nor
   * KE_TU (measured badge counts: DECEASED 452, DAU 345, RE 682, CON_NUOI 23,
   * TUYET_TU 8, DICH_TON 1, TRUONG_CHI 0, KE_TU 0):
   *
   *  - TRUONG_CHI needs `childGeneration === Math.min(6, father.generation + 2)`,
   *    which is only satisfiable by a gen-5 father still on the eldest line -
   *    a state the generator never reaches.
   *  - KE_TU needs a childless deceased father to also pass `rand() < 0.15`,
   *    which never coincides.
   *
   * Consequence: "Trưởng chi" (a clan title) and "kế tự" (lineage
   * continuation) never appear on the canvas in dev or in a demo to the Hội
   * đồng Tộc biểu, so those two rendering paths are never actually seen
   * before production.
   */
  it("produces at least one Trưởng chi and one Kế tự so both badges are demonstrable", () => {
    const badges = new Set([...graph.personsById.values()].flatMap((p) => p.badges));
    expect([...badges]).toContain("TRUONG_CHI");
    expect([...badges]).toContain("KE_TU");
  });
});
