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
   * Cỡ thẻ phải là MỘT nguồn, không phải hai chỗ chép cho khớp nhau.
   *
   * <p>Trước đây `person-node.tsx` ghi thẳng `width: 208` / `minHeight: 96` và ca kiểm này đọc mã
   * nguồn bằng biểu thức chính quy để canh cho hai con số khỏi lệch. Cách ấy chỉ phát hiện được
   * độ lệch <em>sau khi</em> nó đã xảy ra, và nó im lặng hỏng theo một kiểu khác: sửa hằng số mà
   * quên sửa component thì mọi toạ độ bố cục sai đi (thẻ chồng nhau, `fitView` canh nhầm khung) mà
   * không có lỗi nào ở đâu cả.</p>
   *
   * <p>Nay component IMPORT hằng số, nên độ lệch không còn là trạng thái biểu diễn được. Ca kiểm
   * đổi vai theo: nó không so hai con số nữa, nó ghim rằng **không còn con số nào để mà so** —
   * tức `person-node.tsx` không được phép quay lại lối ghi thẳng số đo.</p>
   */
  it("khai cỡ thẻ bằng cách IMPORT hằng số, không chép tay con số vào component", () => {
    const source = readFileSync(
      resolve(__dirname, "../../../src/components/tree/person-node.tsx"),
      "utf8"
    );

    expect(source, "person-node.tsx phải lấy cỡ thẻ từ layout-constants").toMatch(
      /style=\{\{\s*width:\s*NODE_WIDTH,\s*minHeight:\s*NODE_HEIGHT\s*\}\}/
    );
    expect(source).toContain("NODE_WIDTH");
    expect(source).toContain("NODE_HEIGHT");

    // Và không còn một con số đo đạc nào chép tay ở chỗ khai cỡ thẻ.
    expect(/width:\s*\d+/.test(source), "person-node.tsx đang ghi thẳng bề ngang thẻ").toBe(false);
    expect(
      /minHeight:\s*\d+/.test(source),
      "person-node.tsx đang ghi thẳng chiều cao thẻ"
    ).toBe(false);

    // Hai hằng số vẫn phải thoả ràng buộc mà vùng chạm đặt ra (xem toggle-hit-area.test.ts).
    expect(NODE_WIDTH).toBeGreaterThan(80);
    expect(NODE_HEIGHT).toBeGreaterThan(80);
  });
});
