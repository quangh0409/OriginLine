import { describe, expect, it } from "vitest";
import { mergeProjections } from "@/lib/tree/merge-projections";
import { edge, node, projection } from "../../setup/tree-fixtures";

describe("mergeProjections — combining one fetch per expanded branch", () => {
  it("returns an empty graph when nothing has loaded yet", () => {
    const merged = mergeProjections([undefined, undefined]);
    expect(merged.nodesById.size).toBe(0);
    expect(merged.edgesById.size).toBe(0);
    expect(merged.truncated).toBe(false);
    expect(merged.truncatedNodeIds.size).toBe(0);
  });

  it("unions nodes and edges across projections", () => {
    const a = projection("root", [node("root", 0), node("a", 1)], [edge("e1", "root", "a")]);
    const b = projection("a", [node("a", 0), node("a1", 1)], [edge("e2", "a", "a1")]);
    const merged = mergeProjections([a, b]);
    expect([...merged.nodesById.keys()].sort()).toEqual(["a", "a1", "root"]);
    expect([...merged.edgesById.keys()].sort()).toEqual(["e1", "e2"]);
  });

  it("dedupes a node reached from two different expand actions", () => {
    // A shared spouse can legitimately appear in two branch fetches.
    const a = projection("x", [node("shared", 1)], []);
    const b = projection("y", [node("shared", 1)], []);
    expect(mergeProjections([a, b]).nodesById.size).toBe(1);
  });

  it("lets a later fetch's fresher node win", () => {
    // hasMoreDescendants only gets MORE accurate as branches load.
    const stale = projection("root", [node("n", 1, { hasMoreDescendants: true, childCount: 5 })], []);
    const fresh = projection("n", [node("n", 0, { hasMoreDescendants: false, childCount: 5 })], []);
    const merged = mergeProjections([stale, fresh]);
    expect(merged.nodesById.get("n")!.hasMoreDescendants).toBe(false);
  });

  it("raises the truncated flag if ANY fetch was cut off by maxNodes", () => {
    const complete = projection("root", [node("root", 0)], []);
    const cut = projection("big", [node("big", 0)], [], {
      truncated: true,
      truncatedNodeIds: ["big"],
    });
    const merged = mergeProjections([complete, cut]);
    expect(merged.truncated).toBe(true);
    expect([...merged.truncatedNodeIds]).toEqual(["big"]);
  });

  it("unions truncated node ids across fetches without duplicating them", () => {
    const one = projection("a", [node("a", 0)], [], { truncated: true, truncatedNodeIds: ["a", "b"] });
    const two = projection("b", [node("b", 0)], [], { truncated: true, truncatedNodeIds: ["b", "c"] });
    const merged = mergeProjections([one, two]);
    expect([...merged.truncatedNodeIds].sort()).toEqual(["a", "b", "c"]);
  });

  it("tolerates a truncated projection that omits truncatedNodeIds", () => {
    const cut = projection("a", [node("a", 0)], [], { truncated: true });
    const merged = mergeProjections([cut]);
    expect(merged.truncated).toBe(true);
    expect(merged.truncatedNodeIds.size).toBe(0);
  });

  it("stays false for truncation when a guest's tree merely has privacy holes", () => {
    // A guest's tree can legitimately have gaps with truncated=false; the
    // volume banner must never double as a "someone is hidden here" signal.
    const guestView = projection("root", [node("root", 0), node("grandchild", 2)], []);
    expect(mergeProjections([guestView]).truncated).toBe(false);
  });

  it("skips undefined entries from queries that have not resolved", () => {
    const a = projection("root", [node("root", 0)], []);
    const merged = mergeProjections([a, undefined, a]);
    expect(merged.nodesById.size).toBe(1);
  });
});
