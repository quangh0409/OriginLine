import { describe, expect, it } from "vitest";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { resolveKinshipMock } from "@/mocks/kinship-engine";
import { DEFAULT_KINSHIP_RULE_SET } from "@/mocks/data";

/**
 * Pins the danh xưng answers the F5 component test asserts on. If the mock
 * graph or the fixture rule set changes shape, this fails first and names the
 * pair, instead of the UI test failing with "text not found".
 *
 * These are FIXTURE rules, not an approved clan rule set (contracts/README
 * §6.2) — the point here is that the engine wiring resolves, not that the
 * council has blessed the wording.
 */
const resolve = (fromId: string, toId: string) =>
  resolveKinshipMock(getMockGraph(), {
    fromId,
    toId,
    rules: DEFAULT_KINSHIP_RULE_SET.rules,
    ruleSetId: DEFAULT_KINSHIP_RULE_SET.id,
    isVisible: () => true,
  });

describe("kinship fixtures used by the F5 UI test", () => {
  it("resolves đời 2 -> thủy tổ as a direct paternal parent link", () => {
    const result = resolve("p-010", "p-001");
    expect(result.status).toBe("RESOLVED");
    expect(result.title).toBe("Cha");
    expect(result.reciprocalTitle).toBe("Con");
    expect(result.facts).toMatchObject({ genDelta: -1, side: "PATERNAL", collateralDegree: 0 });
    expect(result.lca?.personId).toBe("p-001");
  });

  it("walks the path from the caller up to the tổ chung and no further", () => {
    const result = resolve("p-010", "p-001");
    expect(result.path?.map((s) => [s.personId, s.direction])).toEqual([
      ["p-010", "SELF"],
      ["p-001", "UP"],
    ]);
  });

  it("resolves two chi heads as siblings through their shared thủy tổ", () => {
    const result = resolve("p-010", "p-011");
    expect(result.status).toBe("RESOLVED");
    expect(result.facts).toMatchObject({ genDelta: 0, side: "PATERNAL", collateralDegree: 1 });
    expect(result.lca?.personId).toBe("p-001");
    expect(result.lca?.distanceFrom).toBe(1);
    expect(result.lca?.distanceTo).toBe(1);
    // The path must go UP through the tổ chung and back DOWN — that ladder is
    // the evidence the feature exists to show.
    expect(result.path?.map((s) => s.direction)).toEqual(["SELF", "UP", "DOWN"]);
  });

  it("answers SELF rather than a title when both pickers hold one person", () => {
    expect(resolve("p-001", "p-001").status).toBe("SELF");
  });

  it("keeps the collateral degree, which is the only thing separating bác ruột from bác họ", () => {
    const siblings = resolve("p-010", "p-011");
    expect(siblings.facts?.collateralDegree).toBe(1);
  });
});
