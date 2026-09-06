import { describe, expect, it } from "vitest";
import {
  CANVAS_MAX_ZOOM,
  CANVAS_MIN_ZOOM,
  INITIAL_FIT_VIEW_OPTIONS,
  MIN_INITIAL_ZOOM,
  WHOLE_TREE_FIT_VIEW_OPTIONS,
} from "@/lib/tree/fit-view";

/**
 * Hai phép canh khung của phả đồ có hai mục đích trái nhau, và điều PHÂN BIỆT chúng chỉ là một
 * con số `minZoom`. Đặt nhầm một chỗ là hỏng một trong hai:
 *
 *  - bỏ sàn ở lần mở đầu tiên → phả đồ mở ra ở mức ~0.21 trên điện thoại: thẻ 44px, tên cỡ chữ
 *    3px, nút bung nhánh 5px, dưới ngưỡng chạm 24px của WCAG 2.5.8;
 *  - áp sàn cho nút "Thu toàn cây" → nút không làm được đúng việc nó hứa, người dùng bấm mà cây
 *    vẫn tràn ra ngoài khung.
 */
describe("tham số canh khung phả đồ", () => {
  it("giữ sàn dễ đọc cho lần mở đầu tiên", () => {
    expect(MIN_INITIAL_ZOOM).toBe(0.75);
    expect(INITIAL_FIT_VIEW_OPTIONS.minZoom).toBe(MIN_INITIAL_ZOOM);
  });

  it('KHÔNG áp sàn ấy cho "Thu toàn cây" — đó mới là điểm khác biệt của nút này', () => {
    expect(WHOLE_TREE_FIT_VIEW_OPTIONS.minZoom).toBeLessThan(MIN_INITIAL_ZOOM);
    // Sàn duy nhất còn lại là giới hạn của chính canvas.
    expect(WHOLE_TREE_FIT_VIEW_OPTIONS.minZoom).toBe(CANVAS_MIN_ZOOM);
  });

  it("thu toàn cây vẫn nằm trong khoảng phóng canvas cho phép", () => {
    expect(CANVAS_MIN_ZOOM).toBeGreaterThan(0);
    expect(CANVAS_MIN_ZOOM).toBeLessThan(CANVAS_MAX_ZOOM);
    expect(WHOLE_TREE_FIT_VIEW_OPTIONS.minZoom).toBeGreaterThanOrEqual(CANVAS_MIN_ZOOM);
  });

  it("có hiệu ứng chuyển cảnh, chứ không nhảy phắt sang khung mới", () => {
    // Nhảy phắt thì người dùng mất phương hướng: không biết mình vừa bị đưa đi đâu trên cây.
    expect(INITIAL_FIT_VIEW_OPTIONS.duration).toBeGreaterThan(0);
    expect(WHOLE_TREE_FIT_VIEW_OPTIONS.duration).toBeGreaterThan(0);
  });
});
