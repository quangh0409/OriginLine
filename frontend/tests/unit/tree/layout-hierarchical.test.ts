import { describe, expect, it } from "vitest";
import { layoutHierarchical } from "@/lib/tree/layout-hierarchical";
import {
  HIERARCHICAL_RANK_SEP,
  NODE_HEIGHT,
  NODE_WIDTH,
} from "@/lib/tree/layout-constants";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { edge, node, threeGenerationFamily } from "../../setup/tree-fixtures";

/** Các cặp thẻ chồng lên nhau — hai hình chữ nhật NODE_WIDTH x NODE_HEIGHT giao nhau. */
function overlappingPairs(positions: Map<string, { x: number; y: number }>): string[] {
  const ids = [...positions.keys()];
  const pairs: string[] = [];
  for (let i = 0; i < ids.length; i += 1) {
    for (let j = i + 1; j < ids.length; j += 1) {
      const a = positions.get(ids[i]!)!;
      const b = positions.get(ids[j]!)!;
      if (Math.abs(a.x - b.x) < NODE_WIDTH && Math.abs(a.y - b.y) < NODE_HEIGHT) {
        pairs.push(`${ids[i]} @(${a.x},${a.y}) chồng lên ${ids[j]} @(${b.x},${b.y})`);
      }
    }
  }
  return pairs;
}

describe("layoutHierarchical — the traditional top-down phả đồ", () => {
  it("positions every node it was given", () => {
    const { nodes, edges } = threeGenerationFamily();
    const positions = layoutHierarchical(nodes, edges);
    for (const n of nodes) {
      expect(positions.has(n.id), `no position for ${n.id}`).toBe(true);
      expect(Number.isFinite(positions.get(n.id)!.x)).toBe(true);
      expect(Number.isFinite(positions.get(n.id)!.y)).toBe(true);
    }
  });

  it("places children strictly below their parent", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutHierarchical(nodes, edges);
    expect(p.get("sonA")!.y).toBeGreaterThan(p.get("root")!.y);
    expect(p.get("sonB")!.y).toBeGreaterThan(p.get("root")!.y);
    expect(p.get("grandA1")!.y).toBeGreaterThan(p.get("sonA")!.y);
  });

  it("treats an adopted child as a descendant, same rank as the biological ones", () => {
    // Con nuôi is a full member of the generation; only the EDGE is drawn
    // dashed (see toFlowEdges), the node is never demoted.
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutHierarchical(nodes, edges);
    expect(p.get("adopted")!.y).toBe(p.get("sonA")!.y);
  });

  /**
   * BUG WATCH — spouse ranking in the hierarchical view.
   *
   * A husband and wife are one đời, and a phả đồ that draws bà tổ below ông
   * tổ misstates the generation of every married-in dâu on the canvas. The
   * module feeds SPOUSE edges to dagre with `minlen: 0` intending exactly
   * this, but dagre 0.8.5 still ranks the partner one layer down.
   */
  it("keeps a spouse on the same row as their partner", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutHierarchical(nodes, edges);
    expect(Math.abs(p.get("rootWife")!.y - p.get("root")!.y)).toBeLessThan(NODE_HEIGHT);
    expect(Math.abs(p.get("sonAWife")!.y - p.get("sonA")!.y)).toBeLessThan(NODE_HEIGHT);
  });

  it("separates generations by at least the configured rank gap", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutHierarchical(nodes, edges);
    expect(p.get("sonA")!.y - p.get("root")!.y).toBeGreaterThanOrEqual(HIERARCHICAL_RANK_SEP);
  });

  it("gives siblings distinct horizontal slots — no two cards stacked on one point", () => {
    const { nodes, edges } = threeGenerationFamily();
    const p = layoutHierarchical(nodes, edges);
    const gen1 = ["sonA", "sonB", "adopted"].map((id) => p.get(id)!.x);
    expect(new Set(gen1).size).toBe(gen1.length);
  });

  it("is deterministic — the same input lays out identically every run", () => {
    // Perf runs and visual regressions are only comparable if the layout is
    // stable; dagre's ordering pass is seeded, so this must hold.
    const { nodes, edges } = threeGenerationFamily();
    const a = layoutHierarchical(nodes, edges);
    const b = layoutHierarchical(nodes, edges);
    for (const [id, pos] of a) expect(b.get(id)).toEqual(pos);
  });

  it("ignores an edge whose endpoint is not in the loaded node set", () => {
    // A lazily loaded projection legitimately contains an edge to a node that
    // has not been fetched yet; that must not throw or shift the layout.
    const { nodes, edges } = threeGenerationFamily();
    const withDangling = [...edges, edge("e-dangling", "sonB", "not-loaded-yet")];
    const clean = layoutHierarchical(nodes, edges);
    const dangling = layoutHierarchical(nodes, withDangling);
    for (const [id, pos] of clean) expect(dangling.get(id)).toEqual(pos);
  });

  it("returns an empty map for an empty graph instead of throwing", () => {
    expect(layoutHierarchical([], []).size).toBe(0);
  });

  it("lays out a single node without any edges", () => {
    const positions = layoutHierarchical([node("solo", 0)], []);
    expect(positions.size).toBe(1);
    expect(Number.isFinite(positions.get("solo")!.x)).toBe(true);
  });

  it("keeps all three wives of a polygamous marriage on the husband's rank", () => {
    // spouse_order 1/2/3 — crowding is a documented limitation, but the
    // three wives must at least land consistently with one another and above
    // the child they share.
    const nodes = [
      node("chong", 0, { spouseIds: ["vo1", "vo2", "vo3"] }),
      node("vo1", 0),
      node("vo2", 0),
      node("vo3", 0),
      node("con", 1, { parentIds: ["chong", "vo2"] }),
    ];
    const edges = [
      edge("s1", "chong", "vo1", "SPOUSE", { spouseOrder: 1 }),
      edge("s2", "chong", "vo2", "SPOUSE", { spouseOrder: 2 }),
      edge("s3", "chong", "vo3", "SPOUSE", { spouseOrder: 3 }),
      edge("c1", "chong", "con"),
      edge("c2", "vo2", "con"),
    ];
    const p = layoutHierarchical(nodes, edges);
    const wifeRows = ["vo1", "vo2", "vo3"].map((id) => p.get(id)!.y);
    expect(new Set(wifeRows).size, "wives of one husband must share one row").toBe(1);
    const wifeCols = ["vo1", "vo2", "vo3"].map((id) => p.get(id)!.x);
    expect(new Set(wifeCols).size, "wives must not be stacked on one point").toBe(3);
    expect(p.get("con")!.y).toBeGreaterThan(p.get("chong")!.y);
    expect(p.get("con")!.y).toBeGreaterThan(wifeRows[0]!);
  });

  /**
   * HỒI QUY — thẻ chồng lên nhau sau khi kéo vợ/chồng về cùng hàng.
   *
   * `alignSpouses` chỉ sửa `y`. Cạnh SPOUSE thì kéo hai vợ chồng sát nhau theo chiều ngang, nên
   * khi nâng người vợ từ hàng dưới lên hàng chồng, hai thẻ nằm gần như đè khít lên nhau. Trên
   * phả đồ thật của bộ dữ liệu giả, thẻ bà tổ phủ kín thẻ Thủy Tổ: Playwright không bấm được vào
   * nút mở rộng lẫn vào thẻ để mở hồ sơ (5 test E2E của tree-canvas hỏng cùng một nguyên nhân).
   * Một chồng lấn ở đây không phải chuyện thẩm mỹ — nó làm mất hẳn thao tác.
   */
  it("không để hai thẻ nào chồng lên nhau sau khi kéo vợ/chồng về cùng hàng", () => {
    const nodes = [
      node("ong-to", 0, { spouseIds: ["ba-to"] }),
      node("ba-to", 0, { spouseIds: ["ong-to"] }),
      node("con-truong", 1, { parentIds: ["ong-to", "ba-to"] }),
      node("con-thu", 1, { parentIds: ["ong-to", "ba-to"] }),
    ];
    const edges = [
      edge("s-1", "ong-to", "ba-to", "SPOUSE", { spouseOrder: 1 }),
      edge("c-1", "ong-to", "con-truong"),
      edge("c-2", "ong-to", "con-thu"),
      edge("c-3", "ba-to", "con-truong"),
      edge("c-4", "ba-to", "con-thu"),
    ];

    const p = layoutHierarchical(nodes, edges);

    expect(p.get("ba-to")!.y).toBe(p.get("ong-to")!.y);
    expect(
      Math.abs(p.get("ba-to")!.x - p.get("ong-to")!.x),
      "thẻ bà tổ đè lên thẻ ông tổ"
    ).toBeGreaterThanOrEqual(NODE_WIDTH);
    expect(overlappingPairs(p)).toEqual([]);
  });

  it("không để thẻ nào chồng nhau trên phóng chiếu THẬT của bộ dữ liệu giả", () => {
    // Bài kiểm bằng đồ chơi có thể lọt; đây là đúng đồ thị mà /tree trả về ở chế độ dev:mock,
    // tức đúng thứ E2E chạy trên đó.
    const graph = getMockGraph();
    const projection = queryTreeProjection(graph, {
      rootId: graph.rootId,
      depth: 2,
      direction: "DESCENDANTS",
      includeSpouses: true,
      maxNodes: 300,
      isVisible: () => true,
    })!;

    const p = layoutHierarchical(projection.nodes, projection.edges);

    expect(p.size).toBeGreaterThan(10);
    expect(overlappingPairs(p)).toEqual([]);
  });

  it("giữ nguyên tâm của mỗi hàng khi phải giãn ra — cây không bị lệch hẳn sang phải", () => {
    const nodes = [
      node("cha", 0, { spouseIds: ["me"] }),
      node("me", 0, { spouseIds: ["cha"] }),
      node("con", 1, { parentIds: ["cha", "me"] }),
    ];
    const edges = [
      edge("s-1", "cha", "me", "SPOUSE", { spouseOrder: 1 }),
      edge("c-1", "cha", "con"),
      edge("c-2", "me", "con"),
    ];

    const p = layoutHierarchical(nodes, edges);
    const rowCentre = (p.get("cha")!.x + p.get("me")!.x) / 2;

    // Người con nằm giữa hai bố mẹ, sai lệch không quá nửa bề rộng thẻ.
    expect(Math.abs(p.get("con")!.x - rowCentre)).toBeLessThan(NODE_WIDTH / 2);
  });
});
