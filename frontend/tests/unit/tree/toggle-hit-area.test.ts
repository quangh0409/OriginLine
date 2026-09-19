import { describe, expect, it } from "vitest";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { buildFamilyUnits } from "@/lib/tree/family-units";
import { layoutFamily } from "@/lib/tree/layout-family";
import { layoutHierarchical } from "@/lib/tree/layout-hierarchical";
import {
  CANVAS_MIN_ZOOM,
  MIN_INITIAL_ZOOM,
  WHOLE_TREE_FIT_VIEW_OPTIONS,
} from "@/lib/tree/fit-view";
import {
  COUPLE_GAP,
  FAMILY_RANK_SEP,
  HIERARCHICAL_NODE_SEP,
  MIN_TOUCH_TARGET_PX,
  NODE_HEIGHT,
  NODE_WIDTH,
  OPERABLE_MIN_ZOOM,
  TOGGLE_HIT_CARD_INTRUSION,
  TOGGLE_HIT_OVERHANG,
  TOGGLE_HIT_SIZE,
  TOGGLE_KNOB_INSET,
  TOGGLE_KNOB_SIZE,
} from "@/lib/tree/layout-constants";

/**
 * VÙNG CHẠM của nút bung/thu nhánh — thao tác chính của màn hình chính.
 *
 * <p>Lỗi đã sửa: nút vẽ ở {@code h-6 w-6} (24px hệ toạ độ cây) nên ở mức phóng mặc định 0.75 nó chỉ
 * còn <b>18px thật</b> trên màn hình — dưới cả sàn 24px của WCAG 2.5.8, nói gì tới 44px. Đây đúng
 * họ hàng của "tiền lệ đau thương" mà {@code lib/tree/fit-view.ts} ghi lại: hồi cây mở ở mức 0.21,
 * nút co còn 5px.</p>
 *
 * <p>Không thể chữa bằng cách phóng to nút: thẻ chỉ rộng {@link NODE_WIDTH} = 208px và phần lớn bề
 * ngang ấy là tên người. Nên phần NHÌN THẤY giữ nguyên 24px, còn vùng NHẬN CÚ CHẠM được nới trong
 * suốt ra 80px. Tệp này ghim ba điều mà chỉ số học mới trả lời được, để không phải mở trình duyệt
 * mới biết đã hỏng:</p>
 *
 * <ol>
 *   <li>vùng chạm đủ 44px THẬT ở mọi mức phóng còn thao tác được;</li>
 *   <li>vùng chạm của hai người kề nhau <b>không bao giờ chồng lên nhau</b> — khe giữa hai anh em
 *       ruột chỉ {@link HIERARCHICAL_NODE_SEP} = 32px, nới ẩu là hai nút ăn vào nhau và người dùng
 *       bung nhầm nhánh của người bên cạnh;</li>
 *   <li>vùng chạm <b>không nuốt tâm tấm thẻ</b> — "chạm giữa thẻ thì trúng thẻ" là bất biến vừa
 *       giành được, xem {@code e2e/tree-legibility.spec.ts}.</li>
 * </ol>
 */

/** Vùng chạm của một thẻ, theo hệ toạ độ canvas, từ GÓC TRÊN-TRÁI của thẻ. */
function hitBox(position: { x: number; y: number }) {
  const centerX = position.x + NODE_WIDTH / 2;
  const cardBottom = position.y + NODE_HEIGHT;
  return {
    left: centerX - TOGGLE_HIT_SIZE / 2,
    right: centerX + TOGGLE_HIT_SIZE / 2,
    top: cardBottom - TOGGLE_HIT_CARD_INTRUSION,
    bottom: cardBottom + TOGGLE_HIT_OVERHANG,
  };
}

/** Vòng tròn NHÌN THẤY, đặt theo đúng công thức component dùng. */
function knobBox(position: { x: number; y: number }) {
  const box = hitBox(position);
  const centerX = position.x + NODE_WIDTH / 2;
  return {
    left: centerX - TOGGLE_KNOB_SIZE / 2,
    right: centerX + TOGGLE_KNOB_SIZE / 2,
    top: box.top + TOGGLE_KNOB_INSET,
    bottom: box.top + TOGGLE_KNOB_INSET + TOGGLE_KNOB_SIZE,
  };
}

interface Box {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

function overlaps(a: Box, b: Box): boolean {
  return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
}

function contains(box: Box, x: number, y: number): boolean {
  return x > box.left && x < box.right && y > box.top && y < box.bottom;
}

describe("hằng số vùng chạm", () => {
  it("suy ra từ sàn 44px chứ không phải một con số nhặt được", () => {
    expect(TOGGLE_HIT_SIZE).toBe(Math.ceil(MIN_TOUCH_TARGET_PX / OPERABLE_MIN_ZOOM));
    expect(TOGGLE_HIT_SIZE).toBe(80);
    expect(TOGGLE_HIT_CARD_INTRUSION + TOGGLE_HIT_OVERHANG).toBe(TOGGLE_HIT_SIZE);
  });

  it("đạt 44px THẬT ở mức phóng phả đồ mở ra", () => {
    const real = TOGGLE_HIT_SIZE * MIN_INITIAL_ZOOM;
    expect(real).toBeGreaterThanOrEqual(MIN_TOUCH_TARGET_PX);
    // 60px — dư 16px so với sàn, đủ chỗ cho sai số làm tròn của trình duyệt và cho việc canh khung
    // trên màn hình rộng ra hơi khác 0.75.
    expect(real).toBe(60);
  });

  it("vẫn đạt 44px THẬT ở mức phóng thấp nhất còn coi là đang thao tác", () => {
    expect(TOGGLE_HIT_SIZE * OPERABLE_MIN_ZOOM).toBeGreaterThanOrEqual(MIN_TOUCH_TARGET_PX);
  });

  it("vẫn đạt sàn 24px của WCAG 2.5.8 xuống tới tận mức phóng 0.30", () => {
    expect(TOGGLE_HIT_SIZE * 0.3).toBeGreaterThanOrEqual(24);
  });

  it('cố ý KHÔNG cấm bấm ở chế độ "Thu toàn cây" — người dùng chủ ý xin nhìn toàn cảnh', () => {
    // Nút "Thu toàn cây" bỏ sàn phóng (xem fit-view.ts). Nếu vùng chạm là một ô cố định thì ở mức
    // đó nó nhỏ theo cả cây, và đó là ĐÁNH ĐỔI DO NGƯỜI DÙNG CHỌN — giống hệt lý lẽ đã ghi cho
    // WHOLE_TREE_FIT_VIEW_OPTIONS. Ghim ở đây để lần sau ai định thêm một cái chốt chặn thì đọc
    // được vì sao trước đó đã không thêm.
    expect(WHOLE_TREE_FIT_VIEW_OPTIONS.minZoom).toBe(CANVAS_MIN_ZOOM);
    expect(CANVAS_MIN_ZOOM).toBeLessThan(OPERABLE_MIN_ZOOM);
  });

  it("giữ nguyên phần nhìn thấy, và vòng tròn vẫn nằm giữa mép đáy thẻ như cũ", () => {
    expect(TOGGLE_KNOB_SIZE).toBe(24);
    const box = knobBox({ x: 0, y: 0 });
    // Mép đáy thẻ ở y = NODE_HEIGHT; vòng tròn phải cưỡi lên đúng giữa nó (12px trên, 12px dưới) —
    // đúng vị trí của `-bottom-3` cũ, tức người dùng không thấy gì xê dịch.
    expect(box.top).toBe(NODE_HEIGHT - TOGGLE_KNOB_SIZE / 2);
    expect(box.bottom).toBe(NODE_HEIGHT + TOGGLE_KNOB_SIZE / 2);
  });

  it("đặt CÂN ĐỐI quanh vòng tròn — tâm ô chạm không dịch so với nút 24px cũ", () => {
    // Mọi phép thử "chạm vào giữa nút có trúng nút không" đều lấy mẫu tại TÂM nút. Bản thử đầu
    // tiên dồn ô chạm xuống hành lang, dịch tâm xuống 16px, và rơi trúng nút "Xem thêm" của thẻ
    // chú giải trên Pixel 5 — nút bung nhánh của người ấy lập tức bấm không được.
    const hit = hitBox({ x: 0, y: 0 });
    const knob = knobBox({ x: 0, y: 0 });
    expect((hit.top + hit.bottom) / 2).toBe((knob.top + knob.bottom) / 2);
    expect((hit.top + hit.bottom) / 2).toBe(NODE_HEIGHT); // đúng mép đáy thẻ, như `-bottom-3` cũ
    expect(TOGGLE_HIT_CARD_INTRUSION).toBe(TOGGLE_HIT_OVERHANG);
  });

  it("vòng tròn nhìn thấy nằm trọn trong vùng chạm", () => {
    const hit = hitBox({ x: 0, y: 0 });
    const knob = knobBox({ x: 0, y: 0 });
    expect(knob.left).toBeGreaterThanOrEqual(hit.left);
    expect(knob.right).toBeLessThanOrEqual(hit.right);
    expect(knob.top).toBeGreaterThanOrEqual(hit.top);
    expect(knob.bottom).toBeLessThanOrEqual(hit.bottom);
  });
});

describe("vùng chạm với hình học phả đồ", () => {
  it("hẹp hơn cả tấm thẻ, nên khe anh em 32px không bao giờ bị đụng tới", () => {
    // Đây là cái bẫy: nới đều 44px ra bốn phía thì ô rộng 208 + 88 = 296px, trong khi bước ngang
    // giữa hai anh em chỉ 240px — hai vùng chạm chồng nhau 56px. Ô 80px đặt giữa thẻ thì kể cả khe
    // bằng 0 cũng không chạm được người bên cạnh.
    expect(TOGGLE_HIT_SIZE).toBeLessThan(NODE_WIDTH);

    const siblingPitch = NODE_WIDTH + HIERARCHICAL_NODE_SEP;
    const a = hitBox({ x: 0, y: 0 });
    const b = hitBox({ x: siblingPitch, y: 0 });
    expect(overlaps(a, b)).toBe(false);
    expect(b.left - a.right).toBe(siblingPitch - TOGGLE_HIT_SIZE); // 160px hở
  });

  it("không chồng lên vùng chạm của người bạn đời đứng kề", () => {
    const a = hitBox({ x: 0, y: 0 });
    const b = hitBox({ x: NODE_WIDTH + COUPLE_GAP, y: 0 });
    expect(overlaps(a, b)).toBe(false);
  });

  it("thò xuống hành lang giữa hai đời, không chạm tới đời sau", () => {
    const rowPitch = NODE_HEIGHT + FAMILY_RANK_SEP;
    expect(TOGGLE_HIT_OVERHANG).toBeLessThan(FAMILY_RANK_SEP);

    const parent = hitBox({ x: 0, y: 0 });
    const childCard = { left: 0, right: NODE_WIDTH, top: rowPitch, bottom: rowPitch + NODE_HEIGHT };
    expect(overlaps(parent, childCard)).toBe(false);

    const childHit = hitBox({ x: 0, y: rowPitch });
    expect(overlaps(parent, childHit)).toBe(false);
  });

  it("không nuốt tâm tấm thẻ của chính mình", () => {
    const box = hitBox({ x: 0, y: 0 });
    const centerX = NODE_WIDTH / 2;
    const centerY = NODE_HEIGHT / 2;
    expect(contains(box, centerX, centerY)).toBe(false);
    // Còn thừa 8px trước khi chạm tới tâm thẻ — và khoảng ấy chỉ NỚI RA khi thẻ cao hơn 96px, vì
    // thẻ dùng minHeight còn ô chạm neo theo đáy thật.
    expect(box.top - centerY).toBe(NODE_HEIGHT / 2 - TOGGLE_HIT_CARD_INTRUSION);
    expect(box.top - centerY).toBe(8);
  });
});

describe("trên một lát cắt thật của đồ thị giả lập", () => {
  const projection = queryTreeProjection(getMockGraph(), {
    rootId: "p-001",
    depth: 6,
    direction: "DESCENDANTS",
    includeSpouses: true,
    maxNodes: 400,
    isVisible: () => true,
  });

  it("không hai vùng chạm nào chồng nhau, và không vùng chạm nào phủ tâm một tấm thẻ khác", () => {
    expect(projection, "không truy được lát cắt nào từ đồ thị giả lập").not.toBeNull();
    const { nodes, edges } = projection!;
    expect(nodes.length).toBeGreaterThan(100);

    const layout = layoutFamily(nodes, buildFamilyUnits(nodes, edges), edges);
    const entries = [...layout.positions.entries()];
    expect(entries.length).toBe(nodes.length);

    const boxes = entries.map(([id, p]) => ({ id, box: hitBox(p), position: p }));

    const collisions: string[] = [];
    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        if (overlaps(boxes[i]!.box, boxes[j]!.box)) {
          collisions.push(`${boxes[i]!.id} ↔ ${boxes[j]!.id}`);
        }
      }
    }
    expect(collisions.slice(0, 10), `${collisions.length} cặp vùng chạm chồng nhau`).toEqual([]);

    const swallowedCards: string[] = [];
    for (const { id, box } of boxes) {
      for (const [otherId, p] of entries) {
        const cx = p.x + NODE_WIDTH / 2;
        const cy = p.y + NODE_HEIGHT / 2;
        if (contains(box, cx, cy)) swallowedCards.push(`${id} nuốt tâm thẻ ${otherId}`);
      }
    }
    expect(swallowedCards.slice(0, 10), swallowedCards.join("\n")).toEqual([]);
  });

  it("bất biến ấy đúng cả ở chế độ Phân cấp dựng bằng dagre (dự phòng)", () => {
    const { nodes, edges } = projection!;
    const positions = layoutHierarchical(nodes, edges);
    const boxes = [...positions.entries()].map(([id, p]) => ({ id, box: hitBox(p) }));

    const collisions: string[] = [];
    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        if (overlaps(boxes[i]!.box, boxes[j]!.box)) {
          collisions.push(`${boxes[i]!.id} ↔ ${boxes[j]!.id}`);
        }
      }
    }
    expect(collisions.slice(0, 10), `${collisions.length} cặp vùng chạm chồng nhau`).toEqual([]);
  });
});
