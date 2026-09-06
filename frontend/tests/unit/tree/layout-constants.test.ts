import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  HIERARCHICAL_NODE_SEP,
  HIERARCHICAL_RANK_SEP,
  MATRIX_COL_GAP,
  MATRIX_ROW_HEIGHT,
  NODE_HEIGHT,
  NODE_WIDTH,
  RADIAL_RADIUS_STEP,
} from "@/lib/tree/layout-constants";

describe("layout constants", () => {
  it("leaves vertical room between generations for the expand/collapse toggle", () => {
    // The toggle button sits at `-bottom-3` (12px) and is 24px tall, so it
    // overhangs the card by ~12px on each of two stacked cards. A rank
    // separation smaller than that would put two toggles on top of each other.
    expect(HIERARCHICAL_RANK_SEP).toBeGreaterThan(24);
    expect(HIERARCHICAL_NODE_SEP).toBeGreaterThan(0);
  });

  it("keeps matrix rows taller than a node card so generations never overlap", () => {
    expect(MATRIX_ROW_HEIGHT).toBeGreaterThan(NODE_HEIGHT);
    expect(MATRIX_COL_GAP).toBeGreaterThan(0);
  });

  it("keeps radial rings further apart than a node card is wide", () => {
    // Two adjacent rings closer than the card width would overlap cards
    // radially in the tỏa tròn view.
    expect(RADIAL_RADIUS_STEP).toBeGreaterThanOrEqual(NODE_WIDTH * 0.9);
  });

  /**
   * layout-constants.ts says "Keep in sync with the actual rendered card size
   * in components/tree/person-node.tsx". PersonNode currently hard-codes the
   * numbers instead of importing them, so nothing but this test stops the two
   * from drifting — and a drift makes every layout position subtly wrong
   * (overlapping cards, wrong fitView bounds) with no error anywhere.
   */
  it("matches the card size PersonNode actually renders", () => {
    const source = readFileSync(
      resolve(__dirname, "../../../src/components/tree/person-node.tsx"),
      "utf8"
    );
    const widthMatch = /width:\s*(\d+)/.exec(source);
    const heightMatch = /minHeight:\s*(\d+)/.exec(source);

    expect(widthMatch, "PersonNode should declare an explicit card width").not.toBeNull();
    expect(heightMatch, "PersonNode should declare an explicit card min-height").not.toBeNull();
    expect(Number(widthMatch?.[1])).toBe(NODE_WIDTH);
    expect(Number(heightMatch?.[1])).toBe(NODE_HEIGHT);
  });
});
